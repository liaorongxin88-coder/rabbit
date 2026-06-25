import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';

void main() {
  testWidgets('shows login screen before session is restored', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    expect(find.text('智能兔管家'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
  });

  testWidgets('restores session and opens shell without duplicate keys',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260623',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '测试兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 3,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith(
            (_) async => const <EventItem>[],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('今日预警!'), findsOneWidget);
  });
}

class ProviderScopeWrapper extends StatelessWidget {
  const ProviderScopeWrapper({super.key, this.overrides = const []});

  final List<Override> overrides;

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: overrides,
      child: const RabbitManagerApp(),
    );
  }
}
