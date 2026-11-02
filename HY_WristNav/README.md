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

The Xiaomi Android interconnect SDK is not present on this workstation. The
Android source therefore exposes a small transport adapter and a logging
fallback. The exact Xiaomi SDK binding must be added before claiming a real
phone-to-watch V0.1 pass.

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

As of 2026-11-02, `java`, `gradle`, `adb`, `aapt2`, and `apkanalyzer` were not
available on PATH. Source scaffolding and offline tests are included, but no
APK/RPK build artifact is claimed from this environment.

## References

- Xiaomi Vela interconnect: https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html
- Xiaomi Vela brightness: https://iot.mi.com/vela/quickapp/zh/features/system/brightness.html
