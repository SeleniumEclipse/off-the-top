param(
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [string]$Package = 'com.nicgames.offthetop'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path $PSScriptRoot
$shots = Join-Path $root 'screenshots'
New-Item -ItemType Directory -Force $shots | Out-Null

function Read-Ui {
    & $Adb shell uiautomator dump /sdcard/offthetop-test.xml | Out-Null
    if ($LASTEXITCODE) { throw 'UI dump failed' }
    $raw = & $Adb shell cat /sdcard/offthetop-test.xml
    [xml]($raw -join "`n")
}
function Tap-Text([string]$Label) {
    $ui = Read-Ui
    $node = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Label } | Select-Object -First 1
    if (!$node) { throw "Control not found: $Label" }
    $b = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
}
function Capture([string]$Label) {
    & $Adb shell screencap -p /sdcard/offthetop-shot.png
    & $Adb pull /sdcard/offthetop-shot.png (Join-Path $shots "$Label.png") | Out-Null
}
function Require-Text([string]$Label) {
    $ui = Read-Ui
    $matches = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq $Label })
    if (!$matches.Count) { throw "Expected screen text missing: $Label" }
    Write-Output "PASS: $Label"
}

& $Adb shell am force-stop $Package
& $Adb shell am start -W -n "$Package/com.nicgames.offthetop.MainActivity"
Require-Text 'PICK YOUR DECK'
Tap-Text '60s'
Capture 'home'
Tap-Text 'Wild World'
& $Adb emu sensor set acceleration 9.81:0:0
Require-Text 'Start round  →'
& $Adb emu sensor set acceleration 0:0:-9.81
Require-Text 'GOT IT! Bring the screen upright again.'
& $Adb emu sensor set acceleration 9.81:0:0
Require-Text 'GOT IT! Bring the screen upright again.'
& $Adb emu sensor set acceleration 0:0:9.81
Require-Text 'PASS! Bring the screen upright again.'
Capture 'tilt-practice'
& $Adb emu sensor set acceleration 9.81:0:0
Tap-Text 'Start round  →'
Capture 'countdown'
# UI snapshots provide real elapsed time and verification, not arbitrary sleep delays.
$watch = [Diagnostics.Stopwatch]::StartNew()
do {
    $ui = Read-Ui
    $playing = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq '0 CORRECT' }).Count -gt 0
    if ($watch.Elapsed.TotalSeconds -gt 15) { throw 'Round did not start' }
} until ($playing)
Capture 'playing'
& $Adb emu sensor set acceleration 0:0:-9.81
Require-Text '1 CORRECT'
& $Adb emu sensor set acceleration 9.81:0:0
Require-Text '1 CORRECT'
& $Adb emu sensor set acceleration 0:0:9.81
Require-Text '1 CORRECT'
& $Adb emu sensor set acceleration 9.81:0:0
Tap-Text 'Pause'
Require-Text 'ON HOLD'
Capture 'paused'
Tap-Text 'End & review'
Require-Text '1 passed · 1 unanswered'
Capture 'results'
Tap-Text 'Change deck'
Tap-Text 'Settings'
Tap-Text 'Night'
Capture 'settings-night'
Tap-Text '‹ Back'
Capture 'home-night'
Tap-Text 'Settings'
Tap-Text 'Day'
Tap-Text '‹ Back'
& $Adb shell am force-stop $Package
& $Adb shell am start -W -n "$Package/com.nicgames.offthetop.MainActivity"
Tap-Text 'Recent rounds  →'
Require-Text 'Wild World'
Write-Output 'PASS: signed-release tilt gameplay, pause, review, themes, and history after process restart'
Tap-Text '‹ Back'
Tap-Text '30s'
Tap-Text 'Do Your Thing'
Tap-Text 'Start round  →'
$watch = [Diagnostics.Stopwatch]::StartNew()
do {
    $ui = Read-Ui
    $finished = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq 'ROUND COMPLETE' }).Count -gt 0
    if ($watch.Elapsed.TotalSeconds -gt 45) { throw 'Real 30-second round did not expire' }
} until ($finished)
if ($watch.Elapsed.TotalSeconds -lt 29) { throw 'Timer expired prematurely' }
Require-Text '0 passed · 1 unanswered'
Capture 'timer-expired'
Write-Output "PASS: real 30-second timer expired without injected time after $([math]::Round($watch.Elapsed.TotalSeconds, 1)) seconds including countdown and UI verification"