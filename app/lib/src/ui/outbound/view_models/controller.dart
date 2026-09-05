import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/storage/outbound.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/dashboard/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

class OutboundEntry {
  const OutboundEntry({
    required this.userId,
    required this.houseId,
    required this.entryType,
    this.rabbitId,
    this.cageId,
    this.rowCode,
  });

  final int userId;
  final int houseId;
  final String entryType;
  final int? rabbitId;
  final int? cageId;
  final String? rowCode;

  @override
  bool operator ==(Object other) =>
      other is OutboundEntry &&
      other.userId == userId &&
      other.houseId == houseId &&
      other.entryType == entryType &&
      other.rabbitId == rabbitId &&
      other.cageId == cageId &&
      other.rowCode == rowCode;

  @override
  int get hashCode =>
      Object.hash(userId, houseId, entryType, rabbitId, cageId, rowCode);
}

final outboundControllerProvider = StateNotifierProvider.autoDispose
    .family<OutboundController, OutboundState, OutboundEntry>((ref, entry) {
  return OutboundController(
    entry: entry,
    repository: ref.watch(outboundRepositoryProvider),
    store: ref.watch(outboundLocalStoreProvider),
    onCompleted: () {
      ref.invalidate(houseRabbitsProvider(entry.houseId));
      ref.invalidate(allActiveHouseRabbitsProvider(entry.houseId));
      ref.invalidate(houseCagesProvider(entry.houseId));
      ref.invalidate(houseBatchesProvider(entry.houseId));
      ref.invalidate(batchStatisticsProvider);
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseReportProvider(entry.houseId));
    },
  );
});

enum OutboundLoadStatus { loading, ready, empty, error }

enum OutboundSyncStatus { online, offline, saving, saved, failed, stale }

enum OutboundSubmitStatus {
  idle,
  validating,
  requesting,
  unknown,
  conflict,
  failed,
  success
}

class OutboundState {
  const OutboundState({
    this.loadStatus = OutboundLoadStatus.loading,
    this.syncStatus = OutboundSyncStatus.online,
    this.submitStatus = OutboundSubmitStatus.idle,
    this.task,
    this.selectedRabbitIds = const {},
    this.earlySaleReasons = const {},
    this.mode = OutboundSelectionMode.cage,
    this.filter,
    this.selectedOnly = false,
    this.saleTime,
    this.totalWeight = '',
    this.unitPrice = '',
    this.batchAllocationWeights = const {},
    this.customer = '',
    this.remark = '',
    this.requestId,
    this.result,
    this.conflicts = const [],
    this.bannerMessage,
    this.errorMessage,
  });

  final OutboundLoadStatus loadStatus;
  final OutboundSyncStatus syncStatus;
  final OutboundSubmitStatus submitStatus;
  final OutboundTask? task;
  final Set<int> selectedRabbitIds;
  final Map<int, String> earlySaleReasons;
  final OutboundSelectionMode mode;
  final OutboundEligibility? filter;
  final bool selectedOnly;
  final DateTime? saleTime;
  final String totalWeight;
  final String unitPrice;
  final Map<String, String> batchAllocationWeights;
  final String customer;
  final String remark;
  final String? requestId;
  final OutboundSubmitResult? result;
  final List<OutboundConflict> conflicts;
  final String? bannerMessage;
  final String? errorMessage;

  List<OutboundRabbit> get rabbits => task?.rabbits ?? const [];
  List<OutboundRabbit> get visibleRabbits {
    if (selectedOnly) {
      return rabbits
          .where((rabbit) => selectedRabbitIds.contains(rabbit.rabbitId))
          .toList();
    }
    if (filter == null) return rabbits;
    if (filter == OutboundEligibility.needsAction) {
      return rabbits
          .where((rabbit) =>
              rabbit.eligibility == OutboundEligibility.needsAction ||
              rabbit.eligibility == OutboundEligibility.blocked)
          .toList();
    }
    return rabbits.where((rabbit) => rabbit.eligibility == filter).toList();
  }

  bool get isConfirming => task?.status == 'WAITING_CONFIRMATION';
  int get selectedCount => selectedRabbitIds.length;
  int get selectedCageCount => rabbits
      .where((rabbit) => selectedRabbitIds.contains(rabbit.rabbitId))
      .map((rabbit) => rabbit.cageId)
      .toSet()
      .length;
  int get selectedRowCount => rabbits
      .where((rabbit) => selectedRabbitIds.contains(rabbit.rabbitId))
      .map((rabbit) => rabbit.rowCode)
      .toSet()
      .length;
  double? get parsedWeight => double.tryParse(totalWeight.trim());
  double? get parsedUnitPrice =>
      unitPrice.trim().isEmpty ? null : double.tryParse(unitPrice.trim());
  List<OutboundAllocationGroup> get allocationGroups =>
      buildOutboundAllocationGroups(selectedItems, rabbits);
  String allocationWeight(OutboundAllocationGroup group) {
    return batchAllocationWeights[group.key] ??
        (allocationGroups.length == 1 ? totalWeight : '');
  }

  List<OutboundBatchAllocation> get batchAllocations => allocationGroups
      .map(
        (group) => OutboundBatchAllocation(
          batchId: group.batchId,
          actualWeightKg: double.tryParse(allocationWeight(group)) ?? 0,
        ),
      )
      .toList(growable: false);

  List<OutboundBatchAllocation> get draftBatchAllocations =>
      _validDraftAllocations(
        groups: allocationGroups,
        weights: batchAllocationWeights,
        totalWeight: totalWeight,
      );

  double? get estimatedAmount => parsedWeight == null || parsedUnitPrice == null
      ? null
      : parsedWeight! * parsedUnitPrice!;

  List<OutboundSelectedItem> get selectedItems {
    final byId = {for (final rabbit in rabbits) rabbit.rabbitId: rabbit};
    final ids = selectedRabbitIds.toList()..sort();
    return [
      for (final id in ids)
        if (byId[id] != null)
          OutboundSelectedItem(
            rabbitId: id,
            stateVersion: byId[id]!.stateVersion,
            selectionType:
                earlySaleReasons.containsKey(id) ? 'EARLY_SALE' : 'NORMAL',
            earlySaleReason: earlySaleReasons[id],
          ),
    ];
  }

