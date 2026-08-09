import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/nfc_hardware_service.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_pending_sync_controller.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_queue_provider.dart';

final nfcWriteControllerProvider = StateNotifierProvider.autoDispose
    .family<NfcWriteController, NfcWriteState, int>((ref, houseId) {
  return NfcWriteController(
    houseId: houseId,
    repository: ref.watch(nfcRepositoryProvider),
    hardware: ref.watch(nfcHardwareServiceProvider),
    store: ref.watch(nfcLocalStoreProvider),
    pendingSync: ref.read(nfcPendingSyncControllerProvider.notifier),
    onQueueChanged: () => ref.invalidate(nfcCageWriteQueueProvider(houseId)),
  );
});

class NfcWriteController extends StateNotifier<NfcWriteState> {
  NfcWriteController({
    required this.houseId,
    required NfcBindingGateway repository,
    required NfcHardwareService hardware,
    required NfcLocalStore store,
    required NfcPendingSyncController pendingSync,
    required void Function() onQueueChanged,
  })  : _repository = repository,
        _hardware = hardware,
        _store = store,
        _pendingSync = pendingSync,
        _onQueueChanged = onQueueChanged,
        super(const NfcWriteState(phase: NfcWritePhase.loading)) {
    _restore();
  }

  final int houseId;
  final NfcBindingGateway _repository;
  final NfcHardwareService _hardware;
  final NfcLocalStore _store;
  final NfcPendingSyncController _pendingSync;
  final void Function() _onQueueChanged;
  static const _uuid = Uuid();
  NfcWriteResult? _pendingResult;
  String? _pendingRequestId;
  var _replaceExisting = false;
  var _pauseRequested = false;
  var _operationActive = false;
  var _operationId = 0;
  var _disposed = false;

  Future<void> _restore() async {
    final session = await _store.readSession();
    if (_disposed) return;
    if (session == null ||
        session.houseId != houseId ||
        session.items.isEmpty) {
      state = const NfcWriteState(
        phase: NfcWritePhase.error,
        message: '没有可恢复的NFC写入队列',
      );
      return;
    }
    final index = session.currentIndex.clamp(0, session.items.length);
    state = NfcWriteState(
      phase: index >= session.items.length
          ? NfcWritePhase.completed
          : NfcWritePhase.waiting,
      session: session.copyWith(currentIndex: index),
      message: index >= session.items.length ? '本次写入已完成' : '等待标签',
    );
    unawaited(_pendingSync.syncAll());
    if (index < session.items.length) {
      unawaited(_listenForCurrent());
    }
  }

  Future<void> _listenForCurrent({bool allowOverwrite = false}) async {
    final session = state.session;
    final item = state.currentItem;
    if (session == null ||
        item == null ||
        state.phase == NfcWritePhase.paused ||
        _operationActive) {
      return;
    }
    _operationActive = true;
    final operationId = ++_operationId;
    state = state.copyWith(
      phase: NfcWritePhase.waiting,
      message: '等待标签',
      clearError: true,
      clearConflict: true,
    );
    try {
      final result = await _hardware.writePayload(
        payload: item.queueItem.payload,
        previousCompletedUid: _lastCompletedUid(session),
        allowOverwrite: allowOverwrite,
      );
      if (_disposed) return;
      _replaceExisting = allowOverwrite;
      state = state.copyWith(
        phase: NfcWritePhase.binding,
        message: '正在绑定 ${item.queueItem.cageNumber}',
      );
      await _bindAndAdvance(result, replaceExisting: allowOverwrite);
    } on NfcWriteException catch (error) {
      if (_disposed) return;
      if (error.kind == NfcWriteError.cancelled) return;
      try {
        await _updateCurrent(
          NfcWriteItemStatus.ready,
          null,
          error.diagnosticMessage,
        );
      } catch (_) {
        // A storage failure must not hide the NFC error from the operator.
      }
      if (_disposed) return;
      HapticFeedback.heavyImpact();
      SystemSound.play(SystemSoundType.alert);
      state = state.copyWith(
        phase: error.requiresOverwriteConfirmation
            ? NfcWritePhase.confirmOverwrite
            : NfcWritePhase.error,
        message: error.message,
        conflict: error,
      );
    } finally {
      if (_operationId == operationId) _operationActive = false;
    }
  }

