# 许可证与合规说明（COMPLIANCE）

本文说明 Motrix Lite 1.1.0 在开源许可证、对应源码、商标、权限与分发层面的立场。**本文不构成法律意见**；正式分发前应自行复核相关许可证原文，必要时咨询法务。

- 项目整体许可证：**GNU GPLv3**，见 [LICENSE](../LICENSE)
- 当前版本：1.1.0（versionCode 2）
- 第三方组件清单：见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
- 引擎许可证论证：见 [ENGINE_LICENSE_ANALYSIS.md](ENGINE_LICENSE_ANALYSIS.md)
- 应用标识：Motrix Lite，包名 `com.motrixlite.downloader`（包名不变）

---

## 一、结论摘要

| 项目 | 许可证 | 是否随 APK 分发 | 本项目的义务 |
| --- | --- | --- | --- |
| 本项目代码（App、集成代码、构建脚本） | **GPLv3**（整体发布物） | 是（源码 + 二进制） | 提供完整对应源码、GPLv3 全文、修改说明；不得附加额外限制 |
| Gopeed v1.9.3 | **GPL-3.0** | 是（`libgopeed.aar`，与 App 同进程链接） | 按 GPLv3 提供对应源码、许可证全文；与 App 构成结合作品 |
| aria2 1.37.0 | **GPL-2.0-or-later** | 是（`libaria2c.so`，子进程 + JSON-RPC） | 按 GPLv2 提供对应源码或书面要约、GPLv2 全文、修改说明；整体发布物按 GPLv3 |
| OpenSSL 3.0.0（aria2 静态链接，实际使用） | **Apache-2.0** | 是（静态链接进 aria2 二进制） | 随附 Apache-2.0 全文与 OpenSSL 版权声明；GPLv3 与 Apache-2.0 兼容，组合分发成立 |
| aria2 `LICENSE.OpenSSL`（历史例外文本） | 旧 OpenSSL/SSLeay 双许可证 | 随归档提供 | 仅作历史文本保留说明；**不用于解释本二进制的 OpenSSL 3.0.0** |
| libssh2 1.9.0_DEV / c-ares 1.18.1 / libexpat 2.4.1 / zlib 1.2.11 / libc++（NDK r23b） | BSD-3 / MIT / MIT / zlib / Apache-2.0 WITH LLVM-exception | 是（静态链接进 aria2 二进制） | 保留各自版权与许可声明 |
| Go runtime（Gopeed AAR 内） | BSD-3-Clause | 是 | 保留版权与许可声明 |
| Motrix（桌面版） | MIT | 否 | 仅参考思路，无代码/资源复用，不产生额外义务 |
| Kotlin / 协程 / OkHttp / Okio | Apache-2.0 | 是 | 保留版权与许可证声明、NOTICE（如有） |
| AndroidX / Jetpack Compose / Material3 / material-icons / documentfile | Apache-2.0 | 是 | 同上 |
| JUnit / MockWebServer / coroutines-test / org.json | EPL-1.0 / Apache-2.0 / Public Domain | 否（仅测试/构建） | 无 |

**一句话概括**：本 APK 同时包含 Gopeed（GPL-3.0，AAR 链接）与 aria2（GPL-2.0-or-later，子进程），**整体必须按 GPLv3 开源分发，闭源分发不可行**。任何"可以通过独立进程/RPC/加壳/动态加载规避 GPL、对包含两者的 APK 闭源分发"的说法都不成立。

---

## 二、事实基线与集成形态

| 组件 | 版本 | 许可证（核实方式） | 集成形态 | 是否构成结合作品 |
| --- | --- | --- | --- | --- |
| Gopeed | `v1.9.3` | GPL-3.0（GitHub 仓库声明） | gomobile 生成 `libgopeed.aar`，JNI 同进程调用 | **是**（链接，不是进程隔离） |
| aria2 | `1.37.0`（devgianlu/aria2-android 预编译 arm64 二进制，69,107,592 字节） | GPL-2.0-or-later（`COPYING`；仓库另有历史 `LICENSE.OpenSSL` 例外文本）。**二进制实际静态链接 OpenSSL 3.0.0（Apache-2.0）**，该例外不覆盖此组合 | 可执行文件打包为 `jniLibs/arm64-v8a/libaria2c.so`，运行时子进程 + JSON-RPC | 不必然构成结合作品，但**aria2 自身的 GPLv2 义务独立成立** |
| Motrix Lite（本项目） | 1.1.0（仓库根 `LICENSE` 为 GPLv3 全文） | **GPLv3** | App 本体 | — |

