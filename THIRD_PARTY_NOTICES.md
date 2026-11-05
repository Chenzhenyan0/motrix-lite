# 第三方组件与许可声明（THIRD PARTY NOTICES）

本文件列出 Motrix Lite Android 应用（包名 `com.motrixlite.downloader`）所使用的第三方开源组件、其许可证类型，以及是否随 APK 分发。本工程自身代码以 **MIT** 许可证发布，见 [LICENSE](LICENSE)。

- 依赖版本来自 `app/build.gradle.kts`（唯一事实来源）；如需核对，请以该文件为准。
- 本文档只做许可信息归集，不构成法律意见。涉及再分发、商用或上架时，请自行复核各许可证原文。

---

## 一、随 APK 分发的运行时组件

| 组件 | 版本 | 许可证 | 用途 | 备注 |
| --- | --- | --- | --- | --- |
| Kotlin Standard Library | 2.0.21（由 Kotlin 插件引入） | Apache-2.0 | 语言运行时 | 随 R8 裁剪 |
| kotlinx-coroutines-android / kotlinx-coroutines-core | 1.9.0 | Apache-2.0 | 协程与并发 | — |
| OkHttp | 4.12.0 | Apache-2.0 | HTTP 客户端、Range 请求 | 核心下载能力 |
| Okio | 随 OkHttp 传递（4.12.0 对应 3.x） | Apache-2.0 | OkHttp 的 I/O 层 | 传递依赖 |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | Android 兼容与 KTX 扩展 | — |
| androidx.activity:activity-compose | 1.9.3 | Apache-2.0 | Compose Activity 与结果回调 | — |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 | Apache-2.0 | 生命周期 | — |
| androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 | Apache-2.0 | `collectAsStateWithLifecycle` 等 | — |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | Apache-2.0 | ViewModel 与 Compose 集成 | — |
| androidx.documentfile | 1.0.1 | Apache-2.0 | SAF 目录读写（自定义保存位置） | — |
| Jetpack Compose BOM | 2024.12.01 | Apache-2.0 | 统一 Compose 版本 | 仅版本约束，不含代码 |
| androidx.compose.ui:ui / ui-graphics / ui-tooling-preview | 由 BOM 决定 | Apache-2.0 | Compose UI 基础 | — |
| androidx.compose.material3:material3 | 由 BOM 决定 | Apache-2.0 | Material 3 组件 | — |
| androidx.compose.material:material-icons-core | 由 BOM 决定 | Apache-2.0 | 图标集（`Icons.Default.*`） | 仅 core 子集 |
| androidx.compose.ui:ui-tooling（debug） | 由 BOM 决定 | Apache-2.0 | 仅调试构建的预览工具 | 仅 debug 包 |
| androidx.compose.ui:ui-test-manifest（debug） | 由 BOM 决定 | Apache-2.0 | 仅调试构建 | 仅 debug 包 |

> 说明：构建脚本 `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }` 会把部分 AAR 中的许可证占位文件从 APK 中排除，因此本项目采用**随安装包/仓库附带本清单**的方式满足署名与许可证提示义务。若再分发 APK，请一并提供本文件与 `LICENSE`。

---

## 二、仅用于构建与测试、不随 APK 分发的组件

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | 构建 |
| Kotlin Gradle Plugin（`org.jetbrains.kotlin.android`、`...plugin.compose`） | 2.0.21 | Apache-2.0 | 构建 |
| Gradle Wrapper / Gradle | 8.9 | Apache-2.0 | 构建 |
| JUnit 4 | 4.13.2 | EPL-1.0 | 单元测试 |
| kotlinx-coroutines-test | 1.9.0 | Apache-2.0 | 单元测试 |
| OkHttp MockWebServer | 4.12.0 | Apache-2.0 | 单元测试（本地 HTTP 服务桩） |
| org.json:json | 20240303 | Public Domain（Maven Central 标注；历史版本为 JSON License） | 单元测试中提供 `org.json` 实现（Android 运行时由系统提供） |

上述组件只在开发机或 CI 上运行，未打包进发布产物，因此不构成对最终用户的分发。

---

## 三、被参考但未使用的项目

| 项目 | 许可证 | 关系 |
| --- | --- | --- |
| Motrix（桌面版，Electron） | MIT | **仅参考产品定位、功能组织与交互思路**。本工程不包含 Motrix 的任何源代码、界面资源、图标或品牌素材，也不是 Motrix 桌面版的封装。 |
| aria2 | GPLv2 | **未集成、未分发**。本应用使用自研 OkHttp 分段下载内核，APK 内不含 aria2 可执行文件、共享库或 JNI 包装。 |

关于这两个项目的详细合规分析见 [docs/COMPLIANCE.md](docs/COMPLIANCE.md)。

---

## 四、图标与素材来源

- 应用图标（`app/src/main/res/drawable/ic_launcher_foreground.xml`、`mipmap-anydpi-v26/ic_launcher.xml`）与界面内图标（`ic_download`、`ic_pause`、`ic_folder`、`ic_wifi`、`ic_bolt`、`ic_link`、`ic_battery`、`ic_rocket`、`ic_notification`）为本工程自绘的 VectorDrawable，随本项目以 MIT 许可证提供。
- 未使用 Motrix 官方图标、未使用任何第三方付费素材库。

---

## 五、许可证原文获取

| 许可证 | 参考地址 |
| --- | --- |
| MIT | https://opensource.org/license/mit |
| Apache-2.0 | https://www.apache.org/licenses/LICENSE-2.0 |
| EPL-1.0 | https://www.eclipse.org/legal/epl-v10.html |
| GPLv2（仅说明用途，本项目不使用） | https://www.gnu.org/licenses/old-licenses/gpl-2.0.html |

如发现清单遗漏或许可证标注错误，请在项目仓库提交 issue 更正。
