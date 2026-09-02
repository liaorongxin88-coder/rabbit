import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/entry.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

import '../../core/widgets/nfc_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets('cage intake chooses source before purchase type',
      (tester) async {
    await tester.pumpWidget(
      _sourceTestApp(
        permission: const HousePermission(
          perms: 'edit',
          isAdmin: false,
          permissions: ['rabbit:rabbits:add'],
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-rabbit-intake-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('选择兔子录入方式'), findsOneWidget);
    expect(find.text('自定义兔子录入'), findsOneWidget);
    expect(find.text('从批次中录入商品兔'), findsOneWidget);
    expect(
        find.byKey(const ValueKey('rabbit-intake-purchase')), findsOneWidget);
    final production = tester.widget<OutlinedButton>(
      find.byKey(const ValueKey('rabbit-intake-production')),
    );
    expect(production.onPressed, isNull);

    await tester.tap(find.byKey(const ValueKey('rabbit-intake-purchase')));
    await tester.pumpAndSettle();
    expect(find.text('请选择录入兔子类型'), findsOneWidget);
  });

  testWidgets('production source requires query and edit permissions',
      (tester) async {
    await tester.pumpWidget(
      _sourceTestApp(
        permission: const HousePermission(
          perms: 'edit',
          isAdmin: false,
          permissions: [
            'rabbit:rabbits:add',
            'rabbit:batches:query',
            'rabbit:batches:edit',
          ],
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-rabbit-intake-sheet')));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    final production = tester.widget<OutlinedButton>(
      find.byKey(const ValueKey('rabbit-intake-production')),
    );
    expect(production.onPressed, isNotNull);
  });

  testWidgets('purchase creation always sends arrivalMethod zero',
      (tester) async {
    final adapter = _CapturingAdapter();
    await tester.pumpWidget(
      _commodityEntryTestApp(repository: _repository(adapter)),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.text('从批次中录入商品兔'), findsNothing);
    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    expect(adapter.requests.single.body['arrivalMethod'], '0');
    expect(adapter.requests.single.body.containsKey('batchId'), isFalse);
  });

  testWidgets('commodity batch creation sends quantity and total weight',
      (tester) async {
    final adapter = _CapturingAdapter();
    await tester.pumpWidget(
      _commodityEntryTestApp(repository: _repository(adapter)),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final seller = find.byKey(const ValueKey('rabbit-entry-source-seller'));
    await tester.ensureVisible(seller);
    await tester.enterText(seller, '测试供应方');
    final quantity = find.byKey(const ValueKey('rabbit-entry-quantity'));
    await tester.ensureVisible(quantity);
    await tester.enterText(quantity, '3');
    await tester.pumpAndSettle();
    final totalWeight = find.byKey(const ValueKey('rabbit-entry-weight'));
    await tester.ensureVisible(totalWeight);
    await tester.enterText(totalWeight, '7.5');

    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    final request = adapter.requests.single;
    expect(request.path, '/api/rabbits/batch-entry');
    expect(request.body['quantity'], 3);
    expect(request.body['totalWeight'], 7.5);
    expect(request.body['sourceSeller'], '测试供应方');
    expect(request.body.containsKey('growthStage'), isFalse);
    expect(request.body.containsKey('growthStageEnteredAt'), isFalse);
  });

  testWidgets('commodity batch entry shows partial result in the form',
      (tester) async {
    final adapter = _CapturingAdapter(
      responseData: {
        'requestedRabbitCount': 3,
        'enteredRabbitCount': 2,
        'replayedRabbitCount': 0,
        'skippedCages': [
          {
            'cageId': 13,
            'cageNumber': 'C-01',
            'rabbitCount': 1,
            'reason': '商品兔笼剩余容量不足',
          },
        ],
      },
    );
    await tester.pumpWidget(
      _commodityEntryTestApp(repository: _repository(adapter)),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final quantity = find.byKey(const ValueKey('rabbit-entry-quantity'));
    await tester.ensureVisible(quantity);
    await tester.enterText(quantity, '3');
    await tester.pumpAndSettle();
    final totalWeight = find.byKey(const ValueKey('rabbit-entry-weight'));
    await tester.ensureVisible(totalWeight);
    await tester.enterText(totalWeight, '7.5');
    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    expect(
      find.byKey(const ValueKey('rabbit-entry-batch-result')),
      findsOneWidget,
    );
    expect(find.textContaining('C-01：1只未录入'), findsOneWidget);
    expect(find.text('关闭'), findsOneWidget);
  });

  testWidgets('self retained entry sends the manually entered mother ID',
      (tester) async {
    final adapter = _CapturingAdapter();
    await tester.pumpWidget(
      _entryTestAppWithOverrides(
        repository: _repository(adapter),
        batches: const <Batch>[],
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final source = find.byKey(const ValueKey('rabbit-entry-source-method'));
    await tester.ensureVisible(source);
    await tester.tap(source);
    await tester.pumpAndSettle();
    await tester.tap(find.text('自留').last);
    await tester.pumpAndSettle();

    final mother = find.byKey(const ValueKey('rabbit-entry-source-mother'));
    await tester.ensureVisible(mother);
    await tester.enterText(mother, '18');
    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    expect(adapter.requests.single.body['arrivalMethod'], '1');
    expect(adapter.requests.single.body['motherId'], 18);
  });

  testWidgets(
    'breeding intake stages follow sex and keep actions above dynamic keyboards',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 800);
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      await tester.pumpWidget(_entryTestApp());
      await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确定'));
      await tester.pumpAndSettle();

      final reproductiveStage = find.byKey(
        const ValueKey('rabbit-reproductive-stage'),
      );
      final reproEntryStage = find.byKey(const ValueKey('rabbit-repro-stage'));
      expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsNothing);
      // 种母兔不再提供旧的繁殖阶段下拉：后端已拒收手录值，
      // 她们走服务端下发的生产阶段入轨。
      expect(reproductiveStage, findsNothing);
      expect(reproEntryStage, findsOneWidget);

      // 从【待分笼】入轨使用进入阶段日作为分娩日，并补录完整仔数。
      await tester.ensureVisible(reproEntryStage);
      await tester.pumpAndSettle();
      await tester.tap(reproEntryStage);
      await tester.pumpAndSettle();
      await tester.tap(find.text('待分笼').last);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('rabbit-stage-entered-at')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('rabbit-birth-date')), findsNothing);
      expect(find.byKey(const ValueKey('rabbit-total-kits')), findsOneWidget);
      expect(find.byKey(const ValueKey('rabbit-live-kits')), findsOneWidget);
      expect(find.byKey(const ValueKey('rabbit-kept-kits')), findsOneWidget);
      expect(find.byKey(const ValueKey('rabbit-mating-date')), findsNothing);

      final male = find.text('公');
      await tester.ensureVisible(male);
      await tester.pumpAndSettle();
      await tester.tap(male);
      await tester.pumpAndSettle();
      // 种公兔仍然是旧的两选一，它不进生产周期。
      expect(reproEntryStage, findsNothing);
      expect(reproductiveStage, findsOneWidget);
      await tester.ensureVisible(reproductiveStage);
      await tester.pumpAndSettle();
      await tester.tap(reproductiveStage);
      await tester.pumpAndSettle();
      expect(find.text('可配'), findsOneWidget);
      expect(find.text('休整'), findsOneWidget);
      expect(find.text('妊娠'), findsNothing);
      final ready = find.text('可配');
      await tester.ensureVisible(ready);
      await tester.tap(ready);
      await tester.pumpAndSettle();

      final breed = find.byKey(const ValueKey('rabbit-entry-breed'));
      await tester.ensureVisible(breed);
      await tester.tap(breed);
      await tester.pump();
      final editableText = find.descendant(
        of: breed,
        matching: find.byType(EditableText),
      );
      expect(
        tester.widget<EditableText>(editableText).focusNode.hasFocus,
        isTrue,
      );

      final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
      for (final deviceSize in const [
        Size(360, 800),
        Size(393, 852),
        Size(412, 915),
      ]) {
        tester.view.physicalSize = deviceSize;
        for (final keyboardHeight in const [180.0, 300.0, 420.0]) {
          tester.view.viewInsets = FakeViewPadding(bottom: keyboardHeight);
          await tester.pumpAndSettle();

          expect(
            tester.takeException(),
            isNull,
            reason: '$deviceSize with keyboard $keyboardHeight',
          );
          final submitRect = tester.getRect(submit);
          expect(submitRect.top, greaterThanOrEqualTo(0));
          expect(
            submitRect.bottom,
            lessThanOrEqualTo(deviceSize.height - keyboardHeight),
          );
        }
      }
    },
  );

  // 调用方（笼位详情页）靠这个 Future 决定何时刷笼内列表。早先的写法在类型页
  // pop 后用 post-frame 回调另开表单且不等，于是刷新在兔子创建前就跑完了——
  // 真机上表现为录入完看不到新兔，得退出重进页面。
  testWidgets('entry date picker keeps the selected historical day',
      (tester) async {
    await tester.pumpWidget(_entryTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final dateField = find.byKey(const ValueKey('rabbit-arrival-date'));
    await tester.ensureVisible(dateField);
    await tester.tap(dateField);
    await tester.pumpAndSettle();

    final today = DateTime.now();
    final targetDay = today.day > 2 ? today.day - 2 : 1;
    final targetDate = DateTime(today.year, today.month, targetDay);
    await tester.tap(find.text('$targetDay').last);
    await tester.tap(find.text('确定').last);
    await tester.pumpAndSettle();

    final expected = '${targetDate.year.toString().padLeft(4, '0')}-'
        '${targetDate.month.toString().padLeft(2, '0')}-'
        '${targetDate.day.toString().padLeft(2, '0')}';
    expect(
      find.descendant(of: dateField, matching: find.text(expected)),
      findsOneWidget,
    );
  });

  testWidgets('commodity creation uses weaning date without stage inputs',
      (tester) async {
    final adapter = _CapturingAdapter();
    await tester.pumpWidget(
      _commodityEntryTestApp(repository: _repository(adapter)),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.text('断奶日期'), findsOneWidget);
    expect(
      find.text('系统根据断奶日期和兔舍生长参数自动计算成长阶段。'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsNothing);
    expect(
      find.byKey(const ValueKey('rabbit-growth-stage-entered-at')),
      findsNothing,
    );

    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    expect(adapter.requests.single.body.containsKey('arrivalDate'), isTrue);
    expect(adapter.requests.single.body.containsKey('growthStage'), isFalse);
    expect(
      adapter.requests.single.body.containsKey('growthStageEnteredAt'),
      isFalse,
    );
  });

  testWidgets('new replacement uses a separate reserve-stage date',
      (tester) async {
    await tester.pumpWidget(_replacementEntryTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.text('入场日期'), findsOneWidget);
    expect(find.text('进入后备阶段日期'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-growth-stage')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-growth-stage-entered-at')),
      findsOneWidget,
    );
  });

  testWidgets('early repro entry accepts an optional planned batch',
      (tester) async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    await tester.pumpWidget(
      _entryTestAppWithOverrides(
        repository: repository,
        batches: const [_activeBatch, _completedBatch],
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final stage = find.byKey(const ValueKey('rabbit-repro-stage'));
    await tester.ensureVisible(stage);
    await tester.tap(stage);
    await tester.pumpAndSettle();
    await tester.tap(find.text('待催情').last);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('rabbit-repro-batch')), findsOneWidget);
    expect(find.text('计划批次（可选）'), findsOneWidget);
    await _fillRequiredDoeProfile(tester);
    await tester.ensureVisible(
      find.byKey(const ValueKey('rabbit-entry-submit')),
    );
    await tester.tap(find.byKey(const ValueKey('rabbit-entry-submit')));
    await _waitForCapturedRequest(tester, adapter);

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.body['reproStage'], 'AWAIT_ESTRUS');
    expect(adapter.requests.single.body.containsKey('batchId'), isFalse);
  });

  testWidgets('repro entry selects only an active batch and sends its id',
      (tester) async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    await tester.pumpWidget(
      _entryTestAppWithOverrides(
        repository: repository,
        batches: const [_activeBatch, _completedBatch],
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final stage = find.byKey(const ValueKey('rabbit-repro-stage'));
    await tester.ensureVisible(stage);
    await tester.tap(stage);
    await tester.pumpAndSettle();
    await tester.tap(find.text('待催情').last);
    await tester.pumpAndSettle();

    final batch = find.byKey(const ValueKey('rabbit-repro-batch'));
    await tester.ensureVisible(batch);
    await tester.tap(batch);
    await tester.pumpAndSettle();
    expect(find.text(_activeBatch.batchCode), findsWidgets);
    expect(find.text(_completedBatch.batchCode), findsNothing);
    await tester.tap(find.text(_activeBatch.batchCode).last);
    await tester.pumpAndSettle();

    // Leaving and re-entering tracking must not retain the earlier batch.
    await tester.ensureVisible(stage);
    await tester.tap(stage);
    await tester.pumpAndSettle();
    await tester.tap(find.text('暂不入轨').last);
    await tester.pumpAndSettle();
    await tester.ensureVisible(stage);
    await tester.tap(stage);
    await tester.pumpAndSettle();
    await tester.tap(find.text('待催情').last);
    await tester.pumpAndSettle();

    expect(adapter.requests, isEmpty);

    await tester.ensureVisible(batch);
    await tester.tap(batch);
    await tester.pumpAndSettle();
    await tester.tap(find.text(_activeBatch.batchCode).last);
    await tester.pumpAndSettle();

    await _fillRequiredDoeProfile(tester);
    final submitAfterReselection =
        find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submitAfterReselection);
    await tester.tap(submitAfterReselection);
    await _waitForCapturedRequest(tester, adapter);

    final body = adapter.requests.single.body;
    expect(adapter.requests, hasLength(1));
    expect(body['reproStage'], 'AWAIT_ESTRUS');
    expect(body['batchId'], _activeBatch.id);
    expect(
        body['requestId'],
        isA<String>().having(
          (value) => value.isNotEmpty,
          'non-empty',
          true,
        ));
  });

  testWidgets('no batch is valid when create stays outside repro tracking',
      (tester) async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    await tester.pumpWidget(
      _entryTestAppWithOverrides(
        repository: repository,
        batches: const <Batch>[],
      ),
    );
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await _waitForCapturedRequest(tester, adapter);

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.body.containsKey('reproStage'), isFalse);
    expect(adapter.requests.single.body.containsKey('batchId'), isFalse);
  });

  testWidgets('entry flow future completes only after the form closes',
      (tester) async {
    var finished = false;
    await tester
        .pumpWidget(_entryTestApp(onFlowFinished: () => finished = true));
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('rabbit-entry-submit')), findsOneWidget);
    expect(finished, isFalse, reason: '表单还开着时不得当作流程结束');

    await tester.tap(find.text('取消').last);
    await tester.pumpAndSettle();
    expect(finished, isTrue, reason: '表单关闭后调用方才能刷列表');
  });

  testWidgets('create rechecks capacity and house ownership before request',
      (tester) async {
    final cases = <({Cage cage, String message})>[
      (
        cage: _commodityCage.copyWith(rabbitCount: Cage.commodityCapacity),
        message: '该商品兔笼已满（最多 ${Cage.commodityCapacity} 只）',
      ),
      (
        cage: _commodityCage.copyWith(houseId: 9),
        message: '目标笼位不属于当前兔舍，请重新选择',
      ),
    ];

    for (final testCase in cases) {
      final adapter = _CapturingAdapter();
      final repository = _repository(adapter);
      await tester.pumpWidget(
        _commodityEntryTestApp(
          repository: repository,
          refreshedCages: [testCase.cage],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确定'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('rabbit-entry-submit')),
      );
      await tester.pumpAndSettle();

      expect(find.text(testCase.message), findsOneWidget);
      expect(adapter.requests, isEmpty);
      await tester.tap(find.text('取消').last);
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
    }
  });

  testWidgets('historical juvenile commodity stage displays adaptation',
      (tester) async {
    await tester.pumpWidget(_historicalCommodityEditTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-edit-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('适应期'), findsOneWidget);
    expect(find.text('幼兔'), findsNothing);
  });

  testWidgets('replacement locks to reserve and commodity omits reproduction',
      (tester) async {
    await tester.pumpWidget(_replacementEditTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-edit-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('繁殖阶段：后备（后备兔固定记录为后备阶段）'), findsOneWidget);
    expect(find.text('笼位 #12（只读）'), findsOneWidget);
    expect(find.text('2025-08-23'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-reproductive-stage')),
      findsNothing,
    );
    await tester.tap(find.byTooltip('关闭'));
    await tester.pumpAndSettle();

    await tester.pumpWidget(_commodityEntryTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.text('断奶日期'), findsOneWidget);
    expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsNothing);
    expect(
      find.byKey(const ValueKey('rabbit-reproductive-stage')),
      findsNothing,
    );
    expect(find.text('繁殖阶段'), findsNothing);
  });

  testWidgets('存栏母兔入轨时碰一下公兔的笼位，配种公兔就填好了', (tester) async {
    final nfc = NfcHarness();
    await tester.pumpWidget(_reproEntryNfcTestApp());
    await _openReproEntrySheet(tester);
    await _selectStage(
      tester,
      const ValueKey('existing-rabbit-repro-stage'),
      '待摸胎',
    );

    await _startNfcCapture(tester);
    await nfc.tap(houseId: 8, cageId: 21);
    await tester.pumpAndSettle();

    expect(find.text('已选择兔 #21'), findsOneWidget);
    expect(
      _selectedMaleId(tester, const ValueKey('existing-rabbit-mating-male')),
      21,
    );
  });

  testWidgets('存栏母兔入轨时碰到没有可选公兔的笼位，会说清楚没选中', (tester) async {
    final nfc = NfcHarness();
    await tester.pumpWidget(_reproEntryNfcTestApp());
    await _openReproEntrySheet(tester);
    await _selectStage(
      tester,
      const ValueKey('existing-rabbit-repro-stage'),
      '待摸胎',
    );

    await _startNfcCapture(tester);
    // 30 号笼里只有一只种母兔，不是可选的配种公兔。
    await nfc.tap(houseId: 8, cageId: 30);
    await tester.pumpAndSettle();

    expect(find.text('该笼位没有当前可选的兔只'), findsOneWidget);
    expect(
      _selectedMaleId(tester, const ValueKey('existing-rabbit-mating-male')),
      isNull,
    );
    expect(
      find.byKey(const ValueKey('existing-rabbit-mating-male')),
      findsOneWidget,
      reason: '碰不出结果时下拉仍然留着，人还能自己选',
    );
  });

  testWidgets('录入母兔时碰一下公兔的笼位，配种公兔同样填得上', (tester) async {
    final nfc = NfcHarness();
    await tester.pumpWidget(_createRabbitNfcTestApp());
    await _openCreateRabbitSheet(tester);
    await _selectStage(tester, const ValueKey('rabbit-repro-stage'), '待摸胎');

    await _startNfcCapture(tester);
    await nfc.tap(houseId: 8, cageId: 21);
    await tester.pumpAndSettle();

    expect(find.text('已选择兔 #21'), findsOneWidget);
    expect(
      _selectedMaleId(tester, const ValueKey('rabbit-entry-mating-male')),
      21,
    );
  });

  testWidgets('录入母兔时碰到有两只可选公兔的笼位，会让人回列表里选', (tester) async {
    final nfc = NfcHarness();
    await tester.pumpWidget(_createRabbitNfcTestApp());
    await _openCreateRabbitSheet(tester);
    await _selectStage(tester, const ValueKey('rabbit-repro-stage'), '待摸胎');

    await _startNfcCapture(tester);
    // 22 号笼里住着两只可配的种公兔，猜哪一只都是错的。
    await nfc.tap(houseId: 8, cageId: 22);
    await tester.pumpAndSettle();

    expect(find.text('该笼位有 2 只可选兔只，请在列表中选择'), findsOneWidget);
    expect(
      _selectedMaleId(tester, const ValueKey('rabbit-entry-mating-male')),
      isNull,
      reason: '两只都可配时不得替人猜一只',
    );
    expect(
      find.byKey(const ValueKey('rabbit-entry-mating-male')),
      findsOneWidget,
      reason: '拒绝后下拉仍然留着，人还能自己选',
    );
  });

  testWidgets('读标签失败时表单还在，已经填好的品种、体重、来源都不会丢', (tester) async {
    final nfc = NfcHarness();
    await tester.pumpWidget(
      _createRabbitNfcTestApp(
        nfcRepository: _StubNfcRepository(
          failure: const ApiException('标签解析失败，请重试'),
        ),
      ),
    );
    await _openCreateRabbitSheet(tester);
    await _fillRequiredDoeProfile(tester);
    await _selectStage(tester, const ValueKey('rabbit-repro-stage'), '待摸胎');

    await _startNfcCapture(tester);
    await nfc.tap(houseId: 8, cageId: 21);
    await tester.pumpAndSettle();

    expect(find.text('标签解析失败，请重试'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-entry-submit')),
      findsOneWidget,
      reason: '读标签失败不能把表单关掉',
    );
    expect(find.text('新西兰白'), findsOneWidget);
    expect(find.text('3.8'), findsOneWidget);
    expect(find.text('测试供应方'), findsOneWidget);
    expect(
      _selectedMaleId(tester, const ValueKey('rabbit-entry-mating-male')),
      isNull,
    );
  });
}

Widget _reproEntryNfcTestApp({NfcRepository? nfcRepository}) {
  return _testApp(
    houseRabbits: _breedingHouseRabbits,
    nfcRepository: nfcRepository ?? _StubNfcRepository(),
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-repro-entry-sheet'),
        onPressed: () => showRabbitReproEntrySheet(
          context: context,
          houseId: 8,
          rabbit: _breedingDoe,
        ),
        child: const Text('入轨'),
      ),
    ),
  );
}

