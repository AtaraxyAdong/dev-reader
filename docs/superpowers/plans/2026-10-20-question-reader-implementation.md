# IDEA 题目阅读工具窗口 — Implementation Doc

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现支持左侧、右侧、底部停靠及动态尺寸适配的题目阅读工具窗口，列表与 Markdown 详情在同一窗口内切换。

**Architecture:** 在现有 ToolWindow 内创建唯一 Content，由项目级 `SidebarController` 驱动 `CardLayout` 页面。数据适配与 Markdown 转换独立于 Swing / JCEF；所有题目入口进入统一控制器，阅读器加载本地生成的文档。

**Tech Stack:** Java 11、Swing、IntelliJ Platform 2021.3 基准、JCEF、Retrofit、flexmark 0.62.2、JUnit 4.13.2、Gradle 7.6。

**Spec:** [Design Doc](../specs/2026-10-20-question-reader-design.md)。执行前同时阅读两份文档。

日期：2026-10-20
状态：用户已授权实施，插件改动与自动验证已执行。以下保留原始验收清单；完成证据及仍未验证的项目以插件仓库的 `docs/reader-validation.md` 为准。

目标环境：IDEA 最后一个独立发行的社区版系列 2025.2.x，具体检查版本为 IC 2025.2.6.3（252.28539.97）；保留 2021.3 编译基准。静态兼容检查不等于完整 IDE 内交互验收。

## Global Constraints

- 代码目标为 `yuyuanweb/mianshiya-plugin`，分析基准为 `98d5a90901ba7b78c2fed8923b270e284db3a48d`；当前 `E:\01-Java\mianshiya` 是网站仓库，仅存放本轮文档。
- 保留插件 ID `com.github.yuyuanweb.mianshiyaplugin` 和 ToolWindow ID `面试鸭`。
- 一个工具窗口、一个 Content、一套工具栏、最多一个按需创建的 JCEF；三个停靠位置不各创建一个阅读器。
- 使用 `BANK_LIST / QUESTION_LIST / QUESTION_DETAIL` 页面；返回列表保留原列表实例和查询状态。
- 正文始终单列；宽度 `< 480 CSS px` 使用 12 px 水平内边距，`≥ 480 CSS px` 使用 16 px；正文最大宽度 960 px。
- 正文 viewport 高度 `< 300 CSS px` 时上下内边距为 8 px；缩放窗口不自动缩小字号。
- 最少验收逻辑尺寸：`280×600`、`360×600`、`480×600`、`960×180`、`1440×240`；系统缩放覆盖 100%、150%、200%。
- 保留 Java 11、Gradle 7.6、Gradle IntelliJ Plugin 1.14.2 构建基准；不得仅扩大 `untilBuild` 宣称新版兼容。
- 禁止猜测详情 endpoint，禁止用旧网站接口替代插件接口契约，禁止将社区回答第一条视为参考答案。
- 第一版不新增答案磁盘缓存；退出登录／切换账号清除相关内存状态。
- 全局 Action 根据当前 Project 取得控制器；账号会话变化需要通知所有打开的项目。
- 本计划不包含插件发布；交付本地安装包和验证记录。

## Review Focus

以下五类易遗漏情况已分别分配到对应任务，Review 时复核其断言与结果：

1. HTML 中的代码缩进、实体、相对图片地址、表格和安全语义标签经过转换后仍完整：任务 2。
2. A → B 快速切题、返回列表、注销后旧请求才返回，不能覆盖新状态：任务 3。
3. 底栏低高度与高 DPI 组合下，工具栏不挤掉正文，代码／表格不撑宽整个页面：任务 4、5。
4. 共享登录状态变化、多个项目、重复显示隐藏、关闭项目后回调，不能串状态或泄漏浏览器：任务 1、3、5。
5. 只有题干可读、答案无权限、答案字段省略、格式未知，不能统一显示“暂无答案”：任务 0、1、4。

## 1. 路径与文件职责

以下 `J/` 仅为表格缩写，展开为 `src/main/java/com/github/yuyuanweb/mianshiyaplugin/`；`T/` 展开为 `src/test/java/com/github/yuyuanweb/mianshiyaplugin/`。均相对**插件工作区**，并非当前网站目录。

### 修改现有文件

