import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';

final rabbitRepositoryProvider = Provider<RabbitRepository>((ref) {
  return RabbitRepository(ref.watch(apiClientProvider));
});

final houseCagesProvider =
    FutureProvider.family<List<Cage>, int>((ref, houseId) async {
  if (houseId <= 0) {
    return const <Cage>[];
  }
  return ref.watch(rabbitRepositoryProvider).listCages(houseId);
});

final houseRabbitsProvider =
    FutureProvider.family<List<Rabbit>, int>((ref, houseId) async {
  if (houseId <= 0) {
    return const <Rabbit>[];
  }
  return ref.watch(rabbitRepositoryProvider).listRabbits(houseId);
});

class RabbitRepository {
  RabbitRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Cage>> listCages(int houseId) {
    return _api.get<List<Cage>>(
      '/api/cages',
      houseId: houseId,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('笼位列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => Cage.fromJson(Map<String, dynamic>.from(item)))
            .where((cage) => cage.id > 0 && cage.isEnabled)
            .toList();
      },
    );
  }

  Future<List<Rabbit>> listRabbits(int houseId) {
    return _api.get<List<Rabbit>>(
      '/api/rabbits',
      houseId: houseId,
      query: const {'active': true, 'page': 1, 'pageSize': 50},
      decode: (data) {
        if (data is! List) {
          throw const ApiException('兔只列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => Rabbit.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<Cage> createCage({
    required int houseId,
    required String cageNumber,
    String? remark,
  }) {
    return _api.post<Cage>(
      '/api/cages',
      houseId: houseId,
      body: {
        'cageNumber': cageNumber,
        'isEnabled': true,
        'remark': remark ?? '',
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('新增笼位结果格式不正确');
        }
        return Cage.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<Rabbit> createRabbit({
    required int houseId,
    required int cageId,
    required String type,
    required String gender,
    required String breed,
    required String arrivalMethod,
    required double? weight,
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'type': type,
      'gender': gender,
      'arrivalMethod': arrivalMethod,
      'arrivalDate': DateTime.now().millisecondsSinceEpoch,
      'requestId': _uuid.v4(),
    };
    final trimmedBreed = breed.trim();
    if (trimmedBreed.isNotEmpty) {
      body['breed'] = trimmedBreed;
    }
    if (weight != null && weight > 0) {
      body['weight'] = weight;
    }

    return _api.post<Rabbit>(
      '/api/rabbits',
      houseId: houseId,
      body: body,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('录入兔只结果格式不正确');
        }
        return Rabbit.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<Rabbit> updateRabbit({
    required int houseId,
    required int rabbitId,
    required int cageId,
    required int? motherId,
    required String breed,
    required String arrivalMethod,
    required DateTime? arrivalDate,
    required double? weight,
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'arrivalMethod': arrivalMethod,
      'requestId': _uuid.v4(),
    };
    if (motherId != null && motherId > 0) {
      body['motherId'] = motherId;
    }
    if (arrivalDate != null) {
      body['arrivalDate'] = arrivalDate.millisecondsSinceEpoch;
    }
    final trimmedBreed = breed.trim();
    if (trimmedBreed.isNotEmpty) {
      body['breed'] = trimmedBreed;
    }
    if (weight != null && weight > 0) {
      body['weight'] = weight;
    }

    return _api.put<Rabbit>(
      '/api/rabbits/$rabbitId',
      houseId: houseId,
      body: body,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('编辑兔只结果格式不正确');
        }
        return Rabbit.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }
}
