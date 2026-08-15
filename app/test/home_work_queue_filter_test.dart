import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/home/widgets/home_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

void main() {
  testWidgets('production queue filters remain usable at true 200 percent text',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final now = DateTime.now();
    final events = [
      EventItem(
        recordId: 11,
        category: '生产周期',
        eventType: '配种',
        eventDate: now.subtract(const Duration(days: 1)),
        batchId: 7,
        rabbitId: 101,
        status: 'overdue',
        sourceHouseId: 1,
        sourceHouseName: '一号兔舍',
      ),
      EventItem(
        recordId: 12,
        category: '生产周期',
        eventType: '摸胎',
        eventDate: now,
        batchId: 7,
        rabbitId: 102,
        status: 'due',
        sourceHouseId: 1,
        sourceHouseName: '一号兔舍',
      ),
      EventItem(
        recordId: 13,
        category: '生产周期',
        eventType: '分娩',
        eventDate: now.add(const Duration(days: 1)),
        batchId: 8,
        rabbitId: 201,
        status: 'upcoming',
        sourceHouseId: 2,
        sourceHouseName: '二号兔舍',
      ),
    ];

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '一号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
              RabbitHouse(
                id: 2,
                name: '二号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => events),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: const TextScaler.linear(2),
            ),
            child: child!,
          ),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('显示 3 / 3 条任务'), findsOneWidget);
    expect(tester.takeException(), isNull);

    final search = find.byKey(const ValueKey('production-work-search'));
    await tester.ensureVisible(search);
    await tester.enterText(search, '101');
    await tester.pumpAndSettle();

    expect(find.text('显示 1 / 3 条任务'), findsOneWidget);
    expect(find.text('母兔 #101'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('production-filter-clear')));
    await tester.pumpAndSettle();
    expect(find.text('显示 3 / 3 条任务'), findsOneWidget);

    final dueFilter = find.byKey(const ValueKey('production-due-filter'));
    await tester.ensureVisible(dueFilter);
    await tester.tap(dueFilter);
    await tester.pumpAndSettle();
    await tester.tap(find.text('仅逾期').last);
    await tester.pumpAndSettle();

    expect(find.text('显示 1 / 3 条任务'), findsOneWidget);
    expect(find.text('母兔 #101'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
