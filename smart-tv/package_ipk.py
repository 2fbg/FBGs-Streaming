#!/usr/bin/env python3
"""
LG webOS IPK Package Builder for MK21 Streaming
Creates valid, installable .ipk packages for LG Smart TVs (webOS 3.0+ to webOS 24)
compatible with webOS Dev Manager, Homebrew Channel, and webOS CLI (ares-install).
"""

import os
import sys
import io
import json
import time
import tarfile

def build_ipk(src_dir, output_ipk_path):
    appinfo_path = os.path.join(src_dir, "appinfo.json")
    if not os.path.exists(appinfo_path):
        raise FileNotFoundError(f"appinfo.json not found in {src_dir}")

    with open(appinfo_path, "r", encoding="utf-8") as f:
        appinfo = json.load(f)

    app_id = appinfo.get("id", "com.fbg2.mk21streaming")
    version = appinfo.get("version", "1.0.0")
    title = appinfo.get("title", "MK21 Streaming")
    vendor = appinfo.get("vendor", "FBG2")

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
Installed-Size: 1024
""".encode("utf-8")

    control_tar_io = io.BytesIO()
    with tarfile.open(fileobj=control_tar_io, mode="w:gz") as tar:
        ti = tarfile.TarInfo(name="control")
        ti.size = len(control_content)
        ti.mtime = int(time.time())
        ti.mode = 0o644
        tar.addfile(ti, io.BytesIO(control_content))

    control_tar_gz = control_tar_io.getvalue()

    # 3. data.tar.gz
    data_tar_io = io.BytesIO()
    with tarfile.open(fileobj=data_tar_io, mode="w:gz") as tar:
        # Directories
        app_target_dir = f"usr/palm/applications/{app_id}"
        pkg_target_dir = f"usr/palm/packages/{app_id}"

        # Add all files from src_dir
        for root, dirs, files in os.walk(src_dir):
            rel_dir = os.path.relpath(root, src_dir)
            target_base = app_target_dir if rel_dir == "." else f"{app_target_dir}/{rel_dir}"

            for f in files:
                file_path = os.path.join(root, f)
                arcname = f"{target_base}/{f}".replace("\\", "/")
                tar.add(file_path, arcname=arcname)

        # Add packageinfo.json in usr/palm/packages/<id>/
        packageinfo = {
            "id": app_id,
            "version": version,
            "app": app_id
        }
        pkg_bytes = json.dumps(packageinfo, indent=2).encode("utf-8")
        pkg_ti = tarfile.TarInfo(name=f"{pkg_target_dir}/packageinfo.json")
        pkg_ti.size = len(pkg_bytes)
        pkg_ti.mtime = int(time.time())
        pkg_ti.mode = 0o644
        tar.addfile(pkg_ti, io.BytesIO(pkg_bytes))

    data_tar_gz = data_tar_io.getvalue()

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
            header = f"{name:<16}{int(time.time()):<12}0     0     100644  {len(data):<10}\x60\n".encode("ascii")
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
        try:
            with open(appinfo_file, "r") as f:
                data = json.load(f)
                if "version" in data:
                    app_version = data["version"]
        except Exception:
            pass

    default_ipk = os.path.join(script_dir, f"mk21play_{app_version}_all.ipk")
    dst = sys.argv[2] if len(sys.argv) > 2 else default_ipk
    build_ipk(src, dst)
    
    # Also generate mirrors for web and compatibility links
    import shutil
    web_dir = os.path.join(os.path.dirname(script_dir), "web")
    if os.path.exists(web_dir):
        shutil.copy2(dst, os.path.join(web_dir, f"mk21play_{app_version}_all.ipk"))
        shutil.copy2(dst, os.path.join(web_dir, "mk21.ipk"))
    shutil.copy2(dst, os.path.join(script_dir, "mk21-tv.ipk"))
    shutil.copy2(dst, os.path.join(script_dir, "com.fbg2.mk21streaming_1.1.0_all.ipk"))

