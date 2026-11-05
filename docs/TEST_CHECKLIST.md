# 测试与验收清单（TEST CHECKLIST）

本清单用于在**真实 Android 设备**上验收 Motrix Lite **1.1.0（三引擎：内置 / Gopeed / aria2）**。当前仓库的自动化测试覆盖 JVM 单元测试（**32 个用例，9 个测试类**，见 [BUILD_AND_SIGN.md](BUILD_AND_SIGN.md)）与 `:app:assembleDebug` / `:app:assembleRelease` 构建（最终 APK 已含两个原生内核），**不包含真机功能验收**。请按本清单逐项执行，并在"结果记录"栏填写结论、设备信息与证据位置。

> ⚠️ 事实声明：本项目在开发环境中**没有 Android 设备或模拟器**，因此下列真机用例**全部处于"待验证"状态**。文档中不声称已在任何具体机型（包括红米 Turbo 4 Pro）上通过验收。

---

## 一、使用说明

1. **执行顺序**：先做 A1（安装启动），再做 A2–A7；B 系列为补充与边界用例；**E 系列为 1.1.0 双内核专项**，建议在 A+B 通过后执行。
2. **结果记录**：每项只能填 `通过` / `失败` / `阻塞` / `未执行`。失败时必须填写缺陷编号（见缺陷记录表）。
3. **证据**：截图、录屏、`adb logcat` 片段、文件列表输出。建议每个失败项至少保留一张截图 + 一段日志。
4. **一轮完整验收建议预留 4–6 小时**（含 30 分钟后台挂机、1 次重启、两引擎各 10 分钟锁屏）。

### A 系列与交付要求的对应关系

| 编号 | 验收项 | 对应能力 |
| --- | --- | --- |
| **A1** | 安装与启动 | 安装包可用、应用可正常启动、无崩溃 |
| **A2** | HTTP 断点续传（暂停/恢复） | 内置引擎多连接分段、暂停恢复、字节一致 |
| **A3** | 锁屏/后台持续下载 10 分钟 | 前台服务 + WakeLock 保活（内置引擎） |
| **A4** | 通知权限拒绝后的降级 | 无通知仍可下载、应用内提示 |
| **A5** | 文件在文件管理器中可见 | MediaStore 发布到 `Download/MotrixLite` |
| **A6** | 5 任务并发/暂停/恢复/删除 + 重启状态 | 队列、持久化、重启恢复 |
| **A7** | 限速 / 并发 / 仅 Wi-Fi 策略 | 全局令牌桶、并发上限、网络策略（内置引擎） |

### 双内核（1.1.0）验收映射

| 编号 | 验收项 | 对应能力 | 备注 |
| --- | --- | --- | --- |
| **E1** | 引擎选择器与不可用提示 | 设置页三引擎单选、可用性探测、新建任务拒绝 | 无原生产物/缺少内核时也不崩溃 |
| **E2** | Gopeed HTTP 断点续传 + 锁屏 10 分钟 | AAR 内嵌 REST、Go 内核下载、`DestinationPublisher` | 记录限制：Gopeed 不限速 |
| **E3** | aria2 HTTP 断点续传 + 锁屏 10 分钟 | 子进程 + JSON-RPC、`.aria2` 续传、发布 | 记录进程/端口回收 |
| **E4** | 切换引擎后新建任务 | `task.engine` 创建时固定，旧任务不受影响 | 任务卡片/详情显示引擎 |
| **E5** | 双引擎任务并存 | 两个 Controller 同时运行、通知聚合 | 观察资源占用 |
| **E6** | 服务停止/杀进程后的进程回收 | aria2 子进程回收、Gopeed 端口/线程释放 | 无孤儿 `aria2c` |
| **E7** | AAR/二进制加载失败不闪退 | 可用性探测与错误提示 | 可临时移除产物复现 |
| **E8** | 许可证页面/源码 offer 检查 | GPLv3/GPLv2 文本、源码 URL、书面要约 | 与 GPL 合规对应 |

### 与原始验收标准（7 条）的对应关系

| 原始验收标准 | 对应用例 | 说明 |
| --- | --- | --- |
| 1. 在红米 Turbo 4 Pro 上安装启动不闪退 | A1、E1、E7 | 本环境未在真机执行 |
| 2. HTTP/HTTPS 断点续传；锁屏或切后台 10 分钟后仍能继续下载 | A2、A3（内置）；**E2（Gopeed）、E3（aria2）** | 三个引擎分别验证 |
| 3. 通知权限被拒绝时有降级提示 | A4 | 应用内横幅 + Snackbar + 设置页状态 |
| 4. 下载文件可在系统文件管理器中找到 | A5、E2、E3 | 三个引擎统一经 `DestinationPublisher` 发布 |
| 5. 连续添加 5 个任务，执行暂停/恢复/删除，重启 App 后状态正确 | A6 | 另覆盖 force-stop 与设备重启 |
| 6. 不因 HyperOS 后台限制导致频繁失败，并提供设置引导 | **B2**（配合 A3 复测） | 自启动/省电策略无公开 API，应用只能引导 |
| 7. 不承诺"直接封装 Motrix 桌面版"，技术限制和风险提前说明 | 非测试项 | 见 README「重要声明」、[COMPLIANCE.md](COMPLIANCE.md) |
| 8.（1.1.0 新增）双内核可用、可切换、可回收，许可证合规 | **E1–E8** | 见上表；全部待验证 |

---

## 二、测试环境记录（每轮填写）

| 项 | 值 |
| --- | --- |
| 设备型号 | （必填，例如：红米 Turbo 4 Pro） |
| 系统版本 / ROM | （例：HyperOS 2.x / Android 15） |
| Android API Level | |
| 应用版本 | 1.1.0（versionCode 2） |
| ABI | arm64-v8a（本版本仅支持该 ABI） |
| 构建类型 | debug / release |
| 原生产物状态 | 已放入 Gopeed AAR：是/否；已放入 aria2 二进制：是/否 |
| APK 来源与 SHA-256 | （建议从 `dist/` 安装并填写实际哈希，基线见 3.5） |
| 测试日期 | |
| 测试人 | |
| 网络环境 | Wi-Fi（SSID/频段） / 蜂窝（运营商） |
| 屏幕锁定方式 | |

---

## 三、测试资源准备

### 3.1 本地 Range 测试服务器（推荐，可控且可复现）

工程自带 `tools/range_test_server.py`，支持 `Range`、`Content-Range`、`Content-Disposition`、`ETag`、`Last-Modified` 与限速。

```powershell
# 在工程根目录执行；首次运行会在 tools/ 下生成 testfile.bin
python tools\range_test_server.py --port 8000 --size-mb 50
```

输出示例：

