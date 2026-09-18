<#
.SYNOPSIS
    Boots an Android emulator, installs the debug build, and launches Gratitude Garden.

.DESCRIPTION
    One entry point for seeing the app run. Safe to re-run: if an emulator is already
    booted it is reused rather than starting a second one.

    Nothing from the Android SDK is assumed to be on PATH -- every tool is invoked by
    absolute path, resolved from ANDROID_HOME / ANDROID_SDK_ROOT / local.properties.

.EXAMPLE
    .\scripts\run-emulator.ps1
    Boot (or reuse) the Medium_Phone AVD, install the debug APK, open the app.

.EXAMPLE
    .\scripts\run-emulator.ps1 -Screenshot shot.png
    Same, then save a screenshot of whatever is on screen.

.EXAMPLE
    .\scripts\run-emulator.ps1 -ColdBoot -SkipInstall
    Wipe the saved snapshot state and boot fresh, without rebuilding or installing.
#>
[CmdletBinding()]
param(
    # Name of the AVD to boot. `emulator -list-avds` shows what's available.
    [string] $Avd = 'Medium_Phone',

    # Ignore the saved boot snapshot and boot from scratch. Slower, but fixes a
    # wedged or stale snapshot.
    [switch] $ColdBoot,

    # Skip `gradlew installDebug` -- use whatever is already installed on the device.
    [switch] $SkipInstall,

    # Skip launching MainActivity (just leaves the emulator up).
    [switch] $NoLaunch,

    # Save a PNG of the device screen to this path once the app is up.
    [string] $Screenshot,

    # How long to wait for the device to finish booting.
    [int] $BootTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$AppId    = 'com.gratitudegarden.app'
$Activity = "$AppId/.MainActivity"

function Write-Step { param([string] $Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Note { param([string] $Message) Write-Host "    $Message" -ForegroundColor DarkGray }

# PowerShell 5.1 turns a native command's stderr into a terminating error while
# $ErrorActionPreference is 'Stop'. adb writes ordinary progress there ("1 file
# pulled, 0 skipped"), so relax the preference around native calls and judge
# success by exit code / side effects instead.
function Invoke-Native {
    param([scriptblock] $Command)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Command } finally { $ErrorActionPreference = $prev }
}

# --- 1. Locate the SDK -------------------------------------------------------
# Android Studio writes sdk.dir into local.properties, which is the one place
# guaranteed to be correct for this checkout (the env vars are often unset).
function Resolve-AndroidSdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)

    $localProps = Join-Path $RepoRoot 'local.properties'
    if (Test-Path $localProps) {
        $line = Select-String -Path $localProps -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' |
                Select-Object -First 1
        if ($line) {
            # sdk.dir escapes backslashes and may use forward slashes; normalise both.
            $candidates += $line.Matches[0].Groups[1].Value.Trim().Replace('\\', '\').Replace('/', '\')
        }
    }

    $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')

    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'platform-tools\adb.exe'))) { return $c }
    }
    throw "Could not find an Android SDK containing platform-tools\adb.exe. Set ANDROID_HOME, or sdk.dir in local.properties."
}

$Sdk      = Resolve-AndroidSdk
$Adb      = Join-Path $Sdk 'platform-tools\adb.exe'
$Emulator = Join-Path $Sdk 'emulator\emulator.exe'

if (-not (Test-Path $Emulator)) {
    throw "Found the SDK at $Sdk but no emulator\emulator.exe. Install the Emulator package from Android Studio's SDK Manager."
}
Write-Step "Android SDK: $Sdk"

# --- 2. Pin the JDK ----------------------------------------------------------
# gradle/gradle-daemon-jvm.properties pins toolchainVersion=21. With JAVA_HOME
# unset, Gradle falls back to whatever `java` is on PATH (JDK 25 here) and then
# provisions a JDK 21 over the network. Android Studio already ships a JBR 21,
# so point at that instead -- scoped to this process, nothing is persisted.
if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $jbr 'bin\java.exe')) {
        $env:JAVA_HOME = $jbr
        Write-Note "JAVA_HOME -> $jbr (Android Studio JBR 21)"
    }
}

# --- 3. Boot, or reuse what's already running --------------------------------
function Get-BootedEmulator {
    $out = & $Adb devices 2>$null
    foreach ($line in $out) {
        if ($line -match '^(emulator-\d+)\s+device$') { return $Matches[1] }
    }
    return $null
}

