# Publishing BeanDoc (signed APK + download website)

The website is a single static page served by Caddy on the Hetzner VM. It offers a direct download link and a QR code that both point at `https://<domain>/beandoc.apk`, which is always the latest version.

## 1. Create the signing key (once)

Every release must be signed with the **same** key, or phones refuse to install it as an update. Create it once and back it up somewhere safe outside this repo (password manager + offline copy). If you lose it, every user must uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore beandoc-release.jks -alias beandoc -keyalg RSA -keysize 4096 -validity 10000
```

(`keytool` is in Android Studio's JDK: `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.)

Add to `local.properties` (gitignored; never commit it):

```properties
beandoc.keystore=C:/path/outside/repo/beandoc-release.jks
beandoc.keystore.password=...
beandoc.key.alias=beandoc
beandoc.key.password=...
```

Or set the environment variables `BEANDOC_KEYSTORE`, `BEANDOC_KEYSTORE_PASSWORD`, `BEANDOC_KEY_ALIAS`, `BEANDOC_KEY_PASSWORD`.

## 2. Set up the server (once)

On the Hetzner VM (Debian/Ubuntu):

```bash
# Caddy: https://caddyserver.com/docs/install#debian-ubuntu-raspbian
sudo apt install -y caddy rsync
sudo mkdir -p /var/www/beandoc
sudo chown "$USER" /var/www/beandoc
```

Point your domain's DNS A (and AAAA) record at the VM, open ports 80 and 443 in the Hetzner firewall, then copy `deploy/Caddyfile` to `/etc/caddy/Caddyfile`, replace `beandoc.example.com` with your domain, and run `sudo systemctl reload caddy`. Caddy fetches the HTTPS certificate automatically.

## 3. Release a version

1. Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`. Android will not install an update with the same or a lower `versionCode`.
2. Build and test:
   ```bash
   ./gradlew test assembleRelease
   ```
3. Build the site (needs `pip install segno`):
   ```bash
   python tools/build_site.py --base-url https://<your-domain>
   ```
   This writes `site/public/`: the page, `beandoc.apk`, `beandoc-<version>.apk`, and the QR code as SVG and PNG.
4. Open `site/public/index.html` in a browser to check it.
5. Upload:
   ```bash
   BEANDOC_HOST=<user>@<server> ./deploy/deploy.sh
   ```
6. On a phone: scan the QR code (from the screen and from a printed copy), install, and check that the SHA-256 on the page matches. When releasing an update, install it over the previous version.

## Notes

- The QR code encodes the stable link, so printed posters keep working after updates.
- The page has no JavaScript, analytics, external fonts or CDNs. Caddy's access log (`/var/log/caddy/beandoc.log`) is enough to count downloads.
- The app itself has no internet permission and never checks for updates: tell users to come back to the page for new versions, or share the APK phone to phone.
