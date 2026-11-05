#requires -version 5
<#
  获取 aria2c Android arm64 预编译二进制并放入 jniLibs/arm64-v8a/libaria2c.so。
  来源：https://github.com/devgianlu/aria2-android/releases/tag/v1.37.0
  说明：Android 10+ targetSdk>=29 不允许执行应用私有数据目录中的文件，因此必须作为
  native library 打包，由系统解压到 nativeLibraryDir 后执行；不能放在 assets 里释放后 exec。

  用法：
    powershell -ExecutionPolicy Bypass -File native/aria2/fetch.ps1
  可选 -Mirror https://gh-proxy.com/ 或 -ExpectedSha256 <hash>
#>
param(
    [string]$Mirror = "https://gh-proxy.com/",
    [string]$ExpectedSha256 = "dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a",
    [string]$WorkDir = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $WorkDir) { $WorkDir = Join-Path (Split-Path $root -Parent) ".tools\aria2" }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$fileName = "aria2c-arm64-v8a"
$url = $Mirror + "https://github.com/devgianlu/aria2-android/releases/download/v1.37.0/" + $fileName
$outFile = Join-Path $WorkDir $fileName
Write-Host "下载 $url"
if (-not (Test-Path $outFile) -or (Get-Item $outFile).Length -ne 69107592) {
    curl.exe --ssl-no-revoke -L --fail --retry 3 -o $outFile $url
}
$size = (Get-Item $outFile).Length
if ($size -ne 69107592) { throw "大小不符：$size != 69107592（下载被代理截断？）" }
$hash = (Get-FileHash $outFile -Algorithm SHA256).Hash.ToLower()
Write-Host "SHA256: $hash"
if ($ExpectedSha256 -and $hash -ne $ExpectedSha256.ToLower()) { throw "SHA256 不匹配，期望 $ExpectedSha256" }
$destDir = Join-Path $root "app\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
Copy-Item $outFile (Join-Path $destDir "libaria2c.so") -Force
Write-Host "已写入 $(Join-Path $destDir 'libaria2c.so')"