| 路径 | 变更职责 |
| --- | --- |
| `J/toolWindow/MyToolWindowFactory.java` | 创建唯一根面板、Content 和 disposer |
| `J/actions/QuestionAction.java` | 全部题目入口委托控制器 |
| `J/actions/QuestionBankAction.java` | 拆出纯题库面板，题库选择进入控制器，不创建新 Content |
| `J/view/QuestionListManager.java` | 抽出返回纯组件的方法，题目回调替换 `openNewEditorTab` |
| `J/utils/ContentUtil.java` | 检查遗留调用；新阅读流程不再调用 `createContent` |
| `J/utils/ThemeUtil.java` | 提供 IDEA 颜色、字体到阅读样式的映射，保留旧调用兼容 |
| `J/api/MianShiYaApi.java` | 仅按验证所得契约补充必需 API |
| `J/config/ApiConfig.java` | 增加共享会话但不自动弹登录的阅读请求配置 |
| `J/api/interceptor/ResponseInterceptor.java` | 支持阅读请求的静默业务错误处理，保留其他调用默认行为 |
| `src/main/resources/META-INF/plugin.xml` | 默认右侧停靠、控制器／Action 注册、稳定 ID 与兼容性声明 |
| `src/main/resources/messages/MyBundle.properties` | 新交互文案；同步仓库实际存在的其他语言资源 |
| `build.gradle`、`gradle/libs.versions.toml` | 按需启用 flexmark 核心、表格、HTML 转 Markdown 依赖 |

账号变更通知接入点按任务 1 对登录／退出的实际调用点定位；不假设现有项目已经提供会话事件。`FileUtils`、旧 `file/preview` 实现不做无关重构，保留历史兼容用途。

### 新增文件

| 路径 | 职责 |
| --- | --- |
| `J/reader/SidebarController.java` | 页面、选题、导航、请求代次与 Disposable 生命周期 |
| `J/reader/SidebarPanel.java` | 三页 CardLayout 与唯一工具栏 |
| `J/reader/QuestionReaderPanel.java` | 单个 JCEF、本地文档载入、主题与滚动位置 |
| `J/reader/ReaderView.java` | 控制器依赖的视图接口，供测试替身实现 |
| `J/reader/ToggleReaderAction.java` | 可在 Keymap 配置的显示／隐藏动作 |
| `J/reader/model/ReaderModels.java` | 下文定义的不可变 DTO、枚举及导航上下文，使用 Java 11 普通类 |
| `J/reader/data/QuestionRepository.java` | 数据访问接口 |
| `J/reader/data/ApiQuestionRepository.java` | 真实契约适配、权限状态与导航映射 |
| `J/reader/data/ReaderSessionListener.java` | 登录状态变更的应用级事件契约 |
| `J/reader/render/ContentNormalizer.java` | HTML / Markdown 到规范 Markdown |
| `J/reader/render/MarkdownRenderer.java` | 规范 Markdown 到 HTML fragment |
| `src/main/resources/reader/reader.html` | 本地阅读壳，无远程网站样式依赖 |
| `src/main/resources/reader/reader.css` | 主题变量、自适应、内容元素样式 |
| `src/main/resources/reader/reader.js` | 内容锚点、进度恢复、链接和图片事件 |
| `T/reader/data/ApiQuestionRepositoryTest.java` | 数据契约与状态映射测试 |
| `T/reader/render/ContentNormalizerTest.java` | 有语义价值的转换回归测试 |
| `T/reader/SidebarControllerTest.java` | 导航、过期响应、会话和释放测试 |
| `src/test/resources/reader/` | 脱敏内容与接口 fixture；不得包含 Cookie／token |

这些是拟新增的路径，不表示当前源码已经具有这些类型。

## 2. 模块之间的接口

`ReaderModels` 内的类型采用不可变普通 Java 类；字段使用明确类型，构造器／访问器保持一致。`bankId` 和 `sessionUserId` 允许为 null，其余字段用显式状态表达缺失。