```
creating test file ...\tools\testfile.bin (50 MB) ...
serving ...\tools\testfile.bin (50.0 MB) at http://0.0.0.0:8000/testfile.bin
Range supported, Content-Disposition enabled, ETag enabled
```

常用参数：

| 参数 | 说明 |
| --- | --- |
| `--port 8000` | 监听端口 |
| `--size-mb 50` | 自动生成 50 MB 测试文件（默认 20 MB） |
| `--file D:\test.iso` | 改为提供已有文件，不再生成 |
| `--throttle-kbps 200` | 服务端限速 200 KB/s，便于观察暂停/恢复与限速 |

### 3.2 让手机访问电脑上的服务

**方式一：USB + adb reverse（推荐，不需要同一局域网）**

```powershell
adb reverse tcp:8000 tcp:8000
```

然后在应用里输入：

```
http://127.0.0.1:8000/testfile.bin
```

> `adb reverse` 在设备重启或 USB 断开后失效，需要重新执行。此方式下流量走 USB，不受 Wi-Fi/蜂窝策略影响（但应用仍会读取系统网络类型）。

**方式二：同一局域网**

```powershell
# 查询电脑 IP
ipconfig | Select-String "IPv4"
```

手机访问 `http://<电脑IP>:8000/testfile.bin`。Windows 防火墙需放行 Python 的入站连接（首次运行时会弹窗）。

### 3.3 验证服务器行为

```powershell
# 应返回 206 + Content-Range
curl.exe -sI -H "Range: bytes=0-0" http://127.0.0.1:8000/testfile.bin

# 应返回 200 + Content-Length
curl.exe -sI http://127.0.0.1:8000/testfile.bin
```

### 3.4 其它测试源

| 场景 | 准备方式 |
| --- | --- |
| 不支持 Range 的服务器 | 另开一个不支持 Range 的静态服务：`python -m http.server 8001 --directory tools`（Python 标准库的 `http.server` 不实现 Range），访问 `http://127.0.0.1:8001/testfile.bin` |
| 404 | 访问不存在的路径：`http://127.0.0.1:8001/nope.bin` |
| 403 / 401 | 使用公开测试端点（如 `https://httpbin.org/status/403`、`https://httpbin.org/status/401`），或自建返回 403 的服务 |
| 连接超时 | 使用不可路由地址：`http://10.255.255.1/big.bin`（应触发连接超时） |
| 空文件 | `python tools\range_test_server.py --port 8002 --file tools\empty.bin`（先创建 0 字节文件：`New-Item tools\empty.bin -ItemType File`） |
| 大文件 | `--size-mb 2048`（2 GB，注意磁盘空间）或 `--file` 指向本地 ISO |
| 重名文件 | 对同一 URL 连续创建两次任务（文件名相同） |
| 公网大文件 | 选择**明确支持 Range** 的源：各大 Linux 发行版 ISO 镜像（Debian/Ubuntu/Fedora 等）、开源项目的正式发布产物。先用 `curl -sI -H "Range: bytes=0-0" <URL>` 确认返回 `206` 且带 `Content-Range`，不要用网盘直链（通常带鉴权且不支持 Range） |

> 测试完成后请删除 `tools/testfile.bin`（50 MB～2 GB），避免误提交；该文件未被 `.gitignore` 覆盖。

### 3.5 本次构建基线（v1.1.0 最终构建，可直接用于测试）

| 项 | 值 |
| --- | --- |
| Debug APK | `dist/MotrixLite-debug.apk`，67,482,342 B（64.36 MB），SHA-256 `b78789b787ff9a9465a45657b3a807de31460db10385581d3f410f3fbcff5689` |
| Release APK | `dist/MotrixLite-release.apk`，38,559,243 B（36.77 MB），SHA-256 `5272ca1e679786a05a7f4c3abf8f013a05f3a445996084341c33f86244155d5f` |
| 原生内核 | aria2 1.37.0：`lib/arm64-v8a/libaria2c.so`（69,107,592 B，SHA-256 `dbfd705d437b6fe58a897b83bfafa07cd90972a369269bc2392a29e30fc7389a`）；Gopeed v1.9.3：`lib/arm64-v8a/libgojni.so`（43,908,776 B，来自 AAR 15,438,987 B，SHA-256 `aa064e347705067a19f61222f85f199e0a8527f57fcd80f6e082bdb899dd2d7c`） |
| 许可证资产 | APK `assets/licenses/` 含 GPL-3.0、GPL-2.0、aria2 COPYING、历史 aria2 LICENSE.OpenSSL/说明、**OpenSSL 3.0.0 Apache-2.0 全文+版权声明**、Gopeed LICENSE、zlib/c-ares/libexpat/libssh2 许可证、DEPENDENCY-VERSIONS、SOURCE-OFFER、THIRD_PARTY_NOTICES |

> 以上为 v1.1.0 **最终 rebuild** 的实测基线（已含补齐的 OpenSSL 3.0.0 / Apache-2.0 等许可证 assets），与 `dist/SHA256SUMS.txt` 一致。测试前可用 `Get-FileHash dist\MotrixLite-release.apk -Algorithm SHA256` 复核 APK 未被替换；E7 需要的"不含原生产物"变体需自行构建（见 E7 说明）。

---

## 四、A 系列：验收用例

### A1 安装与启动

| 项 | 内容 |
| --- | --- |
| **前置条件** | 设备已开启"USB 调试"或允许"安装未知来源应用"；使用 `dist/MotrixLite-debug.apk` 或 `dist/MotrixLite-release.apk`（v1.1.0 已构建；release 为演示签名，正式发布需换证书）；设备 ABI 为 arm64-v8a；设备剩余空间建议 ≥ 300 MB |
| **操作步骤** | 1. `adb install -r dist\MotrixLite-release.apk`（或 debug 包，或点击 APK 安装）<br>2. 桌面找到「Motrix Lite」图标，冷启动<br>3. 观察首屏 10 秒<br>4. 切换到「设置」页再切回「任务」页<br>5. 旋转屏幕（若未锁定）<br>6. `adb shell dumpsys package com.motrixlite.downloader \| findstr versionName`<br>7. 打开「设置 → 关于」，记录 Gopeed / aria2 的打包状态与版本信息 |
| **预期结果** | 1. 安装成功，无 `INSTALL_FAILED_*` 报错<br>2. 启动进入任务列表，无闪退、无白屏<br>3. 空列表展示「还没有下载任务」与"暂不支持磁力/BT"提示<br>4. 页面切换正常，无状态丢失<br>5. 旋转后布局正常（或按系统设置不旋转）<br>6. 输出 `versionName=1.1.0`（versionCode=2）<br>7. 「关于」中 Gopeed / aria2 均显示"已打包"（本次最终 APK 已放入两个原生产物）<br>8. `adb logcat -s AndroidRuntime:E` 无 FATAL EXCEPTION |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>设备/系统：____________　证据：____________　备注：____________ |

