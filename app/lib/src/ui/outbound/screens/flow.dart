import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/controller.dart';

class OutboundFlowScreen extends ConsumerWidget {
  const OutboundFlowScreen({super.key, required this.entry});

  final OutboundEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permission = ref.watch(housePermissionProvider(entry.houseId));
    return permission.when(
      skipLoadingOnRefresh: false,
      skipLoadingOnReload: false,
      data: (value) => value.canEdit
          ? _AuthorizedOutboundFlow(entry: entry)
          : _OutboundAccessScaffold(
              entry: entry,
              child: EmptyState(
                key: const ValueKey('outbound-read-only-state'),
                icon: Icons.lock_outline,
                title: '当前账号仅可查看',
                message: '批量出库会修改兔只和销售数据，请联系兔舍管理员授予编辑权限。',
                actionLabel: '返回兔舍',
                onAction: () => _leaveOutbound(context, entry),
              ),
            ),
      loading: () => _OutboundAccessScaffold(
        entry: entry,
        child: const Center(
          key: ValueKey('outbound-permission-loading'),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 14),
              Text('正在校验出库权限...'),
            ],
          ),
        ),
      ),
      error: (error, _) => _OutboundAccessScaffold(
        entry: entry,
        child: ErrorState(
          key: const ValueKey('outbound-permission-error'),
          message: '无法确认出库权限：$error',
          onRetry: () => ref.invalidate(housePermissionProvider(entry.houseId)),
        ),
      ),
    );
  }
}

class _AuthorizedOutboundFlow extends ConsumerWidget {
  const _AuthorizedOutboundFlow({required this.entry});

  final OutboundEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(outboundControllerProvider(entry));
    final controller = ref.read(outboundControllerProvider(entry).notifier);
    final bottomBar = _bottomBar(context, state, controller);
    final result = state.result;
    final isResult =
        state.submitStatus == OutboundSubmitStatus.success && result != null;
    final isConfirm =
        state.isConfirming || state.submitStatus != OutboundSubmitStatus.idle;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBack(context, state, controller);
      },
      child: Scaffold(
        appBar: AppBar(
          leadingWidth: appBackLeadingWidth(context),
          leading: AppBackButton(
            tooltip: isConfirm && !isResult ? '返回选择' : '返回',
            onPressed: () => _handleBack(context, state, controller),
          ),
          title: Text(isResult
              ? '出库结果'
              : isConfirm
                  ? '确认出库'
                  : '批量出库'),
          actions: [
            if (!isResult &&
                state.task != null &&
                state.submitStatus != OutboundSubmitStatus.requesting &&
                state.submitStatus != OutboundSubmitStatus.unknown)
              IconButton(
                tooltip: '放弃草稿',
                onPressed: () => _cancel(context, controller),
                icon: const Icon(Icons.delete_outline),
              ),
            if (!isResult && state.task != null)
              IconButton(
                tooltip: '重新预检',
                onPressed: state.submitStatus == OutboundSubmitStatus.requesting
                    ? null
                    : controller.refresh,
                icon: const Icon(Icons.refresh),
              ),
          ],
        ),
        body: SafeArea(
          top: false,
          child: _body(context, ref, state, controller),
        ),
        bottomNavigationBar: bottomBar == null
            ? null
            : AnimatedPadding(
                key: const ValueKey('outbound-keyboard-aware-bottom-bar'),
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOut,
                padding: EdgeInsets.only(
                  bottom: MediaQuery.viewInsetsOf(context).bottom,
                ),
                child: bottomBar,
              ),
      ),
    );
  }

  Future<void> _handleBack(BuildContext context, OutboundState state,
      OutboundController controller) async {
    final submitting = state.submitStatus == OutboundSubmitStatus.requesting ||
        state.submitStatus == OutboundSubmitStatus.unknown;
    if (submitting) return;
    final isResult = state.submitStatus == OutboundSubmitStatus.success;
    final isConfirm =
        state.isConfirming || state.submitStatus != OutboundSubmitStatus.idle;
    if (isConfirm && !isResult) {
      await controller.backToSelection();
      return;
    }
    if (!isResult &&
        state.task != null &&
        !state.task!.resumed &&
        state.selectedCount > 0) {
      final choice = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('退出批量出库？'),
          content: const Text('可以保留当前草稿稍后继续，也可以放弃本次任务。'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('继续编辑')),
            TextButton(
                onPressed: () => Navigator.pop(context, 'discard'),
                child: const Text('放弃草稿')),
            FilledButton(
                onPressed: () => Navigator.pop(context, 'keep'),
                child: const Text('保留并退出')),
          ],
        ),
      );
      if (choice == null) return;
      if (choice == 'discard') await controller.cancel();
    }
    if (!context.mounted) return;
    _leaveOutbound(context, entry);
  }

  Widget _body(BuildContext context, WidgetRef ref, OutboundState state,
      OutboundController controller) {
    if (state.loadStatus == OutboundLoadStatus.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.loadStatus == OutboundLoadStatus.error) {
      return ErrorState(
          message: state.errorMessage ?? '出库任务加载失败',
          onRetry: controller.initialize);
    }
    if (state.submitStatus == OutboundSubmitStatus.success &&
        state.result != null) {
      return _OutboundResultView(houseId: entry.houseId, result: state.result!);
    }
    if (state.task == null) {
      return _SubmitRecoveryView(state: state, onPoll: controller.pollStatus);
    }
    if (state.task!.resumed) {
      return _ResumeDraftView(
        task: state.task!,
        onContinue: controller.continueResumedDraft,
        onDiscard: controller.discardResumedDraft,
      );
    }
    if (state.loadStatus == OutboundLoadStatus.empty) {
      return EmptyState(
        icon: Icons.inventory_2_outlined,
        title: '当前范围没有在场兔只',
        message: '可返回调整范围，或刷新后重新预检。',
        actionLabel: '重新预检',
        onAction: controller.refresh,
      );
    }
    if (state.isConfirming || state.submitStatus != OutboundSubmitStatus.idle) {
      return _ConfirmView(entry: entry, state: state);
    }
    return _SelectionView(entry: entry, state: state);
  }

  Widget? _bottomBar(BuildContext context, OutboundState state,
      OutboundController controller) {
    if (state.loadStatus != OutboundLoadStatus.ready ||
        state.task == null ||
        state.task!.resumed ||
        state.submitStatus == OutboundSubmitStatus.success) return null;
    if (state.isConfirming || state.submitStatus != OutboundSubmitStatus.idle) {
      return _ConfirmBar(state: state, controller: controller);
    }
    return _SelectionBar(
        state: state, onContinue: controller.continueToConfirm);
  }

  Future<void> _cancel(
      BuildContext context, OutboundController controller) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('放弃出库草稿？'),
        content: const Text('任务将标记为已取消，当前选择和销售信息不可继续提交。'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('继续编辑')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('放弃草稿')),
        ],
      ),
    );
    if (confirmed != true) return;
    await controller.cancel();
    if (context.mounted) context.go('/houses/${entry.houseId}');
  }
}

