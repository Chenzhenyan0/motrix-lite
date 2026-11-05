# aria2 二进制集成说明

## 来源与许可证
- 二进制：devgianlu/aria2-android release `v1.37.0` 的 `aria2c-arm64-v8a`（69,107,592 字节）
- aria2 许可证：GPL-2.0-or-later；仓库另有 LICENSE.OpenSSL 历史例外文本（针对旧 OpenSSL/SSLeay）
- 构建脚本与依赖源码：https://github.com/devgianlu/aria2-android
- aria2 源码：https://github.com/aria2/aria2/tree/release-1.37.0

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
  `libaria2c.so --enable-rpc --rpc-secret ... --dir ... --continue=true ...`
- JSON-RPC 调用见 `Aria2RpcClient`；
- 进程退出时清理 gid，下次按 `continue=true` 重新 addUri（依赖 `.aria2` 控制文件续传）；
- 服务停止/任务结束会 `aria2.shutdown` 并 `waitFor`，避免僵尸进程。

## GPLv2 合规
- 源码、构建脚本、许可证文本见仓库 `native/`、`licenses/` 与 Release 源码归档；
- 本应用整体按 GPLv3 发布；
- 第三方静态链接库（实际为 OpenSSL 3.0.0 / Apache-2.0、libssh2 1.9.0_DEV / BSD-3-Clause、c-ares 1.18.1 / MIT、libexpat 2.4.1 / MIT、zlib 1.2.11、libc++；devgianlu 构建使用 --without-libxml2 --without-sqlite3）的版权/许可证声明见仓库根目录 `THIRD_PARTY_NOTICES.md` 与 `licenses/`；
- 正式发布前建议使用 devgianlu 构建脚本自行复现二进制并记录 SHA-256；注意预编译版本使用较旧的 OpenSSL 3.0.0 与 libssh2 1.9.0_DEV，存在已知 CVE/维护风险。