---

### A2 HTTP 断点续传（暂停 / 恢复）

| 项 | 内容 |
| --- | --- |
| **前置条件** | `tools/range_test_server.py --port 8000 --size-mb 50 --throttle-kbps 300` 已启动；`adb reverse tcp:8000 tcp:8000` 已执行；应用连接数为默认 8（或设为 4 便于观察多段） |
| **操作步骤** | 1. 新建任务，URL：`http://127.0.0.1:8000/testfile.bin`<br>2. 点「解析链接」，确认显示大小 50 MB、断点续传"支持"<br>3. 开始下载，观察到进度 > 20%<br>4. 点任务卡片右侧的暂停按钮，记录已下载字节数<br>5. 等待 10 秒，确认进度不再增长、速度归零<br>6. 点「继续下载」，观察是否从暂停点继续（而非从 0 开始）<br>7. 再暂停一次，然后**杀掉应用进程**（`adb shell am force-stop com.motrixlite.downloader`）<br>8. 重新打开应用，观察任务状态<br>9. 点继续，等待完成<br>10. 校验落盘文件：`adb shell ls -l /sdcard/Download/MotrixLite/` 与 SHA-256 |
| **预期结果** | 1. 下载速度符合服务端限速（约 300 KB/s，接收端略低）<br>2. 暂停后进度冻结、状态显示「已暂停」、前台通知显示暂停/继续动作<br>3. 继续后进度从暂停点增长（不回退到 0）<br>4. force-stop 后重开，任务状态为「排队中」或「已暂停」（取决于 `desiredRunning` 与自动恢复设置），**进度保留**<br>5. 完成后文件大小精确等于 50 MB<br>6. 本地 `sha256sum` 与设备端文件哈希一致（用 `adb shell sha256sum /sdcard/Download/MotrixLite/testfile.bin`）<br>7. 分段数 ≥ 2（任务详情页显示"支持 Range（N 段）"） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>暂停点（%）：______　文件大小：______　SHA-256 是否一致：☐ 是 ☐ 否<br>证据：____________　备注：____________ |

> 说明：若 force-stop 后任务保持「排队中」但服务未启动，属于正常现象——应用被强制停止后系统不允许它自启，需要用户重新打开应用（这是 Android 的平台行为，不是缺陷）。

> 双内核说明：A2 在**内置引擎**下执行；Gopeed / aria2 的断点续传与锁屏分别见 E2、E3。执行前请确认设置页「下载引擎」为"内置引擎（OkHttp）"。

---

### A3 锁屏 / 后台持续下载 10 分钟

| 项 | 内容 |
| --- | --- |
| **前置条件** | A2 的服务端已启动（建议 `--size-mb 500` 或 `--throttle-kbps 500` 以保证 10 分钟内不结束）；通知权限已授予；电池优化已设为"不限制"（可选，但建议记录状态） |
| **操作步骤** | 1. 开始下载一个大文件<br>2. 按电源键锁屏<br>3. 保持锁屏 10 分钟（可用 `adb shell dumpsys battery` 确认屏幕状态）<br>4. 期间每分钟下拉通知栏（或亮屏不解锁）观察进度是否变化<br>5. 10 分钟后解锁，查看任务进度与通知<br>6. 后台挂机：按 Home 键退出应用，重复 5 分钟观察<br>7. 检查 `adb shell dumpsys activity services com.motrixlite.downloader` |
| **预期结果** | 1. 锁屏期间下载持续进行，进度增长（速度可能因省电策略下降）<br>2. 通知栏持续显示聚合进度与速度<br>3. 解锁后应用内进度与通知一致，无回退<br>4. `dumpsys` 中服务处于前台状态，`isForeground=true`，类型含 `dataSync`<br>5. `adb logcat -s DownloadService:V` 无 `startForeground failed` |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>10 分钟后进度：______%　是否仍在下载：☐ 是 ☐ 否<br>是否被系统杀死：☐ 是 ☐ 否　证据：____________ |

> 双内核说明：A3 在**内置引擎**下执行；Gopeed / aria2 的锁屏 10 分钟分别在 E2、E3 中验证。若切换引擎，请记录当前引擎与 `task.engine`。

---

### A4 通知权限拒绝后的降级

| 项 | 内容 |
| --- | --- |
| **前置条件** | Android 13+ 设备；应用**首次安装**（或已执行 `adb shell pm clear com.motrixlite.downloader` 重置权限状态） |
| **操作步骤** | 1. 首次启动应用，在系统弹出的通知权限对话框中选择「不允许」<br>2. 观察应用内提示<br>3. 返回任务页，确认顶部是否有通知横幅<br>4. 新建并开始一个下载任务<br>5. 观察 Snackbar 提示<br>6. 锁屏 2 分钟后再回来，确认下载仍在继续<br>7. 到「设置 → 通知」中手动开启权限，返回应用（触发 `ON_RESUME`）<br>8. 确认横幅消失、通知恢复 |
| **预期结果** | 1. 拒绝后**不崩溃、不阻断**，应用继续可用<br>2. 任务页顶部出现横幅："通知权限未开启：后台下载会继续……"<br>3. 开始下载时出现 Snackbar 提示通知不可见<br>4. 下载正常进行（前台服务仍可启动，只是通知不可见）<br>5. 设置页"通知权限"一行显示"未开启（降级运行）"<br>6. 重新授权并返回后，横幅消失，通知栏出现进度<br>7. `adb shell dumpsys notification --noredact \| findstr MotrixLite` 在授权前无记录、授权后有记录 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>拒绝后是否仍能下载：☐ 是 ☐ 否　横幅是否出现：☐ 是 ☐ 否<br>证据：____________　备注：____________ |

> 说明：Android 13+ 中通知权限被拒时，前台服务仍可运行，但通知不会显示（系统行为）。这正是本应用采用"应用内横幅 + Snackbar"降级提示的原因。

---

### A5 文件在文件管理器中可见