$serial = Get-BootedEmulator
if ($serial) {
    Write-Step "Reusing the emulator already running ($serial)"
} else {
    $known = & $Emulator -list-avds 2>$null
    if ($known -notcontains $Avd) {
        throw "No AVD named '$Avd'. Available: $($known -join ', '). Create one in Android Studio's Device Manager."
    }

    Write-Step "Booting $Avd"
    $emuArgs = @('-avd', $Avd)
    if ($ColdBoot) { $emuArgs += '-no-snapshot-load' }
    Start-Process -FilePath $Emulator -ArgumentList $emuArgs -WorkingDirectory (Split-Path -Parent $Emulator) | Out-Null

    # wait-for-device returns as soon as adb can talk to it, which is well before
    # Android itself is usable -- sys.boot_completed is the real signal.
    & $Adb wait-for-device
    $serial = Get-BootedEmulator
    if (-not $serial) { throw "The emulator started but adb never listed it as a device." }

    Write-Note "Waiting for Android to finish booting (up to $BootTimeoutSeconds s)..."
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    while ($true) {
        $booted = (& $Adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($booted -eq '1') { break }
        if ((Get-Date) -gt $deadline) {
            throw "Timed out waiting for $serial to boot. Try -ColdBoot."
        }
        Start-Sleep -Seconds 3
    }

    # Dismiss the lock screen so the app is actually visible.
    & $Adb -s $serial shell input keyevent 82 2>$null | Out-Null
    Write-Note "$serial is up"
}

# Make sure Gradle and every later adb call target this device, even if a
# physical phone happens to be plugged in too.
$env:ANDROID_SERIAL = $serial

# --- 4. Install --------------------------------------------------------------
if ($SkipInstall) {
    Write-Step "Skipping install (-SkipInstall)"
} else {
    Write-Step "gradlew installDebug"
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') installDebug
        if ($LASTEXITCODE -ne 0) { throw "installDebug failed (exit $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
}

# --- 5. Launch ---------------------------------------------------------------

# `am start` returns as soon as the activity is queued, so a fixed sleep tends to
# screenshot the splash screen instead of the app. Wait for the window to take
# focus. The grep runs on the device -- piping a full dumpsys over adb is slow.
function Wait-ForAppWindow {
    param([string] $Package, [int] $TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $focus = Invoke-Native {
            & $Adb -s $serial shell "dumpsys window | grep -m1 mCurrentFocus" 2>$null
        } | Out-String
        if ($focus -match [regex]::Escape($Package)) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

if (-not $NoLaunch) {
    # This AVD has 2 GB of RAM and the app is a heavy debug build. When the device
    # is already low on memory the process thrashes on startup and Android kills it
    # with "failed to complete startup" before a single frame is drawn.
    $meminfo = Invoke-Native { & $Adb -s $serial shell "cat /proc/meminfo | grep MemAvailable" 2>$null } | Out-String
    if ($meminfo -match '(\d+)\s*kB') {
        $availableMb = [int]([int]$Matches[1] / 1024)
        if ($availableMb -lt 400) {
            Write-Warning "Only $availableMb MB free on the device -- startup may be killed before it finishes. Re-run with -ColdBoot, or give the AVD more RAM in Android Studio's Device Manager."
        }
    }

    Write-Step "Launching $Activity"
    # -W blocks until the activity is displayed and reports how long that took.
    $start = Invoke-Native { & $Adb -s $serial shell am start -W -n $Activity 2>&1 } | Out-String
    if ($start -match 'Error|Exception') { throw "Could not start the app: $start" }
    if ($start -match 'TotalTime:\s*(\d+)') { Write-Note "displayed in $($Matches[1]) ms" }

    if (Wait-ForAppWindow -Package $AppId) {
        Start-Sleep -Seconds 2   # let the first Compose frame settle
        Write-Note "$AppId has window focus"
    } else {
        Write-Warning "$AppId never took window focus. It may have crashed -- check: adb -s $serial logcat -d -b crash"
    }
}

# --- 6. Screenshot -----------------------------------------------------------
if ($Screenshot) {
    # Capture on the device, then pull the file. Piping `adb exec-out` through
    # PowerShell mangles binary output, so don't.
    # Resolves relative and absolute paths alike, without requiring the file to exist.
    $target = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Screenshot)
    $dir = Split-Path -Parent $target
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

    Invoke-Native {
        & $Adb -s $serial shell screencap -p /sdcard/gg-screenshot.png
        & $Adb -s $serial pull /sdcard/gg-screenshot.png $target 2>&1 | Out-Null
        & $Adb -s $serial shell rm -f /sdcard/gg-screenshot.png
    }
    if (-not (Test-Path $target)) { throw "Screenshot capture failed -- nothing was written to $target." }
    Write-Step "Screenshot: $target"
}

Write-Host ""
Write-Step "Ready on $serial"
Write-Note "logcat:    adb -s $serial logcat --pid=(adb -s $serial shell pidof $AppId)"
Write-Note "uninstall: adb -s $serial uninstall $AppId"
