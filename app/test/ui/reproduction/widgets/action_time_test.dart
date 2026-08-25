import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/ui/reproduction/widgets/action_time.dart';

void main() {
  test('action time field formats UTC instants in the farm time zone', () {
    expect(
      formatActionTime(DateTime.utc(2025, 1, 2, 16, 30)),
      '2025-01-03 00:30',
    );
  });

  testWidgets('action time pickers start on the farm calendar day and time',
      (tester) async {
    const openKey = ValueKey('open-action-time-picker');
    final current = DateTime.utc(2025, 1, 2, 16, 30);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => FilledButton(
              key: openKey,
              onPressed: () => pickActionTime(
                context: context,
                current: current,
                helpText: '选择测试日期',
              ),
              child: const Text('打开'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(openKey));
    await tester.pumpAndSettle();

    final dateDialog = tester.widget<DatePickerDialog>(
      find.byType(DatePickerDialog),
    );
    expect(dateDialog.initialDate, DateTime(2025, 1, 3));

    await tester.tap(find.text('下一步'));
    await tester.pumpAndSettle();

    final timeDialog = tester.widget<TimePickerDialog>(
      find.byType(TimePickerDialog),
    );
    expect(timeDialog.initialTime, const TimeOfDay(hour: 0, minute: 30));

    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
  });
}
