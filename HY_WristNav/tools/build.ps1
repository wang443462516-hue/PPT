param(
    [switch]$SkipAndroid,
    [switch]$SkipWatch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $SkipAndroid) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    $wrapper = Join-Path $root 'android\gradlew.bat'
    if (-not $java) { throw 'Android build blocked: java was not found on PATH.' }
    if ((-not $gradle) -and (-not (Test-Path -LiteralPath $wrapper))) {
        throw 'Android build blocked: gradle/gradlew.bat was not found. Generate the wrapper with Android Studio first.'
    }
    if (Test-Path -LiteralPath $wrapper) {
        Push-Location (Join-Path $root 'android')
        try { & $wrapper ':app:assembleDebug' } finally { Pop-Location }
    } else {
        Push-Location (Join-Path $root 'android')
        try { & $gradle ':app:assembleDebug' } finally { Pop-Location }
    }
}

if (-not $SkipWatch) {
    Write-Warning 'Watch build is intentionally not guessed: open watch/ in the installed Xiaomi AIoT-IDE and use its official Build/Package action.'
}
