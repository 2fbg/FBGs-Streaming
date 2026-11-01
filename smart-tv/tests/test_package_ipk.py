import gzip
import io
import json
import os
import sys
import tarfile
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from package_ipk import build_ipk


class PackageIpkTests(unittest.TestCase):
    def test_builds_webos_package_with_manifest_and_correct_installed_size(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = os.path.join(temp_dir, "app")
            os.makedirs(source_dir)
            manifest = {
                "id": "com.example.mk21",
                "version": "2.3.12",
                "title": "MK21 Test",
                "vendor": "MK21",
            }
            with open(os.path.join(source_dir, "appinfo.json"), "w", encoding="utf-8") as file:
                json.dump(manifest, file)
            with open(os.path.join(source_dir, "index.html"), "w", encoding="utf-8") as file:
                file.write("<!doctype html><title>MK21</title>")

            output_path = os.path.join(temp_dir, "mk21.ipk")
            build_ipk(source_dir, output_path)

            with open(output_path, "rb") as file:
                archive = file.read()
            self.assertTrue(archive.startswith(b"!<arch>\n"))

            members = {}
            offset = 8
            while offset < len(archive):
                header = archive[offset:offset + 60]
                name = header[:16].decode("ascii").strip()
                size = int(header[48:58].decode("ascii").strip())
                start = offset + 60
                members[name] = archive[start:start + size]
                offset = start + size + size % 2

            self.assertEqual(members["debian-binary"], b"2.0\n")
            control = gzip.decompress(members["control.tar.gz"])
            with tarfile.open(fileobj=io.BytesIO(control), mode="r:") as tar:
                control_text = tar.extractfile("control").read().decode("utf-8")
            self.assertIn("Package: com.example.mk21", control_text)
            self.assertIn("Version: 2.3.12", control_text)
            self.assertIn("Installed-Size: 1", control_text)

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

            self.assertEqual(app_manifest["version"], "2.3.12")
            self.assertEqual(package_info["app"], "com.example.mk21")
            self.assertEqual(package_info["version"], "2.3.12")


if __name__ == "__main__":
    unittest.main()
