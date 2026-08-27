import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/vaccinations/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

const _vaccineRoutes = <String>['皮下注射', '肌肉注射', '口服', '滴鼻'];

Future<bool> showRabbitVaccinationSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
}) async {
  final recorded = await showAppModalSheet<bool>(
    context: context,
    builder: (context) =>
        _RabbitVaccinationSheet(houseId: houseId, rabbit: rabbit),
  );
  return recorded ?? false;
}

class _RabbitVaccinationSheet extends ConsumerStatefulWidget {
  const _RabbitVaccinationSheet({required this.houseId, required this.rabbit});

  final int houseId;
  final Rabbit rabbit;

  @override
  ConsumerState<_RabbitVaccinationSheet> createState() =>
      _RabbitVaccinationSheetState();
}

class _RabbitVaccinationSheetState
    extends ConsumerState<_RabbitVaccinationSheet> {
  final _formKey = GlobalKey<FormState>();
  final _vaccineNameController = TextEditingController();
  final _batchNoController = TextEditingController();
  final _doseController = TextEditingController();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  late DateTime _vaccinatedAt;
  DateTime? _nextDueDate;
  String? _route;
  var _wholeCage = false;
  var _saving = false;

  /// 失败原因直接渲染在表单里，不用 SnackBar。
  ///
  /// 真机上验证时发现：底部弹层盖住了屏幕下方，而 SnackBar 就渲染在那里，
  /// 断网提交时操作者什么都看不到——表单不关、没报错，分不清是卡住了还是成功了。
  String? _submitError;

  int? get _cageId {
    final cageId = widget.rabbit.cageId;
    return cageId > 0 ? cageId : null;
  }

  @override
  void initState() {
    super.initState();
    _vaccinatedAt = _dateOnly(DateTime.now());
  }

  @override
  void dispose() {
    _vaccineNameController.dispose();
    _batchNoController.dispose();
    _doseController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _pickVaccinatedAt() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _vaccinatedAt,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      helpText: '选择接种日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      final next = _dateOnly(picked);
      setState(() {
        _vaccinatedAt = next;
        // 接种日期往后挪时，旧的下次接种日期可能不再晚于本次，直接清掉，
        // 否则用户会拿到一条自己没改过的字段引起的报错。
        final due = _nextDueDate;
        if (due != null && !due.isAfter(next)) {
          _nextDueDate = null;
        }
      });
    }
  }

  Future<void> _pickNextDueDate() async {
    final earliest = _vaccinatedAt.add(const Duration(days: 1));
    final initial = _nextDueDate ?? _vaccinatedAt.add(const Duration(days: 21));
    final picked = await showDatePicker(
      context: context,
      initialDate: initial.isBefore(earliest) ? earliest : initial,
      firstDate: earliest,
      lastDate: DateTime(2100),
      helpText: '选择下次接种日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _nextDueDate = _dateOnly(picked));
    }
  }

  /// 本次要打的兔只。整笼接种时取同笼在栏兔，取不到就退回当前这一只。
  List<int> _targets() {
    if (!_wholeCage) {
      return [widget.rabbit.id];
    }
    final cageId = _cageId;
    if (cageId == null) {
      return [widget.rabbit.id];
    }
    final cageRabbits = ref
        .read(cageRabbitsProvider((houseId: widget.houseId, cageId: cageId)))
        .valueOrNull;
    if (cageRabbits == null || cageRabbits.isEmpty) {
      return [widget.rabbit.id];
    }
    final ids = cageRabbits
        .where((rabbit) => rabbit.isActive)
        .map((rabbit) => rabbit.id)
        .toSet()
      ..add(widget.rabbit.id);
    return ids.toList(growable: false);
  }

  Future<void> _submit() async {
    if (_saving || !(_formKey.currentState?.validate() ?? false)) {
      return;
    }

    final targets = _targets();
    final vaccineName = _vaccineNameController.text.trim();
    final batchNo = _batchNoController.text.trim();
    final dose = _doseController.text.trim();
    final remark = _remarkController.text.trim();
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'rabbitVaccination',
        'houseId': widget.houseId,
        'rabbitIds': targets,
        'vaccineName': vaccineName,
        'vaccineBatchNo': batchNo,
        'dose': dose,
        'route': _route,
        'vaccinatedAt': _vaccinatedAt.millisecondsSinceEpoch,
        'nextDueDate': _nextDueDate?.millisecondsSinceEpoch,
        'remark': remark,
      }),
    );

    setState(() {
      _saving = true;
      _submitError = null;
    });
    try {
      await ref.read(vaccinationRepositoryProvider).createVaccination(
            houseId: widget.houseId,
            rabbitIds: targets,
            vaccineName: vaccineName,
            vaccinatedAt: _vaccinatedAt,
            vaccineBatchNo: batchNo,
            dose: dose,
            route: _route,
            nextDueDate: _nextDueDate,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }

      ref.invalidate(
        rabbitVaccinationsProvider(
          RabbitDetailRequest(
            houseId: widget.houseId,
            rabbitId: widget.rabbit.id,
          ),
        ),
      );
      ref.invalidate(houseDueVaccinationsProvider(widget.houseId));

      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            targets.length > 1
                ? '已为 ${targets.length} 只兔记录「$vaccineName」接种'
                : '兔 #${widget.rabbit.id} 已记录「$vaccineName」接种',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        setState(() => _submitError = _errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final palette = AppPalette.of(context);
    final availableHeight =
        mediaQuery.size.height - mediaQuery.viewInsets.bottom;
    final cageId = _cageId;
    final cageRabbitCount = cageId == null
        ? 0
        : (ref
                    .watch(cageRabbitsProvider(
                        (houseId: widget.houseId, cageId: cageId)))
                    .valueOrNull ??
                const <Rabbit>[])
            .where((rabbit) => rabbit.isActive)
            .length;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: mediaQuery.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * .92),
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
                            '接种疫苗',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '兔 #${widget.rabbit.id} · ${widget.rabbit.typeLabel}',
                            maxLines: 1,
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
                child: Form(
                  key: _formKey,
                  child: ListView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 84),
                    children: [
                      if (_submitError != null) ...[
                        Container(
                          key: const ValueKey('rabbit-vaccination-error'),
                          margin: const EdgeInsets.only(top: 4, bottom: 8),
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: palette.dangerSoft,
                            borderRadius: BorderRadius.circular(8),
                            border:
                                Border.all(color: palette.danger.withAlpha(90)),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.error_outline, color: palette.danger),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  _submitError!,
                                  style: TextStyle(color: palette.text),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      ListTile(
                        key: const ValueKey('rabbit-vaccination-date'),
                        contentPadding: EdgeInsets.zero,
                        title: const Text('接种日期'),
                        subtitle: Text(_formatDate(_vaccinatedAt)),
                        trailing: const Icon(Icons.calendar_today_outlined),
                        onTap: _saving ? null : _pickVaccinatedAt,
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-vaccination-name'),
                        controller: _vaccineNameController,
                        enabled: !_saving,
                        maxLength: 100,
                        textInputAction: TextInputAction.next,
                        // 否则填上名称后那句红字还挂在那里，看着像没填对。
                        autovalidateMode: AutovalidateMode.onUserInteraction,
                        decoration: const InputDecoration(
                          labelText: '疫苗名称*',
                          hintText: '如：兔瘟疫苗',
                        ),
                        validator: (value) =>
                            (value?.trim().isEmpty ?? true) ? '请填写疫苗名称' : null,
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-vaccination-batch-no'),
                        controller: _batchNoController,
                        enabled: !_saving,
                        maxLength: 64,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: '疫苗批号（可选）',
                          hintText: '出问题时按批号追溯',
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-vaccination-dose'),
                        controller: _doseController,
                        enabled: !_saving,
                        maxLength: 50,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: '剂量（可选）',
                          hintText: '如：1ml',
                        ),
                      ),
                      const SizedBox(height: 8),
                      DropdownButtonFormField<String>(
                        key: const ValueKey('rabbit-vaccination-route'),
                        value: _route,
                        decoration:
                            const InputDecoration(labelText: '接种途径（可选）'),
                        items: [
                          const DropdownMenuItem<String>(
                            value: null,
                            child: Text('未填写'),
                          ),
                          for (final route in _vaccineRoutes)
                            DropdownMenuItem<String>(
                              value: route,
                              child: Text(route),
                            ),
                        ],
                        onChanged: _saving
                            ? null
                            : (value) => setState(() => _route = value),
                      ),
                      const SizedBox(height: 8),
                      ListTile(
                        key: const ValueKey('rabbit-vaccination-next-due'),
                        contentPadding: EdgeInsets.zero,
                        title: const Text('下次接种日期（可选）'),
                        subtitle: Text(
                          _nextDueDate == null
                              ? '不安排下一针'
                              : _formatDate(_nextDueDate!),
                        ),
                        trailing: _nextDueDate == null
                            ? const Icon(Icons.event_outlined)
                            : IconButton(
                                key: const ValueKey(
                                    'rabbit-vaccination-next-due-clear'),
                                tooltip: '清除',
                                onPressed: _saving
                                    ? null
                                    : () => setState(() => _nextDueDate = null),
                                icon: const Icon(Icons.close),
                              ),
                        onTap: _saving ? null : _pickNextDueDate,
                      ),
                      if (cageId != null && cageRabbitCount > 1) ...[
                        const SizedBox(height: 8),
                        CheckboxListTile(
                          key: const ValueKey('rabbit-vaccination-whole-cage'),
                          contentPadding: EdgeInsets.zero,
                          value: _wholeCage,
                          onChanged: _saving
                              ? null
                              : (value) =>
                                  setState(() => _wholeCage = value == true),
                          controlAffinity: ListTileControlAffinity.leading,
                          title: const Text('同笼一起接种'),
                          subtitle: Text('本笼在栏 $cageRabbitCount 只，将一次全部记录'),
                        ),
                      ],
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-vaccination-remark'),
                        controller: _remarkController,
                        enabled: !_saving,
                        maxLines: 3,
                        maxLength: 240,
                        decoration: const InputDecoration(labelText: '备注（可选）'),
                      ),
                      if (_nextDueDate != null) ...[
                        const SizedBox(height: 8),
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: palette.warningSoft,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                                color: palette.warning.withAlpha(90)),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.info_outline, color: palette.warning),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  '到期后会出现在待接种列表，补种同一疫苗后自动消除。',
                                  style: TextStyle(color: palette.text),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  border: Border(top: BorderSide(color: palette.line)),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      OutlinedButton(
                        onPressed:
                            _saving ? null : () => Navigator.of(context).pop(),
                        child: const Text('取消'),
                      ),
                      const SizedBox(height: 8),
                      FilledButton.icon(
                        key: const ValueKey('rabbit-vaccination-submit'),
                        onPressed: _saving ? null : _submit,
                        icon: _saving
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.vaccines_outlined),
                        label: const Text('确认接种'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

DateTime _dateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);

String _formatDate(DateTime value) => DateFormat('yyyy-MM-dd').format(value);

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '提交失败，请检查网络后重试';
}
