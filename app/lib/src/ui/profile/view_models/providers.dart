import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/auth/account.dart';
import 'package:rabbit_flutter/src/domain/profile/profile.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

final userProfileProvider = FutureProvider<UserProfile>((ref) {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(accountRepositoryProvider).loadProfile();
});
