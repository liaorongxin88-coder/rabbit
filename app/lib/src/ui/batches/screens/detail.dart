import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/batch_code.dart';
import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/abortion.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/add_members.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/tracking.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/separation.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/departure.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

/// 批量选择模式。
///
/// 批次页面只保留批量催情；配种必须从单只母兔的生产动作进入。
enum _BulkMode { estrus }

class HouseBatchDetailScreen extends ConsumerStatefulWidget {
  const HouseBatchDetailScreen({
    super.key,
    required this.houseId,
    required this.batchId,
  });

  final int houseId;
  final int batchId;

  @override
  ConsumerState<HouseBatchDetailScreen> createState() =>
      _HouseBatchDetailScreenState();
}

class _HouseBatchDetailScreenState
    extends ConsumerState<HouseBatchDetailScreen> {
  static const _all = '__ALL__';
  static const _active = '__ACTIVE__';
  static const _ended = '__ENDED__';

  final _searchController = TextEditingController();
  final _selectedRabbitIds = <int>{};
  final _batchActionRequest = BatchWriteRequestController();
  final _completeRequest = BatchWriteRequestController();
  final _renameRequest = BatchWriteRequestController();

  String _query = '';
  String _role = _all;
  String _status = _all;
  String _activity = _active;
  _BulkMode? _selectionAction;
  bool _saving = false;

  BatchDetailRequest get _request => BatchDetailRequest(
        houseId: widget.houseId,
        batchId: widget.batchId,
      );

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _refresh({bool includePermission = false}) async {
    if (_selectedRabbitIds.isNotEmpty || _selectionAction != null) {
      setState(_resetSelectionState);
    }
    ref.invalidate(batchDetailProvider(_request));
    ref.invalidate(batchMembersProvider(_request));
    ref.invalidate(pendingWeaningRecordsProvider(_request));
    ref.invalidate(houseBatchesProvider(widget.houseId));
    if (includePermission) {
      ref.invalidate(housePermissionProvider(widget.houseId));
    }
    if (!mounted) return;
    try {
      final futures = <Future<Object?>>[
        ref.read(batchDetailProvider(_request).future),
        ref.read(batchMembersProvider(_request).future),
        ref.read(pendingWeaningRecordsProvider(_request).future),
        ref.read(houseBatchesProvider(widget.houseId).future),
      ];
      if (includePermission) {
        futures.add(ref.read(housePermissionProvider(widget.houseId).future));
      }
      await Future.wait(futures);
    } catch (_) {
      // The mutation has already succeeded. The screen keeps its normal error
      // state so the user can retry the read without seeing a false write error.
    }
  }

  void _resetSelectionState() {
    _selectedRabbitIds.clear();
    _selectionAction = null;
  }

  void _clearSelection() {
    setState(_resetSelectionState);
  }

  @override
  Widget build(BuildContext context) {
    final batch = ref.watch(batchDetailProvider(_request));
    final members = ref.watch(batchMembersProvider(_request));
    final pendingWeanings = ref.watch(pendingWeaningRecordsProvider(_request));
    final permission = ref.watch(housePermissionProvider(widget.houseId));

    return AppPage(
      title: '批次详情',
      leading: IconButton(
        tooltip: '返回批次列表',
        onPressed: () => context.go('/houses/${widget.houseId}/batches'),
        icon: const Icon(Icons.arrow_back),
      ),
      actions: [
        IconButton(
          tooltip: '刷新批次',
          onPressed: _saving ? null : _refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: _buildBody(batch, members, pendingWeanings, permission),
    );
  }

  Widget _buildBody(
    AsyncValue<Batch> batch,
    AsyncValue<List<BatchRabbitItem>> members,
    AsyncValue<List<PendingWeaningRecord>> pendingWeanings,
    AsyncValue<dynamic> permission,
  ) {
    if (batch.isLoading ||
        members.isLoading ||
        pendingWeanings.isLoading ||
        permission.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    final error = batch.error ??
        members.error ??
        pendingWeanings.error ??
        permission.error;
    if (error != null) {
      return ErrorState(
        message: _errorMessage(error),
        onRetry: () => _refresh(includePermission: true),
      );
    }

    final currentBatch = batch.requireValue;
    final allMembers = members.requireValue;
    final pendingRecords = pendingProductionRecords(
      pendingWeanings.requireValue,
    );
    final currentPermission = permission.requireValue;
    final canEdit = currentPermission.canEdit == true;
    final canSeparate = currentPermission.canEditBatches == true;
    final statuses = _statuses(allMembers);
    final effectiveStatus = _effectiveStatus(statuses);
    if (effectiveStatus != _status) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _status != effectiveStatus) {
          setState(() {
            _status = effectiveStatus;
            if (_selectedRabbitIds.isNotEmpty || _selectionAction != null) {
              _resetSelectionState();
            }
          });
        }
      });
    }
    final filtered = _filterMembers(allMembers, status: effectiveStatus);
    final selected = allMembers
        .where(
          (item) =>
              _selectedRabbitIds.contains(item.rabbitId) &&
              _selectionAction == _BulkMode.estrus &&
              _isEstrusSelectable(item),
        )
        .toList();
    if (selected.length != _selectedRabbitIds.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        final validIds = selected.map((item) => item.rabbitId).toSet();
        final staleIds = _selectedRabbitIds.difference(validIds);
        if (staleIds.isEmpty) {
          return;
        }
        setState(() {
          _selectedRabbitIds.removeAll(staleIds);
          if (_selectedRabbitIds.isEmpty) {
            _selectionAction = null;
          }
        });
      });
    }

    return RefreshIndicator(
      onRefresh: () async {
        await _refresh();
      },
      child: ListView.builder(
        key: const ValueKey('batch-detail-member-list'),
        padding: AppSpacing.pagePadding,
        itemCount: 9 + (filtered.isEmpty ? 1 : filtered.length),
        itemBuilder: (context, index) {
          switch (index) {
            case 0:
              return _BatchHeader(
                batch: currentBatch,
                activeCount:
                    allMembers.where((item) => item.isActivityActive).length,
                canEdit: canEdit,
                saving: _saving,
                onComplete: () => _completeBatch(
                  currentBatch,
                  allMembers,
                ),
                onAddMembers: () => _addBatchMembers(allMembers),
                onRename: () => _renameBatch(currentBatch),
              );
            case 1:
              return const SizedBox(height: 12);
            case 2:
              return _BatchMetrics(members: allMembers);
            case 3:
              return const SizedBox(height: 12);
            case 4:
              return _PendingWeaningSection(
                records: pendingRecords,
                canEdit: canSeparate,
                saving: _saving,
                onSeparate: _separateWeaning,
              );
            case 5:
              return pendingRecords.isEmpty
                  ? const SizedBox.shrink()
                  : const SizedBox(height: 12);
            case 6:
              return _MemberFilters(
                controller: _searchController,
                query: _query,
                role: _role,
                status: effectiveStatus,
                activity: _activity,
                statuses: statuses,
                visibleCount: filtered.length,
                totalCount: allMembers.length,
                onQueryChanged: (value) => _updateFilter(() => _query = value),
                onRoleChanged: (value) => _updateFilter(() => _role = value),
                onStatusChanged: (value) =>
                    _updateFilter(() => _status = value),
                onActivityChanged: (value) =>
                    _updateFilter(() => _activity = value),
                onReset: _resetFilters,
              );
            case 7:
              return const SizedBox(height: 12);
            case 8:
              return canEdit
                  ? _BatchSelectionBar(
                      visible: filtered,
                      selected: selected,
                      selectionAction: _selectionAction,
                      saving: _saving,
                      onSelect: () => _selectVisible(filtered),
                      onClear:
                          _selectedRabbitIds.isEmpty ? null : _clearSelection,
                      onSubmit: selected.isEmpty || _selectionAction == null
                          ? null
                          : () => _submitAphrodisiac(selected),
                    )
                  : const _ReadOnlyNotice();
          }

          if (filtered.isEmpty) {
            return Padding(
              padding: const EdgeInsets.only(top: 12),
              child: SectionCard(
                child: EmptyState(
                  icon: Icons.filter_alt_off_outlined,
                  title: allMembers.isEmpty ? '批次暂无追踪标签' : '没有符合条件的标签',
                  message: allMembers.isEmpty
                      ? '添加母兔或商品兔标签后会显示在这里。'
                      : '调整兔号、种类、状态或在场筛选。',
                  actionLabel: allMembers.isEmpty ? null : '重置筛选',
                  onAction: allMembers.isEmpty ? null : _resetFilters,
                ),
              ),
            );
          }

          final item = filtered[index - 9];
          final action = _isEstrusSelectable(item) ? _BulkMode.estrus : null;
          return Padding(
            padding: const EdgeInsets.only(top: 10),
            child: _BatchMemberCard(
              key: ValueKey('batch-member-${item.rabbitId}'),
              item: item,
              canEdit: canEdit,
              selectableAction: action,
              selected: _selectedRabbitIds.contains(item.rabbitId),
              saving: _saving,
              onSelectionChanged: action == null
                  ? null
                  : (selected) => _toggleSelection(item, action, selected),
              onAction: canEdit && _memberIsActionable(item)
                  ? () => _handleMemberAction(item)
                  : null,
              onDeparture: canEdit && _memberIsDepartureActionable(item)
                  ? () => _handleMemberDeparture(item)
                  : null,
              onAbortion: canEdit && _memberCanAbort(item)
                  ? () => _handleMemberAbortion(item)
                  : null,
              onRemove: canEdit && item.isActive
                  ? () => _removeBatchMember(item)
                  : null,
              onOpenRabbit: () => context.push(
                '/houses/${widget.houseId}/rabbits/${item.rabbitId}',
              ),
              onOpenTracking: item.batchRole == 'breeding'
                  ? () => showBatchTrackingSheet(
                        context: context,
                        houseId: widget.houseId,
                        item: item,
                      )
                  : null,
            ),
          );
        },
      ),
    );
  }

  Future<void> _separateWeaning(PendingWeaningRecord record) async {
    final result = await showBatchWeaningSeparationSheet(
      context: context,
      houseId: widget.houseId,
      batchId: widget.batchId,
      record: record,
    );
    if (result != null && mounted) {
      await _refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '已分笼 ${result.separatedCount} 只，剩余 ${result.waitingCount} 只',
            ),
          ),
        );
      }
    }
  }

  String _effectiveStatus(List<String> statuses) {
    if (_status == _all || statuses.contains(_status)) {
      return _status;
    }
    return _all;
  }

  void _updateFilter(VoidCallback update) {
    setState(() {
      update();
      if (_selectedRabbitIds.isNotEmpty || _selectionAction != null) {
        _resetSelectionState();
      }
    });
  }

  List<BatchRabbitItem> _filterMembers(
    List<BatchRabbitItem> members, {
    String? status,
  }) {
    final query = _query.trim().toLowerCase();
    final selectedStatus = status ?? _status;
    return members.where((item) {
      final displayStatus = item.displayStatus;
      final nextEventType = _nextEventType(item);
      if (_role != _all && item.batchRole != _role) {
        return false;
      }
      if (selectedStatus != _all && displayStatus != selectedStatus) {
        return false;
      }
      if (_activity == _active && !item.isActivityActive) {
        return false;
      }
      if (_activity == _ended && item.isActivityActive) {
        return false;
      }
      if (query.isEmpty) {
        return true;
      }
      return item.rabbitId.toString().contains(query) ||
          (item.cageId?.toString().contains(query) ?? false) ||
          displayStatus.toLowerCase().contains(query) ||
          nextEventType.toLowerCase().contains(query) ||
          _roleLabel(item.batchRole).contains(query);
    }).toList();
  }

  List<String> _statuses(List<BatchRabbitItem> members) {
    final values = members
        .map((item) => item.displayStatus)
        .where((value) => value.isNotEmpty)
        .toSet()
        .toList();
    values.sort();
    return values;
  }

  void _resetFilters() {
    _searchController.clear();
    setState(() {
      _query = '';
      _role = _all;
      _status = _all;
      _activity = _active;
      if (_selectedRabbitIds.isNotEmpty || _selectionAction != null) {
        _resetSelectionState();
      }
    });
  }

  void _selectVisible(List<BatchRabbitItem> visible) {
    final ids =
        visible.where(_isEstrusSelectable).map((item) => item.rabbitId).toSet();
    setState(() {
      _selectionAction = ids.isEmpty ? null : _BulkMode.estrus;
      _selectedRabbitIds
        ..clear()
        ..addAll(ids);
    });
  }

  void _toggleSelection(
    BatchRabbitItem item,
    _BulkMode action,
    bool selected,
  ) {
    setState(() {
      if (_selectionAction != null && _selectionAction != action) {
        _selectedRabbitIds.clear();
      }
      _selectionAction = action;
      if (selected) {
        _selectedRabbitIds.add(item.rabbitId);
      } else {
        _selectedRabbitIds.remove(item.rabbitId);
      }
      if (_selectedRabbitIds.isEmpty) {
        _selectionAction = null;
      }
    });
  }

  Future<void> _addBatchMembers(List<BatchRabbitItem> members) async {
    if (_saving) {
      return;
    }
    final completed = await showAddBatchMembersSheet(
      context: context,
      houseId: widget.houseId,
      batchId: widget.batchId,
      currentMemberIds: members
          .where((item) => item.isActive)
          .map((item) => item.rabbitId)
          .toSet(),
    );
    if (!completed || !mounted) {
      return;
    }
    ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
    ref.invalidate(houseRabbitsProvider(widget.houseId));
    ref.invalidate(homeEventsProvider);
    await _refresh();
  }

  Future<void> _removeBatchMember(BatchRabbitItem item) async {
    if (_saving || !item.isActive) {
      return;
    }
    final confirmed = await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('移除批次标签'),
            content: Text(
              '从批次 #${widget.batchId} 移除兔 #${item.rabbitId}？'
              '此操作只解除标签关系，不会终止繁育周期或让兔离场。',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('取消'),
              ),
              FilledButton(
                key: const ValueKey('batch-member-remove-confirm'),
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: const Text('确认移除'),
              ),
            ],
          ),
        ) ==
        true;
    if (!confirmed || !mounted) {
      return;
    }

    final requestId = _batchActionRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'removeBatchMember',
        'houseId': widget.houseId,
        'batchId': widget.batchId,
        'rabbitIds': [item.rabbitId],
      }),
    );
    setState(() => _saving = true);
    try {
      await ref.read(batchRepositoryProvider).removeBatchRabbit(
            houseId: widget.houseId,
            batchId: widget.batchId,
            rabbitId: item.rabbitId,
            requestId: requestId,
          );
      _batchActionRequest.startNewDraft();
      ref.invalidate(
        rabbitBatchMembershipsProvider(
          RabbitBatchMembershipRequest(
            houseId: widget.houseId,
            rabbitId: item.rabbitId,
          ),
        ),
      );
      ref.invalidate(
        rabbitBatchMembershipsProvider(
          RabbitBatchMembershipRequest(
            houseId: widget.houseId,
            rabbitId: item.rabbitId,
            active: false,
          ),
        ),
      );
      ref.invalidate(
        rabbitDetailProvider(
          RabbitDetailRequest(
            houseId: widget.houseId,
            rabbitId: item.rabbitId,
          ),
        ),
      );
      await _refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('批次标签已移除')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_errorMessage(error))),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _submitAphrodisiac(List<BatchRabbitItem> selected) async {
    if (selected.isEmpty || _saving) {
      return;
    }
    const label = '催情';
    final confirmed = selected.length == 1 ||
        await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: Text('$label ${selected.length} 只母兔？'),
                content: const Text('提交后将统一推进所选母兔的生产状态。'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    key: const ValueKey('batch-bulk-confirm'),
                    onPressed: () => Navigator.of(context).pop(true),
                    child: const Text('确认提交'),
                  ),
                ],
              ),
            ) ==
            true;
    if (!confirmed || !mounted) {
      return;
    }

    setState(() => _saving = true);
    try {
      final ids = selected.map((item) => item.rabbitId).toList();
      final requestId = _batchActionRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'estrus',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'rabbitIds': ids,
        }),
      );
      final result =
          await ref.read(reproRepositoryProvider).bulkApplyForRabbits(
                houseId: widget.houseId,
                batchId: widget.batchId,
                taskType: 'ESTRUS',
                action: ReproAction.estrus,
                rabbitIds: ids,
                occurredAt: DateTime.now(),
                requestId: requestId,
              );
      if (!mounted) {
        return;
      }
      _clearSelection();
      _refresh();
      ref.invalidate(homeEventsProvider);
      // 部分成功是常态，不是异常：一百只里有一只被别人先推进了，
      // 不应该让另外九十九只白做，所以分开报告而不是抛错。
      final message = switch (result) {
        _ when result.total == 0 => '所选母兔当前没有待$label任务，可能已被处理',
        _ when result.failed == 0 => '$label已提交，共 ${result.succeeded} 只母兔',
        _ => '$label完成 ${result.succeeded} 只，${result.failed} 只未成功：'
            '${result.failures.first.message ?? '原因未知'}',
      };
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_errorMessage(error))),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _handleMemberAction(BatchRabbitItem item) async {
    if (_isEstrusSelectable(item)) {
      _selectionAction = _BulkMode.estrus;
      _selectedRabbitIds
        ..clear()
        ..add(item.rabbitId);
      await _submitAphrodisiac([item]);
      return;
    }

    if (item.nextEventType.contains('出售')) {
      await context.push(
        '/houses/${widget.houseId}/outbound?entryType=RABBIT&rabbitId=${item.rabbitId}',
      );
      if (mounted) {
        _refresh();
      }
      return;
    }

    final event = _eventFor(item);
    if (!eventIsActionable(event)) {
      return;
    }
    await showProductionEventSheet(context: context, event: event);
    if (mounted) {
      _refresh();
    }
  }

  Future<void> _handleMemberDeparture(BatchRabbitItem item) async {
    if (!_memberIsDepartureActionable(item) || _saving) {
      return;
    }
    await showRabbitDepartureSheet(
      context: context,
      houseId: widget.houseId,
      batchId: widget.batchId,
      rabbitId: item.rabbitId,
      rabbitLabel: '母兔 #${item.rabbitId}',
    );
    if (mounted) {
      _refresh();
      ref.invalidate(homeEventsProvider);
    }
  }

  Future<void> _completeBatch(
    Batch batch,
    List<BatchRabbitItem> members,
  ) async {
    if (_saving || batch.status.trim() == '已完成') {
      return;
    }
    final activeCount = members.where((item) => item.isActive).length;
    final choice = await _showCompleteDialog(activeCount);
    if (choice == null || !mounted) {
      return;
    }

    setState(() => _saving = true);
    try {
      final endDate = DateTime.now();
      final remark = choice.remark.trim();
      final requestId = _completeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'completeBatch',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'endDate': formatBatchWriteDate(endDate),
          'force': choice.force,
          'remark': remark,
        }),
      );
      await ref.read(batchRepositoryProvider).completeBatch(
            houseId: widget.houseId,
            batchId: widget.batchId,
            endDate: endDate,
            force: choice.force,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }
      _clearSelection();
      _refresh();
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('批次已结束')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_errorMessage(error))),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  /// 改批次编号。
  ///
  /// 已完成的批次也允许改：翻历史记录时把一个错名字改对是正当需求，
  /// 而且改名不影响任何生产数据。
  Future<void> _renameBatch(Batch batch) async {
    if (_saving) {
      return;
    }
    final code = await _showRenameDialog(batch.batchCode);
    if (code == null || !mounted || code == batch.batchCode.trim()) {
      return;
    }

    setState(() => _saving = true);
    try {
      final requestId = _renameRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'renameBatch',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'batchCode': code,
        }),
      );
      await ref.read(batchRepositoryProvider).renameBatch(
            houseId: widget.houseId,
            batchId: widget.batchId,
            batchCode: code,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }
      _renameRequest.startNewDraft();
      _refresh();
      // 批次列表和提醒页都按编号称呼批次，改完名两边都要重拉。
      ref.invalidate(houseBatchesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('批次编号已改为 $code')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_errorMessage(error))),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<String?> _showRenameDialog(String currentCode) {
    return showDialog<String>(
      context: context,
      builder: (_) => _RenameBatchDialog(initialCode: currentCode),
    );
  }

  Future<_CompleteChoice?> _showCompleteDialog(int activeCount) async {
    final remark = TextEditingController();
    var force = false;
    final result = await showDialog<_CompleteChoice>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('结束这个批次？'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  activeCount == 0
                      ? '当前没有活跃标签，可以正常结束。'
                      : '当前仍有 $activeCount 个活跃标签。强制结束会关闭开放繁殖周期并移出批次。',
                ),
                if (activeCount > 0) ...[
                  const SizedBox(height: 12),
                  CheckboxListTile(
                    key: const ValueKey('batch-complete-force'),
                    contentPadding: EdgeInsets.zero,
                    value: force,
                    onChanged: (value) =>
                        setDialogState(() => force = value == true),
                    title: const Text('确认强制结束活跃关系'),
                    controlAffinity: ListTileControlAffinity.leading,
                  ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: remark,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: '结束备注（可选）',
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('取消'),
            ),
            FilledButton(
              key: const ValueKey('batch-complete-confirm'),
              onPressed: activeCount > 0 && !force
                  ? null
                  : () => Navigator.of(context).pop(
                        _CompleteChoice(
                          force: force,
                          remark: remark.text.trim(),
                        ),
                      ),
              child: const Text('结束批次'),
            ),
          ],
        ),
      ),
    );
    remark.dispose();
    return result;
  }

  bool _memberIsActionable(BatchRabbitItem item) {
    if (!item.isActive) {
      return false;
    }
    if (_isEstrusSelectable(item)) {
      return true;
    }
    if (item.nextEventType.contains('出售')) {
      return true;
    }
    return eventIsActionable(_eventFor(item));
  }

  bool _memberIsDepartureActionable(BatchRabbitItem item) {
    return item.isActive && item.batchRole == 'breeding';
  }

  /// 能不能对这头母兔记流产。
  ///
  /// 判据来自服务端的阶段字典，而不是在这里写死「待摸胎/待备产/待分娩」：
  /// 那份规则属于转换表，拄写到客户端日后必定漂移，用户会看到一个
  /// 点下去就 409 的按钮。字典还没拉到时宁可不显示，不猜。
  bool _memberCanAbort(BatchRabbitItem item) {
    if (!item.isActive ||
        item.batchRole != 'breeding' ||
        item.currentCycleId == null) {
      return false;
    }
    final dictionary =
        ref.watch(reproStageActionsProvider(widget.houseId)).valueOrNull;
    if (dictionary == null) {
      return false;
    }
    final stage = ReproStage.tryParse(item.currentStage);
    return stage != null &&
        (dictionary[stage.wire]?.contains('ABORTION') ?? false);
  }

  Future<void> _handleMemberAbortion(BatchRabbitItem item) async {
    if (!_memberCanAbort(item) || _saving) {
      return;
    }
    final recorded = await showAbortionSheet(
      context: context,
      houseId: widget.houseId,
      cycleId: item.currentCycleId!,
      rabbitId: item.rabbitId,
      batchId: widget.batchId,
      rabbitLabel: '母兔 #${item.rabbitId}',
      stageLabel: ReproStage.tryParse(item.currentStage)?.label,
    );
    if (recorded && mounted) {
      _refresh();
      ref.invalidate(homeEventsProvider);
    }
  }

  /// 能不能对这只母兔执行催情。
  ///
  /// 判据是服务端维护的实时阶段，而不是旧的中文状态快照——
  /// 旧写路径删除后那个快照不再更新，拿它判断等于用建批时的旧值做决定。
  static bool _isEstrusSelectable(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') {
      return false;
    }
    return ReproStage.tryParse(item.currentStage) == ReproStage.awaitEstrus;
  }

  EventItem _eventFor(BatchRabbitItem item) {
    final date = item.nextEventDate;
    // 周期 id 优先取实时投影列；latestCycleId 是旧写路径的快照。
    final cycleId = item.currentCycleId ?? item.latestCycleId;
    return EventItem(
      recordId: cycleId ?? item.id,
      category: cycleId == null ? '生产' : '生产周期',
      eventType: _nextEventType(item),
      eventDate: date,
      batchId: widget.batchId,
      rabbitId: item.rabbitId,
      status: _eventStatus(date),
      sourceHouseId: widget.houseId,
    );
  }

  String _nextEventType(BatchRabbitItem item) => _nextEventTypeFor(item);

  String _eventStatus(DateTime? date) {
    if (date == null) {
      return '';
    }
    // 同样走兔场时区，否则跨 UTC 日界的待办会比提醒页早一天变成逾期。
    final today = farmToday();
    final eventDay = localDateOnly(date);
    if (eventDay.isBefore(today)) {
      return 'overdue';
    }
    if (eventDay == today) {
      return 'due';
    }
    return 'upcoming';
  }
}

