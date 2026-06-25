import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final settingsRepositoryProvider = Provider<SettingsRepository>((ref) {
  return SettingsRepository(ref.watch(apiClientProvider));
});

final currentHouseSettingProvider = FutureProvider<GlobalSetting>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    throw const ApiException('请先选择兔舍');
  }
  return ref.watch(settingsRepositoryProvider).getSetting(houseId);
});

class SettingsRepository {
  SettingsRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<GlobalSetting> getSetting(int houseId) {
    return _api.get<GlobalSetting>(
      '/api/settings',
      houseId: houseId,
      decode: (data) {
        if (data == null) {
          return GlobalSetting.defaultsForHouse(houseId);
        }
        if (data is! Map) {
          throw const ApiException('设置数据格式不正确');
        }
        return GlobalSetting.fromJson(
          Map<String, dynamic>.from(data),
          houseId: houseId,
        );
      },
    );
  }

  Future<void> updateSetting({
    required int houseId,
    required GlobalSetting setting,
  }) {
    return _api.put<void>(
      '/api/settings',
      houseId: houseId,
      body: setting.toUpdateJson(requestId: _uuid.v4()),
      decode: (_) {},
    );
  }
}
