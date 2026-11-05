# libaria2c.so 静态链接依赖清单（精确版本与许可证）

本文件记录 devgianlu/aria2-android `v1.37.0` 产物 `aria2c-arm64-v8a`
（重命名为 `libaria2c.so`）中静态链接的第三方库版本、许可证与证据。
构建依据：该 tag 的 git tree 中固定的 submodule commit（GitHub Actions
`build.yml` 使用 `actions/checkout@v2` + `submodules: true` 构建）。

| 组件 | 固定版本 | 许可证 | submodule pin（v1.37.0 git tree） | 证据 |
| --- | --- | --- | --- | --- |
| aria2 | 1.37.0 | GPL-2.0-or-later + OpenSSL 例外 | `02f2d0d8472b3c38c29b4dba8c75ebd5fdd2899a` | 源码 `configure.ac` AC_INIT、二进制字符串 `aria2/1.37.0` |
| OpenSSL | **3.0.0**（2021-09-07） | **Apache-2.0** | `89cd17a031e022211684eb7eb41190cf1910f9fa` | 该 commit `VERSION.dat`：MAJOR=3 MINOR=0 PATCH=0；二进制含 `OSSL_PROVIDER_load`、`OSSL_LIB_CTX_*`、`EVP_MD_fetch` |
| zlib | 1.2.11 | zlib license | `cacf7f1d4e3d44d871b605da3b647f07d718623f` | 该 commit `zlib.h`：`#define ZLIB_VERSION "1.2.11"` |
| c-ares | 1.18.1 | MIT | `2aa086f822aad5017a6f2061ef656f237a62d0ed` | 该 commit `ares_version.h`：`ARES_VERSION_STR "1.18.1"`；二进制字符串 `c-ares/1.18.1` |
| libexpat | 2.4.1 | MIT | `a28238bdeebc087071777001245df1876a11f5ee` | 该 commit `expat/lib/expat.h`：XML_MAJOR 2 / MINOR 4 / MICRO 1；二进制字符串 `expat_2.4.1` |
| libssh2 | 1.9.0_DEV | BSD-3-Clause | `635caa90787220ac3773c1d5ba11f1236c22eae8` | 该 commit `include/libssh2.h`：`LIBSSH2_VERSION "1.9.0_DEV"`；二进制字符串 `libssh2/1.9.0_DEV` |
| libc++（NDK r23b 静态） | clang/LLVM 12.0.8 | Apache-2.0 WITH LLVM-exception | NDK r23b 工具链 | 二进制 `.comment`：`Android (7714059, ...) clang version 12.0.8 ... Linker: LLD 12.0.8`；`.note.android.ident` section |

许可证文本位置（native/licenses/）：

- `OpenSSL-3.0.0-Apache-2.0.txt`（Apache-2.0 全文）
- `OpenSSL-3.0.0-COPYRIGHT.txt`（OpenSSL 版权与归属说明）
- `zlib-1.2.11-zlib-license.txt`
- `c-ares-1.18.1-MIT.txt`
- `libexpat-2.4.1-MIT.txt`
- `libssh2-1.9.0-BSD-3-Clause.txt`
- `aria2-COPYING`（GPLv2 全文）
- `aria2-LICENSE.OpenSSL`（aria2 旧的 OpenSSL 链接例外原文，供归档）
- `aria2-OpenSSL-Exception.txt`（例外说明 & 与 OpenSSL 3.0.0 的关系）

## 重要合规结论

1. **不是 OpenSSL 1.1.1**：仓库旧文档（`THIRD_PARTY_NOTICES.md`、
   `native/aria2/README.md`）写的 “OpenSSL 1.1.1 / OpenSSL License + SSLeay
   License” 与事实不符。实际静态链接的是 **OpenSSL 3.0.0，Apache-2.0**。
2. aria2 的 `LICENSE.OpenSSL` 例外文本只覆盖与旧 OpenSSL/SSLeay 双许可证
   “identical” 的版本，并不覆盖 Apache-2.0 的 OpenSSL 3.0.0。由于 aria2 是
   **GPL-2.0-or-later**（942 个源码文件均带 “either version 2 ... or any later
   version”），而整个 App 以 **GPLv3** 分发，GPLv3 与 Apache-2.0 兼容，
   因此组合分发在法律上可行；须随附 Apache-2.0 全文与 OpenSSL 版权声明。
3. **安全提示**：OpenSSL 3.0.0 发布于 2021-09，后续 3.0.x 修复了多个 CVE
   （如 CVE-2022-0778、CVE-2022-3786/3602 等）。正式发布前建议用更新版本的
   OpenSSL 重新构建，或向用户说明供应链时点。
4. libssh2 使用的 pin 是 1.9.0_DEV（开发分支快照），并非正式发行版；复现构建时
   需按上述 commit 检出，不建议升级（会改变二进制哈希）。
