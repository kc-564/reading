# 阅读器 App 增量架构设计 + 任务分解（Phase 2–5 + F04 调研）

> 包名 `com.example.reader`，源码根 `app/src/main/java/com/example/reader/`
> 技术栈：Kotlin 1.9.22 · Compose BOM 2023.10.01（foundation 1.5.4）· Material3 · Room 2.6.1 · Navigation Compose 2.7.5 · DataStore 1.0.0 · juniversalchardet 2.4.0 · KSP
> compileSdk/targetSdk 34，minSdk 26。**本机无 Android SDK，所有编译靠 CI `assembleDebug` 验证**，因此下文所有 API 均限定在 BOM 2023.10.01 之内（`HorizontalPager` 位于 `androidx.compose.foundation.pager`，可用）。
> 本设计在 Phase 0/1 已交付代码之上**增量扩展，不推翻**。

---

## 1. 实现方案与框架选型

### 1.1 核心技术难点
1. **排版引擎增强**：需支持字号缩放(1×–3×)、精确像素段距、字间距/对齐/首行缩进、章首页标题高度预留、超大章节分块测量、翻页/滚动双模式、RTL；且**测量样式与渲染样式必须完全一致**，避免换字体/字号后行断点错位（D06/D08/C02）。
2. **跨章全书级进度**：总页数 / 当前跨章页 / 全书百分比；改参数重排时进度保持不跳开头（C01/C07）。
3. **布局缓存 key**：`文件指纹 + 排版参数 + 屏幕尺寸`，命中即跳过整本测量（C07）。
4. **多主题**：白天/夜间/OLED 纯黑/羊皮纸，状态栏与阅读区配色随之变化（C03）。
5. **无本机编译**：代码须在指定依赖组合下编译通过。

### 1.2 框架 / 库选型
| 模块 | 选型 | 说明 |
|------|------|------|
| 排版 | 继续用 Compose `TextMeasurer`（`rememberTextMeasurer`）+ 自研段落级测量 | **不引入三方排版库** |
| 字体导入 | Android `Typeface.Builder(path)` + Compose `Font(Typeface)` | **无需新依赖** |
| 翻页动画 | Compose 原生：`graphicsLayer` `rotationY` 做 3D 翻转近似、pager 默认滑动、snap 无动画 | **不引入三方库**（落实 E03 拍板）|
| 压缩包 | zip 用 JDK `java.util.zip`（内置）；rar 用 `com.github.anjoze:junrar`（best-effort，见风险）| D03 |
| WiFi 传书 | `fi.iki.elonen:nanohttpd`（内嵌 HTTP Server，纯 Java，Android 兼容）| E02 |
| 封面/纹理 | 本地文件用 `ImageBitmap`/`painterResource`；可选引 `io.coil-kt:coil-compose` 简化 Uri 加载 | F02/F05，标注可选 |
| 测试 | JVM 单元测试 `junit:junit:4.13.2`（纯逻辑：TocRules / EncodingDetector / ParagraphSplitter）| 无需 Robolectric/设备；CI 加 `testDebugUnitTest` |

### 1.3 架构模式
- **MVVM 维持**：将"分页"从 Composable `remember` 上提到 `ReaderPagination` 用例（在 ViewModel 中持有），便于跨章进度、缓存、渐进排版与重排。
- **单一数据源**：`ReaderStyleConfig` → `toTextStyle(density)` 产出**唯一** `TextStyle` 供测量与渲染共用，杜绝断点错位。
- **阅读参数持久化**：全部经 `AppPrefs`（DataStore），改动实时 `Flow` 下发到 ViewModel 触发重排。
- **阅读工具 UI 全底部**：底部 `BottomAppBar`/`ModalBottomSheet` 从底部展开；TOC 用 `ModalNavigationDrawer` 左侧抽屉。
- **数据层**：Room + Repository；新增 `BookmarkRepository`/`StatsRepository`。

---

## 2. 文件列表（按模块，新增 / 修改）

> 路径均相对 `app/src/main/java/com/example/reader/`（res 单独标注）。`[新]`=新增，`[改]`=修改。