class _OutboundAccessScaffold extends StatelessWidget {
  const _OutboundAccessScaffold({required this.entry, required this.child});

  final OutboundEntry entry;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leadingWidth: appBackLeadingWidth(context),
        leading: AppBackButton(
          buttonKey: const ValueKey('outbound-access-back'),
          tooltip: '返回兔舍',
          onPressed: () => _leaveOutbound(context, entry),
        ),
        title: const Text('批量出库'),
      ),
      body: SafeArea(top: false, child: child),
    );
  }
}

void _leaveOutbound(BuildContext context, OutboundEntry entry) {
  if (context.canPop()) {
    context.pop();
  } else {
    context.go('/houses/${entry.houseId}');
  }
}

String _scopeLabel(OutboundTask task) {
  switch (task.entryType.toUpperCase()) {
    case 'RABBIT':
      return '兔 #${task.sourceRabbitId ?? '-'}';
    case 'CAGE':
      for (final rabbit in task.rabbits) {
        if (rabbit.cageId == task.sourceCageId) {
          return '笼位 ${rabbit.cageNumber}';
        }
      }
      return '笼位 #${task.sourceCageId ?? '-'}';
    case 'ROW':
      return '${task.sourceRowCode ?? '-'} 排';
    default:
      return '当前兔舍';
  }
}

class _ResumeDraftView extends StatelessWidget {
  const _ResumeDraftView(
      {required this.task, required this.onContinue, required this.onDiscard});

  final OutboundTask task;
  final VoidCallback onContinue;
  final Future<void> Function() onDiscard;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.restore_outlined, size: 48),
              const SizedBox(height: 16),
              Text('发现未完成的出库草稿',
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center),
              const SizedBox(height: 8),
              Text('候选范围：${_scopeLabel(task)}', textAlign: TextAlign.center),
              const SizedBox(height: 4),
              Text('已选 ${task.selectedItems.length} 只，继续后会重新预检当前状态。',
                  textAlign: TextAlign.center),
              const SizedBox(height: 24),
              SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                      onPressed: onContinue,
                      icon: const Icon(Icons.play_arrow),
                      label: const Text('继续草稿'))),
              const SizedBox(height: 8),
              SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                      onPressed: onDiscard,
                      icon: const Icon(Icons.delete_outline),
                      label: const Text('放弃并新建'))),
            ],
          ),
        ),
      ),
    );
  }
}

class _SelectionView extends ConsumerWidget {
  const _SelectionView({required this.entry, required this.state});