| 项 | 内容 |
| --- | --- |
| **前置条件** | 已完成 A2（下载成功）；设备为 Android 10+（验证 MediaStore 路径）；另需一台 Android 8/9 设备或模拟器验证回退路径（可选） |
| **操作步骤** | 1. 完成一次下载，等待状态变为「已完成」<br>2. 打开系统「文件管理」，进入 `内部存储/Download/MotrixLite`<br>3. 确认文件存在、大小正确、文件名与任务一致<br>4. 点击文件，确认可被系统播放器/查看器打开<br>5. `adb shell ls -l /sdcard/Download/MotrixLite/`<br>6. 在应用内长按/详情页点「打开文件」<br>7. （Android 8/9，可选）确认文件位于 `Android/data/com.motrixlite.downloader/files/Download/MotrixLite/` |
| **预期结果** | 1. 文件出现在 `Download/MotrixLite`，通知栏与文件管理器时间戳一致<br>2. 文件大小等于任务显示大小<br>3. 系统可直接打开（MIME 正确）<br>4. 应用内「打开文件」可正常跳转<br>5. **全程未申请存储权限**（`adb shell dumpsys package com.motrixlite.downloader \| findstr permission` 中无 `READ/WRITE_EXTERNAL_STORAGE`、无 `MANAGE_EXTERNAL_STORAGE`）<br>6. Android 8/9 上文件位于应用专属目录，文件管理器能浏览到，但卸载应用会一并删除（这是设计限制） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>实际路径：____________　大小是否一致：☐ 是 ☐ 否<br>是否申请了存储权限：☐ 否（预期） ☐ 是（缺陷）　证据：____________ |

---

### A6 连续 5 个任务：暂停 / 恢复 / 删除 / 重启状态

| 项 | 内容 |
| --- | --- |
| **前置条件** | 服务端已启动；准备 5 个可区分的 URL（可用 `?n=1`…`?n=5` 查询参数，服务器会忽略但文件名相同——建议改用 `--file` 提供不同文件，或用 5 个不同大小的文件避免同名）<br>并发上限保持默认 3，连接数默认 8 |
| **操作步骤** | 1. 连续创建 5 个任务，全部开始<br>2. 观察同时处于"下载中"的任务数是否 ≤ 3，其余为「排队中」<br>3. 暂停其中 2 个（记作 P1、P2）<br>4. 对其中 1 个已暂停任务点「继续下载」<br>5. 删除其中 1 个未完成任务（记作 D1），确认 staging 文件被清理<br>6. 等待剩余任务进行到不同进度<br>7. `adb shell am force-stop com.motrixlite.downloader`，然后重新打开应用<br>8. 核对 5 个任务（4 个存在 + 1 个已删除）的状态与进度<br>9. 重启设备（`adb reboot`），再次打开应用核对<br>10. 全部完成后点右上角「清理已完成任务（保留文件）」 |
| **预期结果** | 1. 运行中任务数不超过并发上限<br>2. 暂停的任务立即停止增长，状态「已暂停」<br>3. 恢复的任务从原进度继续<br>4. 删除的任务从列表消失，staging 中对应 `.part` 被删除（`adb shell ls /sdcard/Android/data/com.motrixlite.downloader/files/staging`）<br>5. force-stop 重开后：此前运行中的任务变为「排队中/已暂停」，**已下载进度保留**；用户暂停的任务保持「已暂停」<br>6. 设备重启后同样能恢复，且**不会自动开始**（需用户打开应用）<br>7. 清理已完成任务后列表为空，但文件仍在 `Download/MotrixLite`<br>8. 全程无崩溃、无任务重复、无状态错乱 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>force-stop 后状态：____________　重启后状态：____________<br>是否有任务丢失/重复：☐ 否 ☐ 是（编号：____）　证据：____________ |

---

### A7 限速 / 并发 / 仅 Wi-Fi 策略

| 项 | 内容 |
| --- | --- |
| **前置条件** | 服务端 `--throttle-kbps 0`（不限速）或使用公网大文件；可切换 Wi-Fi 与蜂窝（或使用飞行模式模拟） |
| **操作步骤** | **限速**：1. 设置页把"全局限速"设为 1024 KB/s，开始下载，观察速度稳定在 ~1 MB/s；2. 改为 0（不限速），确认速度上升；3. 改为 256 KB/s，确认下降<br>**并发**：4. 并发设为 1，创建 3 个任务，确认只有 1 个在下载；5. 改为 5，确认 3 个同时下载<br>**连接数**：6. 单任务连接数设为 1 与 16，分别在任务详情页确认分段数（1 段 / 多段）<br>**仅 Wi-Fi**：7. 打开"仅 Wi-Fi 下载"，切到蜂窝网络，新建任务，确认状态为「等待 Wi-Fi」；8. 切回 Wi-Fi，确认自动开始；9. 下载过程中从 Wi-Fi 切到蜂窝，观察行为并记录 |
| **预期结果** | 1. 限速生效且**全局共享**（多任务总和 ≈ 设定值，而非每任务各自限速）<br>2. 限速值改为 0 后立即解除限制<br>3. 设置变更**无需重启任务**即刻生效<br>4. 并发上限生效，排队任务显示「排队中」<br>5. 连接数为 1 时单连接（详情页显示 1 段）；16 时按总大小切分且不超过 16 段<br>6. 仅 Wi-Fi 开启时，蜂窝下任务进入「等待 Wi-Fi」，不产生流量<br>7. 连回 Wi-Fi 后自动转「排队中」并开始<br>8. 下载中切换网络的行为需如实记录（当前实现只在调度时判断，已建立的连接可能继续） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>限速实测（1 MB/s 设定）：______ KB/s　并发实测：______<br>仅 Wi-Fi 是否阻断：☐ 是 ☐ 否　切网行为记录：____________<br>证据：____________ |

---

## 五、B 系列：补充与边界用例

### B1 Android 15 前台服务时限（dataSync 6 小时 / 24 小时）

| 项 | 内容 |
| --- | --- |
| **前置条件** | Android 15（API 35）设备；无法真实等待 6 小时的场景见下方"替代验证" |
| **操作步骤** | 1. 静态检查：`adb shell dumpsys package com.motrixlite.downloader \| findstr foregroundServiceType`<br>2. 运行中检查：`adb shell dumpsys activity services com.motrixlite.downloader`<br>3. 长时间挂机：保持下载 > 6 小时（可用超大文件 + 低限速）<br>4. 触发后观察应用提示与任务状态<br>5. 重新打开应用，验证可继续 |
| **预期结果** | 1. 服务声明含 `dataSync`，运行时 `foregroundServiceType` 包含 `dataSync`（`0x1`）<br>2. 达到系统时限时触发 `onTimeout(startId, fgsType)`，应用**优雅暂停**全部任务并给出说明<br>3. 服务退出前台并停止，不发生崩溃<br>4. 重新打开应用后可继续下载，进度保留 |
| **替代验证（无 6 小时条件时）** | 执行 `adb shell device_config list activity_manager \| findstr /i "data_sync fgs"`，若存在可调超时开关，可临时把时限缩短到数分钟观察；**不同厂商 ROM 可能移除该开关，此时只能做静态检查 + 记录"未验证"**。不得以静态检查代替结论。 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行（无 6 小时条件）<br>是否触发 onTimeout：☐ 是 ☐ 否 ☐ 未验证　证据：____________ |

