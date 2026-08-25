import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/cage_target.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

Future<bool> showRabbitReplacementSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
}) async {
  if (houseId <= 0 ||
      rabbit.houseId != houseId ||
      rabbit.type != '2' ||
      !rabbit.isActive) {
    return false;
  }
  final converted = await showAppModalSheet<bool>(
    context: context,
    builder: (context) => _RabbitReplacementSheet(
      houseId: houseId,
      rabbit: rabbit,
    ),
  );
  return converted ?? false;
}

class _RabbitReplacementSheet extends ConsumerStatefulWidget {
  const _RabbitReplacementSheet({
    required this.houseId,
    required this.rabbit,
  });

  final int houseId;
  final Rabbit rabbit;

  @override
  ConsumerState<_RabbitReplacementSheet> createState() =>
      _RabbitReplacementSheetState();
}

class _RabbitReplacementSheetState
    extends ConsumerState<_RabbitReplacementSheet> {
  int? _selectedCageId;
  int? _pendingCageId;
  String? _pendingRequestId;
  var _saving = false;

  @override
  Widget build(BuildContext context) {
    final cages = ref.watch(houseCagesProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);

    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: mediaQuery.size.height * 0.84),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(context),
            Flexible(child: _buildBody(cages)),
            _buildActions(context),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 8, 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '留种转后备',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '商品兔 #${widget.rabbit.id} · 选择空闲后备笼',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: '关闭',
            onPressed: _saving ? null : () => Navigator.of(context).pop(false),
            icon: const Icon(Icons.close),
          ),
        ],
      ),
    );
  }

  Widget _buildBody(AsyncValue<List<Cage>> cages) {
    return cages.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('无法加载可用笼位，请检查网络后重试'),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () =>
                  ref.invalidate(houseCagesProvider(widget.houseId)),
              icon: const Icon(Icons.refresh),
              label: const Text('重试'),
            ),
          ],
        ),
      ),
      data: (items) {
        final targets = items
            .where((cage) => isReplacementCageTarget(cage, widget.houseId))
            .toList()
          ..sort((a, b) => a.cageNumber.compareTo(b.cageNumber));
        if (targets.isEmpty) {
          return const Padding(
            padding: EdgeInsets.all(20),
            child: Center(
              child: Text('当前兔舍没有启用、空闲且可接收后备兔的笼位'),
            ),
          );
        }
        return ListView.separated(
          key: const ValueKey('rabbit-replacement-cage-list'),
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 16),
          itemCount: targets.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final cage = targets[index];
            return RadioListTile<int>(
              key: ValueKey('rabbit-replacement-cage-${cage.id}'),
              value: cage.id,
              groupValue: _selectedCageId,
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _selectedCageId = value),
              title: Text(_cageLabel(cage)),
              subtitle: Text(cage.status == '2' ? '后备笼 · 空闲' : '空笼 · 空闲'),
            );
          },
        );
      },
    );
  }

  Widget _buildActions(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AppPalette.of(context).surface,
        border: Border(top: BorderSide(color: AppPalette.of(context).line)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('确认后将退出当前批次，并转为在栏后备兔。'),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed:
                        _saving ? null : () => Navigator.of(context).pop(false),
                    child: const Text('取消'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    key: const ValueKey('rabbit-replacement-submit'),
                    onPressed:
                        _saving || _selectedCageId == null ? null : _submit,
                    child: _saving
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('确认转后备'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    if (_saving) {
      return;
    }
    final cageId = _selectedCageId;
    if (cageId == null) {
      return;
    }
    setState(() => _saving = true);
    try {
      final freshCages = await _refreshCages();
      if (freshCages == null || !mounted) {
        return;
      }
      final validation = validateRabbitCageTarget(
        cages: freshCages,
        houseId: widget.houseId,
        cageId: cageId,
        rabbitType: '1',
        requireEmpty: true,
      );
      if (!validation.isValid) {
        _showMessage(validation.message!);
        return;
      }

      if (_pendingCageId != cageId || _pendingRequestId == null) {
        _pendingCageId = cageId;
        _pendingRequestId = const Uuid().v4();
      }
      final conversions =
          await ref.read(rabbitRepositoryProvider).convertToReplacement(
                houseId: widget.houseId,
                rabbitIds: [widget.rabbit.id],
                targetCageId: cageId,
                forceExitBatch: true,
                requestId: _pendingRequestId,
              );
      if (conversions.isEmpty) {
        throw StateError('留种转后备未返回处理结果');
      }
      _pendingCageId = null;
      _pendingRequestId = null;
      _invalidateAfterConversion();
      if (!mounted) {
        return;
      }
      final target = validation.cage!;
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(
            content:
                Text('商品兔 #${widget.rabbit.id} 已转入 ${_cageLabel(target)}')),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(
          error is ApiException ? error.message : '留种转后备失败，请检查网络后重试',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<List<Cage>?> _refreshCages() async {
    try {
      return await ref.refresh(houseCagesProvider(widget.houseId).future);
    } catch (_) {
      if (mounted) {
        _showMessage('笼位状态刷新失败，请检查网络后重试');
      }
      return null;
    }
  }

  void _invalidateAfterConversion() {
    final activeRequest = RabbitBatchMembershipRequest(
      houseId: widget.houseId,
      rabbitId: widget.rabbit.id,
    );
    ref.invalidate(
      rabbitDetailProvider(
        RabbitDetailRequest(
          houseId: widget.houseId,
          rabbitId: widget.rabbit.id,
        ),
      ),
    );
    ref.invalidate(houseRabbitsProvider(widget.houseId));
    ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
    ref.invalidate(houseCagesProvider(widget.houseId));
    ref.invalidate(rabbitBatchMembershipsProvider(activeRequest));
    ref.invalidate(
      rabbitBatchMembershipsProvider(
        RabbitBatchMembershipRequest(
          houseId: widget.houseId,
          rabbitId: widget.rabbit.id,
          active: false,
        ),
      ),
    );
    ref.invalidate(homeEventsProvider);
  }

  void _showMessage(String message) {
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  String _cageLabel(Cage cage) {
    return cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;
  }
}
