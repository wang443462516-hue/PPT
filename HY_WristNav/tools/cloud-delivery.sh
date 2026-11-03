#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${HY_SIGNING_DIR:?Original signing identity must be restored first}"
umask 077
sign_dir="$HY_SIGNING_DIR"
export HY_KEY_PASSWORD="$(cat "$sign_dir/password.txt")"
echo "::add-mask::$HY_KEY_PASSWORD"
export HY_KEYSTORE="$sign_dir/hy-wristnav.p12"
test -s "$HY_KEYSTORE"
mkdir -p watch/sign/debug watch/sign/release delivery
openssl pkcs12 -in "$HY_KEYSTORE" -passin env:HY_KEY_PASSWORD -nocerts -nodes -out "$sign_dir/key-bag.pem"
openssl pkey -in "$sign_dir/key-bag.pem" -out watch/sign/debug/private.pem
openssl pkcs12 -in "$HY_KEYSTORE" -passin env:HY_KEY_PASSWORD -clcerts -nokeys -out "$sign_dir/cert-bag.pem"
openssl x509 -in "$sign_dir/cert-bag.pem" -out watch/sign/debug/certificate.pem
cp watch/sign/debug/*.pem watch/sign/release/
openssl x509 -in watch/sign/debug/certificate.pem -outform DER -out "$sign_dir/certificate.der"
export HY_CERT_DER="$sign_dir/certificate.der"
export HY_EXPECTED_CERT_SHA256=5eddf10339348d9d1aa8fd12dc8b6a80a79f2def8288bdb8c46744e887c0f454
actual="$(sha256sum "$HY_CERT_DER" | cut -d' ' -f1)"
test "$actual" = "$HY_EXPECTED_CERT_SHA256" || { echo "::error::Signing identity differs from v0.1.0"; exit 1; }
bash tools/fetch-xiaomi-sdk.sh
node tools/test-watch.cjs
(cd android && gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug)
(cd watch && npm install --no-audit --no-fund && npm run build 2>&1 | tee build.log)
if grep -E 'ERROR:|build error' watch/build.log; then exit 1; fi
mapfile -t rpks < <(find watch/dist -name '*.rpk' -size +0c)
test "${#rpks[@]}" -eq 1
cp android/app/build/outputs/apk/debug/app-debug.apk delivery/HY_WristNav-0.1.1.apk
cp "${rpks[0]}" delivery/HY_WristNav-0.1.1.rpk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs delivery/HY_WristNav-0.1.1.apk > delivery/apk-signature.txt
python3 tools/verify-delivery.py
(cd delivery && sha256sum HY_WristNav-0.1.1.apk HY_WristNav-0.1.1.rpk > SHA256SUMS.txt)
printf 'Cloud tests and paired signatures verified; original v0.1.0 signing certificate preserved. S4 real-device authorization/navigation acceptance remains pending.\n' > delivery/TEST-STATUS.txt