  final OutboundEntry entry;
  final OutboundState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.read(outboundControllerProvider(entry).notifier);
    final groups = _groupRabbits(state.visibleRabbits);
    final listEntries = <_SelectionListEntry>[];
    for (final row in groups.entries) {
      listEntries.add(_SelectionRowEntry(row.key, row.value));
      listEntries.addAll(row.value.map(_SelectionCageEntry.new));
    }
    final visibleIds =
        state.visibleRabbits.map((rabbit) => rabbit.rabbitId).toSet();
    final hiddenSelectedCount = state.selectedRabbitIds
        .where((rabbitId) => !visibleIds.contains(rabbitId))
        .length;
    final normalRabbitIds = state.rabbits
        .where((rabbit) => rabbit.isNormal)
        .map((rabbit) => rabbit.rabbitId)
        .toSet();
    final houseFullySelected = normalRabbitIds.isNotEmpty &&
        normalRabbitIds.every(state.selectedRabbitIds.contains);
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: CustomScrollView(
        key: const ValueKey('outbound-selection-scroll'),
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                if (state.bannerMessage != null ||
                    state.syncStatus == OutboundSyncStatus.offline ||
                    state.syncStatus == OutboundSyncStatus.failed)
                  _StatusBanner(
                    message: state.bannerMessage ??
                        (state.syncStatus == OutboundSyncStatus.offline
                            ? '当前离线，可继续查看和编辑本地草稿，联网后需重新预检'
                            : '草稿保存失败，请检查网络后重试'),
                    warning: true,
                  ),
                _ScopeSummary(task: state.task!),
                const SizedBox(height: 12),
                _EligibilitySummary(
                    state: state, onFilter: controller.setFilter),
                const SizedBox(height: 12),
                SegmentedButton<OutboundSelectionMode>(
                  key: const ValueKey('outbound-selection-mode'),
                  segments: const [
                    ButtonSegment(
                        value: OutboundSelectionMode.cage,
                        icon: Icon(Icons.grid_view_outlined),
                        label: Text('按笼')),
                    ButtonSegment(
                        value: OutboundSelectionMode.row,
                        icon: Icon(Icons.view_stream_outlined),
                        label: Text('按排')),
                    ButtonSegment(
                        value: OutboundSelectionMode.house,
                        icon: Icon(Icons.home_work_outlined),
                        label: Text('整舍')),
                  ],
                  selected: {state.mode},
                  onSelectionChanged: (value) =>
                      controller.setMode(value.first),
                ),
                const SizedBox(height: 12),
                _OutboundNfcCageSelection(entry: entry, state: state),
                if (state.mode == OutboundSelectionMode.house) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.tonalIcon(
                      onPressed: state.task!.summary.normal == 0
                          ? null
                          : controller.toggleHouse,
                      icon: Icon(houseFullySelected
                          ? Icons.deselect
                          : Icons.select_all),
                      label: Text(houseFullySelected
                          ? '取消整舍已选 ${state.task!.summary.normal} 只'
                          : '选择整舍可出库兔 ${state.task!.summary.normal} 只'),
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    ChoiceChip(
                        label: const Text('全部'),
                        selected: state.filter == null,
                        onSelected: (_) => controller.setFilter(null)),
                    ChoiceChip(
                        label: Text('可出库 ${state.task!.summary.normal}'),
                        selected: state.filter == OutboundEligibility.normal,
                        onSelected: (_) =>
                            controller.setFilter(OutboundEligibility.normal)),
                    ChoiceChip(
                        label: Text('提前出售 ${state.task!.summary.earlySale}'),
                        selected: state.filter == OutboundEligibility.earlySale,
                        onSelected: (_) => controller
                            .setFilter(OutboundEligibility.earlySale)),
                    ChoiceChip(
                        label: Text(
                            '需处理 ${state.task!.summary.needsAction + state.task!.summary.blocked}'),
                        selected:
                            state.filter == OutboundEligibility.needsAction ||
                                state.filter == OutboundEligibility.blocked,
                        onSelected: (_) => controller
                            .setFilter(OutboundEligibility.needsAction)),
                    ChoiceChip(
                        key: const ValueKey('outbound-filter-selected'),
                        avatar: const Icon(Icons.check, size: 18),
                        label: Text('已选 ${state.selectedCount}'),
                        selected: state.selectedOnly,
                        onSelected: controller.setSelectedOnly),
                  ],
                ),
                if (hiddenSelectedCount > 0) ...[
                  const SizedBox(height: 8),
                  Text(
                    '当前筛选隐藏了 $hiddenSelectedCount 只已选兔，底部汇总仍包含它们',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ]),
            ),
          ),
          if (listEntries.isEmpty)
            const SliverPadding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 0),
              sliver: SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 48),
                  child: Center(child: Text('当前范围没有符合筛选的兔只')),
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final item = listEntries[index];
                    if (item is _SelectionRowEntry) {
                      return Padding(
                        padding: const EdgeInsets.only(top: 12, bottom: 8),
                        child: _RowHeader(
                          entry: entry,
                          rowCode: item.rowCode,
                          rabbits:
                              item.cages.expand((cage) => cage.value).toList(),
                          selected: state.selectedRabbitIds,
                        ),
                      );
                    }
                    final cage = (item as _SelectionCageEntry).cage;
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: _CageCard(
                        entry: entry,
                        cageId: cage.key,
                        rabbits: cage.value,
                        selected: state.selectedRabbitIds,
                      ),
                    );
                  },
                  childCount: listEntries.length,
                ),
              ),
            ),
          SliverToBoxAdapter(child: SizedBox(height: largeText ? 148 : 104)),
        ],
      ),
    );
  }
}

class _OutboundNfcCageSelection extends ConsumerStatefulWidget {
  const _OutboundNfcCageSelection({
    required this.entry,
    required this.state,
  });

  final OutboundEntry entry;
  final OutboundState state;

  @override
  ConsumerState<_OutboundNfcCageSelection> createState() =>
      _OutboundNfcCageSelectionState();
}

class _OutboundNfcCageSelectionState
    extends ConsumerState<_OutboundNfcCageSelection> {
  StreamSubscription<NfcLaunchEvent>? _nfcSubscription;
  StateController<bool>? _captureFlag;
  String? _hint;
  var _listening = false;

  @override
  void dispose() {
    _nfcSubscription?.cancel();
    _captureFlag?.state = false;
    super.dispose();
  }

  Future<void> _start() async {
    if (_listening) {
      return;
    }
    try {
      // 没有 NFC 硬件时直接说清楚，否则这里会一直停在“等待贴标签”，
      // 用户看不出是设备不支持还是自己贴的位置不对。写标签路径一直有这个检查。
      final available =
          await ref.read(nfcHardwareServiceProvider).isAvailable();
      if (!mounted) {
        return;
      }
      if (!available) {
        setState(() => _hint = '设备不支持NFC或NFC未开启，请改用下方手动选择笼位');
        return;
      }
      final service = ref.read(nfcIntentServiceProvider);
      await service.initialize();
      if (!mounted) {
        return;
      }
      final flag = ref.read(nfcCaptureActiveProvider.notifier);
      flag.state = true;
      _captureFlag = flag;
      setState(() {
        _listening = true;
        _hint = '请将手机靠近要出库笼位的 NFC 标签';
      });
      _nfcSubscription = service.events.listen(_onNfcEvent);
    } catch (_) {
      if (mounted) {
        setState(() => _hint = '无法使用 NFC，请检查系统授权后重试');
      }
    }
  }

  void _stop({String? hint}) {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _captureFlag?.state = false;
    _captureFlag = null;
    if (!mounted) {
      return;
    }
    setState(() {
      _listening = false;
      _hint = hint;
    });
  }

  Future<void> _onNfcEvent(NfcLaunchEvent event) async {
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      if (target.houseId != widget.entry.houseId) {
        _stop(hint: '该标签属于其它兔舍，未加入出库清单');
        return;
      }
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: widget.entry.houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      if (!mounted) {
        return;
      }
      final cageRabbits = widget.state.rabbits
          .where((rabbit) => rabbit.cageId == binding.cageId)
          .toList();
      if (cageRabbits.isEmpty) {
        _stop(hint: '该笼位不在当前出库范围内，请刷新后重试');
        return;
      }
      final normalCount = cageRabbits.where((rabbit) => rabbit.isNormal).length;
      if (normalCount == 0) {
        _stop(hint: '${cageRabbits.first.cageNumber} 没有可批量选择的兔只');
        return;
      }
      ref
          .read(outboundControllerProvider(widget.entry).notifier)
          .selectCage(binding.cageId);
      _stop(
        hint: '已加入 ${cageRabbits.first.cageNumber} 的 $normalCount 只可出库兔',
      );
    } catch (error) {
      _stop(
        hint: error is ApiException ? error.message : '读取标签失败，请重试',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton.icon(
          key: const ValueKey('outbound-nfc-cage-capture'),
          onPressed: _listening ? () => _stop(hint: '已停止读取 NFC 标签') : _start,
          icon: Icon(_listening ? Icons.stop_circle_outlined : Icons.nfc),
          label: Text(_listening ? '停止读取 NFC 标签' : '碰标签加入笼位'),
        ),
        if (_hint != null) ...[
          const SizedBox(height: 8),
          Container(
            key: const ValueKey('outbound-nfc-cage-hint'),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: _listening ? palette.primarySoft : palette.warningSoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  _listening ? Icons.nfc : Icons.info_outline,
                  color: _listening ? palette.primary : palette.warning,
                ),
                const SizedBox(width: 8),
                Expanded(child: Text(_hint!)),
              ],
            ),
          ),
        ],
      ],
    );
  }
}

