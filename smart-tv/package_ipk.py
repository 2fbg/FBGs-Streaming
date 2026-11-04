#!/usr/bin/env python3
"""
LG webOS IPK Package Builder for MK21 Streaming
Creates valid, installable .ipk packages for LG Smart TVs (webOS 3.0+ to webOS 24)
compatible with webOS Dev Manager, Homebrew Channel, and webOS CLI (ares-install).
"""

import gzip
import hashlib
import os
import re
import sys
import io
import json
import math
import shutil
import struct
import tarfile


def _png_dimensions(path):
    """Return (width, height) for a PNG file, or None when it is not a valid PNG."""
    try:
        with open(path, "rb") as file:
            header = file.read(24)
    except OSError:
        return None
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", header[16:24])


def validate_appinfo(src_dir, appinfo):
    """Validate the manifest fields webOS relies on to render the app.

    Raises ValueError listing every problem, so the build fails before shipping
    a package the TV would install with a missing or wrongly sized icon.
    """
    errors = []

    app_id = appinfo.get("id")
    if not isinstance(app_id, str) or not re.fullmatch(
        r"[A-Za-z][A-Za-z0-9-]*(\.[A-Za-z][A-Za-z0-9-]*)+", app_id
    ):
        errors.append(f'"id" must look like "com.vendor.app" (got {app_id!r})')

    version = appinfo.get("version")
    if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        errors.append(f'"version" must be a semantic version like 2.4.0 (got {version!r})')

    required_fields = ("title", "vendor", "type", "main", "icon", "largeIcon", "splashBackground")
    for field in required_fields:
        value = appinfo.get(field)
        if not isinstance(value, str) or not value.strip():
            errors.append(f'"{field}" is required')

    main = appinfo.get("main")
    if isinstance(main, str) and main.strip():
        if not os.path.isfile(os.path.join(src_dir, main)):
            errors.append(f'"main" points to a missing file: {main}')

    # Launcher icon sizes documented by LG for webOS TV packages.
    for field, expected_size in (
        ("icon", (80, 80)),
        ("largeIcon", (130, 130)),
        ("splashBackground", (1920, 1080)),
    ):
        rel_path = appinfo.get(field)
        if not isinstance(rel_path, str) or not rel_path.strip():
            continue
        path = os.path.join(src_dir, rel_path)
        if not os.path.isfile(path):
            errors.append(f'"{field}" points to a missing file: {rel_path}')
            continue
        dimensions = _png_dimensions(path)
        if dimensions is None:
            errors.append(f'"{field}" must be a PNG image: {rel_path}')
        elif dimensions != expected_size:
            errors.append(
                f'"{field}" must be {expected_size[0]}x{expected_size[1]} '
                f'(got {dimensions[0]}x{dimensions[1]}): {rel_path}'
            )

    if errors:
        details = "\n".join(f"  - {error}" for error in errors)
        raise ValueError(f"Invalid appinfo.json in {src_dir}:\n{details}")


# The TV's pkgverifier extracts control.tar.gz/data.tar.gz with its own tar. A member
# with an epoch-zero mtime makes that tar print "implausibly old time stamp 1969-12-31",
# which the verifier treats as a fatal extraction error and reports to the installer as
# "-5: ipk verified failed". Keep a fixed yet realistic timestamp so builds stay
# byte-for-byte reproducible without tripping that check.
DEFAULT_BUILD_TIMESTAMP = 1735689600  # 2025-01-01T00:00:00Z


def _build_timestamp():
    """Fixed timestamp for every archive member, so the same sources give the same bytes."""
    source_date_epoch = os.environ.get("SOURCE_DATE_EPOCH", "").strip()
    if not source_date_epoch:
        return DEFAULT_BUILD_TIMESTAMP
    try:
        timestamp = int(source_date_epoch)
    except ValueError:
        raise ValueError(
            f"SOURCE_DATE_EPOCH must be an integer timestamp (got {source_date_epoch!r})"
        )
    if timestamp <= 0:
        raise ValueError(
            "SOURCE_DATE_EPOCH must be positive: webOS rejects packages whose archive "
            "members carry an epoch-zero mtime"
        )
    return timestamp


def _write_tar_gz(add_members, timestamp):
    """Write a gzipped tar with a constant gzip header and the build's fixed mtime."""
    buffer = io.BytesIO()
    with gzip.GzipFile(
        fileobj=buffer, mode="wb", compresslevel=9, mtime=timestamp, filename=""
    ) as gz:
        with tarfile.open(fileobj=gz, mode="w") as tar:
            add_members(tar)
    return buffer.getvalue()


