# aria2 二进制集成说明

> 当前发布版使用 **aria2 1.35.0**（不是 1.37.0）。原因见下方“版本选择”。

## 来源与校验
- 二进制：devgianlu/aria2-android release `v1.35.0` 的 `aria2c-arm64-v8a`
- 大小：**5,872,304 字节**
- SHA-256：`88b8b5feec7e4e7392f129fed4cf61f52a88fdc5dfe1e1464ed467fb41bd1a7f`
- ELF：ELF64 / little-endian / `ET_DYN` / AArch64；仅 NEEDED `libm.so`、`libdl.so`、`libc.so`
- 运行版本（真机 `--version`）：`aria2 version 1.35.0`
- 静态依赖：zlib 1.2.9、expat 2.2.9、OpenSSL **1.1.1d**、c-ares 1.14.0、libssh2 1.9.0_DEV；
  C++ 运行库为 clang 8.0.7（Android NDK 工具链）静态链接
- 构建文件快照：`native/sources/devgianlu-build-files-v1.35.0.zip`（devgianlu v1.35.0 tag）
- 源码：`native/sources/aria2-1.35.0.tar.gz`（aria2 `release-1.35.0`）

## 版本选择（v1.1.1 重要变更）
最初打包的是 devgianlu **v1.37.0**（69,107,592 B，OpenSSL 3.0.0 静态链接）。在 Redmi Turbo 4 Pro
（25053RT47C，Android 16 / HyperOS OS3）真机上启动即失败：

```
Exception: [Platform.cc:125] errorCode=1 OSSL_PROVIDER_load 'legacy' failed.
```

该预编译二进制缺少 OpenSSL 3 的 `legacy` provider 模块，进程立即退出（aria2 code 7），
JSON-RPC 永远起不来。改用 devgianlu **v1.35.0**（5.87 MB，OpenSSL 1.1.1d）后，真机实测
aria2 子进程、RPC 鉴权、下载、暂停/恢复全部正常。

**安全权衡**：OpenSSL 1.1.1d（2019-09）与 aria2 1.35.0（2019-10）均已停止维护，存在已知 CVE；
正式生产建议用受支持的 OpenSSL / aria2 版本按 devgianlu 构建脚本自行复现（并同步更新
`licenses/DEPENDENCY-VERSIONS.md` 与 SHA-256），或迁移到其它维护中的传输内核。

## 为什么不是 assets → 私有目录 → exec
Android 10（API 29）起，targetSdk >= 29 的应用不能 `exec()` 应用私有数据目录中的文件（EACCES）。
因此本工程把二进制重命名为 `libaria2c.so` 放入 `app/src/main/jniLibs/arm64-v8a/`，
由系统安装时解压到 `applicationInfo.nativeLibraryDir`，再从该目录启动子进程。
`app/build.gradle.kts` 已设置 `packaging.jniLibs.useLegacyPackaging = true` 以确保解压。

## 获取方式
```powershell
powershell -ExecutionPolicy Bypass -File native/aria2/fetch.ps1
```
执行后生成 `app/src/main/jniLibs/arm64-v8a/libaria2c.so`（该文件被 .gitignore 忽略，不进入源码仓库）。

## 运行时
- 由 `Aria2EngineController` 在存在 aria2 任务时启动：
  `libaria2c.so --enable-rpc=true --rpc-listen-port=<port> --rpc-secret=<secret> --dir=<staging> ...`
- **JSON-RPC 鉴权参数必须是 `token:<secret>`**（仅传 secret 会被 400 拒绝）；见 `Aria2RpcClient`。
- 进程退出时清理 gid，下次按 `continue=true` 重新 addUri（依赖 `.aria2` 控制文件续传）；
- 服务停止/任务结束会 `aria2.shutdown` 并 `waitFor`，真机验证无残留 aria2 进程；
- **未使用 `--stop-with-process`**：在 Android 16 真机上该选项会导致 aria2 子进程提前以 code 7
  退出（与父进程可见性/SELinux 有关），故移除；由前台服务生命周期负责回收子进程。

## GPLv2 合规
- 源码、构建脚本、许可证文本见仓库 `native/`、`licenses/` 与 Release 源码归档；
- 本应用整体按 GPLv3 发布；
- 第三方静态链接库（OpenSSL 1.1.1d / OpenSSL+SSLeay 双许可证、libssh2 1.9.0_DEV / BSD-3-Clause、
  c-ares 1.14.0 / MIT、expat 2.2.9 / MIT、zlib 1.2.9、libc++ / Apache-2.0 WITH LLVM-exception）
  的版权/许可证文本见 `licenses/`；`aria2-LICENSE.OpenSSL` 为 aria2 的 OpenSSL 链接例外原文；
- 建议发布前按 devgianlu 构建脚本自行复现并记录 SHA-256。
