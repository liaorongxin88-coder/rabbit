import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/weaning_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

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
      return '点击记录配种';
    case ProductionKind.pregnancyCheck:
      return '点击记录摸胎结果';
    case ProductionKind.prepartum:
      return '点击完成备产';
    case ProductionKind.parturition:
      return '点击记录分娩';
    case ProductionKind.weaning:
      return '点击断奶并放入笼位';
    case ProductionKind.sale:
      return '点击记录出售';
    case ProductionKind.replacement:
      return '点击转后备兔并分配笼位';
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
  if (houseId == null ||
      houseId <= 0 ||
      rabbitId == null ||
      rabbitId <= 0) {
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
    final picked = await showDatePicker(
      context: context,
      initialDate: _actionDate,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (picked != null) {
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
        );
      } else if (widget.kind == ProductionKind.pregnancyCheck) {
        await repo.submitPregnancyCheck(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          checkDate: _actionDate,
          result: _pregnancyResult,
          remark: _remarkController.text,
        );
      } else if (widget.kind == ProductionKind.prepartum) {
        await repo.submitPrepartumFinish(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          actionDate: _actionDate,
          remark: _remarkController.text,
        );
      } else if (widget.kind == ProductionKind.parturition) {
        final total = int.tryParse(_totalKitsController.text.trim()) ?? -1;
        final live = int.tryParse(_liveKitsController.text.trim()) ?? -1;
        if (total < 0 || live < 0) {
          _showMessage('请输入有效的产仔数量');
          return;
        }
        if (live > total) {
          _showMessage('活仔数不能大于总产仔数');
          return;
        }
        await repo.submitParturition(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitId: widget.rabbitId,
          birthDate: _actionDate,
          totalKits: total,
          liveKits: live,
          failed: _parturitionFailed,
          remark: _remarkController.text,
        );
      } else if (widget.kind == ProductionKind.sale) {
        await repo.submitSale(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitIds: [widget.rabbitId],
          saleDate: _actionDate,
          remark: _remarkController.text,
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
        );
      }

      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$_title 已完成')),
        );
      }
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
    final rabbitsAsync = ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final cagesAsync = ref.watch(houseCagesProvider(widget.houseId));
    final keyboardInset = MediaQuery.of(context).viewInsets.bottom;
    final dateLabel = DateFormat('yyyy-MM-dd').format(_actionDate);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(context).size.height * 0.92,
          ),
          child: rabbitsAsync.when(
            loading: () => const SizedBox(
              height: 240,
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (error, _) => SizedBox(
              height: 240,
              child: Center(child: Text(error.toString())),
            ),
            data: (rabbits) => cagesAsync.when(
              loading: () => const SizedBox(
                height: 240,
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (error, _) => SizedBox(
                height: 240,
                child: Center(child: Text(error.toString())),
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
                      setState(() => _selectedBackupCageId = backupCages.first.id);
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
                                Text(
                                  _title,
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '${widget.event.houseLabel} · 兔 #${widget.rabbitId}',
                                  style: Theme.of(context).textTheme.bodyMedium,
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            onPressed:
                                _saving ? null : () => Navigator.pop(context),
                            icon: const Icon(Icons.close),
                          ),
                        ],
                      ),
                    ),
                    Flexible(
                      child: ListView(
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                        children: [
                          ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: const Text('日期'),
                            subtitle: Text(dateLabel),
                            trailing:
                                const Icon(Icons.calendar_today_outlined),
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
                                value: result,
                                groupValue: _pregnancyResult,
                                onChanged: _saving
                                    ? null
                                    : (value) => setState(
                                          () => _pregnancyResult = value ?? result,
                                        ),
                                title: Text(result),
                              ),
                          ],
                          if (widget.kind == ProductionKind.parturition) ...[
                            const SizedBox(height: 8),
                            TextField(
                              controller: _totalKitsController,
                              enabled: !_saving,
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
                              controller: _liveKitsController,
                              enabled: !_saving,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '活仔数',
                              ),
                            ),
                            SwitchListTile(
                              contentPadding: EdgeInsets.zero,
                              title: const Text('判定为失败产'),
                              value: _parturitionFailed,
                              onChanged: _saving
                                  ? null
                                  : (value) =>
                                      setState(() => _parturitionFailed = value),
                            ),
                          ],
                          if (widget.kind == ProductionKind.replacement) ...[
                            const SizedBox(height: 8),
                            _InfoBox(
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
                                              () => _selectedBackupCageId = value,
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
                                onPressed:
                                    _saving ? null : () => Navigator.pop(context),
                                child: const Text('取消'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: ElevatedButton(
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
