import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

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
