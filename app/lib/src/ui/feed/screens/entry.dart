import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/feed/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/feed/log.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/dashboard/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

class FeedEntryScreen extends ConsumerStatefulWidget {
  const FeedEntryScreen({
    super.key,
    required this.houseId,
    this.initialRabbitId,
  });

  final int houseId;
  final int? initialRabbitId;

  @override
  ConsumerState<FeedEntryScreen> createState() => _FeedEntryScreenState();
}

class _FeedEntryScreenState extends ConsumerState<FeedEntryScreen> {
  final _amountController = TextEditingController();
  final _feedTypeController = TextEditingController(text: '日常投喂');
  final _remarkController = TextEditingController();
  final _selectedCageIds = <int>{};
  final _writeRequest = BatchWriteRequestController();
  final _allocationAmounts = <String, String>{};
  StreamSubscription<NfcLaunchEvent>? _nfcSubscription;
  StateController<bool>? _captureFlag;
  DateTime _feedTime = farmNow();
  FeedAllocationPreview? _allocationPreview;
  String? _previewFingerprint;
  String? _errorMessage;
  String? _successMessage;
  String? _nfcHint;
  var _saving = false;
  var _previewing = false;
  var _nfcListening = false;
  var _initialSelectionApplied = false;
  var _viewGeneration = 0;
  var _previewSequence = 0;

  @override
  void didUpdateWidget(covariant FeedEntryScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    final houseChanged = oldWidget.houseId != widget.houseId;
    if (!houseChanged && oldWidget.initialRabbitId == widget.initialRabbitId) {
      return;
    }
    _viewGeneration++;
    _previewSequence++;
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseCaptureFlag();
    _nfcListening = false;
    _selectedCageIds.clear();
    _initialSelectionApplied = false;
    _nfcHint = null;
    _errorMessage = null;
    _successMessage = null;
    _clearAllocationPreview();
    _writeRequest.startNewDraft();
    if (houseChanged) {
      _amountController.clear();
      _feedTypeController.text = '日常投喂';
      _remarkController.clear();
      _feedTime = farmNow();
      _saving = false;
      _previewing = false;
    }
  }

  @override
  void dispose() {
    _viewGeneration++;
    _previewSequence++;
    _nfcSubscription?.cancel();
    _releaseCaptureFlag();
    _amountController.dispose();
    _feedTypeController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  void _releaseCaptureFlag() {
    _captureFlag?.state = false;
    _captureFlag = null;
  }

  void _stopNfcCapture({String? hint}) {
    _nfcSubscription?.cancel();
    _nfcSubscription = null;
    _releaseCaptureFlag();
    if (!mounted) {
      return;
    }
    setState(() {
      _nfcListening = false;
      _nfcHint = hint;
    });
  }

  Future<void> _startNfcCapture() async {
    if (_nfcListening || _saving) {
      return;
    }
    final generation = _viewGeneration;
    final houseId = widget.houseId;
    try {
      final service = ref.read(nfcIntentServiceProvider);
      await service.initialize();
      if (!_isCurrent(generation, houseId)) {
        return;
      }
      final flag = ref.read(nfcCaptureActiveProvider.notifier);
      flag.state = true;
      _captureFlag = flag;
      setState(() {
        _nfcListening = true;
        _nfcHint = '请将手机靠近已投喂笼位的 NFC 标签';
      });
      _nfcSubscription = service.events.listen(_onNfcEvent);
    } catch (_) {
      if (_isCurrent(generation, houseId)) {
        setState(() {
          _nfcHint = '无法使用 NFC，请检查系统授权后重试';
        });
      }
    }
  }

  Future<void> _onNfcEvent(NfcLaunchEvent event) async {
    final generation = _viewGeneration;
    final houseId = widget.houseId;
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      if (target.houseId != houseId) {
        _stopNfcCapture(hint: '该标签属于其它兔舍，未选中');
        return;
      }
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      if (!_isCurrent(generation, houseId)) {
        return;
      }
      final cages = ref.read(houseCagesProvider(houseId)).valueOrNull;
      final rabbits =
          ref.read(allActiveHouseRabbitsProvider(houseId)).valueOrNull;
      if (cages == null || rabbits == null) {
        _stopNfcCapture(hint: '笼位数据正在加载，请稍后重试');
        return;
      }
      final cage = _cageById(cages, binding.cageId);
      if (cage == null) {
        _stopNfcCapture(hint: '未在当前兔舍找到该笼位，请刷新后重试');
        return;
      }
      final rabbitCount = rabbits
          .where((rabbit) => rabbit.isActive && rabbit.cageId == cage.id)
          .length;
      if (rabbitCount == 0) {
        _stopNfcCapture(hint: '${_cageLabel(cage)} 没有在栏兔只，未选中');
        return;
      }
      setState(() {
        _selectedCageIds.add(cage.id);
      });
      _stopNfcCapture(hint: '已选中 ${_cageLabel(cage)}，包含 $rabbitCount 只');
    } catch (error) {
      if (_isCurrent(generation, houseId)) {
        _stopNfcCapture(
          hint: error is ApiException ? error.message : '读取标签失败，请重试',
        );
      }
    }
  }

