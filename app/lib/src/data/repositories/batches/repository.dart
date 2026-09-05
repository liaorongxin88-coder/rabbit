import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/carcass_yield.dart';
import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/statistics.dart';
import 'package:rabbit_flutter/src/domain/batches/tracking.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

final batchRepositoryProvider = Provider<BatchRepository>((ref) {
  return BatchRepository(ref.watch(apiClientProvider));
});

class BatchRepository {
  BatchRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Batch>> listBatches(
    int houseId, {
    CancelToken? cancelToken,
  }) async {
    const pageSize = 200;
    final batches = <Batch>[];
    var page = 1;

    while (true) {
      final items = await listBatchesPage(
        houseId: houseId,
        page: page,
        pageSize: pageSize,
        cancelToken: cancelToken,
      );
      batches.addAll(items);
      if (items.length < pageSize) {
        return batches;
      }
      page += 1;
    }
  }

  Future<List<Batch>> listBatchesPage({
    required int houseId,
    required int page,
    required int pageSize,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<Batch>>(
      '/api/batches',
      houseId: houseId,
      query: {'page': page, 'pageSize': pageSize},
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(
        data,
        message: '批次列表格式不正确',
      ).map(Batch.fromJson).toList(),
    );
  }

  Future<Batch> createBatch({
    required int houseId,
    required String batchCode,
    required List<int> femaleRabbitIds,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<Batch>(
      '/api/batches',
      houseId: houseId,
      body: {
        'batchCode': batchCode.trim(),
        'femaleRabbitIds': _sortedUniqueIds(femaleRabbitIds),
        'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) => Batch.fromJson(
        requireJsonObject(data, message: '创建批次结果格式不正确'),
      ),
    );
  }

  /// 改批次编号。
  ///
  /// 编号是操作者认批次的名字，建完才发现打错字时不必重建批次搬兔只。
  Future<Batch> renameBatch({
    required int houseId,
    required int batchId,
    required String batchCode,
    String? requestId,
  }) {
    return _api.post<Batch>(
      '/api/batches/$batchId/code',
      houseId: houseId,
      body: {
        'batchCode': batchCode.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) => Batch.fromJson(
        requireJsonObject(data, message: '批次改名结果格式不正确'),
      ),
    );
  }

  Future<Batch> getBatch({
    required int houseId,
    required int batchId,
    CancelToken? cancelToken,
  }) {
    return _api.get<Batch>(
      '/api/batches/$batchId',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) => Batch.fromJson(
        requireJsonObject(data, message: '批次详情格式不正确'),
      ),
    );
  }

  Future<BatchStatistics> getBatchStatistics({
    required int houseId,
    required int batchId,
    CancelToken? cancelToken,
  }) {
    return _api.get<BatchStatistics>(
      '/api/batches/$batchId/statistics',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) => BatchStatistics.fromJson(
        requireJsonObject(data, message: '批次统计格式不正确'),
      ),
    );
  }

  Future<File> downloadBatchStatistics({
    required int houseId,
    required int batchId,
    Directory? directory,
  }) async {
    final targetDirectory = directory ?? await getTemporaryDirectory();
    await targetDirectory.create(recursive: true);
    final separator = Platform.pathSeparator;
    final fallbackName = 'batch-$batchId-statistics.xlsx';
    final partial = File(
      '${targetDirectory.path}$separator.$fallbackName-${_uuid.v4()}.part',
    );
    try {
      final result = await _api.downloadProtected(
        '/api/reports/batches/$batchId/statistics.xlsx',
        partial.path,
        houseId: houseId,
      );
      if (!_isSpreadsheetContentType(result.contentType)) {
        throw const ApiException('批次统计导出文件类型不正确');
      }
      if (!await partial.exists() || await partial.length() == 0) {
        throw const ApiException('批次统计导出文件为空');
      }
      final filename = _batchStatisticsFilename(
        result.contentDisposition,
        fallbackName,
      );
      final target = File('${targetDirectory.path}$separator$filename');
      if (await target.exists()) await target.delete();
      return partial.rename(target.path);
    } catch (_) {
      await _deleteFileBestEffort(partial);
      rethrow;
    }
  }

  Future<BatchCarcassYieldRecord> createCarcassYield({
    required int houseId,
    required int batchId,
    required BatchCarcassYieldDraft draft,
  }) {
    final validation = draft.validate();
    if (validation != null) {
      throw ArgumentError(validation);
    }
    return _api.post<BatchCarcassYieldRecord>(
      '/api/batches/$batchId/carcass-yields',
      houseId: houseId,
      body: draft.toJson(),
      decode: (data) => BatchCarcassYieldRecord.fromJson(
        requireJsonObject(data, message: '出肉率保存结果格式不正确'),
      ),
    );
  }

  Future<BatchCarcassYieldPage> listCarcassYields({
    required int houseId,
    required int batchId,
    int page = 1,
    int pageSize = 20,
    CancelToken? cancelToken,
  }) {
    return _api.get<BatchCarcassYieldPage>(
      '/api/batches/$batchId/carcass-yields',
      houseId: houseId,
      query: {'page': page, 'pageSize': pageSize},
      cancelToken: cancelToken,
      decode: (data) => BatchCarcassYieldPage.fromJson(
        requireJsonObject(data, message: '出肉率历史格式不正确'),
      ),
    );
  }

