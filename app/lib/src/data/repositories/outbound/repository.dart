import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';

final outboundRepositoryProvider = Provider<OutboundGateway>((ref) {
  return OutboundRepository(ref.watch(apiClientProvider));
});

abstract interface class OutboundGateway {
  Future<OutboundTask> createTask(
      {required int houseId,
      required String entryType,
      int? rabbitId,
      int? cageId,
      String? rowCode,
      bool resumeExisting = true});
  Future<OutboundTask> precheck({required int houseId, required String taskId});
  Future<OutboundTask> saveDraft(
      {required int houseId,
      required OutboundTask task,
      required String status,
      required List<OutboundSelectedItem> items,
      required DateTime saleTime,
      double? totalWeight,
      double? unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark});
  Future<void> cancel({required int houseId, required String taskId});
  Future<OutboundSubmitResult> submit(
      {required int houseId,
      required OutboundTask task,
      required List<OutboundSelectedItem> items,
      required String requestId,
      required DateTime saleTime,
      required double totalWeight,
      required double unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark});
  Future<OutboundSubmitResult> status(
      {required int houseId, required String requestId});
}

class OutboundRepository implements OutboundGateway {
  OutboundRepository(this._api);
  final ApiClient _api;

  @override
  Future<OutboundTask> createTask(
      {required int houseId,
      required String entryType,
      int? rabbitId,
      int? cageId,
      String? rowCode,
      bool resumeExisting = true}) {
    return _api.post('/api/outbound/tasks',
        houseId: houseId,
        body: {
          'entryType': entryType,
          if (rabbitId != null) 'rabbitId': rabbitId,
          if (cageId != null) 'cageId': cageId,
          if (rowCode != null) 'rowCode': rowCode,
          'resumeExisting': resumeExisting,
        },
        decode: _decodeTask);
  }

  @override
  Future<OutboundTask> precheck(
      {required int houseId, required String taskId}) {
    return _api.post('/api/outbound/tasks/$taskId/precheck',
        houseId: houseId, decode: _decodeTask);
  }

  @override
  Future<OutboundTask> saveDraft(
      {required int houseId,
      required OutboundTask task,
      required String status,
      required List<OutboundSelectedItem> items,
      required DateTime saleTime,
      double? totalWeight,
      double? unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark}) {
    return _api.put('/api/outbound/tasks/${task.taskId}',
        houseId: houseId,
        body: {
          'revision': task.revision,
          'status': status,
          'items': items.map((item) => item.toJson()).toList(),
          'saleTime': DateFormat('yyyy-MM-dd').format(saleTime),
          if (totalWeight != null) 'totalWeight': totalWeight,
          if (unitPrice != null) 'unitPricePerKg': unitPrice,
          'batchAllocations':
              batchAllocations.map((item) => item.toJson()).toList(),
          if (customer?.trim().isNotEmpty == true) 'customer': customer!.trim(),
          if (remark?.trim().isNotEmpty == true) 'remark': remark!.trim(),
        },
        decode: _decodeTask);
  }

  @override
  Future<void> cancel({required int houseId, required String taskId}) {
    return _api.post('/api/outbound/tasks/$taskId/cancel',
        houseId: houseId, decode: (_) {});
  }

  @override
  Future<OutboundSubmitResult> submit(
      {required int houseId,
      required OutboundTask task,
      required List<OutboundSelectedItem> items,
      required String requestId,
      required DateTime saleTime,
      required double totalWeight,
      required double unitPrice,
      required List<OutboundBatchAllocation> batchAllocations,
      String? customer,
      String? remark}) {
    return _api.post('/api/outbound/tasks/${task.taskId}/submit',
        houseId: houseId,
        body: {
          'rabbitIds': items.map((item) => item.rabbitId).toList(),
          'stateVersions': {
            for (final item in items) '${item.rabbitId}': item.stateVersion
          },
          'earlySaleReasons': {
            for (final item in items.where((item) => item.isEarlySale))
              '${item.rabbitId}': item.earlySaleReason
          },
          'saleTime': DateFormat('yyyy-MM-dd').format(saleTime),
          'totalWeight': totalWeight,
          'unitPricePerKg': unitPrice,
          'batchAllocations':
              batchAllocations.map((item) => item.toJson()).toList(),
          if (customer?.trim().isNotEmpty == true) 'customer': customer!.trim(),
          if (remark?.trim().isNotEmpty == true) 'remark': remark!.trim(),
          'requestId': requestId,
        },
        decode: _decodeResult);
  }

  @override
  Future<OutboundSubmitResult> status(
      {required int houseId, required String requestId}) {
    return _api.get('/api/outbound/requests/$requestId',
        houseId: houseId, decode: _decodeResult);
  }

  OutboundTask _decodeTask(Object? data) {
    return OutboundTask.fromJson(
      requireJsonObject(data, message: '出库任务格式不正确'),
    );
  }

  OutboundSubmitResult _decodeResult(Object? data) {
    return OutboundSubmitResult.fromJson(
      requireJsonObject(data, message: '出库结果格式不正确'),
    );
  }
}