class _ScopeSummary extends StatelessWidget {
  const _ScopeSummary({required this.task});

  final OutboundTask task;

  @override
  Widget build(BuildContext context) {
    return Row(
      key: const ValueKey('outbound-scope-summary'),
      children: [
        const SizedBox.square(
          dimension: 48,
          child: Icon(Icons.filter_alt_outlined),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('候选范围', style: Theme.of(context).textTheme.bodySmall),
              Text(
                _scopeLabel(task),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ),
        ),
        Text('${task.rabbits.length} 只'),
      ],
    );
  }
}

sealed class _SelectionListEntry {
  const _SelectionListEntry();
}

class _SelectionRowEntry extends _SelectionListEntry {
  const _SelectionRowEntry(this.rowCode, this.cages);

  final String rowCode;
  final List<MapEntry<int, List<OutboundRabbit>>> cages;
}

class _SelectionCageEntry extends _SelectionListEntry {
  const _SelectionCageEntry(this.cage);

  final MapEntry<int, List<OutboundRabbit>> cage;
}

class _EligibilitySummary extends StatelessWidget {
  const _EligibilitySummary({required this.state, required this.onFilter});
  final OutboundState state;
  final ValueChanged<OutboundEligibility?> onFilter;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final summary = state.task!.summary;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
          color: palette.surface,
          border: Border.all(color: palette.line),
          borderRadius: BorderRadius.circular(8)),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth < 420
              ? (constraints.maxWidth - 16) / 2
              : (constraints.maxWidth - 24) / 3;
          return Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _SummaryMetric(
                  metricKey: const ValueKey('outbound-summary-normal'),
                  width: width,
                  label: '正常可出库',
                  value: summary.normal,
                  icon: Icons.check_circle_outline,
                  color: palette.success,
                  onTap: () => onFilter(OutboundEligibility.normal)),
              _SummaryMetric(
                  metricKey: const ValueKey('outbound-summary-early-sale'),
                  width: width,
                  label: '可提前出售',
                  value: summary.earlySale,
                  icon: Icons.schedule_outlined,
                  color: palette.warning,
                  onTap: () => onFilter(OutboundEligibility.earlySale)),
              _SummaryMetric(
                  metricKey: const ValueKey('outbound-summary-blocked'),
                  width: width,
                  label: '不可批量选择',
                  value: summary.needsAction + summary.blocked,
                  icon: Icons.block_outlined,
                  color: palette.danger,
                  onTap: () => onFilter(OutboundEligibility.needsAction)),
            ],
          );
        },
      ),
    );
  }
}

class _SummaryMetric extends StatelessWidget {
  const _SummaryMetric(
      {required this.metricKey,
      required this.width,
      required this.label,
      required this.value,
      required this.icon,
      required this.color,
      required this.onTap});
  final Key metricKey;
  final double width;
  final String label;
  final int value;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: InkWell(
        key: metricKey,
        borderRadius: BorderRadius.circular(6),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(children: [
            Icon(icon, color: color),
            const SizedBox(width: 8),
            Expanded(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                  Text('$value', style: Theme.of(context).textTheme.titleLarge),
                  Text(label,
                      maxLines: 2,
                      style: Theme.of(context).textTheme.bodyMedium)
                ]))
          ]),
        ),
      ),
    );
  }
}

class _RowHeader extends ConsumerWidget {
  const _RowHeader(
      {required this.entry,
      required this.rowCode,
      required this.rabbits,
      required this.selected});
  final OutboundEntry entry;
  final String rowCode;
  final List<OutboundRabbit> rabbits;
  final Set<int> selected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final normal = rabbits.where((rabbit) => rabbit.isNormal).toList();
    final value =
        _checkValue(normal.map((rabbit) => rabbit.rabbitId), selected);
    return Row(
      children: [
        SizedBox.square(
          dimension: 48,
          child: Checkbox(
            tristate: true,
            value: normal.isEmpty ? false : value,
            onChanged: normal.isEmpty
                ? null
                : (_) => ref
                    .read(outboundControllerProvider(entry).notifier)
                    .toggleRow(rowCode),
          ),
        ),
        Expanded(
            child: Text('$rowCode 排',
                style: Theme.of(context).textTheme.titleMedium)),
        Text(
            '已选 ${normal.where((rabbit) => selected.contains(rabbit.rabbitId)).length}/${normal.length}'),
      ],
    );
  }
}