def _add_file(tar, path, arcname, timestamp):
    """Add a file with normalized ownership, mode and mtime instead of the checkout's."""
    info = tarfile.TarInfo(name=arcname)
    info.size = os.path.getsize(path)
    info.mtime = timestamp
    info.mode = 0o755 if os.stat(path).st_mode & 0o111 else 0o644
    info.uid = info.gid = 0
    info.uname = info.gname = "root"
    info.type = tarfile.REGTYPE
    with open(path, "rb") as file:
        tar.addfile(info, file)


def _sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _stamp_manifests(manifests, version, fields):
    """Write ``fields`` into every manifest that declares ``version``.

    Manifests that declare another version are left untouched, so a stale manifest
    can never advertise this build. Returns the paths that were considered a match.
    """
    updated = []
    summary = " ".join(f"{key}={value}" for key, value in fields.items())

    for manifest_path in manifests:
        if not os.path.exists(manifest_path):
            continue
        with open(manifest_path, "r", encoding="utf-8") as file:
            current_content = file.read()
        manifest = json.loads(current_content)
        declared_version = str(manifest.get("version", ""))
        if declared_version != str(version):
            print(
                f"[-] Skipping {manifest_path}: declares version {declared_version or 'unknown'!r}, "
                f"build is {version!r}"
            )
            continue
        manifest.update(fields)
        new_content = json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"
        updated.append(manifest_path)
        if new_content == current_content:
            print(f"[=] {manifest_path} already up to date: {summary}")
            continue
        with open(manifest_path, "w", encoding="utf-8") as file:
            file.write(new_content)
        print(f"[*] Stamped {manifest_path}: {summary}")

    return updated


def stamp_version_manifests(manifests, version, ipk_path):
    """Publish the built package hash and size in every release manifest of this version.

    The manifest bundled inside the app is not one of these: it cannot contain the
    hash of the package that contains it.
    """
    return _stamp_manifests(
        manifests,
        version,
        {
            "ipkSha256": _sha256_of(ipk_path),
            "ipkSizeBytes": os.path.getsize(ipk_path),
        },
    )


def stamp_apk_manifests(manifests, version, apk_path):
    """Publish the built APK hash and size, which the Android updater verifies on download."""
    apk_sha256 = _sha256_of(apk_path)
    return _stamp_manifests(
        manifests,
        version,
        {
            "apkSha256": apk_sha256,
            "sha256": apk_sha256,
            "sizeBytes": os.path.getsize(apk_path),
        },
    )