  OutboundState copyWith({
    OutboundLoadStatus? loadStatus,
    OutboundSyncStatus? syncStatus,
    OutboundSubmitStatus? submitStatus,
    OutboundTask? task,
    Set<int>? selectedRabbitIds,
    Map<int, String>? earlySaleReasons,
    OutboundSelectionMode? mode,
    OutboundEligibility? filter,
    bool clearFilter = false,
    bool? selectedOnly,
    DateTime? saleTime,
    String? totalWeight,
    String? unitPrice,
    Map<String, String>? batchAllocationWeights,
    String? customer,
    String? remark,
    String? requestId,
    bool clearRequestId = false,
    OutboundSubmitResult? result,
    bool clearResult = false,
    List<OutboundConflict>? conflicts,
    String? bannerMessage,
    bool clearBanner = false,
    String? errorMessage,
    bool clearError = false,
  }) {
    return OutboundState(
      loadStatus: loadStatus ?? this.loadStatus,
      syncStatus: syncStatus ?? this.syncStatus,
      submitStatus: submitStatus ?? this.submitStatus,
      task: task ?? this.task,
      selectedRabbitIds: selectedRabbitIds ?? this.selectedRabbitIds,
      earlySaleReasons: earlySaleReasons ?? this.earlySaleReasons,
      mode: mode ?? this.mode,
      filter: clearFilter ? null : filter ?? this.filter,
      selectedOnly: selectedOnly ?? this.selectedOnly,
      saleTime: saleTime ?? this.saleTime,
      totalWeight: totalWeight ?? this.totalWeight,
      unitPrice: unitPrice ?? this.unitPrice,
      batchAllocationWeights:
          batchAllocationWeights ?? this.batchAllocationWeights,
      customer: customer ?? this.customer,
      remark: remark ?? this.remark,
      requestId: clearRequestId ? null : requestId ?? this.requestId,
      result: clearResult ? null : result ?? this.result,
      conflicts: conflicts ?? this.conflicts,
      bannerMessage: clearBanner ? null : bannerMessage ?? this.bannerMessage,
      errorMessage: clearError ? null : errorMessage ?? this.errorMessage,
    );
  }
}

class OutboundController extends StateNotifier<OutboundState> {
  OutboundController({
    required this.entry,
    required OutboundGateway repository,
    required OutboundLocalStore store,
    required void Function() onCompleted,
  })  : _repository = repository,
        _store = store,
        _onCompleted = onCompleted,
        super(OutboundState(
          saleTime: farmNow(),
          mode: _selectionModeForEntry(entry.entryType),
        )) {
    initialize();
  }

  final OutboundEntry entry;
  final OutboundGateway _repository;
  final OutboundLocalStore _store;
  final void Function() _onCompleted;
  static const _uuid = Uuid();
  Timer? _saveTimer;
  Future<void> _saveQueue = Future<void>.value();
  Future<void>? _submitInFlight;
  bool _localPersistenceEnabled = true;
  bool _disposed = false;
  int _lifecycleGeneration = 0;
  int _refreshSequence = 0;
  int _saveSequence = 0;
  int _statusSequence = 0;

  Future<void> initialize() async {
    final generation = _beginLifecycle();
    _emit(state.copyWith(
        loadStatus: OutboundLoadStatus.loading, clearError: true));
    final cached = await _store.readSnapshot(entry.userId, entry.houseId);
    if (!_isActive(generation)) return;
    final pendingRequest =
        await _store.readPendingRequest(entry.userId, entry.houseId);
    if (!_isActive(generation)) return;
    if (pendingRequest != null) {
      try {
        final result = await _repository.status(
            houseId: entry.houseId, requestId: pendingRequest);
        if (!_isActive(generation)) return;
        if (result.isCompleted) {
          _emit(state.copyWith(
              loadStatus: OutboundLoadStatus.ready,
              submitStatus: OutboundSubmitStatus.success,
              result: result,
              requestId: pendingRequest));
          _localPersistenceEnabled = false;
          await _store.clear(entry.userId, entry.houseId);
          if (_isActive(generation)) _onCompleted();
          return;
        }
        if (result.isFailed) {
          await _store.clearPendingRequest(entry.userId, entry.houseId);
          if (!_isActive(generation)) return;
        } else if (!result.isConflict) {
          if (cached != null) {
            _applyTask(cached.task,
                syncStatus: OutboundSyncStatus.offline, localSnapshot: cached);
          }
          if (!_isActive(generation)) return;
          _emit(state.copyWith(
              loadStatus: OutboundLoadStatus.ready,
              syncStatus: OutboundSyncStatus.offline,
              submitStatus: OutboundSubmitStatus.unknown,
              result: result,
              requestId: pendingRequest,
              bannerMessage: '正在确认上次提交结果'));
          return;
        } else {
          await _store.clearPendingRequest(entry.userId, entry.houseId);
          if (!_isActive(generation)) return;
        }
      } catch (error) {
        if (error is ApiException && _isSafeFinalFailure(error)) {
          await _store.clearPendingRequest(entry.userId, entry.houseId);
          if (!_isActive(generation)) return;
        } else {
          if (cached != null) {
            _applyTask(cached.task,
                syncStatus: OutboundSyncStatus.offline, localSnapshot: cached);
          }
          if (!_isActive(generation)) return;
          _emit(state.copyWith(
              loadStatus: OutboundLoadStatus.ready,
              submitStatus: OutboundSubmitStatus.unknown,
              requestId: pendingRequest,
              bannerMessage: '网络不可用，提交结果尚未确认'));
          return;
        }
      }
    }
    try {
      final task = await _repository.createTask(
          houseId: entry.houseId,
          entryType: entry.entryType,
          rabbitId: entry.rabbitId,
          cageId: entry.cageId,
          rowCode: entry.rowCode);
      if (!_isActive(generation)) return;
      final local = cached != null &&
              cached.task.taskId == task.taskId &&
              cached.task.revision >= task.revision
          ? cached
          : null;
      final restored = local == null
          ? task
          : task.copyWith(
              status: local.task.status,
              selectedItems: local.task.selectedItems);
      _applyTask(restored,
          localSnapshot: local, banner: task.resumed ? '已恢复未完成的出库草稿' : null);
    } catch (error) {
      if (!_isActive(generation)) return;
      if (cached != null) {
        _applyTask(cached.task,
            localSnapshot: cached,
            syncStatus: OutboundSyncStatus.offline,
            banner: '当前离线，展示最近保存的草稿');
      } else {
        _emit(state.copyWith(
            loadStatus: OutboundLoadStatus.error,
            errorMessage: _message(error)));
      }
    }
  }