### 2.1 engine（排版引擎增强）
| 文件 | 动作 | 说明 |
|------|------|------|
| `engine/LayoutEngine.kt` | [改] | 改为段落测量 + 段距像素注入；接收 `ReaderStyleConfig`；分块测量；章首页标题高度预留 |
| `engine/LayoutResult.kt` | [改] | `PageInfo` 增加 `chapterIndex/paragraphIndex`；新增 `LayoutPage`/`BookPagination` |
| `engine/ReaderStyleConfig.kt` | [新] | 排版参数数据类 + `toTextStyle()` + `layoutHash()` |
| `engine/ReaderPagination.kt` | [新] | 全书级分页、跨章页映射、`globalPercent` 计算、渐进排版、缓存命中 |
| `engine/LayoutCache.kt` | [新] | 排版缓存读写（调用 `LayoutCacheRepository`）|
| `util/ParagraphSplitter.kt` | [新] | 按"空白行"切段落，连续空行去重 |

### 2.2 db（数据层）
| 文件 | 动作 | 说明 |
|------|------|------|
| `db/AppDatabase.kt` | [改] | version 2→3，注册新实体/DAO |
| `db/BookEntity.kt` | [改] | 增加 `author/coverUri/isRead` 列 |
| `db/BookDao.kt` | [改] | 增加排序/筛选查询、按格式/已读筛选 |
| `db/BookmarkEntity.kt` | [新] | 书签实体 |
| `db/BookmarkDao.kt` | [新] | 书签增删查 |
| `db/ReadingSessionEntity.kt` | [新] | 阅读时长会话（F07）|
| `db/StatsDao.kt` | [新] | 统计聚合查询 |
| `db/TocRulePrefEntity.kt` | [新] | 每本书 TOC 规则开关（E05）|
| `db/TocRulePrefDao.kt` | [新] | 规则开关读写 |
| `db/HighlightEntity.kt` | [新] | 高亮/划线（F06）|
| `db/HighlightDao.kt` | [新] | 高亮读写 |
| `db/LayoutCacheEntity.kt` | [新] | 排版缓存表（C07）|
| `db/LayoutCacheDao.kt` | [新] | 缓存读写 |
| `db/Migrations.kt` | [新] | Migration(2→3)，保留历史、`fallbackToDestructiveMigration` 兜底 |

### 2.3 data（Repository）
| 文件 | 动作 | 说明 |
|------|------|------|
| `data/BookRepository.kt` | [改] | 增加排序/筛选/编码批量接口 |
| `data/BookmarkRepository.kt` | [新] | 书签仓储 |
| `data/StatsRepository.kt` | [新] | 阅读统计仓储 |

### 2.4 prefs（偏好）
| 文件 | 动作 | 说明 |
|------|------|------|
| `prefs/AppPrefs.kt` | [改] | 新增 key：`font_family, alignment, paragraph_spacing, letter_spacing, first_line_indent, reading_mode, page_animation, rtl, click_zones, texture_key, imported_fonts`；复用既有 `font_scale/line_spacing/page_margin/theme_mode/brightness` |

### 2.5 parser（解析）
| 文件 | 动作 | 说明 |
|------|------|------|
| `parser/TxtParser.kt` | [改] | `parse(path, enc, enabledRules)` 接收启用规则集合 |
| `parser/TocRules.kt` | [新] | 章节规则集（整行锚定、优先级、负向过滤），可被 Python 验证脚本对齐 |
| `parser/EncodingDetector.kt` | [改] | C05：补充 BIG5 / GB18030 候选与解码合法性校验 |
| `parser/LruEncodingCache.kt` | [改] | LRU 优化（按文件大小预算淘汰）|

### 2.6 ui/theme（主题）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/theme/Color.kt` | [改] | 增加 DARK / OLED_BLACK / PARCHMENT 调色板 |
| `ui/theme/Theme.kt` | [改] | 按 `ThemeMode` 选 scheme；状态栏颜色随主题 |
| `ui/theme/ThemeDefs.kt` | [新] | 枚举：`ThemeMode/FontFamilyKey/PageAnimationMode/ReadingMode` + `ClickZoneConfig` |
| `ui/theme/ReaderThemeColors.kt` | [新] | 每主题阅读区背景/正文色 |

