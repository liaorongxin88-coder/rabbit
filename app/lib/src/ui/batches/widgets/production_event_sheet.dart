import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/batch_sheet_async_state.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_context_line.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/weaning_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

enum ProductionKind {
  mating,
  pregnancyCheck,
  prepartum,
  parturition,
  weaning,
  sale,
  replacement,
}

ProductionKind? productionKindFromEvent(EventItem event) {
  if (event.isReplacement) {
    return ProductionKind.replacement;
  }
  final type = event.eventType;
  if (type.contains('配种')) {
    return ProductionKind.mating;
  }
  if (type.contains('摸胎')) {
    return ProductionKind.pregnancyCheck;
  }
  if (type.contains('备产')) {
    return ProductionKind.prepartum;
  }
  if (type.contains('分娩') || type.contains('生产')) {
    return ProductionKind.parturition;
  }
  if (type.contains('断奶')) {
    return ProductionKind.weaning;
  }
  if (type.contains('出售')) {
    return ProductionKind.sale;
  }
  return null;
}

bool eventIsActionable(EventItem event) =>
    productionKindFromEvent(event) != null;

String productionActionHint(EventItem event) {
  switch (productionKindFromEvent(event)) {
    case ProductionKind.mating:
      return '记录配种';
    case ProductionKind.pregnancyCheck:
      return '记录摸胎结果';
    case ProductionKind.prepartum:
      return '完成备产';
    case ProductionKind.parturition:
      return '记录分娩';
    case ProductionKind.weaning:
      return '断奶并放入笼位';
    case ProductionKind.sale:
      return '记录出售';
    case ProductionKind.replacement:
      return '转后备兔并分配笼位';
    case null:
      return '';
  }
}

Future<void> showProductionEventSheet({
  required BuildContext context,
  required EventItem event,
}) {
  final kind = productionKindFromEvent(event);
  if (kind == null) {
    return Future<void>.value();
  }
  if (kind == ProductionKind.weaning) {
    return showWeaningSheet(context: context, event: event);
  }

  final houseId = event.sourceHouseId;
  final rabbitId = event.rabbitId;
  if (houseId == null || houseId <= 0 || rabbitId == null || rabbitId <= 0) {
    return Future<void>.value();
  }

  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _ProductionEventSheet(
      event: event,
      kind: kind,
      houseId: houseId,
      batchId: event.batchId,
      rabbitId: rabbitId,
    ),
  );
}

Future<bool> showBatchMatingSheet({
  required BuildContext context,
  required int houseId,
  required int batchId,
  required List<int> rabbitIds,
  String? requestId,
  BatchWriteRequestController? writeRequest,
}) {
  if (rabbitIds.isEmpty) {
    return Future<bool>.value(false);
  }
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _BatchMatingSheet(
      houseId: houseId,
      batchId: batchId,
      rabbitIds: rabbitIds,
      requestId: requestId,
      writeRequest: writeRequest,
    ),
  ).then((value) => value == true);
}

class _BatchMatingSheet extends ConsumerStatefulWidget {
  const _BatchMatingSheet({
    required this.houseId,
    required this.batchId,
    required this.rabbitIds,
    this.requestId,
    this.writeRequest,
  });

  final int houseId;
  final int batchId;
  final List<int> rabbitIds;
  final String? requestId;
  final BatchWriteRequestController? writeRequest;

  @override
  ConsumerState<_BatchMatingSheet> createState() => _BatchMatingSheetState();
}

