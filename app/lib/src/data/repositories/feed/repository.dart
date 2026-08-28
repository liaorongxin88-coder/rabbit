import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/feed/log.dart';

final feedRepositoryProvider = Provider<FeedGateway>((ref) {
  return FeedRepository(ref.watch(apiClientProvider));
});

abstract interface class FeedGateway {
  Future<void> addFeedLog({
    required int houseId,
    required FeedLogDraft draft,
  });
}

class FeedRepository implements FeedGateway {
  FeedRepository(this._api);

  final ApiClient _api;

  @override
  Future<void> addFeedLog({
    required int houseId,
    required FeedLogDraft draft,
  }) {
    return _api.post<void>(
      '/api/feed-logs',
      houseId: houseId,
      body: draft.toJson(),
      decode: (_) {},
    );
  }
}