  void setMode(OutboundSelectionMode mode) {
    _emit(state.copyWith(mode: mode));
    unawaited(_enqueueLocalSnapshot());
  }

  void setFilter(OutboundEligibility? filter) {
    _emit(filter == null
        ? state.copyWith(clearFilter: true, selectedOnly: false)
        : state.copyWith(filter: filter, selectedOnly: false));
    unawaited(_enqueueLocalSnapshot());
  }

  void setSelectedOnly(bool selectedOnly) {
    _emit(state.copyWith(
      selectedOnly: selectedOnly,
      clearFilter: selectedOnly,
    ));
    unawaited(_enqueueLocalSnapshot());
  }

  Future<void> continueResumedDraft() async {
    final task = state.task;
    if (task == null) return;
    _applyTask(task.copyWith(resumed: false));
    await refresh();
    if (!_disposed && state.loadStatus == OutboundLoadStatus.ready) {
      _emit(state.copyWith(bannerMessage: '已继续草稿并重新预检当前状态'));
    }
  }

  Future<void> discardResumedDraft() async {
    final task = state.task;
    if (task == null) return;
    final generation = _beginLifecycle();
    _emit(state.copyWith(
        loadStatus: OutboundLoadStatus.loading, clearError: true));
    try {
      await _repository.cancel(houseId: entry.houseId, taskId: task.taskId);
      if (!_isActive(generation)) return;
      await _store.clear(entry.userId, entry.houseId);
      if (!_isActive(generation)) return;
      final replacement = await _repository.createTask(
        houseId: entry.houseId,
        entryType: entry.entryType,
        rabbitId: entry.rabbitId,
        cageId: entry.cageId,
        rowCode: entry.rowCode,
        resumeExisting: false,
      );
      if (!_isActive(generation)) return;
      _localPersistenceEnabled = true;
      _applyTask(replacement, banner: '已放弃旧草稿并创建新任务');
    } catch (error) {
      if (!_isActive(generation)) return;
      _emit(state.copyWith(
          loadStatus: OutboundLoadStatus.error, errorMessage: _message(error)));
    }
  }

  void toggleRabbit(OutboundRabbit rabbit) {
    if (_editingLocked) return;
    if (!rabbit.isNormal) {
      if (state.selectedRabbitIds.contains(rabbit.rabbitId)) {
        removeRabbit(rabbit.rabbitId);
      }
      return;
    }
    final selected = {...state.selectedRabbitIds};
    selected.contains(rabbit.rabbitId)
        ? selected.remove(rabbit.rabbitId)
        : selected.add(rabbit.rabbitId);
    final reasons = {...state.earlySaleReasons}..remove(rabbit.rabbitId);
    _selectionChanged(selected, reasons);
  }

  void toggleCage(int cageId) => _toggleNormalScope(
      state.rabbits.where((rabbit) => rabbit.cageId == cageId));

  /// NFC 碰笼位的语义是“把这笼加入出库清单”，不是再次扫描时反选。
  void selectCage(int cageId) => _selectNormalScope(
      state.rabbits.where((rabbit) => rabbit.cageId == cageId));
  void toggleRow(String rowCode) => _toggleNormalScope(
      state.rabbits.where((rabbit) => rabbit.rowCode == rowCode));
  void toggleHouse() => _toggleNormalScope(state.rabbits);

  void selectEarlySale(OutboundRabbit rabbit, String reason) {
    if (_editingLocked) return;
    if (!rabbit.canEarlySell || reason.trim().isEmpty) return;
    _selectionChanged({...state.selectedRabbitIds, rabbit.rabbitId},
        {...state.earlySaleReasons, rabbit.rabbitId: reason.trim()});
  }

  void removeRabbit(int rabbitId) {
    if (_editingLocked) return;
    _selectionChanged({...state.selectedRabbitIds}..remove(rabbitId),
        {...state.earlySaleReasons}..remove(rabbitId));
  }

  void updateForm(
      {DateTime? saleTime,
      String? totalWeight,
      String? unitPrice,
      String? customer,
      String? remark}) {
    if (_editingLocked) return;
    final payloadChanged = _formPayloadChanged(
      state,
      saleTime: saleTime,
      totalWeight: totalWeight,
      unitPrice: unitPrice,
      customer: customer,
      remark: remark,
    );
    final staysOffline = state.syncStatus == OutboundSyncStatus.offline;
    _emit(state.copyWith(
        saleTime: saleTime,
        totalWeight: totalWeight,
        unitPrice: unitPrice,
        customer: customer,
        remark: remark,
        syncStatus: staysOffline
            ? OutboundSyncStatus.offline
            : OutboundSyncStatus.saving,
        clearRequestId: payloadChanged));
    if (payloadChanged) unawaited(_clearPendingRequestForEdit());
    unawaited(_enqueueLocalSnapshot());
    _scheduleSave();
  }

