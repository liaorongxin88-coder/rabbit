import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/settings/repository.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/reproduction/reminder_preference.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

final userSettingProvider = FutureProvider<GlobalSetting>((ref) async {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(settingsRepositoryProvider).getSetting();
});

final houseSettingProvider =
    FutureProvider.family<HouseSettingState, int>((ref, houseId) async {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(settingsRepositoryProvider).getHouseSetting(houseId);
});

final reminderPreferenceProvider =
    FutureProvider.family<ReminderPreference, int>((ref, houseId) async {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(settingsRepositoryProvider).getReminderPreference(houseId);
});
