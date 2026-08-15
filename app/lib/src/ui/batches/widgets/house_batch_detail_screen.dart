import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_event_sheet.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_departure_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

enum _AphrodisiacAction { start, finish }

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
  static const _exited = '__EXITED__';

  final _searchController = TextEditingController();
  final _selectedRabbitIds = <int>{};
  final _batchActionRequest = BatchWriteRequestController();
  final _matingRequest = BatchWriteRequestController();
  final _completeRequest = BatchWriteRequestController();

  String _query = '';
  String _role = _all;
  String _status = _all;
  String _activity = _active;
  _AphrodisiacAction? _selectionAction;
  var _matingSelection = false;
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

  void _refresh({bool includePermission = false}) {
    if (_selectedRabbitIds.isNotEmpty ||
        _selectionAction != null ||
        _matingSelection) {
      setState(_resetSelectionState);
    }
    ref.invalidate(batchDetailProvider(_request));
    ref.invalidate(batchMembersProvider(_request));
    ref.invalidate(houseBatchesProvider(widget.houseId));
    if (includePermission) {
      ref.invalidate(housePermissionProvider(widget.houseId));
    }
  }

  void _resetSelectionState() {
    _selectedRabbitIds.clear();
    _selectionAction = null;
    _matingSelection = false;
  }

  void _clearSelection({bool rotateRequest = true}) {
    setState(() {
      if (rotateRequest) {
        _resetSelectionState();
      } else {
        _selectedRabbitIds.clear();
        _selectionAction = null;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final batch = ref.watch(batchDetailProvider(_request));
    final members = ref.watch(batchMembersProvider(_request));
    final permission = ref.watch(housePermissionProvider(widget.houseId));

    return AppPage(
      title: 'Batch 详情',
      leading: IconButton(
        tooltip: '返回 Batch 列表',
        onPressed: () => context.go('/houses/${widget.houseId}/batches'),
        icon: const Icon(Icons.arrow_back),
      ),
      actions: [
        IconButton(
          tooltip: '刷新 Batch',
          onPressed: _saving ? null : _refresh,
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: _buildBody(batch, members, permission),
    );
  }

  Widget _buildBody(
    AsyncValue<Batch> batch,
    AsyncValue<List<BatchRabbitItem>> members,
    AsyncValue<dynamic> permission,
  ) {
    if (batch.isLoading || members.isLoading || permission.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    final error = batch.error ?? members.error ?? permission.error;
    if (error != null) {
      return ErrorState(
        message: _errorMessage(error),
        onRetry: () => _refresh(includePermission: true),
      );
    }

    final currentBatch = batch.requireValue;
    final allMembers = members.requireValue;
    final canEdit = permission.requireValue.canEdit == true;
    final statuses = _statuses(allMembers);
    final effectiveStatus = _effectiveStatus(statuses);
    if (effectiveStatus != _status) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _status != effectiveStatus) {
          setState(() {
            _status = effectiveStatus;
            if (_selectedRabbitIds.isNotEmpty ||
                _selectionAction != null ||
                _matingSelection) {
              _resetSelectionState();
            }
          });
        }
      });
    }
    final filtered = _filterMembers(allMembers, status: effectiveStatus);
    final selected = allMembers
        .where((item) =>
            _selectedRabbitIds.contains(item.rabbitId) &&
            (_matingSelection
                ? _isMatingSelectable(item)
                : _selectionAction != null &&
                    _aphrodisiacAction(item) == _selectionAction))
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
            _matingSelection = false;
          }
        });
      });
    }

    return RefreshIndicator(
      onRefresh: () async {
        _refresh();
        await Future.wait([
          ref.read(batchDetailProvider(_request).future),
          ref.read(batchMembersProvider(_request).future),
        ]);
      },
      child: ListView.builder(
        key: const ValueKey('batch-detail-member-list'),
        padding: AppSpacing.pagePadding,
        itemCount: 7 + (filtered.isEmpty ? 1 : filtered.length),
        itemBuilder: (context, index) {
          switch (index) {
            case 0:
              return _BatchHeader(
                batch: currentBatch,
                activeCount: allMembers.where((item) => item.isActive).length,
                canEdit: canEdit,
                saving: _saving,
                onComplete: () => _completeBatch(
                  currentBatch,
                  allMembers,
                ),
              );
            case 1:
              return const SizedBox(height: 12);
            case 2:
              return _BatchMetrics(members: allMembers);
            case 3:
              return const SizedBox(height: 12);
            case 4:
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
            case 5:
              return const SizedBox(height: 12);
            case 6:
              return canEdit
                  ? _BatchSelectionBar(
                      visible: filtered,
                      selected: selected,
                      selectionAction: _selectionAction,
                      matingSelection: _matingSelection,
                      saving: _saving,
                      onSelect: (action) => _selectVisible(filtered, action),
                      onSelectMating: () => _selectMatingVisible(filtered),
                      onClear:
                          _selectedRabbitIds.isEmpty ? null : _clearSelection,
                      onSubmit: selected.isEmpty || _selectionAction == null
                          ? null
                          : () => _submitAphrodisiac(
                                _selectionAction!,
                                selected,
                              ),
                      onSubmitMating: selected.isEmpty || !_matingSelection
                          ? null
                          : () => _submitMating(selected),
                    )
                  : const _ReadOnlyNotice();
          }

          if (filtered.isEmpty) {
            return Padding(
              padding: const EdgeInsets.only(top: 12),
              child: SectionCard(
                child: EmptyState(
                  icon: Icons.filter_alt_off_outlined,
                  title: allMembers.isEmpty ? 'Batch 暂无成员' : '没有符合条件的成员',
                  message:
                      allMembers.isEmpty ? '成员加入后会显示在这里。' : '调整兔号、角色、状态或在场筛选。',
                  actionLabel: allMembers.isEmpty ? null : '重置筛选',
                  onAction: allMembers.isEmpty ? null : _resetFilters,
                ),
              ),
            );
          }

          final item = filtered[index - 7];
          // A selection mode owns the row checkboxes. Do not expose an
          // aphrodisiac checkbox while the user is preparing a mating batch.
          final action = _matingSelection ? null : _aphrodisiacAction(item);
          final matingSelectable =
              _matingSelection && _isMatingSelectable(item);
          return Padding(
            padding: const EdgeInsets.only(top: 10),
            child: _BatchMemberCard(
              key: ValueKey('batch-member-${item.rabbitId}'),
              item: item,
              canEdit: canEdit,
              selectableAction: action,
              matingSelectable: matingSelectable,
              selected: _selectedRabbitIds.contains(item.rabbitId),
              saving: _saving,
              onSelectionChanged: matingSelectable
                  ? (selected) => _toggleMatingSelection(item, selected)
                  : action == null
                      ? null
                      : (selected) => _toggleSelection(item, action, selected),
              onAction: canEdit && _memberIsActionable(item)
                  ? () => _handleMemberAction(item)
                  : null,
              onDeparture: canEdit && _memberIsDepartureActionable(item)
                  ? () => _handleMemberDeparture(item)
                  : null,
            ),
          );
        },
      ),
    );
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
      if (_selectedRabbitIds.isNotEmpty ||
          _selectionAction != null ||
          _matingSelection) {
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
      if (_role != _all && item.batchRole != _role) {
        return false;
      }
      if (selectedStatus != _all &&
          item.currentStatus.trim() != selectedStatus) {
        return false;
      }
      if (_activity == _active && !item.isActive) {
        return false;
      }
      if (_activity == _exited && item.isActive) {
        return false;
      }
      if (query.isEmpty) {
        return true;
      }
      return item.rabbitId.toString().contains(query) ||
          (item.cageId?.toString().contains(query) ?? false) ||
          item.currentStatus.toLowerCase().contains(query) ||
          item.nextEventType.toLowerCase().contains(query) ||
          _roleLabel(item.batchRole).contains(query);
    }).toList();
  }

  List<String> _statuses(List<BatchRabbitItem> members) {
    final values = members
        .map((item) => item.currentStatus.trim())
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
      if (_selectedRabbitIds.isNotEmpty ||
          _selectionAction != null ||
          _matingSelection) {
        _resetSelectionState();
      }
    });
  }

  void _selectVisible(
    List<BatchRabbitItem> visible,
    _AphrodisiacAction action,
  ) {
    final ids = visible
        .where((item) => _aphrodisiacAction(item) == action)
        .map((item) => item.rabbitId)
        .toSet();
    setState(() {
      _matingSelection = false;
      _selectionAction = ids.isEmpty ? null : action;
      _selectedRabbitIds
        ..clear()
        ..addAll(ids);
    });
  }

  void _selectMatingVisible(List<BatchRabbitItem> visible) {
    final ids =
        visible.where(_isMatingSelectable).map((item) => item.rabbitId).toSet();
    if (ids.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('当前筛选结果没有可配种母兔')),
      );
      return;
    }
    if (ids.length > 1000) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('单次最多批量配种 1000 只母兔，请缩小筛选范围')),
      );
      return;
    }
    setState(() {
      _matingSelection = true;
      _selectionAction = null;
      _selectedRabbitIds
        ..clear()
        ..addAll(ids);
    });
  }

  void _toggleSelection(
    BatchRabbitItem item,
    _AphrodisiacAction action,
    bool selected,
  ) {
    setState(() {
      _matingSelection = false;
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

  void _toggleMatingSelection(BatchRabbitItem item, bool selected) {
    if (!_matingSelection || !_isMatingSelectable(item)) return;
    setState(() {
      if (selected) {
        _selectedRabbitIds.add(item.rabbitId);
      } else {
        _selectedRabbitIds.remove(item.rabbitId);
      }
      if (_selectedRabbitIds.isEmpty) {
        _matingSelection = false;
      }
    });
  }

  Future<void> _submitMating(List<BatchRabbitItem> selected) async {
    if (!_matingSelection || selected.isEmpty || _saving) return;
    final completed = await showBatchMatingSheet(
      context: context,
      houseId: widget.houseId,
      batchId: widget.batchId,
      rabbitIds: selected.map((item) => item.rabbitId).toList(),
      writeRequest: _matingRequest,
    );
    if (completed && mounted) {
      _clearSelection();
      _refresh();
    }
  }

  Future<void> _submitAphrodisiac(
    _AphrodisiacAction action,
    List<BatchRabbitItem> selected,
  ) async {
    if (selected.isEmpty || _saving) {
      return;
    }
    final label = action == _AphrodisiacAction.start ? '开始催情' : '完成催情';
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
      final repository = ref.read(batchRepositoryProvider);
      final requestId = _batchActionRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': action == _AphrodisiacAction.start
              ? 'startAphrodisiac'
              : 'finishAphrodisiac',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'rabbitIds': ids,
        }),
      );
      if (action == _AphrodisiacAction.start) {
        await repository.startAphrodisiac(
          houseId: widget.houseId,
          batchId: widget.batchId,
          rabbitIds: ids,
          requestId: requestId,
        );
      } else {
        await repository.finishAphrodisiac(
          houseId: widget.houseId,
          batchId: widget.batchId,
          rabbitIds: ids,
          requestId: requestId,
        );
      }
      if (!mounted) {
        return;
      }
      _clearSelection();
      _refresh();
      ref.invalidate(homeEventsProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$label已提交，共 ${ids.length} 只母兔')),
      );
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
    final aphrodisiac = _aphrodisiacAction(item);
    if (aphrodisiac != null) {
      _selectionAction = aphrodisiac;
      _selectedRabbitIds
        ..clear()
        ..add(item.rabbitId);
      await _submitAphrodisiac(aphrodisiac, [item]);
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
          const SnackBar(content: Text('Batch 已结束')),
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

  Future<_CompleteChoice?> _showCompleteDialog(int activeCount) async {
    final remark = TextEditingController();
    var force = false;
    final result = await showDialog<_CompleteChoice>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('结束这个 Batch？'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  activeCount == 0
                      ? '当前没有活跃成员，可以正常结束。'
                      : '当前仍有 $activeCount 个活跃成员。强制结束会关闭开放繁殖周期并移出 Batch。',
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
              child: const Text('结束 Batch'),
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
    if (_aphrodisiacAction(item) != null) {
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

  bool _isMatingSelectable(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') return false;
    return switch (item.currentStatus.trim()) {
      '待配种' || '哺乳中' => true,
      _ => false,
    };
  }

  _AphrodisiacAction? _aphrodisiacAction(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') {
      return null;
    }
    // API status values are display data; tolerate harmless surrounding
    // whitespace so a filtered member remains actionable.
    switch (item.currentStatus.trim()) {
      case '待催情':
      case '休整期':
      case '哺乳中':
        return _AphrodisiacAction.start;
      case '催情中':
        return _AphrodisiacAction.finish;
      default:
        return null;
    }
  }

  EventItem _eventFor(BatchRabbitItem item) {
    final date = item.nextEventDate;
    return EventItem(
      recordId: item.latestCycleId ?? item.id,
      category: item.latestCycleId == null ? '生产' : '生产周期',
      eventType: item.nextEventType,
      eventDate: date,
      batchId: widget.batchId,
      rabbitId: item.rabbitId,
      status: _eventStatus(date),
      sourceHouseId: widget.houseId,
    );
  }

  String _eventStatus(DateTime? date) {
    if (date == null) {
      return '';
    }
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final eventDay = DateTime(date.year, date.month, date.day);
    if (eventDay.isBefore(today)) {
      return 'overdue';
    }
    if (eventDay == today) {
      return 'due';
    }
    return 'upcoming';
  }
}

class _BatchHeader extends StatelessWidget {
  const _BatchHeader({
    required this.batch,
    required this.activeCount,
    required this.canEdit,
    required this.saving,
    required this.onComplete,
  });

  final Batch batch;
  final int activeCount;
  final bool canEdit;
  final bool saving;
  final VoidCallback onComplete;

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
              _LabelChip(
                label:
                    batch.status.trim().isEmpty ? '状态未设置' : batch.status.trim(),
              ),
              Text('ID #${batch.id}'),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${batch.dateLabel} · 活跃成员 $activeCount',
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
              key: const ValueKey('batch-complete-button'),
              onPressed: saving ? null : onComplete,
              icon: const Icon(Icons.task_alt),
              label: const Text('结束 Batch'),
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
    final active = members.where((item) => item.isActive).length;
    final mothers = members
        .where((item) => item.isActive && item.batchRole == 'breeding')
        .length;
    final commodity = members
        .where((item) => item.isActive && item.batchRole == 'fattening')
        .length;
    final nursing = members.fold<int>(
      0,
      (sum, item) => sum + (item.isActive ? item.currentNursingKits : 0),
    );

    return SectionCard(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = (constraints.maxWidth - 8) / 2;
          return Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _MetricTile(width: width, label: '全部成员', value: members.length),
              _MetricTile(width: width, label: '活跃成员', value: active),
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
              labelText: '搜索成员',
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
            decoration: const InputDecoration(labelText: '成员角色'),
            items: const [
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._all,
                  child: Text('全部角色')),
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
            decoration: const InputDecoration(labelText: '成员范围'),
            items: const [
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._active,
                  child: Text('仅活跃成员')),
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._all,
                  child: Text('全部成员')),
              DropdownMenuItem(
                  value: _HouseBatchDetailScreenState._exited,
                  child: Text('仅已退出成员')),
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
                  '显示 $visibleCount / $totalCount 个成员',
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

class _BatchSelectionBar extends StatelessWidget {
  const _BatchSelectionBar({
    required this.visible,
    required this.selected,
    required this.selectionAction,
    required this.matingSelection,
    required this.saving,
    required this.onSelect,
    required this.onSelectMating,
    required this.onClear,
    required this.onSubmit,
    required this.onSubmitMating,
  });

  final List<BatchRabbitItem> visible;
  final List<BatchRabbitItem> selected;
  final _AphrodisiacAction? selectionAction;
  final bool matingSelection;
  final bool saving;
  final ValueChanged<_AphrodisiacAction> onSelect;
  final VoidCallback onSelectMating;
  final VoidCallback? onClear;
  final VoidCallback? onSubmit;
  final VoidCallback? onSubmitMating;

  @override
  Widget build(BuildContext context) {
    final startCount = visible
        .where((item) => _actionForStatus(item) == _AphrodisiacAction.start)
        .length;
    final finishCount = visible
        .where((item) => _actionForStatus(item) == _AphrodisiacAction.finish)
        .length;
    final matingCount = visible.where(_matingSelectable).length;
    final label =
        selectionAction == _AphrodisiacAction.finish ? '完成催情' : '开始催情';

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('批量操作', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            key: const ValueKey('batch-select-start-visible'),
            onPressed: saving || startCount == 0
                ? null
                : () => onSelect(_AphrodisiacAction.start),
            icon: const Icon(Icons.play_arrow),
            label: Text('选择当前待开始（$startCount）'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            key: const ValueKey('batch-select-finish-visible'),
            onPressed: saving || finishCount == 0
                ? null
                : () => onSelect(_AphrodisiacAction.finish),
            icon: const Icon(Icons.check),
            label: Text('选择当前催情中（$finishCount）'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            key: const ValueKey('batch-select-mating-visible'),
            onPressed: saving || matingCount == 0 ? null : onSelectMating,
            icon: const Icon(Icons.favorite_border),
            label: Text('选择当前可配种（$matingCount）'),
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
              key: ValueKey(
                matingSelection
                    ? 'batch-mating-submit'
                    : 'batch-selected-submit',
              ),
              onPressed: saving
                  ? null
                  : matingSelection
                      ? onSubmitMating
                      : onSubmit,
              icon: saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      matingSelection
                          ? Icons.favorite
                          : selectionAction == _AphrodisiacAction.finish
                              ? Icons.check
                              : Icons.play_arrow,
                    ),
              label: Text(
                matingSelection
                    ? '批量配种 ${selected.length} 只'
                    : '$label ${selected.length} 只',
              ),
            ),
          ],
        ],
      ),
    );
  }

  static _AphrodisiacAction? _actionForStatus(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') return null;
    switch (item.currentStatus.trim()) {
      case '待催情':
      case '休整期':
      case '哺乳中':
        return _AphrodisiacAction.start;
      case '催情中':
        return _AphrodisiacAction.finish;
      default:
        return null;
    }
  }

  static bool _matingSelectable(BatchRabbitItem item) {
    if (!item.isActive || item.batchRole != 'breeding') return false;
    return item.currentStatus.trim() == '待配种' ||
        item.currentStatus.trim() == '哺乳中';
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

class _BatchMemberCard extends StatelessWidget {
  const _BatchMemberCard({
    super.key,
    required this.item,
    required this.canEdit,
    required this.selectableAction,
    required this.matingSelectable,
    required this.selected,
    required this.saving,
    required this.onSelectionChanged,
    required this.onAction,
    required this.onDeparture,
  });

  final BatchRabbitItem item;
  final bool canEdit;
  final _AphrodisiacAction? selectableAction;
  final bool matingSelectable;
  final bool selected;
  final bool saving;
  final ValueChanged<bool>? onSelectionChanged;
  final VoidCallback? onAction;
  final VoidCallback? onDeparture;

  @override
  Widget build(BuildContext context) {
    final actionLabel = _memberActionLabel(item, selectableAction);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              if (canEdit && (selectableAction != null || matingSelectable))
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
              if (onDeparture != null)
                IconButton(
                  key: ValueKey('batch-member-departure-${item.rabbitId}'),
                  tooltip: '母兔离场',
                  onPressed: saving ? null : onDeparture,
                  icon: const Icon(Icons.exit_to_app_outlined),
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
                label: item.currentStatus.trim().isEmpty
                    ? '状态未设置'
                    : item.currentStatus.trim(),
              ),
              Text(_roleLabel(item.batchRole)),
              if (item.cageId != null) Text('笼 #${item.cageId}'),
              if (!item.isActive) const Text('已退出'),
            ],
          ),
          if (item.nextEventType.isNotEmpty || item.currentNursingKits > 0) ...[
            const SizedBox(height: 8),
            Text(
              [
                if (item.nextEventType.isNotEmpty)
                  '下一步 ${item.nextEventType}${_dateSuffix(item.nextEventDate)}',
                if (item.currentNursingKits > 0)
                  '当前带仔 ${item.currentNursingKits} 只 / ${item.nursingLitterCount} 窝',
              ].join(' · '),
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ],
      ),
    );
  }

  static String _memberActionLabel(
    BatchRabbitItem item,
    _AphrodisiacAction? action,
  ) {
    if (action == _AphrodisiacAction.start) return '开始催情';
    if (action == _AphrodisiacAction.finish) return '完成催情';
    if (item.nextEventType.contains('出售')) return '进入出库';
    return item.nextEventType.isEmpty ? '处理生产任务' : '处理${item.nextEventType}';
  }

  static IconData _memberActionIcon(
    BatchRabbitItem item,
    _AphrodisiacAction? action,
  ) {
    if (action == _AphrodisiacAction.start) return Icons.play_arrow;
    if (action == _AphrodisiacAction.finish) return Icons.check;
    if (item.nextEventType.contains('出售')) return Icons.local_shipping_outlined;
    return Icons.chevron_right;
  }

  static String _dateSuffix(DateTime? date) {
    if (date == null) return '';
    return '（${date.month}月${date.day}日）';
  }
}

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
