# Publishing BeanDoc (signed APK + download website)

The website is a single static page with a direct download link and a QR code that both point at `https://<host>/beandoc.apk`, which is always the latest version.

BeanDoc runs on a Hetzner Ubuntu VM that it shares with other apps. Static files are served by the host's nginx with a Let's Encrypt certificate from certbot. The server address and SSH key live in `deploy/deploy.env`, which is gitignored; copy [deploy.env.example](deploy.env.example) to create it.

## Release an update

1. Increase `versionCode` (and `versionName`) in `app/build.gradle.kts`. Android will not install an update with the same or a lower `versionCode`.
2. Build, generate the site, upload:
   ```bash
   source deploy/deploy.env
   ./gradlew test assembleRelease
   python tools/build_site.py --base-url "$BASE_URL"     # needs: pip install segno
   bash deploy/deploy.sh
   ```
3. On a phone: open the site or scan the QR code, install the update over the previous version, and check that the SHA-256 on the page matches.

`deploy.sh` uploads to a new folder and swaps it in with one rename, so visitors never see a half-uploaded site. Older versioned APKs are kept.

## One-time setup

### 1. Signing key

Every release must be signed with the **same** key, or phones refuse to install it as an update. Create it once, outside the repo, and back up the file and password (password manager + offline copy). If you lose it, every user must uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore beandoc-release.jks -alias beandoc -keyalg RSA -keysize 4096 -validity 10000
```

(`keytool` is in Android Studio's JDK: `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.)

Add to `local.properties` (gitignored):

```properties
beandoc.keystore=C:/path/outside/repo/beandoc-release.jks
beandoc.keystore.password=...
beandoc.key.alias=beandoc
beandoc.key.password=...
```

Or set `BEANDOC_KEYSTORE`, `BEANDOC_KEYSTORE_PASSWORD`, `BEANDOC_KEY_ALIAS`, `BEANDOC_KEY_PASSWORD`. Without a key, `assembleRelease` produces an unsigned APK and `build_site.py` refuses to publish it.

### 2. Server with nginx (shared VM, as deployed)

On a VM that already runs other sites, make **additive changes only**: no firewall or sshd changes, and never reload nginx when `nginx -t` fails. As root:

```bash
install -d -o app -g app /home/app/beandoc/site
setfacl -m u:www-data:x /home/app                      # nginx may traverse /home/app, not list it
sed 's/beandoc.example.com/<your-host>/' nginx-beandoc.conf > /etc/nginx/sites-available/beandoc
ln -s /etc/nginx/sites-available/beandoc /etc/nginx/sites-enabled/beandoc
nginx -t && systemctl reload nginx
certbot --nginx -d <your-host> --redirect
```

No domain? `<your-ip-with-dashes-or-dots>.sslip.io` resolves to the server's IP and works with Let's Encrypt.

[nginx-beandoc.conf](nginx-beandoc.conf) sets the APK MIME type (`application/vnd.android.package-archive`, so Android offers "Install"), `no-cache` for the stable link, long caching for versioned APKs, and a strict Content-Security-Policy.

### Alternative: dedicated server with Caddy

On a server that runs nothing else, [Caddyfile](Caddyfile) does the same with automatic HTTPS:

```bash
sudo apt install -y caddy
sudo mkdir -p /var/www/beandoc && sudo chown "$USER" /var/www/beandoc
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile     # replace beandoc.example.com with your domain
sudo systemctl reload caddy
```

Then set `REMOTE_DIR=/var/www/beandoc` in `deploy/deploy.env`.

## Notes

- The QR code encodes the stable link, so printed posters keep working after updates.
- The page has no JavaScript, analytics, external fonts or CDNs. The web server's access log is enough to count downloads.
- The app itself has no internet permission and never checks for updates: users come back to the page for new versions, or share the APK phone to phone.