class _CageCard extends ConsumerWidget {
  const _CageCard(
      {required this.entry,
      required this.cageId,
      required this.rabbits,
      required this.selected});
  final OutboundEntry entry;
  final int cageId;
  final List<OutboundRabbit> rabbits;
  final Set<int> selected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final normal = rabbits.where((rabbit) => rabbit.isNormal).toList();
    final selectedNormal =
        normal.where((rabbit) => selected.contains(rabbit.rabbitId)).length;
    final first = rabbits.first;
    return Card(
      key: ValueKey('outbound-cage-$cageId'),
      child: Row(
        children: [
          SizedBox.square(
            dimension: 56,
            child: Checkbox(
              tristate: true,
              value: normal.isEmpty
                  ? false
                  : _checkValue(
                      normal.map((rabbit) => rabbit.rabbitId), selected),
              onChanged: normal.isEmpty
                  ? null
                  : (_) => ref
                      .read(outboundControllerProvider(entry).notifier)
                      .toggleCage(cageId),
            ),
          ),
          Expanded(
            child: InkWell(
              onTap: () => _showRabbitDrawer(
                  context, ref, entry, first.cageNumber, rabbits),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(0, 14, 12, 14),
                child: Row(
                  children: [
                    Expanded(
                        child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                          Text(first.cageNumber,
                              style: Theme.of(context).textTheme.titleMedium),
                          const SizedBox(height: 4),
                          Text(
                              '可选 $selectedNormal/${normal.length} · 提前 ${rabbits.where((rabbit) => rabbit.canEarlySell).length} · 阻断 ${rabbits.where((rabbit) => !rabbit.isNormal && !rabbit.canEarlySell).length}',
                              style: Theme.of(context).textTheme.bodyMedium)
                        ])),
                    const Icon(Icons.chevron_right),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SelectionBar extends StatelessWidget {
  const _SelectionBar({required this.state, required this.onContinue});
  final OutboundState state;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final selectionSummary = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('已选 ${state.selectedCount} 只',
            style: Theme.of(context).textTheme.titleMedium),
        Text(
          '${state.selectedCageCount} 个笼位 · ${state.selectedRowCount} 排 · ${_syncLabel(state.syncStatus)}',
          maxLines: largeText ? 2 : 1,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ],
    );
    final continueButton = FilledButton(
      key: const ValueKey('outbound-continue-button'),
      onPressed: state.selectedCount == 0 ||
              state.syncStatus == OutboundSyncStatus.offline
          ? null
          : onContinue,
      child: Text(state.selectedCount == 0
          ? '请选择兔只'
          : '下一步 · ${state.selectedCount} 只'),
    );
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
        decoration: BoxDecoration(
            color: palette.surface,
            border: Border(top: BorderSide(color: palette.line))),
        child: Row(children: [
          Expanded(child: selectionSummary),
          const SizedBox(width: 12),
          continueButton,
        ]),
      ),
    );
  }
}

class _ConfirmView extends ConsumerStatefulWidget {
  const _ConfirmView({required this.entry, required this.state});
  final OutboundEntry entry;
  final OutboundState state;

  @override
  ConsumerState<_ConfirmView> createState() => _ConfirmViewState();
}

class _ConfirmViewState extends ConsumerState<_ConfirmView> {
  final _scrollController = ScrollController();
  OutboundSubmitStatus? _lastSubmitStatus;

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final controller =
        ref.read(outboundControllerProvider(widget.entry).notifier);
    final editable = state.submitStatus == OutboundSubmitStatus.idle ||
        state.submitStatus == OutboundSubmitStatus.failed ||
        state.submitStatus == OutboundSubmitStatus.conflict;
    final selected = state.rabbits
        .where((rabbit) => state.selectedRabbitIds.contains(rabbit.rabbitId))
        .toList();
    final groups = _groupRabbits(selected);
    final rows = groups.entries.toList();
    final revealProblem = state.submitStatus != _lastSubmitStatus &&
        (state.submitStatus == OutboundSubmitStatus.conflict ||
            state.submitStatus == OutboundSubmitStatus.failed ||
            state.submitStatus == OutboundSubmitStatus.unknown);
    _lastSubmitStatus = state.submitStatus;
    if (revealProblem) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_scrollController.hasClients) return;
        _scrollController.animateTo(
          0,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      });
    }
    return CustomScrollView(
      key: const ValueKey('outbound-confirm-scroll'),
      controller: _scrollController,
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              if (state.submitStatus == OutboundSubmitStatus.requesting ||
                  state.submitStatus == OutboundSubmitStatus.validating)
                const _StatusBanner(message: '正在确认提交结果，请勿重复操作'),
              if (state.submitStatus == OutboundSubmitStatus.unknown)
                _ProblemCard(
                    icon: Icons.cloud_off_outlined,
                    title: '提交结果尚未确认',
                    message:
                        state.errorMessage ?? '请保持当前 requestId 并查询结果，不要创建新的提交。',
                    actionLabel: '查询提交结果',
                    onAction: controller.pollStatus),
              if (state.submitStatus == OutboundSubmitStatus.conflict)
                _ConflictPanel(conflicts: state.conflicts),
              if (state.submitStatus == OutboundSubmitStatus.failed &&
                  state.errorMessage != null)
                _ProblemCard(
                    icon: Icons.error_outline,
                    title: '无法提交',
                    message: state.errorMessage!,
                    actionLabel: '继续修改',
                    onAction: controller.backToSelection),
              _ConfirmSummary(state: state),
              const SizedBox(height: 12),
              Text('销售信息', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 10),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(children: [
                    ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.calendar_today_outlined),
                        title: const Text('出库日期'),
                        subtitle: Text(DateFormat('yyyy-MM-dd')
                            .format(state.saleTime ?? DateTime.now())),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: editable
                            ? () async {
                                final date = await showDatePicker(
                                    context: context,
                                    initialDate:
                                        state.saleTime ?? DateTime.now(),
                                    firstDate: DateTime.now()
                                        .subtract(const Duration(days: 30)),
                                    lastDate: DateTime.now());
                                if (date != null) {
                                  controller.updateForm(saleTime: date);
                                }
                              }
                            : null),
                    TextFormField(
                        key: const ValueKey('outbound-total-weight'),
                        initialValue: state.totalWeight,
                        keyboardType: const TextInputType.numberWithOptions(
                            decimal: true),
                        enabled: editable,
                        decoration: const InputDecoration(
                            labelText: '总重量（kg）*',
                            prefixIcon: Icon(Icons.scale_outlined)),
                        onChanged: (value) =>
                            controller.updateForm(totalWeight: value)),
                    const SizedBox(height: 12),
                    TextFormField(
                        key: const ValueKey('outbound-unit-price'),
                        initialValue: state.unitPrice,
                        keyboardType: const TextInputType.numberWithOptions(
                            decimal: true),
                        enabled: editable,
                        decoration: const InputDecoration(
                            labelText: '单价（元/kg）',
                            prefixIcon: Icon(Icons.payments_outlined)),
                        onChanged: (value) =>
                            controller.updateForm(unitPrice: value)),
                    if (state.estimatedAmount != null)
                      Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Align(
                              alignment: Alignment.centerRight,
                              child: Text(
                                  '预计总金额 ¥${state.estimatedAmount!.toStringAsFixed(2)}'))),
                    const SizedBox(height: 12),
                    TextFormField(
                        key: const ValueKey('outbound-customer'),
                        initialValue: state.customer,
                        enabled: editable,
                        maxLength: 100,
                        decoration: const InputDecoration(
                            labelText: '客户（可选）',
                            prefixIcon: Icon(Icons.person_outline)),
                        onChanged: (value) =>
                            controller.updateForm(customer: value)),
                    TextFormField(
                        key: const ValueKey('outbound-remark'),
                        initialValue: state.remark,
                        enabled: editable,
                        maxLength: 2000,
                        maxLines: 3,
                        decoration: const InputDecoration(
                            labelText: '备注（可选）',
                            prefixIcon: Icon(Icons.notes_outlined)),
                        onChanged: (value) =>
                            controller.updateForm(remark: value)),
                  ]),
                ),
              ),
              const SizedBox(height: 16),
              Text('出库清单', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
            ]),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                final row = rows[index];
                return _ConfirmRowTile(
                  key: ValueKey('outbound-confirm-row-${row.key}'),
                  rowCode: row.key,
                  cages: row.value,
                  earlySaleReasons: state.earlySaleReasons,
                );
              },
              childCount: rows.length,
            ),
          ),
        ),
        const SliverToBoxAdapter(child: SizedBox(height: 120)),
      ],
    );
  }
}

