import gzip
import hashlib
import io
import json
import math
import os
import struct
import sys
import tarfile
import tempfile
import time
import unittest
import zlib

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from package_ipk import build_ipk, stamp_apk_manifests, stamp_version_manifests


def write_png(path, width, height):
    """Write a minimal solid-color PNG so tests do not need an image library."""
    raw = b"".join(b"\x00" + b"\x00\x00\x00" * width for _ in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(
            ">I", zlib.crc32(kind + data) & 0xFFFFFFFF
        )

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 6))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as file:
        file.write(png)


def read_ar_members(path):
    with open(path, "rb") as file:
        archive = file.read()
    members = {}
    offset = 8
    while offset < len(archive):
        header = archive[offset:offset + 60]
        name = header[:16].decode("ascii").strip()
        size = int(header[48:58].decode("ascii").strip())
        start = offset + 60
        members[name] = archive[start:start + size]
        offset = start + size + size % 2
    return archive, members


def make_app(source_dir, manifest_overrides=None):
    os.makedirs(source_dir, exist_ok=True)
    manifest = {
        "id": "com.example.mk21",
        "version": "2.3.12",
        "title": "MK21 Test",
        "vendor": "MK21",
        "type": "web",
        "main": "index.html",
        "icon": "icon.png",
        "largeIcon": "largeIcon.png",
        "splashBackground": "splash.png",
    }
    manifest.update(manifest_overrides or {})
    with open(os.path.join(source_dir, "appinfo.json"), "w", encoding="utf-8") as file:
        json.dump(manifest, file)
    with open(os.path.join(source_dir, "index.html"), "w", encoding="utf-8") as file:
        file.write("<!doctype html><title>MK21</title>")
    write_png(os.path.join(source_dir, "icon.png"), 80, 80)
    write_png(os.path.join(source_dir, "largeIcon.png"), 130, 130)
    write_png(os.path.join(source_dir, "splash.png"), 1920, 1080)
    return manifest


