import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/houses/repository.dart';
import 'package:rabbit_flutter/src/domain/houses/member.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

final housesProvider = FutureProvider<List<RabbitHouse>>((ref) {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0) {
    return Future.value(const <RabbitHouse>[]);
  }
  return ref.watch(houseRepositoryProvider).listHouses();
});

final housePermissionProvider =
    FutureProvider.family<HousePermission, int>((ref, houseId) {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return Future.value(const HousePermission(perms: 'view', isAdmin: false));
  }
  return ref.watch(houseRepositoryProvider).getMyPermission(houseId);
});

final houseMembersProvider =
    FutureProvider.family<List<HouseMember>, int>((ref, houseId) {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return Future.value(const <HouseMember>[]);
  }
  return ref.watch(houseRepositoryProvider).listMembers(houseId);
});