  void updateBatchAllocation(String key, String value) {
    if (_editingLocked ||
        !state.allocationGroups.any((group) => group.key == key)) {
      return;
    }
    if (state.batchAllocationWeights[key] == value) return;
    final before = state.draftBatchAllocations;
    final weights = {
      ...state.batchAllocationWeights,
      key: value,
    };
    final after = _validDraftAllocations(
      groups: state.allocationGroups,
      weights: weights,
      totalWeight: state.totalWeight,
    );
    final payloadChanged = !_sameAllocations(before, after);
    final staysOffline = state.syncStatus == OutboundSyncStatus.offline;
    _emit(state.copyWith(
      batchAllocationWeights: weights,
      syncStatus:
          staysOffline ? OutboundSyncStatus.offline : OutboundSyncStatus.saving,
      clearRequestId: payloadChanged,
    ));
    if (payloadChanged) unawaited(_clearPendingRequestForEdit());
    unawaited(_enqueueLocalSnapshot());
    _scheduleSave();
  }

  Future<void> refresh() async {
    final task = state.task;
    if (task == null || _disposed) return;
    final generation = _lifecycleGeneration;
    final sequence = ++_refreshSequence;
    try {
      final refreshed = await _repository.precheck(
          houseId: entry.houseId, taskId: task.taskId);
      if (!_isActive(generation) || sequence != _refreshSequence) return;
      final previous = {...state.selectedRabbitIds};
      final currentTask = state.task;
      final mergedRefresh = currentTask != null &&
              currentTask.taskId == refreshed.taskId &&
              currentTask.revision > refreshed.revision
          ? refreshed.copyWith(
              status: currentTask.status, revision: currentTask.revision)
          : refreshed.copyWith(status: currentTask?.status);
      _applyPrecheckedTask(mergedRefresh, state.selectedItems);
      await _save(state.isConfirming ? 'WAITING_CONFIRMATION' : 'SELECTING');
      if (!_isActive(generation) || sequence != _refreshSequence) return;
      final removed = previous.difference(state.selectedRabbitIds);
      if (removed.isNotEmpty) {
        _emit(state.copyWith(bannerMessage: '${removed.length}只兔因状态变化已移出'));
      }
    } catch (error) {
      if (!_isActive(generation) || sequence != _refreshSequence) return;
      _emit(state.copyWith(
          syncStatus: OutboundSyncStatus.offline,
          errorMessage: _message(error)));
    }
  }

  Future<void> continueToConfirm() async {
    if (state.selectedRabbitIds.isEmpty) return;
    _refreshSequence++;
    _saveTimer?.cancel();
    await _save('WAITING_CONFIRMATION');
  }

  Future<void> backToSelection() async {
    _refreshSequence++;
    _saveTimer?.cancel();
    final task = state.task;
    _emit(state.copyWith(
      task: task?.copyWith(status: 'SELECTING'),
      submitStatus: OutboundSubmitStatus.idle,
      conflicts: const [],
      clearResult: true,
      clearError: true,
    ));
    try {
      await _save('SELECTING');
    } catch (_) {
      // The stale server draft may be unsavable. Keep the local selection
      // editable so the user can refresh or remove invalid rabbits.
    }
  }

  Future<void> submit() {
    if (_disposed || _submitInFlight != null || _submitLocked) {
      return Future<void>.value();
    }
    final task = state.task;
    final weight = state.parsedWeight;
    if (task == null || state.selectedItems.isEmpty) {
      return Future<void>.value();
    }
    final validation = validateOutboundAllocations(
      totalWeight: weight,
      unitPricePerKg: state.parsedUnitPrice,
      allocations: state.batchAllocations,
    );
    if (validation != null) {
      _emit(state.copyWith(
        submitStatus: OutboundSubmitStatus.failed,
        errorMessage: validation,
      ));
      return Future<void>.value();
    }
    _emit(state.copyWith(
        submitStatus: OutboundSubmitStatus.validating, clearError: true));
    _refreshSequence++;
    final generation = _lifecycleGeneration;
    final operation = _submitOnce(generation);
    _submitInFlight = operation;
    return operation.whenComplete(() {
      if (identical(_submitInFlight, operation)) _submitInFlight = null;
    });
  }

