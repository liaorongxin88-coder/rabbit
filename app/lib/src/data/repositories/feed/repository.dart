import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/feed/log.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

final feedRepositoryProvider = Provider<FeedGateway>((ref) {
  return FeedRepository(ref.watch(apiClientProvider));
});

abstract interface class FeedGateway {
  Future<FeedAllocationPreview> previewAllocations({
    required int houseId,
    required List<int> rabbitIds,
    required DateTime feedTime,
  });

  Future<void> addFeedLog({
    required int houseId,
    required FeedLogDraft draft,
  });
}

class FeedRepository implements FeedGateway {
  FeedRepository(this._api);

  final ApiClient _api;

  @override
  Future<FeedAllocationPreview> previewAllocations({
    required int houseId,
    required List<int> rabbitIds,
    required DateTime feedTime,
  }) {
    final ids = rabbitIds.toSet().toList()..sort();
    return _api.post<FeedAllocationPreview>(
      '/api/feed-logs/allocation-preview',
      houseId: houseId,
      body: {
        'rabbitIds': ids,
        'feedTime': farmDateTimeToEpochMilliseconds(feedTime),
      },
      decode: (data) => FeedAllocationPreview.fromJson(
        requireJsonObject(data, message: '投喂归属预览格式不正确'),
      ),
    );
  }

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