/// 改批次编号的弹窗。
///
/// 单独做成 StatefulWidget 是为了让它自己持有 controller：在调用方 await 完
/// showDialog 立即 dispose，弹窗的退场动画还在跑，TextField 会碰到已释放的 controller。
class _RenameBatchDialog extends StatefulWidget {
  const _RenameBatchDialog({required this.initialCode});

  final String initialCode;

  @override
  State<_RenameBatchDialog> createState() => _RenameBatchDialogState();
}

class _RenameBatchDialogState extends State<_RenameBatchDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initialCode);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('修改批次编号'),
      content: TextField(
        key: const ValueKey('batch-rename-field'),
        controller: _controller,
        autofocus: true,
        maxLength: maxBatchCodeLength,
        decoration: const InputDecoration(labelText: '批次编号'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('取消'),
        ),
        FilledButton(
          key: const ValueKey('batch-rename-confirm'),
          onPressed: () {
            final value = _controller.text.trim();
            if (value.isEmpty) {
              return;
            }
            Navigator.of(context).pop(value);
          },
          child: const Text('保存'),
        ),
      ],
    );
  }
}

class _BatchHeader extends StatelessWidget {
  const _BatchHeader({
    required this.batch,
    required this.activeCount,
    required this.canEdit,
    required this.saving,
    required this.onComplete,
    required this.onAddMembers,
    required this.onRename,
  });

