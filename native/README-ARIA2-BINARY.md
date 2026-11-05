# aria2 预编译二进制（Android arm64-v8a）来源与校验记录

本文件记录 Motrix Lite 打包的 aria2 可执行文件的来源、校验值与合规材料位置。
该二进制**不进入源码仓库**（`app/src/main/jniLibs/**` 被 `.gitignore` 忽略），
由 `native/aria2/fetch.ps1` 按本文件记录的方式获取与校验。

## 1. 二进制来源

| 项目 | 值 |
| --- | --- |
| 上游项目 | [devgianlu/aria2-android](https://github.com/devgianlu/aria2-android) |
| Release tag | `v1.37.0`（release 标题 `1.37.0`，发布于 2025-09-05T15:32:24Z） |
| Release asset 名 | `aria2c-arm64-v8a` |
| 官方下载 URL | https://github.com/devgianlu/aria2-android/releases/download/v1.37.0/aria2c-arm64-v8a |
| 实际获取 URL（经代理） | `https://gh-proxy.com/https://github.com/devgianlu/aria2-android/releases/download/v1.37.0/aria2c-arm64-v8a` |
| GitHub API 资产元数据 | https://api.github.com/repos/devgianlu/aria2-android/releases/assets/290054430 |
| 官方资产大小（API `assets[].size`） | **69,107,592 字节** |
| 下载后实测大小 | **69,107,592 字节**（与官方一致，未截断） |
| SHA-256 | `dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a` |

对应关系：`aria2` 1.37.0 由 devgianlu 的交叉编译工程用 Android NDK 构建，
静态链接 OpenSSL 3.0.0、zlib 1.2.11、c-ares 1.18.1、libexpat 2.4.1、
libssh2 1.9.0_DEV 与 libc++（NDK r23b）。各依赖的精确版本、许可证、submodule
pin 与证据见 `native/licenses/DEPENDENCY-VERSIONS.md`。

## 2. ELF 头校验（arm64-v8a）

用 Python 按 ELF64 规范解析下载文件，结果如下：

```
magic            : 7f 45 4c 46            -> \x7fELF，通过
EI_CLASS         : 0x02                   -> ELFCLASS64（64 位），通过
EI_DATA          : 0x01                   -> ELFDATA2LSB（little-endian），通过
EI_VERSION       : 0x01                   -> EV_CURRENT
EI_OSABI         : 0x00                   -> ELFOSABI_SYSV（Android 二进制通常如此）
e_type           : 0x0003                 -> ET_DYN（PIE 可执行文件），通过
e_machine        : 0x00b7                 -> EM_AARCH64（AArch64），通过
e_version        : 0x00000001
e_entry          : 0x0000000000227000
e_phoff          : 64
e_shoff          : 69,105,352
e_flags          : 0x0
e_ehsize         : 64
e_phentsize      : 56
e_phnum          : 11                     -> 含 4 个 PT_LOAD、PT_PHDR、PT_INTERP、PT_DYNAMIC、PT_GNU_* 等
e_shentsize      : 64
e_shnum          : 35
e_shstrndx       : 33
```

一致性检查：`e_shoff + e_shnum * e_shentsize = 69,105,352 + 35*64 = 69,107,592`
等于文件大小，ELF 结构完整。

节区中另有 `.note.android.ident`（Android NDK 构建标记），`.comment` 记录：
`Android (7714059, based on r416183c1) clang version 12.0.8 ... Linker: LLD 12.0.8`，
与上游 `build.yml` 使用的 NDK r23b 一致。

结论：文件是 **AArch64、64 位、小端、ET_DYN** 的 ELF 可执行文件，适用于 Android
`arm64-v8a` ABI；未在 Windows 上运行（Windows 无法执行 Android ELF）。

## 3. 打包位置

```
app/src/main/jniLibs/arm64-v8a/libaria2c.so      # 由本二进制重命名而来
```

- 重命名为 `libaria2c.so` 的原因：Android 10（API 29）起，`targetSdk >= 29` 的应用
  不能对应用私有数据目录中的文件 `exec()`；作为 native library 打包后由系统在安装时
  解压到 `applicationInfo.nativeLibraryDir`，该目录中的文件可执行。
- 该副本与下载文件逐字节一致（SHA-256 相同，见上表）。
- 工程侧需保持 `packaging.jniLibs.useLegacyPackaging = true`，确保 `.so` 被解压而不是
  从 APK 内直接映射（`native/aria2/README.md` 有说明）。

## 4. 下载方式与遇到的问题（可复现记录）

环境限制与实测：

- `github.com`（含 release 直链）被网络阻断；`api.github.com` 可访问。
- 可用代理：**`https://gh-proxy.com/`**（对 GitHub 资源返回 200）。
  `ghproxy.net`、`ghfast.top`、`github.moeyy.xyz`、`gh.llkk.cc` 均连接超时，不可用。
- `release-assets.githubusercontent.com` 可连通（经 GitHub API 的 `Accept:
  application/octet-stream` 跳转），但单连接仅约 7 KB/s，且签名 URL 约 1 小时过期，
  未采用。
- gh-proxy 单连接约 20–25 KB/s，且有概率「连接建立后 0 字节挂起」；并发过高
  （20+ 连接）会被 Cloudflare 边缘阻塞。最终采用 **10 路 HTTPS Range 分段并发 +
  短超时重试 + 断点追加**，聚合约 160 KB/s，总耗时约 14 分钟。每段 75 秒超时后
  把已到达的字节追加到段文件并从断点续传，重试直到段完整。

最终使用的下载命令（单连接等价形式）：

```powershell
# 直连被阻断，必须经代理；Schannel 吊销检查报错时加 --ssl-no-revoke
curl.exe -L --ssl-no-revoke --retry 5 --retry-all-errors --connect-timeout 30 `
  -o aria2c-arm64-v8a `
  "https://gh-proxy.com/https://github.com/devgianlu/aria2-android/releases/download/v1.37.0/aria2c-arm64-v8a"

# 校验
(Get-Item .\aria2c-arm64-v8a).Length          # 应为 69107592
(Get-FileHash .\aria2c-arm64-v8a -Algorithm SHA256).Hash.ToLower()
# 应为 dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a
```

仓库内可复现脚本：`native/aria2/fetch.ps1`（`-Mirror`、`-ExpectedSha256` 可覆盖默认值）。

## 5. GPLv2 合规材料位置

aria2 以 **GNU GPL v2** 发布；devgianlu 产出的二进制为 aria2 的衍生作品，随 APK
分发时必须同时提供对应源码与许可证文本。本目录已归档：

| 文件 | 内容 |
| --- | --- |
| `native/licenses/aria2-COPYING` | aria2 1.37.0 官方 `COPYING`（GPLv2 全文，18,092 字节） |
| `native/licenses/aria2-LICENSE.OpenSSL` | aria2 1.37.0 仓库根目录 `LICENSE.OpenSSL`（OpenSSL 链接例外 + OpenSSL/SSLeay 许可证全文，6,968 字节） |
| `native/licenses/aria2-OpenSSL-Exception.txt` | 上述例外的摘录与说明（aria2 的 `COPYING` 本身**不含** OpenSSL 例外，例外在独立的 `LICENSE.OpenSSL` 中） |
| `native/licenses/Gopeed-LICENSE` | Gopeed v1.9.3 的 `LICENSE`（GPL-3.0 全文，35,149 字节，应用整体按 GPLv3 分发） |
| `native/licenses/devgianlu-aria2-android-LICENSE` | devgianlu/aria2-android 的许可证状态说明：该仓库**未声明许可证**（GitHub API `license: null`，`/license` 返回 404，根目录无 LICENSE 文件），仅提供构建脚本 |
| `native/licenses/DEPENDENCY-VERSIONS.md` | 二进制中静态链接的第三方库精确版本、许可证、submodule pin 与证据（OpenSSL 3.0.0、zlib 1.2.11、c-ares 1.18.1、libexpat 2.4.1、libssh2 1.9.0_DEV、libc++） |
| `native/licenses/OpenSSL-3.0.0-Apache-2.0.txt` | OpenSSL 3.0.0 的 Apache-2.0 许可证全文（修正：旧文档误写为 OpenSSL 1.1.1 + OpenSSL/SSLeay license） |
| `native/licenses/OpenSSL-3.0.0-COPYRIGHT.txt` | OpenSSL 版权与归属声明、版本证据 |
| `native/licenses/zlib-1.2.11-zlib-license.txt` | zlib 1.2.11 许可证（自 zlib.h 头部摘录） |
| `native/licenses/c-ares-1.18.1-MIT.txt` | c-ares 1.18.1 MIT 许可证 |
| `native/licenses/libexpat-2.4.1-MIT.txt` | libexpat 2.4.1 MIT 许可证 |
| `native/licenses/libssh2-1.9.0-BSD-3-Clause.txt` | libssh2（1.9.0_DEV）BSD-3-Clause 许可证 |
| `native/sources/aria2-1.37.0.tar.gz` | aria2 `release-1.37.0` 官方源码归档（GitHub tag archive，4,186,236 字节） |
| `native/sources/devgianlu-build-files.zip` | devgianlu/aria2-android `master` 的构建脚本与说明（README、build_all.sh、build_aria2.sh、build_openssl.sh、build_libssh2.sh、androidbuildlib、.gitmodules、.github/workflows/build.yml、许可说明、来源清单） |
| `native/sources/SHA256SUMS.txt` | 上述所有源码/许可证文件的 SHA-256 清单 |

> **许可证事实修正**：二进制静态链接的是 **OpenSSL 3.0.0（Apache-2.0）**，并非
> OpenSSL 1.1.1。aria2 的 `LICENSE.OpenSSL` 例外文本只覆盖旧 OpenSSL/SSLeay
> 双许可证版本；由于 aria2 是 GPL-2.0-or-later、整包按 GPLv3 分发，GPLv3 与
> Apache-2.0 兼容，因此必须随附 Apache-2.0 全文与 OpenSSL 版权声明（见上表）。
> 详细证据与推论见 `native/licenses/DEPENDENCY-VERSIONS.md`。

源码获取地址（与归档对应）：

- aria2 1.37.0：https://github.com/aria2/aria2/tree/release-1.37.0
  （归档：`https://gh-proxy.com/https://github.com/aria2/aria2/archive/refs/tags/release-1.37.0.tar.gz`）
- devgianlu/aria2-android：https://github.com/devgianlu/aria2-android/tree/master

> GPLv2 的「对应源码」还应包含用于产生该二进制的构建脚本；本目录的
> `devgianlu-build-files.zip` 即为上游构建脚本快照。

## 6. 正式发布前建议

1. **建议自行复现构建**：使用 Android NDK r23 与
   `native/sources/devgianlu-build-files.zip` 中的 `build_all.sh` 重新编译
   aria2 1.37.0，比对本 README 记录的 SHA-256，以降低供应链风险。
   上游 README 明确要求 `git clone --recurse-submodules` 并设置
   `ANDROID_NDK_HOME`（NDK r23）。
2. 发布 APK 时确认 `app/src/main/jniLibs/arm64-v8a/libaria2c.so` 的 SHA-256 与上表一致。
3. 首次使用前可在 Android 设备上执行 `libaria2c.so --version` 冒烟验证（本仓库环境为
   Windows，无法运行 Android ELF）。
4. GPLv2 要求提供完整对应源码；除本目录归档外，Release 页面也应附上源码归档与
   `SHA256SUMS.txt`，并保留至少三年有效的书面要约。
5. **供应链安全**：该二进制内置的 OpenSSL 为 3.0.0（2021-09 发布），后续 3.0.x
   修复了多个 CVE（如 CVE-2022-0778、CVE-2022-3602/3786 等）。正式发布前建议
   用更新的 OpenSSL 3.0.x 重新构建、更新 SHA-256，或在发布说明中披露时点。
   另外 libssh2 使用的是 `1.9.0_DEV` 开发快照（不是正式发行版），复现构建时应按
   `DEPENDENCY-VERSIONS.md` 记录的 submodule commit 检出。
