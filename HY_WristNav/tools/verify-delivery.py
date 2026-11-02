import hashlib
import json
import os
import pathlib
import zipfile

out = pathlib.Path('delivery')
cert = pathlib.Path(os.environ['HY_CERT_DER']).read_bytes()
fingerprint = hashlib.sha256(cert).hexdigest()
apk_report = (out / 'apk-signature.txt').read_text().lower().replace(':', '')
assert fingerprint in apk_report, 'APK signing certificate mismatch'
with zipfile.ZipFile(out / 'HY_WristNav-0.1.0.rpk') as package:
    manifest = json.loads(package.read('manifest.json'))
    assert manifest['package'] == 'com.hy.wristnav'
    assert cert in package.read('META-INF/CERT'), 'RPK signing certificate mismatch'
    assert 'pages/index/index.js' in package.namelist()
with zipfile.ZipFile(out / 'HY_WristNav-0.1.0.apk') as package:
    assert 'classes.dex' in package.namelist()
(out / 'certificate-sha256.txt').write_text(fingerprint + '\n')
print('APK signature verified; APK/RPK certificates match; RPK package checked')
