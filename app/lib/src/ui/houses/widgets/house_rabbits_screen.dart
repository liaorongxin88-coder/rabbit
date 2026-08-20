import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/repro_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_batch_membership.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/abortion_sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_event_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_departure_sheet.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';

class HouseRabbitsScreen extends ConsumerWidget {
  const HouseRabbitsScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);

    return AppPage(
      title: '兔只管理',
      actions: [
        IconButton(
          tooltip: '返回兔舍详情',
          onPressed: () => context.go('/houses/$houseId'),
          icon: const Icon(Icons.storefront_outlined),
        ),
        IconButton(
          tooltip: '刷新',
          onPressed: () {
            ref.invalidate(houseRabbitsProvider(houseId));
            ref.invalidate(houseCagesProvider(houseId));
          },
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) {
          final house = _findHouse(items);
          if (house == null) {
            return EmptyState(
              icon: Icons.storefront_outlined,
              title: '兔舍不存在',
              message: '返回兔舍列表后重新选择一个兔舍。',
              actionLabel: '返回列表',
              onAction: () => context.go('/houses'),
            );
          }
          return _RabbitsContent(house: house);
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
      ),
    );
  }

  RabbitHouse? _findHouse(List<RabbitHouse> houses) {
    for (final house in houses) {
      if (house.id == houseId) {
        return house;
      }
    }
    return null;
  }
}

class _RabbitsContent extends ConsumerWidget {
  const _RabbitsContent({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rabbits = ref.watch(houseRabbitsProvider(house.id));
    final cages = ref.watch(houseCagesProvider(house.id));
    final permission = ref.watch(housePermissionProvider(house.id));
    final cageItems = cages.valueOrNull;
    final rabbitItems = rabbits.valueOrNull;
    final currentPermission = permission.valueOrNull;

    if (cageItems == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: cages.hasError ? '笼位加载失败' : '正在加载完整列表...',
        onRefresh: () => ref.invalidate(houseCagesProvider(house.id)),
        body: cages.hasError
            ? _InlineError(
                message: cages.error.toString(),
                onRetry: () => ref.invalidate(houseCagesProvider(house.id)),
              )
            : const _SectionLoading(label: '加载笼位中...'),
      );
    }

    if (rabbitItems == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: rabbits.hasError ? '完整列表加载失败' : '正在加载完整列表...',
        onRefresh: () => ref.invalidate(houseRabbitsProvider(house.id)),
        body: rabbits.hasError
            ? _InlineError(
                message: rabbits.error.toString(),
                onRetry: () => ref.invalidate(houseRabbitsProvider(house.id)),
              )
            : const _SectionLoading(label: '加载全部兔只中...'),
      );
    }

    if (currentPermission == null) {
      return _RabbitListShell(
        house: house,
        statusLabel: permission.hasError ? '权限加载失败' : '正在加载权限...',
        onRefresh: () => ref.invalidate(housePermissionProvider(house.id)),
        body: permission.hasError
            ? _InlineError(
                message: permission.error.toString(),
                onRetry: () =>
                    ref.invalidate(housePermissionProvider(house.id)),
              )
            : const _SectionLoading(label: '加载权限中...'),
      );
    }

    return _LoadedRabbitList(
      house: house,
      rabbits: rabbitItems,
      cages: cageItems,
      canEdit: currentPermission.canEdit,
      onRefresh: () {
        ref.invalidate(houseRabbitsProvider(house.id));
        ref.invalidate(houseCagesProvider(house.id));
      },
    );
  }
}

class _RabbitListShell extends StatelessWidget {
  const _RabbitListShell({
    required this.house,
    required this.statusLabel,
    required this.onRefresh,
    required this.body,
  });

