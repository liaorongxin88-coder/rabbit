import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_entry.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/separation.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/cage_target.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

const _growthStageOptions = <_StageOption>[
  _StageOption('ADAPTATION', '适应期'),
  _StageOption('GROWING', '成长期'),
  _StageOption('FATTENING', '育肥期'),
  _StageOption('MATURE', '成熟可售'),
];

const _buckReproductiveStageOptions = <_StageOption>[
  _StageOption('READY', '可配'),
  _StageOption('RESTING', '休整'),
];

const _replacementReproductiveStageOptions = <_StageOption>[
  _StageOption('RESERVE', '后备'),
];

List<Rabbit> _availableBreedingMales(
  Iterable<Rabbit> rabbits, {
  required int houseId,
}) {
  final result = rabbits
      .where(
        (rabbit) =>
            rabbit.id > 0 &&
            rabbit.houseId == houseId &&
            rabbit.isActive &&
            rabbit.type == '0' &&
            rabbit.gender == '1',
      )
      .toList();
  result.sort((left, right) => left.id.compareTo(right.id));
  return result;
}

List<Batch> _inProgressBatches(Iterable<Batch> batches) {
  final result = batches
      .where((batch) => batch.id > 0 && batch.status.trim() == '进行中')
      .toList();
  result.sort((left, right) => right.id.compareTo(left.id));
  return result;
}

Future<void> showRabbitIntakeSheet({
  required BuildContext context,
  required int houseId,
  required Cage cage,
  required HousePermission permission,
}) async {
  final choice = await showAppModalSheet<_RabbitIntakeChoice>(
    context: context,
    builder: (_) => _RabbitSourceSheet(
      houseId: houseId,
      cage: cage,
      permission: permission,
    ),
  );
  if (choice == null || !context.mounted) {
    return;
  }

  if (choice.source == _RabbitIntakeSource.purchase) {
    await showRabbitPurchaseEntrySheet(
      context: context,
      houseId: houseId,
      cage: choice.cage,
    );
    return;
  }

  final result = await showProductionWeaningSeparationSheet(
    context: context,
    houseId: houseId,
    currentCage: choice.cage,
  );
  if (result != null && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          '已分笼 ${result.separatedCount} 只，剩余 ${result.waitingCount} 只',
        ),
      ),
    );
  }
}

Future<void> showRabbitPurchaseEntrySheet({
  required BuildContext context,
  required int houseId,
  required Cage cage,
}) async {
  final type = await showAppModalSheet<String>(
    context: context,
    builder: (_) => _RabbitTypeSheet(cage: cage),
  );
  if (type == null || !context.mounted) {
    return;
  }
  await showAppModalSheet<void>(
    context: context,
    builder: (_) => _CreateRabbitSheet(
      houseId: houseId,
      cage: cage,
      initialType: type,
    ),
  );
}

enum _RabbitIntakeSource { purchase, production }

class _RabbitIntakeChoice {
  const _RabbitIntakeChoice({required this.source, required this.cage});

  final _RabbitIntakeSource source;
  final Cage cage;
}

class _RabbitSourceSheet extends ConsumerStatefulWidget {
  const _RabbitSourceSheet({
    required this.houseId,
    required this.cage,
    required this.permission,
  });

  final int houseId;
  final Cage cage;
  final HousePermission permission;

  @override
  ConsumerState<_RabbitSourceSheet> createState() => _RabbitSourceSheetState();
}

class _RabbitSourceSheetState extends ConsumerState<_RabbitSourceSheet> {
  AsyncValue<Cage?> _productionCage = const AsyncValue.data(null);

  bool get _canUseProduction =>
      widget.permission.canQueryBatches && widget.permission.canEditBatches;

  @override
  void initState() {
    super.initState();
    if (_canUseProduction) {
      _productionCage = const AsyncValue.loading();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _refreshProductionCage();
      });
    }
  }

  Future<void> _refreshProductionCage() async {
    setState(() => _productionCage = const AsyncValue.loading());
    try {
      final cages =
          await ref.refresh(houseCagesProvider(widget.houseId).future);
      final cage = cages.where((item) => item.id == widget.cage.id).firstOrNull;
      if (!mounted) return;
      setState(() => _productionCage = AsyncValue.data(cage));
    } catch (error, stackTrace) {
      if (!mounted) return;
      setState(
        () => _productionCage = AsyncValue.error(error, stackTrace),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final freshCage = _productionCage.valueOrNull;
    final productionEnabled =
        _canUseProduction && freshCage?.isProductionIntakeCage == true;
    final cageName = widget.cage.cageNumber.isEmpty
        ? '#${widget.cage.id}'
        : widget.cage.cageNumber;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('选择兔子录入方式', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 6),
            Text(
              '$cageName · ${widget.cage.usageLabel} · ${widget.cage.rabbitCount} 只',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              key: const ValueKey('rabbit-intake-purchase'),
              onPressed: widget.permission.canAddRabbit
                  ? () => Navigator.of(context).pop(
                        _RabbitIntakeChoice(
                          source: _RabbitIntakeSource.purchase,
                          cage: freshCage ?? widget.cage,
                        ),
                      )
                  : null,
              icon: const Icon(Icons.add_business_outlined),
              label: const Text('自定义兔子录入'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              key: const ValueKey('rabbit-intake-production'),
              onPressed: productionEnabled
                  ? () => Navigator.of(context).pop(
                        _RabbitIntakeChoice(
                          source: _RabbitIntakeSource.production,
                          cage: freshCage!,
                        ),
                      )
                  : null,
              icon: _productionCage.isLoading
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.call_split_outlined),
              label: const Text('从批次中录入商品兔'),
            ),
            if (!_canUseProduction) ...[
              const SizedBox(height: 8),
              const Text('从批次中录入商品兔需要批次查看和编辑权限。'),
            ] else if (_productionCage.hasError) ...[
              const SizedBox(height: 8),
              TextButton.icon(
                key: const ValueKey('rabbit-intake-cage-retry'),
                onPressed: _refreshProductionCage,
                icon: const Icon(Icons.refresh),
                label: const Text('笼位刷新失败，重新读取'),
              ),
            ] else if (!_productionCage.isLoading && !productionEnabled) ...[
              const SizedBox(height: 8),
              const Text('从批次中录入商品兔仅可进入启用且有容量的通用空笼或商品兔笼。'),
            ],
          ],
        ),
      ),
    );
  }
}

Future<void> showRabbitEditSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  required List<Cage> cages,
}) {
  return showAppModalSheet<void>(
    context: context,
    builder: (context) => _CreateRabbitSheet.edit(
      houseId: houseId,
      rabbit: rabbit,
    ),
  );
}

/// 将尚无活动繁育管线的种母兔接入生产流程；未分笼的哺乳窝不占管线。
///
/// 这是生产动作，不复用兔档案编辑接口；阶段和必填事实完全来自服务端入轨字典。
Future<ReproActionResult?> showRabbitReproEntrySheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  String? initialStage,
}) {
  if (houseId <= 0 ||
      rabbit.id <= 0 ||
      rabbit.type != '0' ||
      rabbit.gender != '0') {
    return Future<ReproActionResult?>.value();
  }
  return showAppModalSheet<ReproActionResult>(
    context: context,
    builder: (context) => _ExistingRabbitReproEntrySheet(
      houseId: houseId,
      rabbit: rabbit,
      initialStage: initialStage,
    ),
  );
}

class _ExistingRabbitReproEntrySheet extends ConsumerStatefulWidget {
  const _ExistingRabbitReproEntrySheet({
    required this.houseId,
    required this.rabbit,
    this.initialStage,
  });

  final int houseId;
  final Rabbit rabbit;
  final String? initialStage;

