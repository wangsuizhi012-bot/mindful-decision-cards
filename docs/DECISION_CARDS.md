# Decision Cards（决策卡）

> 在原版 Mindful 之上新增的实验性功能：当你打开被「守护」的应用时，
> 先弹出一张决策卡——选一条路线，按步骤播放，播完再进入应用。
> 它不拦截任何东西，只是在分心的入口处放一张「该怎么做」的纸条。

## 工作原理

1. `MindfulTrackerService.onNewAppLaunch()` 检测到前台应用切换；
2. `DecisionCardsRepository.getCardFor()` 查该应用包名是否配置了决策卡；
3. 有卡且应用未被限时拦截 → `OverlayManager.showDecisionCardOverlay()` 盖上决策卡；
4. 应用同时被限时且额度用尽 → Mindful 原生拦截浮层优先（决策卡不出现）；
5. 卡片播完后点「进入应用」或「跳过」→ 浮层消失，原应用就在下面。

## 卡片 JSON 格式

所有卡片是一个 JSON 数组，每个元素：

```json
{
  "appPackage": "com.ss.android.ugc.aweme",
  "enabled": true,
  "title": "打开抖音 · 先定个意图",
  "question": "接下来的 15 分钟，打算怎么用？",
  "routes": [
    {
      "label": "就想放松刷一刷",
      "sub": "定个时间，到点就退",
      "steps": ["第 1 步", "第 2 步", "第 3 步"],
      "doneMsg": "15 分钟，体面退场",
      "doneSub": "放松到位，注意力没失控"
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `appPackage` | 触发卡片的应用包名。查包名：`adb shell pm list packages \| grep 关键词` |
| `enabled` | `false` 时该卡不生效 |
| `title` / `question` | 卡头副标题 / 选路线前的问题 |
| `routes[]` | 路线分支，数量不限；`label` 为主文案，`sub` 为代价提示 |
| `routes[].steps[]` | 播放的步骤列表 |
| `routes[].doneMsg` / `doneSub` | 播完的结束语 |

内置默认卡见 `android/app/src/main/res/raw/decision_cards_default.json`
（美团外卖 / 美团 / 饿了么 / 抖音），首次运行自动种子到 SharedPrefs。

## 添加 / 修改卡片（推荐工作流）

入口：**设置 → 通用 → 决策卡 → 决策卡管理**。页面上有四样东西：

1. **启用开关**：一键开关整个决策卡功能（原生 `updateDecisionCardsEnabled`）；
2. **当前卡片**：美化后的 JSON 预览，可长按选择复制；
3. **导入框**：粘贴完整 JSON 数组 → 「校验并导入」，通过校验才写入（缺 `appPackage` / `steps` / `label` 的卡会被拒绝）；
4. **复制给 AI 的指令模板**：内嵌格式说明 + 常用包名表 + 当前卡片 JSON。

典型流程（不需要会编程）：

```
复制「给 AI 的指令模板」 → 粘贴给手机上的任意 AI 助手（豆包/Kimi/DeepSeek…）
→ 在末尾补一句需求，例如「给淘宝加一张卡：防止我深夜瞎买东西」
→ AI 回复 JSON → 复制 → 粘贴进导入框 → 校验并导入 → 完成
```

导入通过 MethodChannel 走原生 `updateDecisionCards`，**立即生效**，下次打开目标应用就是新卡，无需重启或重装。

### 备选方式

- **改内置默认文件**：编辑 `res/raw/decision_cards_default.json` 后重新编译；注意种子只在首次运行写入，覆盖安装不会刷新，需先清除应用数据。
- **Dart 代码调用**：`MethodChannelService.instance.updateDecisionCards(json)` / `updateDecisionCardsEnabled(bool)`。

## 编译

```bash
flutter pub get
dart run build_runner build -d
flutter build apk --debug        # 无需签名配置，applicationId 为 com.mindful.android.debug
```

产物：`build/app/outputs/flutter-apk/app-debug.apk`，安装后与正式版 Mindful 可共存
（应用名 Mindful Debug）。需要授予：悬浮窗（显示在其他应用上层）+ 无障碍或使用情况访问权限。
