# 阅读器 App v1.1 增量架构设计 + 任务分解

> 包名 `com.example.reader`，源码根 `app/src/main/java/com/example/reader/`
> 技术栈：Kotlin 1.9.22 · Compose BOM 2023.10.01（Compose 1.5.4）· Material3 1.1.2 · Room 2.6.1 · Navigation Compose 2.7.5 · DataStore 1.0.0
> compileSdk/targetSdk 34，minSdk 26。**当前 72 源文件，CI 全绿**。
> 本设计在既有 Phase 0–5 已交付代码之上**增量扩展，不推翻**。

---

## Part A: 系统设计

### 1. 实现方案

**一句话**：重构 MainActivity 为 BottomNavigation 三板块架构，阅读器面板改为可收起+功能重组，新增 EpubParser（轻量），个人/设置页从零新建，TOC 规则从阅读页迁入设置。

#### 1.1 核心技术难点

| 难点 | 分析 |
|------|------|
| **底部导航嵌套路由** | 书架/书城/个人三 Tab 各自有独立回退栈，阅读页在底部导航之外全屏展示。需用嵌套 NavHost 或路由状态管理 |
| **阅读面板动画** | 默认隐藏 TopAppBar + BottomBar，点击屏幕中间区域弹出，需 `AnimatedVisibility` + 点击手势冲突处理（与现有 ClickZone 翻页不冲突） |
| **EPUB 轻量解析** | 约束：不加 epublib 等重依赖。只用 `java.util.zip.ZipFile` + `XmlPullParser`。需处理 container.xml → OPF → metadata/manifest/spine，HTML 文本提取 |
| **封面处理** | EPUB 封面从 manifest 提取 → 存到 `filesDir/cover/{bookId}.png`；无封面→默认占位 drawable |
| **TOC 规则迁移** | 从 per-book Room 表 → 全局 DataStore 键（或 Room 全局行），阅读页不再显示规则开关 |
| **最近阅读去重** | 同一 bookId 连续出现合并为一条（取最新 openedAt），SQL 层或 Repository 层处理 |

#### 1.2 框架选型（新增依赖）

| 场景 | 选型 | 说明 |
|------|------|------|
| EPUB 解析 | `java.util.zip.ZipFile` + `org.xmlpull.v1.XmlPullParser`（Android SDK 内置） | **零新增依赖** |
| HTML→纯文本 | 简单正则 `<[^>]*>` + `Html.fromHtml()` 备选 | 零新增依赖 |
| 面板动画 | Compose `AnimatedVisibility`（material3 已依赖） | 零新增依赖 |
| 底部导航 | Material3 `NavigationBar` + `NavigationBarItem`（material3 已依赖） | 零新增依赖 |
| 封面加载 | 现有 `ImageBitmap`/`painterResource` 方案 | 零新增依赖 |

**本期无新增 gradle 依赖。** 所有能力均基于现有依赖实现。

#### 1.3 架构模式

- **MVVM 维持**：不改变现有分层。新增 ProfileScreen/SettingsScreen 均使用独立 ViewModel 或复用 ShelfViewModel。
- **导航重构**：`MainActivity` 的 `Scaffold` 顶部放 NavHost（书架/书城/个人），底部放 `NavigationBar`；阅读页路由在 NavHost 外单独处理（全屏覆盖）。
- **阅读面板**：`ReaderScreen` 内部状态 `isPanelVisible = false`，点击 ReaderContent 中间区域→toggle；`AnimatedVisibility` 包裹 TopAppBar + BottomBar。
- **TOC 规则全局化**：从 Room `toc_rule_prefs`（per-bookId）→ DataStore `KEY_GLOBAL_TOC_RULES`（JSON 字符串），TocRuleSettingsScreen 编辑。

---

### 2. 文件变更清单

> 路径均相对 `app/src/main/java/com/example/reader/`，res 单独标注。`[新]`=新建，`[改]`=修改。

#### 2.1 导航（Navigation）
| 文件 | 动作 | 说明 |
|------|------|------|
| `MainActivity.kt` | [改] | Scaffold + NavigationBar（书架/书城/个人）+ NavHost（reader 路由全屏覆盖） |
| `app/build.gradle.kts` | [改] | 验证依赖（本期无新增，但需确认 material-icons-extended 存在） |

