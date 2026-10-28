#!/usr/bin/env bash
# Upload site/public/ to the server (static files served by nginx, see deploy/nginx-beandoc.conf).
# Run tools/build_site.py first. Server settings come from deploy/deploy.env (gitignored; see deploy.env.example).
#
#   bash deploy/deploy.sh
#
# The new site is uploaded next to the live one and swapped in with one rename, so visitors never see a
# half-uploaded page. Older versioned APKs are carried over, so their links keep working.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
[ -f "$HERE/deploy.env" ] && source "$HERE/deploy.env"
VM="${VM:?Set VM in deploy/deploy.env, e.g. app@your-server}"
KEY="${KEY:?Set KEY in deploy/deploy.env, e.g. ~/.ssh/your_deploy_key}"
REMOTE_DIR="${REMOTE_DIR:-beandoc}"   # relative to the remote user's home
SRC="$(cd "$HERE/.." && pwd)/site/public"
SSH=(ssh -i "$KEY" -o BatchMode=yes "$VM")

[ -f "$SRC/index.html" ] && [ -f "$SRC/beandoc.apk" ] || {
    echo "site/public/ is incomplete. Run: python tools/build_site.py --base-url \"\$BASE_URL\"" >&2
    exit 1
}

echo "Uploading site/public to $REMOTE_DIR/site"
"${SSH[@]}" "rm -rf $REMOTE_DIR/site.new && mkdir -p $REMOTE_DIR/site.new $REMOTE_DIR/site"
scp -q -r -i "$KEY" -o BatchMode=yes "$SRC"/. "$VM:$REMOTE_DIR/site.new/"
"${SSH[@]}" "cd $REMOTE_DIR && for f in site/beandoc-[0-9]*.apk; do [ -e \"\$f\" ] && [ ! -e \"site.new/\${f#site/}\" ] && cp -p \"\$f\" site.new/; done; \
    chmod -R a+rX site.new && rm -rf site.old && mv site site.old && mv site.new site && rm -rf site.old"
echo "Done: ${BASE_URL:-<your site>}"
