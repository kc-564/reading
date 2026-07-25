# PDF 阅读可行性调研（F04 · 仅调研结论，无代码）

> 结论先行：本期 **不实现** PDF 支持（任务清单 F04 明确为"仅调研"）。本文档汇总技术选型、约束与推荐路线，供后续迭代决策。

---

## 1. 背景与目标

当前阅读器仅支持 TXT（含 zip/rar 内嵌文本）。用户常有的书源为 PDF（扫描版 / 文字版）。调研目标：

- 能否在既有 **MVVM + Room + 自研排版引擎** 架构内接入 PDF？
- 受 **minSdk 26 / Kotlin 1.9.22 / Compose BOM 2023.10.01 / 无本机编译（CI 验证）** 约束，哪些方案风险最低？
- 能否复用现有"章节切分 + 分页 + 进度"能力？

---

## 2. 可选方案对比

| 方案 | 来源 | 渲染 | 文字提取 | 体积极大? | 许可 | 适配风险 |
|------|------|------|----------|-----------|------|----------|
| **Android `PdfRenderer`** | 框架自带（`android.graphics.pdf`，API 21+） | 逐页 `Bitmap` | 仅 API 28+ 有 `Page.getText()`（部分） | 否（系统） | Apache（框架） | 低；扫描版只能出图 |
| **`com.github.barteksc:android-pdf-viewer`** (PdfiumAndroid) | 开源 | 原生 View | 支持 | 是（~16MB so） | Apache-2.0 | 中（so 体积、ABI） |
| **Apache `PdfBox` Android 移植** | 开源 | 弱（无原生 View） | 强 | 大（~10MB+） | Apache-2.0 | 高（JVM 内存、慢） |
| **MuPDF / 商业 SDK** | 商业/开源 | 强 | 强 | 中 | 商用需授权 | 高（授权/合规） |

### 2.1 Android `PdfRenderer`（推荐基线）
- 优点：零新增依赖、零 so、与 minSdk 26 完全兼容；CI 无需额外 Native 工具链。
- 缺点：
  - **渲染即出 `Bitmap`**，无法直接拿到"字符级文本流"，因此**无法套用现有 `TxtParser` 的章节切分 / 段落测量 / `TextMeasurer` 分页**。
  - 文字版 PDF 的"选词/搜索/高亮"需要基于 `PdfRenderer.Page.getText()`（API 28+，且多数实现仅返回纯文本、无坐标），与现有 `HighlightEntity`（按 `charStart/charEnd` 高亮）不兼容。
  - 扫描版（图片型）PDF 无文本层，必须 OCR，超出本期范围。

### 2.2 PdfiumAndroid（`android-pdf-viewer`）
- 优点：渲染质量高、支持文字层与坐标，可支撑搜索/高亮。
- 缺点：引入 ~16MB 的 `libjniPdfium` 多 ABI so，违反"新增依赖尽量轻量"原则；CI 需 Native 链接但 GitHub Actions 标准镜像可编译；体积与分发成本高。

---

## 3. 与现有架构的衔接分析

现有"排版—分页—进度"链路高度依赖 **纯文本 + `TextMeasurer`**：

- `ReaderPagination.paginateBook(chapters: List<Chapter>, style: TextStyle, ...)` 接收字符级 `Chapter`。
- `ParagraphSplitter` / `LayoutEngine` 直接操作字符串与 `TextMeasurer`。

若接入 PDF：
1. **文字版 PDF**：需新增 `PdfParser` 将每页 `getText()` 拼成 `Chapter`（无可靠"章节标题"坐标，TOC 规则 `TocRules` 仍可套用纯文本）。分页可复用 `ReaderPagination`，但"页"概念会与 PDF 物理页冲突——需明确"逻辑页"= 排版页，原 PDF 页仅作书签锚点。
2. **渲染**：`PdfRenderer` 出图后只能以 `Image` 展示，**无法**套用字号缩放/段距/对齐/主题配色等现有 `ReaderStyleConfig` 能力。即 PDF 与 TXT 将走两套渲染路径，UI 复杂度上升。
3. **高亮/搜索**：文字版可桥接到 `HighlightManager`/`SearchEngine`（按字符偏移），扫描版不行。

**结论**：复用收益有限，主要收益仅在"文字版 PDF 的章节切分 + 进度持久化"，但渲染与交互需另起炉灶。

---

## 4. 风险与未决项

1. **渲染一致性**：PDF 图文混排与自研段落排版无法统一，易产生"两种阅读体验"，需产品拍板是否接受。
2. **体积/性能**：Pdfium 方案显著增大 APK，低端机渲染大文档可能卡顿。
3. **扫描版 PDF**：无 OCR 即不可用，OCR 依赖第三方（如 ML Kit），超出本期。
4. **TOC 坐标**：PDF 章节锚点需映射回逻辑页，跨章进度公式需扩展 `globalPercentOf` 以兼容"非连续字符区间"。

---

## 5. 推荐路线（供后续迭代）

- **近期（低成本）**：采用 **`PdfRenderer` + 文字版 PDF** 只读模式——将每页文本拼接为 `Chapter`，复用 `TocRules` 切目录、复用 `ReaderPagination` 做逻辑分页与进度；渲染以"整页文本 + 逻辑分页"为主，**不追求像素级图文还原**。扫描版提示"暂不支持"。
- **中期（体验优先）**：若用户强需求图文混排/高亮，评估引入 **PdfiumAndroid**，代价是 APK 体积与 Native 维护成本；并将 `HighlightEntity` 扩展为兼容"PDF 页 + 坐标矩形"。
- **暂缓**：扫描版 PDF + OCR 不在路线图中。

> 本期按任务约定仅交付本文档，不落地任何 PDF 代码。