class _ConfirmRowTile extends StatefulWidget {
  const _ConfirmRowTile({
    super.key,
    required this.rowCode,
    required this.cages,
    required this.earlySaleReasons,
  });

  final String rowCode;
  final List<MapEntry<int, List<OutboundRabbit>>> cages;
  final Map<int, String> earlySaleReasons;

  @override
  State<_ConfirmRowTile> createState() => _ConfirmRowTileState();
}

class _ConfirmRowTileState extends State<_ConfirmRowTile> {
  static const _pageSize = 20;
  var _expanded = false;
  var _visibleCages = _pageSize;

  @override
  Widget build(BuildContext context) {
    final rabbitCount =
        widget.cages.fold<int>(0, (total, cage) => total + cage.value.length);
    final visibleCount = _visibleCages.clamp(0, widget.cages.length);
    return ExpansionTile(
      tilePadding: EdgeInsets.zero,
      title: Text('${widget.rowCode} 排'),
      subtitle: Text('$rabbitCount 只 · ${widget.cages.length} 笼'),
      onExpansionChanged: (expanded) => setState(() => _expanded = expanded),
      children: _expanded
          ? [
              for (var index = 0; index < visibleCount; index++)
                _ConfirmCageTile(
                  cage: widget.cages[index],
                  earlySaleReasons: widget.earlySaleReasons,
                ),
              if (visibleCount < widget.cages.length)
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: TextButton.icon(
                    key: ValueKey('outbound-confirm-more-${widget.rowCode}'),
                    onPressed: () => setState(() {
                      _visibleCages = (_visibleCages + _pageSize)
                          .clamp(0, widget.cages.length);
                    }),
                    icon: const Icon(Icons.expand_more),
                    label: Text(
                        '再显示 ${widget.cages.length - visibleCount > _pageSize ? _pageSize : widget.cages.length - visibleCount} 笼'),
                  ),
                ),
            ]
          : const [],
    );
  }
}

class _ConfirmCageTile extends StatelessWidget {
  const _ConfirmCageTile({
    required this.cage,
    required this.earlySaleReasons,
  });

  final MapEntry<int, List<OutboundRabbit>> cage;
  final Map<int, String> earlySaleReasons;

  @override
  Widget build(BuildContext context) {
    final rabbits = cage.value;
    final earlyCount = rabbits
        .where((rabbit) => earlySaleReasons.containsKey(rabbit.rabbitId))
        .length;
    final sampleCount = rabbits.length > 4 ? 4 : rabbits.length;
    final sample = rabbits
        .take(sampleCount)
        .map((rabbit) => '#${rabbit.rabbitId}')
        .join(' · ');
    final remaining = rabbits.length - sampleCount;
    final summary = [
      '$sample${remaining > 0 ? ' · 另 $remaining 只' : ''}',
      if (earlyCount > 0) '提前出售 $earlyCount 只',
    ].join('\n');
    return ListTile(
      key: ValueKey('outbound-confirm-cage-${cage.key}'),
      title: Text(rabbits.first.cageNumber),
      subtitle: Text(summary, maxLines: 2, overflow: TextOverflow.ellipsis),
    );
  }
}

class _ConfirmSummary extends StatelessWidget {
  const _ConfirmSummary({required this.state});
  final OutboundState state;
  @override
  Widget build(BuildContext context) {
    final early = state.earlySaleReasons.length;
    return Card(
        child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              const Icon(Icons.fact_check_outlined),
              const SizedBox(width: 12),
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text('冻结清单 ${state.selectedCount} 只',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(
                        '正常 ${state.selectedCount - early} · 提前出售 $early · ${state.selectedCageCount} 笼 · ${state.selectedRowCount} 排',
                        style: Theme.of(context).textTheme.bodyMedium)
                  ]))
            ])));
  }
}