| 类型 | 字段与约束 |
| --- | --- |
| `SourceFormat` | `MARKDOWN / HTML / UNKNOWN` |
| `PartStatus` | `AVAILABLE / EMPTY / FORBIDDEN / UNAVAILABLE / ERROR` |
| `ContentPart` | `PartStatus status`、`SourceFormat format`、`String content`、`URI baseUri`；非 AVAILABLE 内容为空，状态另行显示 |
| `QuestionDocument` | `long id`、`String title`、`ContentPart body`、`ContentPart referenceAnswer`、`URI sourceUri` |
| `LoadStatus` | `READY / NEEDS_LOGIN / FORBIDDEN / NOT_FOUND / ERROR / CANCELLED` |
| `QuestionLoadResult` | `LoadStatus status`、成功时的 `QuestionDocument document`、面向用户的 `String message` |
| `NavigationContext` | `Long bankId`、`List<Long> orderedQuestionIds`、`long selectedId`；列表顺序复制为不可变快照 |
| `QuestionSelection` | `long id`、`String title`、`NavigationContext navigation` |
| `ReadingPosition` | `String blockId`、`double offsetRatio`；按内容块保存，不只保存绝对滚动像素 |
| `ReaderTheme` | 字体、字号、前景、背景、弱化文字、边框、代码背景等 CSS 变量值 |
| `RenderedQuestion` | `long id`、`String html`、`URI sourceUri`；包含固定状态文案和已转义元信息 |

数据接口和转换接口：

```java
QuestionLoadResult QuestionRepository.loadQuestion(long questionId, Long bankId);
NavigationContext QuestionRepository.loadNavigation(NavigationContext current);
String ContentNormalizer.toMarkdown(ContentPart part);
String MarkdownRenderer.renderFragment(String markdown);
```

数据接口为同步契约，控制器必须在后台任务中调用。`loadNavigation` 成功返回确认过的导航；失败保留已有列表快照，不能影响正文成功结果。`toMarkdown` 只接收 AVAILABLE 且格式已知的内容；其他状态由组装文档的逻辑处理，不用空串吞掉错误。

控制器与视图接口：

```java
void SidebarController.showBanks();
void SidebarController.showQuestions(Long bankId);
void SidebarController.showQuestion(QuestionSelection selection);
void SidebarController.backToList();
void SidebarController.previousQuestion();
void SidebarController.nextQuestion();
void SidebarController.retry();
void SidebarController.onSessionChanged();
void SidebarController.dispose();

void ReaderView.showBanks();
void ReaderView.showQuestions(Long bankId);
void ReaderView.restoreQuestionList();
void ReaderView.showLoading(QuestionSelection selection);
void ReaderView.showDocument(RenderedQuestion document, ReadingPosition position);
void ReaderView.showFailure(QuestionLoadResult result);
void ReaderView.updateNavigation(boolean hasPrevious, boolean hasNext);
void ReaderView.clearSessionContent();
```

`SidebarPanel` 实现 `ReaderView`。组装全文、获取主题、进度回传和后台执行器作为控制器的依赖／回调接入，不将 `Project`、JCEF 或 Swing 暴露给数据与转换模块。文案在 Java 端解析资源 bundle 后注入模板，避免 HTML / JS 再维护一套翻译。

## 3. 任务与验证步骤

### 任务 0：建立正确的实施基线并验证内容契约

**产出：** 插件工作区、构建基线记录、内容契约记录及脱敏样本。
**涉及文件：** 插件仓库中的 `docs/reader-api-contract.md`、`src/test/resources/reader/`；本任务不编造 API。

- [ ] 在独立插件工作区检出分析提交，记录工作区绝对路径与 `git rev-parse HEAD`；已有用户修改需保留，差异先核对。不得在网站目录直接新增 Java 插件工程，也不得把另一 Git 仓库嵌入其中。
- [ ] 核对 origin、Gradle wrapper、Java 和实际目标 IDEA 版本；记录 `.\gradlew.bat --version` 与 `.\gradlew.bat tasks --all`。确认 `test`、`check`、`verifyPlugin`、`buildPlugin`、`runIde` 可用。
- [ ] 在获授权的登录环境中确认题干完整性、参考答案来源、内容格式、资源基址、权限码与导航顺序。记录请求方法、路径、必要参数、字段语义；不记录凭据。
- [ ] 对正常 Markdown、富文本、无权限、无答案及会话失效保存脱敏 fixture；无法取得的响应明确标为未验证。
- [ ] 确定 `loadQuestion` 具体调用哪些接口。列表完整时复用列表；否则增加已经确认的详情接口。字段省略映射 UNAVAILABLE，只有契约明确表示不存在时映射 EMPTY。
- [ ] 在契约记录中写明 `referenceAnswer` 的来源与标题含义。社区列表不能直接作为来源；未验证的 `bestQuestionAnswer` 不作已确认映射。