---

### B2 HyperOS 后台限制（自启动 / 省电策略 / 多任务锁定）

| 项 | 内容 |
| --- | --- |
| **前置条件** | 小米/红米 HyperOS 或 MIUI 设备 |
| **操作步骤** | 1. 进入 `设置 → 应用设置 → 应用管理 → Motrix Lite`<br>2. 打开「自启动」，将「省电策略」设为「无限制」<br>3. 打开「通知管理」，允许通知<br>4. 进入多任务（最近任务）界面，下拉 Motrix Lite 卡片并点击锁图标锁定<br>5. 回到应用开始下载，锁屏挂机 30–60 分钟<br>6. 在应用「设置 → 通知与后台运行」中点击"HyperOS 自启动 / 省电策略"按钮，确认能跳转到系统页面<br>7. 若无对应页面，确认回退到应用详情页 |
| **预期结果** | 1. 按上述配置后，锁屏 30–60 分钟下载不中断（或被中断后能自动恢复）<br>2. 应用内按钮能跳到 MIUI 安全中心的相应页面；组件名不匹配时回退到应用详情页，**不崩溃**<br>3. 应用内「电池优化」按钮可拉起系统的电池优化请求页<br>4. 未配置白名单时，能复现"息屏一段时间后任务停止"的现象并记录（这是预期限制，见 KI-08） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>机型/ROM：____________　锁屏 30 分钟后进度：______%<br>跳转是否成功：☐ 成功 ☐ 回退到详情页　证据：____________ |

> 说明：HyperOS 的"自启动""省电策略""后台弹出界面"等开关**没有公开 API**，应用只能引导用户手动配置，无法通过代码直接修改。

---

### B3 SAF 自定义保存目录

| 项 | 内容 |
| --- | --- |
| **前置条件** | 已安装支持 SAF 的文件管理器（系统自带即可）；准备一个可写目录（如 `Documents/MotrixTest`） |
| **操作步骤** | 1. 「设置 → 保存位置」选择"自定义文件夹（SAF）"<br>2. 点「选择文件夹」，在系统选择器中选中目标目录并允许访问<br>3. 确认设置页显示所选文件夹名<br>4. 新建并完成一个下载任务<br>5. 用文件管理器确认文件出现在该目录<br>6. 重启应用后再下一个文件，确认无需重新授权<br>7. （异常场景）在系统设置中撤销该目录授权，再下一个文件，观察错误提示 |
| **预期结果** | 1. 选择目录后立即生效，新任务保存到该目录<br>2. 文件可被文件管理器看到并可打开<br>3. 重启应用后授权仍有效（`takePersistableUriPermission`）<br>4. 撤销授权后任务进入「失败」并提示"无法访问所选保存文件夹/请重新选择"，**不崩溃**<br>5. 未选择目录就切到 SAF 模式时，设置页显示红色提示 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>目录 URI：____________　重启后是否需重新授权：☐ 否（预期） ☐ 是<br>证据：____________ |

---

### B4 异常链接（404 / 403 / 401 / 超时 / DNS 失败）

| 项 | 内容 |
| --- | --- |
| **前置条件** | 准备以下 URL（8001 端口需先执行 `adb reverse tcp:8001 tcp:8001`）：<br>404：`http://127.0.0.1:8001/nope.bin`（`python -m http.server 8001`）<br>403：`https://httpbin.org/status/403`<br>401：`https://httpbin.org/status/401`<br>超时：`http://10.255.255.1/big.bin`<br>DNS：`http://no-such-host.invalid/file.bin` |
| **操作步骤** | 1. 逐个新建任务并开始<br>2. 记录每个任务的最终状态与错误文案<br>3. 点任务详情查看"错误信息"<br>4. 对 404/403 任务点「重试」，确认不会无限重试<br>5. 确认应用无崩溃、UI 可继续操作 |
| **预期结果** | 1. 404 → "文件不存在（HTTP 404）……"，状态「下载失败」，**不自动重试**<br>2. 403 → "服务器拒绝访问（HTTP 403）……"，不自动重试<br>3. 401 → 提示需要登录或授权，当前版本不支持鉴权下载<br>4. 超时 → "无法连接服务器/连接超时……"（可能先经历 3 次自动重试，约 26 秒）<br>5. DNS 失败 → "无法解析服务器域名……"<br>6. 失败任务保留在列表中，可手动重试或删除<br>7. 通知栏出现失败提醒（若通知权限已授予） |
| **结果记录** | 404：______　403：______　401：______　超时：______　DNS：______<br>证据：____________ |

---

### B5 服务器不支持 Range（降级单连接）

| 项 | 内容 |
| --- | --- |
| **前置条件** | 启动无 Range 支持的静态服务：`python -m http.server 8001 --directory tools`；`adb reverse tcp:8001 tcp:8001` |
| **操作步骤** | 1. 新建任务 `http://127.0.0.1:8001/testfile.bin`<br>2. 点「解析链接」，记录"断点续传"提示与大小<br>3. 开始下载并完成<br>4. 在任务详情页确认分段信息<br>5. 尝试暂停后再继续（观察是否从头开始） |
| **预期结果** | 1. 解析后显示"断点续传：服务器不支持"<br>2. 下载以**单连接**进行，速度明显低于多段（对比 A2）<br>3. 详情页显示"服务器不支持 Range（单连接）"，分段数为 1<br>4. 文件大小与内容正确<br>5. 暂停后继续可能从头重下（若服务器对带 Range 的请求仍返回 200，会清空已下载数据）——如实记录 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>分段数：______　暂停后是否从头开始：☐ 是 ☐ 否　证据：____________ |

---

### B6 空文件（0 字节）

| 项 | 内容 |
| --- | --- |
| **前置条件** | 创建 0 字节文件并启动服务：<br>`New-Item tools\empty.bin -ItemType File -Force`<br>`python tools\range_test_server.py --port 8002 --file tools\empty.bin` |
| **操作步骤** | 1. 新建任务 `http://127.0.0.1:8002/testfile.bin`（服务器对任意路径都返回该文件）<br>2. 开始下载<br>3. 观察状态与耗时<br>4. 在文件管理器中确认文件存在且为 0 字节 |
| **预期结果** | 1. 任务快速进入「已完成」，不卡在"下载中"<br>2. `Download/MotrixLite` 下生成 0 字节文件<br>3. 无除零崩溃、无 "剩余时间 计算中" 卡死<br>4. 通知正常（若已授权） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>状态：______　文件大小：______ 字节　证据：____________ |

---

### B7 大文件（≥ 1 GB）与长时间下载