class _ConfirmBar extends StatelessWidget {
  const _ConfirmBar({required this.state, required this.controller});
  final OutboundState state;
  final OutboundController controller;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final busy = state.submitStatus == OutboundSubmitStatus.validating ||
        state.submitStatus == OutboundSubmitStatus.requesting;
    final offline = state.syncStatus == OutboundSyncStatus.offline;
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
        decoration: BoxDecoration(
            color: palette.surface,
            border: Border(top: BorderSide(color: palette.line))),
        child: state.submitStatus == OutboundSubmitStatus.conflict
            ? FilledButton.icon(
                onPressed: state.conflicts.isEmpty
                    ? controller.refresh
                    : controller.removeConflicts,
                icon: const Icon(Icons.remove_circle_outline),
                label: Text(state.conflicts.isEmpty
                    ? '重新预检'
                    : '移除冲突兔只 ${state.conflicts.length} 只'))
            : state.submitStatus == OutboundSubmitStatus.unknown
                ? FilledButton.icon(
                    onPressed: controller.pollStatus,
                    icon: const Icon(Icons.sync),
                    label: const Text('查询提交结果'))
                : FilledButton.icon(
                    key: const ValueKey('outbound-submit-button'),
                    onPressed: busy || offline || state.selectedCount == 0
                        ? null
                        : controller.submit,
                    icon: busy
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.local_shipping_outlined),
                    label: Text(offline
                        ? '离线不可提交'
                        : busy
                            ? '提交中'
                            : '确认出库 ${state.selectedCount} 只')),
      ),
    );
  }
}

class _ConflictPanel extends StatelessWidget {
  const _ConflictPanel({required this.conflicts});
  final List<OutboundConflict> conflicts;
  @override
  Widget build(BuildContext context) {
    return _ProblemCard(
      icon: Icons.warning_amber_outlined,
      title: '${conflicts.length} 只兔状态冲突',
      message: conflicts.isEmpty
          ? '请重新预检当前清单。'
          : conflicts
              .map((item) => '#${item.rabbitId} ${item.message}')
              .join('\n'),
    );
  }
}

class _SubmitRecoveryView extends StatelessWidget {
  const _SubmitRecoveryView({required this.state, required this.onPoll});
  final OutboundState state;
  final VoidCallback onPoll;
  @override
  Widget build(BuildContext context) => Center(
      child: Padding(
          padding: const EdgeInsets.all(24),
          child: _ProblemCard(
              icon: Icons.sync,
              title: '正在恢复提交',
              message: state.errorMessage ?? '正在查询原 requestId 的处理结果。',
              actionLabel: '重新查询',
              onAction: onPoll)));
}

class _OutboundResultView extends StatelessWidget {
  const _OutboundResultView({required this.houseId, required this.result});
  final int houseId;
  final OutboundSubmitResult result;
  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return ListView(padding: const EdgeInsets.all(20), children: [
      const SizedBox(height: 24),
      Icon(Icons.check_circle, color: palette.success, size: 72),
      const SizedBox(height: 14),
      Text('出库完成',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 6),
      Text(result.saleOrderNumber ?? '销售单 #${result.saleOrderId}',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyLarge),
      const SizedBox(height: 24),
      Card(
          child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(children: [
                _ResultRow(label: '兔只数量', value: '${result.rabbitCount} 只'),
                _ResultRow(
                    label: '空间范围',
                    value: '${result.rowCount} 排 · ${result.cageCount} 笼'),
                _ResultRow(
                    label: '总重量',
                    value:
                        '${result.totalWeight?.toStringAsFixed(2) ?? '-'} kg'),
                _ResultRow(
                    label: '总金额',
                    value: result.totalAmount == null
                        ? '-'
                        : '¥${result.totalAmount!.toStringAsFixed(2)}'),
                _ResultRow(
                    label: '出库日期',
                    value: result.saleTime == null
                        ? '-'
                        : DateFormat('yyyy-MM-dd').format(result.saleTime!)),
              ]))),
      const SizedBox(height: 20),
      FilledButton.icon(
          onPressed: () => context.go('/houses/$houseId/rabbits'),
          icon: const Icon(Icons.list_alt),
          label: const Text('返回兔只管理')),
      const SizedBox(height: 8),
      OutlinedButton.icon(
          onPressed: () => context.go('/'),
          icon: const Icon(Icons.home_outlined),
          label: const Text('返回首页')),
    ]);
  }
}

class _ResultRow extends StatelessWidget {
  const _ResultRow({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(children: [
        Expanded(
            child: Text(label, style: Theme.of(context).textTheme.bodyMedium)),
        Text(value, style: Theme.of(context).textTheme.titleMedium)
      ]));
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.message, this.warning = false});
  final String message;
  final bool warning;
  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
            color: warning ? palette.warningSoft : palette.primarySoft,
            borderRadius: BorderRadius.circular(8)),
        child: Row(children: [
          Icon(warning ? Icons.warning_amber : Icons.sync,
              color: warning ? palette.warning : palette.primary),
          const SizedBox(width: 10),
          Expanded(child: Text(message))
        ]));
  }
}

class _ProblemCard extends StatelessWidget {
  const _ProblemCard(
      {required this.icon,
      required this.title,
      required this.message,
      this.actionLabel,
      this.onAction});
  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;
  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
            color: palette.warningSoft,
            border: Border.all(color: palette.line),
            borderRadius: BorderRadius.circular(8)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, color: palette.warning),
          const SizedBox(width: 10),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(title, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(message),
                if (actionLabel != null && onAction != null) ...[
                  const SizedBox(height: 8),
                  TextButton(onPressed: onAction, child: Text(actionLabel!))
                ]
              ]))
        ]));
  }
}

