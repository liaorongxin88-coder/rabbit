import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/summary.dart';

final cageRepositoryProvider = Provider<CageRepository>((ref) {
  return CageRepository(ref.watch(apiClientProvider));
});

class CageRepository {
  CageRepository(this._api);

  final ApiClient _api;

  Future<List<Cage>> listCages(int houseId, {CancelToken? cancelToken}) {
    return _api.get<List<Cage>>(
      '/api/cages',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) {
        // 停用笼位仍占据真实货架位置，是否可放兔由 Cage 的容量规则判断。
        return requireJsonObjectList(data, message: '笼位列表格式不正确')
            .map(Cage.fromJson)
            .where((cage) => cage.id > 0)
            .toList();
      },
    );
  }

  Future<CageSummary> getCageSummary({
    required int houseId,
    required int cageId,
  }) {
    return _api.get<CageSummary>(
      '/api/cages/$cageId/summary',
      houseId: houseId,
      decode: (data) => CageSummary.fromJson(
        requireJsonObject(data, message: '笼位摘要格式不正确'),
      ),
    );
  }

  /// [cageNumber] 留空时由后端按「排-位-层」生成（见后端 CageNumbers）。
  Future<Cage> createCage({
    required int houseId,
    String? cageNumber,
    String? rowCode,
    int? layerIndex,
    int? positionIndex,
    String? remark,
  }) {
    return _api.post<Cage>(
      '/api/cages',
      houseId: houseId,
      body: {
        if (cageNumber != null && cageNumber.trim().isNotEmpty)
          'cageNumber': cageNumber.trim(),
        if (rowCode != null && rowCode.trim().isNotEmpty)
          'rowCode': rowCode.trim(),
        if (layerIndex != null) 'layerIndex': layerIndex,
        if (positionIndex != null) 'positionIndex': positionIndex,
        'isEnabled': true,
        'remark': remark ?? '',
      },
      decode: (data) => Cage.fromJson(
        requireJsonObject(data, message: '新增笼位结果格式不正确'),
      ),
    );
  }
}