### 2.7 ui/reader（阅读页）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/reader/ReaderScreen.kt` | [改] | 接入底部工具条、TOC 抽屉、书签/搜索/设置 sheet；音量键；RTL |
| `ui/reader/ReaderContent.kt` | [改] | 应用 styleConfig 渲染、主题色、段距、对齐、RTL、点击区、动画 |
| `ui/reader/ReaderPage.kt` | [改] | 用统一 `TextStyle` 渲染 + 主题色；高亮/选择（F06）；无障碍语义 |
| `ui/reader/ReaderUiState.kt` | [改] | `Ready` 增加 `styleConfig/perChapterPageCounts/totalPages/currentGlobalPage/globalPercent` |
| `ui/reader/ReaderViewModel.kt` | [改] | 持有 `ReaderPagination`；改参数重排保持进度；搜索/书签/统计心跳 |
| `ui/reader/ReaderStatusBar.kt` | [新] | 底部状态栏（章节名/全书页码/百分比）|
| `ui/reader/ReaderToolbar.kt` | [新] | 底部工具条（目录/主题/字体/书签/搜索/设置入口）|
| `ui/reader/SettingsSheet.kt` | [新] | 底部弹窗：字号/行距/段距/字距/边距/对齐/主题/动画/字体/点击区/纹理 |
| `ui/reader/TocDrawer.kt` | [新] | 左侧抽屉目录 + 规则开关（E05）|
| `ui/reader/BookmarkSheet.kt` | [新] | 书签列表/增删 |
| `ui/reader/SearchSheet.kt` | [新] | 全文搜索结果 + 跳转 |
| `ui/reader/HighlightSheet.kt` | [新] | 高亮列表/颜色 |

### 2.8 ui/shelf（书架）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/shelf/ShelfScreen.kt` | [改] | 排序/筛选条、封面 3:4 + 书名常显、导入入口、WiFi 入口、统计入口 |
| `ui/shelf/ShelfViewModel.kt` | [改] | 排序/筛选状态；导入/统计 |
| `ui/shelf/SortFilterBar.kt` | [新] | 排序与筛选控件 |
| `ui/shelf/BookCover.kt` | [新] | 3:4 封面（无封面时按书名生成占位）|
| `ui/shelf/ImportDialog.kt` | [新] | 多文件/文件夹/压缩包选择 |
| `ui/shelf/BookMetaEditor.kt` | [新] | 编辑书名/作者/封面（F05）|
| `ui/shelf/StatsScreen.kt` | [新] | 阅读统计页（F07）|

### 2.9 feature（功能模块）
| 文件 | 动作 | 说明 |
|------|------|------|
| `feature/fonts/FontManager.kt` | [新] | 内置字族映射 + 导入 TTF/OTF 解析（D04）|
| `feature/import/ImportManager.kt` | [新] | 批量导入、SAF 多选/文件夹（D03）|
| `feature/import/ArchiveExtractor.kt` | [新] | zip/rar 解压 |
| `feature/search/SearchEngine.kt` | [新] | 章节级线性搜索 + 上下文（D02）|
| `feature/stats/ReadingStatsTracker.kt` | [新] | 前台时长采样 + 心跳（F07）|
| `feature/clickzone/ClickZoneHandler.kt` | [新] | 四区域点击/滑动映射（E01）|
| `feature/animation/PageAnimation.kt` | [新] | 平滑/无/3D 翻转近似（E03）|
| `feature/wifi/WifiServer.kt` | [新] | nanohttpd 内嵌服务（E02）|
| `feature/wifi/WifiTransferViewModel.kt` | [新] | WiFi 传书 VM |
| `feature/wifi/WifiTransferScreen.kt` | [新] | WiFi 传书页 |
| `feature/export/ExportManager.kt` | [新] | 导出/分享 TXT/图片（F03）|
| `feature/highlight/HighlightManager.kt` | [新] | 高亮管理（F06）|

