import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/range.dart';
import 'package:rabbit_flutter/src/domain/rabbits/range_entry.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

Future<void> showRabbitRangeEntrySheet({
  required BuildContext context,
  required int houseId,
}) {
  return showAppModalSheet<void>(
    context: context,
    builder: (_) => _RangeRabbitEntrySheet(houseId: houseId),
  );
}

class _RangeRabbitEntrySheet extends ConsumerStatefulWidget {
  const _RangeRabbitEntrySheet({required this.houseId});

  final int houseId;

  @override
  ConsumerState<_RangeRabbitEntrySheet> createState() =>
      _RangeRabbitEntrySheetState();
}

class _RangeRabbitEntrySheetState
    extends ConsumerState<_RangeRabbitEntrySheet> {
  final _formKey = GlobalKey<FormState>();
  final _rowStart = TextEditingController(text: '1');
  final _rowEnd = TextEditingController(text: '1');
  final _positionStart = TextEditingController(text: '1');
  final _positionEnd = TextEditingController(text: '1');
  final _layerStart = TextEditingController(text: '1');
  final _layerEnd = TextEditingController(text: '1');
  final _count = TextEditingController(text: '1');
  final _breed = TextEditingController();
  var _type = '2';
  var _gender = '0';
  var _saving = false;
  RangeRabbitEntryResult? _result;

  @override
  void initState() {
    super.initState();
    for (final controller in [
      _rowStart,
      _rowEnd,
      _positionStart,
      _positionEnd,
      _layerStart,
      _layerEnd,
      _count,
    ]) {
      controller.addListener(_refreshPreview);
    }
  }

  @override
  void dispose() {
    for (final controller in [
      _rowStart,
      _rowEnd,
      _positionStart,
      _positionEnd,
      _layerStart,
      _layerEnd,
      _count,
      _breed,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _refreshPreview() {
    if (mounted) setState(() => _result = null);
  }

  int? _positive(TextEditingController controller) {
    final value = int.tryParse(controller.text.trim());
    return value != null && value > 0 ? value : null;
  }

  CageCoordinateRange? get _range {
    final values = [
      _positive(_rowStart),
      _positive(_rowEnd),
      _positive(_positionStart),
      _positive(_positionEnd),
      _positive(_layerStart),
      _positive(_layerEnd),
    ];
    if (values.any((value) => value == null)) return null;
    return CageCoordinateRange.normalized(
      rowStart: values[0]!,
      rowEnd: values[1]!,
      positionStart: values[2]!,
      positionEnd: values[3]!,
      layerStart: values[4]!,
      layerEnd: values[5]!,
    );
  }

  int get _rabbitsPerCage => _type == '2' ? _positive(_count) ?? 0 : 1;

  CageRangePreview? _preview(Iterable<Cage> cages) {
    final range = _range;
    if (range == null || !range.isValid || _rabbitsPerCage <= 0) return null;
    return CageRangePreview.fromCages(
      cages: cages,
      range: range,
      rabbitType: _type,
      rabbitsPerCage: _rabbitsPerCage,
    );
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final cages = ref.watch(houseCagesProvider(widget.houseId));
    return cages.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => Padding(
        padding: const EdgeInsets.all(20),
        child: Text('笼位加载失败：$error'),
      ),
      data: (items) {
        final preview = _preview(items);
        return SafeArea(
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              18,
              20,
              20 + MediaQuery.viewInsetsOf(context).bottom,
            ),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('按笼位范围录入',
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 6),
                  Text(
                    '填写起止排、位、层后先核对预览。未编排笼位不能参与范围录入。',
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: palette.muted),
                  ),
                  const SizedBox(height: 16),
                  Flexible(
                    child: SingleChildScrollView(
                      child: Column(
                        children: [
                          _RangeAxisFields(
                              label: '排', start: _rowStart, end: _rowEnd),
                          const SizedBox(height: 10),
                          _RangeAxisFields(
                              label: '位',
                              start: _positionStart,
                              end: _positionEnd),
                          const SizedBox(height: 10),
                          _RangeAxisFields(
                              label: '层', start: _layerStart, end: _layerEnd),
                          const SizedBox(height: 14),
                          DropdownButtonFormField<String>(
                            key: const ValueKey('range-entry-type'),
                            value: _type,
                            decoration:
                                const InputDecoration(labelText: '兔子类型'),
                            items: const [
                              DropdownMenuItem(value: '0', child: Text('种兔')),
                              DropdownMenuItem(value: '1', child: Text('后备兔')),
                              DropdownMenuItem(value: '2', child: Text('商品兔')),
                            ],
                            onChanged: _saving
                                ? null
                                : (value) {
                                    if (value != null) {
                                      setState(() {
                                        _type = value;
                                        if (value != '2') _count.text = '1';
                                        _result = null;
                                      });
                                    }
                                  },
                          ),
                          const SizedBox(height: 10),
                          DropdownButtonFormField<String>(
                            value: _gender,
                            decoration: const InputDecoration(labelText: '性别'),
                            items: const [
                              DropdownMenuItem(value: '0', child: Text('母')),
                              DropdownMenuItem(value: '1', child: Text('公')),
                            ],
                            onChanged: _saving
                                ? null
                                : (value) {
                                    if (value != null) {
                                      setState(() {
                                        _gender = value;
                                        _result = null;
                                      });
                                    }
                                  },
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            key: const ValueKey('range-entry-count'),
                            controller: _count,
                            enabled: _type == '2' && !_saving,
                            keyboardType: TextInputType.number,
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly
                            ],
                            decoration: InputDecoration(
                              labelText: '每笼数量',
                              helperText: _type == '2'
                                  ? '商品兔每笼 1-10 只'
                                  : '种兔和后备兔每笼固定 1 只',
                            ),
                            validator: (_) =>
                                _rabbitsPerCage >= 1 && _rabbitsPerCage <= 10
                                    ? null
                                    : '请输入 1-10',
                          ),
                          const SizedBox(height: 10),
                          TextFormField(
                            controller: _breed,
                            enabled: !_saving,
                            maxLength: 100,
                            decoration:
                                const InputDecoration(labelText: '品种（可选）'),
                          ),
                          const SizedBox(height: 14),
                          if (preview == null)
                            const _PreviewMessage(text: '填写完整坐标后显示预览。')
                          else
                            _RangePreview(preview: preview),
                          if (_result != null) ...[
                            const SizedBox(height: 12),
                            _SubmitResult(result: _result!),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: _saving
                              ? null
                              : () => Navigator.of(context).pop(),
                          child: const Text('取消'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: FilledButton(
                          key: const ValueKey('range-entry-submit'),
                          onPressed: _saving ||
                                  preview == null ||
                                  preview.range.slotCount > maxRangeCageSlots ||
                                  preview.range.slotCount *
                                          preview.rabbitsPerCage >
                                      maxRangeRabbits ||
                                  preview.eligible.isEmpty
                              ? null
                              : () => _submit(preview),
                          child: _saving
                              ? const SizedBox.square(
                                  dimension: 20,
                                  child:
                                      CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Text('确认录入'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _submit(CageRangePreview preview) async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final result =
          await ref.read(rabbitRepositoryProvider).createRabbitsInRange(
                houseId: widget.houseId,
                rowStart: preview.range.rowStart,
                rowEnd: preview.range.rowEnd,
                positionStart: preview.range.positionStart,
                positionEnd: preview.range.positionEnd,
                layerStart: preview.range.layerStart,
                layerEnd: preview.range.layerEnd,
                rabbitsPerCage: _rabbitsPerCage,
                type: _type,
                gender: _gender,
                arrivalMethod: '0',
                arrivalDate: DateTime.now(),
                breed: _breed.text,
              );
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) setState(() => _result = result);
    } catch (error) {
      if (!mounted) return;
      final message = error is ApiException ? error.message : error.toString();
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

class _RangeAxisFields extends StatelessWidget {
  const _RangeAxisFields(
      {required this.label, required this.start, required this.end});

  final String label;
  final TextEditingController start;
  final TextEditingController end;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(width: 38, child: Text(label)),
        Expanded(child: _NumberField(controller: start, label: '起始')),
        const Padding(
            padding: EdgeInsets.symmetric(horizontal: 8), child: Text('至')),
        Expanded(child: _NumberField(controller: end, label: '结束')),
      ],
    );
  }
}

class _NumberField extends StatelessWidget {
  const _NumberField({required this.controller, required this.label});

  final TextEditingController controller;
  final String label;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: InputDecoration(labelText: label, isDense: true),
      validator: (value) =>
          (int.tryParse(value ?? '') ?? 0) > 0 ? null : '大于 0',
    );
  }
}

class _PreviewMessage extends StatelessWidget {
  const _PreviewMessage({required this.text});

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
      ),
      child: Text(text, style: TextStyle(color: palette.muted)),
    );
  }
}

class _RangePreview extends StatelessWidget {
  const _RangePreview({required this.preview});

  final CageRangePreview preview;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final tooLarge = preview.range.slotCount > maxRangeCageSlots ||
        preview.range.slotCount * preview.rabbitsPerCage > maxRangeRabbits;
    return Container(
      key: const ValueKey('range-entry-preview'),
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: tooLarge ? palette.warningSoft : palette.successSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: tooLarge ? palette.warning : palette.success),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('预览：${preview.range.label}',
              style: const TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(
              '可入栏 ${preview.eligible.length} 笼，预计 ${preview.enteredRabbitCount} 只'),
          Text(
              '跳过 ${preview.blocked.length} 笼，缺笼 ${preview.missingCageCount} 个坐标'),
          if (preview.unplacedCageCount > 0)
            Text('另有 ${preview.unplacedCageCount} 个未编排笼位，补齐坐标后才能选择'),
          if (preview.blocked.isNotEmpty) ...[
            const SizedBox(height: 6),
            for (final candidate in preview.blocked.take(6))
              Text('${candidate.cage.cageNumber}：${candidate.blockedReason}'),
            if (preview.blocked.length > 6)
              Text('其余 ${preview.blocked.length - 6} 笼见提交结果'),
          ],
          if (tooLarge)
            const Padding(
              padding: EdgeInsets.only(top: 6),
              child: Text('范围过大，请缩小范围或降低每笼数量。'),
            ),
        ],
      ),
    );
  }
}

class _SubmitResult extends StatelessWidget {
  const _SubmitResult({required this.result});

  final RangeRabbitEntryResult result;

  @override
  Widget build(BuildContext context) {
    return _PreviewMessage(
      text:
          '已录入 ${result.enteredRabbitCount} 只，涉及 ${result.enteredCageCount} 笼。'
          '${result.skippedCages.isEmpty ? '' : ' ${result.skippedCages.length} 笼未录入：${result.skippedCages.take(4).map((item) => '${item.cageNumber}${item.reason}').join('；')}'}',
    );
  }
}
