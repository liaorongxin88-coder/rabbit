import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

Future<bool> showRabbitPromotionSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
}) async {
  return await showAppModalSheet<bool>(
        context: context,
        builder: (context) => _PromotionSheet(
          houseId: houseId,
          rabbit: rabbit,
        ),
      ) ??
      false;
}

class _PromotionSheet extends ConsumerStatefulWidget {
  const _PromotionSheet({
    required this.houseId,
    required this.rabbit,
  });

  final int houseId;
  final Rabbit rabbit;

  @override
  ConsumerState<_PromotionSheet> createState() => _PromotionSheetState();
}

class _PromotionSheetState extends ConsumerState<_PromotionSheet> {
  static const _uuid = Uuid();

  final _formKey = GlobalKey<FormState>();
  final _reasonController = TextEditingController();
  late final String _requestId;
  var _confirmed = false;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _requestId = _uuid.v4();
  }

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || !_confirmed) {
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(rabbitRepositoryProvider).promoteReplacement(
            houseId: widget.houseId,
            rabbitId: widget.rabbit.id,
            reason: _reasonController.text,
            requestId: _requestId,
          );
      if (!mounted) return;
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(content: Text('兔 #${widget.rabbit.id} 已转为种兔')),
      );
    } catch (error) {
      if (!mounted) return;
      final message = error is ApiException ? error.message : '转种失败，请检查网络后重试';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final palette = AppPalette.of(context);
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
                            '后备兔转种',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '兔 #${widget.rabbit.id} · 保留原兔号和历史记录',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
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
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                    children: [
                      TextFormField(
                        key: const ValueKey('rabbit-promotion-reason'),
                        controller: _reasonController,
                        enabled: !_saving,
                        maxLength: 200,
                        maxLines: 3,
                        decoration: const InputDecoration(
                          labelText: '转种原因*',
                          hintText: '例如：育种计划调整',
                          helperText: '成熟日前提前转种时，服务端会核验并记录此原因。',
                        ),
                        validator: (value) =>
                            value == null || value.trim().isEmpty
                                ? '请填写转种原因'
                                : null,
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: palette.warningSoft,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: palette.warning.withAlpha(90),
                          ),
                        ),
                        child: const Text(
                          '转种后仍使用当前兔号和笼位。母兔进入待催情生产流程，公兔进入可配状态。',
                        ),
                      ),
                      CheckboxListTile(
                        key: const ValueKey('rabbit-promotion-confirm'),
                        contentPadding: EdgeInsets.zero,
                        value: _confirmed,
                        onChanged: _saving
                            ? null
                            : (value) =>
                                setState(() => _confirmed = value == true),
                        controlAffinity: ListTileControlAffinity.leading,
                        title: const Text('我确认转种信息无误'),
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
                  child: Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: _saving
                              ? null
                              : () => Navigator.of(context).pop(),
                          child: const Text('取消'),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: FilledButton.icon(
                          key: const ValueKey('rabbit-promotion-submit'),
                          onPressed: _saving || !_confirmed ? null : _submit,
                          icon: _saving
                              ? const SizedBox.square(
                                  dimension: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Icon(Icons.arrow_upward_outlined),
                          label: const Text('确认转种'),
                        ),
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