  final RabbitHouse house;
  final String statusLabel;
  final VoidCallback onRefresh;
  final Widget body;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        _HouseSummaryCard(house: house),
        const SizedBox(height: 12),
        _AddHint(houseId: house.id),
        const SizedBox(height: 12),
        _RabbitListHeader(
          statusLabel: statusLabel,
          onRefresh: onRefresh,
        ),
        const SizedBox(height: 8),
        SectionCard(child: body),
      ],
    );
  }
}

class _LoadedRabbitList extends StatelessWidget {
  const _LoadedRabbitList({
    required this.house,
    required this.rabbits,
    required this.cages,
    required this.canEdit,
    required this.onRefresh,
  });

  final RabbitHouse house;
  final List<Rabbit> rabbits;
  final List<Cage> cages;
  final bool canEdit;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final cageDisplayById = <int, String>{
      for (final cage in cages)
        cage.id: cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
    };
    final listItemCount = rabbits.isEmpty ? 1 : rabbits.length;

    return ListView.builder(
      key: const ValueKey('house-rabbit-list'),
      padding: AppSpacing.pagePadding,
      itemCount: 5 + listItemCount,
      itemBuilder: (context, index) {
        switch (index) {
          case 0:
            return _HouseSummaryCard(house: house);
          case 1:
            return const SizedBox(height: 12);
          case 2:
            return _AddHint(houseId: house.id);
          case 3:
            return const SizedBox(height: 12);
          case 4:
            return _RabbitListHeader(
              statusLabel: '共 ${rabbits.length} 只 · 已全部加载',
              onRefresh: onRefresh,
              canEdit: canEdit,
              onOutbound: () => context.push(
                '/houses/${house.id}/outbound?entryType=HOUSE',
              ),
            );
        }

        if (rabbits.isEmpty) {
          return Padding(
            padding: const EdgeInsets.only(top: 8),
            child: _CompactEmpty(
              icon: Icons.cruelty_free,
              title: '暂无兔只',
              message: '请先进入笼位管理，点击具体笼位录入第一只兔子。',
              actionLabel: '去笼位',
              onAction: () => context.go('/houses/${house.id}/cages'),
            ),
          );
        }

        final rabbit = rabbits[index - 5];
        return Padding(
          padding: const EdgeInsets.only(top: 8),
          child: _RabbitListTile(
            key: ValueKey('house-rabbit-${rabbit.id}'),
            houseId: house.id,
            rabbit: rabbit,
            cageDisplay: cageDisplayById[rabbit.cageId] ?? '#${rabbit.cageId}',
          ),
        );
      },
    );
  }
}

class RabbitDetailSheet extends ConsumerStatefulWidget {
  const RabbitDetailSheet({
    super.key,
    required this.houseId,
    required this.rabbit,
    required this.cageDisplay,
    required this.canEdit,
    this.onMove,
    this.onEdit,
    this.onOutbound,
    this.onChanged,
    this.onOpenBatch,
    this.onBindBatch,
    this.onRemoveBatch,
    this.pageMode = false,
  });

  final int houseId;
  final Rabbit rabbit;
  final String cageDisplay;
  final bool canEdit;
  final VoidCallback? onMove;
  final VoidCallback? onEdit;
  final VoidCallback? onOutbound;
  final VoidCallback? onChanged;
  final ValueChanged<int>? onOpenBatch;
  final VoidCallback? onBindBatch;
  final ValueChanged<RabbitBatchMembership>? onRemoveBatch;
  final bool pageMode;

  @override
  ConsumerState<RabbitDetailSheet> createState() => _RabbitDetailSheetState();
}

class _RabbitDetailSheetState extends ConsumerState<RabbitDetailSheet> {
  var _activeMemberships = true;
  ReproStage? _stageOverride;
  int? _cycleIdOverride;
  var _hasCycleIdOverride = false;

  RabbitBatchMembershipRequest get _request => RabbitBatchMembershipRequest(
        houseId: widget.houseId,
        rabbitId: widget.rabbit.id,
        active: _activeMemberships,
      );

