# 版本与发布规范（权威）

所有后续 agent 必须遵守本文件。本仓库产品名固定为 **dev-reader**，plugin ID 为 `io.github.ataraxyadong.devreader`。本规范不授权改 plugin ID、删除用户配置或擅自删除 Git tags。

## 最终版本规则

五个插件统一从 **0.1.0** 开始，之后各自独立递增，不要求一直保持相同版本号。只使用纯数字 SemVer `X.Y.Z`，数字不带前导零。禁止 rc、local、ui、日期、分支名等后缀和 `+build` 元数据。

- 问题修复、小样式调整：升 patch，例如 `0.1.0` → `0.1.1`。
- 新功能：升 minor，patch 归零，例如 `0.1.2` → `0.2.0`。
- 稳定基线真实验收通过后发布 `1.0.0`；之后破坏性变化升 major，minor/patch 归零。`0.x` 期间不兼容变化升 minor，并明确说明。
- 用户不要求旧版兼容；仍须如实说明新安装、配置迁移、plugin ID 和功能限制，不能把不兼容说成无影响升级。

纯数字版本不等于宣称稳定。早期 `0.x` 可以在说明中明确“早期版本”和未覆盖的验证；GitHub 的 prerelease 布尔标志与版本字符串独立，真实验收不完整时可设为 true，但版本仍为纯数字。不得因此添加 rc 后缀。`1.0.0` 及以上的正式稳定发布必须完成真实关键验收。

IDE 平台兼容范围（例如 `213–252.*`）独立于插件 SemVer。兼容范围变化应按实际行为判断升级级别，不能用插件版本号代替 IDE build 范围。

## 唯一来源与产物名称

唯一插件版本来源为 `gradle.properties` 的 `pluginVersion`，值不带 `v`。Gradle `patchPluginXml` 从该属性注入内部 `plugin.xml`；禁止在源码描述文件中手写第二个版本。

| 项目 | 规则 | 本仓库示例 |
| --- | --- | --- |
| plugin version | 纯数字 `X.Y.Z` | `0.1.0` |
| Git tag | `v` + version | `v0.1.0` |
| 安装 ZIP | 产品名 + `-` + version + `.zip` | `dev-reader-0.1.0.zip` |
| GitHub Release | 与 tag 对应 | `dev-reader 0.1.0` |

统一稳定产品名分别为 `dev-reader`、`educative-reader`、`geektime-reader`、`reading-plugin`、`quiet-words`。本仓库只使用 `dev-reader`，不改其他插件。GitHub artifact 的外层 ZIP 不能直接安装；Release 只能发布其中的安装 ZIP 与校验文件。

## master、CI 与发布流程

`master` 是唯一默认、最新整合主线，保留原功能分支。汇合全部有效历史与本次功能并回归，不强推、不删除分支、不丢弃用户工作。

- 普通 push、PR 和手动 CI 执行版本策略测试、Java 测试、构建、IDE 兼容性检查、浏览器回归与 ZIP 校验，不创建 Release。
- 先在 master 更新唯一版本来源，确认 master CI 成功，再创建唯一版本 tag；`v*` tag 触发 Actions 构建与发布。CI 要求 tag/version 相同、tag 提交已属于 master、ZIP 名称/内部产品名/ID/version/IDE 范围一致。
- 所有构建检查成功后，发布 job 只下载本次 CI artifact，先查 SHA-256，再复验 ZIP，只上传安装 ZIP 与 `SHA256SUMS.txt`。禁止用本地包代替 CI 产物。
- 每次 tag 发布都要有 `release-acceptance/X.Y.Z.json`：测试人、系统/IDE、测试提交、结果、公开证据摘要和限制。模板见 `.github/release-acceptance.example.json`。
- 真实关键验收包括安装启动、实际账号与权限内容、导航快捷键、Markdown/图片和设置持久化。测试绿、模拟账号或样本页面均不能代替真实账号验收，不能虚填 passed。CI 只能检查记录完整性及被验收源码一致性，不能证明记录内容真实。
- `0.x` 未完成真实验收时记录 `status: partial`、各项状态和限制，并作为早期版本发布（GitHub prerelease=true）。完成真实关键验收后才记录 `status: passed`；`1.0.0` 及以上必须为 passed。验收后产品源码、依赖或兼容范围改变，需重新验收。
- 公开产物排除凭据、用户数据、课程/题库原文、测试截图和私有验收材料。验收记录只保存可公开摘要，不写 cookie、token 或账号隐私。

