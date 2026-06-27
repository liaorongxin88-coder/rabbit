import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';

final settingsRepositoryProvider = Provider<SettingsRepository>((ref) {
  return SettingsRepository(ref.watch(apiClientProvider));
});

final userSettingProvider = FutureProvider<GlobalSetting>((ref) async {
  return ref.watch(settingsRepositoryProvider).getSetting();
});

final houseSettingProvider =
    FutureProvider.family<HouseSettingState, int>((ref, houseId) async {
  return ref.watch(settingsRepositoryProvider).getHouseSetting(houseId);
});

class SettingsRepository {
  SettingsRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<GlobalSetting> getSetting() {
    return _api.get<GlobalSetting>(
      '/api/settings',
      decode: (data) {
        if (data == null) {
          return GlobalSetting.defaults();
        }
        if (data is! Map) {
          throw const ApiException('设置数据格式不正确');
        }
        return GlobalSetting.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<void> updateSetting({
    required GlobalSetting setting,
  }) {
    return _api.put<void>(
      '/api/settings',
      body: setting.toUpdateJson(requestId: _uuid.v4()),
      decode: (_) {},
    );
  }

  Future<HouseSettingState> getHouseSetting(int houseId) {
    return _api.get<HouseSettingState>(
      '/api/house-settings',
      houseId: houseId,
      decode: (data) {
        if (data == null) {
          return HouseSettingState(
            setting: GlobalSetting.defaults(),
            customized: false,
          );
        }
        if (data is! Map) {
          throw const ApiException('兔舍设置数据格式不正确');
        }
        return HouseSettingState.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<void> updateHouseSetting({
    required int houseId,
    required GlobalSetting setting,
  }) {
    return _api.put<void>(
      '/api/house-settings',
      houseId: houseId,
      body: setting.toUpdateJson(requestId: _uuid.v4()),
      decode: (_) {},
    );
  }
}
