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
import 'package:mindful/config/navigation/app_routes.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/extensions/ext_num.dart';
import 'package:mindful/core/extensions/ext_widget.dart';
import 'package:mindful/core/services/method_channel_service.dart';
import 'package:mindful/ui/common/default_list_tile.dart';
import 'package:mindful/ui/common/sliver_tabs_bottom_padding.dart';
import 'package:mindful/ui/common/styled_text.dart';

/// NOTE: Personal fork feature. Simple landing screen that replaces the
/// original dashboard so the app opens straight onto decision cards.
/// Strings are intentionally hardcoded in Chinese.

class DecisionHomeScreen extends StatefulWidget {
  const DecisionHomeScreen({super.key});

  @override
  State<DecisionHomeScreen> createState() => _DecisionHomeScreenState();
}

class _DecisionHomeScreenState extends State<DecisionHomeScreen> {
  bool _isEnabled = true;
  int _cardCount = 0;
  List<String> _appNames = const [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  Future<void> _reload() async {
    String? error;
    try {
      final enabled =
          await MethodChannelService.instance.getDecisionCardsEnabled();
      final json = await MethodChannelService.instance.getDecisionCards();
      final names = <String>[];
      try {
        final cards = jsonDecode(json) as List;
        for (final card in cards) {
          final pkg = card['appPackage'];
          if (pkg is String && pkg.isNotEmpty) {
            final dot = pkg.lastIndexOf('.');
            names.add(dot > 0 ? pkg.substring(dot + 1) : pkg);
          }
        }
      } catch (_) {}

      if (mounted) {
        setState(() {
          _isEnabled = enabled;
          _cardCount = names.length;
          _appNames = names;
          _isLoading = false;
        });
      }
    } catch (e) {
      error = e.toString();
    }
    if (error != null && mounted) {
      setState(() => _isLoading = false);
      context.showSnackAlert("读取失败：$error");
    }
  }

  Future<void> _copyPrompt() async {
    final json = await MethodChannelService.instance.getDecisionCards();
    final prompt = "你是一个安卓应用「决策卡」的编辑助手。这个功能的行为是："
        "用户打开被守护的 App 时会先弹出一张卡片，选一条路线逐步播放攻略，播完才进入应用。\n\n"
        "请按下面的格式生成或修改决策卡 JSON（顶层是数组，每个元素：\n"
        '{"appPackage":"应用包名","enabled":true,"title":"卡片副标题","question":"选路线前的问题",'
        '"routes":[{"label":"路线名","sub":"一句话代价或收益","steps":["可立刻执行的具体动作"],'
        '"doneMsg":"结束语","doneSub":"结束小字"}]})\n'
        "每张卡 1~3 条路线，每条 2~5 步；步骤必须是在目标 App 里能立刻执行的具体动作。只输出 JSON。\n\n"
        "常用包名：抖音 com.ss.android.ugc.aweme；美团外卖 com.sankuai.meituan.takeoutnew；"
        "美团 com.sankuai.meituan；饿了么 me.ele；淘宝 com.taobao.taobao；小红书 com.xingin.xhs；"
        "哔哩哔哩 tv.danmaku.bili；微博 com.sina.weibo\n\n"
        "我当前的卡片：\n$json\n\n我的需求：";
    await Clipboard.setData(ClipboardData(text: prompt));
    if (mounted) {
      context.showSnackAlert(
        "指令已复制，粘给手机里的 AI，回复的 JSON 在「卡片管理」里导入",
        icon: FluentIcons.checkmark_circle_20_filled,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.background,
      appBar: AppBar(
        title: const StyledText("决策卡", fontSize: 18, fontWeight: FontWeight.w600),
        centerTitle: false,
        actions: [
          /// Back to the original Mindful dashboard
          IconButton(
            tooltip: "原版功能（专注/限额）",
            icon: const Icon(FluentIcons.apps_list_20_regular),
            onPressed: () =>
                Navigator.of(context).pushNamed(AppRoutes.homePath),
          ),
          8.hBox,
        ],
      ),
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          12.vSliverBox,

          /// Big status card
          SliverToBoxAdapter(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 12),
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: theme.colorScheme.secondaryContainer,
                borderRadius: BorderRadius.circular(24),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        _isLoading
                            ? FluentIcons.hourglass_half_20_regular
                            : _isEnabled && _cardCount > 0
                                ? FluentIcons.checkmark_circle_20_filled
                                : FluentIcons.warning_20_regular,
                        size: 34,
                        color: theme.colorScheme.primary,
                      ),
                      12.hBox,
                      Expanded(
                        child: StyledText(
                          _isLoading
                              ? "加载中……"
                              : !_isEnabled
                                  ? "决策卡已暂停"
                                  : _cardCount == 0
                                      ? "还没有卡片"
                                      : "守护 $_cardCount 个应用",
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  10.vBox,
                  StyledText(
                    _isLoading
                        ? ""
                        : !_isEnabled
                            ? "打开开关后，进入守护应用时会自动弹出引导卡"
                            : _cardCount == 0
                                ? "点下面「复制给 AI 的指令」让 AI 帮你写第一张卡"
                                : "进入 ${_appNames.take(3).join('、')}${_appNames.length > 3 ? ' 等' : ''} 时自动弹卡；卡关掉后屏幕上会留一个悬浮球，点球随时重新看卡",
                    fontSize: 13,
                    height: 1.5,
                    color: theme.colorScheme.onSecondaryContainer,
                  ),
                ],
              ),
            ),
          ),
          16.vSliverBox,

          /// How it works
          SliverToBoxAdapter(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 12),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceVariant.withOpacity(0.35),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  StyledText(
                    "怎么用",
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                  8.vBox,
                  StyledText(
                    "1. 复制下面的指令发给手机里的 AI（豆包/Kimi/DeepSeek），告诉它你想守护哪个应用、管什么行为\n"
                    "2. 把 AI 回复的 JSON 粘贴到「卡片管理」里导入\n"
                    "3. 打开被守护的应用试试：自动弹卡 → 选路线 → 逐步播放 → 进入应用\n"
                    "4. 卡片播完后屏幕边缘有个悬浮球，点它随时重看攻略",
                    fontSize: 13,
                    height: 1.7,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ],
              ),
            ),
          ),
          20.vSliverBox,

          /// Actions
          SliverToBoxAdapter(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(52),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    onPressed: _copyPrompt,
                    icon: const Icon(FluentIcons.sparkle_20_filled),
                    label: const StyledText(
                      "复制给 AI 的指令",
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                10.vBox,
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: FilledButton.tonal(
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(52),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    onPressed: () async {
                      await Navigator.of(context)
                          .pushNamed(AppRoutes.decisionCardsPath);
                      _reload();
                    },
                    child: const StyledText(
                      "卡片管理（查看 / 导入 / 开关）",
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
          16.vSliverBox,

          /// Quick links to original features
          SliverToBoxAdapter(
            child: Column(
              children: [
                DefaultListTile(
                  margin: const EdgeInsets.symmetric(horizontal: 12),
                  leadingIcon: FluentIcons.shield_task_20_regular,
                  titleText: "权限自检（不弹卡先看这里）",
                  subtitleText: "悬浮窗 / 使用情况访问 / 电池不限制",
                  trailing:
                      const Icon(FluentIcons.chevron_right_20_regular),
                  onPressed: () => Navigator.of(context)
                      .pushNamed(AppRoutes.settingsPath, arguments: {'tab': 0}),
                ),
                DefaultListTile(
                  margin: const EdgeInsets.symmetric(horizontal: 12),
                  leadingIcon: FluentIcons.apps_list_20_regular,
                  titleText: "原版功能（专注 / 限额 / 就寝）",
                  subtitleText: "Mindful 原有界面",
                  trailing:
                      const Icon(FluentIcons.chevron_right_20_regular),
                  onPressed: () =>
                      Navigator.of(context).pushNamed(AppRoutes.homePath),
                ),
              ],
            ),
          ),

          const SliverTabsBottomPadding(),
        ],
      ),
    );
  }
}