| 项 | 内容 |
| --- | --- |
| **前置条件** | 设备可用空间 ≥ 文件大小 × 2.5（发布阶段需要约 2 倍临时空间）；服务端 `--size-mb 2048` 或 `--file` 指向大 ISO；建议使用 Wi-Fi |
| **操作步骤** | 1. 开始下载 ≥ 1 GB 文件，观察进度、速度、剩余时间<br>2. 中途暂停 / 继续各 2 次<br>3. 观察系统剩余空间变化（下载中 vs 完成后）<br>4. `adb shell df -h /sdcard` 记录<br>5. 完成后校验大小与哈希<br>6. 记录总耗时与平均速度 |
| **预期结果** | 1. 进度条与百分比持续更新，无卡死<br>2. 剩余时间估算合理（会随速度波动）<br>3. 下载阶段占用 ≈ 文件大小（staging），发布阶段峰值 ≈ 2 倍，完成后回到 ≈ 1 倍<br>4. 文件大小/哈希正确<br>5. 无 OOM、无 ANR |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>文件大小：______　峰值额外占用：______　总耗时：______<br>哈希是否一致：☐ 是 ☐ 否　证据：____________ |

---

### B8 重名文件

| 项 | 内容 |
| --- | --- |
| **前置条件** | `Download/MotrixLite` 下已存在 `testfile.bin` |
| **操作步骤** | 1. 再次下载同名文件并完成<br>2. 查看 `Download/MotrixLite` 目录<br>3. 重复 2 次<br>4. 改用 SAF 目录重复一次<br>5. 在应用内删除其中一个任务并勾选"同时删除文件"，确认只删对应文件 |
| **预期结果** | 1. 新文件命名为 `testfile (1).bin`、`testfile (2).bin`…（保留扩展名）<br>2. 原文件不被覆盖<br>3. SAF 模式下同样去重<br>4. 删除任务+文件时只删除该任务的 `destinationUri` 指向的文件<br>5. 不出现同名冲突导致覆盖或失败 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>实际文件名：____________　是否覆盖原文件：☐ 否（预期） ☐ 是<br>证据：____________ |

---

## 六、E 系列：双内核专项（1.1.0 新增，全部待验证）

> 前置：完成 A1；设备为 arm64-v8a；测试 APK **已放入** `app/libs/libgopeed.aar` 与 `app/src/main/jniLibs/arm64-v8a/libaria2c.so`（E7 例外，用未放入产物的 APK）。
> 测试源建议使用 `tools/range_test_server.py`（支持 Range/ETag/限速），E2/E3 需要至少 10 分钟不结束的下载（如 `--size-mb 500 --throttle-kbps 500`）。

### E1 引擎选择器可用性与不可用提示

| 项 | 内容 |
| --- | --- |
| **前置条件** | 两份 APK：① `dist/` 中已含两个原生产物的最终 APK；② 不含原生产物的变体（临时移出 `app/libs/libgopeed.aar` 与 `app/src/main/jniLibs/` 后 `assembleDebug`，见 E7）。 |
| **操作步骤** | 1. 安装 APK①，打开「设置 → 下载引擎」<br>2. 检查三个选项的可用状态与说明文案<br>3. 分别选择 Gopeed、aria2，返回「关于」核对引擎状态<br>4. 安装 APK②（先卸载或覆盖），重复步骤 1–3<br>5. 在 APK② 上尝试新建任务（当前引擎为内置），再到设置页尝试选择不可用引擎 |
| **预期结果** | 1. APK① 中 Gopeed/aria2 可选；标题与副标题显示许可证与能力（Gopeed 标注"不支持全局限速"）<br>2. APK② 中两个原生引擎显示"当前 APK 不可用"且**不可选择**；点击/新建任务被拒绝并提示"内核未打包进当前 APK"<br>3. 两种情况都**不崩溃**；切换只影响新建任务 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>APK① 可用状态：______　APK② 提示：______　证据：____________ |

---

### E2 Gopeed 引擎：HTTP 断点续传 + 锁屏 10 分钟

| 项 | 内容 |
| --- | --- |
| **前置条件** | 含 Gopeed AAR 的 APK；设置页选择"Gopeed"；服务端已启动（`--size-mb 500 --throttle-kbps 500`）；通知权限已授予；电池优化按 B2 建议配置。 |
| **操作步骤** | 1. 设置「下载引擎」为 Gopeed，新建任务 `http://127.0.0.1:8000/testfile.bin`<br>2. 开始下载，确认任务卡片显示 `· Gopeed`，详情页"下载引擎=Gopeed"<br>3. 进度 > 20% 后按电源键锁屏，保持 **10 分钟**<br>4. 期间每分钟亮屏观察通知/进度（可不解锁）<br>5. 解锁后暂停，记录已下载字节，等 5 秒再继续<br>6. 完成后确认文件发布到 `Download/MotrixLite`，校验大小/哈希<br>7. `adb shell ps -A \| findstr gopeed`（进程内无独立进程属预期）<br>8. 记录测试期间 App 是否崩溃（`adb logcat -s AndroidRuntime:E`） |
| **预期结果** | 1. Gopeed 任务可创建、进度/速度正常刷新<br>2. 锁屏 10 分钟期间下载持续（或按 HyperOS 策略暂停后可恢复）<br>3. 暂停后再继续从暂停点续传，不从 0 开始<br>4. 完成后文件大小/哈希正确，出现在 `Download/MotrixLite`<br>5. 无 App 崩溃；任务详情显示 Gopeed task id 与原始状态<br>6. 记录"全局限速对 Gopeed 不生效"的实际表现（KI-23），不记为缺陷 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>锁屏 10 分钟后进度：______%　暂停后续传：☐ 正常 ☐ 从 0 开始<br>文件大小/哈希：______　是否崩溃：☐ 否 ☐ 是　证据：____________ |

> 说明：Gopeed 在全部任务暂停后会停止内嵌服务并清空引擎任务 ID，恢复时重建任务。若实测"暂停后恢复从 0 开始"，按 [KNOWN_ISSUES.md](KNOWN_ISSUES.md) KI-29 记录为产品限制（不是崩溃/数据损坏缺陷），并在结果备注中写明。

---

### E3 aria2 引擎：HTTP 断点续传 + 锁屏 10 分钟