### 2.10 res 资源
| 文件 | 动作 | 说明 |
|------|------|------|
| `res/raw/wifi_upload.html` | [新] | WiFi 上传页 |
| `res/drawable/texture_wood.xml` `texture_linen.xml` `texture_paper.xml` | [新] | 背景纹理（F02）|
| `res/drawable/ic_book_placeholder.xml` | [新] | 无封面占位 |
| `res/values/strings.xml` | [改] | 新增大量文案 |
| `res/values/themes.xml` | [改] | 适配边缘到边缘/状态栏 |

### 2.11 测试 / CI / 文档
| 文件 | 动作 | 说明 |
|------|------|------|
| `app/src/test/java/.../parser/TocRulesTest.kt` | [新] | 规则命中/负向过滤（JVM）|
| `app/src/test/java/.../parser/EncodingDetectorTest.kt` | [新] | BIG5/GBK/UTF-8（JVM，样本在 `tools/samples`）|
| `app/src/test/java/.../engine/ParagraphSplitterTest.kt` | [新] | 段落切分（JVM）|
| `app/src/test/resources/samples/*.txt` | [新] | 测试样本 |
| `.github/workflows/ci.yml` | [改] | 增加 `./gradlew testDebugUnitTest` 步骤 |
| `app/build.gradle.kts` | [改] | 新增依赖（nanohttpd / junrar / coil / junit）|
| `tools/samples/{titles,body,lookbehind}/*.txt` | [改] | 补充真实样本供 CI |
| `docs/pdf_research.md` | [新] | F04 仅调研结论（无代码）|
| `docs/system_design.md` `docs/sequence-diagram.mermaid` `docs/class-diagram.mermaid` | [新] | 本设计产物 |

---

## 3. 数据结构和接口

类图见 `docs/class-diagram.mermaid`。关键数据结构如下。

### 3.1 排版参数 `ReaderStyleConfig`（engine/ReaderStyleConfig.kt）
```kotlin
data class ReaderStyleConfig(
    val fontScale: Float = 1.0f,            // 来自 AppPrefs.font_scale
    val lineSpacing: Float = 1.6f,          // AppPrefs.line_spacing（行高倍数）
    val paragraphSpacingPx: Int = 8,        // D06 精确像素段距
    val letterSpacing: Float = 0.5f,        // 字间距(sp)
    val pageMarginPx: Int = 16,             // AppPrefs.page_margin
    val alignment: TextAlign = TextAlign.Start,
    val firstLineIndentPx: Int = 0,         // 首行缩进(px)
    val fontFamily: FontFamilyKey = FontFamilyKey.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val brightness: Float = -1f,            // -1 跟随系统
    val pageAnimation: PageAnimationMode = PageAnimationMode.SMOOTH,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val rtl: Boolean = false,
    val clickZones: ClickZoneConfig = ClickZoneConfig(),
    val textureKey: String = "none"
) {
    fun toTextStyle(density: Density): TextStyle   // 唯一 TextStyle：测量与渲染共用
    fun layoutHash(): String                        // 仅含影响排版的字段
}
```
**流入 LayoutEngine 的方式**：`ReaderViewModel` 收集 `AppPrefs` 各 `Flow` 合成 `ReaderStyleConfig`；调用 `cfg.toTextStyle(density)` 得到**单一** `TextStyle` 实例，同时把 `paragraphSpacingPx/firstLineIndentPx/pageMarginPx` 作为排版参数传给 `ReaderPagination`→`LayoutEngine.paginate(...)`。`ReaderPage` 渲染时**复用同一 `TextStyle` 实例**，确保测量与渲染行断点完全一致（C02/D06 不破版的关键）。

### 3.2 新增 Room 实体（字段 + 索引）
**BookmarkEntity**（`bookmarks`）
- `bookmarkId: Long @PrimaryKey(autoGenerate)`
- `bookId: String`，索引 `(bookId)`
- `chapterIndex: Int`，索引 `(bookId, chapterIndex)`
- `pageIndex: Int`（章节内页序号，便于排序/显示）
- `charOffset: Int`
- `previewText: String`
- `createdAt: Long`

