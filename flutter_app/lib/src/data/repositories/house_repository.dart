import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';

final houseRepositoryProvider = Provider<HouseRepository>((ref) {
  return HouseRepository(ref.watch(apiClientProvider));
});

final housesProvider = FutureProvider<List<RabbitHouse>>((ref) {
  return ref.watch(houseRepositoryProvider).listHouses();
});

class HouseRepository {
  HouseRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<RabbitHouse>> listHouses() {
    return _api.get<List<RabbitHouse>>(
      '/api/houses',
      decode: (data) {
        if (data is! List) {
          throw const ApiException('兔舍列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map(
                (item) => RabbitHouse.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<RabbitHouse> createHouse({
    required String name,
    required int rows,
    required int cols,
    required int layers,
    String remark = '',
  }) {
    return _api.post<RabbitHouse>(
      '/api/houses',
      body: {
        'name': name,
        'layoutRows': rows,
        'layoutCols': cols,
        'layoutLayers': layers,
        'remark': remark,
        'requestId': _uuid.v4(),
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('创建兔舍结果格式不正确');
        }
        return RabbitHouse.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }
}
