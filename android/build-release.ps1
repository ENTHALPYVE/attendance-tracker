<#
  Builds the signed Android app (APK) for installing on phones.
  Run it yourself in PowerShell after android/setup-tools.sh:

      powershell -ExecutionPolicy Bypass -File android\build-release.ps1

  The first time it creates your signing key. keytool and apksigner ask for the key password
  themselves; this script never sees, stores or prints it.

  KEEP THE KEYSTORE FILE AND ITS PASSWORD SAFE (e.g. a copy on a USB drive or in a password
  manager). Every future version of the app must be signed with the same key, and it must never
  be added to Git (.gitignore blocks *.keystore).
#>
param(
    [string]$Keystore = "$env:USERPROFILE\attendance-signing\attendance-release.keystore"
)
# No $ErrorActionPreference = 'Stop': Windows PowerShell would treat keytool's normal progress
# messages (written to stderr) as errors. Every step checks $LASTEXITCODE instead.
$T ="$env:LOCALAPPDATA\attendance-android-tools"
$env:JAVA_HOME = "$T\jdk17"
$env:ANDROID_HOME = "$T\sdk"
$keytool = "$T\jdk17\bin\keytool.exe"
$buildTools = "$T\sdk\build-tools\35.0.0"
$alias = 'attendance'

if (-not (Test-Path "$T\project\gradlew.bat")) {
    throw "Android project not found. Run android/setup-tools.sh in Git Bash first."
}

# 1. Signing key (only the first time)
if (-not (Test-Path $Keystore)) {
    New-Item -ItemType Directory -Force (Split-Path $Keystore) | Out-Null
    Write-Host "`nCreating your signing key in $Keystore" -ForegroundColor Cyan
    Write-Host "Choose a strong password (at least 6 characters) and write it down somewhere safe."
    Write-Host "Name / organisation questions can be answered with your name and college.`n"
    & $keytool -genkeypair -v -keystore $Keystore -alias $alias -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "keytool could not create the key." }
}

# 2. Build the unsigned release APK
Push-Location "$T\project"
try {
    & .\gradlew.bat assembleRelease --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
} finally {
    Pop-Location
}

# 3. Align and sign it (apksigner asks for the key password)
$unsigned = "$T\project\app\build\outputs\apk\release\app-release-unsigned.apk"
$aligned = "$T\app-release-aligned.apk"
$signed = "$T\AttendanceTracker.apk"
& "$buildTools\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed." }
Write-Host "`nSigning the app - enter your key password:" -ForegroundColor Cyan
& "$buildTools\apksigner.bat" sign --ks $Keystore --ks-key-alias $alias --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed." }
& "$buildTools\apksigner.bat" verify $signed
if ($LASTEXITCODE -ne 0) { throw "The signed APK did not verify." }
Remove-Item $aligned

# 4. The key's public fingerprint, for the website (not a secret)
Write-Host "`nShowing the key's fingerprint - enter your key password once more:" -ForegroundColor Cyan
$fingerprint = (& $keytool -list -v -keystore $Keystore -alias $alias |
        Select-String -Pattern 'SHA256:\s*([0-9A-F:]+)').Matches.Groups[1].Value

Write-Host "`nDone." -ForegroundColor Green
Write-Host "Signed app:   $signed"
Write-Host "Fingerprint:  $fingerprint"
Write-Host "`nOn Render, add the environment variable  ANDROID_CERT_SHA256 = $fingerprint"
Write-Host "so https://attandtrackinger.qd.je/.well-known/assetlinks.json lists this app and it opens full screen."
