param(
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [string]$Package = 'com.nicgames.offthetop'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [Console]::OutputEncoding
# Pin every command to the single running emulator, never a connected phone.
$serial = & $Adb -e get-serialno
if ($LASTEXITCODE -or "$serial" -notmatch '^emulator-\d+$') { throw 'Exactly one running Android emulator is required' }
$device = @('-s', "$serial")
$root = Split-Path $PSScriptRoot
$shots = Join-Path $root 'screenshots'
New-Item -ItemType Directory -Force $shots | Out-Null

function Read-Ui {
    & $Adb @device shell uiautomator dump /sdcard/offthetop-test.xml | Out-Null
    if ($LASTEXITCODE) { throw 'UI dump failed' }
    $raw = & $Adb @device shell cat /sdcard/offthetop-test.xml
    [xml]($raw -join "`n")
}
function Tap-Text([string]$Label) {
    $ui = Read-Ui
    $node = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Label } | Select-Object -First 1
    if (!$node) { throw "Control not found: $Label" }
    $b = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb @device shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
}
function Capture([string]$Label) {
    & $Adb @device shell screencap -p /sdcard/offthetop-shot.png
    & $Adb @device pull /sdcard/offthetop-shot.png (Join-Path $shots "$Label.png") | Out-Null
}
function Require-Text([string]$Label) {
    $ui = Read-Ui
    $matches = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq $Label })
    if (!$matches.Count) { throw "Expected screen text missing: $Label" }
    Write-Output "PASS: $Label"
}
function Require-Description([string]$Label) {
    $ui = Read-Ui
    $matches = @($ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Label })
    if (!$matches.Count) { throw "Expected screen description missing: $Label" }
    Write-Output "PASS: $Label"
}
function Disable-Toggle([string]$Label) {
    $ui = Read-Ui
    $node = $ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Label -and $_.checkable -eq 'true' } | Select-Object -First 1
    if (!$node) { throw "Toggle not found: $Label" }
    if ($node.checked -eq 'true') {
        $b = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
        & $Adb @device shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
        $ui = Read-Ui
    }
    if (!@($ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $Label -and $_.checkable -eq 'true' -and $_.checked -eq 'false' }).Count) { throw "Toggle must be off: $Label" }
}

& $Adb @device shell am force-stop $Package
& $Adb @device shell am start -W -n "$Package/com.nicgames.offthetop.MainActivity"
Require-Text 'Choose a deck'
# A release install retains preferences: normalize only this emulator via its UI.
Tap-Text 'Settings'
Tap-Text 'Day'
Disable-Toggle 'Touch-only mode'
Disable-Toggle 'Gentle tilts'
Capture 'settings-day'
Tap-Text '‹ Back'
Tap-Text '60s'
Capture 'home'
Tap-Text 'Wild World'
& $Adb @device emu sensor set acceleration 9.81:0:0
Require-Text 'Start round  →'
Require-Text 'Ready'
& $Adb @device emu sensor set acceleration 0:0:-9.81
Require-Description 'Correct tested'
Require-Text 'Return to start'
& $Adb @device emu sensor set acceleration 9.81:0:0
Require-Text 'Ready'
& $Adb @device emu sensor set acceleration 0:0:9.81
Require-Description 'Pass tested'
Require-Text 'Return to start'
Capture 'tilt-practice'
& $Adb @device emu sensor set acceleration 9.81:0:0
Tap-Text 'Start round  →'
Capture 'countdown'
# UI snapshots provide real elapsed time and verification, not arbitrary sleep delays.
$watch = [Diagnostics.Stopwatch]::StartNew()
do {
    $ui = Read-Ui
    $playing = @($ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq '0 correct' }).Count -gt 0
    if ($watch.Elapsed.TotalSeconds -gt 15) { throw 'Round did not start' }
} until ($playing)
Capture 'playing'
& $Adb @device emu sensor set acceleration 0:0:-9.81
Require-Description '1 correct'
& $Adb @device emu sensor set acceleration 9.81:0:0
Require-Description '1 correct'
& $Adb @device emu sensor set acceleration 0:0:9.81
Require-Description '1 correct'
& $Adb @device emu sensor set acceleration 9.81:0:0
Tap-Text 'Pause'
Require-Text 'Paused'
Capture 'paused'
Tap-Text 'End & review'
Require-Text 'Results'
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
& $Adb @device shell am force-stop $Package
& $Adb @device shell am start -W -n "$Package/com.nicgames.offthetop.MainActivity"
Tap-Text 'Recent rounds'
Require-Text 'Wild World'
Write-Output 'PASS: signed-release tilt gameplay, pause, review, themes, and history after process restart'
Tap-Text '‹ Back'
Tap-Text '30s'
Tap-Text 'Do Your Thing'
Tap-Text 'Start round  →'
$watch = [Diagnostics.Stopwatch]::StartNew()
do {
    $ui = Read-Ui
    $finished = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq 'Results' }).Count -gt 0
    if ($watch.Elapsed.TotalSeconds -gt 45) { throw 'Real 30-second round did not expire' }
} until ($finished)
if ($watch.Elapsed.TotalSeconds -lt 29) { throw 'Timer expired prematurely' }
Require-Text '0 passed · 1 unanswered'
Capture 'timer-expired'
Write-Output "PASS: real 30-second timer expired without injected time after $([math]::Round($watch.Elapsed.TotalSeconds, 1)) seconds including countdown and UI verification"