  Cage? _cageById(List<Cage> cages, int cageId) {
    for (final cage in cages) {
      if (cage.id == cageId) {
        return cage;
      }
    }
    return null;
  }

  String _cageLabel(Cage cage) =>
      cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber;

  void _applyInitialRabbitSelection(List<Rabbit> rabbits) {
    if (_initialSelectionApplied) {
      return;
    }
    _initialSelectionApplied = true;
    final rabbitId = widget.initialRabbitId;
    if (rabbitId == null || rabbitId <= 0) {
      return;
    }
    for (final rabbit in rabbits) {
      if (rabbit.id == rabbitId && rabbit.isActive) {
        _selectedCageIds.add(rabbit.cageId);
        _nfcHint = '已根据首页提醒选中兔 #$rabbitId 所在笼位';
        return;
      }
    }
    _errorMessage = '提醒对应的兔只不在当前兔舍，请刷新后重试';
  }

  Future<void> _pickFeedDate() async {
    if (_saving) {
      return;
    }
    final generation = _viewGeneration;
    final houseId = widget.houseId;
    final selected = await showDatePicker(
      context: context,
      initialDate: _feedTime,
      firstDate: farmToday().subtract(const Duration(days: 30)),
      lastDate: farmToday(),
    );
    if (selected == null || !_isCurrent(generation, houseId)) {
      return;
    }
    setState(() {
      _feedTime = DateTime(
        selected.year,
        selected.month,
        selected.day,
        _feedTime.hour,
        _feedTime.minute,
      );
      _clearAllocationPreview();
    });
  }

  void _toggleCage(int cageId) {
    if (_saving) {
      return;
    }
    setState(() {
      if (!_selectedCageIds.add(cageId)) {
        _selectedCageIds.remove(cageId);
      }
      _errorMessage = null;
      _successMessage = null;
      _clearAllocationPreview();
    });
  }

  void _clearAllocationPreview() {
    _previewSequence++;
    _allocationPreview = null;
    _previewFingerprint = null;
    _allocationAmounts.clear();
  }

  List<int> _selectedRabbitIds(List<Rabbit> rabbits) {
    return rabbits
        .where(
          (rabbit) =>
              rabbit.isActive && _selectedCageIds.contains(rabbit.cageId),
        )
        .map((rabbit) => rabbit.id)
        .toSet()
        .toList()
      ..sort();
  }

  String _currentPreviewFingerprint(List<int> rabbitIds) {
    return canonicalBatchWriteFingerprint({
      'rabbitIds': rabbitIds,
      'feedTime': farmDateTimeToIso(_feedTime),
    });
  }

  void _amountChanged(String value) {
    final groups = _allocationPreview?.groups ?? const <FeedAllocationGroup>[];
    if (groups.length == 1 && _canAutoAllocate(groups.single)) {
      _allocationAmounts[groups.single.key] = value;
    }
    setState(() {
      _errorMessage = null;
      _successMessage = null;
    });
  }