**ReadingSessionEntity**（`reading_sessions`，F07）
- `id: Long @PrimaryKey(autoGenerate)`
- `bookId: String`，索引 `(bookId)`
- `startedAt: Long`
- `endedAt: Long`
- `durationSec: Int`
- `dateKey: String`（yyyy-MM-dd），索引 `(dateKey)`

**TocRulePrefEntity**（`toc_rule_prefs`，E05）
- `@PrimaryKey bookId: String` + `ruleId: String`（复合主键）
- `enabled: Boolean`

**HighlightEntity**（`highlights`，F06）
- `id: Long @PrimaryKey(autoGenerate)`
- `bookId: String`，索引 `(bookId)`
- `chapterIndex: Int` / `startChar: Int` / `endChar: Int`
- `colorArgb: Int`
- `createdAt: Long`

**LayoutCacheEntity**（`layout_cache`，C07）
- `@PrimaryKey cacheKey: String`
- `bookId: String`
- `pagesJson: String`（仅存分页结果 = 每章 `(start,end)` 字符区间，KB 级）
- `createdAt: Long`

**BookEntity 扩展列**：`author: String?`、`coverUri: String?`、`isRead: Boolean`（默认 false）。

### 3.3 `ReaderUiState.Ready` 扩展（解决 C01 全书级百分比）
```kotlin
data class Ready(
    val chapters: List<Chapter>,
    val currentChapterIndex: Int,
    val currentCharOffset: Int,
    val totalChars: Long,
    val encoding: String,
    // —— 新增 ——
    val styleConfig: ReaderStyleConfig,
    val perChapterPageCounts: List<Int>,   // 每章页数
    val totalPages: Int,                    // 全书总页数
    val currentGlobalPage: Int,             // 跨章当前页（0-based）
    val globalPercent: Float                // 全书百分比 0..1
)
```

### 3.4 迁移策略
`AppDatabase` version 2→3，在 `db/Migrations.kt` 写 `Migration(2,3)`：ALTER `books` 增加 `author/coverUri/isRead` 列；CREATE 上述新表。`Room.databaseBuilder` 保留 `fallbackToDestructiveMigration()` 作破坏性兜底；坏缓存自愈（缓存读取 try/catch，异常即删除重排）。

---

## 4. 程序调用流程

时序图见 `docs/sequence-diagram.mermaid`，覆盖 6 条主链路：
① 打开书 → 恢复进度 → 排版（含缓存命中）
② 改排版参数 → 实时重排（进度保持不跳开头）
③ 书签增删
④ 全文搜索 → 跳转
⑤ 批量导入 → 解析 → 入库
⑥ WiFi 传书上传 → 入库

关键不变量：**改参数重排时 `currentCharOffset` 保持不变**，由 `ReaderPagination.globalPage(chapter, offset)` 重新定位到同一字符偏移所在页，绝不回到第 0 页（C01/C07）。

---

## 5. 任务列表（核心交付，有序 + 依赖）

> 排序原则：**先打通编译主干（数据层 + 排版参数 + 引擎 + 主题），再补枝节**；把"改参数重排不跳开头""缓存""跨章进度"等贯穿性能力前置。
> 验证方式：`assembleDebug` 编译通过（CI）+ 标注的单元/UI 验证。