Widget _createRabbitNfcTestApp({NfcRepository? nfcRepository}) {
  return _testApp(
    refreshedCages: const [_breedingCage],
    houseRabbits: _breedingHouseRabbits,
    nfcRepository: nfcRepository ?? _StubNfcRepository(),
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitPurchaseEntrySheet(
          context: context,
          houseId: 8,
          cage: _breedingCage,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Future<void> _openReproEntrySheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('open-rabbit-repro-entry-sheet')));
  await tester.pumpAndSettle();
}

Future<void> _openCreateRabbitSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
  await tester.pumpAndSettle();
  await tester.tap(find.text('确定'));
  await tester.pumpAndSettle();
}

Future<void> _selectStage(
  WidgetTester tester,
  Key stageKey,
  String stageLabel,
) async {
  final stage = find.byKey(stageKey);
  await tester.ensureVisible(stage);
  await tester.tap(stage);
  await tester.pumpAndSettle();
  await tester.tap(find.text(stageLabel).last);
  await tester.pumpAndSettle();
}

/// 打开采集窗口并确认它真的在等标签，避免后面注入的碰一下落空。
Future<void> _startNfcCapture(WidgetTester tester) async {
  final button = find.byKey(const ValueKey('nfc-rabbit-picker-button'));
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
  expect(
    find.text('请靠近种公兔所在笼位的 NFC 标签'),
    findsWidgets,
    reason: '按钮点下去要进入等待态，否则注入的标签没人接',
  );
}

