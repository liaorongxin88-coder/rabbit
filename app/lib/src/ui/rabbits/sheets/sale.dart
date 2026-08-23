import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

Future<bool> showRabbitSaleSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
}) async {
  final sold = await showAppModalSheet<bool>(
    context: context,
    builder: (context) => _RabbitSaleSheet(houseId: houseId, rabbit: rabbit),
  );
  return sold ?? false;
}

class _RabbitSaleSheet extends ConsumerStatefulWidget {
  const _RabbitSaleSheet({required this.houseId, required this.rabbit});

  final int houseId;
  final Rabbit rabbit;

  @override
  ConsumerState<_RabbitSaleSheet> createState() => _RabbitSaleSheetState();
}

class _RabbitSaleSheetState extends ConsumerState<_RabbitSaleSheet> {
  final _formKey = GlobalKey<FormState>();
  final _weightController = TextEditingController();
  final _unitPriceController = TextEditingController();
  final _customerController = TextEditingController();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  late DateTime _saleDate;
  var _confirmed = false;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _saleDate = _dateOnly(DateTime.now());
    final weight = widget.rabbit.weight;
    if (weight != null && weight > 0) {
      _weightController.text = weight.toStringAsFixed(2);
    }
  }

  @override
  void dispose() {
    _weightController.dispose();
    _unitPriceController.dispose();
    _customerController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _saleDate,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      helpText: '选择出售日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _saleDate = _dateOnly(picked));
    }
  }

  Future<void> _submit() async {
    if (_saving || !(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    if (!_confirmed) {
      _showMessage('请确认出售出栏的影响');
      return;
    }

    final totalWeight = double.parse(_weightController.text.trim());
    final unitPriceText = _unitPriceController.text.trim();
    final unitPrice =
        unitPriceText.isEmpty ? null : double.parse(unitPriceText);
    final customer = _customerController.text.trim();
    final remark = _remarkController.text.trim();
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'rabbitSale',
        'houseId': widget.houseId,
        'rabbitId': widget.rabbit.id,
        'saleDate': _saleDate.millisecondsSinceEpoch,
        'totalWeight': totalWeight,
        'unitPrice': unitPrice,
        'customer': customer,
        'remark': remark,
      }),
    );

    setState(() => _saving = true);
    try {
      await ref.read(rabbitRepositoryProvider).createRabbitSale(
            houseId: widget.houseId,
            rabbitId: widget.rabbit.id,
            saleTime: _saleDate,
            totalWeight: totalWeight,
            unitPrice: unitPrice,
            customer: customer,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }

      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      ref.invalidate(cageRabbitsProvider);
      ref.invalidate(cageSummaryProvider);

      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(content: Text('兔 #${widget.rabbit.id} 已出售出栏')),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(_errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final palette = AppPalette.of(context);
    final dateLabel = DateFormat('yyyy-MM-dd').format(_saleDate);
    final availableHeight =
        mediaQuery.size.height - mediaQuery.viewInsets.bottom;

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
                            '出售出栏',
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
                      ListTile(
                        key: const ValueKey('rabbit-sale-date'),
                        contentPadding: EdgeInsets.zero,
                        title: const Text('出售日期'),
                        subtitle: Text(dateLabel),
                        trailing: const Icon(Icons.calendar_today_outlined),
                        onTap: _saving ? null : _pickDate,
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-sale-weight'),
                        controller: _weightController,
                        enabled: !_saving,
                        keyboardType: const TextInputType.numberWithOptions(
                            decimal: true),
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: '销售重量（kg）*',
                          hintText: '请输入实际销售重量',
                        ),
                        validator: (value) {
                          final parsed = double.tryParse(value?.trim() ?? '');
                          return parsed == null || parsed <= 0
                              ? '请输入大于 0 的销售重量'
                              : null;
                        },
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-sale-unit-price'),
                        controller: _unitPriceController,
                        enabled: !_saving,
                        keyboardType: const TextInputType.numberWithOptions(
                            decimal: true),
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: '单价（元/kg，可选）',
                        ),
                        validator: (value) {
                          final text = value?.trim() ?? '';
                          if (text.isEmpty) {
                            return null;
                          }
                          final parsed = double.tryParse(text);
                          return parsed == null || parsed < 0
                              ? '请输入不小于 0 的单价'
                              : null;
                        },
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-sale-customer'),
                        controller: _customerController,
                        enabled: !_saving,
                        maxLength: 100,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(labelText: '客户（可选）'),
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-sale-remark'),
                        controller: _remarkController,
                        enabled: !_saving,
                        maxLines: 3,
                        maxLength: 240,
                        decoration: const InputDecoration(labelText: '备注（可选）'),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: palette.warningSoft,
                          borderRadius: BorderRadius.circular(8),
                          border:
                              Border.all(color: palette.warning.withAlpha(90)),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(Icons.info_outline, color: palette.warning),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                '出售后会退出所有活跃批次，种母兔和后备母兔的未完成生产周期及待办也会结束。',
                                style: TextStyle(color: palette.text),
                              ),
                            ),
                          ],
                        ),
                      ),
                      CheckboxListTile(
                        key: const ValueKey('rabbit-sale-confirm'),
                        contentPadding: EdgeInsets.zero,
                        value: _confirmed,
                        onChanged: _saving
                            ? null
                            : (value) =>
                                setState(() => _confirmed = value == true),
                        controlAffinity: ListTileControlAffinity.leading,
                        title: const Text('我确认出售信息无误，并了解该操作不可撤销'),
                      ),
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
                        key: const ValueKey('rabbit-sale-submit'),
                        onPressed: _saving || !_confirmed ? null : _submit,
                        icon: _saving
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.sell_outlined),
                        label: const Text('确认出售出栏'),
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

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '提交失败，请检查网络后重试';
}