**通过条件：** 能用真实来源说明题干和参考答案的完整性；能够区分缺失、无权限和空内容。若此条件未满足，任务 1 的真实接入不得标记完成；任务 2 和基于 fixture 的任务 3、4 可以推进。

### 任务 1：实现数据适配与静默登录状态

**Files:** `ReaderModels.java`、`reader/data/*`、`ApiConfig.java`、`ResponseInterceptor.java`、必要的 `MianShiYaApi.java` 方法；测试 `ApiQuestionRepositoryTest.java`。

**Consumes:** 任务 0 的已验证契约与 fixture。
**Produces:** `QuestionRepository`、`QuestionLoadResult`、`QuestionDocument`、`ReaderSessionListener`。

- [ ] 编写 `mapsCompleteQuestion`、`keepsBodyWhenAnswerForbidden`、`distinguishesMissingAndEmptyAnswer`、`keepsUnknownFormat`、`mapsExpiredSessionWithoutPrompt` 测试；分别断言完整正文、局部权限状态、UNAVAILABLE / EMPTY 区别、UNKNOWN 格式、NEEDS_LOGIN 且未调用登录 UI。
- [ ] 执行 `.\gradlew.bat test --tests "*ApiQuestionRepositoryTest"`，确认新增断言在实现前失败，且不是环境或依赖故障造成的假失败。
- [ ] 按表中类型实现 DTO 和 Repository；用可替换 API 接口／测试替身模拟响应，单元测试不依赖线上登录。
- [ ] 在 `ApiConfig` 增加阅读专用 API 实例，复用 Cookie 读取和请求拦截逻辑；`ResponseInterceptor` 增加可显式关闭自动登录的配置，现有构造默认行为保持兼容。阅读请求不自动弹窗。
- [ ] 定位实际登录成功、退出、换号处理点，发布应用级 `ReaderSessionListener` 事件。事件只含会话代次／用户标识，不含凭据；每个打开项目分别清理状态。
- [ ] 将题目正文加载与导航加载解耦；导航失败不回退或覆盖正文。校验所选题在导航 ID 列表中，异常列表不启用错误切题动作。
- [ ] 重新执行数据测试；确认错误信息未包含响应凭据，登录取消不会触发循环重试。

**交付检查：** 正常、部分可读和异常状态均能通过稳定 DTO 表达；未取得真实样本的状态继续在契约记录中标注验证缺口。

### 任务 2：实现可测试的 Markdown 规范化与 HTML 渲染

**Files:** `ContentNormalizer.java`、`MarkdownRenderer.java`、`build.gradle`、`gradle/libs.versions.toml`、`ContentNormalizerTest.java` 与 fixture。

**Consumes:** `ContentPart`、`SourceFormat` 与内容样本。
**Produces:** `toMarkdown(ContentPart)` 与 `renderFragment(String)`。

- [ ] 建立 Markdown 和富文本样本：中文段落／列表、代码缩进及语言、HTML 实体、相对图片链接、表格、`sub/sup/br`、携带原网站样式的节点、脚本与事件属性。
- [ ] 编写 `preservesCodeAndLanguage`、`resolvesRelativeImages`、`preservesTablesAndSemantics`、`removesWebsiteStylesAndActiveContent`、`rejectsUnknownFormat`；断言语义结构及关键文本，避免锁定整个 HTML 字符串。
- [ ] 执行 `.\gradlew.bat test --tests "*ContentNormalizerTest"`，确认测试能暴露转换缺失。
- [ ] 将 flexmark 核心、表格扩展及 HTML 转换模块统一锁到版本目录的 0.62.2；核实实际 artifact 名称和解析结果，禁止混用版本。若与基线不兼容，先更新两份文档中的版本决定再替换。
- [ ] 实现 HTML 清理与转换；保留必要语义标签的白名单，处理相对资源基址，未知格式显式拒绝。
- [ ] 实现 Markdown HTML 渲染；对原始 HTML、链接协议和资源地址执行约束，正文不携带原站 CSS／JS。生成稳定内容块标识供进度恢复使用。
- [ ] 重新执行转换测试，逐个检查代码、表格和图片 fixture 的输出；确认不引入 IDEA Markdown 插件依赖。

