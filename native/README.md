# 原生下载内核构建与许可证说明

本目录包含两个 GPL 原生内核的构建/获取说明与脚本，**不包含二进制本体**（被 .gitignore 忽略）。
发布 APK 时这些内核会被打包；源码仓库保持轻量，二进制从脚本生成/校验。

| 内核 | 许可证 | 产物 | 获取/构建方式 |
| --- | --- | --- | --- |
| Gopeed v1.9.3 | GPL-3.0 | `app/libs/libgopeed.aar`（gomobile，arm64） | `native/gopeed/build.ps1` |
| aria2 **1.35.0** | GPL-2.0-or-later（OpenSSL 例外） | `app/src/main/jniLibs/arm64-v8a/libaria2c.so`（5,872,304 B，SHA-256 `88b8b5fe…`） | `native/aria2/fetch.ps1`（devgianlu v1.35.0 预编译） |

> **版本选择说明（v1.1.1）**：devgianlu v1.37.0 预编译二进制静态链接 OpenSSL 3.0.0，
> 在 Android 16 真机上初始化时 `OSSL_PROVIDER_load 'legacy'` 失败并退出（code 7），
> 无法作为可用的 aria2 内核。v1.35.0 预编译二进制使用 OpenSSL 1.1.1d，在 Redmi 25253RT47C /
> Android 16 上实测 subprocess + JSON-RPC、断点续传、暂停/恢复、限速均可用。
> 代价是 OpenSSL 1.1.1d（2019）已停止维护、aria2 1.35.0 也较旧；正式分发前建议按
> `native/aria2/README.md` 用受支持版本自行复现构建。

## 许可证与源码获取
- 本应用整体按 **GPLv3** 分发（见仓库根 LICENSE）。
- Gopeed 对应源码：https://github.com/GopeedLab/gopeed/tree/v1.9.3
- aria2 对应源码：https://github.com/aria2/aria2/tree/release-1.35.0
- devgianlu v1.35.0 构建文件：`native/sources/devgianlu-build-files-v1.35.0.zip`
- 构建脚本与依赖清单：本目录 + `licenses/DEPENDENCY-VERSIONS.md`
- 许可证文本：`licenses/`；APK 内 `assets/licenses/` 同步打包
- 书面要约：通过仓库 Issue 联系，提供至少三年有效的对应源码获取方式

## 可复现构建
`native/gopeed/build.ps1` 固定 Go 1.24.11、NDK 27.2.12479018、Gopeed v1.9.3；
`native/aria2/fetch.ps1` 固定 devgianlu release v1.35.0 并校验大小/SHA-256。
