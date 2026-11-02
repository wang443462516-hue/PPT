param(
    [string]$Apk = (Join-Path $PSScriptRoot '..\analysis\original\apk'),
    [string]$Rpk = (Join-Path $PSScriptRoot '..\analysis\original\rpk')
)

$ErrorActionPreference = 'Stop'
Write-Output 'APK evidence markers:'
rg -a -o 'NotificationListenerService|AmapNotificationListener|com\.autonavi\.minimap|android\.title|android\.text|miui\.focus\.param|NavBridge' (Join-Path $Apk 'classes.dex') | Sort-Object -Unique
Write-Output 'RPK manifest:'
Get-Content -LiteralPath (Join-Path $Rpk 'manifest.json') -Encoding UTF8
Write-Output 'RPK JS evidence markers:'
rg -n 'interconnect|vibrator|DEMO|simulate|reset|onmessage|stop' (Join-Path $Rpk 'pages\index\index.js') | Select-Object -First 80