**交付检查：** 同一内容无论来源为 Markdown 或富文本，都能进入一致的本地阅读样式；不为了去样式而丢内容。

### 任务 3：实现导航、请求时序与生命周期控制器

**Files:** `SidebarController.java`、`ReaderView.java`、`SidebarControllerTest.java`。

**Consumes:** 任务 1 的 Repository 和状态模型、任务 2 的转换器。
**Produces:** 第 2 节列出的控制器方法和 `RenderedQuestion`。

- [ ] 使用假 Repository、可控后台任务队列、假 ReaderView 编写测试：`ignoresLateResultFromAAfterOpeningB`、`doesNotReopenDetailAfterBack`、`clearsContentOnSessionChange`、`ignoresCallbackAfterDispose`、`preservesListOnBack`。
- [ ] 增加 `disablesNavigationAtSnapshotEdges` 和 `keepsDocumentWhenNavigationFails`；断言当前搜索页首尾不能越界，导航错误不使正文失败。
- [ ] 执行 `.\gradlew.bat test --tests "*SidebarControllerTest"`，确认这些时序断言在实现前失败。
- [ ] 实现页面状态与选题逻辑。后台加载、转换和全文组装结束后，通过 UI 调度更新视图；提交前同时检查请求代次、题目 ID、会话代次和 disposed 状态。
- [ ] 实现返回列表取消／作废当前详情请求；前后切题复用统一 `showQuestion` 路径，不再调用旧 Preview 中的 URL 替换逻辑。
- [ ] 保存会话内 `ReadingPosition` 和答案折叠状态；注销／换号时清理并通知视图清空正文和列表数据。
- [ ] 增加两个独立控制器的隔离测试，验证 A 项目选题不改变 B 项目，应用级会话变更能使两者各自失效。
- [ ] 重新执行控制器测试；重复 dispose 安全，过期回调不调用任何视图方法。

**交付检查：** 控制器可脱离真实 JCEF 测试；快速操作不会串题、串项目或重新显示已注销内容。

### 任务 4：接入唯一工具窗口与自适应阅读器

**Files:** `SidebarPanel.java`、`QuestionReaderPanel.java`、`ToggleReaderAction.java`、`reader.html/css/js`，以及文件职责表中的 Factory、Actions、QuestionListManager、主题、plugin.xml 和消息资源。

**Consumes:** 控制器接口、RenderedQuestion、ReaderTheme、ReadingPosition。
**Produces:** 完整的真实 ToolWindow 阅读流程。

- [ ] 在 `QuestionListManager` 新增 `JComponent createPanel(Long bankId, Project project, Consumer<QuestionSelection> onQuestionOpen)`，保留原搜索／分页行为，移除组件内部创建 Content 的副作用。
- [ ] 为 `QuestionBankAction` 抽出纯题库组件，选择题库调用 `showQuestions(bankId)`。`QuestionAction` 调用 `showQuestions(null)`；所有全局 Action 使用事件 Project 查找控制器。
- [ ] 在 Factory 创建唯一 `SidebarPanel` 和唯一 Content；设置 disposer。将根工具栏和 CardLayout 接入，详情工具栏由同一个动作区切换，避免嵌套重复工具栏。
- [ ] 替换 `QuestionListManager#getDataPanel` 内的 `FileUtils.openNewEditorTab(...)` 为选题回调。搜索、全量列表、题库列表都必须经过该入口。
- [ ] 用 `rg -n "openNewEditorTab|addQuestionTab|ContentUtil.createContent" src/main` 审核残余调用；新浏览流程不允许继续创建编辑器或额外 Content，旧兼容代码保留并记录用途。
- [ ] 实现本地 reader 模板：一个纵向正文区域，代码与表格各自局部溢出，960 px 最大正文宽度；应用 480 px 宽度与 300 px 高度规则。
- [ ] Swing 工具栏使用 IDEA 的尺寸／缩放支持和溢出菜单；不以物理屏幕分辨率计算宽度，不设置撑大侧栏的固定最小尺寸。
- [ ] 首次进入详情才创建 JCEF，先检查支持性；不支持时显示提示和查看原文。窗口隐藏和页面切换不重建浏览器。
- [ ] 接入颜色、字体与主题变化监听；通过 CSS 变量更新。实现稳定内容块锚点回传、图片加载和重排后的恢复，合并尺寸事件，禁止 resize 触发 API 请求。
- [ ] 处理原文链接、文内锚点、图片失败、答案折叠与各类加载状态；所有题目标题、状态消息和元信息进入 HTML 前转义。
- [ ] 注册显示／隐藏 Action，不绑定默认全局快捷键；设置新安装默认右侧，不覆盖已有停靠设置。