  Future<void> _submitOnce(int generation) async {
    String? requestId;
    var requestMayHaveReachedServer = false;
    try {
      _saveTimer?.cancel();
      _saveTimer = null;
      final acknowledged = await _save(
        'WAITING_CONFIRMATION',
        restoreAcknowledged: true,
      );
      if (!_isActive(generation) || acknowledged == null) return;

      final current = state.task;
      final items = state.selectedItems;
      final weight = state.parsedWeight;
      final unitPrice = state.parsedUnitPrice;
      final allocations = state.batchAllocations;
      if (current == null ||
          current.status != 'WAITING_CONFIRMATION' ||
          items.isEmpty) {
        _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.failed,
          errorMessage: '服务端确认草稿后没有可提交的兔只，请返回重新选择',
        ));
        return;
      }
      final validation = validateOutboundAllocations(
        totalWeight: weight,
        unitPricePerKg: unitPrice,
        allocations: allocations,
      );
      if (validation != null || weight == null || unitPrice == null) {
        _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.failed,
          errorMessage: validation ?? '服务端确认的出库草稿不完整，请核对后重试',
        ));
        return;
      }

      requestId = state.requestId ?? _uuid.v4();
      await _store.savePendingRequest(entry.userId, entry.houseId, requestId);
      if (!_isActive(generation)) return;
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.requesting, requestId: requestId));
      requestMayHaveReachedServer = true;
      final result = await _repository.submit(
          houseId: entry.houseId,
          task: current,
          items: items,
          requestId: requestId,
          saleTime: state.saleTime ?? farmNow(),
          totalWeight: weight,
          unitPrice: unitPrice,
          batchAllocations: allocations,
          customer: state.customer,
          remark: state.remark);
      if (!_isActive(generation)) return;
      await _handleResult(result, generation);
    } on ApiException catch (error) {
      if (!_isActive(generation)) return;
      final safeFailure =
          !requestMayHaveReachedServer || _isSafeFinalFailure(error);
      if (safeFailure && requestId != null) {
        await _store.clearPendingRequest(entry.userId, entry.houseId);
        if (!_isActive(generation)) return;
      }
      _emit(state.copyWith(
          submitStatus: safeFailure
              ? OutboundSubmitStatus.failed
              : OutboundSubmitStatus.unknown,
          requestId: requestId,
          errorMessage: error.message));
    } catch (error) {
      if (!_isActive(generation)) return;
      _emit(state.copyWith(
          submitStatus: requestMayHaveReachedServer
              ? OutboundSubmitStatus.unknown
              : OutboundSubmitStatus.failed,
          requestId: requestId,
          errorMessage: _message(error)));
    }
  }

  Future<void> pollStatus() async {
    final requestId = state.requestId;
    if (requestId == null || _disposed) return;
    final generation = _lifecycleGeneration;
    final sequence = ++_statusSequence;
    _emit(state.copyWith(
        submitStatus: OutboundSubmitStatus.requesting, clearError: true));
    try {
      final result = await _repository.status(
          houseId: entry.houseId, requestId: requestId);
      if (!_isActive(generation) || sequence != _statusSequence) return;
      await _handleResult(result, generation);
    } on ApiException catch (error) {
      if (!_isActive(generation) || sequence != _statusSequence) return;
      if (_isSafeFinalFailure(error)) {
        await _store.clearPendingRequest(entry.userId, entry.houseId);
        if (!_isActive(generation) || sequence != _statusSequence) return;
        _emit(state.copyWith(
            submitStatus: OutboundSubmitStatus.failed,
            clearRequestId: true,
            errorMessage: error.message));
      } else {
        _emit(state.copyWith(
            submitStatus: OutboundSubmitStatus.unknown,
            errorMessage: error.message));
      }
    } catch (error) {
      if (!_isActive(generation) || sequence != _statusSequence) return;
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.unknown,
          errorMessage: _message(error)));
    }
  }

  Future<void> removeConflicts() async {
    final ids = state.conflicts.map((item) => item.rabbitId).toSet();
    final remaining = state.selectedRabbitIds.difference(ids);
    _selectionChanged(remaining,
        {...state.earlySaleReasons}..removeWhere((id, _) => ids.contains(id)));
    _emit(state.copyWith(
        submitStatus: OutboundSubmitStatus.idle,
        conflicts: const [],
        clearResult: true,
        clearRequestId: true));
    await _store.clearPendingRequest(entry.userId, entry.houseId);
    await _save(remaining.isEmpty ? 'SELECTING' : 'WAITING_CONFIRMATION');
  }

  Future<void> cancel() async {
    final task = state.task;
    if (task == null) return;
    await _repository.cancel(houseId: entry.houseId, taskId: task.taskId);
    if (_disposed) return;
    _localPersistenceEnabled = false;
    await _store.clear(entry.userId, entry.houseId);
  }

  void _toggleNormalScope(Iterable<OutboundRabbit> scope) {
    if (_editingLocked) return;
    final ids = scope
        .where((rabbit) => rabbit.isNormal)
        .map((rabbit) => rabbit.rabbitId)
        .toSet();
    if (ids.isEmpty) return;
    final selected = {...state.selectedRabbitIds};
    ids.every(selected.contains)
        ? selected.removeAll(ids)
        : selected.addAll(ids);
    _selectionChanged(selected, state.earlySaleReasons);
  }

  void _selectNormalScope(Iterable<OutboundRabbit> scope) {
    if (_editingLocked) return;
    final ids = scope
        .where((rabbit) => rabbit.isNormal)
        .map((rabbit) => rabbit.rabbitId)
        .toSet();
    if (ids.isEmpty || ids.every(state.selectedRabbitIds.contains)) return;
    _selectionChanged(
      {...state.selectedRabbitIds, ...ids},
      state.earlySaleReasons,
    );
  }

  void _selectionChanged(Set<int> selected, Map<int, String> reasons) {
    final staysOffline = state.syncStatus == OutboundSyncStatus.offline;
    final reconciled = _reconcileMeasuredWeights(
      previousRabbits: state.rabbits,
      previousSelection: state.selectedRabbitIds,
      nextRabbits: state.rabbits,
      nextSelection: selected,
      weights: state.batchAllocationWeights,
      currentTotalWeight: state.totalWeight,
    );
    _emit(state.copyWith(
        selectedRabbitIds: selected,
        earlySaleReasons: reasons,
        totalWeight: reconciled.totalWeight,
        batchAllocationWeights: reconciled.weights,
        syncStatus: staysOffline
            ? OutboundSyncStatus.offline
            : OutboundSyncStatus.saving,
        clearRequestId: true,
        clearBanner: true));
    unawaited(_clearPendingRequestForEdit());
    unawaited(_enqueueLocalSnapshot());
    _scheduleSave();
  }

  void _scheduleSave() {
    if (_disposed) return;
    if (state.syncStatus == OutboundSyncStatus.offline) {
      unawaited(_enqueueLocalSnapshot());
      return;
    }
    _saveTimer?.cancel();
    _saveTimer = Timer(const Duration(milliseconds: 750), () {
      unawaited(
        _save(state.isConfirming ? 'WAITING_CONFIRMATION' : 'SELECTING')
            .then<void>((_) {}, onError: (_) {}),
      );
    });
  }

  Future<OutboundTask?> _save(
    String status, {
    bool restoreAcknowledged = false,
  }) {
    final generation = _lifecycleGeneration;
    final sequence = ++_saveSequence;
    final operation = _saveQueue.then(
      (_) => _performSave(
        status,
        generation,
        sequence,
        restoreAcknowledged: restoreAcknowledged,
      ),
    );
    _saveQueue = operation.then<void>((_) {}, onError: (_) {});
    return operation;
  }

  Future<OutboundTask?> _performSave(
    String status,
    int generation,
    int sequence, {
    required bool restoreAcknowledged,
  }) async {
    if (!_isActive(generation)) return null;
    final task = state.task;
    if (task == null) return null;
    _emit(state.copyWith(syncStatus: OutboundSyncStatus.saving));
    try {
      final saved = await _repository.saveDraft(
          houseId: entry.houseId,
          task: task,
          status: status,
          items: state.selectedItems,
          saleTime: state.saleTime ?? farmNow(),
          totalWeight: state.parsedWeight,
          unitPrice: state.parsedUnitPrice,
          batchAllocations: state.draftBatchAllocations,
          customer: state.customer,
          remark: state.remark);
      if (!_isActive(generation)) return null;
      final current = state;
      if (restoreAcknowledged && sequence == _saveSequence) {
        final validRabbitIds =
            saved.rabbits.map((rabbit) => rabbit.rabbitId).toSet();
        final selectedItems = saved.selectedItems
            .where((item) => validRabbitIds.contains(item.rabbitId))
            .toList(growable: false);
        final selectedRabbitIds =
            selectedItems.map((item) => item.rabbitId).toSet();
        final earlySaleReasons = {
          for (final item in selectedItems.where(
              (item) => item.isEarlySale && item.earlySaleReason != null))
            item.rabbitId: item.earlySaleReason!,
        };
        final serverPayloadChanged =
            !_sameSelectedItems(current.selectedItems, selectedItems) ||
                !_sameSaleDate(current.saleTime, saved.saleTime) ||
                current.parsedWeight != saved.totalWeight ||
                current.parsedUnitPrice != saved.unitPrice ||
                !_sameAllocations(
                  current.draftBatchAllocations,
                  saved.batchAllocations,
                ) ||
                current.customer.trim() != (saved.customer ?? '').trim() ||
                current.remark.trim() != (saved.remark ?? '').trim();
        if (serverPayloadChanged && current.requestId != null) {
          await _store.clearPendingRequest(entry.userId, entry.houseId);
          if (!_isActive(generation)) return null;
        }
        _emit(current.copyWith(
          task: saved,
          selectedRabbitIds: selectedRabbitIds,
          earlySaleReasons: earlySaleReasons,
          saleTime: saved.saleTime,
          totalWeight: saved.totalWeight?.toString() ?? '',
          unitPrice: saved.unitPrice?.toString() ?? '',
          batchAllocationWeights: {
            for (final allocation in saved.batchAllocations)
              allocation.key: allocation.actualWeightKg.toString(),
          },
          customer: saved.customer ?? '',
          remark: saved.remark ?? '',
          syncStatus: OutboundSyncStatus.saved,
          clearRequestId: serverPayloadChanged,
          clearError: true,
        ));
      } else {
        final currentTask = current.task;
        final responseTask = currentTask != null &&
                currentTask.taskId == saved.taskId &&
                currentTask.revision > saved.revision
            ? currentTask
            : saved;
        final merged = responseTask.copyWith(
            status: status,
            saleTime: current.saleTime,
            totalWeight: current.parsedWeight,
            unitPrice: current.parsedUnitPrice,
            batchAllocations: current.draftBatchAllocations,
            customer: current.customer,
            remark: current.remark,
            selectedItems: current.selectedItems);
        _emit(current.copyWith(
            task: merged,
            syncStatus: sequence == _saveSequence
                ? OutboundSyncStatus.saved
                : OutboundSyncStatus.saving,
            clearError: true));
      }
      unawaited(_enqueueLocalSnapshot());
      return saved;
    } catch (error) {
      if (_isActive(generation) && sequence == _saveSequence) {
        _emit(state.copyWith(
            syncStatus: OutboundSyncStatus.failed,
            errorMessage: _message(error)));
      }
      rethrow;
    }
  }

  void _applyTask(OutboundTask task,
      {OutboundSyncStatus syncStatus = OutboundSyncStatus.online,
      OutboundLocalSnapshot? localSnapshot,
      String? banner,
      bool preserveLocalForm = false}) {
    final selected = task.selectedItems.map((item) => item.rabbitId).toSet();
    final validIds = task.rabbits.map((rabbit) => rabbit.rabbitId).toSet();
    selected.retainAll(validIds);
    final early = {
      for (final item in task.selectedItems
          .where((item) => item.isEarlySale && item.earlySaleReason != null))
        item.rabbitId: item.earlySaleReason!
    };
    final status = task.rabbits.isEmpty
        ? OutboundLoadStatus.empty
        : OutboundLoadStatus.ready;
    final taskWeights = {
      for (final allocation in task.batchAllocations)
        allocation.key: allocation.actualWeightKg.toString(),
    };
    final restoredWeights = localSnapshot?.hasBatchAllocationWeights == true
        ? localSnapshot!.batchAllocationWeights
        : taskWeights.isNotEmpty
            ? taskWeights
            : state.batchAllocationWeights;
    final reconciled = preserveLocalForm
        ? _reconcileMeasuredWeights(
            previousRabbits: state.rabbits,
            previousSelection: state.selectedRabbitIds,
            nextRabbits: task.rabbits,
            nextSelection: selected,
            weights: state.batchAllocationWeights,
            currentTotalWeight: state.totalWeight,
          )
        : _pruneMeasuredWeights(
            rabbits: task.rabbits,
            selection: selected,
            weights: restoredWeights,
            totalWeight: localSnapshot?.totalWeight ??
                task.totalWeight?.toString() ??
                state.totalWeight,
          );
    _emit(state.copyWith(
        loadStatus: status,
        syncStatus: syncStatus,
        task: task,
        selectedRabbitIds: selected,
        earlySaleReasons: early,
        mode: preserveLocalForm
            ? state.mode
            : _selectionModeFromSnapshot(
                localSnapshot?.selectionMode,
                state.mode,
              ),
        selectedOnly: preserveLocalForm
            ? state.selectedOnly
            : localSnapshot?.selectedOnly ?? state.selectedOnly,
        saleTime: preserveLocalForm
            ? state.saleTime
            : localSnapshot?.saleTime ??
                task.saleTime ??
                state.saleTime ??
                farmNow(),
        totalWeight: preserveLocalForm
            ? reconciled.totalWeight
            : localSnapshot?.totalWeight ??
                task.totalWeight?.toString() ??
                state.totalWeight,
        unitPrice: preserveLocalForm
            ? state.unitPrice
            : localSnapshot?.unitPrice ??
                task.unitPrice?.toString() ??
                state.unitPrice,
        batchAllocationWeights: reconciled.weights,
        customer: preserveLocalForm
            ? state.customer
            : localSnapshot?.customer ?? task.customer ?? state.customer,
        remark: preserveLocalForm
            ? state.remark
            : localSnapshot?.remark ?? task.remark ?? state.remark,
        bannerMessage: banner,
        clearBanner: banner == null,
        clearError: true));
    unawaited(_enqueueLocalSnapshot());
  }

  void _applyPrecheckedTask(
      OutboundTask task, List<OutboundSelectedItem> currentSelection) {
    final rabbits = {
      for (final rabbit in task.rabbits) rabbit.rabbitId: rabbit
    };
    final selected = <OutboundSelectedItem>[];
    for (final item in currentSelection) {
      final rabbit = rabbits[item.rabbitId];
      if (rabbit == null) continue;
      if (item.isEarlySale &&
          rabbit.canEarlySell &&
          item.earlySaleReason != null) {
        selected.add(OutboundSelectedItem(
            rabbitId: rabbit.rabbitId,
            stateVersion: rabbit.stateVersion,
            selectionType: 'EARLY_SALE',
            earlySaleReason: item.earlySaleReason));
      } else if (rabbit.isNormal) {
        selected.add(OutboundSelectedItem(
            rabbitId: rabbit.rabbitId,
            stateVersion: rabbit.stateVersion,
            selectionType: 'NORMAL'));
      }
    }
    final submitPayloadChanged =
        !_sameSelectedItems(currentSelection, selected);
    _applyTask(task.copyWith(resumed: false, selectedItems: selected),
        preserveLocalForm: true);
    if (submitPayloadChanged &&
        state.requestId != null &&
        state.submitStatus != OutboundSubmitStatus.unknown &&
        state.submitStatus != OutboundSubmitStatus.requesting &&
        state.submitStatus != OutboundSubmitStatus.validating) {
      _emit(state.copyWith(clearRequestId: true));
      unawaited(_clearPendingRequestForEdit());
    }
  }

  Future<void> _handleResult(
      OutboundSubmitResult result, int generation) async {
    if (!_isActive(generation)) return;
    if (result.isCompleted) {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.success,
          result: result,
          conflicts: const []));
      _localPersistenceEnabled = false;
      await _store.clear(entry.userId, entry.houseId);
      if (_isActive(generation)) _onCompleted();
    } else if (result.isConflict) {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.conflict,
          result: result,
          conflicts: result.conflicts,
          errorMessage: result.message));
      await _store.clearPendingRequest(entry.userId, entry.houseId);
    } else if (result.isFailed) {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.failed,
          result: result,
          errorMessage: result.message));
      await _store.clearPendingRequest(entry.userId, entry.houseId);
    } else {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.unknown, result: result));
    }
  }

  Future<void> _enqueueLocalSnapshot() {
    if (!_localPersistenceEnabled ||
        state.submitStatus == OutboundSubmitStatus.success) {
      return Future<void>.value();
    }
    final task = state.task;
    if (task == null) return Future<void>.value();
    final snapshot = OutboundLocalSnapshot(
      task: task.copyWith(
          status: state.isConfirming ? 'WAITING_CONFIRMATION' : 'SELECTING',
          saleTime: state.saleTime,
          totalWeight: state.parsedWeight,
          unitPrice: state.parsedUnitPrice,
          batchAllocations: state.draftBatchAllocations,
          customer: state.customer,
          remark: state.remark,
          selectedItems: state.selectedItems),
      saleTime: state.saleTime,
      totalWeight: state.totalWeight,
      unitPrice: state.unitPrice,
      batchAllocationWeights: state.batchAllocationWeights,
      customer: state.customer,
      remark: state.remark,
      selectionMode: state.mode.name,
      selectedOnly: state.selectedOnly,
    );
    return _store.saveSnapshot(entry.userId, snapshot);
  }

  Future<void> _clearPendingRequestForEdit() {
    return _store.clearPendingRequest(entry.userId, entry.houseId);
  }

  String _message(Object error) =>
      error is ApiException ? error.message : error.toString();

  bool _isSafeFinalFailure(ApiException error) {
    const safeCodes = {400, 401, 403, 404};
    final statusCode = error.statusCode;
    if (statusCode != null) {
      if (statusCode >= 500) return false;
      return safeCodes.contains(statusCode);
    }
    return safeCodes.contains(error.businessCode);
  }

  bool get _submitLocked => switch (state.submitStatus) {
        OutboundSubmitStatus.validating ||
        OutboundSubmitStatus.requesting ||
        OutboundSubmitStatus.unknown ||
        OutboundSubmitStatus.success ||
        OutboundSubmitStatus.conflict =>
          true,
        _ => false,
      };

  bool get _editingLocked => _submitLocked;

  int _beginLifecycle() {
    _saveTimer?.cancel();
    _refreshSequence++;
    _statusSequence++;
    return ++_lifecycleGeneration;
  }

  bool _isActive(int generation) =>
      !_disposed && generation == _lifecycleGeneration;

  void _emit(OutboundState next) {
    if (!_disposed) state = next;
  }

  @override
  void dispose() {
    _saveTimer?.cancel();
    unawaited(_enqueueLocalSnapshot());
    _disposed = true;
    _lifecycleGeneration++;
    _refreshSequence++;
    _statusSequence++;
    super.dispose();
  }
}

