import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/account_repository.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final userProfileProvider = FutureProvider<UserProfile>((ref) {
  ref.watch(authenticatedUserIdProvider);
  return ref.watch(accountRepositoryProvider).loadProfile();
});