| ID | 所属 Phase | 任务 | 改动文件（关键） | 依赖 | 验证 |
|----|-----------|------|------------------|------|------|
| **T01** | 数据层 | 数据库迁移 + 新增实体/仓储 | `db/AppDatabase.kt` `BookEntity.kt` `BookDao.kt` `BookmarkEntity/Dao` `ReadingSessionEntity` `StatsDao` `TocRulePrefEntity/Dao` `HighlightEntity/Dao` `LayoutCacheEntity/Dao` `Migrations.kt` `data/BookRepository.kt` `BookmarkRepository.kt` `StatsRepository.kt` | — | `assembleDebug`；Migration(2→3) 在真机/模拟器升级不丢数据 |
| **T02** | 引擎基座 | 排版参数数据类 + 风格工厂 + 偏好 key | `engine/ReaderStyleConfig.kt` `ui/theme/ThemeDefs.kt` `prefs/AppPrefs.kt` | — | 编译；`toTextStyle`/`layoutHash` 单测 |
| **T03** | 引擎(D06/D08) | 排版引擎增强：段落测量 + 精确段距 + 分块测量 | `engine/LayoutEngine.kt` `LayoutResult.kt` `util/ParagraphSplitter.kt` | T02 | `ParagraphSplitterTest`；3× 字号不破版；超大章节分块不 OOM |
| **T04** | 引擎/缓存 | 分页服务 + 跨章进度 + 布局缓存 | `engine/ReaderPagination.kt` `LayoutCache.kt` `db/LayoutCacheEntity/Dao` | T01,T03 | 缓存命中跳过测量；跨章 page/percent 正确 |
| **T05** | 主题(C03) | 多主题系统 + 状态栏 + 阅读区配色 | `ui/theme/Color.kt` `Theme.kt` `ReaderThemeColors.kt` | T02 | 四主题切换；状态栏随主题 |
| **T06** | 阅读 VM | ReaderUiState/ViewModel 重构（承载分页与进度）| `ui/reader/ReaderUiState.kt` `ReaderViewModel.kt` | T04,T01 | 打开恢复进度；改参数重排不跳开头 |
| **T07** | 阅读渲染(C02/D05/D06) | 正文渲染：字号缩放/字体/段距/对齐/RTL/标题预留 | `ui/reader/ReaderContent.kt` `ReaderPage.kt` `ReaderScreen.kt` | T05,T06,T02 | 150/200/300% 不破版；RTL 正确；章首页标题不重叠 |
| **T08** | 阅读(C01) | 底部状态栏（全书页码/百分比）| `ui/reader/ReaderStatusBar.kt` `ReaderScreen.kt` | T06 | 显示"第 x/总 页 · n%" |
| **T09** | 阅读工具 | 底部工具条 + 设置面板（弹窗底部展开）| `ui/reader/ReaderToolbar.kt` `SettingsSheet.kt` `ReaderScreen.kt` | T05,T02,T07 | 调参实时重排且进度保持 |
| **T10** | 阅读(TOC/E05) | 目录左侧抽屉 + 每章规则开关 | `ui/reader/TocDrawer.kt` `parser/TxtParser.kt` `parser/TocRules.kt` | T06,T01 | 左抽屉打开；跳转；规则开关持久化 |
| **T11** | 内容(D01) | 书签系统 | `feature/...` 经 `BookmarkSheet.kt` + `BookmarkRepository` | T01,T06 | 增/删/列；预览文本 |
| **T12** | 内容(D02) | 全文搜索 + 跳转 | `feature/search/SearchEngine.kt` `SearchSheet.kt` `ReaderViewModel.kt` | T06 | 搜索→上下文→跳转 |
| **T13** | 书架(C04/F05) | 书架增强：排序/筛选/封面 3:4/书名常显/最近 | `ui/shelf/ShelfScreen.kt` `ShelfViewModel.kt` `SortFilterBar.kt` `BookCover.kt` `BookRepository.kt` | T01 | 排序/筛选生效；封面比例 3:4 书名常显 |
| **T14** | 导入(D03/C05) | 批量导入（多文件/文件夹/压缩包/批量编码）| `feature/import/ImportManager.kt` `ArchiveExtractor.kt` `ui/shelf/ImportDialog.kt` `EncodingDetector.kt` `LruEncodingCache.kt` | T13,T01 | 多选/zip 导入；编码识别 |
| **T15** | 字体(D04) | 字体管理：内置字族 + 导入 TTF/OTF | `feature/fonts/FontManager.kt` `SettingsSheet.kt` `AppPrefs.kt` | T02,T09 | 切换内置字族；导入 TTF 生效 |
| **T16** | 交互(E01) | 点击区域自定义 | `feature/clickzone/ClickZoneHandler.kt` `ReaderContent.kt` `AppPrefs.kt` `SettingsSheet.kt` | T07,T02 | 四区域映射到动作 |
| **T17** | 交互(E03) | 翻页动画（原生 + 3D 近似）| `feature/animation/PageAnimation.kt` `ReaderContent.kt` `SettingsSheet.kt` | T07,T02 | 平滑/无/3D 翻转 |
| **T18** | 无障碍(E04) | TalkBack 支持 | `ReaderPage.kt` `ReaderStatusBar.kt` `ReaderToolbar.kt` | T08 | TalkBack 焦点与描述 |
| **T19** | 便捷(E02) | WiFi 传书 | `feature/wifi/*` `res/raw/wifi_upload.html` `ShelfScreen.kt` `build.gradle.kts` | T14 | 浏览器上传→书籍入库 |
| **T20** | P2(F01) | 音量键翻页 | `ReaderScreen.kt` `ReaderViewModel.kt` | T06 | 音量键翻页 |
| **T21** | P2(F02) | 背景纹理 | `res/drawable/texture_*` `SettingsSheet.kt` `ReaderContent.kt` `AppPrefs.kt` | T09 | 选纹理生效 |
| **T22** | P2(F03) | 导出/分享 | `feature/export/ExportManager.kt` `BookmarkSheet.kt` `SearchSheet.kt` | T11,T12 | 导出书签为 TXT；分享 |
| **T23** | P2(F05) | 元信息编辑（书名/作者/封面）| `ui/shelf/BookMetaEditor.kt` `ShelfViewModel.kt` | T13 | 编辑持久化 |
| **T24** | P2(F06) | 高亮/划线 | `feature/highlight/HighlightManager.kt` `HighlightEntity/Dao` `ReaderPage.kt` `HighlightSheet.kt` `AppDatabase.kt` | T01,T07 | 选区高亮；列表；换色 |
| **T25** | P2(F07) | 阅读统计 | `feature/stats/ReadingStatsTracker.kt` `StatsRepository.kt` `ReaderViewModel.kt` `StatsScreen.kt` | T01 | 读 1 分钟→会话记录；聚合 |
| **T26** | 调研(F04) | PDF 可行性调研（仅文档）| `docs/pdf_research.md` | — | 交付结论文档，无代码 |
| **T27** | QA(C05/D07) | CI 单元测试 + 样本 | `app/src/test/...` `tools/samples/*` `.github/workflows/ci.yml` `build.gradle.kts` | T03,T10,T14 | CI `testDebugUnitTest` 绿 |

