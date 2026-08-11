import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/settings_repository.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final userSettingProvider = FutureProvider<GlobalSetting>((ref) async {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(settingsRepositoryProvider).getSetting();
});

final houseSettingProvider =
    FutureProvider.family<HouseSettingState, int>((ref, houseId) async {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(settingsRepositoryProvider).getHouseSetting(houseId);
});