> 说明：Gopeed 官方发布物（桌面/Android App）使用 GPL-3.0；gomobile 生成的 AAR 只是打包形式变化，不改变许可证。aria2 的 `COPYING` 为 GPLv2 标准文本；`LICENSE.OpenSSL` 是历史 OpenSSL 链接例外（针对旧 OpenSSL/SSLeay），**不适用于 OpenSSL 3.0.0**。整包按 GPLv3 分发，GPLv3 与 Apache-2.0 兼容，因此 aria2 + OpenSSL 3.0.0 的组合成立；分发时需附 Apache-2.0 全文与 OpenSSL 版权声明（见 `licenses/DEPENDENCY-VERSIONS.md`）。

---

## 三、为什么整体必须是 GPLv3

### 3.1 Gopeed AAR = 结合作品

- gomobile 把 Go 代码编译为共享库（`.so`），通过 JNI 与 Kotlin/Java 代码在**同一 Android 进程**内直接调用，不是通过独立可执行程序、独立进程、socket 或公开协议与 App 通信。
- 这符合 GPL 对"基于该程序的作品（work based on the Program）"与"结合作品"的典型理解：App 与 Gopeed 形成单一程序整体。
- 因此**分发包含 Gopeed 的 APK 时，App 的完整对应源码必须按 GPLv3 提供**，且不得附加额外限制。不能只公开 Gopeed 源码而把 App 闭源。

### 3.2 aria2 子进程不改变整体结论

- GPL FAQ 对"独立程序通过管道/命令行通信"通常承认可作为独立作品处理（mere aggregation），但 APK 把 aria2 二进制与 App 一起打包、由 App 专门调用并依赖其输出，是否构成"结合作品"取决于具体事实与法域解释。
- **即使按"独立程序/聚合"处理，aria2 本身仍受 GPLv2 约束**：必须向接收者提供 aria2 的对应源码（或有效书面要约）、GPLv2 许可证文本与修改说明。
- 在双内核方案中，App 已因 Gopeed 成为 GPLv3 作品，aria2 的聚合争议只影响 aria2 自身义务的适用方式，**不影响"整体必须开源"的结论**。

### 3.3 GPL-2.0-or-later 与 GPLv3 兼容

- aria2 为 `GPL-2.0-or-later`（`COPYING` 原文允许选择 GPLv3），因此可与 Gopeed 一起组合进同一 GPLv3 发布物。
- 若 aria2 是 `GPL-2.0-only`，则与 GPLv3 不兼容，会产生直接冲突；本项目使用的版本不是这种情况。
- aria2 的 `LICENSE.OpenSSL` 例外是历史文本，只针对旧 OpenSSL/SSLeay 双许可证，**不覆盖本二进制实际静态链接的 OpenSSL 3.0.0（Apache-2.0）**；该组合的合规性来自"整包按 GPLv3 分发、GPLv3 与 Apache-2.0 兼容"，同时随附 Apache-2.0 全文与 OpenSSL 版权声明。

### 3.4 GPLv3 的主要义务（本项目已覆盖）

1. **Corresponding Source**：随二进制提供（或书面承诺提供）生成、安装、运行目标代码所需的全部源码，包括构建脚本、Gopeed/aria2 源码及静态链接依赖的源码获取方式。
2. **许可证文本与版权声明**：随 APK 附 GPLv3 全文、各依赖许可证、版权声明（`assets/licenses/`）。
3. **不得附加额外限制**：不能限制接收者再分发/修改；分发条款（含商店条款）不得与 GPL 冲突。
4. **书面要约**（物理分发场景）：至少 3 年有效的源码获取要约。
5. **专利授权与终止条款**：GPLv3 自带专利许可与终止机制。
6. **修改说明**：若修改 Gopeed/aria2 源码，需标明修改与日期（本项目未修改上游源码；如后续修改需补充）。
7. **Installation Information / 反 Tivoization**：主要针对 User Product 固件锁定；普通 App 商店分发通常不触发，企业预装/定制 ROM 需单独评估。

