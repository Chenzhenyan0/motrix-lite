# libaria2c.so 静态链接依赖清单（精确版本与许可证）

本文件记录 devgianlu/aria2-android `v1.35.0` 产物 `aria2c-arm64-v8a`
（重命名为 `libaria2c.so`，5,872,304 字节，SHA-256 `88b8b5fe…`）中静态链接的
第三方库版本、许可证与证据。构建依据：该 tag 的 git tree 中固定的 submodule commit。

## 版本清单

| 组件 | 固定版本 | 许可证 | devgianlu v1.35.0 submodule pin | 证据 |
| --- | --- | --- | --- | --- |
| aria2 | **1.35.0**（2019-10） | GPL-2.0-or-later（含 OpenSSL 链接例外） | aria2 commit `7f79854b2ed6ef4978a8526831034b34b1fb3871` | 真机 `--version`：`aria2 version 1.35.0`；源码 `configure.ac` AC_INIT 1.35.0 |
| OpenSSL | **1.1.1d**（2019-09-10） | OpenSSL License + SSLeay License（双许可证） | `894da2fb7ed5d314ee5c2fc9fd2d9b8b74111596` | 真机 `--version`：`Libraries: … OpenSSL/1.1.1d`；二进制字符串 `OpenSSL 1.1.1d  10 Sep 2019` |
| zlib | **1.2.9** | zlib license | `2fa463bacfff79181df1a5270fb67cc679a53e71` | 真机 `--version`：`zlib/1.2.9`；二进制字符串 `deflate 1.2.9` |
| libexpat | **2.2.9** | MIT | `a7bc26b69768f7fb24f0c7976fae24b157b85b13` | 真机 `--version`：`expat/2.2.9` |
| c-ares | **1.14.0** | MIT | `e982924acee7f7313b4baa4ee5ec000c5e373c30` | 真机 `--version`：`c-ares/1.14.0`；二进制字符串 `c-ares/1.14.0` |
| libssh2 | **1.9.0_DEV** | BSD-3-Clause | `42d37aa63129a1b2644bf6495198923534322d64` | 真机 `--version`：`libssh2/1.9.0_DEV` |
| libc++ | clang 8.0.7（Android NDK 工具链静态） | Apache-2.0 WITH LLVM-exception | 构建工具链 | 真机 `--version`：`Compiler: clang 8.0.7 … targeting aarch64-unknown-linux-android` |

> aria2 1.35.0 的其他固定依赖（构建脚本引用）：`aria2` 7f79854、`openssl` 894da2f、
> `zlib` 2fa463b、`c-ares` e982924、`libexpat` a7bc26b、`libssh2` 42d37aa。完整构建文件快照见
> `native/sources/devgianlu-build-files-v1.35.0.zip`。

## 许可证文本位置

仓库根 `licenses/`（APK 内 `assets/licenses/` 同步）：

- `OpenSSL-1.1.1d-LICENSE.txt`（OpenSSL + SSLeay 双许可证全文）
- `zlib-1.2.9-zlib-license.txt`
- `c-ares-1.14.0-MIT.txt`
- `libexpat-2.2.9-MIT.txt`
- `libssh2-1.9.0_DEV-BSD-3-Clause.txt`
- `aria2-COPYING.txt`（GPLv2 全文）
- `aria2-LICENSE.OpenSSL`（aria2 的 OpenSSL 链接例外原文，适用于 OpenSSL 1.1.1d）
- `aria2-OpenSSL-Exception.txt`（例外说明）
- `Apache-2.0.txt`（Kotlin/AndroidX/libc++ 等 Apache-2.0 组件）
- `Gopeed-LICENSE.txt`（GPL-3.0）

## 合规结论

1. **OpenSSL 1.1.1d 适用 aria2 的 `LICENSE.OpenSSL` 例外**：该例外文本覆盖与 OpenSSL/SSLeay
   双许可证一致的版本，OpenSSL 1.1.1d 正是该许可证；因此 GPLv2-or-later 的 aria2 与
   OpenSSL 1.1.1d 的组合分发成立，例外文本已随附。
2. 整个 App（含 Gopeed）按 **GPLv3** 分发；GPLv3 与 Apache-2.0（libc++）、MIT、BSD-3-Clause
   兼容。
3. **安全提示**：OpenSSL 1.1.1d 与 aria2 1.35.0 均已停止维护（2019 年），存在多个后续 CVE；
   预编译版本还可能受供应链影响。正式分发前建议用受支持的 OpenSSL（1.1.1w 或 3.x）和
   新版 aria2 按 `native/aria2/README.md` 的构建脚本自行复现，并同步更新本文件与哈希。
4. libssh2 使用 1.9.0_DEV 开发快照；复现构建时须按上述 commit 检出。