#### 2.2 书架（Shelf）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/shelf/ShelfScreen.kt` | [改] | 移除 TopAppBar 的 Wifi/Stats 图标按钮（导航移至底部和个人页）；保留导入 FAB |
| `ui/shelf/ShelfViewModel.kt` | [改] | `recentHistory` 保留但个人页自己查询去重版本；书架页不再显示最近阅读 section |
| `ui/shelf/BookCover.kt` | [改] | 无 `coverUri` 时显示默认占位 drawable（`R.drawable.ic_default_cover`） |

#### 2.3 书城（BookStore — P2 占位）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/bookstore/BookStoreScreen.kt` | [新] | 纯占位："即将推出" |

#### 2.4 个人（Profile — P1）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/profile/ProfileScreen.kt` | [新] | 最近阅读 LazyRow（去重）+ 阅读统计入口 + 设置入口 |

#### 2.5 设置（Settings — P1）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/settings/SettingsScreen.kt` | [新] | 主题模式选择 / 默认字体选择 / 目录规则入口 / 关于 |
| `ui/settings/TocRuleSettingsScreen.kt` | [新] | 全局 TOC 规则开关页面（从 TocDrawer 迁移） |

#### 2.6 阅读器面板（Reader — P0）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/reader/ReaderScreen.kt` | [改] | 引入 `isPanelVisible` 状态 + `AnimatedVisibility`；去掉旧 Scaffold topBar/bottomBar，改用 Column 布局 |
| `ui/reader/ReaderTopBar.kt` | [新] | 返回键 + 书名 + 右侧溢出菜单（搜索🔍、书签🔖） |
| `ui/reader/ReaderBottomBar.kt` | [新] | 太阳图标☀（亮度/背景色/日夜模式弹出菜单）+ 章节导航（◀ T ▸）+ 排版键（字号/边距/对齐/字体弹出） |
| `ui/reader/ReaderContent.kt` | [改] | 点击中间区域 toggle `onPanelToggle`；与 ClickZoneHandler 协调（面板可见时不触发翻页） |
| `ui/reader/ReaderToolbar.kt` | [改] | 废弃旧 5 按钮布局；功能迁移到 ReaderTopBar + ReaderBottomBar |
| `ui/reader/SettingsSheet.kt` | [改] | 可能需要微调（部分设置项在底部排版键和太阳菜单中已有快捷入口） |

#### 2.7 阅读器 TOC（Reader TOC — P1）
| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/reader/TocDrawer.kt` | [改] | 移除底部"章节识别规则"Switch 区域（迁移到设置→目录） |

#### 2.8 EPUB 解析（Parser — P0）
| 文件 | 动作 | 说明 |
|------|------|------|
| `parser/EpubParser.kt` | [新] | ZIP + XmlPullParser：解析 container.xml→OPF→metadata/manifest/spine，提取章节和封面 |
| `feature/import/ImportManager.kt` | [改] | 按扩展名路由：`.txt`→TxtParser，`.epub`→EpubParser；封面提取 + 保存到 filesDir；异步导入 |
| `feature/import/ArchiveExtractor.kt` | [改] | 加入 `.epub` 到识别扩展名列表（epub 不解压，直接传路径给 EpubParser） |
| `ui/shelf/ImportDialog.kt` | [改] | 文件选择器加入 `application/epub+zip` MIME；导入进度 Snackbar/Indicator |

#### 2.9 数据层（DB / Prefs — P1）
| 文件 | 动作 | 说明 |
|------|------|------|
| `db/ReadingHistoryDao.kt` | [改] | 新增 `getDedupedRecentFlow()` — 连续同书去重查询 |
| `data/BookRepository.kt` | [改] | 新增 `getDedupedRecentHistoryFlow()` + `saveCoverImage()` |
| `prefs/AppPrefs.kt` | [改] | 新增 `KEY_GLOBAL_TOC_RULES`（String，JSON 数组）+ `KEY_DEFAULT_FONT`（String） |

#### 2.10 资源（Res）
| 文件 | 动作 | 说明 |
|------|------|------|
| `res/drawable/ic_default_cover.xml` | [新] | 默认封面占位矢量图（书籍+问号图标） |
| `res/values/strings.xml` | [改] | 新增底部导航标签、个人页、设置页文案 |

---

### 3. 数据结构和接口

#### 3.1 BookEntity（无变更）
现有字段 `title` 导入时已赋值为 `file.nameWithoutExtension`（P0-5 已满足）。现有 `coverUri` 用于存储封面路径。现有 `format` 字段支持 `"epub"`。

#### 3.2 新增 DataStore Key（AppPrefs）

```kotlin
// 全局 TOC 规则（JSON 数组，如 "[\"rule_chinese_num\",\"rule_arabic_num\"]"）
private val KEY_GLOBAL_TOC_RULES = stringPreferencesKey("global_toc_rules")