---

## 四、对应源码（Corresponding Source）清单与位置

| 内容 | 位置 / 获取方式 |
| --- | --- |
| 本项目全部源码（App、引擎集成代码、Compose UI、构建脚本、资源） | 公开仓库：https://github.com/Chenzhenyan0/motrix-lite ，发布时打 tag（如 `v1.1.0`） |
| Gopeed v1.9.3 源码 + `go.mod`/`go.sum` + gomobile 构建脚本 | 上游 tag `v1.9.3`；构建脚本 `native/gopeed/build.ps1`（Go 1.24.11 / NDK 27.2.12479018）；建议在 Release 源码归档中附 `gopeed-v1.9.3-src` |
| aria2 1.37.0 源码 + 构建参考脚本 | 上游 tag `release-1.37.0`；构建参考 devgianlu/aria2-android（构建文件快照见 Release 源码归档的 `build-scripts`） |
| aria2 静态链接依赖（OpenSSL 3.0.0、libssh2 1.9.0_DEV、c-ares 1.18.1、libexpat 2.4.1、zlib 1.2.11、libc++ NDK r23b） | 各自上游源码；精确版本/commit 见 `licenses/DEPENDENCY-VERSIONS.md`；Release 源码归档附版本清单或源码包 |
| Gopeed Go 模块依赖清单 | 构建时 `go version -m`、`go list -m all`、`go.sum`，随 Release 源码归档提供 |
| APK 内许可证与源码要约 | `app/src/main/assets/licenses/`（GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 3.0.0 Apache-2.0 全文+版权、各静态依赖许可证、DEPENDENCY-VERSIONS、SOURCE-OFFER、THIRD_PARTY_NOTICES） |
| 仓库许可证文本 | `licenses/`、`native/licenses/` |
| 可复现构建 | Gradle wrapper（8.9）、`native/gopeed/build.ps1`、`native/aria2/fetch.ps1`、`docs/BUILD_AND_SIGN.md` |

**源码获取方式（本项目同时提供）**：

1. 公开 Git 仓库（含全部构建脚本），发布时打 tag；
2. Release 页面附源码归档：`gopeed-v1.9.3-src`、`aria2-1.37.0-src`、`build-scripts`、`licenses`（文件名以实际发布为准）；
3. 应用内「设置 → 关于 → 开源许可 / 源码获取」显示上述链接与书面要约；
4. 对物理分发（如预装设备）另附至少 3 年有效的书面要约，并可按要求提供物理介质副本（仅收取介质成本）。

---

## 五、书面要约（Written Offer）

`licenses/SOURCE-OFFER.txt` 与 APK `assets/licenses/SOURCE-OFFER.txt` 已包含以下要约内容：

- 本应用 1.1.0 包含 Gopeed v1.9.3（GPL-3.0）与 aria2 1.37.0（GPL-2.0-or-later；静态链接 OpenSSL 3.0.0 Apache-2.0 及 zlib/c-ares/libexpat/libssh2/libc++）；
- 对应源码地址：本项目仓库、Gopeed/aria2 上游 tag、Release 源码归档；
- 通过仓库 Issue 联系获取完整对应源码；提供至少三年有效的书面要约；
- 可按要求提供物理介质副本（仅收取介质成本）。

> 注意：GPL 的书面要约义务并不因"只提供网络下载"而消失；对于通过物理介质（U 盘、预装设备、光盘）分发的场景，必须随附要约。网络分发时提供可访问的对应源码下载地址也是合规的必要条件之一。

---

## 六、许可证文本与 APK 内置位置

APK 内 `assets/licenses/` 随版本打包（用户可在源码仓库核对）：

