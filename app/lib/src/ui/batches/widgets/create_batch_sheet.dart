import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

Future<void> showCreateBatchSheet({
  required BuildContext context,
  required int houseId,
  required String houseName,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _CreateBatchSheet(
      houseId: houseId,
      houseName: houseName,
    ),
  );
}

class _CreateBatchSheet extends ConsumerStatefulWidget {
  const _CreateBatchSheet({
    required this.houseId,
    required this.houseName,
  });

  final int houseId;
  final String houseName;

  @override
  ConsumerState<_CreateBatchSheet> createState() => _CreateBatchSheetState();
}

class _CreateBatchSheetState extends ConsumerState<_CreateBatchSheet> {
  final _codeController = TextEditingController();
  final _remarkController = TextEditingController();
  final _selectedFemaleIds = <int>{};
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _codeController.text = 'B${DateFormat('yyyyMMdd').format(DateTime.now())}';
  }

  @override
  void dispose() {
    _codeController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  List<Rabbit> _femaleBreeders(List<Rabbit> rabbits) {
    return rabbits.where((r) => r.type == '0' && r.gender == '0').toList()
      ..sort((a, b) => a.id.compareTo(b.id));
  }

  Future<void> _submit() async {
    final code = _codeController.text.trim();
    if (code.isEmpty) {
      _showMessage('请输入批次编号');
      return;
    }
    if (_selectedFemaleIds.isEmpty) {
      _showMessage('请至少选择一只种母兔');
      return;
    }

    setState(() => _saving = true);
    try {
      await ref.read(batchRepositoryProvider).createBatch(
            houseId: widget.houseId,
            batchCode: code,
            femaleRabbitIds: _selectedFemaleIds.toList(),
            remark: _remarkController.text,
          );
      ref.invalidate(houseBatchesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('批次 $code 已创建（${_selectedFemaleIds.length} 只母兔）')),
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
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final keyboardInset = MediaQuery.of(context).viewInsets.bottom;

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
            data: (rabbits) {
              final females = _femaleBreeders(rabbits);
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
                                '创建生产批次',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                widget.houseName,
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
                        const _InfoBox(
                          text: '批次用于驱动配种、摸胎、分娩、断奶等生产提醒。'
                              '请先在笼位录入种母兔，再创建批次。',
                        ),
                        const SizedBox(height: 14),
                        TextField(
                          controller: _codeController,
                          enabled: !_saving,
                          decoration: const InputDecoration(
                            labelText: '批次编号',
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: _remarkController,
                          enabled: !_saving,
                          maxLines: 2,
                          decoration: const InputDecoration(
                            labelText: '备注（可选）',
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          '选择种母兔（已选 ${_selectedFemaleIds.length} 只）',
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                        const SizedBox(height: 8),
                        if (females.isEmpty)
                          const Text('暂无种母兔，请先在笼位录入种母兔。')
                        else
                          ...females.map((rabbit) {
                            final selected =
                                _selectedFemaleIds.contains(rabbit.id);
                            return CheckboxListTile(
                              value: selected,
                              onChanged: _saving
                                  ? null
                                  : (value) {
                                      setState(() {
                                        if (value == true) {
                                          _selectedFemaleIds.add(rabbit.id);
                                        } else {
                                          _selectedFemaleIds.remove(rabbit.id);
                                        }
                                      });
                                    },
                              title: Text(
                                  '兔 #${rabbit.id} · ${rabbit.breed.isEmpty ? '未填品种' : rabbit.breed}'),
                              subtitle: Text('笼位 #${rabbit.cageId}'),
                              contentPadding: EdgeInsets.zero,
                            );
                          }),
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
                              onPressed:
                                  _saving || females.isEmpty ? null : _submit,
                              child: _saving
                                  ? const SizedBox.square(
                                      dimension: 20,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Text('创建批次'),
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
