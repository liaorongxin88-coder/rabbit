import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/storage/outbound.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';
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
          saleTime: DateTime.now(),
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
    _emit(state.copyWith(
        saleTime: saleTime,
        totalWeight: totalWeight,
        unitPrice: unitPrice,
        customer: customer,
        remark: remark));
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
    _emit(state.copyWith(
        submitStatus: OutboundSubmitStatus.idle,
        conflicts: const [],
        clearResult: true,
        clearRequestId: true,
        clearError: true));
    await _save('SELECTING');
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
    if (weight == null || weight <= 0) {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.failed,
          errorMessage: '请输入大于0的总重量'));
      return Future<void>.value();
    }
    if (state.unitPrice.trim().isNotEmpty && state.parsedUnitPrice == null) {
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.failed, errorMessage: '请输入有效单价'));
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
      final current = state.task;
      if (current == null) return;
      final weight = state.parsedWeight;
      if (weight == null || weight <= 0) return;
      requestId = state.requestId ?? _uuid.v4();
      await _store.savePendingRequest(entry.userId, entry.houseId, requestId);
      if (!_isActive(generation)) return;
      _emit(state.copyWith(
          submitStatus: OutboundSubmitStatus.requesting, requestId: requestId));
      requestMayHaveReachedServer = true;
      final result = await _repository.submit(
          houseId: entry.houseId,
          task: current,
          items: state.selectedItems,
          requestId: requestId,
          saleTime: state.saleTime ?? DateTime.now(),
          totalWeight: weight,
          unitPrice: state.parsedUnitPrice,
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
          clearRequestId: safeFailure,
          errorMessage: error.message));
    } catch (error) {
      if (!_isActive(generation)) return;
      _emit(state.copyWith(
          submitStatus: requestMayHaveReachedServer
              ? OutboundSubmitStatus.unknown
              : OutboundSubmitStatus.failed,
          clearRequestId: !requestMayHaveReachedServer,
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

  void _selectionChanged(Set<int> selected, Map<int, String> reasons) {
    final staysOffline = state.syncStatus == OutboundSyncStatus.offline;
    _emit(state.copyWith(
        selectedRabbitIds: selected,
        earlySaleReasons: reasons,
        syncStatus: staysOffline
            ? OutboundSyncStatus.offline
            : OutboundSyncStatus.saving,
        clearBanner: true));
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
      unawaited(_save(state.isConfirming ? 'WAITING_CONFIRMATION' : 'SELECTING')
          .catchError((_) {}));
    });
  }

  Future<void> _save(String status) {
    final generation = _lifecycleGeneration;
    final sequence = ++_saveSequence;
    final operation =
        _saveQueue.then((_) => _performSave(status, generation, sequence));
    _saveQueue = operation.then<void>((_) {}, onError: (_) {});
    return operation;
  }

  Future<void> _performSave(String status, int generation, int sequence) async {
    if (!_isActive(generation)) return;
    final task = state.task;
    if (task == null) return;
    _emit(state.copyWith(syncStatus: OutboundSyncStatus.saving));
    try {
      final saved = await _repository.saveDraft(
          houseId: entry.houseId,
          task: task,
          status: status,
          items: state.selectedItems,
          saleTime: state.saleTime ?? DateTime.now(),
          totalWeight: state.parsedWeight,
          unitPrice: state.parsedUnitPrice,
          customer: state.customer,
          remark: state.remark);
      if (!_isActive(generation)) return;
      final current = state;
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
          customer: current.customer,
          remark: current.remark,
          selectedItems: current.selectedItems);
      _emit(current.copyWith(
          task: merged,
          syncStatus: sequence == _saveSequence
              ? OutboundSyncStatus.saved
              : OutboundSyncStatus.saving,
          clearError: true));
      unawaited(_enqueueLocalSnapshot());
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
                DateTime.now(),
        totalWeight: preserveLocalForm
            ? state.totalWeight
            : localSnapshot?.totalWeight ??
                task.totalWeight?.toString() ??
                state.totalWeight,
        unitPrice: preserveLocalForm
            ? state.unitPrice
            : localSnapshot?.unitPrice ??
                task.unitPrice?.toString() ??
                state.unitPrice,
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
    _applyTask(task.copyWith(resumed: false, selectedItems: selected),
        preserveLocalForm: true);
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
          clearRequestId: true,
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
          customer: state.customer,
          remark: state.remark,
          selectedItems: state.selectedItems),
      saleTime: state.saleTime,
      totalWeight: state.totalWeight,
      unitPrice: state.unitPrice,
      customer: state.customer,
      remark: state.remark,
      selectionMode: state.mode.name,
      selectedOnly: state.selectedOnly,
    );
    return _store.saveSnapshot(entry.userId, snapshot);
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
