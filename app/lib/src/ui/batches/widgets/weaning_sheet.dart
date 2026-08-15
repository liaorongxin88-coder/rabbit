import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/batch_sheet_async_state.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_context_line.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

Future<void> showWeaningSheet({
  required BuildContext context,
  required EventItem event,
}) {
  final houseId = event.sourceHouseId;
  final batchId = event.batchId;
  final rabbitId = event.rabbitId;
  if (houseId == null ||
      houseId <= 0 ||
      batchId == null ||
      batchId <= 0 ||
      rabbitId == null ||
      rabbitId <= 0) {
    return Future<void>.value();
  }

  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _WeaningSheet(
      houseId: houseId,
      batchId: batchId,
      rabbitId: rabbitId,
      breedingCycleId: event.category == '生产周期' ? event.recordId : null,
      houseLabel: event.houseLabel,
    ),
  );
}

class _WeaningSheet extends ConsumerStatefulWidget {
  const _WeaningSheet({
    required this.houseId,
    required this.batchId,
    required this.rabbitId,
    this.breedingCycleId,
    required this.houseLabel,
  });

  final int houseId;
  final int batchId;
  final int rabbitId;
  final int? breedingCycleId;
  final String houseLabel;

  @override
  ConsumerState<_WeaningSheet> createState() => _WeaningSheetState();
}

class _WeaningSheetState extends ConsumerState<_WeaningSheet> {
  final _writeRequest = BatchWriteRequestController();
  final _countController = TextEditingController(text: '8');
  final _maleController = TextEditingController();
  final _femaleController = TextEditingController();
  final _weightController = TextEditingController();
  final _remarkController = TextEditingController();

  DateTime _weaningDate = DateTime.now();
  var _autoAssignCage = true;
  int? _selectedCageId;
  var _saving = false;

