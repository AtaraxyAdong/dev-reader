# dev-reader

用于 JetBrains IDE 的侧栏阅读插件。当前通过面试鸭接口浏览题库与题目，以本地 Markdown 页面显示正文，提供紧凑阅读工具栏和独立图片窗口。

本仓库是独立维护的衍生版本，并非面试鸭官方发行版。数据源、账号和内容权限仍由原服务提供；更名不会改变接口、解锁权限或提供离线题库。

## 安装

从 [GitHub Releases](https://github.com/AtaraxyAdong/dev-reader/releases) 下载 `dev-reader-版本号.zip`，在 IDEA 的 **Settings > Plugins > 齿轮 > Install Plugin from Disk** 中选择该 ZIP。不要解压插件 ZIP，也不要安装 GitHub 的 Source code ZIP。

Release 安装包必须来自成功的 [Build plugin ZIP Actions](https://github.com/AtaraxyAdong/dev-reader/actions/workflows/build-plugin.yml)，附带 SHA-256 校验文件。若直接下载 Actions artifact，请先解压 GitHub 外层 ZIP，再选择其中的插件 ZIP。

插件显示名为 **dev-reader**，新 plugin ID 为 `io.github.ataraxyadong.devreader`。这是新插件安装，旧插件的登录状态、阅读设置和快捷键绑定不会自动继承。旧插件由用户自行停用或卸载；本项目不会删除旧配置。Java 包名和原作者署名保留以记录来源。

## 使用

- 侧栏默认显示“阅读”，可在 **Settings > Tools > 阅读设置** 修改名称。
- 题库和题目单击选中，双击或按 Enter 打开。
- 单行工具栏提供返回列表、上一题、下一题、刷新、A−、A+ 和更多操作；不可用的操作显示为禁用。
- 更多菜单保留登录、退出、字号设置、内容入口和停靠位置。快捷键可在 Keymap 中为“显示／隐藏阅读窗口”重新绑定。
- 图片使用独立非模态窗口，按所在屏幕居中；可缩放和拖动，阅读窗口仍可操作。

## 构建与验证

使用 JDK 17 与项目自带 Gradle wrapper：

```sh
./gradlew --no-daemon check verifyPlugin buildPlugin
./gradlew --no-daemon runPluginVerifier
python3 .github/scripts/verify-plugin-package.py
```

Actions 在 Linux 执行 Java 测试、IDEA Community 2025.2.6.3 兼容性检查、Chromium 图片交互回归和 ZIP 身份校验。产物位于 `build/distributions/dev-reader-版本号.zip`。

## 限制

插件声明支持 IDEA 平台 213–252，需要支持 JCEF 的 JetBrains Runtime。2026.1（261）不在当前兼容范围。真实登录、会员题目与网络响应依赖第三方服务；未完成账户验收的版本以 pre-release 发布。预览测试样本、用户凭据与截图不包含在 Release 安装包内。

## 来源与许可证

源自 [yuyuanweb/mianshiya-plugin](https://github.com/yuyuanweb/mianshiya-plugin)，保留原作者 yuyuanweb、pine 等署名以及 [Apache-2.0 LICENSE](LICENSE)。来源说明见 [NOTICE](NOTICE)。服务地址仍为 [mianshiya.com](https://www.mianshiya.com)。反馈请提交到 [本仓库 Issues](https://github.com/AtaraxyAdong/dev-reader/issues)。