---

## 6. 依赖包列表（本期新增，均兼容 Kotlin 1.9.22 / Compose BOM 2023.10.01）

| 依赖 | 版本 | 作用 | 风险/备注 |
|------|------|------|-----------|
| `fi.iki.elonen:nanohttpd` | `2.3.1` | E02 WiFi 传书内嵌 HTTP Server | 纯 Java，Android 兼容，低风 |
| `com.github.anjoze:junrar` | `7.5.5`（≈，CI 验证）| D03 rar 解压（best-effort）| **风险**：minSdk26 下需 CI 验证；失败则降级为仅 zip + 提示用户 |
| `io.coil-kt:coil-compose` | `2.5.0` | F02/F05 封面与纹理 Uri 加载 | 可选；不用则改 `ImageBitmap` 加载，零新增依赖 |
| `junit:junit` | `4.13.2` | CI JVM 单测 | `testImplementation` |

既有依赖保持不变（Compose BOM 2023.10.01 / material3 / room 2.6.1 / navigation 2.7.5 / datastore 1.0.0 / juniversalchardet 2.4.0 / lifecycle / ksp）。

---

## 7. 共享知识（跨文件约定）

1. **排版参数默认值来源**：全部来自 `AppPrefs` 既有 key（`font_scale` 默认 1.0、`line_spacing` 1.6、`page_margin` 16）；新增项给保守默认（段距 8px、字距 0.5sp、对齐 Start、动画 SMOOTH、模式 PAGED）。
2. **唯一 TextStyle**：测量与渲染必须使用 `ReaderStyleConfig.toTextStyle(density)` 返回的同一实例；任何新增排版维度（段距/对齐/首行缩进）必须同时作用于该 `TextStyle` 与 `LayoutEngine` 参数，否则视为 bug。
3. **段距精确像素（D06）**：`ParagraphSplitter` 按"1+ 空白行"切段落并去重连续空行；`LayoutEngine` 逐段落测量后按 `paragraphSpacingPx` 注入，绝不用行高倍数近似。
4. **进度保持**：改参数/换主题触发重排时，ViewModel 始终保留 `currentChapterIndex + currentCharOffset`，由 `ReaderPagination` 重新定位到同偏移页，禁止回到第 0 页。
5. **主题切换**：`ReaderTheme` 按 `ThemeMode` 选 scheme；状态栏颜色 = 当前 scheme.background；阅读区背景/正文用 `ReaderThemeColors`（随主题变）。
6. **RTL**：BOM 1.5.4 的 `HorizontalPager` 不保证有 `reverseLayout`；统一用"页面索引反转 + `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`"实现，避免依赖 BOM 外 API。
7. **缓存 key 规则**：`LayoutCacheEntity.cacheKey = sha1(fingerprint + styleConfig.layoutHash() + "${maxWidthPx}x${maxHeightPx}")`；`layoutHash()` 仅含影响排版的字段（fontScale/lineSpacing/paragraphSpacing/letterSpacing/pageMargin/alignment/fontFamily），**不含 themeMode**（主题只改颜色不改排版）。坏缓存 try/catch 自愈。
8. **数据库迁移**：version 2→3 写 `Migration` 保留历史；保留 `fallbackToDestructiveMigration()` 兜底。
9. **阅读工具 UI 一律底部**：工具条/设置/书签/搜索均为 `ModalBottomSheet` 从底部展开；TOC 为左侧 `ModalNavigationDrawer`。
10. **字体导入（D04）**：内置 = `DEFAULT/SANS[黑体]/SERIF[宋体·楷体近似]/MONOSPACE[等宽]` 系统字族映射；用户导入 TTF/OTF 存于 `files/fonts/`，经 `Typeface.Builder(path)` → `Font(Typeface)` 解析；**不打包商用字体**。
11. **阅读统计（F07）**：无前台服务；`ReadingStatsTracker` 在前台采样 + 心跳，离开页面写 `ReadingSessionEntity`。
12. **章节切分（D07/E05）**：规则集集中在 `parser/TocRules.kt`，全 `^...$` 整行锚定 + 负向过滤；`TxtParser.parse` 接收启用规则集合；每本书开关存 `TocRulePrefEntity`。