| 文件 | 内容 |
| --- | --- |
| `GPL-3.0.txt` | GNU GPLv3 全文（本项目整体许可证） |
| `GPL-2.0.txt` | GNU GPLv2 全文（aria2 基础许可证） |
| `aria2-COPYING.txt` | aria2 `COPYING` 原文（GPLv2） |
| `Gopeed-LICENSE.txt` | Gopeed `LICENSE` 原文（GPL-3.0） |
| `aria2-LICENSE.OpenSSL` + `aria2-OpenSSL-Exception.txt` | aria2 历史 OpenSSL 链接例外全文 + 说明（注明该例外针对旧 OpenSSL/SSLeay，不覆盖本二进制的 OpenSSL 3.0.0） |
| `OpenSSL-3.0.0-Apache-2.0.txt` | OpenSSL 3.0.0 的 Apache-2.0 全文（本二进制实际使用的 OpenSSL 许可证） |
| `OpenSSL-3.0.0-COPYRIGHT.txt` | OpenSSL 3.0.0 版权与归属声明 |
| `zlib-1.2.11-zlib-license.txt`、`c-ares-1.18.1-MIT.txt`、`libexpat-2.4.1-MIT.txt`、`libssh2-1.9.0-BSD-3-Clause.txt` | aria2 静态链接依赖的许可证原文 |
| `DEPENDENCY-VERSIONS.md` | 静态依赖精确版本、submodule commit 与证据 |
| `devgianlu-aria2-android-LICENSE` | 二进制构建仓库的许可状态核查记录 |
| `SOURCE-OFFER.txt` | 对应源码获取说明与书面要约 |
| `THIRD_PARTY_NOTICES.md` | 第三方组件与许可证清单（APK `assets/licenses/` 内同步打包） |

应用内「设置 → 关于」会显示当前引擎打包状态（Gopeed/aria2 是否可用）与「开源许可 / 源码获取」对话框，包含 GPLv3 声明、组件列表、源码 URL、许可证位置与书面要约说明。

---

## 七、不得添加额外限制

- 分发 GPLv3 代码（本项目衍生作品及 Gopeed 部分）时，不得限制接收者再分发、修改或反向工程（GPL 本身要求的"反向工程"许可是为调试修改所必需）。
- 若通过应用商店分发，商店的 DRM/条款不得与 GPL 冲突（例如禁止用户提取 GPL 组件或再分发）。
- 若未来增加 EULA/隐私政策/使用条款，需复核其中"禁止再分发""禁止反向工程""源码保密"等条款是否会构成 GPL 禁止的额外限制。
- 本项目**没有**、也不会声称"独立进程隔离可以规避 GPL"；相反，任何包含 Gopeed AAR 与 aria2 的 APK 都按 GPLv3 开源。

---

## 八、aria2 二进制、OpenSSL 3.0.0 与供应链

### 8.1 OpenSSL 事实修正与合规依据

- **事实**：aria2 二进制通过 `--with-openssl` 静态链接了 **OpenSSL 3.0.0（Apache-2.0）**，不是旧文档所称的 OpenSSL 1.1.1 / SSLeay 双许可证。证据：devgianlu v1.37.0 的 openssl submodule pin `89cd17a031e022211684eb7eb41190cf1910f9fa`，其 `VERSION.dat` 为 3.0.0，二进制包含 `OSSL_PROVIDER_load`、`OSSL_LIB_CTX_*`、`EVP_MD_fetch` 等 OpenSSL 3 API。
- aria2 仓库根级 `LICENSE.OpenSSL` 是**历史链接例外文本**，仅覆盖与旧 OpenSSL/SSLeay 双许可证 identical 的版本，**不覆盖 Apache-2.0 的 OpenSSL 3.0.0**。因此不能依赖该例外解释本二进制。
- **合规依据**：aria2 为 **GPL-2.0-or-later**（源码允许选择 GPLv3），整个 App 以 **GPLv3** 分发，而 GPLv3 与 **Apache-2.0 兼容**，故 aria2 + OpenSSL 3.0.0 的组合可以随 GPLv3 发布物分发。必须随附：`aria2-COPYING`（GPLv2）、OpenSSL 3.0.0 的 **Apache-2.0 全文**与 **OpenSSL 版权声明**，以及历史 `LICENSE.OpenSSL`（归档说明用途）。
- 本工程 `licenses/` 与 APK `assets/licenses/` 已提供 `OpenSSL-3.0.0-Apache-2.0.txt`、`OpenSSL-3.0.0-COPYRIGHT.txt`、`aria2-LICENSE.OpenSSL`、`aria2-OpenSSL-Exception.txt`（说明其历史适用范围）与 `DEPENDENCY-VERSIONS.md`。

