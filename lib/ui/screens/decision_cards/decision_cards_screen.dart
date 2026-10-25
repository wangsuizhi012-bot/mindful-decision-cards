/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'dart:convert';

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/ui/common/content_section_header.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/scaffold_shell.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';
import 'package:mindful/ui/common/styled_text.dart';

/// NOTE: Personal fork feature. Strings are intentionally hardcoded in Chinese
/// instead of going through the app's l10n pipeline.

/// Prompt template copied to the clipboard so any AI assistant can produce
/// (or edit) decision cards in exactly the expected json format.
const String _aiPromptHeader = '''你是一个安卓应用「决策卡」的编辑助手。这个功能的行为是：用户打开被守护的 App 时，先弹出一张卡片，让用户选一条路线并逐步播放攻略，播完才进入应用，帮助用户带着意图使用 App 而不是被信息流带走。

请按下面的格式生成或修改决策卡 JSON：
- 顶层是 JSON 数组，每个元素代表一张卡：
{
  "appPackage": "应用包名",
  "enabled": true,
  "title": "卡片副标题",
  "question": "选路线前问用户的问题",
  "routes": [
    {
      "label": "路线名",
      "sub": "这条路线的代价或收益，一句话",
      "steps": ["在目标 App 里能立刻执行的具体动作", "……"],
      "doneMsg": "播完的结束语",
      "doneSub": "结束语下面的一行小字"
    }
  ]
}
- 每张卡 1~3 条路线，每条 2~5 步；步骤必须是在目标 App 里能立刻执行的具体动作，不要写空话。
- 只输出 JSON，不要任何解释文字。

常用应用包名：抖音 com.ss.android.ugc.aweme；美团外卖 com.sankuai.meituan.takeoutnew；美团 com.sankuai.meituan；饿了么 me.ele；淘宝 com.taobao.taobao；小红书 com.xingin.xhs；哔哩哔哩 tv.danmaku.bili；微博 com.sina.weibo。其他应用可用 adb shell pm list packages 查询。

我当前的卡片 JSON（在此基础上修改，没有则新建）：
''';

class DecisionCardsScreen extends StatefulWidget {
  const DecisionCardsScreen({super.key});

  @override
  State<DecisionCardsScreen> createState() => _DecisionCardsScreenState();
}

class _DecisionCardsScreenState extends State<DecisionCardsScreen> {
  final TextEditingController _importController = TextEditingController();
  String _cardsJson = '[]';
  bool _isEnabled = true;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  @override
  void dispose() {
    _importController.dispose();
    super.dispose();
  }

  Future<void> _reload() async {
    String cardsJson = '[]';
    bool enabled = true;
    String? error;
    try {
      final service = MethodChannelService.instance;
      cardsJson = await service.getDecisionCards();
      enabled = await service.getDecisionCardsEnabled();
    } catch (e) {
      error = e.toString();
    }

    if (mounted) {
      setState(() {
        _cardsJson = cardsJson;
        _isEnabled = enabled;
        _isLoading = false;
      });
      if (error != null) context.showSnackAlert("读取失败：$error");
    }
  }

  String get _prettyCardsJson {
    try {
      final decoded = jsonDecode(_cardsJson);
      return const JsonEncoder.withIndent('  ').convert(decoded);
    } catch (_) {
      return _cardsJson;
    }
  }

  /// Returns NULL when the provided json is a usable cards array,
  /// otherwise returns a human readable error message.
  String? _validateCardsJson(String raw) {
    dynamic decoded;
    try {
      decoded = jsonDecode(raw);
    } catch (e) {
      return "JSON 解析失败：$e";
    }
    if (decoded is! List) return "顶层必须是 JSON 数组（[]）";
    if (decoded.isEmpty) return "数组为空，至少需要一张卡片";

    for (final card in decoded) {
      if (card is! Map) return "每个元素必须是对象（{}）";
      final pkg = card["appPackage"];
      if (pkg is! String || pkg.trim().isEmpty) {
        return "有卡片缺少 appPackage（应用包名）";
      }
      final routes = card["routes"];
      if (routes is! List || routes.isEmpty) return "$pkg：缺少 routes（路线）";

      for (final route in routes) {
        if (route is! Map) return "$pkg：每条路线必须是对象（{}）";
        final label = route["label"];
        if (label is! String || label.isEmpty) return "$pkg：有路线缺少 label（名称）";
        final steps = route["steps"];
        if (steps is! List || steps.isEmpty) return "$pkg：有路线缺少 steps（步骤）";
      }
    }
    return null;
  }

