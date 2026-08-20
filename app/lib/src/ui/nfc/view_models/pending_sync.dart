import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';

final nfcPendingSyncControllerProvider =
    StateNotifierProvider<NfcPendingSyncController, NfcPendingSyncState>((ref) {
  return NfcPendingSyncController(
    repository: ref.watch(nfcRepositoryProvider),
    store: ref.watch(nfcLocalStoreProvider),
    onQueueChanged: (houseId) {
      ref.invalidate(nfcCageWriteQueueProvider(houseId));
    },
  );
});

class NfcPendingSyncController extends StateNotifier<NfcPendingSyncState> {
  NfcPendingSyncController({
    required NfcBindingGateway repository,
    required NfcLocalStore store,
    required void Function(int houseId) onQueueChanged,
  })  : _repository = repository,
        _store = store,
        _onQueueChanged = onQueueChanged,
        super(const NfcPendingSyncState()) {
    _restore();
  }

  final NfcBindingGateway _repository;
  final NfcLocalStore _store;
  final void Function(int houseId) _onQueueChanged;
  static const _uuid = Uuid();
  Future<void>? _restoreFuture;
  var _syncing = false;

  Future<void> _restore() {
    return _restoreFuture ??= _load();
  }

  Future<void> _load() async {
    final items = await _store.readPendingBindings();
    if (!mounted) return;
    state = state.copyWith(items: items, loaded: true);
  }

  Future<void> enqueue(NfcPendingBinding item) async {
    await _restore();
    final items = [
      for (final existing in state.items)
        if (existing.storageKey != item.storageKey) existing,
      item.copyWith(
        status: NfcPendingBindingStatus.pending,
        clearError: true,
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ),
    ];
    await _save(items);
  }

  Future<void> syncAll() async {
    await _restore();
    if (_syncing || state.items.isEmpty) return;
    _syncing = true;
    state = state.copyWith(syncing: true);
    try {
      for (final item in [...state.items]) {
        await _syncItem(item);
      }
    } finally {
      _syncing = false;
      if (mounted) state = state.copyWith(syncing: false);
    }
  }

  Future<void> retry(NfcPendingBinding item) async {
    await _restore();
    await _replaceStored(item.copyWith(
      status: NfcPendingBindingStatus.pending,
      clearError: true,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    ));
    await _syncItem(_find(item.storageKey) ?? item);
  }

  Future<void> forceReplace(NfcPendingBinding item) async {
    await _restore();
    final replacement = item.copyWith(
      requestId: _uuid.v4(),
      replaceExisting: true,
      status: NfcPendingBindingStatus.pending,
      clearError: true,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
    await _replaceStored(replacement, oldKey: item.storageKey);
    await _syncItem(replacement);
  }

  Future<void> discard(NfcPendingBinding item) async {
    await _restore();
    await _save(
      state.items
          .where((existing) => existing.storageKey != item.storageKey)
          .toList(),
    );
  }

  Future<void> _syncItem(NfcPendingBinding item) async {
    if (_find(item.storageKey) == null) return;
    try {
      await _repository.bind(
        houseId: item.houseId,
        cageId: item.cageId,
        tagUid: item.tagUid,
        payload: item.payload,
        replaceExisting: item.replaceExisting,
        requestId: item.requestId,
      );
      await discard(item);
      _onQueueChanged(item.houseId);
    } on ApiException catch (error) {
      final status = error.businessCode == 4606
          ? NfcPendingBindingStatus.conflict
          : error.businessCode != null || error.statusCode != null
              ? NfcPendingBindingStatus.failed
              : NfcPendingBindingStatus.pending;
      await _replaceStored(item.copyWith(
        status: status,
        errorMessage: error.message,
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));
    } catch (error) {
      await _replaceStored(item.copyWith(
        status: NfcPendingBindingStatus.pending,
        errorMessage: error.toString(),
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      ));
    }
  }

  NfcPendingBinding? _find(String key) {
    for (final item in state.items) {
      if (item.storageKey == key) return item;
    }
    return null;
  }

  Future<void> _replaceStored(
    NfcPendingBinding item, {
    String? oldKey,
  }) {
    final key = oldKey ?? item.storageKey;
    return _save([
      for (final existing in state.items)
        if (existing.storageKey == key) item else existing,
    ]);
  }

  Future<void> _save(List<NfcPendingBinding> items) async {
    await _store.savePendingBindings(items);
    if (mounted) state = state.copyWith(items: items, loaded: true);
  }
}

class NfcPendingSyncState {
  const NfcPendingSyncState({
    this.items = const [],
    this.loaded = false,
    this.syncing = false,
  });

  final List<NfcPendingBinding> items;
  final bool loaded;
  final bool syncing;

  int get conflictCount => items
      .where((item) => item.status == NfcPendingBindingStatus.conflict)
      .length;

  int get failedCount => items
      .where((item) => item.status == NfcPendingBindingStatus.failed)
      .length;

  NfcPendingSyncState copyWith({
    List<NfcPendingBinding>? items,
    bool? loaded,
    bool? syncing,
  }) {
    return NfcPendingSyncState(
      items: items ?? this.items,
      loaded: loaded ?? this.loaded,
      syncing: syncing ?? this.syncing,
    );
  }
}
