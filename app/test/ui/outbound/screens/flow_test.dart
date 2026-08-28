import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/outbound/screens/flow.dart';

import '../view_models/controller_test.dart' show FakeOutboundGateway;

void main() {
  for (final size in [const Size(360, 800), const Size(412, 915)]) {
    testWidgets(
        'outbound selection and confirmation fit ${size.width.toInt()}x${size.height.toInt()}',
        (tester) async {
      SharedPreferences.setMockInitialValues({});
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final gateway = FakeOutboundGateway();
      final entry = OutboundEntry(
          userId: size.width.toInt(), houseId: 8, entryType: 'HOUSE');

      await tester.pumpWidget(
        ProviderScope(
          overrides: _editableOverrides(gateway),
          child: MaterialApp(
              theme: buildAppTheme(), home: OutboundFlowScreen(entry: entry)),
        ),
      );
      await _pumpUntilFound(tester, find.text('正常可出库'));

      expect(find.text('正常可出库'), findsOneWidget);
      expect(find.text('候选范围'), findsOneWidget);
      expect(find.text('当前兔舍'), findsOneWidget);
      expect(find.byKey(const ValueKey('outbound-filter-selected')),
          findsOneWidget);
      final mode = tester.widget<SegmentedButton<OutboundSelectionMode>>(
        find.byKey(const ValueKey('outbound-selection-mode')),
      );
      expect(mode.selected, {OutboundSelectionMode.house});
      expect(find.text('取消整舍已选 1 只'), findsOneWidget);
      expect(find.text('下一步 · 1 只'), findsOneWidget);
      final nextButton = find.byKey(const ValueKey('outbound-continue-button'));
      expect(tester.getSize(nextButton).height, greaterThanOrEqualTo(48));
      expect(tester.takeException(), isNull);

      await tester.tap(find.text('下一步 · 1 只'));
      await tester.pumpAndSettle();
      expect(find.text('销售信息'), findsOneWidget);
      expect(find.text('确认出库 1 只'), findsOneWidget);
      final submitButton = find.byKey(const ValueKey('outbound-submit-button'));
      expect(tester.getSize(submitButton).height, greaterThanOrEqualTo(48));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets(
      'confirmation submit bar follows dynamic keyboard insets on common devices',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    final gateway = FakeOutboundGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 203, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));
    await tester.tap(find.byKey(const ValueKey('outbound-continue-button')));
    await _pumpUntilFound(tester, find.text('销售信息'));

    final inputValues = <Key, String>{
      const ValueKey('outbound-total-weight'): '2.5',
      const ValueKey('outbound-unit-price'): '18.2',
      const ValueKey('outbound-customer'): '测试客户',
      const ValueKey('outbound-remark'): '出库备注',
    };
    for (final deviceSize in const [
      Size(360, 800),
      Size(393, 852),
      Size(412, 915),
    ]) {
      tester.view.physicalSize = deviceSize;
      for (final keyboardHeight in const [180.0, 300.0, 420.0]) {
        for (final input in inputValues.entries) {
          tester.view.resetViewInsets();
          await tester.pumpAndSettle();
          final field = find.byKey(input.key);
          await _scrollConfirmUntilVisible(tester, field);
          await tester.enterText(field, input.value);

          tester.view.viewInsets = FakeViewPadding(bottom: keyboardHeight);
          await tester.pumpAndSettle();

          final submitRect = tester.getRect(
            find.byKey(const ValueKey('outbound-submit-button')),
          );
          expect(submitRect.top, greaterThanOrEqualTo(0));
          expect(
            submitRect.bottom,
            lessThanOrEqualTo(deviceSize.height - keyboardHeight),
            reason:
                '${input.key} must remain actionable at ${deviceSize.width}x${deviceSize.height} with a ${keyboardHeight}px keyboard',
          );
          expect(tester.takeException(), isNull);
        }
      }
    }
  });

  testWidgets('outbound core flow remains usable with 200 percent text',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final gateway = FakeOutboundGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: AppTypography.ergonomicTextScaler(
                const TextScaler.linear(2),
              ),
            ),
            child: child!,
          ),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 200, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    final context = tester.element(find.byType(OutboundFlowScreen));
    expect(MediaQuery.textScalerOf(context).scale(10), 20);
    expect(find.byKey(const ValueKey('outbound-nfc-cage-capture')), findsOneWidget);
    expect(find.text('碰标签加入笼位'), findsOneWidget);
    expect(find.text('下一步 · 1 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('下一步 · 1 只'));
    await tester.tap(find.text('下一步 · 1 只'));
    await tester.pumpAndSettle();
    expect(find.text('确认出库 1 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('early-sale dialog closes without disposing an active field',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 201, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    await tester.tap(
      find.byKey(const ValueKey('outbound-summary-early-sale')),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('1-1-1'));
    await tester.tap(find.text('1-1-1'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('兔只操作'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('提前出售'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('outbound-early-sale-reason')),
      '客户提前采购',
    );
    await tester.tap(
      find.byKey(const ValueKey('outbound-early-sale-confirm')),
    );
    await tester.pumpAndSettle();

    expect(find.text('提前出售 · 客户提前采购'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'conflict details are revealed after the confirmation form scrolls',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..returnConflict = true;

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 202, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));
    await tester.tap(find.text('下一步 · 1 只'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('outbound-total-weight')),
      '2.5',
    );
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
    await tester.pumpAndSettle();

    final conflictTitle = find.text('1 只兔状态冲突');
    expect(conflictTitle, findsOneWidget);
    expect(tester.getTopLeft(conflictTitle).dy, greaterThanOrEqualTo(0));
    expect(tester.takeException(), isNull);
  });

  testWidgets('7000-rabbit selection lazily builds only visible cage cards',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final gateway = FakeOutboundGateway()
      ..task = _largeTask(rabbitCount: 7000, rabbitsPerCage: 7);

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 7000, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    final builtCages = find.byWidgetPredicate((widget) {
      final key = widget.key;
      return key is ValueKey<String> && key.value.startsWith('outbound-cage-');
    });
    expect(find.text('下一步 · 7000 只'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('outbound-selection-scroll')),
      findsOneWidget,
    );
    expect(find.byType(SliverList), findsWidgets);
    expect(builtCages.evaluate().length, greaterThan(0));
    expect(builtCages.evaluate().length, lessThan(30));
    expect(find.byKey(const ValueKey('outbound-cage-10999')), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const ValueKey('outbound-continue-button')));
    await _pumpUntilFound(tester, find.text('销售信息'));
    await _scrollConfirmUntilVisible(tester, find.text('出库清单'));
    final firstRow = find.byKey(const ValueKey('outbound-confirm-row-R001'));
    await _scrollConfirmUntilVisible(tester, firstRow);
    await tester.tap(firstRow);
    await tester.pumpAndSettle();

    final confirmCages = find.byWidgetPredicate((widget) {
      final key = widget.key;
      return key is ValueKey<String> &&
          key.value.startsWith('outbound-confirm-cage-');
    });
    expect(confirmCages, findsNWidgets(20));
    expect(find.byKey(const ValueKey('outbound-confirm-cage-10019')),
        findsOneWidget);
    expect(find.byKey(const ValueKey('outbound-confirm-cage-10020')),
        findsNothing);
    expect(find.byKey(const ValueKey('outbound-confirm-more-R001')),
        findsOneWidget);
    expect(tester.takeException(), isNull);

    final showMore = find.byKey(const ValueKey('outbound-confirm-more-R001'));
    await _scrollConfirmUntilVisible(tester, showMore);
    await tester.tap(showMore);
    await tester.pumpAndSettle();
    expect(confirmCages, findsNWidgets(40));
    expect(find.byKey(const ValueKey('outbound-confirm-cage-10020')),
        findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('1200-rabbit cage uses bounded confirmation summary',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final gateway = FakeOutboundGateway()
      ..task = _largeTask(
        rabbitCount: 1200,
        rabbitsPerCage: 1200,
        status: 'WAITING_CONFIRMATION',
      );

    await tester.pumpWidget(
      ProviderScope(
        overrides: _editableOverrides(gateway),
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 1200, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('销售信息'));
    await _scrollConfirmUntilVisible(tester, find.text('出库清单'));
    final firstRow = find.byKey(const ValueKey('outbound-confirm-row-R001'));
    await _scrollConfirmUntilVisible(tester, firstRow);
    await tester.tap(firstRow);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('outbound-confirm-cage-10000')),
        findsOneWidget);
    expect(find.textContaining('另 1196 只'), findsOneWidget);
    expect(find.textContaining('#1200'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('permission loading does not initialize an outbound task',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    final permission = Completer<HousePermission>();
    const entry = OutboundEntry(userId: 801, houseId: 8, entryType: 'HOUSE');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboundRepositoryProvider.overrideWithValue(gateway),
          housePermissionProvider(8).overrideWith((_) => permission.future),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(entry: entry),
        ),
      ),
    );
    await tester.pump();

    expect(
      find.byKey(const ValueKey('outbound-permission-loading')),
      findsOneWidget,
    );
    expect(gateway.createCalls, 0);
    expect(find.byKey(const ValueKey('outbound-submit-button')), findsNothing);

    permission.complete(_editablePermission);
    await _pumpUntilFound(tester, find.text('正常可出库'));

    expect(gateway.createCalls, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('read-only deep link never creates or exposes outbound writes',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    const entry = OutboundEntry(userId: 802, houseId: 8, entryType: 'HOUSE');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboundRepositoryProvider.overrideWithValue(gateway),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'view',
              isAdmin: false,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(entry: entry),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('outbound-read-only-state')),
      findsOneWidget,
    );
    expect(find.text('当前账号仅可查看'), findsOneWidget);
    expect(find.byKey(const ValueKey('outbound-submit-button')), findsNothing);
    expect(find.text('放弃草稿'), findsNothing);
    expect(find.text('重新预检'), findsNothing);
    expect(gateway.createCalls, 0);
    expect(gateway.cancelCalls, 0);
    expect(gateway.submitCalls, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('permission error retries before initializing outbound',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();
    var permissionCalls = 0;
    const entry = OutboundEntry(userId: 803, houseId: 8, entryType: 'HOUSE');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboundRepositoryProvider.overrideWithValue(gateway),
          housePermissionProvider(8).overrideWith((_) {
            permissionCalls += 1;
            if (permissionCalls == 1) {
              return Future<HousePermission>.error('权限服务暂不可用');
            }
            return Future.value(_editablePermission);
          }),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(entry: entry),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('outbound-permission-error')),
      findsOneWidget,
    );
    expect(find.textContaining('权限服务暂不可用'), findsOneWidget);
    expect(gateway.createCalls, 0);
    expect(find.byKey(const ValueKey('outbound-submit-button')), findsNothing);

    await tester.tap(find.text('重试'));
    await _pumpUntilFound(tester, find.text('正常可出库'));

    expect(permissionCalls, 2);
    expect(gateway.createCalls, 1);
    expect(tester.takeException(), isNull);
  });
}

List<Override> _editableOverrides(OutboundGateway gateway) {
  return [
    outboundRepositoryProvider.overrideWithValue(gateway),
    housePermissionProvider(8).overrideWith(
      (_) async => _editablePermission,
    ),
  ];
}

const _editablePermission = HousePermission(
  perms: 'edit',
  isAdmin: false,
);

OutboundTask _largeTask({
  required int rabbitCount,
  required int rabbitsPerCage,
  String status = 'SELECTING',
}) {
  final rabbits = List.generate(rabbitCount, (index) {
    final cageIndex = index ~/ rabbitsPerCage;
    final rowIndex = cageIndex ~/ 50;
    return OutboundRabbit(
      rabbitId: index + 1,
      cageId: 10000 + cageIndex,
      cageNumber: 'C-${cageIndex + 1}',
      rowCode: 'R${(rowIndex + 1).toString().padLeft(3, '0')}',
      layerIndex: 1,
      positionIndex: cageIndex + 1,
      rabbitType: '2',
      gender: index.isEven ? '0' : '1',
      weight: 3.0,
      stage: '可出售',
      batchId: 20,
      stateVersion: 0,
      eligibility: OutboundEligibility.normal,
      reasonCode: 'ELIGIBLE',
      message: '可正常出库',
      recommendedAction: '纳入',
      defaultSelected: true,
    );
  });
  return OutboundTask(
    taskId: 'large-$rabbitCount',
    houseId: 8,
    entryType: 'HOUSE',
    status: status,
    revision: 0,
    resumed: false,
    summary: OutboundSummary(
      normal: rabbitCount,
      earlySale: 0,
      needsAction: 0,
      blocked: 0,
    ),
    rabbits: rabbits,
    selectedItems: rabbits
        .map((rabbit) => OutboundSelectedItem(
              rabbitId: rabbit.rabbitId,
              stateVersion: rabbit.stateVersion,
              selectionType: 'NORMAL',
            ))
        .toList(),
  );
}

Future<void> _pumpUntilFound(WidgetTester tester, Finder finder,
    {int maxPumps = 100}) async {
  for (var i = 0; i < maxPumps; i++) {
    await tester.pump(const Duration(milliseconds: 20));
    if (finder.evaluate().isNotEmpty) return;
  }
  fail('Expected widget was not found');
}

Future<void> _scrollConfirmUntilVisible(
  WidgetTester tester,
  Finder target,
) async {
  final scrollView = find.byKey(const ValueKey('outbound-confirm-scroll'));
  expect(scrollView, findsOneWidget);
  final scrollable = find.descendant(
    of: scrollView,
    matching: find.byWidgetPredicate(
      (widget) =>
          widget is Scrollable &&
          widget.axisDirection == AxisDirection.down &&
          widget.restorationId == null,
    ),
  );
  expect(scrollable, findsOneWidget);
  await tester.scrollUntilVisible(
    target,
    400,
    scrollable: scrollable,
  );
  await tester.pumpAndSettle();
}
