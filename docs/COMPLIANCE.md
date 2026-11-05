# 许可证与合规说明

本文说明 Motrix Lite 在开源许可证、商标、权限与分发层面的立场。**本文不构成法律意见**；正式商业分发前应自行复核相关许可证原文，必要时咨询法务。

- 项目代码：MIT，见 [LICENSE](../LICENSE)
- 第三方组件清单：见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
- 应用标识：Motrix Lite，包名 `com.motrixlite.downloader`，versionName 1.0.0

---

## 一、结论摘要

| 项目 | 许可证 | 是否随 APK 分发 | 本项目的义务 |
| --- | --- | --- | --- |
| 本项目代码 | MIT | 是（源码 + 二进制） | 保留版权与许可声明 |
| Motrix（桌面版） | MIT | 否 | 仅参考思路，无代码/资源复用，不产生额外义务 |
| aria2 | GPLv2 | **否** | 未分发二进制，**不触发** GPLv2 源码提供义务 |
| Kotlin / 协程 | Apache-2.0 | 是 | 保留版权与许可证声明、NOTICE（如有） |
| OkHttp / Okio | Apache-2.0 | 是 | 同上 |
| AndroidX / Jetpack Compose / Material3 / material-icons | Apache-2.0 | 是 | 同上 |
| JUnit / MockWebServer / coroutines-test / org.json | EPL-1.0 / Apache-2.0 / Public Domain | 否（仅测试） | 无 |

一句话概括：**本 APK 目前只包含 MIT 与 Apache-2.0 组件，不含 GPLv2 代码，也不含任何 aria2 二进制。**

---

## 二、Motrix（MIT）：参考思路，不复用代码

**事实**

- Motrix 是一款基于 Electron 的桌面下载管理器，采用 MIT 许可证。
- 本项目在**产品定位、功能清单与交互组织方式**上参考 Motrix：任务列表卡片、进度与速度展示、并发/连接数设置、深色模式、下载完成通知等。
- 本工程是独立的 Android 原生工程，使用 Kotlin + Jetpack Compose + OkHttp 编写。

**没有做的事**

- 未复制 Motrix 的 JavaScript/TypeScript、Vue、Electron 代码；
- 未打包 Motrix 的图标、字体、截图、品牌视觉；
- 不是在 Android 上运行 Motrix 桌面版，也不是其 Web UI 的 WebView 封装；
- 未使用 Motrix 的包名或应用 ID。

**MIT 的一般要求**

MIT 许可证要求：在软件副本或实质性部分中保留版权声明与许可声明。由于本项目未复用 Motrix 代码，严格意义上不产生"保留其版权声明"的分发义务；但本项目仍在文档中明确致谢，避免来源混淆。

---

## 三、aria2（GPLv2）：未分发，因此不承担源码提供义务

### 3.1 为什么本 APK 不涉及 GPLv2

GPLv2 的核心义务由**分发（distribution）**触发：当你向他人提供 GPLv2 程序的二进制副本时，必须同时提供（或以书面要约方式承诺提供）完整的对应源代码，并附许可证文本与修改说明。

本项目的实际情况：

- APK 内**没有** aria2 可执行文件（`libaria2`、`aria2c`）；
- **没有**通过 JNI、`Runtime.exec`、`ProcessBuilder` 调用 aria2；
- **没有**把 aria2 的源码翻译/移植进本项目；
- 下载内核为自行实现的 OkHttp 分段下载器（`engine/TaskDownloader.kt`）。

因此，**本 APK 的分发不触发 GPLv2 的源码提供义务**，也不存在 GPLv2 与 MIT/Apache-2.0 的许可证兼容性问题。

### 3.2 为什么选择自研而不是 aria2