  Future<void> _importCards() async {
    final raw = _importController.text.trim();
    if (raw.isEmpty) {
      context.showSnackAlert("先粘贴要导入的 JSON");
      return;
    }

    final error = _validateCardsJson(raw);
    if (error != null) {
      context.showSnackAlert(error);
      return;
    }

    try {
      await MethodChannelService.instance.updateDecisionCards(
        jsonEncode(jsonDecode(raw)),
      );
      _importController.clear();
      await _reload();
      if (mounted) {
        context.showSnackAlert(
          "导入成功",
          icon: FluentIcons.checkmark_circle_20_filled,
        );
      }
    } catch (e) {
      if (mounted) context.showSnackAlert("导入失败：$e");
    }
  }

  Future<void> _copyToClipboard(String text, String successMsg) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) {
      context.showSnackAlert(
        successMsg,
        icon: FluentIcons.checkmark_circle_20_filled,
      );
    }
  }

  Future<void> _toggleEnabled() async {
    final newValue = !_isEnabled;
    setState(() => _isEnabled = newValue);
    try {
      await MethodChannelService.instance.updateDecisionCardsEnabled(newValue);
    } catch (e) {
      if (mounted) context.showSnackAlert("切换失败：$e");
      _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScaffoldShell(
      items: [
        NavbarItem(
          icon: FluentIcons.lightbulb_filament_20_regular,
          filledIcon: FluentIcons.lightbulb_filament_20_filled,
          titleText: "决策卡",
          sliverBody: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              /// Feature switch
              ContentSectionHeader(title: "功能开关").sliver,
              DefaultListTile(
                switchValue: _isEnabled,
                leadingIcon: FluentIcons.lightbulb_filament_20_regular,
                titleText: "启用决策卡",
                subtitleText: "打开受守护的应用时先弹出决策卡",
                onPressed: _isLoading ? null : _toggleEnabled,
              ).sliver,
              24.vSliverBox,

              /// Current cards preview
              ContentSectionHeader(title: "当前卡片").sliver,
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context)
                      .colorScheme
                      .surfaceVariant
                      .withOpacity(0.4),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: SelectableText(
                  _isLoading ? "加载中……" : _prettyCardsJson,
                  style: const TextStyle(
                    fontSize: 12,
                    fontFamily: 'monospace',
                    height: 1.4,
                  ),
                ),
              ).sliver,
              12.vSliverBox,
              DefaultListTile(
                leadingIcon: FluentIcons.clipboard_task_20_regular,
                titleText: "复制当前卡片 JSON",
                subtitleText: "发给 AI 助手让它在此基础上修改",
                onPressed: () =>
                    _copyToClipboard(_prettyCardsJson, "已复制当前卡片"),
              ).sliver,
              24.vSliverBox,

              /// Import
              ContentSectionHeader(title: "导入新卡片").sliver,
              StyledText(
                "粘贴完整的卡片 JSON 数组（会整体替换现有卡片）。导入前会校验格式，缺 appPackage 或 steps 的卡不会被保存。",
              ).sliver,
              6.vSliverBox,
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: TextField(
                  controller: _importController,
                  maxLines: 8,
                  minLines: 4,
                  style: const TextStyle(
                    fontSize: 12,
                    fontFamily: 'monospace',
                    height: 1.4,
                  ),
                  decoration: const InputDecoration(
                    hintText: "把 AI 生成的 JSON 粘贴到这里",
                    border: OutlineInputBorder(),
                  ),
                ),
              ).sliver,
              DefaultListTile(
                isPrimary: true,
                leadingIcon: FluentIcons.clipboard_paste_20_regular,
                titleText: "校验并导入",
                onPressed: _importCards,
              ).sliver,
              24.vSliverBox,

              /// AI prompt
              ContentSectionHeader(title: "让 AI 助手帮你写卡").sliver,
              StyledText(
                "复制下面的指令发给手机上的任意 AI 助手（豆包、Kimi、DeepSeek 等），它会按正确格式输出卡片 JSON；把回复粘到上面的导入框即可。",
              ).sliver,
              6.vSliverBox,
              DefaultListTile(
                leadingIcon: FluentIcons.sparkle_20_regular,
                titleText: "复制给 AI 的指令模板",
                subtitleText: "已包含格式说明、包名表和你的当前卡片",
                onPressed: () => _copyToClipboard(
                  "$_aiPromptHeader\n$_prettyCardsJson\n\n我的需求：",
                  "已复制指令模板，粘贴到 AI 助手里补上你的需求即可",
                ),
              ).sliver,

              /// Padding
              const SliverTabsBottomPadding(),
            ],
          ),
        ),
      ],
    );
  }
}
