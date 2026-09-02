import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/nfc.dart';
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
  /// 碰标签选中的那一行要滚到眼前，所以列表的滚动位置得归本页管。
  final _listController = ScrollController();

  /// 只挂在当前选中的那一行上，用来做最后一步精确对齐。
  final _selectedRowKey = GlobalKey();

  int? _selectedCageId;
  int? _pendingCageId;
  String? _pendingRequestId;
  var _saving = false;

  @override
  void dispose() {
    _listController.dispose();
    super.dispose();
  }

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
        final targets = _replacementTargets(items);
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 10),
              child: NfcCagePicker(
                key: const ValueKey('rabbit-replacement-cage-nfc'),
                houseId: widget.houseId,
                // 传全部笼位而不是只传候选：候选之外的笼被碰到时，要能说出
                // 它到底差在哪，而不是回一句「没找到这个笼位」。
                cages: items,
                enabled: !_saving,
                accepts: _acceptsTarget,
                rejectReason: _rejectReason,
                cageLabel: _cageLabel,
                idleLabel: '碰一下后备笼标签',
                waitingHint: '请将手机靠近目标后备笼的 NFC 标签',
                unavailableLabel: '设备不支持NFC或NFC未开启，请在下方列表中选择',
                onSelected: (cage) => _selectFromTag(cage, targets),
              ),
            ),
            if (targets.isEmpty)
              const Padding(
                padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                child: Center(
                  child: Text('当前兔舍没有启用、空闲且可接收后备兔的笼位'),
                ),
              )
            else
              Expanded(
                child: ListView.separated(
                  key: const ValueKey('rabbit-replacement-cage-list'),
                  controller: _listController,
                  padding: const EdgeInsets.fromLTRB(12, 4, 12, 16),
                  itemCount: targets.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final cage = targets[index];
                    final selected = cage.id == _selectedCageId;
                    final tile = RadioListTile<int>(
                      key: ValueKey('rabbit-replacement-cage-${cage.id}'),
                      value: cage.id,
                      groupValue: _selectedCageId,
                      onChanged: _saving
                          ? null
                          : (value) => setState(() => _selectedCageId = value),
                      title: Text(_cageLabel(cage)),
                      subtitle:
                          Text(cage.status == '2' ? '后备笼 · 空闲' : '空笼 · 空闲'),
                      selected: selected,
                    );
                    if (!selected) {
                      return tile;
                    }
                    return KeyedSubtree(key: _selectedRowKey, child: tile);
                  },
                ),
              ),
          ],
        );
      },
    );
  }

  List<Cage> _replacementTargets(List<Cage> items) {
    return items
        .where((cage) => isReplacementCageTarget(cage, widget.houseId))
        .toList()
      ..sort((a, b) => a.cageNumber.compareTo(b.cageNumber));
  }

  /// 碰标签能选中的笼位，和列表里列出来的是同一批。
  ///
  /// 复用 [isReplacementCageTarget] 而不是另写一条判断：两条规则一旦分头演化，
  /// 就会出现「列表里没有、碰一下却选中了」这种谁也解释不清的状态。
  bool _acceptsTarget(Cage cage) =>
      isReplacementCageTarget(cage, widget.houseId);

  /// 拒绝时说清是哪个笼、卡在哪一条，而不是笼统一句「不可选」。
  ///
  /// 顺序照现场排查的顺序走：先看是不是本舍的，再看停没停用，
  /// 再看用途对不对，最后才看有没有兔占着。
  String _rejectReason(Cage cage) {
    final name = _cageLabel(cage);
    if (cage.houseId != widget.houseId) {
      return '$name 不属于当前兔舍，不能作为后备兔笼';
    }
    if (!cage.isEnabled) {
      return '$name 已停用，不能作为后备兔笼';
    }
    if (cage.status != '0' && cage.status != '2') {
      return '$name 是${cage.usageLabel}笼，不能作为后备兔笼';
    }
    if (cage.rabbitCount > 0) {
      return '$name 已有 ${cage.rabbitCount} 只兔，请选空闲笼位';
    }
    return '$name ${cage.entryBlockedReason ?? '不能作为后备兔笼'}';
  }

  /// 碰中的笼位既要选中，也要滚到看得见的地方。
  ///
  /// 只打勾不滚动的话，选中的那一行常常在屏外，用户看到的是「碰了没反应」，
  /// 于是又碰一次。
  void _selectFromTag(Cage cage, List<Cage> targets) {
    setState(() => _selectedCageId = cage.id);
    final index = targets.indexWhere((item) => item.id == cage.id);
    if (index >= 0) {
      _revealCage(index, targets.length);
    }
  }

  /// 分两步滚：先按平均行高粗滚过去，再按真实行位置精确对齐。
  ///
  /// 少了第一步不行——列表是懒加载的，屏外太远的行压根没建出来，
  /// `ensureVisible` 拿不到 context；少了第二步也不行——平均行高在大字号下会偏，
  /// 粗滚之后那一行可能只露出半截。
  void _revealCage(int index, int itemCount) {
    _afterNextFrame(() {
      if (_listController.hasClients && itemCount > 0) {
        final position = _listController.position;
        final rowExtent =
            (position.maxScrollExtent + position.viewportDimension) / itemCount;
        final offset =
            index * rowExtent - (position.viewportDimension - rowExtent) / 2;
        _listController.jumpTo(
          offset.clamp(position.minScrollExtent, position.maxScrollExtent),
        );
      }
      _afterNextFrame(() {
        final rowContext = _selectedRowKey.currentContext;
        if (rowContext != null) {
          Scrollable.ensureVisible(
            rowContext,
            alignment: 0.5,
            duration: const Duration(milliseconds: 180),
          );
        }
      });
    });
  }

  /// 帧末回调本身不会拉起下一帧，而第二步对齐必须等粗滚后的布局落完。
  /// 不显式要一帧的话，刚好没有其他改动时回调就永远排在队里不执行。
  void _afterNextFrame(VoidCallback action) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        action();
      }
    });
    WidgetsBinding.instance.scheduleFrame();
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