  RabbitReproTasksRequest get _tasksRequest => RabbitReproTasksRequest(
        houseId: widget.houseId,
        rabbitId: widget.rabbit.id,
      );

  bool get _isDoe => widget.rabbit.type == '0' && widget.rabbit.gender == '0';

  int? get _currentCycleId =>
      _hasCycleIdOverride ? _cycleIdOverride : widget.rabbit.currentCycleId;

  ReproStage? get _currentStage =>
      _stageOverride ?? ReproStage.tryParse(widget.rabbit.currentStage);

  bool get _canJoinBatch {
    final rabbit = widget.rabbit;
    final breedingFemale =
        rabbit.gender == '0' && (rabbit.type == '0' || rabbit.type == '1');
    return widget.canEdit &&
        widget.onBindBatch != null &&
        rabbit.isActive &&
        (breedingFemale || rabbit.type == '2');
  }

  @override
  Widget build(BuildContext context) {
    final memberships = ref.watch(rabbitBatchMembershipsProvider(_request));
    final tasks =
        _isDoe ? ref.watch(rabbitReproTasksProvider(_tasksRequest)) : null;
    final stageActions = _isDoe && _currentCycleId != null
        ? ref.watch(reproStageActionsProvider(widget.houseId)).valueOrNull
        : null;
    final mediaQuery = MediaQuery.of(context);

    final content = Column(
      children: [
        _buildHeader(context),
        Expanded(
          child: SingleChildScrollView(
            key: const ValueKey('rabbit-detail-scroll'),
            padding: const EdgeInsets.fromLTRB(20, 6, 20, 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _buildProfile(context),
                if (widget.pageMode && widget.canEdit) ...[
                  const SizedBox(height: 18),
                  _buildPageActions(context),
                ],
                if (tasks != null) ...[
                  const SizedBox(height: 18),
                  _buildReproductionFlow(context, tasks, stageActions),
                ],
                const SizedBox(height: 18),
                _buildMemberships(context, memberships),
                if (widget.rabbit.type == '0' &&
                    widget.rabbit.gender == '1') ...[
                  const SizedBox(height: 12),
                  const _RabbitDetailNotice(
                    icon: Icons.info_outline,
                    text: '种公兔在配种动作中选择，不加入母兔生产批次。',
                  ),
                ],
              ],
            ),
          ),
        ),
        if (widget.canEdit && !widget.pageMode) _buildActionBar(context),
      ],
    );
    if (widget.pageMode) {
      return KeyedSubtree(
        key: const ValueKey('rabbit-detail-page-content'),
        child: content,
      );
    }
    return SizedBox(
      height: mediaQuery.size.height * 0.92,
      child: content,
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '兔 #${widget.rabbit.id}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '${widget.rabbit.typeLabel} · ${widget.rabbit.genderLabel} · 笼位 ${widget.cageDisplay}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
          if (!widget.pageMode)
            IconButton(
              tooltip: '关闭',
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.close),
            ),
        ],
      ),
    );
  }

  Widget _buildProfile(BuildContext context) {
    final rabbit = widget.rabbit;
    final isDoe = rabbit.type == '0' && rabbit.gender == '0';
    final stage = isDoe
        ? _stageOverride?.label ?? _stageLabel(rabbit.currentStage)
        : null;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppPalette.of(context).surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppPalette.of(context).line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('兔只档案', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          Wrap(
            spacing: 24,
            runSpacing: 12,
            children: [
              if (widget.pageMode)
                _RabbitDetailFact(
                  label: '状态',
                  value: rabbit.isActive ? '在栏' : '已离场',
                ),
              _RabbitDetailFact(label: '类型', value: rabbit.typeLabel),
              _RabbitDetailFact(label: '性别', value: rabbit.genderLabel),
              _RabbitDetailFact(
                label: '品种',
                value: rabbit.breed.isEmpty ? '未填写' : rabbit.breed,
              ),
              _RabbitDetailFact(label: '体重', value: rabbit.weightLabel),
              _RabbitDetailFact(label: '笼位', value: widget.cageDisplay),
              if (widget.pageMode) ...[
                _RabbitDetailFact(
                  label: '来源',
                  value: _arrivalMethodLabel(rabbit.arrivalMethod),
                ),
                _RabbitDetailFact(
                  label: '入场日期',
                  value: _dateLabel(rabbit.arrivalDate, fallback: '未填写'),
                ),
                _RabbitDetailFact(
                  label: '母兔',
                  value:
                      rabbit.motherId == null ? '未关联' : '#${rabbit.motherId}',
                ),
              ],
              if (stage != null) _RabbitDetailFact(label: '生产阶段', value: stage),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildReproductionFlow(
    BuildContext context,
    AsyncValue<List<ReproTask>> tasks,
    Map<String, List<String>>? stageActions,
  ) {
    final rabbit = widget.rabbit;
    final stage = _stageOverride?.label ?? _stageLabel(rabbit.currentStage);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('繁育流程', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppPalette.of(context).surfaceSubtle,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppPalette.of(context).line),
          ),
          child: Wrap(
            spacing: 20,
            runSpacing: 8,
            children: [
              _RabbitDetailFact(label: '当前阶段', value: stage),
              _RabbitDetailFact(
                label: '活动周期',
                value: _currentCycleId == null ? '暂无' : '#$_currentCycleId',
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        tasks.when(
          loading: () => const _RabbitReproTasksLoading(),
          error: (error, _) => _InlineError(
            message: error.toString(),
            onRetry: () => ref.invalidate(
              rabbitReproTasksProvider(_tasksRequest),
            ),
          ),
          data: (items) {
            if (items.isEmpty) {
              return _CompactEmpty(
                icon: Icons.event_available_outlined,
                title: _currentCycleId == null ? '尚未开始繁育' : '暂无待办',
                message: _currentCycleId == null
                    ? '可从母兔当前真实阶段入轨，批次关系可稍后建立。'
                    : '当前周期没有待处理任务。',
              );
            }
            return Column(
              children: [
                for (var index = 0; index < items.length; index++) ...[
                  if (index > 0) const SizedBox(height: 8),
                  _RabbitReproTaskCard(
                    task: items[index],
                    canAct:
                        widget.canEdit && reproTaskIsActionable(items[index]),
                    onAction: () => _openTaskAction(items[index]),
                  ),
                ],
              ],
            );
          },
        ),
        if (widget.canEdit && _currentCycleId == null) ...[
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: ValueKey('rabbit-repro-entry-${rabbit.id}'),
            onPressed: _openReproEntry,
            icon: const Icon(Icons.playlist_add),
            label: Text(
              _currentStage == ReproStage.ready ? '开始下一轮待催情' : '开始繁育 / 生产阶段入轨',
            ),
          ),
        ],
        if (_canAbort(stageActions)) ...[
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: ValueKey('rabbit-repro-abortion-${rabbit.id}'),
            onPressed: _openAbortion,
            icon: const Icon(Icons.report_problem_outlined),
            label: const Text('记录流产'),
          ),
        ],
        if (!widget.pageMode && widget.canEdit && rabbit.isActive) ...[
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: ValueKey('rabbit-detail-departure-${rabbit.id}'),
            onPressed: _openDeparture,
            icon: const Icon(Icons.exit_to_app_outlined),
            label: const Text('登记离场'),
          ),
        ],
      ],
    );
  }

  bool _canAbort(Map<String, List<String>>? stageActions) {
    final stage = _currentStage;
    return widget.canEdit &&
        _currentCycleId != null &&
        stage != null &&
        (stageActions?[stage.wire]?.contains(ReproAction.abortion.wire) ??
            false);
  }

  Widget _buildMemberships(
    BuildContext context,
    AsyncValue<List<RabbitBatchMembership>> memberships,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('批次标签', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        SegmentedButton<bool>(
          key: const ValueKey('rabbit-membership-filter'),
          segments: const [
            ButtonSegment(
              value: true,
              icon: Icon(Icons.play_circle_outline),
              label: Text('当前'),
            ),
            ButtonSegment(
              value: false,
              icon: Icon(Icons.history),
              label: Text('历史'),
            ),
          ],
          selected: {_activeMemberships},
          showSelectedIcon: false,
          onSelectionChanged: (selection) {
            if (selection.isNotEmpty) {
              setState(() => _activeMemberships = selection.first);
            }
          },
        ),
        if (_activeMemberships && _canJoinBatch) ...[
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: OutlinedButton.icon(
              key: ValueKey('rabbit-bind-batch-${widget.rabbit.id}'),
              onPressed: widget.onBindBatch,
              icon: const Icon(Icons.add),
              label: const Text('添加批次标签'),
            ),
          ),
        ],
        const SizedBox(height: 12),
        memberships.when(
          loading: () => const _RabbitMembershipLoading(),
          error: (error, _) => _InlineError(
            message: error.toString(),
            onRetry: () => ref.invalidate(
              rabbitBatchMembershipsProvider(_request),
            ),
          ),
          data: (items) {
            if (items.isEmpty) {
              final current = _activeMemberships;
              return _CompactEmpty(
                icon: current ? Icons.account_tree_outlined : Icons.history,
                title: current ? '暂无批次标签' : '暂无历史批次标签',
                message: current ? '该兔当前没有进行中的批次标签。' : '该兔暂无已移除或已结束的批次标签。',
              );
            }
            return Column(
              children: [
                for (var index = 0; index < items.length; index++) ...[
                  if (index > 0) const SizedBox(height: 10),
                  _RabbitMembershipCard(
                    membership: items[index],
                    onOpenBatch: widget.onOpenBatch == null
                        ? null
                        : () => widget.onOpenBatch!(items[index].batchId),
                    onRemove:
                        items[index].isActive && widget.onRemoveBatch != null
                            ? () => widget.onRemoveBatch!(items[index])
                            : null,
                  ),
                ],
              ],
            );
          },
        ),
      ],
    );
  }

  List<Widget> _actionButtons() {
    return [
      if (widget.onOutbound != null)
        OutlinedButton.icon(
          key: ValueKey('rabbit-detail-outbound-${widget.rabbit.id}'),
          onPressed: widget.onOutbound,
          icon: const Icon(Icons.local_shipping_outlined),
          label: const Text('单兔出库'),
        ),
      if (widget.onMove != null)
        OutlinedButton.icon(
          key: ValueKey('rabbit-detail-move-${widget.rabbit.id}'),
          onPressed: widget.onMove,
          icon: const Icon(Icons.move_down_outlined),
          label: const Text('换笼'),
        ),
      if (widget.onEdit != null)
        OutlinedButton.icon(
          key: ValueKey('rabbit-detail-edit-${widget.rabbit.id}'),
          onPressed: widget.onEdit,
          icon: const Icon(Icons.edit_outlined),
          label: const Text('编辑'),
        ),
      if (widget.rabbit.isActive && (widget.pageMode || !_isDoe))
        OutlinedButton.icon(
          key: ValueKey('rabbit-detail-departure-${widget.rabbit.id}'),
          onPressed: _openDeparture,
          icon: const Icon(Icons.exit_to_app_outlined),
          label: const Text('登记离场'),
        ),
    ];
  }

  Widget _buildPageActions(BuildContext context) {
    final actions = _actionButtons();
    if (actions.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      key: const ValueKey('rabbit-detail-inline-actions'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('兔只操作', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        LayoutBuilder(
          builder: (context, constraints) {
            final textScale = MediaQuery.textScalerOf(context).scale(16) / 16;
            final columns = textScale > 1.35
                ? 1
                : constraints.maxWidth >= 720
                    ? 4
                    : constraints.maxWidth >= 480
                        ? 3
                        : 2;
            const spacing = 8.0;
            final width =
                (constraints.maxWidth - spacing * (columns - 1)) / columns;
            return Wrap(
              spacing: spacing,
              runSpacing: spacing,
              children: [
                for (final action in actions)
                  SizedBox(width: width, child: action),
              ],
            );
          },
        ),
      ],
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final actions = _actionButtons();
    if (actions.isEmpty) {
      return const SizedBox.shrink();
    }
    return DecoratedBox(
      key: const ValueKey('rabbit-detail-fixed-actions'),
      decoration: BoxDecoration(
        color: AppPalette.of(context).surface,
        border: Border(top: BorderSide(color: AppPalette.of(context).line)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
          child: Wrap(
            alignment: WrapAlignment.end,
            spacing: 8,
            runSpacing: 8,
            children: actions,
          ),
        ),
      ),
    );
  }

  Future<void> _openTaskAction(ReproTask task) async {
    final result = await showReproTaskActionSheet(
      context: context,
      houseId: widget.houseId,
      task: task,
    );
    if (!mounted) {
      return;
    }
    if (result != null) {
      final currentCycleId = _currentCycleId;
      setState(() {
        _stageOverride = result.stage;
        _cycleIdOverride = result.currentCycleId ??
            result.followUpCycleId ??
            (result.lifecycle?.toUpperCase() == 'CLOSED' &&
                    currentCycleId == task.cycleId
                ? null
                : currentCycleId);
        _hasCycleIdOverride = true;
      });
    }
    _refreshRabbitFlow();
  }

  Future<void> _openReproEntry() async {
    final result = await showRabbitReproEntrySheet(
      context: context,
      houseId: widget.houseId,
      rabbit: widget.rabbit,
      initialStage: _currentStage == ReproStage.ready
          ? ReproStage.awaitEstrus.wire
          : null,
    );
    if (!mounted || result == null) {
      return;
    }
    setState(() {
      _stageOverride = result.stage;
      final currentCycleId = result.currentCycleId ?? result.cycleId;
      _cycleIdOverride = currentCycleId > 0 ? currentCycleId : null;
      _hasCycleIdOverride = true;
    });
    _refreshRabbitFlow();
  }

  Future<void> _openAbortion() async {
    final cycleId = _currentCycleId;
    if (cycleId == null) {
      return;
    }
    final recorded = await showAbortionSheet(
      context: context,
      houseId: widget.houseId,
      cycleId: cycleId,
      rabbitId: widget.rabbit.id,
      rabbitLabel: '母兔 #${widget.rabbit.id}',
      stageLabel: _currentStage?.label,
    );
    if (recorded && mounted) {
      _refreshRabbitFlow();
      if (!widget.pageMode) {
        Navigator.of(context).pop();
      }
    }
  }

  Future<void> _openDeparture() async {
    final recorded = await showRabbitDepartureSheet(
      context: context,
      houseId: widget.houseId,
      rabbitId: widget.rabbit.id,
      rabbitLabel: '兔 #${widget.rabbit.id}',
    );
    if (recorded && mounted) {
      _refreshRabbitFlow();
      if (!widget.pageMode) {
        Navigator.of(context).pop();
      }
    }
  }

  void _refreshRabbitFlow() {
    ref.invalidate(
      rabbitDetailProvider(
        RabbitDetailRequest(
          houseId: widget.houseId,
          rabbitId: widget.rabbit.id,
        ),
      ),
    );
    ref.invalidate(rabbitReproTasksProvider(_tasksRequest));
    ref.invalidate(rabbitBatchMembershipsProvider(_request));
    ref.invalidate(
      rabbitBatchMembershipsProvider(
        RabbitBatchMembershipRequest(
          houseId: widget.houseId,
          rabbitId: widget.rabbit.id,
          active: false,
        ),
      ),
    );
    ref.invalidate(houseRabbitsProvider(widget.houseId));
    ref.invalidate(homeEventsProvider);
    widget.onChanged?.call();
  }
}

class _RabbitDetailFact extends StatelessWidget {
  const _RabbitDetailFact({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 132,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 2),
          Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ],
      ),
    );
  }
}