### 8.2 二进制来源与供应链风险

- aria2 二进制来自 devgianlu/aria2-android `v1.37.0`（`aria2c-arm64-v8a`，69,107,592 字节）。该仓库**未声明 LICENSE**，其构建脚本仅提供交叉编译流程；二进制的许可义务来自 aria2 与静态链接依赖（OpenSSL 3.0.0、zlib 1.2.11、c-ares 1.18.1、libexpat 2.4.1、libssh2 1.9.0_DEV、libc++ NDK r23b）。
- **v1.1.0 实际构建记录**：aria2 二进制经 24 路 Range 分片下载获取，最终 SHA-256 `dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a`，与独立下载方核对一致；Gopeed AAR 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`（Go 1.24.11 + 自动切换的 go1.26.10 toolchain、NDK 27.2.12479018，详见 `.tools/gopeed-build/BUILD_REPORT.md`）。
- **供应链风险**：OpenSSL 3.0.0 发布于 2021-09，后续 3.0.x 修复了多个 CVE；libssh2 的 pin 为 1.9.0_DEV 开发快照而非正式发行版。正式分发前建议用更新版本 OpenSSL/libssh2 重新构建 aria2，或至少在 Release 说明中披露该供应链时点与风险（见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) KI-30）。
- 本工程 `native/aria2/fetch.ps1` 固定版本、校验大小与 SHA-256，并保留构建文件快照（`native/sources/devgianlu-build-files.zip`，该目录被 `.gitignore` 忽略，随 Release 源码归档提供）。
- **建议**：正式发布前自行按 devgianlu 构建脚本（Linux/NDK 环境）重新构建 aria2，记录编译器版本、依赖 commit、SHA-256 与 SBOM；若继续使用预编译二进制，应在 Release 说明中披露来源与哈希。
- Gopeed AAR 建议用 `native/gopeed/build.ps1` 固定 Go/NDK/Gopeed 版本重建，并保留 `go.sum` 与 `go version -m` 输出。

---

## 九、Apache-2.0 / BSD / MIT 组件义务

本项目使用的 Kotlin、OkHttp/Okio、AndroidX、Jetpack Compose、Material3、documentfile 为 Apache-2.0；Go runtime 为 BSD-3；OpenSSL 3.0.0 为 Apache-2.0（另附 OpenSSL 版权声明）；libssh2 1.9.0_DEV 为 BSD-3；c-ares 1.18.1、libexpat 2.4.1 为 MIT；zlib 1.2.11 为 zlib License；libc++（NDK r23b）为 Apache-2.0 WITH LLVM-exception；Motrix 桌面版为 MIT（仅参考）。精确版本与 submodule commit 见 `licenses/DEPENDENCY-VERSIONS.md`。

分发义务：

1. 保留版权、专利、商标与归属声明；
2. 随分发物提供许可证副本（本项目在 APK `assets/licenses/` 与仓库 `licenses/`、`native/licenses/` 提供；第三方 AAR 内的 `META-INF/AL2.0` 占位文件被 Gradle 排除，故以显式归档补偿）；
3. 若组件自带 `NOTICE` 文件，需保留其中的归属声明。

**已知偏差**：`app/build.gradle.kts` 中

```kotlin
packaging {
    resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
```

会剔除 AAR 内的许可证占位文件。1.1.0 的补偿措施：APK `assets/licenses/` 打包完整许可证文本、应用内提供「开源许可」入口、仓库提供 `THIRD_PARTY_NOTICES.md` 与 `licenses/`。面向应用商店或企业分发时，建议把 `THIRD_PARTY_NOTICES.md` 与 `licenses/` 一并随发布物提供。

---

## 十、商标与命名

- **Motrix 的名称与商标归其作者/项目所有**。本项目为**非官方、独立实现**，与 Motrix 官方无隶属、赞助或背书关系。
- 应用名 "Motrix Lite" 仅用于说明产品定位（轻量下载管理器），**不使用** Motrix 官方图标、Logo、启动图或品牌配色素材；应用图标为自绘 VectorDrawable。
- 包名 `com.motrixlite.downloader` 为本项目自有标识，未占用他人包名。
- Gopeed、aria2 名称仅用于事实描述与许可证归属，不使用其官方视觉素材。
- 若收到商标权利人的合理要求，应配合更名或调整品牌呈现。

---

## 十一、隐私与数据

| 项目 | 现状 |
| --- | --- |
| 账号体系 | 无 |
| 统计/崩溃上报 SDK | 无 |
| 广告 SDK | 无 |
| 网络请求 | 仅访问用户输入的下载 URL 及其重定向目标（内置引擎直接访问；aria2/Gopeed 在本地回环运行，下载仍由本机发起） |
| 本地数据 | `SharedPreferences`（`motrix_lite_settings`）+ `filesDir/tasks.json`（schema v2）+ 应用专属目录下的 staging 与原生引擎临时文件 |
| 剪贴板 | 仅在用户主动点击「粘贴」时读取，不后台监听 |
| 权限 | 见 [README 权限说明](../README.md#八权限说明)，**未申请任何宽泛存储权限** |

下载内容与 URL 属于用户数据，应用不做上传。卸载应用会删除内部数据、staging 与原生引擎临时目录；已发布到 `Download/MotrixLite` 的文件不会随卸载删除。

---

## 十二、内容与使用合规

- 本应用仅为通用下载工具，**不对用户下载内容的合法性负责**。用户应确保拥有相应内容的获取与使用权利，并遵守目标站点服务条款与当地法律。
- 应用默认允许 HTTP 明文下载（`android:usesCleartextTraffic="true"`），存在被中间人篡改的风险，详见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) 的 KI-13。
- 应用**不提供**任何破解、绕过付费墙、绕过 DRM 或规避访问控制的能力；不支持需要登录鉴权的链接（见 KI-02）。

---

## 十三、分发检查清单（发布前）

- [ ] 替换演示签名证书，使用正式发布密钥（见 [BUILD_AND_SIGN.md](BUILD_AND_SIGN.md)）
- [ ] `versionCode` / `versionName` 已递增（当前 2 / 1.1.0）
- [ ] 放入原生产物并验证：`app/libs/libgopeed.aar`（15,438,987 B，SHA-256 `aa064e34…dd2d7c`）、`app/src/main/jniLibs/arm64-v8a/libaria2c.so`（69,107,592 B，SHA-256 `dbfd705d…fc7389a`）均已放入并打包
- [ ] `.\gradlew.bat clean :app:testDebugUnitTest :app:assembleRelease` 全绿（v1.1.0 已通过：32/32 单测；debug 67,482,342 B / release 38,559,243 B）
- [ ] APK 内 `assets/licenses/` 含 GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 3.0.0 Apache-2.0 全文+版权声明、历史 LICENSE.OpenSSL/说明、静态依赖许可证（zlib/c-ares/libexpat/libssh2）、DEPENDENCY-VERSIONS、SOURCE-OFFER、THIRD_PARTY_NOTICES（v1.1.0 最终 rebuild 已解包核验 16 个文件）
- [ ] APK 内「设置 → 关于 → 开源许可」可打开并显示源码 URL、版本/要约
- [ ] Release 页附 APK + 源码归档（Gopeed/aria2 对应源码或获取方式、构建脚本）+ 许可证 + SHA-256
- [ ] 记录 Gopeed/aria2 版本、源码 tag/commit、Go/NDK 版本、构建参数，写入 Release 说明
- [ ] 生成并保存 SBOM（Gopeed `go.sum`、aria2 依赖版本）
- [ ] 复核分发渠道条款（Google Play / 国内商店 / 预装）不与 GPLv3 冲突
- [ ] 复核 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 在目标分发渠道的政策要求
- [ ] 复核隐私政策（若上架商店）
- [ ] **不得**在任何对外文档/宣传中声称"包含 Gopeed + aria2 的 APK 可以闭源分发"

---

## 十四、免责声明

本软件按"现状"提供，不附带任何明示或暗示担保。作者与贡献者不对因使用本软件产生的任何直接或间接损失承担责任。详见 [LICENSE](../LICENSE)（GNU GPLv3 第 15–17 条）。
