#!/usr/bin/env bash
# Upload site/public/ to the server. Run tools/build_site.py first.
#
#   BEANDOC_HOST=deploy@203.0.113.10 ./deploy/deploy.sh
#
# Old versioned APKs on the server are kept (no --delete), so older links keep working.
set -euo pipefail

HOST="${BEANDOC_HOST:?Set BEANDOC_HOST, e.g. deploy@your-server}"
REMOTE_DIR="${BEANDOC_REMOTE_DIR:-/var/www/beandoc}"
SRC="$(cd "$(dirname "$0")/.." && pwd)/site/public/"

[ -f "$SRC/index.html" ] && [ -f "$SRC/beandoc.apk" ] || {
    echo "site/public/ is incomplete. Run: python tools/build_site.py --base-url https://<your-domain>" >&2
    exit 1
}

echo "Uploading $SRC to $HOST:$REMOTE_DIR"
# Upload the APKs and QR codes first, then the page, so visitors never see a page pointing at a missing file.
rsync -avz --exclude index.html "$SRC" "$HOST:$REMOTE_DIR/"
rsync -avz "$SRC/index.html" "$HOST:$REMOTE_DIR/index.html"
echo "Done. Check the site, then download and install the APK on a phone."
