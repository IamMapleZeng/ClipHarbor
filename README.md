# ClipHarbor

适用：Android 10 及以上的 arm64 手机。本版本使用 Android 系统分享菜单，不用 Root，也不用启用 LSPosed 模块。

1. 安装 `ClipHarbor-1.6.1.apk`。
2. 打开 X 或 YouTube 的公开视频，点「分享」。在 X 里先点「Share via… / 通过…分享」。
3. 在系统分享菜单里选 **ClipHarbor**（可能需要展开应用列表）。选择后自动下载。
4. 完成后可点「播放视频」，也可在相册或文件管理中查看。X 视频保存到 `Movies/X Save Video`，YouTube 视频保存到 `Movies/YouTube Save Video`。
5. 下载期间可切到别的应用；按返回或点「取消」会停止尚未完成的下载。

抖音普通视频可以在首页下方粘贴分享文字或视频链接，再点「下载抖音视频」。抖音和小红书的输入框均可用「粘贴」替换上一条链接，也可点「清空」。保存目录为 `Movies/Douyin Save Video`。抖音网页必须能在当前网络下公开播放视频。

小红书公开视频可在首页下方粘贴整段分享文字或链接，再点「下载小红书视频」。保存目录为 `Movies/RedNote Save Video`。

支持 FxTwitter 能公开解析、并且 X 提供完整 MP4 的视频或 GIF。多个视频会依次保存。暂不支持受保护帖子、私信、直播、以及只有 HLS 流的帖子。

YouTube 支持普通分享链接、`youtu.be` 短链接和 Shorts。应用使用 yt-dlp 和 QuickJS 在手机上解析并下载带画面和声音的 MP4。清晰度取决于 YouTube 提供的单文件 MP4。播放列表只下载链接指向的单条视频。当前网络如果收到 YouTube 的访问验证提示，可换网络后重试。

下载时会把帖子编号发给 FxTwitter 获取媒体地址，视频文件从 `video.twimg.com` 下载。不读取 X 的登录资料或 Cookie。

## 编译

源码目录含 AndroidManifest、资源、Java 源码和 Windows PowerShell 构建脚本。需要 JDK 17、Android SDK Platform 35、Android Build Tools 35.0.1 和 Python 3。使用你自己的签名密钥，新签名的 APK 无法直接覆盖不同签名的已安装版本。

构建时从 [YTDLnis v1.9.1.1](https://github.com/deniscerri/ytdlnis/releases/tag/v1.9.1.1) 的 arm64 安装包提取 CPython 和 QuickJS，并下载并校验 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 和 [yt-dlp-ejs](https://github.com/yt-dlp/ejs) 的固定版本。原生组件的构建来源见 [YTDLnis packages](https://github.com/deniscerri/ytdlnis-packages)。

```powershell
keytool -genkeypair -alias xsave -keystore xsave.p12 -keyalg RSA -keysize 3072 -validity 10000
# 将 keystore 密码设置为当前进程的 XSAVE_KEYPASS 环境变量，再运行：
./build.ps1 -Jdk <JDK目录> -PlatformJar <android.jar路径> -BuildTools <BuildTools目录> -Keystore <xsave.p12路径> -Output <APK输出路径>
```
