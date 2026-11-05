# 下载内核许可证分析报告（Gopeed GPL-3.0 + aria2 GPL-2.0-or-later）

- 适用工程：Motrix Lite（`com.motrixlite.downloader`，当前版本 **1.1.1 / versionCode 3**，整体已按 **GPLv3** 开源）
- 报告日期：2026-11-05；**决策状态：已由用户确认「整体 GPLv3 开源」并已实施**
- 双内核形态：同一 APK 中集成 **Gopeed v1.9.3（gomobile AAR，进程内 Go 内核）** 与 **aria2 1.35.0（arm64 可执行文件，子进程 + JSON-RPC；静态链接 OpenSSL 1.1.1d）**
- 版本历史：v1.1.0 曾使用 aria2 1.37.0（OpenSSL 3.0.0），在 Android 16 真机启动失败（`OSSL_PROVIDER_load 'legacy' failed`），已被 v1.1.1 取代（详见第 1、7 节）
- 性质：工程/合规分析，**不构成法律意见**；最终分发前应经法务或开源合规负责人确认

---

## 0. 结论摘要（TL;DR）

1. **Gopeed 的实际许可证是 GPL-3.0**（已通过 GitHub API 核实仓库 `license.spdx_id = GPL-3.0`，使用的发布 tag 为 `v1.9.3`）；**aria2 是 GPL-2.0-or-later**（其 `COPYING` 为 GPLv2 标准文本；仓库另有 OpenSSL 链接例外文件 `LICENSE.OpenSSL`，**不在 `COPYING` 内**）。
2. **v1.1.1 使用的 aria2 1.35.0 预编译二进制静态链接 OpenSSL 1.1.1d**（OpenSSL License + SSLeay License 双许可证）；该组合正落在 aria2 `LICENSE.OpenSSL` 例外覆盖范围内，因此可随 GPLv2+ 的 aria2 分发。
3. **把 Gopeed 以 AAR 形式链接进 App**，Go 内核代码与 App 在同一进程内运行，会被认定为**结合作品（combined work）**。包含它的 APK 整体必须按 **GPLv3** 分发：**App 自身源码也必须按 GPLv3 提供**，不能闭源。
4. 在同一 APK 中再加入 aria2（作为独立子进程）**不会改变**上面“含 Gopeed 的 App 必须 GPLv3”的结论；aria2 仍须按其 GPLv2 履行源码/许可证义务。GPL-2.0-or-later 与 GPLv3 兼容，因此**双内核同时存在在许可证上是可组合的（以 GPLv3 为整体许可证）**。
5. **闭源发布包含两者的同一 APK 不可行**，也不存在可以承诺的“技术隔离豁免”。所谓“独立进程/RPC 隔离”至多用于降低“一个 GPL 组件是否导致整个产品成为结合作品”的争议，**不是法律保证**，且无法把 AAR 链接的 Gopeed 变成独立程序。
6. **决策已执行**：整体许可证为 **GPLv3**（仓库根 `LICENSE` 为 GPLv3 全文），1.1.1 同时打包 Gopeed 与 aria2，并已落实对应源码获取方式、构建脚本、许可证归档（`licenses/`、`native/licenses/`、APK `assets/licenses/` 17 个文件）与书面要约（`licenses/SOURCE-OFFER.txt`）。
7. 若未来要闭源，可操作的替代方案是：**不打包 GPL 内核**（保留内置内核），或做“独立 GPL 引擎 APK + 闭源 App”的分离架构（需法务评估，仍有风险），或将引擎放到服务端。详见第 4 节。**当前版本不提供任何闭源方案。**

---

## 1. 事实基线

