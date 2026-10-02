$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javaBin = 'C:\env\Microsoft\jdk-17.0.13.11-hotspot\bin'
$sdk = 'C:\env\Android\SDK'
$androidBuild = "$sdk\build-tools\35.0.1"
$androidJar = "$sdk\platforms\android-35\android.jar"
New-Item -ItemType Directory -Force build\classes | Out-Null
& "$javaBin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d build\classes src\local\mediarescan\MainActivity.java src\local\mediarescan\ScanScope.java
if ($LASTEXITCODE) { throw 'javac failed' }
$classes = @(Get-ChildItem build\classes -Filter *.class -Recurse | ForEach-Object FullName)
& "$javaBin\java.exe" -cp "$androidBuild\lib\d8.jar" com.android.tools.r8.D8 --min-api 30 --lib $androidJar --output build @classes
if ($LASTEXITCODE) { throw 'd8 failed' }
& "$androidBuild\aapt2.exe" compile --dir res -o build\resources.zip
if ($LASTEXITCODE) { throw 'resource compile failed' }
& "$androidBuild\aapt2.exe" link -o build\unsigned.apk -I $androidJar --manifest AndroidManifest.xml build\resources.zip
if ($LASTEXITCODE) { throw 'resource link failed' }
& "$javaBin\jar.exe" uf build\unsigned.apk -C build classes.dex
if ($LASTEXITCODE) { throw 'jar failed' }
& "$androidBuild\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
if ($LASTEXITCODE) { throw 'zipalign failed' }
if (!(Test-Path build\media-rescan.keystore)) {
    & "$javaBin\keytool.exe" -genkeypair -keystore build\media-rescan.keystore -storepass android -keypass android -alias mediarescan -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Media Rescan Personal'
    if ($LASTEXITCODE) { throw 'key generation failed' }
}
& "$javaBin\java.exe" -jar "$androidBuild\lib\apksigner.jar" sign --ks build\media-rescan.keystore --ks-pass pass:android --out MediaRescan.apk build\aligned.apk
if ($LASTEXITCODE) { throw 'sign failed' }
& "$javaBin\java.exe" -jar "$androidBuild\lib\apksigner.jar" verify MediaRescan.apk
if ($LASTEXITCODE) { throw 'verification failed' }
