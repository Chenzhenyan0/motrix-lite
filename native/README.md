# 原生下载内核构建与许可证说明

本目录包含两个 GPL 原生内核的构建/获取说明与脚本，**不包含二进制本体**（被 .gitignore 忽略）。
发布 APK 时这些内核会被打包；源码仓库保持轻量，二进制从脚本生成/校验。

| 内核 | 许可证 | 产物 | 构建脚本 |
| --- | --- | --- | --- |
| Gopeed v1.9.3 | GPL-3.0 | `app/libs/libgopeed.aar`（gomobile，arm64） | `native/gopeed/build.ps1` |
| aria2 1.37.0 | GPL-2.0-or-later（OpenSSL 例外） | `app/src/main/jniLibs/arm64-v8a/libaria2c.so` | `native/aria2/fetch.ps1`（预编译）或 devgianlu/aria2-android 的 build_all.sh |

## 许可证与源码获取
- 本应用整体按 **GPLv3** 分发（见仓库根 LICENSE）。
- Gopeed 对应源码：https://github.com/GopeedLab/gopeed/tree/v1.9.3
- aria2 对应源码：https://github.com/aria2/aria2/tree/release-1.37.0
- 构建脚本与依赖清单：本目录 + `licenses/` + Release 源码归档
- 许可证文本：`licenses/`；APK 内 `assets/licenses/` 同步打包
- 书面要约：通过仓库 Issue 联系，提供至少三年有效的对应源码获取方式

## 可复现构建
`native/gopeed/build.ps1` 固定 Go 1.24.11、NDK 27.2.12479018、Gopeed v1.9.3；
`native/aria2/fetch.ps1` 固定 devgianlu release v1.37.0 并校验大小/SHA-256。