// 默认字体（用于新书默认排版）
private val KEY_DEFAULT_FONT = stringPreferencesKey("default_font")

val globalTocRules: Flow<Set<String>>  // 从 JSON 解析
suspend fun setGlobalTocRules(rules: Set<String>)

val defaultFont: Flow<String>
suspend fun setDefaultFont(key: String)
```

#### 3.3 EpubParser 数据模型

```kotlin
// parser/EpubParser.kt
data class EpubMetadata(
    val title: String,
    val author: String?,
    val coverImagePath: String?,  // ZIP 内路径
    val coverImageBytes: ByteArray?
)

data class EpubChapter(
    val title: String,
    val content: String,          // 纯文本（已去 HTML 标签）
    val charCount: Int
)

class EpubParser {
    fun parse(filePath: String, encoding: Charset = StandardCharsets.UTF_8): EpubParseResult
}

data class EpubParseResult(
    val metadata: EpubMetadata,
    val chapters: List<EpubChapter>
)
```

#### 3.4 ReadingHistoryDao 去重查询

```kotlin
// 连续同书去重：按 openedAt DESC 排序，相邻同 bookId 只保留第一条
@Query("""
    SELECT * FROM (
        SELECT *, 
            LAG(bookId) OVER (ORDER BY openedAt DESC) AS prevBookId
        FROM reading_history
    ) WHERE bookId IS NOT prevBookId OR prevBookId IS NULL
    ORDER BY openedAt DESC LIMIT 10
""")
fun getDedupedRecentFlow(): Flow<List<ReadingHistoryEntity>>
```

> **注意**：Room 2.6.1 支持 `LAG()` 窗口函数（SQLite 3.35+，minSdk 26 满足）。若 Room 编译报错，降级为 Kotlin 层去重。

#### 3.5 类图

详见 `docs/class-diagram.mermaid`（增量部分）。

---

### 4. 程序调用流程

#### 4.1 EPUB 导入流程

```
User → ImportDialog(选择.epub) → copyUriToCache → ImportManager.importFiles(paths)
  → 检测扩展名 .epub
  → EpubParser.parse(path)
    → ZipFile.open(path)
    → 读取 META-INF/container.xml → 获取 OPF 路径
    → 解析 OPF XML → metadata(title, creator) + manifest(items) + spine(itemrefs)
    → 查找 cover: manifest 中 id 含 "cover" 或 properties="cover-image"
    → 提取 cover image bytes
    → 遍历 spine → 读取各 HTML → 去标签 → 纯文本 → 按<h1>-<h6>切章
    → 返回 EpubParseResult(metadata, chapters)
  → 保存封面: BookRepository.saveCoverImage(bookId, bytes) → filesDir/cover/{bookId}.png
  → 计算 totalChars
  → upsertBook(BookEntity(title=metadata.title, format="epub", coverUri=...))
  → 通知进度回调
```

#### 4.2 阅读面板弹出/收起流程

```
默认: isPanelVisible = false → TopBar + BottomBar = Gone
用户点击屏幕中间:
  → ReaderContent.detectTapGestures → ClickZoneHandler.zoneFromOffset
  → 若 zone == CENTER:
    → 若 isPanelVisible == false → toggle → isPanelVisible = true
    → 若 isPanelVisible == true → toggle → isPanelVisible = false
      （面板可见时，CENTER 区域优先切换面板，不触发 OPEN_MENU）