| 项 | 内容 |
| --- | --- |
| **前置条件** | 含 aria2 二进制的 APK；设置页选择"aria2"；服务端已启动；建议先执行 `adb shell ps -A \| findstr aria2c` 记录基线；其余同 E2。 |
| **操作步骤** | 1. 设置「下载引擎」为 aria2，新建任务 `http://127.0.0.1:8000/testfile.bin`<br>2. 开始下载，确认卡片显示 `· aria2`，详情页显示 gid 与原始状态（如 `active`）<br>3. 进度 > 20% 后暂停，记录字节，再继续，确认续传<br>4. 重新下载（或再建任务），锁屏保持 **10 分钟**<br>5. 解锁后确认进度；期间用 `adb shell ps -A \| findstr aria2c` 确认子进程存在<br>6. 完成并校验文件<br>7. 暂停全部任务，等待 10 秒后再次检查 `aria2c` 进程是否被回收 |
| **预期结果** | 1. aria2 子进程按需启动，任务详情出现 gid/原始状态<br>2. 暂停/恢复生效；锁屏 10 分钟期间下载持续或可恢复<br>3. 文件发布到 `Download/MotrixLite`，大小/哈希正确<br>4. 全部任务暂停/完成后 `aria2c` 子进程回收（无残留进程）<br>5. 无 App 崩溃；`adb logcat -s Aria2Engine:W` 无持续报错 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>锁屏 10 分钟后进度：______%　gid：______<br>暂停后续传：☐ 正常 ☐ 从 0 开始　进程回收：☐ 是 ☐ 否　证据：____________ |

---

### E4 切换引擎后新建任务（旧任务不变）

| 项 | 内容 |
| --- | --- |
| **前置条件** | 两个原生内核均已打包；内置引擎任务与 Gopeed/aria2 任务各至少一个。 |
| **操作步骤** | 1. 用内置引擎创建任务 A，开始下载<br>2. 到设置页切换为 Gopeed，新建任务 B<br>3. 再切换为 aria2，新建任务 C<br>4. 对比 A 的详情页"下载引擎"与进度；对比 B/C 的引擎字段<br>5. 杀死 App 重启，核对三个任务的引擎与状态 |
| **预期结果** | 1. A 始终显示"内置引擎"，进度不受切换影响<br>2. B 为 Gopeed、C 为 aria2；新建任务弹层显示的引擎与实际一致<br>3. 任务卡片对非内置引擎显示 `· Gopeed` / `· aria2`<br>4. 重启后 `task.engine` 保持不变（JSON v2 持久化）<br>5. 切换设置不触发旧任务重启或引擎迁移 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>A/B/C 引擎与状态：____________　重启后是否一致：☐ 是 ☐ 否　证据：____________ |

---

### E5 双引擎任务并存

| 项 | 内容 |
| --- | --- |
| **前置条件** | 两个原生内核均已打包；准备两个不同 URL/文件名（避免重名）；Wi-Fi 环境。 |
| **操作步骤** | 1. 用 aria2 创建任务 A 并开始下载<br>2. 切换到 Gopeed，创建任务 B 并开始下载<br>3. 观察通知聚合、任务列表状态、两个任务是否同时推进<br>4. 用 `adb shell dumpsys activity services com.motrixlite.downloader` 确认只有一个前台服务<br>5. 用 `adb shell ps -A \| findstr aria2c` 确认 aria2 子进程存在；`adb logcat -s GopeedEngine:W` 观察 Gopeed<br>6. 记录内存（`adb shell dumpsys meminfo com.motrixlite.downloader`）与设备发热/耗电（可选）<br>7. 完成或暂停两个任务，确认两个内核都被回收 |
| **预期结果** | 1. 两个任务同时下载，通知聚合数字正确<br>2. 只有一个 `DownloadService` 前台服务<br>3. 两个内核各自运行、互不干扰；任务状态/进度正确回写<br>4. 全部任务结束/暂停后 aria2c 与 Gopeed 服务均停止，无泄漏<br>5. 无崩溃；记录资源占用作为真机数据 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>两任务是否同时推进：☐ 是 ☐ 否　内存占用：______<br>内核是否回收：☐ 是 ☐ 否　证据：____________ |

---

### E6 服务停止 / 杀进程后的内核回收

| 项 | 内容 |
| --- | --- |
| **前置条件** | 两个原生内核均已打包；至少一个 aria2 任务与一个 Gopeed 任务处于下载中或暂停中。 |
| **操作步骤** | 1. 两个引擎各创建一个任务并开始下载<br>2. `adb shell am force-stop com.motrixlite.downloader` 杀进程<br>3. 立即检查 `adb shell ps -A \| findstr aria2c` 是否还有孤儿进程<br>4. 等待 10 秒后再次检查<br>5. 重新打开 App，检查任务状态；用 `adb logcat -s Aria2Engine:W GopeedEngine:W` 观察恢复过程<br>6. 再次进入任务列表，暂停全部任务，观察内核是否停止<br>7. 设备重启后再核对一遍（可选） |
| **预期结果** | 1. force-stop 后无孤儿 `aria2c`（`--stop-with-process` 与 shutdown 双重保障）<br>2. Gopeed 端口/线程随进程销毁，无残留监听端口（可用 `adb shell cat /proc/net/tcp` 抽查，或重启后无异常）<br>3. 重启 App 后任务按引擎恢复：原生任务 `engineTaskId` 已清空并按 URL 重建；进度按 `.aria2`/Gopeed 机制续传<br>4. 暂停全部后两个内核都停止，服务 4 秒后空闲退出<br>5. 无崩溃、无重复任务、无状态错乱 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>孤儿 aria2c：☐ 无 ☐ 有　重启后恢复：☐ 正常 ☐ 异常<br>证据：____________ |

---

### E7 AAR / 二进制加载失败不闪退

| 项 | 内容 |
| --- | --- |
| **前置条件** | 使用**未放入** `libgopeed.aar` / `libaria2c.so` 的 APK（或人为让 AAR 类不可加载）。本次 `dist/` 的最终 APK **已包含**两个原生产物，需要临时变体：把 `app/libs/libgopeed.aar` 与 `app/src/main/jniLibs/` 移出（或重命名）后执行 `.\gradlew.bat :app:assembleDebug`，再还原文件。 |
| **操作步骤** | 1. 安装不含原生产物的 APK，启动 App<br>2. 打开设置页，检查两个原生引擎的显示状态<br>3. 若设置中已保存为 Gopeed/aria2（可用旧数据或改设置后重装），直接新建任务<br>4. 观察 Snackbar/提示与任务列表变化<br>5. 检查进程是否存活（App 不应退出） |
| **预期结果** | 1. App 正常启动，不崩溃、不 ANR<br>2. 设置页显示"当前 APK 不可用"并禁用选择<br>3. 新建任务被拒绝并提示引擎未安装到当前 APK<br>4. Controller 不会因缺少 AAR/二进制而崩溃；相关已有任务被标记失败并给出原因（若存在）<br>5. 切回内置引擎后可正常下载 |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>是否闪退：☐ 否 ☐ 是　提示文案：______　证据：____________ |

---

### E8 许可证页面 / 源码 offer 检查