Future<void> _showRabbitDrawer(BuildContext context, WidgetRef ref,
    OutboundEntry entry, String cageNumber, List<OutboundRabbit> rabbits) {
  return showAppModalSheet<void>(
    context: context,
    useRootNavigator: false,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: .72,
      minChildSize: .5,
      maxChildSize: .96,
      builder: (context, scrollController) => Consumer(
        builder: (context, ref, _) {
          final state = ref.watch(outboundControllerProvider(entry));
          final controller =
              ref.read(outboundControllerProvider(entry).notifier);
          final current = {
            for (final rabbit in state.rabbits) rabbit.rabbitId: rabbit
          };
          return Column(children: [
            Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
                child: Row(children: [
                  Expanded(
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                        Text(cageNumber,
                            style: Theme.of(context).textTheme.titleLarge),
                        Text('${rabbits.length} 只兔',
                            style: Theme.of(context).textTheme.bodyMedium)
                      ])),
                  IconButton(
                      tooltip: '关闭',
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.close))
                ])),
            const Divider(height: 1),
            Expanded(
              child: ListView.builder(
                controller: scrollController,
                padding: const EdgeInsets.all(12),
                itemCount: rabbits.length,
                itemBuilder: (context, index) {
                  final original = rabbits[index];
                  final rabbit = current[original.rabbitId];
                  if (rabbit == null) return const SizedBox.shrink();
                  return _RabbitTile(
                    entry: entry,
                    rabbit: rabbit,
                    selected: state.selectedRabbitIds.contains(rabbit.rabbitId),
                    earlyReason: state.earlySaleReasons[rabbit.rabbitId],
                    onToggle: () => controller.toggleRabbit(rabbit),
                    onEarly: () => _earlySale(context, controller, rabbit),
                    onBreeding: () =>
                        _markBreeding(context, ref, entry, rabbit),
                  );
                },
              ),
            ),
          ]);
        },
      ),
    ),
  );
}

class _RabbitTile extends StatelessWidget {
  const _RabbitTile(
      {required this.entry,
      required this.rabbit,
      required this.selected,
      required this.earlyReason,
      required this.onToggle,
      required this.onEarly,
      required this.onBreeding});
  final OutboundEntry entry;
  final OutboundRabbit rabbit;
  final bool selected;
  final String? earlyReason;
  final VoidCallback onToggle;
  final VoidCallback onEarly;
  final VoidCallback onBreeding;
  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
          color: palette.surfaceSubtle,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: palette.line)),
      child: Row(children: [
        if (rabbit.isNormal)
          SizedBox.square(
              dimension: 48,
              child: Checkbox(value: selected, onChanged: (_) => onToggle()))
        else
          SizedBox.square(
              dimension: 48,
              child: Icon(rabbit.canEarlySell ? Icons.schedule : Icons.block,
                  color:
                      rabbit.canEarlySell ? palette.warning : palette.danger)),
        Expanded(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('兔 #${rabbit.rabbitId} · ${rabbit.stage}',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 3),
          Text(earlyReason == null ? rabbit.message : '提前出售 · $earlyReason',
              style: Theme.of(context).textTheme.bodyMedium),
          if (rabbit.recommendedAction.isNotEmpty)
            Text(rabbit.recommendedAction,
                style: Theme.of(context).textTheme.bodySmall)
        ])),
        if (rabbit.canEarlySell && !selected)
          PopupMenuButton<String>(
              key: ValueKey('outbound-rabbit-actions-${rabbit.rabbitId}'),
              tooltip: '兔只操作',
              onSelected: (value) =>
                  value == 'early' ? onEarly() : onBreeding(),
              itemBuilder: (_) => const [
                    PopupMenuItem(value: 'early', child: Text('提前出售')),
                    PopupMenuItem(value: 'breeding', child: Text('标记留种'))
                  ])
        else if (selected && earlyReason != null)
          IconButton(
              tooltip: '移出',
              onPressed: onToggle,
              icon: const Icon(Icons.remove_circle_outline)),
      ]),
    );
  }
}

Future<void> _earlySale(BuildContext context, OutboundController controller,
    OutboundRabbit rabbit) async {
  var reasonDraft = '';
  final reason = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
              title: Text('兔 #${rabbit.rabbitId} 提前出售'),
              content: TextField(
                  key: const ValueKey('outbound-early-sale-reason'),
                  autofocus: true,
                  maxLength: 300,
                  maxLines: 3,
                  onChanged: (value) => reasonDraft = value,
                  decoration: const InputDecoration(labelText: '提前出售原因*')),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('取消')),
                FilledButton(
                    key: const ValueKey('outbound-early-sale-confirm'),
                    onPressed: () {
                      if (reasonDraft.trim().isNotEmpty) {
                        Navigator.pop(context, reasonDraft.trim());
                      }
                    },
                    child: const Text('确认纳入'))
              ]));
  if (reason != null) controller.selectEarlySale(rabbit, reason);
}

Future<void> _markBreeding(BuildContext context, WidgetRef ref,
    OutboundEntry entry, OutboundRabbit rabbit) async {
  final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
              title: Text('兔 #${rabbit.rabbitId} 标记留种？'),
              content: const Text('确认后将立即转入后备管理，并从当前出库任务移除。'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('取消')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('确认留种'))
              ]));
  if (confirmed != true) return;
  try {
    final conversions = await ref
        .read(rabbitRepositoryProvider)
        .convertToReplacement(
            houseId: entry.houseId, rabbitIds: [rabbit.rabbitId]);
    ref
        .read(outboundControllerProvider(entry).notifier)
        .removeRabbit(rabbit.rabbitId);
    await ref.read(outboundControllerProvider(entry).notifier).refresh();
    if (context.mounted && conversions.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '已留种，后备记录 #${conversions.first.replacementRecordId}',
          ),
        ),
      );
    }
  } catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }
}

Map<String, List<MapEntry<int, List<OutboundRabbit>>>> _groupRabbits(
    List<OutboundRabbit> rabbits) {
  final rows = <String, Map<int, List<OutboundRabbit>>>{};
  for (final rabbit in rabbits) {
    rows
        .putIfAbsent(rabbit.rowCode, () => {})
        .putIfAbsent(rabbit.cageId, () => [])
        .add(rabbit);
  }
  return {for (final row in rows.entries) row.key: row.value.entries.toList()};
}

bool? _checkValue(Iterable<int> ids, Set<int> selected) {
  final values = ids.toList();
  if (values.isEmpty) return false;
  final count = values.where(selected.contains).length;
  if (count == 0) return false;
  if (count == values.length) return true;
  return null;
}

String _syncLabel(OutboundSyncStatus status) {
  switch (status) {
    case OutboundSyncStatus.saving:
      return '保存中';
    case OutboundSyncStatus.failed:
      return '保存失败';
    case OutboundSyncStatus.offline:
      return '离线草稿';
    case OutboundSyncStatus.stale:
      return '待校验';
    default:
      return '已保存';
  }
}