面板可见时:
  → AnimatedVisibility(visible=true) → TopBar slideInVertically + BottomBar slideInVertically
  → 顶部溢出菜单: DropdownMenu(搜索/书签)
  → 太阳图标: DropdownMenu(亮度Slider/背景色ChipRow/日夜模式Switch)
  → 排版键: ModalBottomSheet(字号/边距/对齐/字体)
```

#### 4.3 时序图

详见 `docs/sequence-diagram.mermaid`（v1.1 关键流程）。

---

### 5. 待明确事项

| # | 事项 | 推荐方案 |
|---|------|----------|
| 1 | **窗口函数兼容性**：`LAG()` 在 Room 2.6.1 + SQLite 3.35+ 可用，但需 CI 验证。若编译报错，降级为 Kotlin 层去重 | 先用 SQL 窗口函数；编译不过则切 Kotlin |
| 2 | **EPUB HTML→纯文本**：用 `regex <[^>]*>` 简单去标签，可能残留 CSS/JS 内联 | 接受 MVP 质量；复杂 EPUB（含大量 CSS）可能出现残留，后续可迭代 |
| 3 | **面板自动收起**：PRD 写"3 秒无操作自动收起（可选）"→ 已确认"手动点击空白处收起" | 按确认实现：仅手动 toggle，不自动收起 |

---

## Part B: 任务分解

### 6. 依赖包列表

本期**无新增三方依赖**。所有能力基于现有依赖实现：

```
# 现有依赖（无变更）
- androidx.compose:compose-bom:2023.10.01
- androidx.compose.material3:material3 (1.1.2)
- androidx.compose.material:material-icons-extended
- androidx.navigation:navigation-compose:2.7.5
- androidx.room:room-runtime:2.6.1 / room-ktx:2.6.1 / room-compiler:2.6.1
- androidx.datastore:datastore-preferences:1.0.0
- androidx.lifecycle:lifecycle-runtime-compose:2.6.2
- androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2
- com.github.albfernandez:juniversalchardet:2.4.0
- org.nanohttpd:nanohttpd:2.3.1
- androidx.documentfile:documentfile:1.0.1
- com.github.junrar:junrar:7.5.5
```

---

### 7. 任务列表（按依赖排序）

#### T01: 项目基础设施 — 底部导航重构 + 新页面骨架 + Shelf 调整

| 属性 | 值 |
|------|-----|
| **Task ID** | T01 |
| **优先级** | P0 |
| **依赖** | 无 |

**改动文件（6 个）**：

| 文件 | 动作 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | [改] | 验证 `material-icons-extended` 在依赖中（底部导航图标需要） |
| `MainActivity.kt` | [改] | 重构：`Scaffold(bottomBar = NavigationBar{...})` → 内含 NavHost（shelf/bookstore/profile）；reader 路由全屏 overlay |
| `ui/bookstore/BookStoreScreen.kt` | [新] | 纯占位：居中 Text("书城即将推出") |
| `ui/profile/ProfileScreen.kt` | [新] | 骨架：Column(最近阅读区域 + 阅读统计> 入口 + 系统设置> 入口)，点击入口暂用 TODO |
| `ui/settings/SettingsScreen.kt` | [新] | 骨架：Column(主题模式/默认字体/目录规则/关于)，各项暂用 TODO |
| `ui/shelf/ShelfScreen.kt` | [改] | TopAppBar actions 移除 Wifi 图标和 Stats 图标；保留导入 FAB 和 + 按钮 |

**关键实现要点**：
- `MainActivity` 的 `ReaderNavigation` 改为：`Scaffold` + `NavigationBar`（三个 `NavigationBarItem`：书架/书城/个人），`content` 区放 NavHost
- 阅读页路由（`reader/{bookPath}`）在 NavHost 内但全屏覆盖底部导航（通过 `Scaffold` 的 content padding 控制，或在 reader 路由时不显示底部导航）
- 推荐方案：`NavHost` 内 `composable("reader/{bookPath}")` 时，通过状态控制隐藏底部 NavigationBar

---

#### T02: 数据层改动 — 去重查询 + 偏好键 + ViewModel 调整

| 属性 | 值 |
|------|-----|
| **Task ID** | T02 |
| **优先级** | P1 |
| **依赖** | 无（可与 T01 并行） |

**改动文件（5 个）**：

| 文件 | 动作 | 说明 |
|------|------|------|
| `db/ReadingHistoryDao.kt` | [改] | 新增 `getDedupedRecentFlow()`：`SELECT DISTINCT ON (bookId) ... ORDER BY openedAt DESC LIMIT 10`（或 LAG 窗口函数方案） |
| `data/BookRepository.kt` | [改] | 新增 `getDedupedRecentHistoryFlow()` 封装 DAO 去重查询；新增 `saveCoverImage(context, bookId, bytes)` 保存封面到 `filesDir/cover/` |
| `prefs/AppPrefs.kt` | [改] | 新增 `companion object` 键：`KEY_GLOBAL_TOC_RULES`（String/JSONArray）、`KEY_DEFAULT_FONT`（String）；配套 `Flow` + `suspend set` |
| `ui/shelf/ShelfViewModel.kt` | [改] | 保留 `recentHistory`（书架内去重版），但书架 UI 不再显示最近阅读 section |
| `ui/reader/TocDrawer.kt` | [改] | 移除底部"章节识别规则"整个区域（`Divider` + `Text("章节识别规则")` + `Switch` 列表） |

**关键实现要点**：
- 去重查询备选方案（按优先级）：① `LAG()` 窗口函数 → ② Kotlin `distinctBy { it.bookId }` → ③ 新增 SQL 列标记
- `saveCoverImage` 路径：`context.filesDir/cover/{bookId}.png`（bookId = filePath 的 URLEncoder 版本）
- `globalTocRules` 存储格式：`["rule_chinese_num","rule_arabic_num"]` JSON 数组

---

#### T03: EPUB 解析 + 导入增强 + 封面处理

| 属性 | 值 |
|------|-----|
| **Task ID** | T03 |
| **优先级** | P0 |
| **依赖** | 无（使用现有 ImportManager 接口，独立可测） |

**改动文件（6 个）**：

| 文件 | 动作 | 说明 |
|------|------|------|
| `parser/EpubParser.kt` | [新] | 核心解析器：`ZipFile` 读取 → `XmlPullParser` 解析 container.xml/OPF → 提取 metadata/manifest/spine → HTML 去标签 → `EpubParseResult` |
| `feature/import/ImportManager.kt` | [改] | `importFiles()` 按扩展名路由：`.epub`→`EpubParser`，`.txt`→`TxtParser`；提取封面后调 `BookRepository.saveCoverImage`；封面 URI 写入 `BookEntity.coverUri` |
| `feature/import/ArchiveExtractor.kt` | [改] | `extract()` 的 `else` 分支加入 `.epub`：直接 `out.add(path)`（不解压，epub 本身就是 zip 但由 EpubParser 内部处理） |
| `ui/shelf/ImportDialog.kt` | [改] | `collectTreePaths` 加入 `name.endsWith(".epub", true)`；文件选择器回调后显示 Snackbar 导入进度；`filePicker.launch` MIME 加入 `"application/epub+zip"` |
| `data/BookRepository.kt` | [改] | 若 T02 未合并则此处添加 `saveCoverImage`（已在 T02 声明，此处使用） |
| `parser/Chapter.kt` | [改] | 确认兼容 EPUB 场景（`contentLines` 从 EPUB HTML 解析来，行数 > 0） |

**关键实现要点**：
- EPUB 内部结构：`META-INF/container.xml` → `rootfile full-path` → OPF 文件 → `<metadata>`（dc:title/dc:creator）+ `<manifest>`（`<item id/href/media-type>`）+ `<spine>`（`<itemref idref>`）
- 封面查找逻辑：manifest items 中 `id` 包含 "cover"（大小写不敏感）或 `properties="cover-image"`
- HTML→纯文本：`replace(Regex("<[^>]*>"), "")`，然后 `android.text.Html.fromHtml(..., FROM_HTML_MODE_LEGACY)` 解码实体
- 章节切分：spine 中每个 itemref 对应一个 HTML 文件→解析为 `EpubChapter`；若无明显章节标记，整个 HTML 作为一个章节
- 封面存储：`context.filesDir/cover/{bookId}.png`，URI 格式 `file:///data/data/com.example.reader/files/cover/{bookId}.png`

