# 12321 一键举报

用于快速举报垃圾短信与骚扰电话的 Android 原生应用（Kotlin）。

## 功能

- 读取本机短信与通话记录，按关键词与号码特征识别可疑信息，并按风险等级排序
- 打开 12321 官方举报页并**自动尝试填写**发送方号码、短信内容与接收时间
- 自动填写失败时，可用侧边悬浮助手或通知栏一键复制各项内容手动粘贴
- 两种填表通道可选：内置网页自动填写（默认，开箱即用）/ 无障碍服务自动填写

详细说明见 [应用介绍.md](应用介绍.md)。

## 权限用途

| 权限 | 用途 |
|---|---|
| `READ_SMS` | 读取短信内容，用于识别垃圾短信 |
| `READ_CALL_LOG` | 读取通话记录，用于识别骚扰电话 |
| `POST_NOTIFICATIONS` | 显示"复制助手"通知，方便在填写时快速复制 |
| `SYSTEM_ALERT_WINDOW` | 显示侧边悬浮助手 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 打开 12321 举报网页 |
| 无障碍服务（需手动开启） | 可选通道，用于自动填写表单 |

## 隐私

**所有短信与通话数据均仅在设备本地处理，不上传任何服务器。** 应用不含任何统计、埋点或第三方 SDK。

## 下载

从仓库右侧 **Releases** 页面下载最新 APK 安装即可。
（APK 未提交进仓库，`.gitignore` 忽略了 `*.apk`。）

## 编译

要求：

- JDK **17** 或以上
- Android SDK（compileSdk 35）

```bash
./gradlew assembleDebug      # 调试版
./gradlew assembleRelease    # 发布版
```

Windows 用 `gradlew.bat` 替代 `./gradlew`。

> **注意**：项目路径包含非 ASCII 字符（如中文）时，AGP 会拒绝构建并提示
> `Your project path contains non-ASCII characters`。如遇此问题，请把项目放到纯英文路径下，
> 或在 `gradle.properties` 中加一行 `android.overridePathCheck=true`。

### 签名（可选）

只有需要产出**带签名的发布包**时才需要配置；没配置也能正常构建，只是产出未签名包：

```bash
cp keystore.properties.example keystore.properties   # 复制后填入自己的信息
./gradlew assembleRelease
```

`keystore.properties` 与 `*.jks` 都已在 `.gitignore` 中忽略，**请勿提交私钥**。

### 指定 JDK（可选）

若本机默认 `java` 版本低于 17，在用户级配置里指定即可，**不要写进项目**：

- Windows：`C:\Users\<用户名>\.gradle\gradle.properties`
- macOS/Linux：`~/.gradle/gradle.properties`

```
org.gradle.java.home=<你的 JDK 路径>
```

## 免责声明

本项目是 12321 举报流程的**第三方辅助工具**，与 12321 官方无任何隶属关系。
举报内容与最终提交均由用户自行确认，因使用本工具产生的任何后果由使用者承担。

## 许可证

[MIT](LICENSE)
