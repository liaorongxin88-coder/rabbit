import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/tracking.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

final currentHouseBatchesProvider =
    FutureProvider.autoDispose<List<Batch>>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    return const <Batch>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(batchRepositoryProvider)
      .listBatches(houseId, cancelToken: cancelToken);
});

final houseBatchesProvider =
    FutureProvider.autoDispose.family<List<Batch>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Batch>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(batchRepositoryProvider)
      .listBatches(houseId, cancelToken: cancelToken);
});

class BatchDetailRequest {
  const BatchDetailRequest({required this.houseId, required this.batchId});

  final int houseId;
  final int batchId;

  @override
  bool operator ==(Object other) {
    return other is BatchDetailRequest &&
        other.houseId == houseId &&
        other.batchId == batchId;
  }

  @override
  int get hashCode => Object.hash(houseId, batchId);
}

final batchDetailProvider =
    FutureProvider.autoDispose.family<Batch, BatchDetailRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0 || request.houseId <= 0 || request.batchId <= 0) {
      throw ArgumentError('批次路径参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(batchRepositoryProvider).getBatch(
          houseId: request.houseId,
          batchId: request.batchId,
          cancelToken: cancelToken,
        );
  },
);

final batchMembersProvider = FutureProvider.autoDispose
    .family<List<BatchRabbitItem>, BatchDetailRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0 || request.houseId <= 0 || request.batchId <= 0) {
      throw ArgumentError('批次路径参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(batchRepositoryProvider).listBatchRabbits(
          houseId: request.houseId,
          batchId: request.batchId,
          cancelToken: cancelToken,
        );
  },
);

class BatchTrackingRequest {
  const BatchTrackingRequest({
    required this.houseId,
    required this.batchId,
    required this.motherRabbitId,
  });

  final int houseId;
  final int batchId;
  final int motherRabbitId;

  @override
  bool operator ==(Object other) {
    return other is BatchTrackingRequest &&
        other.houseId == houseId &&
        other.batchId == batchId &&
        other.motherRabbitId == motherRabbitId;
  }

  @override
  int get hashCode => Object.hash(houseId, batchId, motherRabbitId);
}

final batchTrackingEventsProvider = FutureProvider.autoDispose
    .family<List<BatchTrackingEvent>, BatchTrackingRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0 ||
        request.houseId <= 0 ||
        request.batchId <= 0 ||
        request.motherRabbitId <= 0) {
      throw ArgumentError('批次追踪参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(batchRepositoryProvider).listBatchTrackingEvents(
          houseId: request.houseId,
          batchId: request.batchId,
          motherRabbitId: request.motherRabbitId,
          cancelToken: cancelToken,
        );
  },
);

/// Produces a stable fingerprint for the exact business payload of a write.
///
/// Text is trimmed and list-like values are sorted and de-duplicated so UI
/// selection order cannot accidentally rotate an idempotency key.
String canonicalBatchWriteFingerprint(Map<String, Object?> payload) {
  return jsonEncode(_canonicalWriteValue(payload));
}

Object? _canonicalWriteValue(Object? value) {
  if (value is String) {
    return value.trim();
  }
  if (value is Map) {
    final keys = value.keys.map((key) => key.toString()).toList()..sort();
    return <String, Object?>{
      for (final key in keys) key: _canonicalWriteValue(value[key]),
    };
  }
  if (value is Iterable) {
    final values = value.map(_canonicalWriteValue).toList()
      ..sort((left, right) => jsonEncode(left).compareTo(jsonEncode(right)));
    final seen = <String>{};
    return values.where((item) => seen.add(jsonEncode(item))).toList();
  }
  if (value == null || value is num || value is bool) {
    return value;
  }
  throw ArgumentError.value(
    value,
    'payload',
    '仅支持 JSON 可编码的写入参数',
  );
}

/// Keeps an idempotency key bound to one canonical business payload.
class BatchWriteRequestController {
  BatchWriteRequestController({
    String? requestId,
    String Function()? requestIdFactory,
  }) : _requestIdFactory = requestIdFactory ?? _newRequestId {
    _requestId = requestId ?? _requestIdFactory();
  }

  final String Function() _requestIdFactory;
  late String _requestId;
  String? _payloadFingerprint;

  String get requestId => _requestId;

  String requestIdFor(String canonicalPayloadFingerprint) {
    if (_payloadFingerprint == null) {
      _payloadFingerprint = canonicalPayloadFingerprint;
    } else if (_payloadFingerprint != canonicalPayloadFingerprint) {
      _requestId = _requestIdFactory();
      _payloadFingerprint = canonicalPayloadFingerprint;
    }
    return _requestId;
  }

  String startNewDraft() {
    _requestId = _requestIdFactory();
    _payloadFingerprint = null;
    return _requestId;
  }

  static String _newRequestId() => const Uuid().v4();
}