  Future<void> _bindAndAdvance(
    NfcWriteResult result, {
    required bool replaceExisting,
    String? requestId,
  }) async {
    final item = state.currentItem;
    if (item == null) return;
    final bindRequestId = requestId ?? _uuid.v4();
    try {
      await _repository.bind(
        houseId: houseId,
        cageId: item.queueItem.cageId,
        tagUid: result.tagUid,
        payload: result.payload,
        replaceExisting: replaceExisting,
        requestId: bindRequestId,
      );
      _onQueueChanged();
      await _completeCurrent(
        NfcWriteItemStatus.completed,
        result.tagUid,
      );
    } on ApiException catch (error) {
      if (error.businessCode == 4606) {
        _pendingResult = result;
        _pendingRequestId = bindRequestId;
        state = state.copyWith(
          phase: NfcWritePhase.confirmBindingReplacement,
          message: error.message,
        );
        return;
      }
      if (error.businessCode == null && error.statusCode == null) {
        await _pendingSync.enqueue(NfcPendingBinding(
          houseId: houseId,
          cageId: item.queueItem.cageId,
          tagUid: result.tagUid,
          payload: result.payload,
          requestId: bindRequestId,
          replaceExisting: replaceExisting,
        ));
        await _completeCurrent(
          NfcWriteItemStatus.pendingSync,
          result.tagUid,
        );
        return;
      }
      state = state.copyWith(
        phase: NfcWritePhase.error,
        message: error.message,
      );
      HapticFeedback.heavyImpact();
      SystemSound.play(SystemSoundType.alert);
    }
  }

  Future<void> confirmOverwrite() {
    return _listenForCurrent(allowOverwrite: true);
  }

  Future<void> confirmBindingReplacement() async {
    final result = _pendingResult;
    if (result == null) return;
    state = state.copyWith(
      phase: NfcWritePhase.binding,
      message: '正在重新绑定',
    );
    await _bindAndAdvance(
      result,
      replaceExisting: true,
      requestId: _pendingRequestId,
    );
    _pendingResult = null;
    _pendingRequestId = null;
  }

  Future<void> retry() => _listenForCurrent(
        allowOverwrite: _replaceExisting,
      );

  Future<void> pause() async {
    _pauseRequested = true;
    state = state.copyWith(
      phase: NfcWritePhase.paused,
      message: '写入已暂停',
    );
    try {
      await _hardware.stop();
    } catch (_) {
      // There may be no active hardware session while binding.
    }
  }

  Future<void> resume() {
    _pauseRequested = false;
    state = state.copyWith(
      phase: NfcWritePhase.waiting,
      message: '等待标签',
    );
    return _listenForCurrent();
  }

  Future<void> skip() async {
    try {
      await _hardware.stop();
    } catch (_) {}
    _operationActive = false;
    await _updateCurrent(NfcWriteItemStatus.skipped, null, '已跳过');
    await _advance();
  }

  Future<void> previous() async {
    final session = state.session;
    if (session == null || session.currentIndex <= 0) return;
    try {
      await _hardware.stop();
    } catch (_) {}
    _operationActive = false;
    final nextIndex = session.currentIndex - 1;
    final items = [...session.items];
    items[nextIndex] = items[nextIndex].copyWith(
      status: NfcWriteItemStatus.ready,
      clearError: true,
    );
    final updated = session.copyWith(items: items, currentIndex: nextIndex);
    await _store.saveSession(updated);
    state = NfcWriteState(
      phase: NfcWritePhase.waiting,
      session: updated,
      message: '等待标签',
    );
    unawaited(_listenForCurrent());
  }

