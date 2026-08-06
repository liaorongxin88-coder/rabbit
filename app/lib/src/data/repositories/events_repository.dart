import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';

final eventsRepositoryProvider = Provider<EventsRepository>((ref) {
  return EventsRepository(ref.watch(apiClientProvider));
});

final homeEventsProvider = FutureProvider<List<EventItem>>((ref) async {
  final houses = await ref.watch(housesProvider.future);
  if (houses.isEmpty) {
    return const <EventItem>[];
  }
  final repository = ref.watch(eventsRepositoryProvider);
  final grouped = await Future.wait(
    houses.map((house) async {
      final items = await repository.listEvents(house.id);
      return items
          .map(
            (item) => item.copyWith(
              sourceHouseId: house.id,
              sourceHouseName: house.name,
            ),
          )
          .toList();
    }),
  );
  final merged = grouped.expand((items) => items).toList()
    ..sort(_compareEvents);
  return merged;
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

int _compareEvents(EventItem a, EventItem b) {
  final priority = _statusPriority(a).compareTo(_statusPriority(b));
  if (priority != 0) {
    return priority;
  }
  final aDate = a.eventDate;
  final bDate = b.eventDate;
  if (aDate == null && bDate == null) {
    return a.recordId.compareTo(b.recordId);
  }
  if (aDate == null) {
    return 1;
  }
  if (bDate == null) {
    return -1;
  }
  final date = aDate.compareTo(bDate);
  if (date != 0) {
    return date;
  }
  return a.recordId.compareTo(b.recordId);
}

int _statusPriority(EventItem event) {
  if (event.isOverdue) {
    return 0;
  }
  if (event.isDue) {
    return 1;
  }
  return 2;
}