function Set-Tilt([double]$Degrees, [int]$Direction = 1) {
    $rad = $Degrees * [Math]::PI / 180
    $x = ($Direction * 9.81 * [Math]::Cos($rad)).ToString('F4', [Globalization.CultureInfo]::InvariantCulture)
    $z = (9.81 * [Math]::Sin($rad)).ToString('F4', [Globalization.CultureInfo]::InvariantCulture)
    & $Adb @device emu sensor set acceleration "${x}:0:${z}" | Out-Null
}

# Real sensor stack with a 22-degree resting hold, not just ideal full-flat flips.
# Both landscape directions must support a small nod down AND back up.
foreach ($direction in @(1, -1)) {
    Tap-Text 'Change deck'
    # Reuse these rounds for Night/Day coverage; no extra round or timed wait.
    Tap-Text 'Settings'
    Tap-Text $(if ($direction -eq 1) { 'Night' } else { 'Day' })
    Tap-Text '‹ Back'
    Tap-Text '60s'
    Set-Tilt 22 $direction
    Tap-Text 'Everyday Things'
    Require-Text 'Ready'
    foreach ($degrees in @(16, 10, 4, -2, -8, -12)) { Set-Tilt $degrees $direction }
    Require-Description 'Correct tested'
    Require-Text 'Return to start'
    Set-Tilt 22 $direction
    Require-Text 'Ready'
    foreach ($degrees in @(28, 34, 40, 46, 52, 56)) { Set-Tilt $degrees $direction }
    Require-Description 'Pass tested'
    Require-Text 'Return to start'
    Set-Tilt 22 $direction
    Require-Text 'Ready'
    Capture "natural-tilt-$direction"
    Tap-Text 'Start round  →'
    $watch = [Diagnostics.Stopwatch]::StartNew()
    do {
        $ui = Read-Ui
        $playing = @($ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq '0 correct' }).Count -gt 0
        if ($watch.Elapsed.TotalSeconds -gt 15) { throw 'Natural-hold round did not start' }
    } until ($playing)
    if ($direction -eq 1) { Capture 'playing-night' }
    foreach ($degrees in @(16, 10, 4, -2, -8, -12)) { Set-Tilt $degrees $direction }
    Require-Description '1 correct'
    Set-Tilt 22 $direction
    Require-Description '1 correct'
    foreach ($degrees in @(28, 34, 40, 46, 52, 56)) { Set-Tilt $degrees $direction }
    Require-Description '1 correct'
    Set-Tilt 22 $direction
    Tap-Text 'Pause'
    Require-Text 'Paused'
    Tap-Text 'End & review'
    Require-Text 'Results'
    Require-Text '1 passed · 1 unanswered'
    Write-Output "PASS: 22-degree forehead hold, modest down/up nods, landscape direction $direction"
}