int? _selectedMaleId(WidgetTester tester, Key dropdownKey) {
  return tester
      .widget<DropdownButtonFormField<int>>(find.byKey(dropdownKey))
      .initialValue;
}

final _breedingDoe = Rabbit.fromJson({
  'id': 20,
  'houseId': 8,
  'cageId': 11,
  'type': '0',
  'gender': '0',
  'breed': '新西兰白兔',
  'arrivalMethod': '0',
  'isActive': true,
});

/// 21 号笼一只公兔、22 号笼两只公兔、30 号笼只有母兔：
/// 碰一下的三种结局各有一个笼位对应。
final _breedingHouseRabbits = <Rabbit>[
  _breedingDoe,
  Rabbit.fromJson({
    'id': 21,
    'houseId': 8,
    'cageId': 21,
    'type': '0',
    'gender': '1',
    'breed': '新西兰白兔',
    'arrivalMethod': '0',
    'isActive': true,
  }),
  Rabbit.fromJson({
    'id': 22,
    'houseId': 8,
    'cageId': 22,
    'type': '0',
    'gender': '1',
    'breed': '加利福尼亚兔',
    'arrivalMethod': '0',
    'isActive': true,
  }),
  Rabbit.fromJson({
    'id': 23,
    'houseId': 8,
    'cageId': 22,
    'type': '0',
    'gender': '1',
    'breed': '伊拉兔',
    'arrivalMethod': '0',
    'isActive': true,
  }),
  Rabbit.fromJson({
    'id': 30,
    'houseId': 8,
    'cageId': 30,
    'type': '0',
    'gender': '0',
    'breed': '新西兰白兔',
    'arrivalMethod': '0',
    'isActive': true,
  }),
];

