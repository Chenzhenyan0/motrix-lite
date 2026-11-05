#requires -version 5
<#
  构建 Gopeed Android AAR（gomobile），输出到 app/libs/libgopeed.aar。

  依赖：
  - JDK 17（JAVA_HOME）
  - Android SDK（环境变量 ANDROID_HOME/ANDROID_SDK_ROOT，或 -AndroidSdk 参数）
  - 网络：dl.google.com 可下载 Go/NDK；goproxy.cn / 阿里云 goproxy 可下载 Go modules（proxy.golang.org 在部分网络不可用）
  默认不修改工程源码，仅生成 app/libs/libgopeed.aar。
#>
param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GoVersion = "1.24.11",
    [string]$NdkVersion = "27.2.12479018",
    [string]$GopeedVersion = "v1.9.3",
    [string]$WorkDir = "",
    [string]$OutAar = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $WorkDir) { $WorkDir = Join-Path (Split-Path $root -Parent) ".tools\gopeed-build" }
if (-not $OutAar) { $OutAar = Join-Path $root "app\libs\libgopeed.aar" }
if (-not $AndroidSdk) { $AndroidSdk = Join-Path (Split-Path $root -Parent) ".tools\android-sdk" }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$downloads = Join-Path $WorkDir "downloads"; New-Item -ItemType Directory -Force -Path $downloads | Out-Null
$goRoot = Join-Path $WorkDir "go-$GoVersion"
$ndkRoot = Join-Path $AndroidSdk "ndk\$NdkVersion"

Write-Host "== 1/5 下载并解压 Go $GoVersion =="
$goZip = Join-Path $downloads "go$GoVersion.windows-amd64.zip"
if (-not (Test-Path $goZip)) {
    curl.exe -L --fail --retry 3 -o $goZip "https://dl.google.com/go/go$GoVersion.windows-amd64.zip"
}
if (-not (Test-Path $goRoot)) {
    New-Item -ItemType Directory -Force -Path $goRoot | Out-Null
    tar.exe -xf $goZip -C $WorkDir
    if (Test-Path (Join-Path $WorkDir "go")) { Move-Item (Join-Path $WorkDir "go") $goRoot }
}

Write-Host "== 2/5 安装 Android NDK $NdkVersion =="
if (-not (Test-Path (Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe"))) {
    $sdkManager = Join-Path $AndroidSdk "cmdline-tools\latest\bin\sdkmanager.bat"
    if (Test-Path $sdkManager) {
        & $sdkManager --sdk_root=$AndroidSdk "ndk;$NdkVersion" | Out-Host
    } else {
        $ndkZip = Join-Path $downloads "android-ndk-r27c-windows.zip"
        $ndkFallback = Join-Path $WorkDir "android-ndk-r27c"
        if (-not (Test-Path $ndkZip)) {
            curl.exe -L --fail --retry 3 -o $ndkZip "https://dl.google.com/android/repository/android-ndk-r27c-windows.zip"
        }
        if (-not (Test-Path $ndkFallback)) {
            New-Item -ItemType Directory -Force -Path $ndkFallback | Out-Null
            tar.exe -xf $ndkZip -C $ndkFallback
        }
        $ndkRoot = Get-ChildItem $ndkFallback -Directory -Filter "android-ndk-*" | Select-Object -First 1 -ExpandProperty FullName
    }
}

Write-Host "== 3/5 配置 Go 环境并安装 gomobile =="
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot" }
$env:Path = (Join-Path $goRoot "bin") + ";" + (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
$env:GOPATH = Join-Path $WorkDir "gopath"
$env:GOBIN = Join-Path $env:GOPATH "bin"
$env:GOPROXY = "https://goproxy.cn,https://mirrors.aliyun.com/goproxy,direct"
$env:GOSUMDB = "off"
$env:GO111MODULE = "on"
New-Item -ItemType Directory -Force -Path $env:GOBIN | Out-Null
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
$gomobile = Join-Path $env:GOBIN "gomobile.exe"
& $gomobile init -ndk $ndkRoot

Write-Host "== 4/5 gomobile bind Gopeed $GopeedVersion =="
$bindDir = Join-Path $WorkDir "bind"
New-Item -ItemType Directory -Force -Path $bindDir | Out-Null
Push-Location $bindDir
try {
    if (-not (Test-Path "go.mod")) { go mod init motrixlite/gopeedbind }
    go get "github.com/GopeedLab/gopeed@$GopeedVersion"
    & $gomobile bind -tags nosqlite -ldflags="-w -s" -o libgopeed.aar -target=android/arm64 -javapkg=com.gopeed "github.com/GopeedLab/gopeed/bind/mobile"
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed" }
} finally { Pop-Location }

Write-Host "== 5/5 复制 AAR =="
New-Item -ItemType Directory -Force -Path (Split-Path $OutAar -Parent) | Out-Null
Copy-Item (Join-Path $bindDir "libgopeed.aar") $OutAar -Force
$hash = (Get-FileHash $OutAar -Algorithm SHA256).Hash.ToLower()
Write-Host "AAR: $OutAar"
Write-Host "点大小: $((Get-Item $OutAar).Length) bytes"
Write-Host "SHA256: $hash"