## Release 文字格式

标题固定为“产品显示名 X.Y.Z”，本仓库为 `dev-reader 0.1.0`；tag 仍为 `v0.1.0`。不要把 Draft、早期版本、版本重置、统一起点等过程描述加入标题。

正文按[发布模板](.github/release-notes-template.md)只写“功能、修复、安装、已知限制”，不重复产品名大标题、不增加内部流程开场白。每次新版本须把功能/修复条目更新为实际改动；未完成真实验收、兼容性及迁移边界放在“已知限制”，不能因润色而隐藏。正文的 Actions 来源和校验方式放在安装说明内。

已授权的标题/正文润色可以直接编辑现有 Release，不改变 tag、安装 ZIP、校验文件或验收事实，也不为此重新构建发布。只有发布文字、模板和规范变化时，可在完成本地发布脚本回归后用 `[skip ci]` 提交避免重打已发布版本；实际功能、依赖、版本或构建行为变化仍必须执行完整 CI 和新版本流程。

## 不可覆盖与本次迁移

已发布 tag 和资产不可覆盖。内容变化使用新版本；任何重试先核查 Actions、tag、Release、资产状态，结果不确定时先只读查询，不能直接重发。自动发布发现同名 Release 会停止，不 clobber、不 force、不删除后重建。

本次用户明确批准：五个插件统一纯数字 `0.1.0`；清理其现有全部 Releases 及附件，但保留代码历史和所有 Git tags。本 agent 只操作 `AtaraxyAdong/dev-reader`。先完成新版本验证、master CI 和产物准备，再清理批准范围内的旧 Release/附件并发布新版本，尽量减少无下载包时间。旧 `v0.5.15-ui.2` tag 保留；它代表旧规则历史，不允许继续发布 ui 后缀。原 `0.5.15-ui.2` 为 prerelease也是历史事实，不回写为新规则。

此迁移删除授权只适用于本次已确认范围，不是未来 agent 的常规删除许可。未来删除不可恢复的 Release/附件必须获得用户针对范围的明确批准；Git tags 的删除/移动从未获批。若 `v0.1.0` 已占用，停止该 tag/发布动作并报告精确指向，不强推、不挪动、不删除。

## 后续 agent 检查清单

1. 读本文件和 AGENTS，检查最新 master、未结束 Git 操作、并发任务、所有远端分支/tag/Release/资产/CI；先核实不确定结果。
2. 选择纯数字语义版本，查目标 tag 与 Release 未占用，记录升级理由。只改唯一版本来源，保留稳定产品名及 plugin ID。
3. 汇合有效功能，更新说明，运行策略测试、Java/浏览器回归、插件/IDE 兼容性和 ZIP 元数据校验。明确区分自动测试、实机操作与真实账号验收。
4. 更新本版本验收记录；未覆盖就如实写 partial/限制，不宣称稳定或实机全通过。确认公开包与记录不含禁止数据。
5. 正常推 master 并追踪 CI 成功，再创建唯一 `v<version>` tag 触发 Actions，不另起本地发布。
6. 等 tag CI 和发布完成；失败先查远端状态，禁止覆盖、盲目重试或自动删旧版本。内容变化必须新版本。
7. 核验 Release 标志、tag 提交、ZIP 身份、SHA-256 和 CI 来源；返回仓库、master/tag Actions、Release、规范链接及验收限制。
