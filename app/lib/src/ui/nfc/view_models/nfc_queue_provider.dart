import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final nfcCageWriteQueueProvider =
    FutureProvider.family<List<NfcCageQueueItem>, int>((ref, houseId) {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return Future.value(const <NfcCageQueueItem>[]);
  }
  return ref.watch(nfcRepositoryProvider).listWriteQueue(houseId);
});