  @override
  ConsumerState<_ExistingRabbitReproEntrySheet> createState() =>
      _ExistingRabbitReproEntrySheetState();
}

class _ExistingRabbitReproEntrySheetState
    extends ConsumerState<_ExistingRabbitReproEntrySheet> {
  final _totalKitsController = TextEditingController();
  final _liveKitsController = TextEditingController();
  final _keptKitsController = TextEditingController();
  String? _stage;
  int? _batchId;
  DateTime? _stageEnteredAt;
  DateTime? _matingDate;
  DateTime? _birthDate;
  int? _maleRabbitId;
  MatingMethod _matingMethod = MatingMethod.natural;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _stage = widget.initialStage;
    if (_stage != null) {
      _stageEnteredAt = _farmToday();
    }
  }

  @override
  void dispose() {
    _totalKitsController.dispose();
    _liveKitsController.dispose();
    _keptKitsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final entriesAsync = ref.watch(reproEntryPointsProvider(widget.houseId));
    final batchesAsync = ref.watch(houseBatchesProvider(widget.houseId));
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: mediaQuery.size.height - keyboardInset,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '开始繁育 / 生产阶段入轨',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '母兔 #${widget.rabbit.id} · 入轨前请选择进行中的批次',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      tooltip: '关闭',
                      onPressed:
                          _saving ? null : () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
              ),
              Flexible(
                child: SingleChildScrollView(
                  key: const ValueKey('existing-rabbit-repro-entry-list'),
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                  child: entriesAsync.when(
                    loading: () => const InfoNotice(
                      icon: Icons.hourglass_empty,
                      text: '正在读取可入轨的生产阶段…',
                    ),
                    error: (error, _) => InfoNotice(
                      icon: Icons.error_outline,
                      text: '生产阶段读取失败：$error',
                    ),
                    data: (entries) =>
                        _buildEntryFields(entries, batchesAsync, rabbitsAsync),
                  ),
                ),
              ),
              _buildSubmitBar(entriesAsync.valueOrNull ?? const []),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEntryFields(
    List<ReproEntryPoint> entries,
    AsyncValue<List<Batch>> batchesAsync,
    AsyncValue<List<Rabbit>> rabbitsAsync,
  ) {
    if (entries.isEmpty) {
      return const InfoNotice(
        icon: Icons.info_outline,
        text: '当前没有可用的生产阶段，请刷新后重试。',
      );
    }
    final selected = _selectedEntryPoint(entries);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionLabel('生产阶段入轨'),
        const SizedBox(height: 6),
        Text(
          '选择母兔当前真实阶段；系统会据此创建周期和第一条待办。',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 10),
        DropdownButtonFormField<String>(
          key: const ValueKey('existing-rabbit-repro-stage'),
          value: _stage,
          isExpanded: true,
          decoration: const InputDecoration(
            labelText: '生产阶段',
            hintText: '请选择生产阶段',
          ),
          items: [
            for (final entry in entries)
              DropdownMenuItem(
                value: entry.stage,
                child: Text(entry.stageLabel),
              ),
          ],
          onChanged: _saving ? null : _setStage,
        ),
        if (selected != null) ...[
          const SizedBox(height: 12),
          _buildBatchField(
            batchesAsync,
            required: selected.batchRequired,
          ),
          const SizedBox(height: 12),
          _buildDateField(
            key: const ValueKey('existing-rabbit-stage-entered-at'),
            label: '进入该阶段日期',
            value: _stageEnteredAt,
            onPicked: (value) => setState(() => _stageEnteredAt = value),
          ),
          if (selected.needsMatingDate) ...[
            const SizedBox(height: 12),
            _buildDateField(
              key: const ValueKey('existing-rabbit-mating-date'),
              label: '配种日期',
              value: _matingDate,
              onPicked: (value) => setState(() => _matingDate = value),
            ),
          ],
          if (selected.requires('BIRTH_DATE')) ...[
            const SizedBox(height: 12),
            _buildDateField(
              key: const ValueKey('existing-rabbit-birth-date'),
              label: '分娩日期',
              value: _birthDate,
              onPicked: (value) => setState(() => _birthDate = value),
            ),
          ],
          if (selected.requires('MALE_RABBIT') ||
              selected.requires('MATING_METHOD')) ...[
            const SizedBox(height: 12),
            _buildMatingFields(rabbitsAsync),
          ],
          if (selected.requires('TOTAL_KITS')) ...[
            const SizedBox(height: 12),
            _buildCountField(
              key: const ValueKey('existing-rabbit-total-kits'),
              label: '产仔数',
              controller: _totalKitsController,
            ),
          ],
          if (selected.requires('LIVE_KITS')) ...[
            const SizedBox(height: 12),
            _buildCountField(
              key: const ValueKey('existing-rabbit-live-kits'),
              label: '活仔数',
              controller: _liveKitsController,
            ),
          ],
          if (selected.requires('KEPT_KITS')) ...[
            const SizedBox(height: 12),
            _buildCountField(
              key: const ValueKey('existing-rabbit-kept-kits'),
              label: '留仔数',
              controller: _keptKitsController,
            ),
          ],
        ],
      ],
    );
  }

  Widget _buildMatingFields(AsyncValue<List<Rabbit>> rabbitsAsync) {
    return rabbitsAsync.when(
      loading: () => const InfoNotice(
        icon: Icons.hourglass_empty,
        text: '正在读取可用种公兔…',
      ),
      error: (_, __) => const InfoNotice(
        icon: Icons.error_outline,
        text: '种公兔读取失败，请刷新后重试。',
      ),
      data: (rabbits) {
        final males = _availableBreedingMales(
          rabbits,
          houseId: widget.houseId,
        );
        return Column(
          children: [
            DropdownButtonFormField<MatingMethod>(
              key: const ValueKey('existing-rabbit-mating-method'),
              value: _matingMethod,
              decoration: const InputDecoration(labelText: '配种方式'),
              items: [
                for (final method in MatingMethod.values)
                  DropdownMenuItem(value: method, child: Text(method.label)),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(
                        () => _matingMethod = value ?? _matingMethod,
                      ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              key: const ValueKey('existing-rabbit-mating-male'),
              value: males.any((rabbit) => rabbit.id == _maleRabbitId)
                  ? _maleRabbitId
                  : null,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: '配种公兔',
                hintText: '请选择种公兔',
              ),
              items: [
                for (final male in males)
                  DropdownMenuItem(
                    value: male.id,
                    child: Text(
                      '兔 #${male.id} · ${male.breed.isEmpty ? '未填品种' : male.breed}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _maleRabbitId = value),
            ),
          ],
        );
      },
    );
  }

  Widget _buildCountField({
    required Key key,
    required String label,
    required TextEditingController controller,
  }) {
    return TextField(
      key: key,
      controller: controller,
      enabled: !_saving,
      decoration: InputDecoration(labelText: label),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
    );
  }

  Widget _buildDateField({
    required Key key,
    required String label,
    required DateTime? value,
    required ValueChanged<DateTime> onPicked,
  }) {
    return InputDecorator(
      key: key,
      decoration: InputDecoration(labelText: label),
      child: InkWell(
        onTap: _saving
            ? null
            : () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: value == null ? _farmToday() : _dateOnly(value),
                  firstDate: DateTime(2020),
                  lastDate: _farmToday().add(const Duration(days: 1)),
                  helpText: label,
                  cancelText: '取消',
                  confirmText: '确定',
                );
                if (picked != null) {
                  onPicked(_dateOnly(picked));
                }
              },
        child: Row(
          children: [
            Expanded(
              child: Text(
                value == null
                    ? '未选择'
                    : DateFormat('yyyy-MM-dd').format(_dateOnly(value)),
              ),
            ),
            const Icon(Icons.calendar_today_outlined, size: 18),
          ],
        ),
      ),
    );
  }

  Widget _buildBatchField(
    AsyncValue<List<Batch>> batchesAsync, {
    required bool required,
  }) {
    return batchesAsync.when(
      loading: () => const InfoNotice(
        icon: Icons.hourglass_empty,
        text: '正在读取进行中的批次…',
      ),
      error: (_, __) => const InfoNotice(
        icon: Icons.error_outline,
        text: '批次读取失败，请刷新后重试。',
      ),
      data: (batches) {
        final activeBatches = _inProgressBatches(batches);
        if (activeBatches.isEmpty) {
          return const InfoNotice(
            icon: Icons.info_outline,
            text: '当前没有进行中的批次，请先创建批次再入轨。',
          );
        }
        final selectedId = activeBatches.any((batch) => batch.id == _batchId)
            ? _batchId
            : null;
        return DropdownButtonFormField<int>(
          key: const ValueKey('existing-rabbit-repro-batch'),
          value: selectedId,
          isExpanded: true,
          decoration: InputDecoration(
            labelText: required ? '生产批次' : '计划批次（可选）',
            hintText: required ? '请选择进行中的生产批次' : '可先选择计划进入的批次',
          ),
          items: [
            for (final batch in activeBatches)
              DropdownMenuItem(
                value: batch.id,
                child: Text(
                  batch.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
          onChanged:
              _saving ? null : (value) => setState(() => _batchId = value),
        );
      },
    );
  }

  Widget _buildSubmitBar(List<ReproEntryPoint> entries) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AppPalette.of(context).surface,
        border: Border(top: BorderSide(color: AppPalette.of(context).line)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
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
                key: const ValueKey('existing-rabbit-repro-submit'),
                onPressed:
                    _saving || entries.isEmpty ? null : () => _submit(entries),
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('确认入轨'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit(List<ReproEntryPoint> entries) async {
    final selected = _selectedEntryPoint(entries);
    final stage = ReproStage.tryParse(_stage);
    if (selected == null || stage == null) {
      _showMessage('请选择生产阶段');
      return;
    }
    final batchId = _selectedInProgressBatchId();
    if (selected.batchRequired && batchId == null) {
      _showMessage('请选择进行中的批次');
      return;
    }
    final missing = _missingEntryFact(selected);
    if (missing != null) {
      _showMessage(missing);
      return;
    }

    setState(() => _saving = true);
    try {
      final result = await ref.read(reproRepositoryProvider).openCycle(
            houseId: widget.houseId,
            motherRabbitId: widget.rabbit.id,
            batchId: batchId,
            stage: stage,
            occurredAt: _stageEnteredAt,
            matingDate: _matingDate,
            birthDate: _birthDate,
            totalKits: int.tryParse(_totalKitsController.text.trim()),
            liveKits: int.tryParse(_liveKitsController.text.trim()),
            keptKits: int.tryParse(_keptKitsController.text.trim()),
            maleRabbitId: _maleRabbitId,
            matingMethod: _matingMethod,
          );
      if (!mounted) {
        return;
      }
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(
        rabbitReproTasksProvider(
          RabbitReproTasksRequest(
            houseId: widget.houseId,
            rabbitId: widget.rabbit.id,
          ),
        ),
      );
      ref.invalidate(homeEventsProvider);
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(result);
      messenger?.showSnackBar(
        SnackBar(
            content:
                Text('母兔 #${widget.rabbit.id} 已从【${selected.stageLabel}】入轨')),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      _showMessage(error is ApiException ? error.message : error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  void _setStage(String? value) {
    setState(() {
      if (_stage != value) {
        _batchId = null;
        _matingDate = null;
        _birthDate = null;
        _maleRabbitId = null;
        _matingMethod = MatingMethod.natural;
        _totalKitsController.clear();
        _liveKitsController.clear();
        _keptKitsController.clear();
      }
      _stage = value;
      _stageEnteredAt = value == null ? null : _farmToday();
    });
  }

  int? _selectedInProgressBatchId() {
    final batchId = _batchId;
    final batches = ref.read(houseBatchesProvider(widget.houseId)).valueOrNull;
    if (batchId == null || batches == null) {
      return null;
    }
    return _inProgressBatches(batches).any((batch) => batch.id == batchId)
        ? batchId
        : null;
  }

  ReproEntryPoint? _selectedEntryPoint(List<ReproEntryPoint> entries) {
    final stage = _stage;
    if (stage == null) {
      return null;
    }
    for (final entry in entries) {
      if (entry.stage == stage) {
        return entry;
      }
    }
    return null;
  }

  String? _missingEntryFact(ReproEntryPoint selected) {
    for (final fact in selected.requiredFacts) {
      final filled = switch (fact.fact) {
        'STAGE_ENTERED_AT' => _stageEnteredAt != null,
        'MATING_DATE' || 'GESTATION_ANCHOR' => _matingDate != null,
        'BIRTH_DATE' => _birthDate != null,
        'MALE_RABBIT' => _maleRabbitId != null,
        'MATING_METHOD' => true,
        'TOTAL_KITS' => _totalKitsController.text.trim().isNotEmpty,
        'LIVE_KITS' => _liveKitsController.text.trim().isNotEmpty,
        'KEPT_KITS' => _keptKitsController.text.trim().isNotEmpty,
        _ => true,
      };
      if (!filled) {
        return '从【${selected.stageLabel}】入轨需要补录${fact.label}';
      }
    }
    return null;
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class _RabbitTypeSheet extends ConsumerStatefulWidget {
  const _RabbitTypeSheet({required this.cage});

  final Cage cage;

  @override
  ConsumerState<_RabbitTypeSheet> createState() => _RabbitTypeSheetState();
}

class _RabbitTypeSheetState extends ConsumerState<_RabbitTypeSheet> {
  late String _type;

  String get _cageName => widget.cage.cageNumber.isEmpty
      ? '#${widget.cage.id}'
      : widget.cage.cageNumber;

  @override
  void initState() {
    super.initState();
    _type = widget.cage.preferredRabbitType;
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('请选择录入兔子类型', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 6),
          Text(
            '$_cageName · ${widget.cage.usageLabel} · ${widget.cage.rabbitCount} 只',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 22),
          Wrap(
            spacing: 14,
            runSpacing: 10,
            children: [
              _RabbitTypeChoice(
                value: '0',
                groupValue: _type,
                label: '种公兔/种母兔',
                onChanged: _handleTypeChanged,
              ),
              _RabbitTypeChoice(
                value: '1',
                groupValue: _type,
                label: '后备兔',
                onChanged: _handleTypeChanged,
              ),
              _RabbitTypeChoice(
                value: '2',
                groupValue: _type,
                label: '商品兔',
                onChanged: _handleTypeChanged,
              ),
            ],
          ),
          if (widget.cage.status != '0' &&
              widget.cage.preferredRabbitType != _type) ...[
            const SizedBox(height: 14),
            _CageTypeWarning(cage: widget.cage),
          ],
          if (!widget.cage.acceptsMoreRabbits) ...[
            const SizedBox(height: 14),
            _CageCapacityWarning(cage: widget.cage),
          ],
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('取消'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(
                  onPressed: widget.cage.acceptsMoreRabbits ? _continue : null,
                  child: const Text('确定'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _handleTypeChanged(String value) {
    setState(() => _type = value);
  }

  void _continue() {
    final blocked = widget.cage.entryBlockedReason;
    if (blocked != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(blocked)),
      );
      return;
    }
    // 只把选定的类型交回给发起方，录入表单由它 await。
    Navigator.of(context).pop(_type);
  }
}

class _CreateRabbitSheet extends ConsumerStatefulWidget {
  const _CreateRabbitSheet({
    required this.houseId,
    required this.cage,
    required this.initialType,
  }) : rabbit = null;

  const _CreateRabbitSheet.edit({
    required this.houseId,
    required this.rabbit,
  })  : cage = null,
        initialType = '';

  final int houseId;
  final Cage? cage;
  final String initialType;
  final Rabbit? rabbit;

  @override
  ConsumerState<_CreateRabbitSheet> createState() => _CreateRabbitSheetState();
}

class _CreateRabbitSheetState extends ConsumerState<_CreateRabbitSheet> {
  static const _uuid = Uuid();

  final _formKey = GlobalKey<FormState>();
  final _breedController = TextEditingController();
  final _weightController = TextEditingController();
  final _quantityController = TextEditingController(text: '1');
  final _sourceSellerController = TextEditingController();
  final _motherIdController = TextEditingController();
  final _totalKitsController = TextEditingController();
  final _liveKitsController = TextEditingController();
  final _keptKitsController = TextEditingController();
  late String _type;
  var _gender = '0';
  var _arrivalMethod = '0';
  DateTime? _arrivalDate;
  String? _growthStage;
  DateTime? _growthStageEnteredAt;
  String? _reproductiveStage;

  /// 录入时直接入轨的生产阶段（飞书 recvsrnEJ8bKrk）。null 表示暂不入轨。
  String? _reproStage;
  int? _batchId;
  DateTime? _stageEnteredAt;
  DateTime? _matingDate;
  DateTime? _birthDate;
  int? _entryMaleRabbitId;
  MatingMethod _entryMatingMethod = MatingMethod.natural;
  StreamSubscription<NfcLaunchEvent>? _nfcSubscription;
  StateController<bool>? _captureFlag;
  BatchRabbitEntryResult? _batchEntryResult;
  String? _batchRequestId;
  String? _nfcHint;
  String? _submitError;
  var _nfcListening = false;
  var _saving = false;

  bool get _isEdit => widget.rabbit != null;

  int get _quantity => int.tryParse(_quantityController.text.trim()) ?? 0;

  bool get _isBatchEntry => !_isEdit && _quantity > 1;

  Rabbit get _rabbit => widget.rabbit!;

  Cage get _createCage => widget.cage!;

  @override
  void initState() {
    super.initState();
    final rabbit = widget.rabbit;
    _arrivalDate = rabbit?.arrivalDate == null
        ? _farmToday()
        : _dateOnly(rabbit!.arrivalDate!);
    _growthStageEnteredAt =
        rabbit == null && widget.initialType == '1' ? _arrivalDate : null;
    if (rabbit == null) {
      _type = widget.initialType;
      _growthStage = null;
      _reproductiveStage = _type == '1' ? 'RESERVE' : null;
    } else {
      _type = rabbit.type;
      _gender = rabbit.gender;
      _arrivalMethod =
          rabbit.arrivalMethod.isEmpty ? '0' : rabbit.arrivalMethod;
      _sourceSellerController.text = rabbit.sourceSeller;
      _motherIdController.text = rabbit.motherId?.toString() ?? '';
      _breedController.text = rabbit.breed;
      final weight = rabbit.weight;
      if (weight != null && weight > 0) {
        _weightController.text = weight.toStringAsFixed(2);
      }
      _growthStage = _matchingStage(rabbit.growthStage, _growthStageOptions);
      _reproductiveStage = switch (_type) {
        '2' => null,
        '1' => 'RESERVE',
        _ => _matchingStage(
            rabbit.reproductiveStage,
            _reproductiveStageOptions,
          ),
      };
    }
    if (!_isEdit && _type == '0') {
      _gender = '0';
    }
  }

  @override
  void dispose() {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseNfcCapture();
    _breedController.dispose();
    _weightController.dispose();
    _quantityController.dispose();
    _sourceSellerController.dispose();
    _motherIdController.dispose();
    _totalKitsController.dispose();
    _liveKitsController.dispose();
    _keptKitsController.dispose();
    super.dispose();
  }

  void _releaseNfcCapture() {
    _captureFlag?.state = false;
    _captureFlag = null;
  }

  Future<void> _startMotherNfcCapture() async {
    if (_nfcListening || _saving) {
      return;
    }
    try {
      final service = ref.read(nfcIntentServiceProvider);
      await service.initialize();
      if (!mounted) {
        return;
      }
      final flag = ref.read(nfcCaptureActiveProvider.notifier);
      flag.state = true;
      _captureFlag = flag;
      setState(() {
        _nfcListening = true;
        _nfcHint = '请将手机靠近母兔所在笼位的 NFC 标签';
      });
      _nfcSubscription = service.events.listen(_onMotherNfcEvent);
    } catch (_) {
      if (mounted) {
        setState(() => _nfcHint = 'NFC 暂不可用，请手动输入母兔 ID');
      }
    }
  }

  void _stopMotherNfcCapture({String? hint}) {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseNfcCapture();
    if (!mounted) {
      return;
    }
    setState(() {
      _nfcListening = false;
      _nfcHint = hint;
    });
  }

  Future<void> _onMotherNfcEvent(NfcLaunchEvent event) async {
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      if (target.houseId != widget.houseId) {
        _stopMotherNfcCapture(hint: '该标签属于其他兔舍，未关联母兔');
        return;
      }
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: widget.houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      final rabbits =
          await ref.read(rabbitRepositoryProvider).listRabbitsForCage(
                houseId: widget.houseId,
                cageId: binding.cageId,
              );
      if (!mounted) {
        return;
      }
      final mothers = rabbits
          .where(
            (rabbit) =>
                rabbit.id > 0 &&
                rabbit.isActive &&
                rabbit.type == '0' &&
                rabbit.gender == '0',
          )
          .toList();
      if (mothers.length != 1) {
        _stopMotherNfcCapture(
          hint: mothers.isEmpty
              ? '该笼位没有在栏种母兔，请手动输入母兔 ID'
              : '该笼位有多只种母兔，请手动输入母兔 ID',
        );
        return;
      }
      final mother = mothers.single;
      _motherIdController.text = '${mother.id}';
      _batchRequestId = null;
      _batchEntryResult = null;
      _submitError = null;
      _stopMotherNfcCapture(hint: '已关联母兔 #${mother.id}');
    } catch (error) {
      _stopMotherNfcCapture(
        hint: error is ApiException ? error.message : '读取标签失败，请重试',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = (mediaQuery.size.height - keyboardInset).clamp(
      0.0,
      mediaQuery.size.height,
    );

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * 0.92),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(
                  child: SingleChildScrollView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _buildHeader(context),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                          child: _buildFormBody(context),
                        ),
                      ],
                    ),
                  ),
                ),
                _buildActionBar(context),
              ],
            ),
          ),
        ),
      ),
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
                  _isEdit ? '编辑兔子信息' : '新增兔子信息',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  _isEdit
                      ? '兔 #${_rabbit.id} · 当前笼位 #${_rabbit.cageId}'
                      : '笼位 ${_createCage.cageNumber.isEmpty ? '#${_createCage.id}' : _createCage.cageNumber}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
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

  Widget _buildFormBody(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_isEdit) ...[
          const _SectionLabel('当前档案'),
          const SizedBox(height: 8),
          InfoNotice(
            icon: Icons.info_outline,
            text:
                '${_rabbit.typeLabel} · ${_rabbit.genderLabel}。类型和性别涉及生产流程，需通过对应业务动作调整。',
          ),
          const SizedBox(height: 18),
        ] else ...[
          const _SectionLabel('性别'),
          const SizedBox(height: 8),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              _RadioChoice(
                value: '1',
                groupValue: _gender,
                label: '公',
                onChanged: _setGender,
              ),
              _RadioChoice(
                value: '0',
                groupValue: _gender,
                label: '母',
                onChanged: _setGender,
              ),
            ],
          ),
          const SizedBox(height: 18),
          InfoNotice(
            icon: Icons.category_outlined,
            text: '兔子类型：${_typeOptionLabel(_type)}（已在上一页选择）',
          ),
          const SizedBox(height: 18),
        ],
        if (_isEdit || _type != '2') ...[
          _buildStageFields(context),
          const SizedBox(height: 18),
        ],
        if (_isEdit) ...[
          const _SectionLabel('当前笼位'),
          const SizedBox(height: 8),
          InfoNotice(
            icon: Icons.home_work_outlined,
            text: '笼位 #${_rabbit.cageId}（只读）',
          ),
          const SizedBox(height: 18),
        ],
        _ResponsiveFieldRow(
          children: [
            TextFormField(
              controller: _breedController,
              key: const ValueKey('rabbit-entry-breed'),
              decoration: const InputDecoration(
                labelText: '品种',
                hintText: '请输入品种',
                counterText: '',
              ),
              maxLength: 100,
              textInputAction:
                  _isEdit ? TextInputAction.done : TextInputAction.next,
              validator: (value) {
                final text = value?.trim() ?? '';
                if (text.length > 100) {
                  return '品种不能超过 100 字';
                }
                if (_canOpenReproEntry && _reproStage != null && text.isEmpty) {
                  return '请填写种母兔品种';
                }
                return null;
              },
            ),
            if (!_isEdit) const SizedBox.shrink(),
          ],
        ),
        const SizedBox(height: 12),
        _buildDateField(
          key: const ValueKey('rabbit-arrival-date'),
          label: _type == '2' ? '断奶日期' : '入场日期',
          value: _arrivalDate,
          enabled: !_isEdit,
          onPicked: (value) => setState(() {
            if (_growthStageEnteredAt == _arrivalDate) {
              _growthStageEnteredAt = value;
            }
            _arrivalDate = value;
          }),
        ),
        const SizedBox(height: 12),
        _ResponsiveFieldRow(
          children: [
            DropdownButtonFormField<String>(
              key: const ValueKey('rabbit-entry-source-method'),
              value: _arrivalMethod,
              isExpanded: true,
              decoration: const InputDecoration(labelText: '来源方式'),
              items: const [
                DropdownMenuItem(value: '0', child: Text('购入')),
                DropdownMenuItem(value: '1', child: Text('自留')),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(() {
                        _arrivalMethod = value ?? _arrivalMethod;
                        if (_arrivalMethod == '0') {
                          _motherIdController.clear();
                        } else {
                          _sourceSellerController.clear();
                        }
                        _batchRequestId = null;
                        _batchEntryResult = null;
                        _submitError = null;
                      }),
            ),
            if (!_isEdit)
              TextFormField(
                key: const ValueKey('rabbit-entry-quantity'),
                controller: _quantityController,
                decoration: const InputDecoration(
                  labelText: '数量',
                  helperText: '单次最多 10 只',
                ),
                enabled: !_saving,
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.next,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                onChanged: (_) => setState(() {
                  _batchRequestId = null;
                  _batchEntryResult = null;
                  _submitError = null;
                }),
                validator: (value) {
                  final quantity = int.tryParse(value?.trim() ?? '');
                  if (quantity == null || quantity < 1 || quantity > 10) {
                    return '数量必须在 1 到 10 之间';
                  }
                  if (quantity > 1 && _type != '2') {
                    return '种兔和后备兔一次只能录入 1 只';
                  }
                  return null;
                },
              )
            else
              const SizedBox.shrink(),
          ],
        ),
        const SizedBox(height: 12),
        TextFormField(
          key: const ValueKey('rabbit-entry-weight'),
          controller: _weightController,
          decoration: InputDecoration(
            labelText: _isBatchEntry ? '总重量' : '体重',
            suffixText: 'kg',
          ),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          textInputAction: TextInputAction.next,
          inputFormatters: [
            FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}')),
          ],
          onChanged: (_) {
            if (_isBatchEntry) {
              setState(() {
                _batchRequestId = null;
                _batchEntryResult = null;
                _submitError = null;
              });
            }
          },
          validator: (value) {
            final weight = double.tryParse(value?.trim() ?? '');
            if (_isBatchEntry) {
              if (weight == null || weight <= 0) {
                return '请填写总重量';
              }
              return weight > 100 ? '总重量不能超过 100 kg' : null;
            }
            if (!_canOpenReproEntry || _reproStage == null) {
              return null;
            }
            return weight == null || weight <= 0 ? '请填写种母兔体重' : null;
          },
        ),
        if (!_isEdit && _arrivalMethod == '0') ...[
          const SizedBox(height: 12),
          TextFormField(
            key: const ValueKey('rabbit-entry-source-seller'),
            controller: _sourceSellerController,
            decoration: const InputDecoration(labelText: '供应方'),
            maxLength: 120,
            validator: (value) {
              if (_reproStage != null &&
                  (value == null || value.trim().isEmpty)) {
                return '请填写购入种母兔的供应方';
              }
              return null;
            },
          ),
        ],
        if (!_isEdit && _arrivalMethod == '1') ...[
          const SizedBox(height: 12),
          TextFormField(
            key: const ValueKey('rabbit-entry-source-mother'),
            controller: _motherIdController,
            decoration: InputDecoration(
              labelText: '母兔 ID（可选）',
              hintText: '输入母兔 ID，或碰一下母兔所在笼位',
              suffixIcon: IconButton(
                key: const ValueKey('rabbit-entry-source-mother-nfc'),
                tooltip: _nfcListening ? '正在读取 NFC 标签' : '碰一下母兔所在笼位',
                onPressed: _saving || _nfcListening
                    ? null
                    : () => unawaited(_startMotherNfcCapture()),
                icon: const Icon(Icons.nfc),
              ),
            ),
            enabled: !_saving,
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.next,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            onChanged: (_) => setState(() {
              _batchRequestId = null;
              _batchEntryResult = null;
              _submitError = null;
            }),
            validator: (value) {
              final text = value?.trim() ?? '';
              if (text.isEmpty) {
                return null;
              }
              return int.tryParse(text) == null || int.parse(text) <= 0
                  ? '请输入有效的母兔 ID'
                  : null;
            },
          ),
          if (_nfcHint != null) ...[
            const SizedBox(height: 6),
            Text(
              _nfcHint!,
              key: const ValueKey('rabbit-entry-source-mother-nfc-hint'),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ],
      ],
    );
  }

  Widget _buildStageFields(BuildContext context) {
    final reproductiveOptions = _reproductiveStageOptions;
    final fixedReproductiveStage = _type == '1';
    // 种母兔的选项为空（阶段由生产流程维护），不能再渲染一个空下拉给用户点。
    final hasReproductiveStage = _type != '2' &&
        (fixedReproductiveStage || reproductiveOptions.isNotEmpty);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionLabel('入栏阶段'),
        const SizedBox(height: 6),
        Text(
          '记录入栏时状态；后续繁殖进度由批次流程维护。',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        if (_type == '2') ...[
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            key: const ValueKey('rabbit-growth-stage'),
            value: _growthStage,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: '成长阶段',
              hintText: '请选择成长阶段',
            ),
            items: [
              for (final option in _growthStageOptions)
                DropdownMenuItem(
                    value: option.value, child: Text(option.label)),
            ],
            onChanged: _saving
                ? null
                : (value) => setState(() => _growthStage = value),
          ),
          if (!_isEdit) ...[
            const SizedBox(height: 12),
            _buildDateField(
              key: const ValueKey('rabbit-growth-stage-entered-at'),
              label: _growthStageDateLabel,
              value: _growthStageEnteredAt,
              onPicked: (value) =>
                  setState(() => _growthStageEnteredAt = value),
            ),
          ],
        ] else if (!_isEdit && _type == '1') ...[
          const SizedBox(height: 10),
          _buildDateField(
            key: const ValueKey('rabbit-growth-stage-entered-at'),
            label: _growthStageDateLabel,
            value: _growthStageEnteredAt,
            onPicked: (value) => setState(() => _growthStageEnteredAt = value),
          ),
        ],
        if (hasReproductiveStage) ...[
          const SizedBox(height: 12),
          if (fixedReproductiveStage)
            const InfoNotice(
              icon: Icons.account_tree_outlined,
              text: '繁殖阶段：后备（后备兔固定记录为后备阶段）',
            )
          else
            DropdownButtonFormField<String>(
              key: const ValueKey('rabbit-reproductive-stage'),
              value: _reproductiveStage,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: '繁殖阶段',
                hintText: '请选择繁殖阶段',
              ),
              items: [
                for (final option in reproductiveOptions)
                  DropdownMenuItem(
                    value: option.value,
                    child: Text(option.label),
                  ),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _reproductiveStage = value),
            ),
        ] else if (_type == '0') ...[
          const SizedBox(height: 12),
          const InfoNotice(
            icon: Icons.account_tree_outlined,
            text: '种母兔的繁殖阶段由生产流程维护，请在下方选择入轨的生产阶段。',
          ),
        ],
        if (_canOpenReproEntry) ...[
          const SizedBox(height: 18),
          _buildReproEntryFields(context),
        ],
      ],
    );
  }

  /// 生产阶段入轨区域。
  ///
  /// 存栏母兔很少处于「什么都没发生」的起点，所以需要能从任意阶段入轨；
  /// 而「从这个阶段入轨要补录什么」由服务端字典决定，客户端不拄第二份。
  Widget _buildReproEntryFields(BuildContext context) {
    final entriesAsync = ref.watch(reproEntryPointsProvider(widget.houseId));
    final batchesAsync = ref.watch(houseBatchesProvider(widget.houseId));
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    return entriesAsync.when(
      loading: () => const InfoNotice(
        icon: Icons.hourglass_empty,
        text: '正在读取可入轨的生产阶段…',
      ),
      error: (_, __) => const InfoNotice(
        icon: Icons.info_outline,
        text: '暂时读不到生产阶段字典，可先录入，稍后在生产流程里入轨。',
      ),
      data: (entries) {
        if (entries.isEmpty) {
          return const SizedBox.shrink();
        }
        final selected = _selectedEntryPoint(entries);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _SectionLabel('生产阶段入轨'),
            const SizedBox(height: 6),
            Text(
              '已在生产中的母兔可直接从当前阶段入轨，录入后立即进入待办。',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String?>(
              key: const ValueKey('rabbit-repro-stage'),
              value: _reproStage,
              isExpanded: true,
              decoration: const InputDecoration(labelText: '生产阶段'),
              items: [
                const DropdownMenuItem<String?>(
                  value: null,
                  child: Text('暂不入轨'),
                ),
                for (final entry in entries)
                  DropdownMenuItem<String?>(
                    value: entry.stage,
                    child: Text(entry.stageLabel),
                  ),
              ],
              onChanged: _saving ? null : _setReproStage,
            ),
            if (selected != null) ...[
              const SizedBox(height: 12),
              _buildReproBatchField(
                batchesAsync,
                required: selected.batchRequired,
              ),
              const SizedBox(height: 12),
              _buildDateField(
                key: const ValueKey('rabbit-stage-entered-at'),
                label: '进入该阶段日期',
                value: _stageEnteredAt,
                onPicked: (value) => setState(() => _stageEnteredAt = value),
              ),
              if (selected.needsMatingDate) ...[
                const SizedBox(height: 12),
                _buildDateField(
                  key: const ValueKey('rabbit-mating-date'),
                  label: '配种日期',
                  value: _matingDate,
                  onPicked: (value) => setState(() => _matingDate = value),
                ),
              ],
              if (selected.requires('BIRTH_DATE')) ...[
                const SizedBox(height: 12),
                _buildDateField(
                  key: const ValueKey('rabbit-birth-date'),
                  label: '分娩日期',
                  value: _birthDate,
                  onPicked: (value) => setState(() => _birthDate = value),
                ),
              ],
              if (selected.requires('MALE_RABBIT') ||
                  selected.requires('MATING_METHOD')) ...[
                const SizedBox(height: 12),
                _buildEntryMatingFields(rabbitsAsync),
              ],
              if (selected.requires('TOTAL_KITS')) ...[
                const SizedBox(height: 12),
                _buildEntryCountField(
                  key: const ValueKey('rabbit-total-kits'),
                  label: '产仔数',
                  controller: _totalKitsController,
                ),
              ],
              if (selected.requires('LIVE_KITS')) ...[
                const SizedBox(height: 12),
                _buildEntryCountField(
                  key: const ValueKey('rabbit-live-kits'),
                  label: '活仔数',
                  controller: _liveKitsController,
                ),
              ],
              if (selected.requires('KEPT_KITS')) ...[
                const SizedBox(height: 12),
                _buildEntryCountField(
                  key: const ValueKey('rabbit-kept-kits'),
                  label: '留仔数',
                  controller: _keptKitsController,
                ),
              ],
            ],
          ],
        );
      },
    );
  }

  Widget _buildEntryMatingFields(AsyncValue<List<Rabbit>> rabbitsAsync) {
    return rabbitsAsync.when(
      loading: () => const InfoNotice(
        icon: Icons.hourglass_empty,
        text: '正在读取可用种公兔…',
      ),
      error: (_, __) => const InfoNotice(
        icon: Icons.error_outline,
        text: '种公兔读取失败，请刷新后重试。',
      ),
      data: (rabbits) {
        final males = _availableBreedingMales(
          rabbits,
          houseId: widget.houseId,
        );
        return Column(
          children: [
            DropdownButtonFormField<MatingMethod>(
              key: const ValueKey('rabbit-entry-mating-method'),
              value: _entryMatingMethod,
              decoration: const InputDecoration(labelText: '配种方式'),
              items: [
                for (final method in MatingMethod.values)
                  DropdownMenuItem(value: method, child: Text(method.label)),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(
                        () => _entryMatingMethod = value ?? _entryMatingMethod,
                      ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              key: const ValueKey('rabbit-entry-mating-male'),
              value: males.any((rabbit) => rabbit.id == _entryMaleRabbitId)
                  ? _entryMaleRabbitId
                  : null,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: '配种公兔',
                hintText: '请选择种公兔',
              ),
              items: [
                for (final male in males)
                  DropdownMenuItem(
                    value: male.id,
                    child: Text(
                      '兔 #${male.id} · ${male.breed.isEmpty ? '未填品种' : male.breed}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _entryMaleRabbitId = value),
            ),
          ],
        );
      },
    );
  }

  Widget _buildEntryCountField({
    required Key key,
    required String label,
    required TextEditingController controller,
  }) {
    return TextFormField(
      key: key,
      controller: controller,
      enabled: !_saving,
      decoration: InputDecoration(labelText: label),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
    );
  }

  Widget _buildReproBatchField(
    AsyncValue<List<Batch>> batchesAsync, {
    required bool required,
  }) {
    return batchesAsync.when(
      loading: () => const InfoNotice(
        icon: Icons.hourglass_empty,
        text: '正在读取进行中的批次…',
      ),
      error: (_, __) => const InfoNotice(
        icon: Icons.error_outline,
        text: '批次读取失败，请刷新后重试。',
      ),
      data: (batches) {
        final activeBatches = _inProgressBatches(batches);
        if (activeBatches.isEmpty) {
          return const InfoNotice(
            icon: Icons.info_outline,
            text: '当前没有进行中的批次；可选择“暂不入轨”后先录入兔只。',
          );
        }
        final selectedId = activeBatches.any((batch) => batch.id == _batchId)
            ? _batchId
            : null;
        return DropdownButtonFormField<int>(
          key: const ValueKey('rabbit-repro-batch'),
          value: selectedId,
          isExpanded: true,
          decoration: InputDecoration(
            labelText: required ? '生产批次' : '计划批次（可选）',
            hintText: required ? '请选择进行中的生产批次' : '可先选择计划进入的批次',
          ),
          items: [
            for (final batch in activeBatches)
              DropdownMenuItem(
                value: batch.id,
                child: Text(
                  batch.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
          onChanged:
              _saving ? null : (value) => setState(() => _batchId = value),
        );
      },
    );
  }

  void _setReproStage(String? value) {
    setState(() {
      if (_reproStage != value) {
        _batchId = null;
        _matingDate = null;
        _birthDate = null;
        _entryMaleRabbitId = null;
        _entryMatingMethod = MatingMethod.natural;
        _totalKitsController.clear();
        _liveKitsController.clear();
        _keptKitsController.clear();
      }
      _reproStage = value;
      _stageEnteredAt = value == null ? null : _farmToday();
    });
  }

  int? _selectedInProgressBatchId() {
    final batchId = _batchId;
    final batches = ref.read(houseBatchesProvider(widget.houseId)).valueOrNull;
    if (batchId == null || batches == null) {
      return null;
    }
    return _inProgressBatches(batches).any((batch) => batch.id == batchId)
        ? batchId
        : null;
  }

  ReproEntryPoint? _selectedEntryPoint(List<ReproEntryPoint> entries) {
    final stage = _reproStage;
    if (stage == null) {
      return null;
    }
    for (final entry in entries) {
      if (entry.stage == stage) {
        return entry;
      }
    }
    return null;
  }

  Widget _buildDateField({
    required Key key,
    required String label,
    required DateTime? value,
    required ValueChanged<DateTime> onPicked,
    bool enabled = true,
  }) {
    final normalizedValue = value == null ? null : _dateOnly(value);
    final text = normalizedValue == null
        ? '未选择'
        : DateFormat('yyyy-MM-dd').format(normalizedValue);
    return InputDecorator(
      key: key,
      decoration: InputDecoration(labelText: label),
      child: InkWell(
        onTap: _saving || !enabled
            ? null
            : () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: normalizedValue ?? _farmToday(),
                  firstDate: DateTime(2020),
                  lastDate: _farmToday().add(const Duration(days: 1)),
                  helpText: label,
                  cancelText: '取消',
                  confirmText: '确定',
                );
                if (picked != null) {
                  onPicked(_dateOnly(picked));
                }
              },
        child: Row(
          children: [
            Expanded(child: Text(text)),
            const Icon(Icons.calendar_today_outlined, size: 18),
          ],
        ),
      ),
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final palette = AppPalette.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border(top: BorderSide(color: palette.line)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_submitError != null) ...[
              Container(
                key: const ValueKey('rabbit-entry-submit-error'),
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: palette.dangerSoft,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: palette.danger.withAlpha(90)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.error_outline, color: palette.danger),
                    const SizedBox(width: 10),
                    Expanded(child: Text(_submitError!)),
                  ],
                ),
              ),
            ],
            if (_batchEntryResult case final result?) ...[
              InfoNotice(
                key: const ValueKey('rabbit-entry-batch-result'),
                icon: Icons.info_outline,
                text: _batchResultMessage(result),
              ),
              const SizedBox(height: 10),
            ],
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed:
                        _saving ? null : () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    key: const ValueKey('rabbit-entry-submit'),
                    onPressed: _saving ? null : _save,
                    child: _saving
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Text(
                            _batchEntryResult != null
                                ? '关闭'
                                : _isEdit
                                    ? '保存'
                                    : '确定',
                          ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _batchResultMessage(BatchRabbitEntryResult result) {
    final skipped = result.skippedCages
        .map((item) =>
            '${item.cageLabel}：${item.rabbitCount}只未录入，${item.reason}')
        .join('；');
    final entered = result.enteredRabbitCount;
    if (skipped.isEmpty) {
      return entered > 0 ? '已录入 $entered 只' : '本次请求已完成';
    }
    return entered > 0 ? '已录入 $entered 只；$skipped' : skipped;
  }

  Future<void> _save() async {
    if (_saving) {
      return;
    }
    if (_batchEntryResult != null) {
      Navigator.of(context).pop();
      return;
    }
    if (!_formKey.currentState!.validate()) {
      return;
    }
    // 阶段中文名要在表单关闭前取，提示里要告知「已入轨」：
    // 否则人不知道还要不要再去生产流程里手工开一轮。
    final enteredStageLabel = _selectedEntryStageLabel();
    final batchId = _reproStage == null ? null : _selectedInProgressBatchId();
    final missingFact = _missingEntryFact();
    if (missingFact != null) {
      setState(() => _submitError = missingFact);
      return;
    }
    _stopMotherNfcCapture();
    setState(() {
      _saving = true;
      _submitError = null;
    });
    try {
      if (!_isEdit) {
        final freshCages = await _refreshCreateCages();
        if (freshCages == null || !mounted) {
          return;
        }
        final validation = validateRabbitCageTarget(
          cages: freshCages,
          houseId: widget.houseId,
          cageId: _createCage.id,
          rabbitType: _type,
        );
        if (!validation.isValid) {
          setState(() => _submitError = validation.message!);
          return;
        }
      }
      if (_isEdit) {
        await ref.read(rabbitRepositoryProvider).updateRabbit(
              houseId: widget.houseId,
              rabbitId: _rabbit.id,
              cageId: _rabbit.cageId,
              motherId: _rabbit.motherId,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              arrivalDate: _arrivalDate,
              weight: double.tryParse(_weightController.text.trim()),
              growthStage: _type == '2' ? _growthStage : null,
              reproductiveStage: _reproductiveStage,
            );
      } else if (_isBatchEntry) {
        final requestId = _batchRequestId ?? _uuid.v4();
        _batchRequestId = requestId;
        final result = await ref
            .read(rabbitRepositoryProvider)
            .createRabbitBatch(
              houseId: widget.houseId,
              cageId: _createCage.id,
              quantity: _quantity,
              totalWeight: double.parse(_weightController.text.trim()),
              type: _type,
              gender: _gender,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              sourceSeller: _sourceSellerController.text,
              motherId: int.tryParse(_motherIdController.text.trim()),
              arrivalDate: _arrivalDate!,
              growthStage: null,
              growthStageEnteredAt: _type == '1' ? _growthStageEnteredAt : null,
              reproductiveStage: _reproductiveStage,
              requestId: requestId,
            );
        ref.invalidate(houseRabbitsProvider(widget.houseId));
        ref.invalidate(houseCagesProvider(widget.houseId));
        ref.invalidate(homeEventsProvider);
        if (result.skippedCages.isNotEmpty) {
          setState(() {
            _batchEntryResult = result;
            _submitError = result.enteredRabbitCount == 0
                ? _batchResultMessage(result)
                : null;
          });
          return;
        }
      } else {
        await ref.read(rabbitRepositoryProvider).createRabbit(
              houseId: widget.houseId,
              cageId: _createCage.id,
              type: _type,
              gender: _gender,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              sourceSeller: _sourceSellerController.text,
              motherId: int.tryParse(_motherIdController.text.trim()),
              arrivalDate: _arrivalDate!,
              weight: double.tryParse(_weightController.text.trim()),
              growthStage: null,
              growthStageEnteredAt: _type == '1' ? _growthStageEnteredAt : null,
              reproductiveStage: _reproductiveStage,
              reproStage: _canOpenReproEntry ? _reproStage : null,
              batchId: _canOpenReproEntry ? batchId : null,
              stageEnteredAt: _stageEnteredAt,
              matingDate: _matingDate,
              birthDate: _birthDate,
              totalKits: int.tryParse(_totalKitsController.text.trim()),
              liveKits: int.tryParse(_liveKitsController.text.trim()),
              keptKits: int.tryParse(_keptKitsController.text.trim()),
              maleRabbitId: _entryMaleRabbitId,
              matingMethod: _entryMatingMethod,
            );
      }
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_successMessage(enteredStageLabel))),
        );
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is ApiException ? error.message : '提交失败，请检查网络后重试';
      setState(() => _submitError = message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<List<Cage>?> _refreshCreateCages() async {
    try {
      return await ref.refresh(houseCagesProvider(widget.houseId).future);
    } catch (_) {
      if (mounted) {
        setState(() => _submitError = '笼位状态刷新失败，请检查网络后重试');
      }
      return null;
    }
  }

  String get _growthStageDateLabel {
    if (_type == '1') {
      return '进入后备阶段日期';
    }
    final selected = _growthStageOptions
        .where((option) => option.value == _growthStage)
        .firstOrNull;
    return selected == null ? '进入成长阶段日期' : '进入${selected.label}日期';
  }

  /// 可手工录入的旧繁殖阶段。
  ///
  /// 种母兔刻意为空：它的阶段由生产流程状态机单写，后端已直接拒收手录的
  /// reproductiveStage；再给一份下拉只会让用户填完才吃 400（飞书 recvsrpMlvu2SC）。
  /// 她们改用下面的「生产阶段入轨」区域。
  List<_StageOption> get _reproductiveStageOptions {
    if (_type == '1') {
      return _replacementReproductiveStageOptions;
    }
    if (_type != '0') {
      return const <_StageOption>[];
    }
    return _gender == '1'
        ? _buckReproductiveStageOptions
        : const <_StageOption>[];
  }

  /// 只有新录入的种母兔能在这里入轨；已存在的母兔要改阶段得走生产动作。
  bool get _canOpenReproEntry => !_isEdit && _type == '0' && _gender == '0';

  void _setGender(String value) {
    setState(() {
      _gender = value;
      final allowedValues =
          _reproductiveStageOptions.map((option) => option.value).toSet();
      if (!allowedValues.contains(_reproductiveStage)) {
        _reproductiveStage = null;
      }
    });
  }

  String _successMessage(String? enteredStageLabel) {
    if (_isEdit) {
      return '已更新兔 #${_rabbit.id}';
    }
    if (enteredStageLabel == null) {
      return '已录入到 ${_createCageName()}';
    }
    return '已录入到 ${_createCageName()}，并从【$enteredStageLabel】入轨';
  }

  String? _selectedEntryStageLabel() {
    if (!_canOpenReproEntry || _reproStage == null) {
      return null;
    }
    final entries =
        ref.read(reproEntryPointsProvider(widget.houseId)).valueOrNull;
    if (entries == null) {
      return null;
    }
    return _selectedEntryPoint(entries)?.stageLabel;
  }

  /// 入轨必填项的本地校验。服务端同样会拦，这里只是不让用户白跑一趟网络。
  String? _missingEntryFact() {
    if (!_canOpenReproEntry || _reproStage == null) {
      return null;
    }
    final entries =
        ref.read(reproEntryPointsProvider(widget.houseId)).valueOrNull;
    final selected = entries == null ? null : _selectedEntryPoint(entries);
    if (selected == null) {
      return null;
    }
    if (selected.batchRequired && _selectedInProgressBatchId() == null) {
      return '请选择进行中的批次';
    }
    for (final fact in selected.requiredFacts) {
      final filled = switch (fact.fact) {
        'STAGE_ENTERED_AT' => _stageEnteredAt != null,
        'MATING_DATE' || 'GESTATION_ANCHOR' => _matingDate != null,
        'BIRTH_DATE' => _birthDate != null,
        'MALE_RABBIT' => _entryMaleRabbitId != null,
        'MATING_METHOD' => true,
        'TOTAL_KITS' => _totalKitsController.text.trim().isNotEmpty,
        'LIVE_KITS' => _liveKitsController.text.trim().isNotEmpty,
        'KEPT_KITS' => _keptKitsController.text.trim().isNotEmpty,
        _ => true,
      };
      if (!filled) {
        return '从【${selected.stageLabel}】入轨需要补录${fact.label}';
      }
    }
    return null;
  }

  String? _matchingStage(String? value, List<_StageOption> options) {
    if (value == null || value.isEmpty) {
      return null;
    }
    final normalized = value == 'JUVENILE' ? 'ADAPTATION' : value;
    return options.any((option) => option.value == normalized)
        ? normalized
        : null;
  }

  String _createCageName() {
    final cage = _createCage;
    return cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
  }

  /// 兔子档案类型（rabbits.type），与批次内生育状态（batch_rabbits.current_status）无关。
  String _typeOptionLabel(String type) {
    switch (type) {
      case '0':
        return _gender == '0' ? '种母兔' : '种公兔';
      case '1':
        return '后备兔';
      case '2':
        return '商品兔';
      default:
        return type;
    }
  }
}

DateTime _dateOnly(DateTime value) => localDateOnly(value);

DateTime _farmToday() => farmToday();

class _StageOption {
  const _StageOption(this.value, this.label);

  final String value;
  final String label;
}

class _RabbitTypeChoice extends StatelessWidget {
  const _RabbitTypeChoice({
    required this.value,
    required this.groupValue,
    required this.label,
    required this.onChanged,
  });

  final String value;
  final String groupValue;
  final String label;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return InkWell(
      onTap: () => onChanged(value),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Radio<String>(
              value: value,
              groupValue: groupValue,
              onChanged: (value) {
                if (value != null) {
                  onChanged(value);
                }
              },
              activeColor: palette.success,
            ),
            Text(label, style: Theme.of(context).textTheme.titleMedium),
          ],
        ),
      ),
    );
  }
}