def build_ipk(src_dir, output_ipk_path):
    appinfo_path = os.path.join(src_dir, "appinfo.json")
    if not os.path.exists(appinfo_path):
        raise FileNotFoundError(f"appinfo.json not found in {src_dir}")

    with open(appinfo_path, "r", encoding="utf-8") as f:
        appinfo = json.load(f)

    validate_appinfo(src_dir, appinfo)

    app_id = appinfo.get("id", "com.fbg2.mk21streaming")
    version = appinfo.get("version", "1.0.0")
    title = appinfo.get("title", "MK21 Streaming")
    vendor = appinfo.get("vendor", "FBG2")
    timestamp = _build_timestamp()
    installed_size = math.ceil(sum(
        os.path.getsize(os.path.join(root, filename))
        for root, _, files in os.walk(src_dir)
        for filename in files
    ) / 1024)

    print(f"[*] Packaging webOS app: {title} ({app_id} v{version})...")

    # 1. debian-binary
    debian_binary = b"2.0\n"

    # 2. control.tar.gz
    control_content = f"""Package: {app_id}
Version: {version}
Section: misc
Priority: optional
Architecture: all
Maintainer: {vendor} <support@mk21.app>
Description: {title} IPTV and Streaming Application for LG Smart TV webOS
Installed-Size: {installed_size}
""".encode("utf-8")

    def add_control(tar):
        ti = tarfile.TarInfo(name="control")
        ti.size = len(control_content)
        ti.mtime = timestamp
        ti.mode = 0o644
        ti.uid = ti.gid = 0
        ti.uname = ti.gname = "root"
        tar.addfile(ti, io.BytesIO(control_content))

    control_tar_gz = _write_tar_gz(add_control, timestamp)

    # 3. data.tar.gz
    app_target_dir = f"usr/palm/applications/{app_id}"
    pkg_target_dir = f"usr/palm/packages/{app_id}"

    # Add packageinfo.json in usr/palm/packages/<id>/
    packageinfo = {
        "id": app_id,
        "version": version,
        "app": app_id
    }
    pkg_bytes = json.dumps(packageinfo, indent=2).encode("utf-8")

    def add_data(tar):
        # Add all files from src_dir, sorted for a stable member order
        for root, dirs, files in os.walk(src_dir):
            dirs.sort()
            files.sort()
            rel_dir = os.path.relpath(root, src_dir)
            target_base = app_target_dir if rel_dir == "." else f"{app_target_dir}/{rel_dir}"

            for f in files:
                file_path = os.path.join(root, f)
                arcname = f"{target_base}/{f}".replace("\\", "/")
                _add_file(tar, file_path, arcname, timestamp)

        pkg_ti = tarfile.TarInfo(name=f"{pkg_target_dir}/packageinfo.json")
        pkg_ti.size = len(pkg_bytes)
        pkg_ti.mtime = timestamp
        pkg_ti.mode = 0o644
        pkg_ti.uid = pkg_ti.gid = 0
        pkg_ti.uname = pkg_ti.gname = "root"
        tar.addfile(pkg_ti, io.BytesIO(pkg_bytes))

    data_tar_gz = _write_tar_gz(add_data, timestamp)

    # 4. Assemble GNU AR archive (.ipk)
    members = [
        ("debian-binary", debian_binary),
        ("control.tar.gz", control_tar_gz),
        ("data.tar.gz", data_tar_gz),
    ]

    os.makedirs(os.path.dirname(os.path.abspath(output_ipk_path)), exist_ok=True)
    with open(output_ipk_path, "wb") as out:
        out.write(b"!<arch>\n")
        for name, data in members:
            header = f"{name:<16}{timestamp:<12}0     0     100644  {len(data):<10}\x60\n".encode("ascii")
            out.write(header)
            out.write(data)
            if len(data) % 2 == 1:
                out.write(b"\n")

    size_kb = os.path.getsize(output_ipk_path) / 1024
    print(f"[+] Successfully generated: {output_ipk_path} ({size_kb:.1f} KB)")
    return output_ipk_path

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(script_dir, "mk21-tv")
    
    # Obter versão do appinfo.json dinamicamente
    app_version = "1.0.0"
    appinfo_file = os.path.join(src, "appinfo.json")
    if os.path.exists(appinfo_file):
        with open(appinfo_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        app_version = data.get("version", app_version)

    default_ipk = os.path.join(script_dir, f"mk21play_{app_version}_all.ipk")
    dst = sys.argv[2] if len(sys.argv) > 2 else default_ipk

    web_dir = os.path.join(os.path.dirname(script_dir), "web")
    root_dir = os.path.dirname(script_dir)
    release_manifests = [
        os.path.join(script_dir, "version.json"),
        os.path.join(root_dir, "version.json"),
        os.path.join(root_dir, "api", "version.json"),
        os.path.join(root_dir, "public", "version.json"),
        os.path.join(web_dir, "version.json"),
        os.path.join(web_dir, "api", "version.json"),
    ]

    # Stamp the APK before packaging, because the app bundles its own version.json.
    apk_path = os.path.join(root_dir, "mk21.apk")
    if os.path.exists(apk_path):
        stamp_apk_manifests(
            release_manifests + [os.path.join(src, "version.json")],
            app_version,
            apk_path,
        )
    else:
        print(f"[-] No APK found at {apk_path}; skipping apkSha256/sizeBytes stamping")

    build_ipk(src, dst)

    # Also generate mirrors for web and compatibility links
    def copy_mirror(destination):
        if os.path.abspath(dst) != os.path.abspath(destination):
            shutil.copy2(dst, destination)

    if os.path.exists(web_dir):
        copy_mirror(os.path.join(web_dir, f"mk21play_{app_version}_all.ipk"))
        copy_mirror(os.path.join(web_dir, "mk21.ipk"))
    copy_mirror(os.path.join(root_dir, "mk21.ipk"))
    copy_mirror(os.path.join(script_dir, "mk21.ipk"))
    copy_mirror(os.path.join(script_dir, "mk21-tv.ipk"))

    # Publish the generated hash/size in the release manifests that match this version
    stamp_version_manifests(release_manifests, app_version, dst)