这不是"规避 GPL"，而是工程与合规双重权衡的结果，详见 [README 技术方案](../README.md#一技术方案)。核心原因：

1. **许可证成本**：一旦随包分发 aria2 二进制，就必须提供完整对应源码与修改说明，且需评估 GPLv2 与整体分发条款的兼容性；对有闭源或商用诉求的发行方，这会显著提高合规复杂度。
2. **Android 可执行文件限制**：Android 10（API 29）起，targetSdk ≥ 29 的应用无法从可写数据目录执行文件；要跑 `aria2c` 只能放进 `lib/<abi>/` 作为"原生库"随包分发，包体与 ABI 适配成本高。
3. **进程管理复杂**：需要额外管理子进程生命周期、JSON-RPC 端口、崩溃重启、与前台服务/唤醒锁的协同。
4. **BT 场景与 Android 后台策略冲突**：监听端口、DHT（UDP）、持续连接与 Doze/省电策略冲突，用户体验难以保证。
5. **可诊断性**：自研内核的错误分类、暂停语义、限速与通知聚合可与前台服务深度集成。

### 3.3 如果未来一定要集成 aria2：义务与风险

> 以下为**未来方案的风险提示**，当前版本**未实施**。

**若随 APK 分发 aria2 二进制（或包含 aria2 的 JNI 库），必须做到：**

1. 随包或以书面要约方式提供 **aria2 完整对应源码**（含你所做的全部修改），并提供获取方式（下载地址或书面承诺）；
2. 附带 **GPLv2 许可证全文**，并在关于页面/文档中说明使用了 aria2 及其版本；
3. 提供**修改说明**（改了哪些文件、为什么改）；
4. 提供**构建脚本与工具链信息**，使第三方可以复现构建；
5. 评估 **GPLv2 与整体分发许可的兼容性**：GPLv2 与 Apache-2.0 存在已知的不兼容点（例如专利条款、附加限制），若 JNI 层与 aria2 构成同一作品，则 JNI 层也需以 GPLv2 兼容方式发布；"独立进程调用"是否构成衍生作品在业界仍有争议，不能默认安全；
6. 若通过 Google Play 分发，还需评估 Play 政策对打包可执行文件、动态下发代码的限制。

**工程风险：**

| 风险 | 说明 |
| --- | --- |
| 包体膨胀 | 4 个 ABI 的 aria2 + 依赖库通常增加 8–15 MB |
| 稳定性 | 子进程被系统杀死后需重建 RPC 连接与任务映射 |
| 端口与安全 | 本地 RPC 端口需鉴权（token），否则同机应用可操纵下载 |
| BT 体验 | Android 后台限制导致做种/端口映射基本不可用 |
| 维护成本 | 需要跟随 aria2 上游更新并重新交叉编译 |

**推荐的替代路径（风险最低）：**

- **方案 C：外置引擎 + RPC 客户端**。应用不打包 aria2，用户自行在 NAS/PC 上运行 aria2，App 只作为 JSON-RPC 客户端（或提供"导出链接到外部下载器"）。由于不分发 aria2 二进制，**不触发 GPLv2 义务**，同时获得 BT/磁力能力。
- **方案 B：自研 BT 引擎**（bencode 解析、磁力 metadata 交换、peer wire、tracker/DHT）。工作量以人月计，且同样受 Android 后台与端口限制，建议仅在有明确产品目标时投入。

---

## 四、Apache-2.0 组件义务

本项目使用的 Kotlin、OkHttp/Okio、AndroidX、Jetpack Compose、Material3、material-icons 均为 Apache-2.0。分发时的义务：

1. 保留版权、专利、商标与归属声明；
2. 随分发物提供 Apache-2.0 许可证副本（本仓库 `LICENSE` 为项目自身 MIT；Apache-2.0 全文请以官方链接或随包 `THIRD_PARTY_NOTICES.md` 指向的地址为准）；
3. 若组件自带 `NOTICE` 文件，需保留其中的归属声明。

**已知偏差（需说明）**：`app/build.gradle.kts` 中

```kotlin
packaging {
    resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
```

会把 AAR 内用于声明许可证的 `META-INF/AL2.0`、`META-INF/LGPL2.1` 占位文件从 APK 中剔除。这是 Android 构建中常见的做法（避免重复文件冲突、减小体积），但严格来说削弱了"随二进制附带许可提示"的效果。项目的补偿措施是：

- 在仓库根目录提供 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) 全量清单；
- 在应用内「设置 → 关于 → 开源合规说明」展示核心结论。

**建议**：若面向应用商店或企业分发，将 `THIRD_PARTY_NOTICES.md` 一并随发布物提供，或改为在 APK 内保留 `META-INF` 许可文件（移除上述 `excludes` 配置）。

---

## 五、商标与命名

- **Motrix 的名称与商标归其作者/项目所有**。本项目为**非官方、独立实现**，与 Motrix 官方无隶属、赞助或背书关系。
- 应用名 "Motrix Lite" 仅用于说明产品定位（轻量下载管理器），**不使用** Motrix 官方图标、Logo、启动图或品牌配色素材；应用图标为自绘 VectorDrawable。
- 包名 `com.motrixlite.downloader` 为本项目自有标识，未占用他人包名。
- 若未来收到商标权利人的合理要求，应配合更名或调整品牌呈现。

---

## 六、隐私与数据

| 项目 | 现状 |
| --- | --- |
| 账号体系 | 无 |
| 统计/崩溃上报 SDK | 无 |
| 广告 SDK | 无 |
| 网络请求 | 仅访问用户输入的下载 URL 及其重定向目标 |
| 本地数据 | `SharedPreferences`（`motrix_lite_settings`）+ `filesDir/tasks.json` + 应用专属目录下的 staging 临时文件 |
| 剪贴板 | 仅在用户主动点击「粘贴」时读取，不后台监听 |
| 权限 | 见 [README 权限说明](../README.md#八权限说明)，**未申请任何宽泛存储权限** |

下载内容与 URL 属于用户数据，应用不做上传。卸载应用会删除内部数据与 staging 文件；已发布到 `Download/MotrixLite` 的文件不会随卸载删除。

---

## 七、内容与使用合规

- 本应用仅为通用下载工具，**不对用户下载内容的合法性负责**。用户应确保拥有相应内容的获取与使用权利，并遵守目标站点服务条款与当地法律。
- 应用默认允许 HTTP 明文下载（`android:usesCleartextTraffic="true"`），存在被中间人篡改的风险，详见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) 的 KI-13。建议仅对可信来源使用 HTTP。
- 应用**不提供**任何破解、绕过付费墙、绕过 DRM 或规避访问控制的能力；不支持需要登录鉴权的链接（见 KI-02）。

---

## 八、分发检查清单（发布前）

- [ ] 替换演示签名证书，使用正式发布密钥（见 [BUILD_AND_SIGN.md](BUILD_AND_SIGN.md)）
- [ ] `versionCode` / `versionName` 已递增
- [ ] 随发布物提供 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`
- [ ] 确认 APK 中不含 aria2 二进制（`unzip -l app-release.apk | findstr /i "aria2 libaria2"` 应为空）
- [ ] 复核 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 在目标分发渠道（如 Google Play）的政策要求
- [ ] 复核隐私政策（若上架商店）
- [ ] 若未来集成 GPLv2 组件，按第 3.3 节补齐源码提供与许可证文本

---

## 九、免责声明

本软件按"现状"提供，不附带任何明示或暗示担保。作者与贡献者不对因使用本软件产生的任何直接或间接损失承担责任。详见 [LICENSE](../LICENSE)。