| 组件 | 版本/标签 | 许可证（核实方式） | 集成形态 | 官方源码 |
|---|---|---|---|---|
| Gopeed | `v1.9.3`（GitHub release；commit `a5cd53f94c18ac65add684b1113fa5f0b47cc4da`） | **GPL-3.0**（GitHub API `/repos/GopeedLab/gopeed` 返回 `license.spdx_id = GPL-3.0`） | gomobile 生成 `libgopeed.aar`，**静态链接进 App 进程** | https://github.com/GopeedLab/gopeed |
| aria2 | **`1.35.0`**（devgianlu/aria2-android 预编译 arm64 二进制，5,872,304 B，SHA-256 `88b8b5fe…bd1a7f`；源码 commit `7f79854b…`） | **GPL-2.0-or-later**（`COPYING` 为 GPLv2 标准文本；OpenSSL 链接例外见独立文件 `LICENSE.OpenSSL`） | 可执行文件放入 APK，运行时子进程 + JSON-RPC | https://github.com/aria2/aria2 |
| OpenSSL（aria2 静态链接） | **1.1.1d**（2019-09-10；openssl submodule pin `894da2fb…`） | **OpenSSL License + SSLeay License**（双许可证） | 静态链接进 `libaria2c.so` | https://www.openssl.org/source/license.html |
| Motrix Lite（本项目） | 1.1.1（当前，versionCode 3） | **GPLv3**（仓库根 `LICENSE` 全文） | App 本体（含双内核集成代码） | https://github.com/Chenzhenyan0/motrix-lite |

> 说明：Gopeed 官方发布物（桌面/Android App）使用 GPL-3.0；gomobile 生成的 AAR 只是打包形式变化，不改变许可证。aria2 的 `COPYING` 是标准 GPLv2 文本，**不包含** OpenSSL 例外；`LICENSE.OpenSSL` 才是逐文件引用的链接许可，**OpenSSL 1.1.1d 的双许可证属于其覆盖范围**。两文件都必须随二进制分发。
>
> **v1.1.0 历史事实**：v1.1.0 使用 devgianlu aria2 **1.37.0**（静态链接 **OpenSSL 3.0.0**/Apache-2.0）。该二进制在 Redmi Turbo 4 Pro（Redmi 25053RT47C，Android 16 / SDK 36）上因缺少 OpenSSL 3 `legacy` provider 启动失败（`OSSL_PROVIDER_load 'legacy' failed`），v1.1.1 改回 1.35.0/OpenSSL 1.1.1d。历史 Release 保留，不删除。

---

## 2. 分发场景矩阵

| 分发方式 | 仅内置内核（1.0.0 历史，MIT） | 仅 aria2（子进程） | 仅 Gopeed（AAR 链接） | **双内核，同一 APK（当前 1.1.1）** |
|---|---|---|---|---|
| **公开发布二进制、不公开源码（闭源）** | 可行（当时 MIT） | **不可直接承诺**：需履行 aria2 GPLv2（提供对应源码/要约）；App 是否被认定为结合作品存在争议，风险中高 | **不可行**：Gopeed 是 GPLv3 结合作品，必须开源 App 源码 | **不可行**：双重 GPL 义务 + AAR 链接，必须 GPLv3 开源 |
| **公开发布，整体 GPLv3 开源** | 可以（兼容升级） | 可以（整体 GPLv3，兼容 GPLv2+） | 可以（推荐） | **唯一可行的合规发布方式（推荐）** |
| **内部使用，不向第三方分发** | 无 GPL 义务 | 无分发则无 GPL 源码对外义务；但仍须遵守内部许可证管理 | 同左；若员工/子公司间分发需评估是否构成 distribution | 同左；一旦对外分发（含客户交付、公开下载）即触发全部义务 |
| **应用商店（Google Play 等）上架** | 可以 | 可上架，但需满足 GPLv2 源码义务；商店“动态链接 GPL 引擎”可能引发合规审查 | 可以上架，必须 GPLv3 开源并披露源码；注意商店条款不得附加与 GPL 冲突的限制 | 可以，但必须是 GPLv3 开源；APK 体积（aria2 二进制 5.87 MB）也是风险 |

**关键点**：是否“闭源”不由代码是否公开决定，而由**分发时的许可证义务**决定。GPL 不禁止收费，但禁止在分发二进制时附加“不得反向工程/不得再分发/源码保密”等额外限制。

---

## 3. 法理细节（为什么不能闭源）

### 3.1 Gopeed AAR = 结合作品
- gomobile 生成的 AAR 把 Go 代码编译为共享库（`.so`），通过 JNI 与 Java/Kotlin 代码在同一 Android 进程中直接调用；不是通过独立可执行程序、独立进程、socket 与 App 通信。
- 这符合 GPL 对“基于该程序的作品（work based on the Program）”和“结合作品”的典型理解：App 与 Gopeed 形成单一程序整体。
- 结论：**分发 APK 时必须按 GPLv3 提供 App 的完整对应源码**，且不得对该 GPLv3 代码附加额外限制。本项目已统一将整体许可证声明为 **GPLv3**，源码必须公开。**不能**只公开 Gopeed 源码而把 App 闭源。