  Future<List<PendingWeaningRecord>> listPendingWeaningRecords({
    required int houseId,
    required int batchId,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<PendingWeaningRecord>>(
      '/api/batches/$batchId/weaning-records',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(
        data,
        message: '待分笼记录格式不正确',
      ).map(PendingWeaningRecord.fromJson).toList(),
    );
  }

  Future<WeaningSeparationResult> separatePendingWeaning({
    required int houseId,
    required int batchId,
    required int weaningRecordId,
    required List<CageAllocation> allocations,
    int? motherRabbitId,
    int? fatherRabbitId,
    String? requestId,
  }) {
    return _api.post<WeaningSeparationResult>(
      '/api/batches/$batchId/weaning-records/$weaningRecordId/separation',
      houseId: houseId,
      body: {
        'allocations': allocations.map((item) => item.toJson()).toList(),
        if (motherRabbitId != null) 'motherRabbitId': motherRabbitId,
        if (fatherRabbitId != null) 'fatherRabbitId': fatherRabbitId,
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) => WeaningSeparationResult.fromJson(
        requireJsonObject(data, message: '分笼结果格式不正确'),
      ),
    );
  }

  Future<List<BatchRabbitItem>> listBatchRabbits({
    required int houseId,
    required int batchId,
    String? role,
    bool? active,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<BatchRabbitItem>>(
      '/api/batches/$batchId/batch-rabbits',
      houseId: houseId,
      query: {
        if (role != null && role.trim().isNotEmpty) 'role': role.trim(),
        if (active != null) 'active': active,
      },
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(
        data,
        message: '批次兔子列表格式不正确',
      ).map(BatchRabbitItem.fromJson).toList(),
    );
  }

  Future<List<BatchTrackingEvent>> listBatchTrackingEvents({
    required int houseId,
    required int batchId,
    required int motherRabbitId,
    int limit = 50,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<BatchTrackingEvent>>(
      '/api/repro/events',
      houseId: houseId,
      query: {
        'batchId': batchId,
        'motherRabbitId': motherRabbitId,
        'limit': limit.clamp(1, 200),
      },
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(
        data,
        message: '批次操作记录格式不正确',
      ).map(BatchTrackingEvent.fromJson).toList(),
    );
  }

  /// 向已有批次追加母兔标签；服务端按需要建立尚未存在的生产管线。
  ///
  /// [requestId] 由调用方在表单草稿生命周期内持有，失败重试时必须复用，
  /// 以便服务端幂等回放而不会重复建立成员关系。
  Future<void> addBatchMembers({
    required int houseId,
    required int batchId,
    required List<int> femaleRabbitIds,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/members',
      houseId: houseId,
      body: {
        'femaleRabbitIds': _sortedUniqueIds(femaleRabbitIds),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  /// 向批次追加任意受支持兔只；同一兔只可以同时携带多个批次标签。
  Future<void> addBatchRabbits({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/members',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> removeBatchRabbit({
    required int houseId,
    required int batchId,
    required int rabbitId,
    String? requestId,
  }) {
    return _api.delete<void>(
      '/api/batches/$batchId/members/$rabbitId',
      houseId: houseId,
      query: {'requestId': requestId ?? _uuid.v4()},
      decode: (_) {},
    );
  }

  Future<void> completeBatch({
    required int houseId,
    required int batchId,
    required DateTime endDate,
    required bool force,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/complete',
      houseId: houseId,
      body: {
        'endDate': formatBatchWriteDate(endDate),
        'force': force,
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitSale({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    required DateTime saleDate,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/sale',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'saleDate': formatBatchWriteDate(saleDate),
        'requestId': requestId ?? _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }
}

String formatBatchWriteDate(DateTime date) {
  final y = date.year.toString().padLeft(4, '0');
  final m = date.month.toString().padLeft(2, '0');
  final d = date.day.toString().padLeft(2, '0');
  return '$y-$m-$d';
}

String formatBatchWriteDateTime(DateTime date) => farmDateTimeToIso(date);

const _spreadsheetContentType =
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

bool _isSpreadsheetContentType(String? contentType) =>
    contentType?.split(';').first.trim().toLowerCase() ==
    _spreadsheetContentType;

Future<void> _deleteFileBestEffort(File file) async {
  try {
    if (await file.exists()) await file.delete();
  } catch (_) {
    // Cleanup must not replace the download validation or transport error.
  }
}

String _batchStatisticsFilename(String? contentDisposition, String fallback) {
  final encoded = RegExp(
    r'''filename\*\s*=\s*(?:UTF-8'')?([^;]+)''',
    caseSensitive: false,
  ).firstMatch(contentDisposition ?? '');
  final regular = RegExp(
    r'''filename\s*=\s*(?:"([^"]*)"|([^;]+))''',
    caseSensitive: false,
  ).firstMatch(contentDisposition ?? '');
  final raw = encoded?.group(1) ?? regular?.group(1) ?? regular?.group(2);
  if (raw == null) return fallback;
  String decoded;
  try {
    decoded = Uri.decodeComponent(raw.trim().replaceAll('"', ''));
  } on FormatException {
    return fallback;
  }
  final basename = decoded.replaceAll('\\', '/').split('/').last;
  var safe = basename
      .replaceAll(RegExp(r'[\x00-\x1f\x7f<>:"/\\|?*]'), '_')
      .replaceAll(RegExp(r'^[ .]+|[ .]+$'), '');
  if (safe.isEmpty || !safe.toLowerCase().endsWith('.xlsx')) {
    return fallback;
  }
  if (safe.length > 180) {
    safe = '${safe.substring(0, 175)}.xlsx';
  }
  return safe;
}

List<int> _sortedUniqueIds(Iterable<int> ids) {
  return ids.toSet().toList()..sort();
}
