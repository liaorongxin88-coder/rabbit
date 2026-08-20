import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

const _growthStageOptions = <_StageOption>[
  _StageOption('JUVENILE', '幼兔'),
  _StageOption('GROWING', '成长期'),
  _StageOption('FATTENING', '育肥期'),
  _StageOption('MATURE', '成熟'),
];

const _buckReproductiveStageOptions = <_StageOption>[
  _StageOption('READY', '可配'),
  _StageOption('RESTING', '休整'),
];

const _replacementReproductiveStageOptions = <_StageOption>[
  _StageOption('RESERVE', '后备'),
];

/// 录入兔只的两步流程：先选类型，再填详情。
///
/// 两步都在这里 await，因为调用方（笼位详情页）要靠这个 Future 决定何时刷列表。
/// 早先的写法是类型页 pop 后在 post-frame 回调里另开表单而不等，于是调用方的
/// 刷新回调在兔子创建之前就跑完了，新录入的兔子要退出重进页面才看得见。
Future<void> showRabbitEntryTypeSheet({
  required BuildContext context,
  required int houseId,
  required Cage cage,
}) async {
  final type = await showAppModalSheet<String>(
    context: context,
    builder: (sheetContext) => _RabbitTypeSheet(cage: cage),
  );
  if (type == null || !context.mounted) {
    return;
  }
  await showAppModalSheet<void>(
    context: context,
    builder: (sheetContext) => _CreateRabbitSheet(
      houseId: houseId,
      cage: cage,
      initialType: type,
    ),
  );
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
  final _liveKitsController = TextEditingController();
  String? _stage;
  DateTime? _stageEnteredAt;
  DateTime? _matingDate;
  DateTime? _birthDate;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _stage = widget.initialStage;
    if (_stage != null) {
      _stageEnteredAt = _dateOnly(DateTime.now());
    }
  }

  @override
  void dispose() {
    _liveKitsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final entriesAsync = ref.watch(reproEntryPointsProvider(widget.houseId));
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
                            '母兔 #${widget.rabbit.id} · 批次可稍后关联',
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
                    data: _buildEntryFields,
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

  Widget _buildEntryFields(List<ReproEntryPoint> entries) {
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
          onChanged: _saving
              ? null
              : (value) => setState(() {
                    _stage = value;
                    _stageEnteredAt ??= _dateOnly(DateTime.now());
                  }),
        ),
        if (selected != null) ...[
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
          if (selected.requires('LIVE_KITS')) ...[
            const SizedBox(height: 12),
            TextField(
              key: const ValueKey('existing-rabbit-live-kits'),
              controller: _liveKitsController,
              enabled: !_saving,
              decoration: const InputDecoration(labelText: '活仔数'),
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            ),
          ],
        ],
      ],
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
                  initialDate: value ?? _dateOnly(DateTime.now()),
                  firstDate: DateTime(2020),
                  lastDate: DateTime.now().add(const Duration(days: 1)),
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
                value == null ? '未选择' : DateFormat('yyyy-MM-dd').format(value),
              ),
            ),
            const Icon(Icons.calendar_today_outlined, size: 18),
          ],
        ),
      ),
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
            stage: stage,
            occurredAt: _stageEnteredAt,
            matingDate: _matingDate,
            birthDate: _birthDate,
            liveKits: int.tryParse(_liveKitsController.text.trim()),
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
        'LIVE_KITS' => _liveKitsController.text.trim().isNotEmpty,
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
  final _formKey = GlobalKey<FormState>();
  final _breedController = TextEditingController();
  final _weightController = TextEditingController();
  final _liveKitsController = TextEditingController();
  late String _type;
  var _gender = '0';
  var _arrivalMethod = '0';
  String? _growthStage;
  String? _reproductiveStage;

  /// 录入时直接入轨的生产阶段（飞书 recvsrnEJ8bKrk）。null 表示暂不入轨。
  String? _reproStage;
  DateTime? _stageEnteredAt;
  DateTime? _matingDate;
  DateTime? _birthDate;
  var _saving = false;

  bool get _isEdit => widget.rabbit != null;

  Rabbit get _rabbit => widget.rabbit!;

  Cage get _createCage => widget.cage!;

  @override
  void initState() {
    super.initState();
    final rabbit = widget.rabbit;
    if (rabbit == null) {
      _type = widget.initialType;
      _growthStage = null;
      _reproductiveStage = _type == '1' ? 'RESERVE' : null;
    } else {
      _type = rabbit.type;
      _gender = rabbit.gender;
      _arrivalMethod =
          rabbit.arrivalMethod.isEmpty ? '0' : rabbit.arrivalMethod;
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
    _breedController.dispose();
    _weightController.dispose();
    _liveKitsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: mediaQuery.size.height * 0.9),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildHeader(context),
                Flexible(
                  fit: FlexFit.loose,
                  child: SingleChildScrollView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                    child: _buildFormBody(context),
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
        _buildStageFields(context),
        const SizedBox(height: 18),
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
                return null;
              },
            ),
            if (!_isEdit) const SizedBox.shrink(),
          ],
        ),
        const SizedBox(height: 12),
        _ResponsiveFieldRow(
          children: [
            DropdownButtonFormField<String>(
              value: _arrivalMethod,
              isExpanded: true,
              decoration: const InputDecoration(labelText: '来源'),
              items: const [
                DropdownMenuItem(value: '0', child: Text('购入')),
                DropdownMenuItem(value: '1', child: Text('出生')),
              ],
              onChanged: _saving
                  ? null
                  : (value) => setState(
                        () => _arrivalMethod = value ?? _arrivalMethod,
                      ),
            ),
            TextFormField(
              controller: _weightController,
              decoration: const InputDecoration(
                labelText: '体重',
                suffixText: 'kg',
              ),
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              textInputAction: TextInputAction.next,
              inputFormatters: [
                FilteringTextInputFormatter.allow(
                  RegExp(r'^\d*\.?\d{0,2}'),
                ),
              ],
            ),
          ],
        ),
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
              DropdownMenuItem(value: option.value, child: Text(option.label)),
          ],
          onChanged:
              _saving ? null : (value) => setState(() => _growthStage = value),
        ),
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
              onChanged: _saving
                  ? null
                  : (value) => setState(() {
                        _reproStage = value;
                        _stageEnteredAt ??= _dateOnly(DateTime.now());
                      }),
            ),
            if (selected != null) ...[
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
              if (selected.requires('LIVE_KITS')) ...[
                const SizedBox(height: 12),
                TextFormField(
                  key: const ValueKey('rabbit-live-kits'),
                  controller: _liveKitsController,
                  enabled: !_saving,
                  decoration: const InputDecoration(labelText: '活仔数'),
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                ),
              ],
            ],
          ],
        );
      },
    );
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
  }) {
    final text = value == null ? '未选择' : DateFormat('yyyy-MM-dd').format(value);
    return InputDecorator(
      key: key,
      decoration: InputDecoration(labelText: label),
      child: InkWell(
        onTap: _saving
            ? null
            : () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: value ?? _dateOnly(DateTime.now()),
                  firstDate: DateTime(2020),
                  lastDate: DateTime.now().add(const Duration(days: 1)),
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
                    : Text(_isEdit ? '保存' : '确定'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    // 阶段中文名要在表单关闭前取，提示里要告知「已入轨」：
    // 否则人不知道还要不要再去生产流程里手工开一轮。
    final enteredStageLabel = _selectedEntryStageLabel();
    final missingFact = _missingEntryFact();
    if (missingFact != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(missingFact)),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      if (_isEdit) {
        await ref.read(rabbitRepositoryProvider).updateRabbit(
              houseId: widget.houseId,
              rabbitId: _rabbit.id,
              cageId: _rabbit.cageId,
              motherId: _rabbit.motherId,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              arrivalDate: _rabbit.arrivalDate,
              weight: double.tryParse(_weightController.text.trim()),
              growthStage: _growthStage,
              reproductiveStage: _reproductiveStage,
            );
      } else {
        await ref.read(rabbitRepositoryProvider).createRabbit(
              houseId: widget.houseId,
              cageId: _createCage.id,
              type: _type,
              gender: _gender,
              breed: _breedController.text,
              arrivalMethod: _arrivalMethod,
              weight: double.tryParse(_weightController.text.trim()),
              growthStage: _growthStage,
              reproductiveStage: _reproductiveStage,
              reproStage: _canOpenReproEntry ? _reproStage : null,
              stageEnteredAt: _stageEnteredAt,
              matingDate: _matingDate,
              birthDate: _birthDate,
              liveKits: int.tryParse(_liveKitsController.text.trim()),
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
      final message = error is ApiException ? error.message : error.toString();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
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
    for (final fact in selected.requiredFacts) {
      final filled = switch (fact.fact) {
        'STAGE_ENTERED_AT' => _stageEnteredAt != null,
        'MATING_DATE' || 'GESTATION_ANCHOR' => _matingDate != null,
        'BIRTH_DATE' => _birthDate != null,
        'LIVE_KITS' => _liveKitsController.text.trim().isNotEmpty,
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
    return options.any((option) => option.value == value) ? value : null;
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

DateTime _dateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);

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