**人工验证：** 使用 `.\gradlew.bat runIde`，在左、右、底部逐一完成列表 → 详情 → 切题 → 返回；在窄栏和底栏放入长标题、代码、表格与图片样本。检查没有第二套工具栏，没有新题目编辑器，没有底部多列布局。

### 任务 5：回归、兼容性与本地安装包

**Files:** 插件仓库的 `docs/reader-validation.md`；仅在验证发现真实兼容问题时修改相关实现／配置。

- [ ] 执行新增三个测试类及 `.\gradlew.bat check`，记录结果；仅因真实失败或新增改动重复相关测试。
- [ ] 执行 `.\gradlew.bat verifyPlugin` 与 `.\gradlew.bat buildPlugin`，记录检查结果和 `build/distributions/` 实际生成的 ZIP 路径。
- [ ] 确认是否提供兼容性验证任务；若有 `runPluginVerifier`，按插件配置显式指定基准版本和选定目标版本。`verifyPlugin` 与编译成功不能代替跨版本兼容验证。
- [ ] 在 IDEA 2021.3 基准和选定的实际使用版本运行 UI 验收；保留 252.* 上限时另覆盖一个 2025.2 版本，不能据此声称所有中间版本均已测试。
- [ ] 若实际使用版本超出当前声明，先解决并记录 SDK / JCEF 差异。IDEA 2026.2 的 JCEF 依赖改变需要适配版本策略，不把不兼容的新依赖直接加进旧基准构建。
- [ ] 完成下表，记录实际 IDE build、窗口尺寸、缩放、通过／失败和证据；无法验证的项明确注明。
- [ ] Review 最终 diff：没有把网站样式带回，没有新增每题浏览器／Content，没有源码凭据，没有无关网站改动。

| 验收组 | 必测内容 | 对应设计 |
| --- | --- | --- |
| 三处停靠 | 左、右、底部；移动后题目和进度保留 | §4、§5、§9.1–2 |
| 尺寸与 DPI | 五组基准尺寸；100% / 150% / 200%；连续拖动 | §5、§9.2、4–5 |
| 同窗口导航 | 题库、全量、搜索；返回保留筛选／页码／选中行／滚动 | §4.2–3、§9.3 |
| 内容完整性 | Markdown / HTML；代码、图片、表格；底栏单列 | §6.2、§9.4–6 |
| 请求时序 | 连续切题、返回后迟到响应、重复重试、网络失败 | §7、§9.7–8 |
| 会话状态 | 取消登录、过期、答案无权限、换号、注销 | §6.3、§7、§9.8 |
| 生命周期 | 两项目、反复显示隐藏、主题切换、项目关闭 | §7、§9.9 |
| 资源数量 | 通过调试／诊断计数确认每项目最多一个 reader JCEF；关闭后释放 | §7 |
| 交付真实性 | 真实接口验证记录、目标版本记录、可安装 ZIP | §9.10 |

## 4. 执行顺序与协作边界

推荐依赖顺序：任务 0 → 任务 1；任务 2 可在模型确定后与任务 1 并行；任务 3 → 任务 4 → 任务 5。

主 Agent 负责接口冻结、ToolWindow 集成、兼容性决策与最终 Review。适合委派的独立边界是数据 fixture／适配、内容转换及测试执行。并行执行前按文件分配所有权，不允许多个 Agent 同时修改 `ReaderModels.java`、`SidebarController.java` 或同一构建文件。各阶段形成可独立 Review 的改动单元。

实施记录位于插件仓库。成功登录后的真实详情响应尚未获得，不能将合成 DTO 测试视为线上内容完整性验收；原生 IDE 的停靠、系统缩放及多项目交互也需按上表继续验收。