---

## 8. 待明确事项（≤3 条，含推荐）

1. **rar 支持可行性（D03）**：`com.github.anjoze:junrar` 在 minSdk 26 + Kotlin 1.9 下能否编译/运行未经真机验证，且 rar5 不支持。
   → **推荐**：先实现 zip 全量 + rar 用 junrar 做 best-effort；CI 验证，若失败降级为"仅 zip，rar 提示用户用 zip"，不影响主干。
2. **仿真翻页 3D 近似的验收标准（E03）**：拍板"不引入三方库、用 3D 翻转近似"。但"近似"视觉接受度无量化标准。
   → **推荐**：用 `graphicsLayer { rotationY }` 在 pager 过渡实现翻页翻转；以"无明显撕裂/闪烁、过渡 < 400ms"为内部验收；上线前需你/用户确认是否够用。
3. **排版缓存 key 是否纳入主题（C07）**：主题切换只改颜色、不改排版，但"羊皮纸"等背景可能影响可用高度（若纹理带内边距）。
   → **推荐**：`layoutHash()` 仅含排版字段（不含 themeMode）；若某主题需额外内边距，则把该内边距作为 `pageMarginPx` 变体纳入 hash。需你确认此边界。

---

> 结论：本设计在既有 Phase 0/1 代码之上增量扩展，27 个有序任务覆盖 C01–C05 / D01–D08 / E01–E05 / F01–F07（F04 仅调研），并落实团队负责人全部拍板（PDF 仅调研、翻页动画不引三方库、字体内置字族映射、统计无前台服务、C05/D07 用 CI 单测）。优先打通数据层 + 排版引擎 + 主题编译主干，再按依赖补齐枝节。