  @override
  void dispose() {
    _countController.dispose();
    _maleController.dispose();
    _femaleController.dispose();
    _weightController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  int? _parseOptionalInt(TextEditingController controller) {
    final text = controller.text.trim();
    if (text.isEmpty) {
      return null;
    }
    return int.tryParse(text);
  }

  double? _parseOptionalDouble(TextEditingController controller) {
    final text = controller.text.trim();
    if (text.isEmpty) {
      return null;
    }
    return double.tryParse(text);
  }

  List<Cage> _commodityCages(List<Cage> cages) {
    return cages.where((cage) => cage.isCommodityCage).toList()
      ..sort((a, b) {
        if (a.status == '0' && b.status != '0') {
          return -1;
        }
        if (b.status == '0' && a.status != '0') {
          return 1;
        }
        return a.cageNumber.compareTo(b.cageNumber);
      });
  }

  String _cageLabel(Cage cage) {
    final name = cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
    if (cage.rabbitCount <= 0) {
      return '$name · 空笼 · 可放 ${Cage.commodityCapacity} 只';
    }
    return '$name · ${cage.rabbitCount} 只 · 还可放 ${cage.commodityRemainingCapacity} 只';
  }

  Future<void> _pickDate() async {
    final firstDate = DateTime(2020);
    final lastDate = DateTime.now().add(const Duration(days: 1));
    final initialDate = _weaningDate.isBefore(firstDate)
        ? firstDate
        : _weaningDate.isAfter(lastDate)
            ? lastDate
            : _weaningDate;
    final picked = await showDatePicker(
      context: context,
      initialDate: initialDate,
      firstDate: firstDate,
      lastDate: lastDate,
    );
    if (picked != null && mounted) {
      setState(() => _weaningDate = picked);
    }
  }

  Future<void> _submit(List<Cage> cages) async {
    final commodityCages = _commodityCages(cages);
    final count = int.tryParse(_countController.text.trim()) ?? -1;
    if (count < 0) {
      _showMessage('请输入有效的断奶数量');
      return;
    }

    final male = _parseOptionalInt(_maleController);
    final female = _parseOptionalInt(_femaleController);
    if (male != null && male < 0) {
      _showMessage('公兔数量不能小于 0');
      return;
    }
    if (female != null && female < 0) {
      _showMessage('母兔数量不能小于 0');
      return;
    }
    if (male != null && female != null && male + female != count) {
      _showMessage('公母数量之和需等于断奶数量');
      return;
    }

    int? targetCageId;
    if (count > 0 && !_autoAssignCage) {
      targetCageId = _selectedCageId;
      if (targetCageId == null || targetCageId <= 0) {
        _showMessage('请选择目标商品兔笼位');
        return;
      }
      Cage? selectedCage;
      for (final cage in commodityCages) {
        if (cage.id == targetCageId) {
          selectedCage = cage;
          break;
        }
      }
      if (selectedCage == null ||
          !selectedCage.canAcceptCommodityCount(count)) {
        _showMessage('所选笼位剩余容量不足');
        return;
      }
    }

    setState(() => _saving = true);
    try {
      final avgWeight = _parseOptionalDouble(_weightController);
      final remark = _remarkController.text.trim();
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'weaning',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'rabbitId': widget.rabbitId,
          'breedingCycleId': widget.breedingCycleId,
          'weaningDate': formatBatchWriteDate(_weaningDate),
          'weaningCount': count,
          'maleCount': male,
          'femaleCount': female,
          'targetCageId': targetCageId,
          'avgWeight': avgWeight,
          'remark': remark,
        }),
      );
      await ref.read(batchRepositoryProvider).submitWeaning(
            houseId: widget.houseId,
            batchId: widget.batchId,
            rabbitId: widget.rabbitId,
            breedingCycleId: widget.breedingCycleId,
            weaningDate: _weaningDate,
            weaningCount: count,
            maleCount: male,
            femaleCount: female,
            targetCageId: targetCageId,
            avgWeight: avgWeight,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }
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
      Navigator.of(context).pop();
      final cageHint = count == 0
          ? ''
          : _autoAssignCage
              ? '，仔兔已自动分配到商品兔笼'
              : '，仔兔已放入笼位 #$targetCageId';
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            '母兔 #${widget.rabbitId} 断奶完成（$count 只$cageHint）',
          ),
        ),
      );
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
    final cagesAsync = ref.watch(houseCagesProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = mediaQuery.size.height - keyboardInset;
    final dateLabel = DateFormat('yyyy-MM-dd').format(_weaningDate);

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
          child: cagesAsync.when(
            skipLoadingOnRefresh: false,
            loading: () => BatchSheetLoadingState(
              sheetTitle: '断奶并放入笼位',
              message: '正在加载可用笼位',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => BatchSheetErrorState(
              sheetTitle: '断奶并放入笼位',
              error: error,
              fallbackMessage: '无法加载笼位信息，请检查网络后重试。',
              onRetry: () => ref.invalidate(houseCagesProvider(widget.houseId)),
              onClose: () => Navigator.pop(context),
            ),
            data: (cages) {
              final commodityCages = _commodityCages(cages);
              if (!_autoAssignCage &&
                  _selectedCageId == null &&
                  commodityCages.isNotEmpty) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted && _selectedCageId == null) {
                    setState(() => _selectedCageId = commodityCages.first.id);
                  }
                });
              }

              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: ListView(
                      key: const ValueKey('weaning-form-list'),
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
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '断奶并放入笼位',
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleLarge,
                                    ),
                                    const SizedBox(height: 4),
                                    ProductionContextLine(
                                      houseLabel: widget.houseLabel,
                                      rabbitId: widget.rabbitId,
                                      batchId: widget.batchId,
                                      cycleRecordId: widget.breedingCycleId,
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
                        const _InfoBox(
                          text: '断奶后将自动生成商品兔仔兔并写入兔笼。'
                              '数量填 0 表示全部损失，不生成仔兔。',
                        ),
                        const SizedBox(height: 14),
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('断奶日期'),
                          subtitle: Text(dateLabel),
                          trailing: const Icon(Icons.calendar_today_outlined),
                          onTap: _saving ? null : _pickDate,
                        ),
                        const SizedBox(height: 8),
                        TextField(
                          key: const ValueKey('weaning-count'),
                          controller: _countController,
                          enabled: !_saving,
                          keyboardType: TextInputType.number,
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly
                          ],
                          onChanged: (_) => setState(() {}),
                          decoration: const InputDecoration(
                            labelText: '断奶数量',
                            hintText: '本次放入笼位的仔兔数量',
                          ),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: TextField(
                                key: const ValueKey('weaning-male-count'),
                                controller: _maleController,
                                enabled: !_saving,
                                keyboardType: TextInputType.number,
                                inputFormatters: [
                                  FilteringTextInputFormatter.digitsOnly,
                                ],
                                decoration: const InputDecoration(
                                  labelText: '公兔数（可选）',
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: TextField(
                                key: const ValueKey('weaning-female-count'),
                                controller: _femaleController,
                                enabled: !_saving,
                                keyboardType: TextInputType.number,
                                inputFormatters: [
                                  FilteringTextInputFormatter.digitsOnly,
                                ],
                                decoration: const InputDecoration(
                                  labelText: '母兔数（可选）',
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          key: const ValueKey('weaning-average-weight'),
                          controller: _weightController,
                          enabled: !_saving,
                          keyboardType: const TextInputType.numberWithOptions(
                            decimal: true,
                          ),
                          decoration: const InputDecoration(
                            labelText: '平均体重 kg（可选）',
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          key: const ValueKey('weaning-remark'),
                          controller: _remarkController,
                          enabled: !_saving,
                          maxLines: 2,
                          decoration: const InputDecoration(
                            labelText: '备注（可选）',
                          ),
                        ),
                        const SizedBox(height: 16),
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('自动分配商品兔笼位'),
                          subtitle: const Text('优先使用空笼，再使用已有商品兔笼'),
                          value: _autoAssignCage,
                          onChanged: _saving
                              ? null
                              : (value) =>
                                  setState(() => _autoAssignCage = value),
                        ),
                        if (!_autoAssignCage) ...[
                          const SizedBox(height: 8),
                          if (commodityCages.isEmpty)
                            Text(
                              '暂无可用商品兔笼位，请先创建笼位或改用自动分配。',
                              style: Theme.of(context).textTheme.bodyMedium,
                            )
                          else
                            ...commodityCages.map((cage) {
                              final count =
                                  int.tryParse(_countController.text.trim()) ??
                                      0;
                              final enabled = count <= 0 ||
                                  cage.canAcceptCommodityCount(count);
                              return RadioListTile<int>(
                                value: cage.id,
                                groupValue: _selectedCageId,
                                onChanged: _saving || !enabled
                                    ? null
                                    : (value) => setState(
                                          () => _selectedCageId = value,
                                        ),
                                title: Text(_cageLabel(cage)),
                                subtitle: enabled ? null : const Text('剩余容量不足'),
                              );
                            }),
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
                              key: const ValueKey('weaning-submit'),
                              onPressed: _saving ? null : () => _submit(cages),
                              child: _saving
                                  ? const SizedBox.square(
                                      dimension: 20,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Text('确认断奶'),
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