### 3.2 aria2 子进程 ≠ 自动“隔离”
- GPL FAQ 对“独立程序通过管道/命令行通信”通常承认可以是独立作品（mere aggregation），但 APK 把 aria2 二进制与 App 一起打包分发、由 App 专门调用并依赖其输出，是否构成“结合作品”仍取决于具体事实与法域解释。
- 即使按“独立程序/聚合”处理，**aria2 本身仍受 GPLv2 约束**：必须向接收者提供 aria2 的对应源码（或有效书面要约）、许可证文本、修改说明（如有）。
- 在双内核方案中 App 已因 Gopeed 成为 GPLv3 作品，aria2 的聚合争议只影响 aria2 自身义务的适用方式，**不影响整体必须开源的结论**。

### 3.3 GPLv2+、GPLv3 与 OpenSSL 例外
- GPL-2.0-or-later 允许选择 GPLv3，因此两者可以组合进同一 GPLv3 发布物。
- 若 aria2 是 `GPL-2.0-only` 则与 GPLv3 不兼容；本项目使用的 aria2 为 `GPL-2.0-or-later`（`COPYING` 原文“either version 2 of the License, or (at your option) any later version”），因此可以组合，整体按 GPLv3 处理。
- **OpenSSL 1.1.1d 的 OpenSSL+SSLeay 双许可证与 GPL 不兼容**，但 aria2 的 `LICENSE.OpenSSL` 例外覆盖“许可证与该文件所列文本 identical”的 OpenSSL 版本，1.1.1d 属于该范围，因此组合分发成立；必须随附例外文本与 OpenSSL/SSLeay 许可证。
- 对照：v1.1.0 的 OpenSSL 3.0.0 为 Apache-2.0，本可与 GPLv3 兼容，但其预编译二进制缺少 legacy provider 导致 Android 16 启动失败，故被替换。

### 3.4 GPLv3 的主要义务（摘要）
1. **Corresponding Source**：随二进制提供或书面承诺提供“生成、安装、运行目标代码所需的全部源码”，包括构建脚本/安装脚本、资源、Go 包装代码、链接的库（aria2 静态链接的 OpenSSL 1.1.1d、zlib 1.2.9、c-ares 1.14.0、libexpat 2.2.9、libssh2 1.9.0_DEV、libc++ clang 8.0.7）的源码或获取方式。
2. **许可证文本与版权声明**：随 APK 附 GPLv3 全文、各依赖许可证、版权声明。
3. **不得附加额外限制**：不能限制接收者再分发/修改；应用商店条款不得与 GPL 冲突。
4. **书面要约**（物理分发时）：至少 3 年有效的源码获取要约。
5. **专利授权与终止条款**：GPLv3 自带专利许可与终止机制。
6. **修改说明**：若修改 Gopeed/aria2 源码，需标明修改与日期。
7. **Installation Information / 反 Tivoization**：主要针对“User Product”的固件锁定；普通 App 商店分发通常不触发，但企业预装/定制 ROM 需单独评估。

### 3.5 “技术隔离”的边界
- 真正可能降低组合风险的架构：**独立进程 + 独立应用 + 仅通过公开 RPC 协议通信 + 不共享内部数据结构 + 不互相链接**。
- 但“专为运行该 GPL 引擎而设计、捆绑分发、默认配合使用”仍可能被认定为结合作品；GPLv3 没有像 LGPL 那样的“动态链接例外”，也没有官方“插件例外”。
- AAR 模式（本项目）**不存在隔离空间**：它是链接，不是进程隔离。

---

## 4. 若计划闭源发布：可操作替代方案与风险