OutboundSelectionMode _selectionModeForEntry(String entryType) {
  return switch (entryType.trim().toUpperCase()) {
    'HOUSE' => OutboundSelectionMode.house,
    'ROW' => OutboundSelectionMode.row,
    _ => OutboundSelectionMode.cage,
  };
}

OutboundSelectionMode _selectionModeFromSnapshot(
  String? value,
  OutboundSelectionMode fallback,
) {
  for (final mode in OutboundSelectionMode.values) {
    if (mode.name == value) return mode;
  }
  return fallback;
}

({Map<String, String> weights, String totalWeight}) _reconcileMeasuredWeights({
  required List<OutboundRabbit> previousRabbits,
  required Set<int> previousSelection,
  required List<OutboundRabbit> nextRabbits,
  required Set<int> nextSelection,
  required Map<String, String> weights,
  required String currentTotalWeight,
}) {
  final previousGroups = _selectedRabbitIdsByGroup(
    previousRabbits,
    previousSelection,
  );
  final nextGroups = _selectedRabbitIdsByGroup(nextRabbits, nextSelection);
  if (_sameIntSet(previousSelection, nextSelection) &&
      _sameGroupedSelection(previousGroups, nextGroups)) {
    return (
      weights: Map.unmodifiable(weights),
      totalWeight: currentTotalWeight
    );
  }
  final retained = <String, String>{};
  for (final entry in weights.entries) {
    final before = previousGroups[entry.key];
    final after = nextGroups[entry.key];
    if (before != null && after != null && _sameIntSet(before, after)) {
      retained[entry.key] = entry.value;
    }
  }
  return _pruneMeasuredWeights(
    rabbits: nextRabbits,
    selection: nextSelection,
    weights: retained,
    totalWeight: '',
    recomputeTotal: true,
  );
}

