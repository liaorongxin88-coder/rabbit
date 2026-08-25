import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

Future<WeaningSeparationResult?> showProductionWeaningSeparationSheet({
  required BuildContext context,
  required int houseId,
  Cage? currentCage,
  int? initialBatchId,
  PendingWeaningRecord? initialRecord,
}) {
  return showAppModalSheet<WeaningSeparationResult>(
    context: context,
    builder: (_) => _ProductionWeaningSeparationSheet(
      houseId: houseId,
      currentCage: currentCage,
      initialBatchId: initialBatchId,
      initialRecord: initialRecord,
    ),
  );
}

Future<WeaningSeparationResult?> showBatchWeaningSeparationSheet({
  required BuildContext context,
  required int houseId,
  required int batchId,
  required PendingWeaningRecord record,
}) {
  return showProductionWeaningSeparationSheet(
    context: context,
    houseId: houseId,
    initialBatchId: batchId,
    initialRecord: record,
  );
}

class _ProductionWeaningSeparationSheet extends ConsumerStatefulWidget {
  const _ProductionWeaningSeparationSheet({
    required this.houseId,
    this.currentCage,
    this.initialBatchId,
    this.initialRecord,
  });

  final int houseId;
  final Cage? currentCage;
  final int? initialBatchId;
  final PendingWeaningRecord? initialRecord;

  @override
  ConsumerState<_ProductionWeaningSeparationSheet> createState() =>
      _ProductionWeaningSeparationSheetState();
}