| 方案 | 做法 | GPL 影响 | 风险/代价 |
|---|---|---|---|
| **A. 不集成 GPL 内核** | 保留现有自研 OkHttp 内核（代码随整体按 GPLv3 发布） | 无额外 GPL 组件义务（整体仍是 GPLv3） | 失去 Gopeed/aria2 的多协议与生态能力；本项目的“双内核”不成立 |
| **B. 独立 GPL 引擎 APK + 闭源主 App** | 两个 APK：主 App（闭源）+ 引擎 APK（GPLv3 开源，内含 Gopeed/aria2），通过 AIDL/ContentProvider/socket/RPC 通信；不互相链接 | 引擎 APK 必须 GPLv3 开源；主 App 是否被认定为结合作品需法务判断 | **不是法律保证**；捆绑分发/默认依赖可能被认定组合；安装体验差；Android 跨应用进程通信与保活复杂 |
| **C. 引擎服务端化** | 下载引擎跑在服务器，App 只做客户端 | App 不分发 GPL 二进制；服务端内部使用 GPL 通常无分发义务 | 产品形态变化；流量/存储/隐私成本；非“本地下载管理器” |
| **D. 商业闭源下载 SDK** | 采购商业许可的下载/加速 SDK | 取决于 SDK 许可 | 成本；功能/生态受限 |
| **E. 仅 aria2 + 独立进程（主 App 尝试保持闭源）** | 只打包 aria2 二进制作为独立子进程，主 App 不链接 GPL | 必须公开 aria2 源码；主 App 是否组合有争议 | 中高法律风险，**不能承诺闭源安全** |
| **F. 只集成 Gopeed（开源整体）** | 放弃闭源，整体 GPLv3 | 可行且相对简单 | 不能闭源；无 aria2 生态 |

**明确结论**：**不存在“把 Gopeed AAR 和 aria2 放进同一 APK 还能闭源分发”的合规方案**。任何声称可以通过加壳、RPC、独立进程或“动态加载”规避 GPL 的说法都不可靠，且有侵权风险。

---

## 5. 已执行路线：整体 GPLv3 开源

| 工作项 | 状态 | 落地位置 |
| --- | --- | --- |
| 整体许可证切换为 GPLv3 | ✅ 已完成 | 仓库根 `LICENSE` 为 GPLv3 全文；README、关于页、`docs/COMPLIANCE.md` 均已更新 |
| 本工程全部源码（App + 引擎集成 + 构建脚本 + 资源） | ✅ 公开仓库 | https://github.com/Chenzhenyan0/motrix-lite |
| Gopeed v1.9.3 对应源码/构建 | ✅ 已固定/记录 | 上游 tag `v1.9.3`；`native/gopeed/build.ps1`（Go 1.24.11 / NDK 27.2.12479018）；AAR 15,438,987 B / SHA-256 `aa064e34…dd2d7c` |
| aria2 1.35.0 对应源码/构建 | ✅ 已固定/记录 | 上游 tag `release-1.35.0`；`native/aria2/fetch.ps1`（固定 5,872,304 B / SHA-256 `88b8b5fe…bd1a7f`）+ devgianlu v1.35.0 构建文件快照（`native/sources/devgianlu-build-files-v1.35.0.zip`） |
| 静态链接依赖源码/版本/许可证 | ✅ 已记录 | OpenSSL 1.1.1d（OpenSSL+SSLeay）、zlib 1.2.9、c-ares 1.14.0、libexpat 2.2.9、libssh2 1.9.0_DEV、libc++（clang 8.0.7）；见 `licenses/DEPENDENCY-VERSIONS.md` 与 `licenses/` |
| 许可证文本 | ✅ 已归档 | `licenses/`、`native/licenses/`、APK `assets/licenses/`（17 个文件） |
| 应用内披露 | ✅ 已实现 | 设置 → 关于 →「开源许可 / 源码获取」对话框（含 GPLv3 声明、组件、源码 URL、书面要约） |
| 源码获取方式 | ✅ 已提供 | GitHub 仓库 + Release 源码归档 + 仓库 Issue + `licenses/SOURCE-OFFER.txt` |
| 不添加额外限制 | ✅ 已声明 | `docs/COMPLIANCE.md` 第七节；分发检查清单包含此项 |
| 构建信息记录 | ✅ 已完成 | Gopeed AAR 15,438,987 B / SHA-256 `aa064e34…dd2d7c`；aria2 1.35.0 二进制 5,872,304 B / SHA-256 `88b8b5fe…bd1a7f`；最终 APK debug 29,358,263 B / SHA-256 `583b6846…f611bc4`、release 18,771,902 B / SHA-256 `a8b5be56…cbd2d5d` |
| v1.1.0 → v1.1.1 修复 | ✅ 已记录 | v1.1.0 的 aria2 1.37.0/OpenSSL 3.0.0 在 Android 16 启动失败，v1.1.1 改用 1.35.0/OpenSSL 1.1.1d；历史 Release 保留并加注说明 |

