# HY_WristNav

V0.1 first-round diagnostic build for Xiaomi Watch S4 + Xiaomi Vela.

This is a new project. It does not modify, re-sign, or overwrite the supplied
`VelaNav.apk` or `watchnav.rpk`.

## Scope implemented in this round

- Android `NotificationListenerService` for Amap (`com.autonavi.minimap`)
- Redacted notification snapshots and parser diagnostics
- Traditional Amap fields plus `miui.focus.param` JSON fallback
- Versioned `nav_snapshot` / `hello` / `request_snapshot` / `stop` protocol
- Explicit `LIVE` versus `DEMO` states
- Vela HUD with large maneuver arrow, distance, road, freshness and link state
- Native screen timeout policy (`keepScreenOn: false`)
- Offline parser tests and sample notifications

The Android transport now uses Xiaomi's official XMS 1.4 SDK, downloaded from
the vendor's documented demo URL with a SHA-256 check. It discovers the connected
device, registers a receive listener, sends snapshots and records watch ACKs.
Device behavior remains unverified until the paired APK/RPK is installed on S4.

## Project layout

```text
android/   Kotlin Android app
watch/     Vela Quick App source
docs/      protocol, install, compatibility, test record
samples/   redacted notifications and demo payloads
analysis/  offline evidence extracted from the supplied APK/RPK
tools/     Windows helper scripts
```

## Build status on the current workstation

Development builds run on GitHub Actions, not on the user's workstation.
On 2026-11-03, Android compilation/unit tests and Vela compilation/state tests
passed for commit d7b861e8818d456ba67aef75989088f8ee3f226d.
The signed delivery workflow exports a paired APK/RPK only after tests, APK
signature verification and matching embedded certificates have passed.

This is a V0.1 diagnostic candidate, not device acceptance. Amap/HyperOS field
compatibility, screen wake behavior, latency and power use are pending real
device tests. The parser fixtures are synthetic; no real navigation logs have
been supplied. Manual JSON editing, full log export and advanced focus schema
coverage are not part of this candidate.

Signing recovery uses an AES-GCM encrypted archive with an RSA-wrapped key.
The recovery private key exists only in the local ignored .private directory.
Preserve the recovery artifact and that key. Delivery now requires the original
identity, pinned by certificate SHA-256, and never generates a replacement.
The ephemeral GitHub runner publishes a temporary RSA public key artifact.
The operator encrypts the recovered signing archive with tools/seal-signing.cjs,
then commits only the encrypted envelope under
signing-exchange/<run-id>-<attempt>.json within ten minutes. The runner decrypts
it locally, builds, verifies the original certificate, and removes private material.
Only data is read from the exchange commit; source stays at the tested build commit.

Version 0.1.1 adds explicit start, DEVICE_MANAGER permission check/request before
message registration, bounded retries, installation/launch checks, notification
listener recovery and copyable diagnostics. See docs/0.1.1-真机测试.md.

## References

- Xiaomi Vela interconnect: https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html
- Xiaomi Vela brightness: https://iot.mi.com/vela/quickapp/zh/features/system/brightness.html