class PackageIpkTests(unittest.TestCase):
    def test_builds_webos_package_with_manifest_and_correct_installed_size(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            make_app(source_dir)

            output_path = os.path.join(temp_dir, "mk21.ipk")
            build_ipk(source_dir, output_path)

            archive, members = read_ar_members(output_path)
            self.assertTrue(archive.startswith(b"!<arch>\n"))

            self.assertEqual(members["debian-binary"], b"2.0\n")
            control = gzip.decompress(members["control.tar.gz"])
            with tarfile.open(fileobj=io.BytesIO(control), mode="r:") as tar:
                control_text = tar.extractfile("control").read().decode("utf-8")
            self.assertIn("Package: com.example.mk21", control_text)
            self.assertIn("Version: 2.3.12", control_text)
            installed_kb = math.ceil(sum(
                os.path.getsize(os.path.join(root, filename))
                for root, _, files in os.walk(source_dir)
                for filename in files
            ) / 1024)
            self.assertIn(f"Installed-Size: {installed_kb}", control_text)

            data = gzip.decompress(members["data.tar.gz"])
            with tarfile.open(fileobj=io.BytesIO(data), mode="r:") as tar:
                app_manifest = json.load(tar.extractfile(
                    "usr/palm/applications/com.example.mk21/appinfo.json"
                ))
                package_info = json.load(tar.extractfile(
                    "usr/palm/packages/com.example.mk21/packageinfo.json"
                ))
                self.assertEqual(tar.extractfile(
                    "usr/palm/applications/com.example.mk21/index.html"
                ).read(), b"<!doctype html><title>MK21</title>")
                self.assertIn(
                    "usr/palm/applications/com.example.mk21/splash.png",
                    tar.getnames(),
                )

            self.assertEqual(app_manifest["version"], "2.3.12")
            self.assertEqual(package_info["app"], "com.example.mk21")
            self.assertEqual(package_info["version"], "2.3.12")

    def test_build_is_byte_for_byte_reproducible(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            make_app(source_dir)
            first_path = os.path.join(temp_dir, "first.ipk")
            second_path = os.path.join(temp_dir, "second.ipk")

            build_ipk(source_dir, first_path)
            time.sleep(1.1)  # a wall-clock timestamp would now differ between builds
            build_ipk(source_dir, second_path)

            first_bytes, _ = read_ar_members(first_path)
            second_bytes, members = read_ar_members(second_path)
            self.assertEqual(
                hashlib.sha256(first_bytes).hexdigest(),
                hashlib.sha256(second_bytes).hexdigest(),
            )

            # Timestamps, ownership and modes must come from the build, not the checkout.
            data = gzip.decompress(members["data.tar.gz"])
            with tarfile.open(fileobj=io.BytesIO(data), mode="r:") as tar:
                info = tar.getmember("usr/palm/applications/com.example.mk21/index.html")
            self.assertEqual(info.mtime, 0)
            self.assertEqual((info.uid, info.gid), (0, 0))
            self.assertEqual((info.uname, info.gname), ("root", "root"))
            self.assertEqual(info.mode, 0o644)

    def test_stamps_built_package_hash_into_matching_version_manifests(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            make_app(source_dir)
            ipk_path = os.path.join(temp_dir, "mk21.ipk")
            build_ipk(source_dir, ipk_path)
            with open(ipk_path, "rb") as file:
                expected_sha256 = hashlib.sha256(file.read()).hexdigest()

            matching_path = os.path.join(temp_dir, "version.json")
            stale_path = os.path.join(temp_dir, "stale.json")
            with open(matching_path, "w", encoding="utf-8") as file:
                json.dump({"version": "2.3.12", "ipkSha256": "", "ipkSizeBytes": 0}, file)
            with open(stale_path, "w", encoding="utf-8") as file:
                json.dump({"version": "2.3.7", "ipkSha256": "old", "ipkSizeBytes": 1}, file)

            updated = stamp_version_manifests(
                [matching_path, stale_path, os.path.join(temp_dir, "missing.json")],
                "2.3.12",
                ipk_path,
            )

            self.assertEqual(updated, [matching_path])
            with open(matching_path, encoding="utf-8") as file:
                manifest = json.load(file)
            self.assertEqual(manifest["ipkSha256"], expected_sha256)
            self.assertEqual(manifest["ipkSizeBytes"], os.path.getsize(ipk_path))

            # A manifest for another version must not advertise this package.
            with open(stale_path, encoding="utf-8") as file:
                self.assertEqual(
                    json.load(file),
                    {"version": "2.3.7", "ipkSha256": "old", "ipkSizeBytes": 1},
                )

    def test_stamps_built_apk_hash_into_matching_version_manifests(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            apk_path = os.path.join(temp_dir, "mk21.apk")
            with open(apk_path, "wb") as file:
                file.write(b"fake-apk-content")
            expected_sha256 = hashlib.sha256(b"fake-apk-content").hexdigest()

            matching_path = os.path.join(temp_dir, "version.json")
            stale_path = os.path.join(temp_dir, "stale.json")
            with open(matching_path, "w", encoding="utf-8") as file:
                json.dump({"version": "2.3.13", "apkSha256": "", "sha256": "", "sizeBytes": 0}, file)
            with open(stale_path, "w", encoding="utf-8") as file:
                json.dump({"version": "2.3.7"}, file)

            updated = stamp_apk_manifests([matching_path, stale_path], "2.3.13", apk_path)

            self.assertEqual(updated, [matching_path])
            with open(matching_path, encoding="utf-8") as file:
                manifest = json.load(file)
            self.assertEqual(manifest["apkSha256"], expected_sha256)
            self.assertEqual(manifest["sha256"], expected_sha256)
            self.assertEqual(manifest["sizeBytes"], len(b"fake-apk-content"))

            with open(stale_path, encoding="utf-8") as file:
                self.assertEqual(json.load(file), {"version": "2.3.7"})

    def test_rejects_launcher_icon_with_wrong_dimensions(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            make_app(source_dir, {"icon": "mk21-logo.png"})
            write_png(os.path.join(source_dir, "mk21-logo.png"), 192, 192)

            with self.assertRaises(ValueError) as context:
                build_ipk(source_dir, os.path.join(temp_dir, "mk21.ipk"))
            self.assertIn('"icon" must be 80x80 (got 192x192)', str(context.exception))
            self.assertFalse(os.path.exists(os.path.join(temp_dir, "mk21.ipk")))

    def test_rejects_missing_splash_background_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            make_app(source_dir, {"splashBackground": "missing-splash.png"})

            with self.assertRaises(ValueError) as context:
                build_ipk(source_dir, os.path.join(temp_dir, "mk21.ipk"))
            self.assertIn('"splashBackground" points to a missing file', str(context.exception))


if __name__ == "__main__":
    unittest.main()