  final Batch batch;
  final int activeCount;
  final bool canEdit;
  final bool saving;
  final VoidCallback onComplete;
  final VoidCallback onAddMembers;
  final VoidCallback onRename;

  @override
  Widget build(BuildContext context) {
    final completed = batch.status.trim() == '已完成';
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 10,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: MediaQuery.sizeOf(context).width * .58,
                ),
                child: Text(
                  batch.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              // 已完成的批次也能改名，所以不跟着下面那组按钮一起被 completed 关掉。
              if (canEdit)
                IconButton(
                  key: const ValueKey('batch-rename-button'),
                  tooltip: '修改批次编号',
                  onPressed: saving ? null : onRename,
                  icon: const Icon(Icons.edit_outlined),
                ),
              _LabelChip(
                label:
                    batch.status.trim().isEmpty ? '状态未设置' : batch.status.trim(),
              ),
              Text('ID #${batch.id}'),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${batch.dateLabel} · 活跃追踪标签 $activeCount',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          if (batch.remark.trim().isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              batch.remark,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          if (canEdit && !completed) ...[
            const SizedBox(height: 14),
            OutlinedButton.icon(
              key: const ValueKey('batch-add-members-button'),
              onPressed: saving ? null : onAddMembers,
              icon: const Icon(Icons.person_add_alt_1),
              label: const Text('添加追踪标签'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              key: const ValueKey('batch-complete-button'),
              onPressed: saving ? null : onComplete,
              icon: const Icon(Icons.task_alt),
              label: const Text('结束批次'),
            ),
          ],
        ],
      ),
    );
  }
}

