# 侧栏 Markdown 阅读器：实现与验证记录

日期：2026-10-20。实现仓库：`E:\01-Java\mianshiya-plugin`；分支：`codex/sidebar-markdown-reader`；上游基线：`98d5a90901ba7b78c2fed8923b270e284db3a48d`。

## 实现范围

- 保留插件 ID 与 `面试鸭` ToolWindow ID。一个项目一个工具窗口 Content，详情按需创建一个 JCEF。
- 工具栏齿轮菜单提供左侧、右侧、底部停靠；同时支持 IDEA 原生工具窗口移动。新安装默认右侧，已保存的停靠位置交给 IDEA 恢复。
- 题库列表、题目列表、详情在同一窗口切换；返回复用列表实例，保留查询、分页、选择和滚动。
- HTML 内容先规范化为 Markdown，再采用本地样式渲染；Markdown 直接解析。未知格式显示转义后的原文提示，答案缺失、空值、权限及错误状态分别处理，不拿社区回答替代参考答案。
- 正文随可用宽高重排，始终单列；图片限制宽度，代码与表格独立横向滚动。移动或缩放不重新请求正文。
- 项目级控制器处理取消、迟到响应、快速切题与换号；题库完整顺序成功返回前禁用前后题，搜索和筛选保持当前结果快照。
- 阅读文档使用每次加载唯一的受控本地 file URL，兼容旧版和 2025.2 JCEF 的 URL 转换、加载去重行为。外部图片请求禁止发送或保存 JCEF Cookie。

## 兼容目标

用户要求支持最后一个独立发行的 IDEA Community 系列。选定 **IC 2025.2.6.3，build 252.28539.97**；这是 2025.2 系列的补丁版本。2025.3 起产品改为统一发行，参见 [JetBrains 说明](https://www.jetbrains.com/help/idea/intellij-idea-single-distribution.html)。

编译仍使用 IC 2021.3 SDK、Java 11 字节码，插件声明 `213` 至 `252.*`。没有引入 Ultimate 专属依赖。此声明不代表所有中间版本均已运行验证。

## 自动验证

构建使用项目私有 JDK 17 和 Gradle 用户目录，不修改机器全局 Java 或代理设置：

```powershell
$env:JAVA_HOME = Join-Path (Get-Location) '.superpowers\toolchains\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.superpowers\gradle-user-home'
& '.\gradlew.bat' --no-daemon check verifyPlugin buildPlugin runPluginVerifier `
  '-PreaderVerifierIde=.superpowers/ide/community-2025.2.6.3' `
  '-PreaderVerifierRuntime=.superpowers/ide/community-2025.2.6.3/jbr' `
  --console=plain '-Dfile.encoding=UTF-8'
```

验证记录：32 项 JUnit 测试，0 失败、0 错误；覆盖数据映射、会话事件、内容语义转换与清理、内容状态、导航及控制器并发时序。`check`、`verifyPlugin`、`buildPlugin` 通过。Plugin Verifier 针对上述社区版报告 **Compatible**。

Verifier 保留 3 项废弃／计划移除 API 警告：`JBCefJSQuery.create(JBCefBrowser)`、`ActionToolbar.setLayoutPolicy(int)`，以及原有浏览器编辑器的 `JBCefCookieManager.setCookie`。当前目标版本未报告二进制不兼容；未来升级超出 252 系列需要重新检查。

## 渲染验证

本地 Chromium 测试页加载实际 reader HTML/CSS/JS 资源，使用代表性标题、长文本、代码、表格及图片 fixture。

| 可用尺寸（CSS px） | 正文单列 | 整体无横向溢出 |
| --- | --- | --- |
| 280 × 600 | 通过 | 通过 |
| 360 × 600 | 通过 | 通过 |
| 480 × 600 | 通过 | 通过 |
| 960 × 180 | 通过 | 通过 |
| 1440 × 240 | 通过 | 通过 |

连续改尺寸后初始化次数仍为 1；延迟 5 秒的图片没有阻塞正文就绪（测试记录约 121 ms）；失败图片显示可点击提示。已目视检查窄栏和浅底栏效果。

这验证了浏览器资源重排，不替代原生 JCEF、工具窗口停靠或系统 DPI 的实机验收。复核同时检查了实际 2021.3 与 2025.2 SDK 的 `loadHTML` 实现，修正受控 file URL 和快速切题的加载去重问题。

## 安装与操作

安装包：`build/distributions/Mianshiya-0.5.6.zip`，为本地修改构建，未发布到 Marketplace。

最终构建大小：4,076,315 字节；SHA-256：`610FDE0A533E6D5756B1A23C8CB255678E9859FAE3BA9BAB406D7C9D5F6DEAB8`。

1. 在 IDEA 的 **Settings → Plugins → 齿轮 → Install Plugin from Disk** 选择该 ZIP，按 IDEA 提示重启。
2. 打开 **View → Tool Windows → 面试鸭**；使用窗口内齿轮菜单选择停靠左侧、右侧或底部，也可通过 IDEA 原生菜单移动。
3. 使用现有登录入口登录，进入题库或全部题目。双击题目后在同一窗口阅读，通过返回按钮恢复列表。
4. 拖动窗口边缘改变尺寸；详情保持单列，表格和代码在自身区域滚动。可在 Keymap 中为“显示／隐藏阅读窗口”配置快捷键。

## 尚未完成的实机验收

- **登录后的真实详情接口联调尚未通过验收。** 已从公开客户端确认 `/api/question/get/vo` 路径及 `bestQuestionAnswer` 参考答案字段；候选 `id` 查询参数及成功载荷完整性仍需真实响应核对。无凭据公开探测得到业务码 `50000`、无数据，未获取成功样本。具体证据和推断见 [接口契约记录](reader-api-contract.md)。自动测试使用合成 DTO，不应视为真实题目加载成功。
- 原生 IDEA 内左／右／底部实际交互、100%／150%／200% 系统缩放、登录与权限态、主题切换、两项目并行和关闭后的资源释放尚未手工执行完整矩阵。
- 图片如依赖网站认证 Cookie，会显示失败链接；阅读浏览器不会共享或注入接口登录 Cookie。

当前交付为已构建、通过自动检查的本地插件实现。完整上线验收仍需补齐上述真实账号和原生 IDE 检查。