---

## 6. APK 中应包含的许可证声明与源码获取方式（清单，1.1.1 已落实）

APK 内提供“开源许可”对话框，并随 APK 打包以下文本（`assets/licenses/`，仓库同步于 `licenses/`、`native/licenses/`）：

| 文件 | 内容 | 来源 |
|---|---|---|
| `GPL-3.0.txt` | GPLv3 全文 | https://www.gnu.org/licenses/gpl-3.0.txt |
| `GPL-2.0.txt` | GPLv2 全文 | https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt |
| `Gopeed-LICENSE.txt` | Gopeed 仓库 LICENSE（GPL-3.0） | https://github.com/GopeedLab/gopeed/blob/v1.9.3/LICENSE |
| `aria2-COPYING.txt` | aria2 1.35.0 COPYING（GPLv2 标准文本） | https://github.com/aria2/aria2/blob/release-1.35.0/COPYING |
| `aria2-LICENSE.OpenSSL` / `aria2-OpenSSL-Exception.txt` | aria2 的 OpenSSL 链接例外全文 / 适用性说明（适用于 OpenSSL 1.1.1d） | aria2 仓库根级 `LICENSE.OpenSSL` |
| `OpenSSL-1.1.1d-LICENSE.txt` | OpenSSL + SSLeay 双许可证全文（实际静态链接版本） | https://www.openssl.org/source/license.html |
| `zlib-1.2.9-zlib-license.txt` / `c-ares-1.14.0-MIT.txt` / `libexpat-2.2.9-MIT.txt` / `libssh2-1.9.0_DEV-BSD-3-Clause.txt` | 静态依赖各自许可证原文 | 见 `licenses/DEPENDENCY-VERSIONS.md` |
| `Apache-2.0.txt` | Apache-2.0 全文（Kotlin/AndroidX/libc++ 等） | https://www.apache.org/licenses/LICENSE-2.0 |
| `DEPENDENCY-VERSIONS.md` | 静态依赖精确版本、submodule commit 与证据 | devgianlu/aria2-android v1.35.0 构建文件 |
| `devgianlu-aria2-android-LICENSE` | 二进制构建仓库许可状态核查记录 | GitHub API |
| `SOURCE-OFFER.txt` | 源码地址、tag、书面要约、联系方式、构建命令 | 本项目生成 |
| `THIRD_PARTY_NOTICES.md` | 第三方组件/许可证完整清单 | 本文件（仓库/APK assets 同步） |
| `README.md` | 许可证目录索引 | 本项目生成 |

> 说明：libc++ 使用 NDK 自带版本（Apache-2.0 WITH LLVM-exception），不涉及 LGPL；1.0 版计划中的 `LGPL-2.1.txt` 在本版本依赖组合下不需要。

**源码获取方式（1.1.1 同时覆盖）**：
1. 公开仓库（含所有构建脚本与对应源码）：`https://github.com/Chenzhenyan0/motrix-lite`，发布时打 tag（如 `v1.1.1`）；
2. Release 页面附源码归档：`Gopeed-v1.9.3-source.zip`、`aria2-1.35.0-source.tar.gz`、`devgianlu-aria2-android-build-files.zip`、`MotrixLite-1.1.1-licenses.zip`；
3. APK 内“关于 → 开源许可 / 源码获取”显示上述链接与书面要约；
4. 对物理分发（如预装设备）另附 3 年有效的书面要约。

---

## 7. 风险与不确定性

