import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/rabbits/vaccination.dart';

/// 疫苗接种的后端接口封装。
final vaccinationRepositoryProvider = Provider<VaccinationRepository>((ref) {
  return VaccinationRepository(ref.watch(apiClientProvider));
});

class VaccinationRepository {
  VaccinationRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  /// 批量接种。单只兔就是长度为 1 的 [rabbitIds]，与后端同形。
  ///
  /// 日期用 epoch 毫秒上报，避免依赖服务端时区或某种 Jackson 文本日期格式，
  /// 与 [RabbitRepository.createRabbitSale] 的做法一致。
  Future<int> createVaccination({
    required int houseId,
    required List<int> rabbitIds,
    required String vaccineName,
    required DateTime vaccinatedAt,
    String? vaccineBatchNo,
    String? dose,
    String? route,
    DateTime? nextDueDate,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<int>(
      '/api/vaccinations',
      houseId: houseId,
      body: {
        'rabbitIds': rabbitIds,
        'vaccineName': vaccineName.trim(),
        if (vaccineBatchNo != null && vaccineBatchNo.trim().isNotEmpty)
          'vaccineBatchNo': vaccineBatchNo.trim(),
        if (dose != null && dose.trim().isNotEmpty) 'dose': dose.trim(),
        if (route != null && route.trim().isNotEmpty) 'route': route.trim(),
        'vaccinatedAt': vaccinatedAt.millisecondsSinceEpoch,
        if (nextDueDate != null)
          'nextDueDate': nextDueDate.millisecondsSinceEpoch,
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) {
        final json = requireJsonObject(data, message: '接种结果格式不正确');
        final created = json['created'];
        return created is num ? created.toInt() : 0;
      },
    );
  }

  Future<List<VaccinationRecord>> listByRabbit({
    required int houseId,
    required int rabbitId,
    int limit = 50,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<VaccinationRecord>>(
      '/api/vaccinations',
      houseId: houseId,
      query: {'rabbitId': rabbitId, 'limit': limit},
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(data, message: '接种记录格式不正确')
          .map(VaccinationRecord.fromJson)
          .toList(growable: false),
    );
  }

  Future<List<VaccinationRecord>> listDue({
    required int houseId,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<VaccinationRecord>>(
      '/api/vaccinations/due',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(data, message: '待接种列表格式不正确')
          .map(VaccinationRecord.fromJson)
          .toList(growable: false),
    );
  }
}