class _BatchMetrics extends StatelessWidget {
  const _BatchMetrics({required this.members});

  final List<BatchRabbitItem> members;

  @override
  Widget build(BuildContext context) {
    final active = members.where((item) => item.isActivityActive).length;
    final mothers = members
        .where((item) => item.isActivityActive && item.batchRole == 'breeding')
        .length;
    final commodity = members
        .where((item) => item.isActivityActive && item.batchRole == 'fattening')
        .length;
    final nursing = members.fold<int>(
      0,
      (sum, item) =>
          sum + (item.isActivityActive ? item.currentNursingKits : 0),
    );

    return SectionCard(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = (constraints.maxWidth - 8) / 2;
          return Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _MetricTile(width: width, label: '全部标签', value: members.length),
              _MetricTile(width: width, label: '活跃标签', value: active),
              _MetricTile(width: width, label: '繁殖母兔', value: mothers),
              _MetricTile(width: width, label: '商品兔', value: commodity),
              _MetricTile(width: width, label: '当前带仔', value: nursing),
              _MetricTile(
                width: width,
                label: '已退出',
                value: members.length - active,
              ),
            ],
          );
        },
      ),
    );
  }
}

class _MetricTile extends StatelessWidget {
  const _MetricTile({
    required this.width,
    required this.label,
    required this.value,
  });