---

#### T04: 阅读器面板重构 — 可收起 + 功能重组

| 属性 | 值 |
|------|-----|
| **Task ID** | T04 |
| **优先级** | P0 |
| **依赖** | 无（纯 UI 改动，使用现有 prefs） |

**改动文件（7 个）**：

| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/reader/ReaderScreen.kt` | [改] | 引入 `var isPanelVisible = false`；用 `AnimatedVisibility` 包裹 TopBar/BottomBar；移除旧 Scaffold topBar/bottomBar 参数；面板可见时显示 TopBar + BottomBar |
| `ui/reader/ReaderTopBar.kt` | [新] | `Row(返回IconButton + Text(书名, weight(1f)) + 溢出IconButton)`；溢出菜单 DropdownMenu：搜索🔍、书签🔖 |
| `ui/reader/ReaderBottomBar.kt` | [新] | `Row(太阳IconButton + 章节Text + 排版IconButton)`；太阳弹出菜单：亮度Slider + 背景色ChipRow + 日夜模式Switch；排版弹出：字号/边距/对齐/字体（可用 ModalBottomSheet 或内联 DropdownMenu） |
| `ui/reader/ReaderContent.kt` | [改] | `detectTapGestures` 中 CENTER 区域：若 `isPanelVisible`→toggle 隐藏面板；若 `!isPanelVisible`→toggle 显示面板。面板隐藏时 CENTER 不触发 `OPEN_MENU` |
| `ui/reader/ReaderToolbar.kt` | [改] | 简化为空或标记 `@Deprecated`；旧 5 按钮（目录/设置/书签/搜索/高亮）功能已迁移 |
| `ui/reader/SettingsSheet.kt` | [改] | 移除已在底部排版键中覆盖的部分（字号/行距/边距/对齐/字体），保留高级设置（翻页动画/RTL/点击区域/纹理/段距/字距/首行缩进） |
| `ui/reader/ReaderViewModel.kt` | [改] | 无核心逻辑变更；确认 `saveProgress` 在面板切换时不重复调用 |

**关键实现要点**：
- `AnimatedVisibility(visible = isPanelVisible, enter = fadeIn + slideInVertically, exit = fadeOut + slideOutVertically)`
- 章节导航：◀（上一章）→ T（当前章名，点击打开 TocDrawer）→ ▸（下一章）
- 太阳图标功能对照：
  - **亮度**：`prefs.brightness`，Slider(-1f..1f)，-1=跟随系统
  - **背景色**：`prefs.themeMode`，ChipRow(浅色/深色/OLED黑/羊皮纸)
  - **日夜模式**：已包含在背景色中（深色=夜间）
- 排版键功能对照：
  - **字号**：`prefs.fontScale`，Slider(0.5f..3f)
  - **页边距**：`prefs.pageMargin`，Slider(0..64)
  - **对齐**：`prefs.alignment`，ChipRow(起始/居中/两端/末尾)
  - **字体**：`prefs.fontFamily`，ChipRow(系统/思源/等宽)

---

#### T05: 个人页完成 + 设置页完成 + TOC 规则迁移 + 最近阅读去重

| 属性 | 值 |
|------|-----|
| **Task ID** | T05 |
| **优先级** | P1 |
| **依赖** | T01（页面骨架）, T02（去重查询 + AppPrefs + TocDrawer 已移除规则） |

**改动文件（7 个）**：

| 文件 | 动作 | 说明 |
|------|------|------|
| `ui/profile/ProfileScreen.kt` | [改] | 完成实现：`LazyRow` 展示去重最近阅读（`BookCover` 卡片 + 点击打开）；"阅读统计 >" 点击导航 StatsScreen；"系统设置 >" 点击导航 SettingsScreen |
| `ui/settings/SettingsScreen.kt` | [改] | 完成实现：主题模式（DropdownMenu 或 ChipRow）、默认字体（ChipRow）、目录规则（点击导航 TocRuleSettingsScreen）、关于（Text v1.1.0） |
| `ui/settings/TocRuleSettingsScreen.kt` | [新] | 独立页面：LazyColumn 列出 `TocRules.ALL` 每项 + Switch（读写 `AppPrefs.globalTocRules`）；顶栏返回键 |
| `ui/reader/TocDrawer.kt` | [改] | 已在 T02 移除规则开关；T05 确认无残留，目录跳转功能正常 |
| `ui/shelf/BookCover.kt` | [改] | `coverUri` 为空时显示 `R.drawable.ic_default_cover`（默认占位封面） |
| `res/drawable/ic_default_cover.xml` | [新] | 矢量图：书本轮廓 + 问号，主题色适配（`?attr/colorOnSurfaceVariant`） |
| `res/values/strings.xml` | [改] | 新增字符串资源：底部导航标签（书架/书城/个人）、个人页标题、设置项标签、目录规则标题、默认封面 contentDescription |

**关键实现要点**：
- `ProfileScreen` 最近阅读：`val recentBooks by repository.getDedupedRecentHistoryFlow().collectAsState()` → 关联 `BookEntity` → LazyRow 展示
- 去重逻辑在 Repository/DAO 层（T02 已实现），ProfileScreen 直接使用
- `TocRuleSettingsScreen`：`val rules by prefs.globalTocRules.collectAsState()` + `Switch(onCheckedChange = { prefs.setGlobalTocRules(newRules) })`
- 全局 TOC 规则影响 `TxtParser.parseWithRules()` 和 `EpubParser`（如 EPUB 也需章节检测规则）
- 导航：ProfileScreen → `onNavigateToStats`/`onNavigateToSettings` → NavHost 路由；SettingsScreen → `onNavigateToTocRules` → NavHost 路由

---

### 8. 共享约定

1. **底部导航路由**：`shelf` / `bookstore` / `profile` 三个顶层路由，reader 路由 `reader/{bookPath}` 全屏覆盖
2. **封面路径约定**：`context.filesDir/cover/{bookId}.png`，bookId 不含特殊字符（URLEncoder 处理）
3. **全局 TOC 规则存储**：DataStore key `global_toc_rules`，JSON 数组格式 `["rule_id_1","rule_id_2"]`
4. **EPUB 解析约定**：HTML→纯文本使用 `Regex("<[^>]*>")` 去标签 + `Html.fromHtml` 解码实体；不处理 CSS 样式
5. **面板动画**：统一使用 `animateFloatAsState` 或 `AnimatedVisibility`，时长 300ms
6. **所有新页面**：使用 `@OptIn(ExperimentalMaterial3Api::class)`（M3 1.1.2 多个组件仍标记为 Experimental）
7. **Divider**：Material3 1.1.2 用 `Divider`（非 `HorizontalDivider`，那是更高版本的 API）
8. **SelectionContainer**：导入路径 `androidx.compose.foundation.text.selection.SelectionContainer`
9. **TOC 规则全局化后**：`TxtParser.parse()` 默认使用全局 TOC 规则（从 AppPrefs 读取），移除 per-book 规则参数（或保留为可选 override）

---

### 9. 任务依赖图

```
┌─────────────┐
│     T01     │  基础设施（底部导航 + 骨架 + Shelf调整）
│   (6 files) │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│     T02     │     │     T03     │     │     T04     │
│   数据层    │     │  EPUB导入   │     │  阅读器面板  │
│  (5 files)  │     │  (6 files)  │     │  (7 files)  │
└──────┬──────┘     └─────────────┘     └─────────────┘
       │               (独立并行)          (独立并行)
       ▼
┌─────────────┐
│     T05     │  个人页+设置页+TOC迁移+去重
│  (7 files)  │
└─────────────┘
```

**并行度**：T01、T02、T03、T04 可同时开工（操作不同文件，无冲突）。T05 等待 T01（页面骨架）+ T02（数据查询）完成后进行。

**预估总改动量**：新建 9 个文件 + 修改 18 个文件 = 约 31 个文件受影响。每个任务含 5–7 个文件。