class _RadioChoice extends StatelessWidget {
  const _RadioChoice({
    required this.value,
    required this.groupValue,
    required this.label,
    required this.onChanged,
  });

  final String value;
  final String groupValue;
  final String label;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return InkWell(
      onTap: () => onChanged(value),
      borderRadius: BorderRadius.circular(8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Radio<String>(
            value: value,
            groupValue: groupValue,
            onChanged: (value) {
              if (value != null) {
                onChanged(value);
              }
            },
            activeColor: palette.success,
          ),
          Text(label, style: Theme.of(context).textTheme.titleMedium),
        ],
      ),
    );
  }
}

class _ResponsiveFieldRow extends StatelessWidget {
  const _ResponsiveFieldRow({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 420) {
          return Column(
            children: [
              for (var i = 0; i < children.length; i++) ...[
                if (i > 0) const SizedBox(height: 12),
                children[i],
              ],
            ],
          );
        }

        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) const SizedBox(width: 10),
              Expanded(child: children[i]),
            ],
          ],
        );
      },
    );
  }
}

class _CageTypeWarning extends StatelessWidget {
  const _CageTypeWarning({required this.cage});

  final Cage cage;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.warningSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(
        '当前笼位用途为${cage.usageLabel}，选择其他类型时后端会拒绝提交。',
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: palette.warning,
              fontWeight: FontWeight.w800,
            ),
      ),
    );
  }
}

class _CageCapacityWarning extends StatelessWidget {
  const _CageCapacityWarning({required this.cage});

  final Cage cage;

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
      child: Text(
        cage.entryBlockedReason ?? '该笼位已满，不能再录入兔子。',
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: palette.danger,
              fontWeight: FontWeight.w800,
            ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(label, style: Theme.of(context).textTheme.titleMedium);
  }
}