  final double width;
  final String label;
  final int value;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: width,
      constraints: const BoxConstraints(minHeight: 76),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$value',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 3),
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _MemberFilters extends StatelessWidget {
  const _MemberFilters({
    required this.controller,
    required this.query,
    required this.role,
    required this.status,
    required this.activity,
    required this.statuses,
    required this.visibleCount,
    required this.totalCount,
    required this.onQueryChanged,
    required this.onRoleChanged,
    required this.onStatusChanged,
    required this.onActivityChanged,
    required this.onReset,
  });

  final TextEditingController controller;
  final String query;
  final String role;
  final String status;
  final String activity;
  final List<String> statuses;
  final int visibleCount;
  final int totalCount;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onRoleChanged;
  final ValueChanged<String> onStatusChanged;
  final ValueChanged<String> onActivityChanged;
  final VoidCallback onReset;

  @override
  Widget build(BuildContext context) {
    final hasFilters = query.trim().isNotEmpty ||
        role != _HouseBatchDetailScreenState._all ||
        status != _HouseBatchDetailScreenState._all ||
        activity != _HouseBatchDetailScreenState._active;
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            key: const ValueKey('batch-member-search'),
            controller: controller,
            onChanged: onQueryChanged,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              labelText: '搜索追踪标签',
              hintText: '兔号、笼号、状态或事件',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: query.isEmpty
                  ? null
                  : IconButton(
                      tooltip: '清除搜索',
                      onPressed: () {
                        controller.clear();
                        onQueryChanged('');
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const ValueKey('batch-member-role-filter'),
            value: role,
            isExpanded: true,
            decoration: const InputDecoration(labelText: '兔子种类'),
            items: const [
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._all,
                  child: Text('全部种类')),
              DropdownMenuItem(value: 'breeding', child: Text('繁殖母兔')),
              DropdownMenuItem(value: 'fattening', child: Text('商品兔')),
            ],
            onChanged: (value) {
              if (value != null) onRoleChanged(value);
            },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const ValueKey('batch-member-status-filter'),
            value: status,
            isExpanded: true,
            decoration: const InputDecoration(labelText: '生产状态'),
            items: [
              const DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._all,
                  child: Text('全部状态')),
              for (final value in statuses)
                DropdownMenuItem(value: value, child: Text(value)),
            ],
            onChanged: (value) {
              if (value != null) onStatusChanged(value);
            },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const ValueKey('batch-member-activity-filter'),
            value: activity,
            isExpanded: true,
            decoration: const InputDecoration(labelText: '批次活动'),
            items: const [
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._active,
                  child: Text('活动进行中')),
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._all,
                  child: Text('全部标签')),
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._ended,
                  child: Text('活动已结束')),
            ],
            onChanged: (value) {
              if (value != null) onActivityChanged(value);
            },
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Text(
                  '显示 $visibleCount / $totalCount 个标签',
                  key: const ValueKey('batch-member-filter-summary'),
                ),
              ),
              if (hasFilters)
                IconButton(
                  tooltip: '重置筛选',
                  onPressed: onReset,
                  icon: const Icon(Icons.filter_alt_off_outlined),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PendingWeaningSection extends StatelessWidget {
  const _PendingWeaningSection({
    required this.records,
    required this.canEdit,
    required this.saving,
    required this.onSeparate,
  });

  final List<PendingWeaningRecord> records;
  final bool canEdit;
  final bool saving;
  final ValueChanged<PendingWeaningRecord> onSeparate;

  @override
  Widget build(BuildContext context) {
    if (records.isEmpty) {
      return const SizedBox.shrink();
    }
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('待分笼', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          for (final record in records) ...[
            Row(
              children: [
                Expanded(
                  child: Text(
                    '母兔 #${record.rabbitId} · 待分笼 ${record.waitingCount} / ${record.weaningCount} 只',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (canEdit)
                  SizedBox(
                    width: 112,
                    child: OutlinedButton.icon(
                      key: ValueKey('pending-weaning-separate-${record.id}'),
                      onPressed: saving ? null : () => onSeparate(record),
                      icon: const Icon(Icons.call_split_outlined),
                      label: const Text('分笼'),
                    ),
                  ),
              ],
            ),
            if (record != records.last) const Divider(height: 20),
          ],
        ],
      ),
    );
  }
}

class _BatchSelectionBar extends StatelessWidget {
  const _BatchSelectionBar({
    required this.visible,
    required this.selected,
    required this.selectionAction,
    required this.saving,
    required this.onSelect,
    required this.onClear,
    required this.onSubmit,
  });

  final List<BatchRabbitItem> visible;
  final List<BatchRabbitItem> selected;
  final _BulkMode? selectionAction;
  final bool saving;
  final VoidCallback onSelect;
  final VoidCallback? onClear;
  final VoidCallback? onSubmit;

  @override
  Widget build(BuildContext context) {
    final estrusCount = visible.where(_estrusSelectable).length;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('批量操作', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            key: const ValueKey('batch-select-start-visible'),
            onPressed: saving || estrusCount == 0 ? null : onSelect,
            icon: const Icon(Icons.play_arrow),
            label: Text('选择当前待催情（$estrusCount）'),
          ),
          if (selected.isNotEmpty) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(child: Text('已选择 ${selected.length} 只母兔')),
                IconButton(
                  tooltip: '清除选择',
                  onPressed: saving ? null : onClear,
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            FilledButton.icon(
              key: const ValueKey('batch-selected-submit'),
              onPressed: saving ? null : onSubmit,
              icon: saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.play_arrow),
              label: Text('批量催情 ${selected.length} 只'),
            ),
          ],
        ],
      ),
    );
  }

  static bool _estrusSelectable(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') return false;
    return ReproStage.tryParse(item.currentStage) == ReproStage.awaitEstrus;
  }
}