class _ProductionWeaningSeparationSheetState
    extends ConsumerState<_ProductionWeaningSeparationSheet> {
  final _countController = TextEditingController();
  final _maleController = TextEditingController();
  final _femaleController = TextEditingController();
  final _request = BatchWriteRequestController();

  int? _batchId;
  int? _recordId;
  int? _cageId;
  int? _motherRabbitId;
  int? _fatherRabbitId;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _batchId = widget.initialBatchId;
    _recordId = widget.initialRecord?.id;
    _cageId = widget.currentCage?.id;
    final record = widget.initialRecord;
    if (record != null) {
      _setRecordControllers(record);
    }
  }

  @override
  void dispose() {
    _countController.dispose();
    _maleController.dispose();
    _femaleController.dispose();
    super.dispose();
  }

  void _startNewDraft() {
    _request.startNewDraft();
  }

  void _setRecordControllers(PendingWeaningRecord record) {
    _countController.text = '${record.waitingCount}';
    if (record.hasTrustworthyWaitingGenderCounts) {
      _maleController.text = '${record.waitingMaleCount}';
      _femaleController.text = '${record.waitingFemaleCount}';
    } else {
      _maleController.clear();
      _femaleController.clear();
    }
  }

  void _selectBatch(int? value) {
    if (value == _batchId) return;
    setState(() {
      _batchId = value;
      _recordId = null;
      _motherRabbitId = null;
      _fatherRabbitId = null;
      _countController.clear();
      _maleController.clear();
      _femaleController.clear();
      _startNewDraft();
    });
  }

  void _selectRecord(
    int? value,
    List<PendingWeaningRecord> records,
  ) {
    if (value == _recordId) return;
    final record = records.where((item) => item.id == value).firstOrNull;
    setState(() {
      _recordId = value;
      _motherRabbitId = null;
      _fatherRabbitId = null;
      if (record == null) {
        _countController.clear();
        _maleController.clear();
        _femaleController.clear();
      } else {
        _setRecordControllers(record);
      }
      _startNewDraft();
    });
  }

  Future<void> _submit({
    required List<Batch> batches,
    required List<PendingWeaningRecord> records,
    required List<Cage> cages,
    required List<Rabbit> parentCandidates,
  }) async {
    if (_saving) return;

    final batchId = _batchId;
    final batch = batches.where((item) => item.id == batchId).firstOrNull;
    if (batch == null) {
      _showMessage('请选择未完成的批次');
      return;
    }
    final record = records.where((item) => item.id == _recordId).firstOrNull;
    if (record == null || record.batchId != batch.id) {
      _showMessage('请选择待分笼记录');
      return;
    }
    final cage = cages.where((item) => item.id == _cageId).firstOrNull;
    if (cage == null || cage.houseId != widget.houseId) {
      _showMessage('目标笼位不属于当前兔舍，请刷新后重试');
      return;
    }
    if (!cage.isProductionIntakeCage) {
      _showMessage('目标笼位已停用、用途不符或没有剩余容量');
      return;
    }

    final count = int.tryParse(_countController.text.trim()) ?? 0;
    int? maleCount;
    int? femaleCount;
    if (record.hasTrustworthyWaitingGenderCounts) {
      maleCount = int.tryParse(_maleController.text.trim());
      femaleCount = int.tryParse(_femaleController.text.trim());
      if (maleCount == null || femaleCount == null) {
        _showMessage('请同时填写本笼公兔和母兔数量');
        return;
      }
    }
    final allocation = CageAllocation(
      cageId: cage.id,
      count: count,
      maleCount: maleCount,
      femaleCount: femaleCount,
    );
    final allocationError = allocation.validate(
      waitingCount: record.waitingCount,
      waitingMaleCount: record.waitingMaleCount,
      waitingFemaleCount: record.waitingFemaleCount,
    );
    if (allocationError != null) {
      _showMessage(allocationError);
      return;
    }
    if (!cage.canAcceptCommodityCount(count)) {
      _showMessage('当前笼位剩余容量不足');
      return;
    }

    final motherError = _validateParent(
      id: _motherRabbitId,
      candidates: parentCandidates,
      gender: '0',
      label: '母兔',
    );
    if (motherError != null) {
      _showMessage(motherError);
      return;
    }
    final fatherError = _validateParent(
      id: _fatherRabbitId,
      candidates: parentCandidates,
      gender: '1',
      label: '公兔',
    );
    if (fatherError != null) {
      _showMessage(fatherError);
      return;
    }

    final allocations = <CageAllocation>[allocation];
    final requestId = _request.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'separateWeaning',
        'houseId': widget.houseId,
        'batchId': batch.id,
        'weaningRecordId': record.id,
        'allocations': allocations.map((item) => item.toJson()).toList(),
        'motherRabbitId': _motherRabbitId,
        'fatherRabbitId': _fatherRabbitId,
      }),
    );

    setState(() => _saving = true);
    try {
      final result =
          await ref.read(batchRepositoryProvider).separatePendingWeaning(
                houseId: widget.houseId,
                batchId: batch.id,
                weaningRecordId: record.id,
                allocations: allocations,
                motherRabbitId: _motherRabbitId,
                fatherRabbitId: _fatherRabbitId,
                requestId: requestId,
              );
      await _refreshAfterSuccess(batch.id, allocations);
      if (!mounted) return;
      Navigator.of(context).pop(result);
    } catch (error) {
      if (!mounted) return;
      if (_isConflict(error)) {
        await _refreshAfterConflict(batch.id, cage.id);
        if (mounted) {
          _showMessage('笼位或待分数量已变化，数据已刷新，请确认后再次提交');
        }
      } else {
        _showMessage(error is ApiException ? error.message : error.toString());
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _validateParent({
    required int? id,
    required List<Rabbit> candidates,
    required String gender,
    required String label,
  }) {
    if (id == null) return null;
    final rabbit = candidates.where((item) => item.id == id).firstOrNull;
    if (rabbit == null ||
        rabbit.houseId != widget.houseId ||
        rabbit.type != '0' ||
        rabbit.gender != gender) {
      return '所选$label不符合关联条件，请重新选择';
    }
    return null;
  }

  bool _isConflict(Object error) {
    return error is ApiException &&
        (error.statusCode == 409 || error.businessCode == 409);
  }

  Future<void> _refreshAfterConflict(int batchId, int cageId) async {
    final request = BatchDetailRequest(
      houseId: widget.houseId,
      batchId: batchId,
    );
    ref.invalidate(
        cageSummaryProvider((houseId: widget.houseId, cageId: cageId)));
    ref.invalidate(
        cageRabbitsProvider((houseId: widget.houseId, cageId: cageId)));
    final recordsRefresh =
        ref.refresh(pendingWeaningRecordsProvider(request).future);
    await Future.wait([
      _ignoreRefresh(ref.refresh(houseCagesProvider(widget.houseId).future)),
      _ignoreRefresh(recordsRefresh),
    ]);
    List<PendingWeaningRecord> records;
    try {
      records = await recordsRefresh;
    } catch (_) {
      return;
    }
    if (!mounted) return;
    final record = records
        .where((item) => item.id == _recordId && item.waitingCount > 0)
        .firstOrNull;
    setState(() {
      if (record == null) {
        _recordId = null;
        _countController.clear();
        _maleController.clear();
        _femaleController.clear();
      } else {
        _setRecordControllers(record);
      }
    });
  }

  Future<void> _refreshAfterSuccess(
    int batchId,
    List<CageAllocation> allocations,
  ) async {
    final request = BatchDetailRequest(
      houseId: widget.houseId,
      batchId: batchId,
    );
    for (final allocation in allocations) {
      final key = (houseId: widget.houseId, cageId: allocation.cageId);
      ref.invalidate(cageSummaryProvider(key));
      ref.invalidate(cageRabbitsProvider(key));
    }
    ref.invalidate(currentHouseBatchesProvider);
    ref.invalidate(homeEventsProvider);
    await Future.wait([
      _ignoreRefresh(ref.refresh(houseCagesProvider(widget.houseId).future)),
      _ignoreRefresh(ref.refresh(houseRabbitsProvider(widget.houseId).future)),
      _ignoreRefresh(ref.refresh(houseBatchesProvider(widget.houseId).future)),
      _ignoreRefresh(
          ref.refresh(pendingWeaningRecordsProvider(request).future)),
    ]);
  }

  Future<void> _ignoreRefresh(Future<Object?> refresh) async {
    try {
      await refresh;
    } catch (_) {
      // The write succeeded or the conflict was already handled. The watched
      // provider keeps its normal error state so the user can retry the read.
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final batchesAsync = ref.watch(houseBatchesProvider(widget.houseId));
    final cagesAsync = ref.watch(houseCagesProvider(widget.houseId));
    final parentsAsync =
        ref.watch(houseBreedingParentCandidatesProvider(widget.houseId));

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: mediaQuery.size.height * 0.92),
          child: _buildAsyncBody(
            batchesAsync: batchesAsync,
            cagesAsync: cagesAsync,
            parentsAsync: parentsAsync,
          ),
        ),
      ),
    );
  }

  Widget _buildAsyncBody({
    required AsyncValue<List<Batch>> batchesAsync,
    required AsyncValue<List<Cage>> cagesAsync,
    required AsyncValue<List<Rabbit>> parentsAsync,
  }) {
    if (batchesAsync.isLoading ||
        cagesAsync.isLoading ||
        parentsAsync.isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: CircularProgressIndicator(),
        ),
      );
    }
    final error = batchesAsync.error ?? cagesAsync.error ?? parentsAsync.error;
    if (error != null) {
      return _LoadFailure(
        message: '生产资料加载失败：$error',
        onRetry: () {
          ref.invalidate(houseBatchesProvider(widget.houseId));
          ref.invalidate(houseCagesProvider(widget.houseId));
          ref.invalidate(houseBreedingParentCandidatesProvider(widget.houseId));
        },
      );
    }

    final batches = productionIntakeBatches(
      batchesAsync.requireValue,
      houseId: widget.houseId,
    );
    final cages = cagesAsync.requireValue;
    final parents = parentsAsync.requireValue;
    final selectedBatchId =
        batches.any((item) => item.id == _batchId) ? _batchId : null;
    final recordsAsync = selectedBatchId == null
        ? const AsyncValue<List<PendingWeaningRecord>>.data(
            <PendingWeaningRecord>[],
          )
        : ref.watch(
            pendingWeaningRecordsProvider(
              BatchDetailRequest(
                houseId: widget.houseId,
                batchId: selectedBatchId,
              ),
            ),
          );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildHeader(),
        Flexible(
          child: SingleChildScrollView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
            child: _buildForm(
              batches: batches,
              selectedBatchId: selectedBatchId,
              recordsAsync: recordsAsync,
              cages: cages,
              parents: parents,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '场内生产入笼',
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          IconButton(
            tooltip: '关闭',
            onPressed: _saving ? null : () => Navigator.of(context).pop(),
            icon: const Icon(Icons.close),
          ),
        ],
      ),
    );
  }

  Widget _buildForm({
    required List<Batch> batches,
    required int? selectedBatchId,
    required AsyncValue<List<PendingWeaningRecord>> recordsAsync,
    required List<Cage> cages,
    required List<Rabbit> parents,
  }) {
    if (batches.isEmpty) {
      return const _SheetEmpty(
        icon: Icons.inventory_2_outlined,
        title: '没有可分笼的批次',
        message: '当前兔舍没有未完成的批次。',
      );
    }

    final fixedBatch = widget.initialBatchId != null;
    final selectedBatch =
        batches.where((item) => item.id == selectedBatchId).firstOrNull;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (fixedBatch && selectedBatch != null)
          InfoNotice(
            icon: Icons.inventory_2_outlined,
            text: '批次：${selectedBatch.title}',
          )
        else
          DropdownButtonFormField<int>(
            key: const ValueKey('production-batch'),
            value: selectedBatchId,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: '批次',
              hintText: '请选择未完成的批次',
            ),
            items: [
              for (final batch in batches)
                DropdownMenuItem(
                  key: ValueKey('production-batch-option-${batch.id}'),
                  value: batch.id,
                  child: Text(
                    batch.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: _saving ? null : _selectBatch,
          ),
        const SizedBox(height: 12),
        _buildRecords(
          selectedBatchId: selectedBatchId,
          recordsAsync: recordsAsync,
          batches: batches,
          cages: cages,
          parents: parents,
        ),
      ],
    );
  }

  Widget _buildRecords({
    required int? selectedBatchId,
    required AsyncValue<List<PendingWeaningRecord>> recordsAsync,
    required List<Batch> batches,
    required List<Cage> cages,
    required List<Rabbit> parents,
  }) {
    if (selectedBatchId == null) {
      return const InfoNotice(
        icon: Icons.arrow_upward,
        text: '先选择批次，再选择待分笼记录。',
      );
    }
    return recordsAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => _LoadFailure(
        message: '待分笼记录加载失败：$error',
        onRetry: () => ref.invalidate(
          pendingWeaningRecordsProvider(
            BatchDetailRequest(
              houseId: widget.houseId,
              batchId: selectedBatchId,
            ),
          ),
        ),
      ),
      data: (allRecords) {
        final records = pendingProductionRecords(allRecords)
            .where((record) => record.batchId == selectedBatchId)
            .toList(growable: false);
        if (records.isEmpty) {
          return const _SheetEmpty(
            icon: Icons.call_split_outlined,
            title: '没有待分笼库存',
            message: '该批次没有剩余数量大于 0 的待分笼记录。',
          );
        }
        final selectedRecordId =
            records.any((item) => item.id == _recordId) ? _recordId : null;
        final record =
            records.where((item) => item.id == selectedRecordId).firstOrNull;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            DropdownButtonFormField<int>(
              key: const ValueKey('production-weaning-record'),
              value: selectedRecordId,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: '待分笼记录',
                hintText: '请选择待分笼记录',
              ),
              items: [
                for (final item in records)
                  DropdownMenuItem(
                    key: ValueKey('production-record-option-${item.id}'),
                    value: item.id,
                    child: Text(
                      '母兔 #${item.rabbitId} · 待分 ${item.waitingCount} 只',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged:
                  _saving ? null : (value) => _selectRecord(value, records),
            ),
            if (record != null) ...[
              const SizedBox(height: 14),
              _buildRecordFacts(record),
              const SizedBox(height: 14),
              _buildCageField(cages),
              const SizedBox(height: 12),
              TextField(
                key: const ValueKey('production-count'),
                controller: _countController,
                enabled: !_saving,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                onChanged: (_) => _startNewDraft(),
                decoration: InputDecoration(
                  labelText: '本次分笼数量',
                  helperText: '剩余 ${record.waitingCount} 只，可部分分笼',
                ),
              ),
              if (record.hasTrustworthyWaitingGenderCounts) ...[
                const SizedBox(height: 12),
                _GenderFields(
                  maleController: _maleController,
                  femaleController: _femaleController,
                  enabled: !_saving,
                  waitingMaleCount: record.waitingMaleCount!,
                  waitingFemaleCount: record.waitingFemaleCount!,
                  onChanged: _startNewDraft,
                ),
              ],
              const SizedBox(height: 16),
              _buildParentField(
                key: const ValueKey('production-mother'),
                label: '关联母兔（选填）',
                candidates: _parentCandidates(
                  parents,
                  gender: '0',
                  recommendedId: record.rabbitId,
                ),
                value: _motherRabbitId,
                recommendedId: record.rabbitId,
                onChanged: (value) {
                  setState(() {
                    _motherRabbitId = value;
                    _startNewDraft();
                  });
                },
              ),
              const SizedBox(height: 12),
              _buildParentField(
                key: const ValueKey('production-father'),
                label: '关联公兔（选填）',
                candidates: _parentCandidates(
                  parents,
                  gender: '1',
                  recommendedId: record.sireRabbitId,
                ),
                value: _fatherRabbitId,
                recommendedId: record.sireRabbitId,
                onChanged: (value) {
                  setState(() {
                    _fatherRabbitId = value;
                    _startNewDraft();
                  });
                },
              ),
              const SizedBox(height: 20),
              _buildActions(
                onSubmit: () => _submit(
                  batches: batches,
                  records: records,
                  cages: cages,
                  parentCandidates: parents,
                ),
              ),
            ],
          ],
        );
      },
    );
  }

  Widget _buildRecordFacts(PendingWeaningRecord record) {
    final date = record.weaningDate == null
        ? '日期未记录'
        : DateFormat('yyyy-MM-dd')
            .format(farmLocalDateTime(record.weaningDate!));
    final genderFacts = record.hasTrustworthyWaitingGenderCounts
        ? ' · 剩余公 ${record.waitingMaleCount} / 母 ${record.waitingFemaleCount}'
        : ' · 剩余公母未知';
    return InfoNotice(
      icon: Icons.fact_check_outlined,
      text: '来源母兔 #${record.rabbitId} · 周期 #${record.breedingCycleId ?? '-'}\n'
          '$date · 断奶 ${record.weaningCount} 只 · 待分 ${record.waitingCount} 只$genderFacts',
    );
  }

  Widget _buildCageField(List<Cage> allCages) {
    final currentCageId = widget.currentCage?.id;
    if (currentCageId != null) {
      final cage =
          allCages.where((item) => item.id == currentCageId).firstOrNull;
      final label = cage == null ? '#$currentCageId' : _cageLabel(cage);
      return InfoNotice(
        key: const ValueKey('production-current-cage'),
        icon: Icons.home_work_outlined,
        text: '当前笼位：$label',
      );
    }

    final cages = allCages.where((cage) => cage.isProductionIntakeCage).toList()
      ..sort((left, right) => left.cageNumber.compareTo(right.cageNumber));
    if (cages.isEmpty) {
      return const InfoNotice(
        icon: Icons.info_outline,
        text: '当前没有启用且有容量的通用空笼或商品兔笼。',
      );
    }
    final selected = cages.any((item) => item.id == _cageId) ? _cageId : null;
    return DropdownButtonFormField<int>(
      key: const ValueKey('production-cage'),
      value: selected,
      isExpanded: true,
      decoration: const InputDecoration(
        labelText: '目标笼位',
        hintText: '请选择笼位',
      ),
      items: [
        for (final cage in cages)
          DropdownMenuItem(
            value: cage.id,
            child: Text(
              _cageLabel(cage),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
      onChanged: _saving
          ? null
          : (value) {
              setState(() {
                _cageId = value;
                _startNewDraft();
              });
            },
    );
  }

  String _cageLabel(Cage cage) {
    final name = cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
    return '$name · ${cage.usageLabel} · 还可放 ${cage.commodityRemainingCapacity} 只';
  }

  List<Rabbit> _parentCandidates(
    List<Rabbit> rabbits, {
    required String gender,
    required int? recommendedId,
  }) {
    final result = rabbits
        .where(
          (rabbit) =>
              rabbit.houseId == widget.houseId &&
              rabbit.type == '0' &&
              rabbit.gender == gender,
        )
        .toList();
    result.sort((left, right) {
      final leftRecommended = left.id == recommendedId ? 0 : 1;
      final rightRecommended = right.id == recommendedId ? 0 : 1;
      final recommended = leftRecommended.compareTo(rightRecommended);
      if (recommended != 0) return recommended;
      final active = (right.isActive ? 1 : 0).compareTo(left.isActive ? 1 : 0);
      if (active != 0) return active;
      return left.id.compareTo(right.id);
    });
    return result;
  }

  Widget _buildParentField({
    required Key key,
    required String label,
    required List<Rabbit> candidates,
    required int? value,
    required int? recommendedId,
    required ValueChanged<int?> onChanged,
  }) {
    final selected = candidates.any((item) => item.id == value) ? value : null;
    return DropdownButtonFormField<int?>(
      key: key,
      value: selected,
      isExpanded: true,
      decoration: InputDecoration(labelText: label),
      items: [
        const DropdownMenuItem<int?>(
          value: null,
          child: Text('不关联'),
        ),
        for (final rabbit in candidates)
          DropdownMenuItem<int?>(
            value: rabbit.id,
            child: Text(
              '兔 #${rabbit.id} · ${rabbit.isActive ? '在场' : '已离场'}'
              '${rabbit.id == recommendedId ? ' · 推荐' : ''}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
      onChanged: _saving ? null : onChanged,
    );
  }

  Widget _buildActions({required VoidCallback onSubmit}) {
    final palette = AppPalette.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border(top: BorderSide(color: palette.line)),
      ),
      child: Padding(
        padding: const EdgeInsets.only(top: 12),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _saving ? null : () => Navigator.of(context).pop(),
                child: const Text('取消'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                key: const ValueKey('production-submit'),
                onPressed: _saving ? null : onSubmit,
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('确认分笼'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GenderFields extends StatelessWidget {
  const _GenderFields({
    required this.maleController,
    required this.femaleController,
    required this.enabled,
    required this.waitingMaleCount,
    required this.waitingFemaleCount,
    required this.onChanged,
  });

  final TextEditingController maleController;
  final TextEditingController femaleController;
  final bool enabled;
  final int waitingMaleCount;
  final int waitingFemaleCount;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final fields = <Widget>[
          TextField(
            key: const ValueKey('production-male-count'),
            controller: maleController,
            enabled: enabled,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            onChanged: (_) => onChanged(),
            decoration: InputDecoration(
              labelText: '公兔数量',
              helperText: '剩余 $waitingMaleCount 只',
            ),
          ),
          TextField(
            key: const ValueKey('production-female-count'),
            controller: femaleController,
            enabled: enabled,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            onChanged: (_) => onChanged(),
            decoration: InputDecoration(
              labelText: '母兔数量',
              helperText: '剩余 $waitingFemaleCount 只',
            ),
          ),
        ];
        if (constraints.maxWidth < 420) {
          return Column(
            children: [fields.first, const SizedBox(height: 12), fields.last],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: fields.first),
            const SizedBox(width: 10),
            Expanded(child: fields.last),
          ],
        );
      },
    );
  }
}

class _LoadFailure extends StatelessWidget {
  const _LoadFailure({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline),
          const SizedBox(height: 10),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            key: const ValueKey('production-retry'),
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
    );
  }
}

class _SheetEmpty extends StatelessWidget {
  const _SheetEmpty({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon),
          const SizedBox(height: 10),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(message, textAlign: TextAlign.center),
        ],
      ),
    );
  }
}
