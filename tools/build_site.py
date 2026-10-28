"""Build the BeanDoc download website into site/public/.

Copies the signed release APK, generates the QR code locally (no online QR service),
and fills version, size, SHA-256 and the direct link into the page.

    site/public/index.html
    site/public/beandoc.apk                 stable direct link: always the latest version
    site/public/beandoc-<versionName>.apk   versioned copy
    site/public/beandoc-qr.svg / .png       QR code for the stable link (printing / sharing)
    site/public/favicon.svg

Usage:
    ./gradlew assembleRelease
    python tools/build_site.py --base-url https://beandoc.example.com

Requires: pip install segno
"""
import argparse
import datetime
import hashlib
import html
import os
import re
import shutil
import sys

import segno

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
TEMPLATE = os.path.join(ROOT, "site", "index.template.html")
GRADLE_FILE = os.path.join(ROOT, "app", "build.gradle.kts")
STABLE_NAME = "beandoc.apk"


def read_version():
    text = open(GRADLE_FILE, encoding="utf-8").read()
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code:
        sys.exit(f"Could not read versionName/versionCode from {GRADLE_FILE}")
    return name.group(1), int(code.group(1))


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", required=True, help="e.g. https://beandoc.example.com (no trailing slash)")
    ap.add_argument("--apk", default=DEFAULT_APK, help="signed release APK")
    ap.add_argument("--out", default=os.path.join(ROOT, "site", "public"))
    args = ap.parse_args()

    if not os.path.exists(args.apk):
        sys.exit(f"APK not found: {args.apk}\n"
                 "Run ./gradlew assembleRelease with a signing key configured (see deploy/README.md).")
    if args.apk.endswith("-unsigned.apk"):
        sys.exit("Refusing to publish an unsigned APK: Android will not install it.")

    base_url = args.base_url.rstrip("/")
    url = f"{base_url}/{STABLE_NAME}"
    version, code = read_version()
    os.makedirs(args.out, exist_ok=True)

    versioned = f"beandoc-{version}.apk"
    shutil.copyfile(args.apk, os.path.join(args.out, STABLE_NAME))
    shutil.copyfile(args.apk, os.path.join(args.out, versioned))

    # QR encodes the stable link, so printed posters keep working after updates.
    qr = segno.make(url, error="m")
    qr.save(os.path.join(args.out, "beandoc-qr.svg"), scale=10, border=4)
    qr.save(os.path.join(args.out, "beandoc-qr.png"), scale=20, border=4)

    size_mb = os.path.getsize(args.apk) / 1_000_000
    digest = sha256(args.apk)
    page = open(TEMPLATE, encoding="utf-8").read()
    for key, value in {
        "VERSION": version,
        "DATE": datetime.date.today().isoformat(),
        "SIZE": f"{size_mb:.1f} MB",
        "URL": url,
        "SHA256": digest,
    }.items():
        page = page.replace("{{" + key + "}}", html.escape(value))
    leftover = re.findall(r"\{\{\w+\}\}", page)
    if leftover:
        sys.exit(f"Unfilled placeholders in template: {leftover}")
    with open(os.path.join(args.out, "index.html"), "w", encoding="utf-8") as f:
        f.write(page)
    shutil.copyfile(os.path.join(ROOT, "site", "favicon.svg"), os.path.join(args.out, "favicon.svg"))

    print(f"BeanDoc {version} (versionCode {code}), {size_mb:.1f} MB")
    print(f"SHA-256 {digest}")
    print(f"Direct link {url}")
    print(f"Site written to {args.out}")


if __name__ == "__main__":
    main()