class _RabbitDetailNotice extends StatelessWidget {
  const _RabbitDetailNotice({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.primarySoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Icon(icon, color: palette.primary),
          const SizedBox(width: 10),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}

class _RabbitMembershipLoading extends StatelessWidget {
  const _RabbitMembershipLoading();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      key: ValueKey('rabbit-membership-loading'),
      height: 96,
      child: Center(child: CircularProgressIndicator()),
    );
  }
}

class _RabbitReproTasksLoading extends StatelessWidget {
  const _RabbitReproTasksLoading();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      key: ValueKey('rabbit-repro-tasks-loading'),
      height: 96,
      child: Center(child: CircularProgressIndicator()),
    );
  }
}

class _RabbitReproTaskCard extends StatelessWidget {
  const _RabbitReproTaskCard({
    required this.task,
    required this.canAct,
    required this.onAction,
  });

  final ReproTask task;
  final bool canAct;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final action = task.action;
    return Container(
      key: ValueKey('rabbit-repro-task-${task.id}'),
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  task.taskLabel,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              if (task.overdue)
                Text(
                  '已逾期',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: palette.danger,
                        fontWeight: FontWeight.w700,
                      ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            '动作：${action?.label ?? '不可直接执行'}',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 3),
          Text(
            '周期：${task.cycleId == null ? '未关联' : '#${task.cycleId}'}',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 3),
          Text(
            '提醒：${_dateLabel(task.dueTime, fallback: '日期未设置')}',
            key: ValueKey('rabbit-repro-task-date-${task.id}'),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (canAct) ...[
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                key: ValueKey('rabbit-repro-task-action-${task.id}'),
                onPressed: onAction,
                icon: const Icon(Icons.play_arrow),
                label: Text(reproTaskActionHint(task)),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _RabbitMembershipCard extends StatelessWidget {
  const _RabbitMembershipCard({
    required this.membership,
    this.onOpenBatch,
    this.onRemove,
  });

  final RabbitBatchMembership membership;
  final VoidCallback? onOpenBatch;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      key: ValueKey('rabbit-membership-${membership.batchId}'),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '批次 #${membership.batchId}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              Text(
                membership.isActive ? '进行中' : '已退出',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color:
                          membership.isActive ? palette.success : palette.muted,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '用途：${_batchRoleLabel(membership.batchRole)}',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 4),
          Text(
            _membershipDateLabel(membership),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (onOpenBatch != null || onRemove != null) ...[
            const SizedBox(height: 10),
            Wrap(
              alignment: WrapAlignment.end,
              spacing: 8,
              runSpacing: 8,
              children: [
                if (onOpenBatch != null)
                  OutlinedButton.icon(
                    key: ValueKey(
                      'rabbit-membership-open-${membership.batchId}',
                    ),
                    onPressed: onOpenBatch,
                    icon: const Icon(Icons.open_in_new),
                    label: const Text('查看批次'),
                  ),
                if (onRemove != null)
                  OutlinedButton.icon(
                    key: ValueKey(
                      'rabbit-membership-remove-${membership.batchId}',
                    ),
                    onPressed: onRemove,
                    icon: const Icon(Icons.remove_circle_outline),
                    label: const Text('移除标签'),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

String _arrivalMethodLabel(String value) {
  switch (value.trim()) {
    case '0':
      return '购入';
    case '1':
      return '出生';
    default:
      return value.trim().isEmpty ? '未填写' : value.trim();
  }
}

String _stageLabel(String? value) {
  final stage = ReproStage.tryParse(value);
  if (stage != null) {
    return stage.label;
  }
  final normalized = value?.trim();
  return normalized == null || normalized.isEmpty ? '未入轨' : normalized;
}

String _batchRoleLabel(String value) {
  switch (value.trim().toLowerCase()) {
    case 'breeding':
      return '繁育';
    case 'fattening':
      return '养育/售卖';
    case 'replacement':
      return '后备培育';
    default:
      return value.trim().isEmpty ? '批次成员' : value.trim();
  }
}

String _membershipDateLabel(RabbitBatchMembership membership) {
  if (membership.isActive) {
    return _dateLabel(membership.joinDate, fallback: '加入日期未设置');
  }
  return _dateLabel(membership.exitDate, fallback: '退出日期未设置');
}

String _dateLabel(DateTime? value, {required String fallback}) {
  return value == null ? fallback : DateFormat('yyyy-MM-dd').format(value);
}

class _HouseSummaryCard extends StatelessWidget {
  const _HouseSummaryCard({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Row(
        children: [
          IconButton(
            tooltip: '返回',
            onPressed: () => context.go('/houses/${house.id}'),
            icon: const Icon(Icons.arrow_back),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  house.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '兔只档案查看与编辑',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RabbitListHeader extends StatelessWidget {
  const _RabbitListHeader({
    required this.statusLabel,
    required this.onRefresh,
    this.canEdit = false,
    this.onOutbound,
  });

  final String statusLabel;
  final VoidCallback onRefresh;
  final bool canEdit;
  final VoidCallback? onOutbound;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: palette.successSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.cruelty_free, color: palette.success),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '兔只列表',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      statusLabel,
                      key: const ValueKey('house-rabbit-load-status'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: '刷新兔只',
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          if (canEdit && onOutbound != null) ...[
            const SizedBox(height: 12),
            Tooltip(
              message: '整舍批量出库',
              child: FilledButton.icon(
                key: const ValueKey('house-rabbits-outbound-action'),
                onPressed: onOutbound,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                ),
                icon: const Icon(Icons.local_shipping_outlined),
                label: const Text('整舍批量出库'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _AddHint extends StatelessWidget {
  const _AddHint({required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: palette.primarySoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(Icons.touch_app_outlined, color: palette.primary),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              '新增兔子需要先选择笼位，进入笼位管理后点击具体笼位录入。',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
          const SizedBox(width: 8),
          TextButton(
            onPressed: () => context.go('/houses/$houseId/cages'),
            child: const Text('去笼位'),
          ),
        ],
      ),
    );
  }
}

class _RabbitListTile extends StatelessWidget {
  const _RabbitListTile({
    super.key,
    required this.houseId,
    required this.rabbit,
    required this.cageDisplay,
  });

  final int houseId;
  final Rabbit rabbit;
  final String cageDisplay;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: palette.successSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.cruelty_free, color: palette.success),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '兔 #${rabbit.id} · ${rabbit.typeLabel} · ${rabbit.genderLabel}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '笼位 $cageDisplay · ${rabbit.breed.isEmpty ? '品种未填' : rabbit.breed} · ${rabbit.weightLabel}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              key: ValueKey('rabbit-row-detail-${rabbit.id}'),
              onPressed: () => context.push(
                '/houses/$houseId/rabbits/${rabbit.id}',
              ),
              style: TextButton.styleFrom(
                minimumSize: const Size(0, 48),
              ),
              icon: const Icon(Icons.visibility_outlined),
              label: const Text('查看详情'),
            ),
          ),
        ],
      ),
    );
  }
}

class _CompactEmpty extends StatelessWidget {
  const _CompactEmpty({
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        children: [
          Icon(icon, color: palette.muted),
          const SizedBox(height: 8),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: onAction,
              child: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}

class _SectionLoading extends StatelessWidget {
  const _SectionLoading({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 10),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _InlineError extends StatelessWidget {
  const _InlineError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.dangerSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: palette.danger),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}
