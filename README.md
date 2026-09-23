# chipTrack

Android 德州扑克计分器。

- 对局记录每一手拿取 / 退还  
- 结束时统一退还手上剩余积分并统计输赢  
- 对账：多退 / 少退差额一目了然  

## 环境要求

- JDK 17
- Android SDK（API 34）
- `ANDROID_HOME` 已配置

## 常用命令

```bash
# Debug 包
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`