| 项 | 内容 |
| --- | --- |
| **前置条件** | 含两个原生产物的 APK；任意设备（无需下载完成）。 |
| **操作步骤** | 1. 打开「设置 → 关于」，检查版本、引擎打包状态、开源许可入口<br>2. 点开「开源许可 / 源码获取」，逐项核对：GPLv3 声明、Gopeed（GPL-3.0）与 aria2（GPL-2.0-or-later；静态链接 OpenSSL 3.0.0 / Apache-2.0）、源码 URL、书面要约说明<br>3. 点击「打开源码仓库」确认可跳转<br>4. 用 `adb shell unzip -l`（或解压 APK）检查 `assets/licenses/` 是否包含 GPLv3、GPLv2、aria2 COPYING、历史 aria2 LICENSE.OpenSSL/说明、OpenSSL 3.0.0 Apache-2.0 全文+版权、Gopeed LICENSE、各静态依赖许可证、DEPENDENCY-VERSIONS、SOURCE-OFFER、THIRD_PARTY_NOTICES<br>5. 比对 APK 内文本与仓库 `licenses/`、`native/licenses/` 是否一致 |
| **预期结果** | 1. 关于页显示 1.1.0 与引擎状态<br>2. 许可对话框包含全部 GPL 组件、版本、源码地址与书面要约；明确"整体 GPLv3、不可闭源分发"<br>3. 源码仓库链接可打开（或给出可操作提示）<br>4. APK assets 内许可证文本齐全、可读、与仓库一致<br>5. 若发现缺失，按缺陷记录（GPL 合规问题优先级最高） |
| **结果记录** | ☐ 通过　☐ 失败　☐ 阻塞　☐ 未执行<br>缺失文件：______　链接是否可用：☐ 是 ☐ 否　证据：____________ |

---

## 七、结果汇总表

| 编号 | 用例 | 结果 | 缺陷编号 | 备注 |
| --- | --- | --- | --- | --- |
| A1 | 安装与启动 | 未执行 | | |
| A2 | 断点续传（暂停/恢复） | 未执行 | | |
| A3 | 锁屏/后台 10 分钟 | 未执行 | | |
| A4 | 通知权限拒绝降级 | 未执行 | | |
| A5 | 文件管理器可见 | 未执行 | | |
| A6 | 5 任务 + 重启恢复 | 未执行 | | |
| A7 | 限速/并发/仅 Wi-Fi | 未执行 | | |
| B1 | Android 15 FGS 时限 | 未执行 | | |
| B2 | HyperOS 后台限制 | 未执行 | | |
| B3 | SAF 目录 | 未执行 | | |
| B4 | 异常链接 | 未执行 | | |
| B5 | 无 Range 服务器 | 未执行 | | |
| B6 | 空文件 | 未执行 | | |
| B7 | 大文件 | 未执行 | | |
| B8 | 重名文件 | 未执行 | | |
| E1 | 引擎选择器/不可用提示 | 未执行 | | |
| E2 | Gopeed 断点续传 + 锁屏 10 分钟 | 未执行 | | |
| E3 | aria2 断点续传 + 锁屏 10 分钟 | 未执行 | | |
| E4 | 切换引擎后新旧任务隔离 | 未执行 | | |
| E5 | 双引擎任务并存 | 未执行 | | |
| E6 | 进程/内核回收 | 未执行 | | |
| E7 | AAR/二进制缺失不闪退 | 未执行 | | |
| E8 | 许可证/源码 offer | 未执行 | | |

**验收结论**：☐ 通过　☐ 有条件通过（缺陷：______）　☐ 不通过

---

## 八、缺陷记录表

| 编号 | 关联用例 | 严重级别 | 现象 | 复现步骤 | 日志/截图 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| D-001 | | 致命/严重/一般/轻微 | | | | 新建/修复中/已验证/关闭 |
| D-002 | | | | | | |

**严重级别定义**

| 级别 | 定义 |
| --- | --- |
| 致命 | 崩溃、数据丢失、无法启动 |
| 严重 | 核心功能不可用（下载失败、无法续传、文件损坏） |
| 一般 | 功能可用但体验明显受损（通知不更新、限速不准） |
| 轻微 | 文案、间距、边缘情况 |

---

## 九、日志与证据采集

```powershell
# 清空日志后开始测试
adb logcat -c

# 只看本应用相关（含两个原生引擎的日志 tag）
adb logcat -s DownloadService:V Aria2Engine:W GopeedEngine:W AndroidRuntime:E ActivityManager:I

# 保存到文件
adb logcat -d > logcat_motrixlite.txt

# 服务状态
adb shell dumpsys activity services com.motrixlite.downloader

# aria2 子进程（下载中应有；暂停/结束后应消失）
adb shell ps -A | Select-String "aria2c"
adb shell ps -A | findstr aria2c

# Gopeed 为同进程服务，无独立进程；检查端口可由 /proc/net/tcp 抽查
adb shell cat /proc/net/tcp

# 引擎临时目录（需 run-as 或 debuggable 包）
adb shell run-as com.motrixlite.downloader ls -l files
adb shell run-as com.motrixlite.downloader ls -l cache

# 通知状态
adb shell dumpsys notification --noredact | Select-String "MotrixLite"

# 已授予权限
adb shell dumpsys package com.motrixlite.downloader | Select-String "granted=true"

# 电池优化白名单
adb shell dumpsys deviceidle whitelist | Select-String "motrixlite"

# 文件与哈希
adb shell ls -l /sdcard/Download/MotrixLite/
adb shell sha256sum /sdcard/Download/MotrixLite/testfile.bin

# APK 内许可证资产（在电脑上执行）
unzip -l app\build\outputs\apk\debug\app-debug.apk | findstr licenses
adb pull /data/app/com.motrixlite.downloader-*/base.apk base.apk   # 如需要
```

> 部分 ROM 限制 `adb shell` 访问 `/sdcard/Download`，可改用 `adb pull` 拉回本地再计算哈希。

---

## 十、判定标准

一次验收被视为**通过**，需同时满足：

1. A1–A7 全部为「通过」；
2. **E1–E8 全部为「通过」**（双内核与许可证合规属于 1.1.0 的交付范围；E7 若因构建方式无法复现，需明确标记「阻塞」并说明）；
3. 无「致命」或「严重」级别未关闭缺陷；
4. B 系列中 B4、B5、B6、B8 为「通过」；B1、B2、B7 若因设备/时间条件无法执行，需明确标记「阻塞」并说明原因，不得默认视为通过；
5. 真机验证记录已回填到本文件与 `README.md` 的"验证状态"章节。

> 再次强调：在回填真实结果之前，任何"已通过真机验收"的说法都不成立。E 系列尤其重要——两个原生内核目前只有构建级与静态验证。
