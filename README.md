# 猫和老鼠计分器

Android 德州扑克计分 App（猫鼠卡通主题）。

- 对局记录每一手拿取 / 退还  
- 结束时统一退还手上剩余积分并统计输赢  
- 对账：多退 / 少退差额一目了然  
- 分享计分表图片（可直达微信）

> 界面与图标为原创猫鼠卡通风格，非华纳《猫和老鼠》正版素材。  
> UI 字体：站酷快乐体（[ZCOOL KuaiLe](https://fonts.google.com/specimen/ZCOOL+KuaiLe)，SIL Open Font License）。

## 预制玩家名单

本地文件（不进 git）：

```bash
cp app/src/main/assets/players.preset.json.example \
   app/src/main/assets/players.preset.json
```

然后按需编辑 `players.preset.json`。开局页会出现「使用预制名单」按钮。

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
