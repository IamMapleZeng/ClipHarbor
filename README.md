# ClipHarbor

适用：Android 10 及以上。本版本使用 Android 系统分享菜单，不用 Root，也不用启用 LSPosed 模块。

1. 安装 `ClipHarbor-1.1.apk`。
2. 打开 X 的公开视频帖子，点帖子的「分享」图标，再点「Share via… / 通过…分享」。
3. 在系统分享菜单里选 **ClipHarbor**（可能需要展开应用列表）。选择后自动下载。
4. 完成后可点「播放视频」，或在相册、文件管理的 `Movies/X Save Video` 下查看。
5. 下载期间可切到别的应用；按返回或点「取消」会停止尚未完成的下载。

支持 FxTwitter 能公开解析、并且 X 提供完整 MP4 的视频或 GIF。多个视频会依次保存。暂不支持受保护帖子、私信、直播、以及只有 HLS 流的帖子。

下载时会把帖子编号发给 FxTwitter 获取媒体地址，视频文件从 `video.twimg.com` 下载。不读取 X 的登录资料或 Cookie。

## 编译

源码目录含 AndroidManifest、资源、Java 源码和 Windows PowerShell 构建脚本。需要 JDK 17、Android SDK Platform 35、Android Build Tools 35.0.1 和 Python 3。使用你自己的签名密钥，新签名的 APK 无法直接覆盖不同签名的已安装版本。

```powershell
keytool -genkeypair -alias xsave -keystore xsave.p12 -keyalg RSA -keysize 3072 -validity 10000
# 将 keystore 密码设置为当前进程的 XSAVE_KEYPASS 环境变量，再运行：
./build.ps1 -Jdk <JDK目录> -PlatformJar <android.jar路径> -BuildTools <BuildTools目录> -Keystore <xsave.p12路径> -Output <APK输出路径>
```