/// 强制「有 NFC」，让组件测试能走到读标签这一步。
class _AvailableNfcHardware extends NfcHardwareService {
  @override
  Future<bool> isAvailable() async => true;
}

/// 把标签载荷里的笼位直接当成解析结果，不联网。
class _StubNfcRepository implements NfcRepository {
  _StubNfcRepository({this.failure});

  final ApiException? failure;

  @override
  Future<NfcCageBinding> resolve({
    required int houseId,
    required String tagUid,
    required String payload,
  }) async {
    final failure = this.failure;
    if (failure != null) {
      throw failure;
    }
    final target = NfcPayloadTarget.parse(payload);
    return NfcCageBinding(
      houseId: target.houseId,
      cageId: target.cageId,
      cageNumber: 'A-${target.cageId}',
      tagUid: tagUid,
      bindingStatus: 'BOUND',
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName} 未在测试中实现');
}

Widget _sourceTestApp({required HousePermission permission}) {
  return _testApp(
    refreshedCages: const [_commodityCage],
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-intake-sheet'),
        onPressed: () => showRabbitIntakeSheet(
          context: context,
          houseId: 8,
          cage: _commodityCage,
          permission: permission,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _entryTestApp({VoidCallback? onFlowFinished}) {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () async {
          await showRabbitPurchaseEntrySheet(
            context: context,
            houseId: 8,
            cage: _breedingCage,
          );
          onFlowFinished?.call();
        },
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _entryTestAppWithOverrides({
  required RabbitRepository repository,
  required List<Batch> batches,
}) {
  return _testApp(
    repository: repository,
    refreshedCages: const [_breedingCage],
    batches: batches,
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitPurchaseEntrySheet(
          context: context,
          houseId: 8,
          cage: _breedingCage,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _replacementEntryTestApp() {
  return _testApp(
    refreshedCages: const [_emptyReplacementCage],
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitPurchaseEntrySheet(
          context: context,
          houseId: 8,
          cage: _emptyReplacementCage,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _replacementEditTestApp() {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-edit-sheet'),
        onPressed: () => showRabbitEditSheet(
          context: context,
          houseId: 8,
          rabbit: _replacementRabbit,
          cages: const [_replacementCage],
        ),
        child: const Text('编辑'),
      ),
    ),
  );
}

Widget _commodityEntryTestApp({
  RabbitRepository? repository,
  List<Cage>? refreshedCages,
}) {
  return _testApp(
    repository: repository,
    refreshedCages: refreshedCages,
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitPurchaseEntrySheet(
          context: context,
          houseId: 8,
          cage: _commodityCage,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _historicalCommodityEditTestApp() {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-edit-sheet'),
        onPressed: () => showRabbitEditSheet(
          context: context,
          houseId: 8,
          rabbit: _historicalCommodityRabbit,
          cages: const [_commodityCage],
        ),
        child: const Text('编辑'),
      ),
    ),
  );
}

Widget _testApp({
  required Widget child,
  RabbitRepository? repository,
  List<Cage>? refreshedCages,
  List<Batch> batches = const [_activeBatch],
  List<Rabbit>? houseRabbits,
  NfcRepository? nfcRepository,
}) {
  return ProviderScope(
    overrides: [
      if (repository != null)
        rabbitRepositoryProvider.overrideWithValue(repository),
      if (houseRabbits != null)
        allActiveHouseRabbitsProvider.overrideWith(
          (ref, houseId) async => houseRabbits,
        ),
      if (nfcRepository != null) ...[
        nfcRepositoryProvider.overrideWithValue(nfcRepository),
        nfcHardwareServiceProvider.overrideWithValue(_AvailableNfcHardware()),
      ],
      houseCagesProvider(8).overrideWith(
        (_) async =>
            refreshedCages ??
            const [_breedingCage, _replacementCage, _commodityCage],
      ),
      houseBatchesProvider(8).overrideWith((_) async => batches),
      // 入轨字典来自服务端；组件测试里用一份与后端 EntryPoint 表一致的子集。
      reproEntryPointsProvider.overrideWith(
        (ref, houseId) async => const [
          ReproEntryPoint(
            stage: 'AWAIT_ESTRUS',
            stageLabel: '待催情',
            requiredFacts: [
              ReproRequiredFact(fact: 'STAGE_ENTERED_AT', label: '进入该阶段的日期'),
            ],
          ),
          ReproEntryPoint(
            stage: 'AWAIT_PALPATION',
            stageLabel: '待摸胎',
            batchRequired: true,
            requiredFacts: [
              ReproRequiredFact(
                fact: 'STAGE_ENTERED_AT',
                label: '进入该阶段的日期',
              ),
              ReproRequiredFact(fact: 'MALE_RABBIT', label: '配种公兔'),
              ReproRequiredFact(fact: 'MATING_METHOD', label: '配种方式'),
            ],
          ),
          ReproEntryPoint(
            stage: 'AWAIT_WEANING',
            stageLabel: '待分笼',
            batchRequired: true,
            requiredFacts: [
              ReproRequiredFact(
                fact: 'STAGE_ENTERED_AT',
                label: '进入该阶段的日期',
              ),
              ReproRequiredFact(fact: 'TOTAL_KITS', label: '产仔数'),
              ReproRequiredFact(fact: 'LIVE_KITS', label: '活仔数'),
              ReproRequiredFact(fact: 'KEPT_KITS', label: '留仔数'),
            ],
          ),
        ],
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, page) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: AppTypography.ergonomicTextScaler(
            MediaQuery.textScalerOf(context),
          ),
        ),
        child: page!,
      ),
      home: Scaffold(body: Center(child: child)),
    ),
  );
}

const _activeBatch = Batch(
  id: 61,
  houseId: 8,
  batchCode: 'BREED-61',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _completedBatch = Batch(
  id: 62,
  houseId: 8,
  batchCode: 'BREED-CLOSED',
  status: '已完成',
  startDate: null,
  endDate: null,
  remark: '',
);

const _breedingCage = Cage(
  id: 11,
  houseId: 8,
  cageNumber: 'A-01',
  status: '1',
  rabbitCount: 0,
  isEnabled: true,
);

const _replacementCage = Cage(
  id: 12,
  houseId: 8,
  cageNumber: 'B-01',
  status: '2',
  rabbitCount: 1,
  isEnabled: true,
);

const _emptyReplacementCage = Cage(
  id: 14,
  houseId: 8,
  cageNumber: 'B-02',
  status: '2',
  rabbitCount: 0,
  isEnabled: true,
);

const _commodityCage = Cage(
  id: 13,
  houseId: 8,
  cageNumber: 'C-01',
  status: '3',
  rabbitCount: 0,
  isEnabled: true,
);

final _historicalCommodityRabbit = Rabbit.fromJson({
  'id': 802,
  'houseId': 8,
  'cageId': 13,
  'type': '2',
  'gender': '0',
  'breed': '新西兰白兔',
  'arrivalMethod': '0',
  'arrivalDate': '2025-08-23',
  'weight': 2.4,
  'isActive': true,
  'growthStage': 'JUVENILE',
});

final _replacementRabbit = Rabbit(
  id: 801,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '1',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: DateTime.utc(2025, 8, 22, 16, 30),
  weight: 3.2,
  isActive: true,
  growthStage: 'GROWING',
  reproductiveStage: null,
);

Future<void> _fillRequiredDoeProfile(WidgetTester tester) async {
  final breed = find.byKey(const ValueKey('rabbit-entry-breed'));
  await tester.ensureVisible(breed);
  await tester.enterText(breed, '新西兰白');

  final weight = find.byKey(const ValueKey('rabbit-entry-weight'));
  await tester.ensureVisible(weight);
  await tester.enterText(weight, '3.8');

  final seller = find.byKey(const ValueKey('rabbit-entry-source-seller'));
  await tester.ensureVisible(seller);
  await tester.enterText(seller, '测试供应方');
  await tester.pump();
}

Future<void> _waitForCapturedRequest(
  WidgetTester tester,
  _CapturingAdapter adapter,
) async {
  for (var attempt = 0;
      attempt < 30 && adapter.requests.isEmpty;
      attempt += 1) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 10)),
    );
    await tester.pump(const Duration(milliseconds: 100));
  }
  await tester.pump(const Duration(milliseconds: 500));
  if (adapter.requests.isEmpty) {
    final visibleText = tester
        .widgetList<Text>(find.byType(Text))
        .map((widget) => widget.data)
        .whereType<String>()
        .where((text) => text.trim().isNotEmpty)
        .join(' | ');
    fail('No repository request. Visible text: $visibleText');
  }
}

RabbitRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({required this.path, required this.body});

  final String path;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  _CapturingAdapter({this.responseData});

  final Object? responseData;
  final requests = <_CapturedRequest>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(
      _CapturedRequest(
        path: options.path,
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': responseData ??
            {
              'id': 900,
              'houseId': 8,
              'cageId': 13,
              'type': '2',
              'gender': '0',
              'isActive': true,
            },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

extension on Cage {
  Cage copyWith({int? houseId, int? rabbitCount}) {
    return Cage(
      id: id,
      houseId: houseId ?? this.houseId,
      cageNumber: cageNumber,
      rowCode: rowCode,
      layerIndex: layerIndex,
      positionIndex: positionIndex,
      breedingOccupantGender: breedingOccupantGender,
      status: status,
      rabbitCount: rabbitCount ?? this.rabbitCount,
      isEnabled: isEnabled,
      isFed: isFed,
    );
  }
}