  Future<FeedAllocationPreview?> _previewAllocations(
    List<int> rabbitIds,
  ) async {
    if (_previewing || rabbitIds.isEmpty) return _allocationPreview;
    final generation = _viewGeneration;
    final houseId = widget.houseId;
    final fingerprint = _currentPreviewFingerprint(rabbitIds);
    final sequence = ++_previewSequence;
    setState(() {
      _previewing = true;
      _errorMessage = null;
    });
    try {
      final preview = await ref.read(feedRepositoryProvider).previewAllocations(
            houseId: houseId,
            rabbitIds: rabbitIds,
            feedTime: _feedTime,
          );
      if (!_isCurrent(generation, houseId) ||
          sequence != _previewSequence ||
          fingerprint != _currentPreviewFingerprint(rabbitIds)) {
        return null;
      }
      setState(() {
        _allocationPreview = preview;
        _previewFingerprint = fingerprint;
        _allocationAmounts
          ..clear()
          ..addEntries(
            preview.groups.map(
              (group) => MapEntry(
                group.key,
                preview.groups.length == 1 && _canAutoAllocate(group)
                    ? _amountController.text.trim()
                    : '',
              ),
            ),
          );
      });
      return preview;
    } catch (error) {
      if (_isCurrent(generation, houseId) && sequence == _previewSequence) {
        setState(() {
          _errorMessage =
              error is ApiException ? error.message : '批次与阶段归属预览失败，请检查网络后重试';
        });
      }
      return null;
    } finally {
      if (_isCurrent(generation, houseId) && sequence == _previewSequence) {
        setState(() => _previewing = false);
      }
    }
  }