  Future<void> _completeCurrent(
    NfcWriteItemStatus status,
    String tagUid,
  ) async {
    await _updateCurrent(status, tagUid, null);
    HapticFeedback.mediumImpact();
    SystemSound.play(SystemSoundType.click);
    state = state.copyWith(
      phase: NfcWritePhase.success,
      message: status == NfcWriteItemStatus.pendingSync ? '已写入，等待同步' : '写入成功',
    );
    await Future<void>.delayed(const Duration(milliseconds: 350));
    await _advance();
  }

  Future<void> _updateCurrent(
    NfcWriteItemStatus status,
    String? tagUid,
    String? error,
  ) async {
    final session = state.session;
    if (session == null || session.currentIndex >= session.items.length) return;
    final items = [...session.items];
    items[session.currentIndex] = items[session.currentIndex].copyWith(
      status: status,
      writtenTagUid: tagUid,
      errorMessage: error,
      clearError: error == null,
    );
    final updated = session.copyWith(items: items);
    await _store.saveSession(updated);
    state = state.copyWith(session: updated);
  }

  Future<void> _advance() async {
    final session = state.session;
    if (session == null) return;
    final nextIndex = session.currentIndex + 1;
    final updated = session.copyWith(currentIndex: nextIndex);
    await _store.saveSession(updated);
    _replaceExisting = false;
    if (_pauseRequested) {
      state = NfcWriteState(
        phase: NfcWritePhase.paused,
        session: updated,
        message: '写入已暂停',
      );
      return;
    }
    if (nextIndex >= updated.items.length) {
      state = NfcWriteState(
        phase: NfcWritePhase.completed,
        session: updated,
        message: '本次写入已完成',
      );
      return;
    }
    state = NfcWriteState(
      phase: NfcWritePhase.waiting,
      session: updated,
      message: '等待标签',
    );
    unawaited(Future<void>.delayed(const Duration(milliseconds: 500), () {
      if (!_disposed && state.phase == NfcWritePhase.waiting) {
        unawaited(_listenForCurrent());
      }
    }));
  }

  String? _lastCompletedUid(NfcWriteSession session) {
    for (var index = session.currentIndex - 1; index >= 0; index--) {
      final uid = session.items[index].writtenTagUid;
      if (uid != null && uid.isNotEmpty) return uid;
    }
    return null;
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_hardware.stop().catchError((_) {}));
    super.dispose();
  }
}

enum NfcWritePhase {
  loading,
  waiting,
  binding,
  success,
  confirmOverwrite,
  confirmBindingReplacement,
  paused,
  completed,
  error,
}

class NfcWriteState {
  const NfcWriteState({
    required this.phase,
    this.session,
    this.message,
    this.conflict,
  });

  final NfcWritePhase phase;
  final NfcWriteSession? session;
  final String? message;
  final NfcWriteException? conflict;

  NfcWriteSessionItem? get currentItem {
    final value = session;
    if (value == null || value.currentIndex >= value.items.length) return null;
    return value.items[value.currentIndex];
  }

  int get completedCount =>
      session?.items
          .where((item) => item.status == NfcWriteItemStatus.completed)
          .length ??
      0;

  int get pendingSyncCount =>
      session?.items
          .where((item) => item.status == NfcWriteItemStatus.pendingSync)
          .length ??
      0;

  int get skippedCount =>
      session?.items
          .where((item) => item.status == NfcWriteItemStatus.skipped)
          .length ??
      0;

  NfcWriteState copyWith({
    NfcWritePhase? phase,
    NfcWriteSession? session,
    String? message,
    NfcWriteException? conflict,
    bool clearError = false,
    bool clearConflict = false,
  }) {
    return NfcWriteState(
      phase: phase ?? this.phase,
      session: session ?? this.session,
      message: clearError ? null : message ?? this.message,
      conflict: clearConflict ? null : conflict ?? this.conflict,
    );
  }
}