1. **本报告不是法律意见**；GPL 的“结合作品/聚合”认定依赖具体事实、法域与司法实践，正式发布前请法务确认。
2. **AAR 链接的认定风险很低但并非零**：Go 静态库 + JNI 直接调用，几乎必然被视为结合作品。
3. **aria2 预编译二进制的来源风险**：devgianlu/aria2-android 未声明 LICENSE，二进制可能包含未披露的补丁/额外库；正式发布前应自行按 devgianlu 构建脚本重新构建并记录 SBOM，或至少核对源码 tag/构建脚本并公开发布对应源码。
4. **OpenSSL 1.1.1d/aria2 1.35.0 已停止维护**：两者均为 2019 年版本，存在已知 CVE；libssh2 为 1.9.0_DEV 开发快照，libc++ 来自 clang 8.0.7 工具链。本版本为兼容 Android 16 而保留旧预编译二进制，**正式分发前建议用受支持的 OpenSSL（1.1.1w / 3.x）与新版 aria2 重建**，或至少在 Release 说明披露供应链时点（KI-30）。
5. **v1.1.0 的 Android 16 兼容性事故**：aria2 1.37.0（OpenSSL 3.0.0）缺少 legacy provider，启动即失败；v1.1.1 已修复并真机验证。后续升级 aria2/OpenSSL 时必须做 Android 16（及更高）启动回归。
6. **Gopeed Go 依赖**：Go 模块依赖众多（MIT/Apache/BSD 等），需生成完整第三方清单；Gopeed 自身 GPLv3 为主许可证。
7. **应用商店/预装设备**：GPLv3 的反 Tivoization/安装信息条款、商店条款与 GPL 的冲突需单独评估。
8. **商标**：Motrix 非官方；Gopeed/aria2 名称仅作事实描述，不使用其官方视觉素材；不影响 GPL 义务。
9. **“闭源 + 独立进程”不是安全港**：不要对外承诺可闭源；如必须闭源，优先选择不集成 GPL 内核的方案。

---

## 8. 决策建议（决策已执行）

- **已选择**：**GPLv3 开源 + 双内核**（1.1.1 已实施，与公开仓库兼容）。
- 若未来目标是**闭源商业产品**：**不要**在同一 APK 中同时集成 Gopeed 与 aria2；改用内置内核或商业 SDK；如坚持 aria2，先由法务评估“独立进程/独立引擎 APK”方案，且仍需公开 aria2 源码。**当前版本不提供闭源分发方案。**
- 若只是**内部使用**：可以不对外分发，但仍建议保留完整第三方许可证记录，并避免把内部构建产物泄露为公开下载。

---

## 附录 A：事实核查与引用

- Gopeed 仓库许可证：`api.github.com/repos/GopeedLab/gopeed` → `license.spdx_id = GPL-3.0`；发布 tag `v1.9.3`。
- Gopeed mobile 绑定：v1.9.3 的 AAR 实际只导出 `start`/`stop`（无 `InvokeAsync`/`SubscribeTaskEvents` 桥），Java 入口类为 `com.gopeed.libgopeed.Libgopeed`；任务/进度通过内嵌 REST API 轮询完成。构建报告见 `.tools/gopeed-build/BUILD_REPORT.md`（不提交仓库）。
- aria2 源码与 COPYING：https://github.com/aria2/aria2（tag `release-1.35.0`，commit `7f79854b2ed6ef4978a8526831034b34b1fb3871`）；`COPYING` 为 GPLv2 标准文本；`LICENSE.OpenSSL` 为 OpenSSL 链接例外。
- 预编译 aria2 Android 二进制：https://github.com/devgianlu/aria2-android/releases/tag/v1.35.0（`aria2c-arm64-v8a`，5,872,304 B，SHA-256 `88b8b5feec7e4e7392f129fed4cf61f52a88fdc5dfe1e1464ed467fb41bd1a7f`，ELF64 ET_DYN AArch64）。构建仓库未声明 LICENSE（2026-11-05 核查）；构建文件快照见 `native/sources/devgianlu-build-files-v1.35.0.zip`。
- OpenSSL 1.1.1d 证据：devgianlu v1.35.0 的 openssl submodule pin `894da2fb7ed5d314ee5c2fc9fd2d9b8b74111596`；真机 `aria2 --version` 显示 `OpenSSL/1.1.1d`（`Libraries` 行）；二进制字符串 `OpenSSL 1.1.1d  10 Sep 2019`。
- 依赖精确版本：见 `licenses/DEPENDENCY-VERSIONS.md`。
- GPLv3 全文：https://www.gnu.org/licenses/gpl-3.0.txt
- GPLv2 全文：https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt

## 附录 B：一句话结论

**Gopeed（GPL-3.0 AAR）与 aria2 1.35.0（GPL-2.0+ 子进程；静态链接 OpenSSL 1.1.1d 并适用其 OpenSSL 例外）同时放进同一个 APK 时，整个 APK 必须按 GPLv3 开源；闭源分发不可行。本项目已按 GPLv3 公开全部对应源码；若必须闭源，则不要集成这两个内核，或先做法务评估的独立引擎方案。**