class _ReadOnlyNotice extends StatelessWidget {
  const _ReadOnlyNotice();

  @override
  Widget build(BuildContext context) {
    return const SectionCard(
      child: Row(
        children: [
          Icon(Icons.visibility_outlined),
          SizedBox(width: 10),
          Expanded(child: Text('当前为只读权限，可查看但不能推进生产状态。')),
        ],
      ),
    );
  }
}

String _nextEventTypeFor(BatchRabbitItem item) {
  final currentStage = item.currentStage?.trim() ?? '';
  if (currentStage.isEmpty) {
    return item.nextEventType;
  }
  final stage = ReproStage.tryParse(currentStage);
  if (stage != null) {
    switch (stage) {
      case ReproStage.awaitEstrus:
        return '催情';
      case ReproStage.awaitMating:
        return '配种';
      case ReproStage.awaitPalpation:
        return '摸胎';
      case ReproStage.awaitPrepartum:
        return '备产';
      case ReproStage.awaitDelivery:
        return '分娩';
      case ReproStage.awaitWeaning:
        return '分笼';
      case ReproStage.ready:
      case ReproStage.suspended:
      case ReproStage.retired:
        // A recognized stage is authoritative even when it has no next task.
        return '';
    }
  }
  // A non-empty server projection is authoritative even when this client does
  // not recognize a newly introduced stage yet.
  return '';
}