({Map<String, String> weights, String totalWeight}) _pruneMeasuredWeights({
  required List<OutboundRabbit> rabbits,
  required Set<int> selection,
  required Map<String, String> weights,
  required String totalWeight,
  bool recomputeTotal = false,
}) {
  final groupKeys = _selectedRabbitIdsByGroup(rabbits, selection).keys.toSet();
  final retained = <String, String>{
    for (final entry in weights.entries)
      if (groupKeys.contains(entry.key)) entry.key: entry.value,
  };
  if (!recomputeTotal) {
    return (weights: Map.unmodifiable(retained), totalWeight: totalWeight);
  }
  if (groupKeys.isEmpty) {
    return (weights: const {}, totalWeight: '');
  }
  var sum = 0.0;
  for (final key in groupKeys) {
    final value = double.tryParse(retained[key] ?? '');
    if (value == null || !value.isFinite || value <= 0) {
      return (weights: Map.unmodifiable(retained), totalWeight: '');
    }
    sum += value;
  }
  return (
    weights: Map.unmodifiable(retained),
    totalWeight: sum.toStringAsFixed(3),
  );
}

Map<String, Set<int>> _selectedRabbitIdsByGroup(
  List<OutboundRabbit> rabbits,
  Set<int> selected,
) {
  final groups = <String, Set<int>>{};
  for (final rabbit in rabbits) {
    if (!selected.contains(rabbit.rabbitId)) continue;
    final key = rabbit.batchId?.toString() ?? 'unassigned';
    groups.putIfAbsent(key, () => <int>{}).add(rabbit.rabbitId);
  }
  return groups;
}

