#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
project_dir="$PWD"
sign_dir="$(mktemp -d)"
chmod 700 "$sign_dir"
export HY_KEY_PASSWORD="$(openssl rand -hex 24)"
echo "::add-mask::$HY_KEY_PASSWORD"
export HY_KEYSTORE="$sign_dir/hy-wristnav.p12"
keytool -genkeypair -alias hy-wristnav -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12 -keystore "$HY_KEYSTORE" -storepass:env HY_KEY_PASSWORD \
  -keypass:env HY_KEY_PASSWORD -dname 'CN=HY WristNav Development,O=HY WristNav,C=CN' -noprompt
mkdir -p watch/sign/debug watch/sign/release delivery
openssl pkcs12 -in "$HY_KEYSTORE" -passin env:HY_KEY_PASSWORD -nocerts -nodes -out "$sign_dir/key-bag.pem"
openssl pkey -in "$sign_dir/key-bag.pem" -out watch/sign/debug/private.pem
openssl pkcs12 -in "$HY_KEYSTORE" -passin env:HY_KEY_PASSWORD -clcerts -nokeys -out "$sign_dir/cert-bag.pem"
openssl x509 -in "$sign_dir/cert-bag.pem" -out watch/sign/debug/certificate.pem
cp watch/sign/debug/*.pem watch/sign/release/
printf '%s' "$HY_KEY_PASSWORD" > "$sign_dir/password.txt"
tar -czf "$sign_dir/backup.tar.gz" -C "$sign_dir" hy-wristnav.p12 password.txt
node tools/seal-signing.cjs "$sign_dir/backup.tar.gz" tools/signing-backup-public.pem delivery/signing-backup.encrypted.json
bash tools/fetch-xiaomi-sdk.sh
node tools/test-watch.cjs
(cd android && gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug)
(cd watch && npm install --no-audit --no-fund && npm run build 2>&1 | tee build.log)
if grep -E 'ERROR:|build error' watch/build.log; then exit 1; fi
mapfile -t rpks < <(find watch/dist -name '*.rpk' -size +0c)
test "${#rpks[@]}" -eq 1
cp android/app/build/outputs/apk/debug/app-debug.apk delivery/HY_WristNav-0.1.0.apk
cp "${rpks[0]}" delivery/HY_WristNav-0.1.0.rpk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs delivery/HY_WristNav-0.1.0.apk > delivery/apk-signature.txt
openssl x509 -in watch/sign/debug/certificate.pem -outform DER -out "$sign_dir/certificate.der"
export HY_CERT_DER="$sign_dir/certificate.der"
python3 tools/verify-delivery.py
(cd delivery && sha256sum HY_WristNav-0.1.0.apk HY_WristNav-0.1.0.rpk > SHA256SUMS.txt)
printf 'Cloud build verified. Real Watch S4 navigation and raise-to-wake testing is pending.\n' > delivery/TEST-STATUS.txt