class _BatchMemberCard extends StatelessWidget {
  const _BatchMemberCard({
    super.key,
    required this.item,
    required this.canEdit,
    required this.selectableAction,
    required this.selected,
    required this.saving,
    required this.onSelectionChanged,
    required this.onAction,
    required this.onDeparture,
    required this.onAbortion,
    required this.onRemove,
    required this.onOpenRabbit,
    required this.onOpenTracking,
  });

  final BatchRabbitItem item;
  final bool canEdit;
  final _BulkMode? selectableAction;
  final bool selected;
  final bool saving;
  final ValueChanged<bool>? onSelectionChanged;
  final VoidCallback? onAction;
  final VoidCallback? onDeparture;

  /// 为空即该母兔当前阶段不允许流产（服务端字典判定）。
  final VoidCallback? onAbortion;
  final VoidCallback? onRemove;
  final VoidCallback onOpenRabbit;
  final VoidCallback? onOpenTracking;

  @override
  Widget build(BuildContext context) {
    final actionLabel = _memberActionLabel(item, selectableAction);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              if (canEdit && selectableAction != null)
                Checkbox(
                  value: selected,
                  onChanged: saving
                      ? null
                      : (value) => onSelectionChanged?.call(value == true),
                )
              else
                const SizedBox(width: 48, child: Icon(Icons.pets_outlined)),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  item.batchRole == 'breeding'
                      ? '母兔 #${item.rabbitId}'
                      : '商品兔 #${item.rabbitId}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              if (onAction != null)
                IconButton(
                  key: ValueKey('batch-member-action-${item.rabbitId}'),
                  tooltip: actionLabel,
                  onPressed: saving ? null : onAction,
                  icon: Icon(_memberActionIcon(item, selectableAction)),
                ),
              if (onAbortion != null)
                IconButton(
                  key: ValueKey('batch-member-abortion-${item.rabbitId}'),
                  tooltip: '记录流产',
                  onPressed: saving ? null : onAbortion,
                  icon: const Icon(Icons.report_problem_outlined),
                ),
              if (onDeparture != null)
                IconButton(
                  key: ValueKey('batch-member-departure-${item.rabbitId}'),
                  // 跟表单标题保持一致：离场表单已是全兔种通用的「登记离场」，
                  // 入口写「母兔离场」会让人以为是另一个功能。
                  tooltip: '登记离场',
                  onPressed: saving ? null : onDeparture,
                  icon: const Icon(Icons.exit_to_app_outlined),
                ),
              if (onRemove != null)
                IconButton(
                  key: ValueKey('batch-member-remove-${item.rabbitId}'),
                  tooltip: '移除批次标签',
                  onPressed: saving ? null : onRemove,
                  icon: const Icon(Icons.remove_circle_outline),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 7,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              _LabelChip(
                label: item.displayStatus,
              ),
              const _LabelChip(label: '批次标签'),
              Text(_roleLabel(item.batchRole)),
              if (item.cageId != null) Text('笼 #${item.cageId}'),
              if (!item.isActive && item.batchRole != 'breeding')
                const Text('已退出'),
            ],
          ),
          if (_nextEventTypeFor(item).isNotEmpty ||
              item.currentNursingKits > 0) ...[
            const SizedBox(height: 8),
            Text(
              [
                if (_nextEventTypeFor(item).isNotEmpty)
                  '下一步 ${_nextEventTypeFor(item)}${_dateSuffix(item.nextEventDate)}',
                if (item.currentNursingKits > 0)
                  '当前带仔 ${item.currentNursingKits} 只 / ${item.nursingLitterCount} 窝',
              ].join(' · '),
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
          if (item.batchRole == 'breeding') ...[
            const SizedBox(height: 10),
            Divider(height: 1, color: AppPalette.of(context).line),
            const SizedBox(height: 10),
            Text(
              '本批次 · ${item.batchCycleCount} 个周期 · ${item.batchOperationCount} 次操作',
              key: ValueKey('batch-member-tracking-${item.rabbitId}'),
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 3),
            Text(
              '${item.batchLitterCount} 窝 · 产仔 ${item.batchTotalKits} · 活仔 ${item.batchLiveKits} · 断奶 ${item.batchWeanedKits}',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            if (item.batchLastOperationAt != null) ...[
              const SizedBox(height: 3),
              Text(
                '最近操作 ${_compactDate(item.batchLastOperationAt!)}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: AppPalette.of(context).muted,
                    ),
              ),
            ],
          ],
          const SizedBox(height: 10),
          Wrap(
            alignment: WrapAlignment.end,
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                key: ValueKey('batch-member-rabbit-${item.rabbitId}'),
                onPressed: onOpenRabbit,
                icon: const Icon(Icons.pets_outlined),
                label: const Text('兔只详情'),
              ),
              if (onOpenTracking != null)
                OutlinedButton.icon(
                  key: ValueKey('batch-member-tracking-open-${item.rabbitId}'),
                  onPressed: onOpenTracking,
                  icon: const Icon(Icons.history),
                  label: const Text('批次记录'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  String _memberActionLabel(BatchRabbitItem item, _BulkMode? action) {
    final nextEventType = _nextEventTypeFor(item);
    if (action == _BulkMode.estrus) return '催情';
    if (nextEventType.contains('出售')) return '进入出库';
    return nextEventType.isEmpty ? '处理生产任务' : '处理$nextEventType';
  }

  IconData _memberActionIcon(BatchRabbitItem item, _BulkMode? action) {
    final nextEventType = _nextEventTypeFor(item);
    if (action == _BulkMode.estrus) return Icons.play_arrow;
    if (nextEventType.contains('出售')) return Icons.local_shipping_outlined;
    return Icons.chevron_right;
  }

  /// 下一步的到期日。
  ///
  /// 走兔场时区并沿用全局的 `MM月dd日`：这个日期和提醒页是同一条待办。旧写法
  /// 直接读 UTC 时刻的 month/day，晚上 8 点后到期的待办会在这里早一天。
  static String _dateSuffix(DateTime? date) {
    if (date == null) return '';
    return '（${DateFormat('MM月dd日').format(farmLocalDateTime(date))}）';
  }
}

String _compactDate(DateTime value) =>
    DateFormat('yyyy-MM-dd').format(farmLocalDateTime(value));

class _LabelChip extends StatelessWidget {
  const _LabelChip({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      constraints: BoxConstraints(
        maxWidth: MediaQuery.sizeOf(context).width * .62,
      ),
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: palette.primarySoft,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: palette.primary,
            ),
      ),
    );
  }
}

class _CompleteChoice {
  const _CompleteChoice({required this.force, required this.remark});

  final bool force;
  final String remark;
}

String _roleLabel(String role) {
  switch (role) {
    case 'breeding':
      return '繁殖母兔';
    case 'fattening':
      return '商品兔';
    default:
      return role.isEmpty ? '角色未设置' : role;
  }
}

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '加载失败，请检查网络后重试';
}