bool _sameGroupedSelection(
  Map<String, Set<int>> left,
  Map<String, Set<int>> right,
) {
  if (left.length != right.length) return false;
  for (final entry in left.entries) {
    final other = right[entry.key];
    if (other == null || !_sameIntSet(entry.value, other)) return false;
  }
  return true;
}

bool _sameIntSet(Set<int> left, Set<int> right) =>
    left.length == right.length && left.containsAll(right);

bool _sameSaleDate(DateTime? left, DateTime? right) {
  if (left == null || right == null) return left == right;
  return left.year == right.year &&
      left.month == right.month &&
      left.day == right.day;
}

List<OutboundBatchAllocation> _validDraftAllocations({
  required List<OutboundAllocationGroup> groups,
  required Map<String, String> weights,
  required String totalWeight,
}) {
  if (groups.isEmpty) return const [];
  final allocations = <OutboundBatchAllocation>[];
  for (final group in groups) {
    final raw = weights[group.key] ?? (groups.length == 1 ? totalWeight : '');
    final value = double.tryParse(raw.trim());
    if (value == null ||
        !value.isFinite ||
        value <= 0 ||
        !_atMostThreeDecimals(value)) {
      continue;
    }
    allocations.add(
      OutboundBatchAllocation(
        batchId: group.batchId,
        actualWeightKg: value,
      ),
    );
  }
  return List.unmodifiable(allocations);
}

bool _sameAllocations(
  List<OutboundBatchAllocation> left,
  List<OutboundBatchAllocation> right,
) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index].batchId != right[index].batchId ||
        left[index].actualWeightKg != right[index].actualWeightKg) {
      return false;
    }
  }
  return true;
}

bool _sameSelectedItems(
  List<OutboundSelectedItem> left,
  List<OutboundSelectedItem> right,
) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    final before = left[index];
    final after = right[index];
    if (before.rabbitId != after.rabbitId ||
        before.stateVersion != after.stateVersion ||
        before.selectionType != after.selectionType ||
        before.earlySaleReason != after.earlySaleReason) {
      return false;
    }
  }
  return true;
}

bool _formPayloadChanged(
  OutboundState state, {
  DateTime? saleTime,
  String? totalWeight,
  String? unitPrice,
  String? customer,
  String? remark,
}) {
  final nextSaleTime = saleTime ?? state.saleTime;
  final currentDate = state.saleTime;
  if (nextSaleTime != null &&
      (currentDate == null ||
          nextSaleTime.year != currentDate.year ||
          nextSaleTime.month != currentDate.month ||
          nextSaleTime.day != currentDate.day)) {
    return true;
  }
  if (totalWeight != null &&
      double.tryParse(totalWeight.trim()) != state.parsedWeight) {
    return true;
  }
  if (unitPrice != null &&
      double.tryParse(unitPrice.trim()) != state.parsedUnitPrice) {
    return true;
  }
  if (customer != null && customer.trim() != state.customer.trim()) {
    return true;
  }
  if (remark != null && remark.trim() != state.remark.trim()) {
    return true;
  }
  return false;
}

bool _atMostThreeDecimals(double value) =>
    ((value * 1000).round() - value * 1000).abs() < 0.000001;