  Future<void> _submit(List<Cage> cages, List<Rabbit> rabbits) async {
    final generation = _viewGeneration;
    final houseId = widget.houseId;
    final amount = double.tryParse(_amountController.text.trim());
    final rabbitIds = _selectedRabbitIds(rabbits);
    if (rabbitIds.isEmpty) {
      setState(() => _errorMessage = '请选择至少一个有在栏兔只的笼位');
      return;
    }
    if (amount == null || amount <= 0) {
      setState(() => _errorMessage = '请输入大于 0 的投喂数量');
      return;
    }
    final previewFingerprint = _currentPreviewFingerprint(rabbitIds);
    var preview = _allocationPreview;
    if (preview == null || _previewFingerprint != previewFingerprint) {
      preview = await _previewAllocations(rabbitIds);
      if (preview == null) return;
    }
    final allocations = preview.groups.map((group) {
      return FeedBatchAllocation(
        batchId: group.batchId,
        phase: group.phase,
        amountKg: double.tryParse(_allocationAmounts[group.key] ?? '') ?? 0,
      );
    }).toList(growable: false);
    final allocationError = validateFeedAllocations(amount, allocations);
    if (allocationError != null) {
      setState(() => _errorMessage = allocationError);
      return;
    }
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'houseId': houseId,
        'rabbitIds': rabbitIds,
        'feedTime': farmDateTimeToIso(_feedTime),
        'amount': amount,
        'feedType': _feedTypeController.text,
        'allocations': allocations.map((item) => item.toJson()).toList(),
        'remark': _remarkController.text,
      }),
    );
    setState(() {
      _saving = true;
      _errorMessage = null;
      _successMessage = null;
    });
    try {
      await ref.read(feedRepositoryProvider).addFeedLog(
            houseId: houseId,
            draft: FeedLogDraft(
              rabbitIds: rabbitIds,
              feedTime: _feedTime,
              requestId: requestId,
              amount: amount,
              allocations: allocations,
              feedType: _feedTypeController.text,
              unit: 'kg',
              remark: _remarkController.text,
            ),
          );
      if (!_isCurrent(generation, houseId)) {
        return;
      }
      setState(() {
        _selectedCageIds.clear();
        _amountController.clear();
        _remarkController.clear();
        _clearAllocationPreview();
        _writeRequest.startNewDraft();
        _successMessage = '已录入 ${rabbitIds.length} 只兔的投喂记录';
      });
      ref.invalidate(houseCagesProvider(houseId));
      ref.invalidate(homeEventsProvider);
      ref.invalidate(dashboardSummaryProvider);
      for (final batchId in allocations.map((item) => item.batchId).nonNulls) {
        ref.invalidate(
          batchStatisticsProvider(
            BatchDetailRequest(houseId: houseId, batchId: batchId),
          ),
        );
      }
    } on ApiException catch (error) {
      if (_isCurrent(generation, houseId) &&
          (error.businessCode == 409 || error.statusCode == 409)) {
        setState(_clearAllocationPreview);
        await _previewAllocations(rabbitIds);
        if (_isCurrent(generation, houseId)) {
          setState(() {
            _errorMessage = '${error.message}，归属已刷新，请核对分组用量后重试';
          });
        }
      } else if (_isCurrent(generation, houseId)) {
        setState(
          () => _errorMessage = '${error.message}，投喂信息已保留，可重新提交',
        );
      }
    } catch (_) {
      if (_isCurrent(generation, houseId)) {
        setState(() => _errorMessage = '网络异常，投喂信息已保留，可重新提交');
      }
    } finally {
      if (_isCurrent(generation, houseId)) {
        setState(() => _saving = false);
      }
    }
  }

  bool _canAutoAllocate(FeedAllocationGroup group) =>
      group.batchId != null && group.phase != FeedAllocationPhase.unassigned;

  bool _isCurrent(int generation, int houseId) {
    return mounted &&
        generation == _viewGeneration &&
        houseId == widget.houseId;
  }

  @override
  Widget build(BuildContext context) {
    final permission = ref.watch(housePermissionProvider(widget.houseId));
    final cages = ref.watch(houseCagesProvider(widget.houseId));
    final rabbits = ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    return AppPage(
      title: '投喂录入',
      fallbackBackLocation: '/houses/${widget.houseId}',
      actions: [
        IconButton(
          tooltip: '刷新投喂资料',
          onPressed: _saving
              ? null
              : () {
                  ref.invalidate(houseCagesProvider(widget.houseId));
                  ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
                },
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: permission.when(
        data: (value) {
          if (!value.canEdit) {
            return const EmptyState(
              icon: Icons.lock_outline,
              title: '当前账号仅可查看',
              message: '投喂录入会修改生产记录，请联系兔舍管理员授予编辑权限。',
            );
          }
          return cages.when(
            data: (cageItems) => rabbits.when(
              data: (rabbitItems) {
                _applyInitialRabbitSelection(rabbitItems);
                return _FeedEntryForm(
                  cages: cageItems,
                  rabbits: rabbitItems,
                  selectedCageIds: _selectedCageIds,
                  amountController: _amountController,
                  feedTypeController: _feedTypeController,
                  remarkController: _remarkController,
                  feedTime: _feedTime,
                  saving: _saving,
                  previewing: _previewing,
                  allocationPreview: _allocationPreview,
                  allocationAmounts: _allocationAmounts,
                  nfcListening: _nfcListening,
                  nfcHint: _nfcHint,
                  errorMessage: _errorMessage,
                  successMessage: _successMessage,
                  onToggleCage: _toggleCage,
                  onPickFeedDate: _pickFeedDate,
                  onAmountChanged: _amountChanged,
                  onAllocationChanged: (key, value) {
                    setState(() {
                      _allocationAmounts[key] = value;
                      _errorMessage = null;
                    });
                  },
                  onPreview: () => _previewAllocations(
                    _selectedRabbitIds(rabbitItems),
                  ),
                  onStartNfc: _startNfcCapture,
                  onStopNfc: () => _stopNfcCapture(hint: '已停止读取 NFC 标签'),
                  onSubmit: () => _submit(cageItems, rabbitItems),
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => ErrorState(
                message: '无法读取兔只：$error',
                onRetry: () => ref
                    .invalidate(allActiveHouseRabbitsProvider(widget.houseId)),
              ),
            ),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => ErrorState(
              message: '无法读取笼位：$error',
              onRetry: () => ref.invalidate(houseCagesProvider(widget.houseId)),
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: '无法确认投喂权限：$error',
          onRetry: () =>
              ref.invalidate(housePermissionProvider(widget.houseId)),
        ),
      ),
    );
  }
}

class _FeedEntryForm extends StatelessWidget {
  const _FeedEntryForm({
    required this.cages,
    required this.rabbits,
    required this.selectedCageIds,
    required this.amountController,
    required this.feedTypeController,
    required this.remarkController,
    required this.feedTime,
    required this.saving,
    required this.previewing,
    required this.allocationPreview,
    required this.allocationAmounts,
    required this.nfcListening,
    required this.nfcHint,
    required this.errorMessage,
    required this.successMessage,
    required this.onToggleCage,
    required this.onPickFeedDate,
    required this.onAmountChanged,
    required this.onAllocationChanged,
    required this.onPreview,
    required this.onStartNfc,
    required this.onStopNfc,
    required this.onSubmit,
  });

  final List<Cage> cages;
  final List<Rabbit> rabbits;
  final Set<int> selectedCageIds;
  final TextEditingController amountController;
  final TextEditingController feedTypeController;
  final TextEditingController remarkController;
  final DateTime feedTime;
  final bool saving;
  final bool previewing;
  final FeedAllocationPreview? allocationPreview;
  final Map<String, String> allocationAmounts;
  final bool nfcListening;
  final String? nfcHint;
  final String? errorMessage;
  final String? successMessage;
  final ValueChanged<int> onToggleCage;
  final VoidCallback onPickFeedDate;
  final ValueChanged<String> onAmountChanged;
  final void Function(String key, String value) onAllocationChanged;
  final VoidCallback onPreview;
  final VoidCallback onStartNfc;
  final VoidCallback onStopNfc;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final rabbitsByCage = <int, List<Rabbit>>{};
    for (final rabbit in rabbits) {
      if (!rabbit.isActive) {
        continue;
      }
      rabbitsByCage.putIfAbsent(rabbit.cageId, () => []).add(rabbit);
    }
    final availableCages = cages
        .where((cage) => rabbitsByCage[cage.id]?.isNotEmpty == true)
        .toList()
      ..sort((left, right) => left.cageNumber.compareTo(right.cageNumber));
    final selectedRabbitCount = availableCages
        .where((cage) => selectedCageIds.contains(cage.id))
        .fold<int>(
            0, (total, cage) => total + (rabbitsByCage[cage.id]?.length ?? 0));
    final palette = AppPalette.of(context);

    return ListView(
      key: const ValueKey('feed-entry-scroll'),
      padding: AppSpacing.pagePadding,
      children: [
        Text('选择投喂笼位', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        Text(
          '可碰标签快速选择，也可以在下方手动勾选。',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            key: const ValueKey('feed-nfc-capture'),
            onPressed: saving ? null : (nfcListening ? onStopNfc : onStartNfc),
            icon: Icon(nfcListening ? Icons.stop_circle_outlined : Icons.nfc),
            label: Text(nfcListening ? '停止读取 NFC 标签' : '碰标签选择笼位'),
          ),
        ),
        if (nfcHint != null) ...[
          const SizedBox(height: 8),
          _FeedNotice(
            key: const ValueKey('feed-nfc-hint'),
            icon: nfcListening ? Icons.nfc : Icons.info_outline,
            color: nfcListening ? palette.primary : palette.muted,
            message: nfcHint!,
          ),
        ],
        const SizedBox(height: 12),
        if (availableCages.isEmpty)
          const EmptyState(
            icon: Icons.grid_off_outlined,
            title: '没有可投喂笼位',
            message: '当前兔舍没有包含在栏兔只的笼位。',
          )
        else
          for (final cage in availableCages)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Card(
                child: CheckboxListTile(
                  key: ValueKey('feed-cage-${cage.id}'),
                  value: selectedCageIds.contains(cage.id),
                  onChanged: saving || previewing
                      ? null
                      : (_) => onToggleCage(cage.id),
                  controlAffinity: ListTileControlAffinity.trailing,
                  title: Text(
                    cage.cageNumber.isEmpty ? '#${cage.id}' : cage.cageNumber,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text('${rabbitsByCage[cage.id]!.length} 只在栏兔只'),
                ),
              ),
            ),
        const SizedBox(height: 12),
        Text('投喂信息', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 10),
        TextFormField(
          key: const ValueKey('feed-amount'),
          controller: amountController,
          enabled: !saving,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(
            labelText: '投喂数量（kg）*',
            prefixIcon: Icon(Icons.scale_outlined),
          ),
          onChanged: onAmountChanged,
        ),
        const SizedBox(height: 12),
        TextFormField(
          key: const ValueKey('feed-type'),
          controller: feedTypeController,
          enabled: !saving,
          maxLength: 50,
          decoration: const InputDecoration(
            labelText: '饲料类型（可选）',
            prefixIcon: Icon(Icons.restaurant_outlined),
          ),
        ),
        ListTile(
          contentPadding: EdgeInsets.zero,
          enabled: !saving && !previewing,
          leading: const Icon(Icons.calendar_today_outlined),
          title: const Text('投喂日期'),
          subtitle: Text(DateFormat('yyyy-MM-dd HH:mm').format(feedTime)),
          trailing: const Icon(Icons.chevron_right),
          onTap: saving || previewing ? null : onPickFeedDate,
        ),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            key: const ValueKey('feed-allocation-preview'),
            onPressed: saving || previewing || selectedRabbitCount == 0
                ? null
                : onPreview,
            icon: previewing
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.account_tree_outlined),
            label: Text(
              previewing
                  ? '正在预览归属'
                  : allocationPreview == null
                      ? '预览批次与阶段归属'
                      : '重新预览归属',
            ),
          ),
        ),
        if (allocationPreview != null) ...[
          const SizedBox(height: 12),
          Text('批次与阶段用量', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          for (final group in allocationPreview!.groups) ...[
            TextFormField(
              key: ValueKey('feed-allocation-${group.key}'),
              initialValue: allocationAmounts[group.key] ?? '',
              enabled: !saving && !previewing,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText:
                    '${group.batchId == null ? '未归属批次' : '批次 #${group.batchId}'} · ${group.phase.label} · ${group.rabbitCount} 只（kg）*',
              ),
              onChanged: (value) => onAllocationChanged(group.key, value),
            ),
            const SizedBox(height: 10),
          ],
        ],
        const SizedBox(height: 4),
        TextFormField(
          key: const ValueKey('feed-remark'),
          controller: remarkController,
          enabled: !saving,
          maxLength: 500,
          maxLines: 3,
          decoration: const InputDecoration(
            labelText: '备注（可选）',
            prefixIcon: Icon(Icons.notes_outlined),
          ),
        ),
        const SizedBox(height: 8),
        if (errorMessage != null)
          _FeedNotice(
            key: const ValueKey('feed-entry-error'),
            icon: Icons.error_outline,
            color: palette.danger,
            message: errorMessage!,
          ),
        if (successMessage != null)
          _FeedNotice(
            key: const ValueKey('feed-entry-success'),
            icon: Icons.check_circle_outline,
            color: palette.success,
            message: successMessage!,
          ),
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            key: const ValueKey('feed-submit'),
            onPressed: saving || previewing || selectedRabbitCount == 0
                ? null
                : onSubmit,
            icon: saving || previewing
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check_circle_outline),
            label: Text(
              saving
                  ? '正在录入'
                  : previewing
                      ? '正在确认归属'
                      : selectedRabbitCount == 0
                          ? '请选择投喂笼位'
                          : '投喂完成 $selectedRabbitCount 只',
            ),
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}

class _FeedNotice extends StatelessWidget {
  const _FeedNotice({
    super.key,
    required this.icon,
    required this.color,
    required this.message,
  });

  final IconData icon;
  final Color color;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          border: Border.all(color: color),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color),
            const SizedBox(width: 10),
            Expanded(child: Text(message)),
          ],
        ),
      ),
    );
  }
}
