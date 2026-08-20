import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';

final eventsRepositoryProvider = Provider<EventsRepository>((ref) {
  return EventsRepository(ref.watch(apiClientProvider));
});

class EventsRepository {
  EventsRepository(this._api);

  final ApiClient _api;

  Future<List<EventItem>> listEvents(
    int houseId, {
    DateTime? dueBefore,
  }) {
    return _api.get<List<EventItem>>(
      '/api/events',
      houseId: houseId,
      query: {
        'onlyUnnotified': true,
        if (dueBefore != null) 'dueBefore': dueBefore.millisecondsSinceEpoch,
      },
      decode: (data) => requireJsonObjectList(
        data,
        message: '预警列表格式不正确',
      ).map(EventItem.fromJson).toList(),
    );
  }
}