class _BatchMatingSheetState extends ConsumerState<_BatchMatingSheet> {
  late final BatchWriteRequestController _writeRequest;
  DateTime _matingDate = DateTime.now();
  int? _selectedMaleId;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _writeRequest = widget.writeRequest ??
        BatchWriteRequestController(requestId: widget.requestId);
  }

  Future<void> _pickDate() async {
    final firstDate = DateTime(2020);
    final lastDate = DateTime.now().add(const Duration(days: 1));
    final initialDate = _matingDate.isBefore(firstDate)
        ? firstDate
        : _matingDate.isAfter(lastDate)
            ? lastDate
            : _matingDate;
    final picked = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: firstDate,
      lastDate: lastDate,
      helpText: '选择配种日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted && !_sameDate(picked, _matingDate)) {
      setState(() => _matingDate = picked);
    }
  }

  void _selectMale(int? value) {
    if (value == null || value == _selectedMaleId) return;
    setState(() => _selectedMaleId = value);
  }

  Future<void> _submit() async {
    final maleId = _selectedMaleId;
    if (maleId == null || maleId <= 0) {
      _showMessage('请选择种公兔');
      return;
    }
    if (widget.rabbitIds.length > 1000) {
      _showMessage('单次最多批量配种 1000 只母兔，请缩小筛选范围');
      return;
    }
    setState(() => _saving = true);
    try {
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'bulkMating',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'femaleRabbitIds': widget.rabbitIds,
          'maleRabbitId': maleId,
          'matingDate': formatBatchWriteDate(_matingDate),
        }),
      );
      await ref.read(batchRepositoryProvider).submitMatingBulk(
            houseId: widget.houseId,
            batchId: widget.batchId,
            rabbitIds: widget.rabbitIds,
            maleRabbitId: maleId,
            matingDate: _matingDate,
            requestId: requestId,
          );
      if (!mounted) return;
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      final detailRequest = BatchDetailRequest(
        houseId: widget.houseId,
        batchId: widget.batchId,
      );
      ref.invalidate(batchDetailProvider(detailRequest));
      ref.invalidate(batchMembersProvider(detailRequest));
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(content: Text('已完成批量配种，共 ${widget.rabbitIds.length} 只母兔')),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(error is ApiException ? error.message : error.toString());
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final rabbits = ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final dateLabel = DateFormat('yyyy-MM-dd').format(_matingDate);
    final mediaQuery = MediaQuery.of(context);
    final availableHeight =
        mediaQuery.size.height - mediaQuery.viewInsets.bottom;
    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: mediaQuery.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * .88),
          child: rabbits.when(
            loading: () => BatchSheetLoadingState(
              sheetTitle: '批量配种',
              message: '正在加载可用种公兔',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => BatchSheetErrorState(
              sheetTitle: '批量配种',
              error: error,
              fallbackMessage: '无法加载种公兔，请检查网络后重试。',
              onRetry: () =>
                  ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId)),
              onClose: () => Navigator.pop(context),
            ),
            data: (items) {
              final males = items
                  .where((rabbit) => rabbit.type == '0' && rabbit.gender == '1')
                  .toList()
                ..sort((a, b) => a.id.compareTo(b.id));
              if (_selectedMaleId == null && males.isNotEmpty) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted && _selectedMaleId == null) {
                    setState(() => _selectedMaleId = males.first.id);
                  }
                });
              }
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 18, 12, 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('批量配种',
                                  style:
                                      Theme.of(context).textTheme.titleLarge),
                              const SizedBox(height: 4),
                              Text(
                                  '已选择 ${widget.rabbitIds.length} 只母兔 · 同一公兔、同一日期',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis),
                            ],
                          ),
                        ),
                        IconButton(
                          tooltip: '关闭',
                          onPressed:
                              _saving ? null : () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                  ),
                  Flexible(
                    child: ListView(
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                      children: [
                        ListTile(
                          key: const ValueKey('batch-mating-date'),
                          contentPadding: EdgeInsets.zero,
                          title: const Text('配种日期'),
                          subtitle: Text(dateLabel),
                          trailing: const Icon(Icons.calendar_today_outlined),
                          onTap: _saving ? null : _pickDate,
                        ),
                        const SizedBox(height: 8),
                        Text('种公兔',
                            style: Theme.of(context).textTheme.titleSmall),
                        if (males.isEmpty)
                          const Padding(
                            padding: EdgeInsets.only(top: 10),
                            child: Text('暂无可用种公兔，请先在笼位录入种公兔。'),
                          )
                        else
                          ...males.map(
                            (male) => RadioListTile<int>(
                              key: ValueKey('batch-mating-male-${male.id}'),
                              value: male.id,
                              groupValue: _selectedMaleId,
                              onChanged: _saving ? null : _selectMale,
                              title: Text(
                                  '兔 #${male.id} · ${male.breed.isEmpty ? '未填品种' : male.breed}'),
                            ),
                          ),
                      ],
                    ),
                  ),
                  DecoratedBox(
                    decoration: BoxDecoration(
                        border: Border(
                            top: BorderSide(
                                color: AppPalette.of(context).line))),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                      child: Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed:
                                  _saving ? null : () => Navigator.pop(context),
                              child: const Text('取消'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: ElevatedButton(
                              key: const ValueKey('batch-mating-confirm'),
                              onPressed:
                                  _saving || males.isEmpty ? null : _submit,
                              child: _saving
                                  ? const SizedBox.square(
                                      dimension: 20,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2, color: Colors.white))
                                  : const Text('确认批量配种'),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  static bool _sameDate(DateTime left, DateTime right) {
    return left.year == right.year &&
        left.month == right.month &&
        left.day == right.day;
  }
}

class _ProductionEventSheet extends ConsumerStatefulWidget {
  const _ProductionEventSheet({
    required this.event,
    required this.kind,
    required this.houseId,
    required this.batchId,
    required this.rabbitId,
  });

  final EventItem event;
  final ProductionKind kind;
  final int houseId;
  final int? batchId;
  final int rabbitId;

  @override
  ConsumerState<_ProductionEventSheet> createState() =>
      _ProductionEventSheetState();
}

class _ProductionEventSheetState extends ConsumerState<_ProductionEventSheet> {
  final _writeRequest = BatchWriteRequestController();
  late DateTime _actionDate;
  final _remarkController = TextEditingController();
  final _totalKitsController = TextEditingController(text: '8');
  final _liveKitsController = TextEditingController(text: '8');
  var _pregnancyResult = '怀孕';
  var _parturitionFailed = false;
  int? _selectedMaleId;
  int? _selectedBackupCageId;
  var _autoAssignBackupCage = true;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _actionDate = widget.event.eventDate ?? DateTime.now();
  }

  @override
  void dispose() {
    _remarkController.dispose();
    _totalKitsController.dispose();
    _liveKitsController.dispose();
    super.dispose();
  }

  String get _title {
    switch (widget.kind) {
      case ProductionKind.mating:
        return '记录配种';
      case ProductionKind.pregnancyCheck:
        return '记录摸胎';
      case ProductionKind.prepartum:
        return '完成备产';
      case ProductionKind.parturition:
        return '记录分娩';
      case ProductionKind.sale:
        return '记录出售';
      case ProductionKind.replacement:
        return '转后备兔';
      case ProductionKind.weaning:
        return '断奶';
    }
  }

  Future<void> _pickDate() async {
    final firstDate = DateTime(2020);
    final lastDate = DateTime.now().add(const Duration(days: 1));
    final initialDate = _actionDate.isBefore(firstDate)
        ? firstDate
        : _actionDate.isAfter(lastDate)
            ? lastDate
            : _actionDate;
    final picked = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: firstDate,
      lastDate: lastDate,
      helpText: '选择$_title日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _actionDate = picked);
    }
  }

  List<Cage> _backupCages(List<Cage> cages) {
    return cages
        .where(
          (cage) =>
              cage.isEnabled &&
              (cage.status == '0' || cage.status == '2') &&
              cage.rabbitCount < 1,
        )
        .toList()
      ..sort((a, b) => a.cageNumber.compareTo(b.cageNumber));
  }

  int? get _breedingCycleId =>
      widget.event.category == '生产周期' ? widget.event.recordId : null;

  String _requestIdFor(Map<String, Object?> fields) {
    return _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': widget.kind.name,
        'houseId': widget.houseId,
        'batchId': widget.batchId,
        'rabbitId': widget.rabbitId,
        'breedingCycleId': _breedingCycleId,
        ...fields,
      }),
    );
  }

  Future<void> _submit({
    required List<Rabbit> rabbits,
    required List<Cage> cages,
  }) async {
    final batchId = widget.batchId;
    if (widget.kind != ProductionKind.replacement &&
        (batchId == null || batchId <= 0)) {
      _showMessage('批次信息缺失，请刷新后重试');
      return;
    }

    setState(() => _saving = true);
    try {
      final repo = ref.read(batchRepositoryProvider);
      final rabbitRepo = ref.read(rabbitRepositoryProvider);

      if (widget.kind == ProductionKind.mating) {
        if (_selectedMaleId == null || _selectedMaleId! <= 0) {
          _showMessage('请选择种公兔');
          return;
        }
        await repo.submitMating(
          houseId: widget.houseId,
          batchId: batchId!,
          femaleRabbitId: widget.rabbitId,
          maleRabbitId: _selectedMaleId!,
          matingDate: _actionDate,
          requestId: _requestIdFor({
            'maleRabbitId': _selectedMaleId,
            'matingDate': formatBatchWriteDate(_actionDate),
          }),
        );
      } else if (widget.kind == ProductionKind.pregnancyCheck) {
        final remark = _remarkController.text.trim();
        await repo.submitPregnancyCheck(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          breedingCycleId: _breedingCycleId,
          checkDate: _actionDate,
          result: _pregnancyResult,
          remark: remark,
          requestId: _requestIdFor({
            'checkDate': formatBatchWriteDate(_actionDate),
            'result': _pregnancyResult,
            'remark': remark,
          }),
        );
      } else if (widget.kind == ProductionKind.prepartum) {
        final remark = _remarkController.text.trim();
        await repo.submitPrepartumFinish(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          breedingCycleId: _breedingCycleId,
          actionDate: _actionDate,
          remark: remark,
          requestId: _requestIdFor({
            'actionDate': formatBatchWriteDate(_actionDate),
            'remark': remark,
          }),
        );
      } else if (widget.kind == ProductionKind.parturition) {
        final total = int.tryParse(_totalKitsController.text.trim()) ?? -1;
        final live = int.tryParse(_liveKitsController.text.trim()) ?? -1;
        if (total < 0 || live < 0) {
          _showMessage('请输入有效的产仔数量');
          return;
        }
        if (_parturitionFailed && (total != 0 || live != 0)) {
          _showMessage('失败产的总产仔数和活仔数必须为 0');
          return;
        }
        if (live > total) {
          _showMessage('活仔数不能大于总产仔数');
          return;
        }
        final remark = _remarkController.text.trim();
        await repo.submitParturition(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          breedingCycleId: _breedingCycleId,
          birthDate: _actionDate,
          totalKits: total,
          liveKits: live,
          failed: _parturitionFailed,
          remark: remark,
          requestId: _requestIdFor({
            'birthDate': formatBatchWriteDate(_actionDate),
            'totalKits': total,
            'liveKits': live,
            'failed': _parturitionFailed,
            'remark': remark,
          }),
        );
      } else if (widget.kind == ProductionKind.sale) {
        final remark = _remarkController.text.trim();
        await repo.submitSale(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitIds: [widget.rabbitId],
          saleDate: _actionDate,
          remark: remark,
          requestId: _requestIdFor({
            'rabbitIds': [widget.rabbitId],
            'saleDate': formatBatchWriteDate(_actionDate),
            'remark': remark,
          }),
        );
      } else if (widget.kind == ProductionKind.replacement) {
        int? targetCageId;
        if (!_autoAssignBackupCage) {
          targetCageId = _selectedBackupCageId;
          if (targetCageId == null || targetCageId <= 0) {
            _showMessage('请选择后备兔笼位');
            return;
          }
        }
        await rabbitRepo.convertToReplacement(
          houseId: widget.houseId,
          rabbitIds: [widget.rabbitId],
          targetCageId: targetCageId,
          requestId: _requestIdFor({
            'rabbitIds': [widget.rabbitId],
            'targetCageId': targetCageId,
            'forceExitBatch': true,
          }),
        );
      }

      if (!mounted) {
        return;
      }
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      if (batchId != null && batchId > 0) {
        final detailRequest = BatchDetailRequest(
          houseId: widget.houseId,
          batchId: batchId,
        );
        ref.invalidate(batchDetailProvider(detailRequest));
        ref.invalidate(batchMembersProvider(detailRequest));
      }
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop();
      messenger?.showSnackBar(SnackBar(content: Text('$_title 已完成')));
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is ApiException ? error.message : error.toString();
      _showMessage(message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final cagesAsync = ref.watch(houseCagesProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = mediaQuery.size.height - keyboardInset;
    final dateLabel = DateFormat('yyyy-MM-dd').format(_actionDate);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: availableHeight,
          ),
          child: rabbitsAsync.when(
            skipLoadingOnRefresh: false,
            loading: () => BatchSheetLoadingState(
              sheetTitle: _title,
              message: '正在加载兔只信息',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => BatchSheetErrorState(
              sheetTitle: _title,
              error: error,
              fallbackMessage: '无法加载兔只信息，请检查网络后重试。',
              onRetry: () => ref.invalidate(
                allActiveHouseRabbitsProvider(widget.houseId),
              ),
              onClose: () => Navigator.pop(context),
            ),
            data: (rabbits) => cagesAsync.when(
              skipLoadingOnRefresh: false,
              loading: () => BatchSheetLoadingState(
                sheetTitle: _title,
                message: '正在加载笼位信息',
                onClose: () => Navigator.pop(context),
              ),
              error: (error, _) => BatchSheetErrorState(
                sheetTitle: _title,
                error: error,
                fallbackMessage: '无法加载笼位信息，请检查网络后重试。',
                onRetry: () =>
                    ref.invalidate(houseCagesProvider(widget.houseId)),
                onClose: () => Navigator.pop(context),
              ),
              data: (cages) {
                final males = rabbits
                    .where((r) => r.type == '0' && r.gender == '1')
                    .toList();
                if (widget.kind == ProductionKind.mating &&
                    _selectedMaleId == null &&
                    males.isNotEmpty) {
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted && _selectedMaleId == null) {
                      setState(() => _selectedMaleId = males.first.id);
                    }
                  });
                }

                final backupCages = _backupCages(cages);
                if (widget.kind == ProductionKind.replacement &&
                    !_autoAssignBackupCage &&
                    _selectedBackupCageId == null &&
                    backupCages.isNotEmpty) {
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted && _selectedBackupCageId == null) {
                      setState(
                          () => _selectedBackupCageId = backupCages.first.id);
                    }
                  });
                }

                return Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: ListView(
                        key: const ValueKey('production-event-form-list'),
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                        children: [
                          Padding(
                            padding: const EdgeInsets.fromLTRB(0, 18, 0, 8),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        _title,
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleLarge,
                                      ),
                                      const SizedBox(height: 4),
                                      ProductionContextLine(
                                        houseLabel: widget.event.houseLabel,
                                        rabbitId: widget.rabbitId,
                                        batchId: widget.batchId,
                                        cycleRecordId:
                                            widget.event.isBreedingCycle
                                                ? widget.event.recordId
                                                : null,
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  onPressed: _saving
                                      ? null
                                      : () => Navigator.pop(context),
                                  icon: const Icon(Icons.close),
                                ),
                              ],
                            ),
                          ),
                          ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: const Text('日期'),
                            subtitle: Text(dateLabel),
                            trailing: const Icon(Icons.calendar_today_outlined),
                            onTap: _saving ? null : _pickDate,
                          ),
                          if (widget.kind == ProductionKind.mating) ...[
                            const SizedBox(height: 8),
                            Text(
                              '种公兔',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            if (males.isEmpty)
                              const Padding(
                                padding: EdgeInsets.only(top: 8),
                                child: Text('暂无可用种公兔，请先在笼位录入。'),
                              )
                            else
                              ...males.map(
                                (male) => RadioListTile<int>(
                                  value: male.id,
                                  groupValue: _selectedMaleId,
                                  onChanged: _saving
                                      ? null
                                      : (value) => setState(
                                            () => _selectedMaleId = value,
                                          ),
                                  title: Text(
                                    '兔 #${male.id} · ${male.breed.isEmpty ? '未填品种' : male.breed}',
                                  ),
                                ),
                              ),
                          ],
                          if (widget.kind == ProductionKind.pregnancyCheck) ...[
                            const SizedBox(height: 8),
                            Text(
                              '摸胎结果',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            for (final result in const ['怀孕', '空怀', '不确定'])
                              RadioListTile<String>(
                                key: ValueKey('pregnancy-result-$result'),
                                value: result,
                                groupValue: _pregnancyResult,
                                onChanged: _saving
                                    ? null
                                    : (value) => setState(
                                          () => _pregnancyResult =
                                              value ?? result,
                                        ),
                                title: Text(result),
                              ),
                          ],
                          if (widget.kind == ProductionKind.parturition) ...[
                            const SizedBox(height: 8),
                            TextField(
                              key: const ValueKey('parturition-total-kits'),
                              controller: _totalKitsController,
                              enabled: !_saving && !_parturitionFailed,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '总产仔数',
                              ),
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              key: const ValueKey('parturition-live-kits'),
                              controller: _liveKitsController,
                              enabled: !_saving && !_parturitionFailed,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '活仔数',
                              ),
                            ),
                            SwitchListTile(
                              key: const ValueKey('parturition-failed-switch'),
                              contentPadding: EdgeInsets.zero,
                              title: const Text('判定为失败产'),
                              value: _parturitionFailed,
                              onChanged: _saving
                                  ? null
                                  : (value) => setState(() {
                                        _parturitionFailed = value;
                                        if (value) {
                                          _totalKitsController.text = '0';
                                          _liveKitsController.text = '0';
                                        }
                                      }),
                            ),
                            if (_parturitionFailed)
                              Text(
                                '失败产的总产仔数和活仔数均固定为 0。',
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                          ],
                          if (widget.kind == ProductionKind.replacement) ...[
                            const SizedBox(height: 8),
                            const _InfoBox(
                              text: '将把商品兔转为后备兔，并放入后备兔笼位。',
                            ),
                            SwitchListTile(
                              contentPadding: EdgeInsets.zero,
                              title: const Text('自动分配后备兔笼'),
                              value: _autoAssignBackupCage,
                              onChanged: _saving
                                  ? null
                                  : (value) => setState(
                                        () => _autoAssignBackupCage = value,
                                      ),
                            ),
                            if (!_autoAssignBackupCage) ...[
                              if (backupCages.isEmpty)
                                const Text('暂无空的后备兔笼位')
                              else
                                ...backupCages.map(
                                  (cage) => RadioListTile<int>(
                                    value: cage.id,
                                    groupValue: _selectedBackupCageId,
                                    onChanged: _saving
                                        ? null
                                        : (value) => setState(
                                              () =>
                                                  _selectedBackupCageId = value,
                                            ),
                                    title: Text(
                                      cage.cageNumber.isEmpty
                                          ? '#${cage.id}'
                                          : cage.cageNumber,
                                    ),
                                  ),
                                ),
                            ],
                          ],
                          if (widget.kind != ProductionKind.mating &&
                              widget.kind != ProductionKind.replacement) ...[
                            const SizedBox(height: 12),
                            TextField(
                              key: const ValueKey('production-event-remark'),
                              controller: _remarkController,
                              enabled: !_saving,
                              maxLines: 2,
                              decoration: const InputDecoration(
                                labelText: '备注（可选）',
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    DecoratedBox(
                      decoration: BoxDecoration(
                        border: Border(
                          top: BorderSide(color: AppPalette.of(context).line),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                        child: Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                onPressed: _saving
                                    ? null
                                    : () => Navigator.pop(context),
                                child: const Text('取消'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: ElevatedButton(
                                key: const ValueKey('production-event-submit'),
                                onPressed: _saving
                                    ? null
                                    : () => _submit(
                                          rabbits: rabbits,
                                          cages: cages,
                                        ),
                                child: _saving
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.white,
                                        ),
                                      )
                                    : const Text('确认'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

class _InfoBox extends StatelessWidget {
  const _InfoBox({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(text, style: Theme.of(context).textTheme.bodyMedium),
    );
  }
}
