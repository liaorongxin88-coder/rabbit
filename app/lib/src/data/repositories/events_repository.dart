import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';

final eventsRepositoryProvider = Provider<EventsRepository>((ref) {
  return EventsRepository(ref.watch(apiClientProvider));
});

class EventsRepository {
  EventsRepository(this._api);

  final ApiClient _api;

  Future<List<EventItem>> listEvents(int houseId) {
    return _api.get<List<EventItem>>(
      '/api/events',
      houseId: houseId,
      query: const {'onlyUnnotified': true},
      decode: (data) {
        if (data is! List) {
          throw const ApiException('预警列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => EventItem.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }
}
