import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/screens/list.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets('estrus task submits exact action with house default reminder',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: _estrusTask),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();

    expect(find.text('完成催情'), findsWidgets);
    expect(find.text('按兔场设置'), findsOneWidget);
    expect(find.text('自定义日期'), findsOneWidget);
    await _expectReminderLabel(
      tester,
      key: const ValueKey('next-reminder-stage-label'),
      expected: '配种提醒日期',
    );

    await tester.tap(
      find.byKey(const ValueKey('production-event-submit')),
    );
    await tester.pumpAndSettle();

    expect(harness.adapter.requests, hasLength(1));
    final body = harness.adapter.requests.single;
    expect(body['action'], 'ESTRUS');
    expect(body['nextRemindAt'], isNull);
    expect(find.textContaining('下一阶段：待配种'), findsOneWidget);
    expect(find.textContaining('配种提醒：2026-02-06'), findsOneWidget);
  });

  testWidgets('custom reminder sends the suggested future date',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: _estrusTask),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();

    final custom = find.byKey(const ValueKey('next-reminder-custom'));
    await tester.ensureVisible(custom);
    await tester.tap(custom);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('next-reminder-custom-date')),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const ValueKey('production-event-submit')),
    );
    await tester.pumpAndSettle();

    final nextRemindAt = harness.adapter.requests.single['nextRemindAt'];
    expect(nextRemindAt, isA<int>());
    final today = DateTime.now();
    final todayStart = DateTime(today.year, today.month, today.day);
    expect(
      DateTime.fromMillisecondsSinceEpoch(nextRemindAt as int)
          .isBefore(todayStart),
      isFalse,
    );
  });

  testWidgets('successful breeding nodes name the next reminder stage',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);
    final cases = [
      (task: _matingTask, expected: '摸胎提醒日期'),
      (task: _palpationTask, expected: '备产提醒日期'),
      (task: _prepartumTask, expected: '分娩提醒日期'),
      (task: _deliveryTask, expected: '断奶提醒日期'),
    ];

    for (final testCase in cases) {
      await tester.pumpWidget(
        _productionApp(repository: harness.repository, task: testCase.task),
      );
      await tester.tap(find.byKey(const ValueKey('open-repro-task')));
      await tester.pumpAndSettle();
      await _expectReminderLabel(
        tester,
        key: const ValueKey('next-reminder-stage-label'),
        expected: testCase.expected,
      );
      await tester.tap(find.byIcon(Icons.close).last);
      await tester.pumpAndSettle();
    }
  });

  testWidgets('empty palpation switches the reminder to estrus',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: _palpationTask),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey('pregnancy-result-EMPTY')),
    );
    await tester.tap(find.byKey(const ValueKey('pregnancy-result-EMPTY')));
    await tester.pumpAndSettle();

    await _expectReminderLabel(
      tester,
      key: const ValueKey('next-reminder-stage-label'),
      expected: '催情提醒日期',
    );
  });

  testWidgets('weaning without batch submits with house default reminder',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: _weaningTask),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();

    expect(find.text('断奶并放入笼位'), findsWidgets);
    expect(find.textContaining('批次'), findsNothing);
    await _expectReminderLabel(
      tester,
      key: const ValueKey('weaning-next-reminder-stage-label'),
      expected: '催情提醒日期',
    );
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(harness.adapter.requests, hasLength(1));
    final body = harness.adapter.requests.single;
    expect(body['action'], 'WEANING');
    expect(body.containsKey('nextRemindAt'), isFalse);
  });

  testWidgets('weaning custom reminder sends postpartum suggestion',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: _weaningTask),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();

    final custom = find.byKey(
      const ValueKey('weaning-next-reminder-custom'),
    );
    await tester.ensureVisible(custom);
    await tester.tap(custom);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('weaning-next-reminder-custom-date')),
      findsOneWidget,
    );

    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    final nextRemindAt = harness.adapter.requests.single['nextRemindAt'];
    final today = DateTime.now();
    final expected = DateTime(today.year, today.month, today.day).add(
      Duration(days: GlobalSetting.defaults().postpartumDays),
    );
    expect(
      DateTime.fromMillisecondsSinceEpoch(nextRemindAt as int),
      expected,
    );
  });

  testWidgets('future task due time is not submitted as occurredAt',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);
    final futureDueTime = DateTime.now().add(const Duration(days: 7));
    final task = ReproTask(
      id: 803,
      taskType: 'ESTRUS',
      taskLabel: '待催情',
      action: ReproAction.estrus,
      cycleId: 701,
      rabbitId: 31,
      dueTime: futureDueTime,
      status: 'PENDING',
    );

    await tester.pumpWidget(
      _productionApp(repository: harness.repository, task: task),
    );
    await tester.tap(find.byKey(const ValueKey('open-repro-task')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('production-event-submit')),
    );
    await tester.pumpAndSettle();

    final occurredAt = DateTime.fromMillisecondsSinceEpoch(
      harness.adapter.requests.single['occurredAt'] as int,
    );
    final today = DateTime.now();
    expect(
      DateTime(occurredAt.year, occurredAt.month, occurredAt.day),
      DateTime(today.year, today.month, today.day),
    );
    expect(
      DateTime(occurredAt.year, occurredAt.month, occurredAt.day),
      isNot(
          DateTime(futureDueTime.year, futureDueTime.month, futureDueTime.day)),
    );
  });

  testWidgets('weaning result refreshes detail and prefers follow-up cycle',
      (tester) async {
    final harness = _RepositoryHarness(
      response: {
        'cycleId': 701,
        'stage': 'AWAIT_ESTRUS',
        'lifecycle': 'CLOSED',
        'followUpCycleId': 888,
      },
    );
    addTearDown(harness.dispose);
    var taskLoads = 0;

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _weaningDoe,
        loadTasks: () {
          taskLoads += 1;
          return taskLoads == 1 ? [_weaningTask] : const <ReproTask>[];
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('rabbit-repro-task-action-804')),
    );
    await tester.pumpAndSettle();
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(taskLoads, greaterThanOrEqualTo(2));
    expect(find.text('#888'), findsOneWidget);
    expect(find.text('待催情'), findsWidgets);
    expect(
      find.byKey(const ValueKey('rabbit-repro-task-action-804')),
      findsNothing,
    );
  });

  testWidgets('authoritative current cycle wins over follow-up cycle',
      (tester) async {
    final harness = _RepositoryHarness(
      response: {
        'cycleId': 701,
        'currentCycleId': 999,
        'stage': 'AWAIT_MATING',
        'lifecycle': 'CLOSED',
        'followUpCycleId': 888,
      },
    );
    addTearDown(harness.dispose);
    var taskLoads = 0;

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _weaningDoe,
        loadTasks: () {
          taskLoads += 1;
          return taskLoads == 1 ? [_weaningTask] : const <ReproTask>[];
        },
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('rabbit-repro-task-action-804')),
    );
    await tester.pumpAndSettle();
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(find.text('#999'), findsOneWidget);
    expect(find.text('待配种'), findsWidgets);
  });

  testWidgets('closed current task without follow-up clears active cycle',
      (tester) async {
    final harness = _RepositoryHarness(
      response: {
        'cycleId': 701,
        'stage': 'AWAIT_ESTRUS',
        'lifecycle': 'CLOSED',
      },
    );
    addTearDown(harness.dispose);
    var taskLoads = 0;

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _weaningDoe,
        loadTasks: () {
          taskLoads += 1;
          return taskLoads == 1 ? [_weaningTask] : const <ReproTask>[];
        },
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('rabbit-repro-task-action-804')),
    );
    await tester.pumpAndSettle();
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(find.text('暂无'), findsWidgets);
    expect(
      find.byKey(const ValueKey('rabbit-repro-entry-31')),
      findsOneWidget,
    );
  });

  testWidgets('closed old task preserves a different active cycle',
      (tester) async {
    final harness = _RepositoryHarness(
      response: {
        'cycleId': 701,
        'stage': 'AWAIT_ESTRUS',
        'lifecycle': 'CLOSED',
      },
    );
    addTearDown(harness.dispose);
    var taskLoads = 0;

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _concurrentWeaningDoe,
        loadTasks: () {
          taskLoads += 1;
          return taskLoads == 1 ? [_weaningTask] : const <ReproTask>[];
        },
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('rabbit-repro-task-action-804')),
    );
    await tester.pumpAndSettle();
    final submit = find.byKey(const ValueKey('weaning-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(find.text('#999'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-repro-entry-31')),
      findsNothing,
    );
  });

  testWidgets(
      'postpartum ready doe starts the next estrus cycle before weaning',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);
    const membershipRequest = RabbitBatchMembershipRequest(
      houseId: 8,
      rabbitId: 31,
    );
    const taskRequest = RabbitReproTasksRequest(
      houseId: 8,
      rabbitId: 31,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          reproRepositoryProvider.overrideWithValue(harness.repository),
          rabbitBatchMembershipsProvider(membershipRequest).overrideWith(
            (_) async => const <RabbitBatchMembership>[],
          ),
          rabbitReproTasksProvider(taskRequest).overrideWith(
            (_) async => [_weaningTask],
          ),
          reproEntryPointsProvider.overrideWith(
            (ref, houseId) async => const [
              ReproEntryPoint(
                stage: 'AWAIT_ESTRUS',
                stageLabel: '待催情',
                requiredFacts: [
                  ReproRequiredFact(
                    fact: 'STAGE_ENTERED_AT',
                    label: '进入该阶段的日期',
                  ),
                ],
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const Scaffold(
            body: RabbitDetailSheet(
              houseId: 8,
              rabbit: _readyDoe,
              cageDisplay: 'D-01',
              canEdit: true,
              pageMode: true,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final entry = find.byKey(const ValueKey('rabbit-repro-entry-31'));
    await tester.ensureVisible(entry);
    expect(find.text('开始下一轮待催情'), findsOneWidget);
    expect(find.text('待断奶'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-repro-kept-kits-31')),
      findsOneWidget,
    );
    await tester.tap(entry);
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('existing-rabbit-repro-submit')),
    );
    await tester.pumpAndSettle();

    expect(harness.adapter.requests, hasLength(1));
    expect(harness.adapter.requests.single['stage'], 'AWAIT_ESTRUS');
    expect(harness.adapter.requests.single['motherRabbitId'], 31);
    expect(tester.takeException(), isNull);
  });

  testWidgets('existing doe without batch can open a breeding cycle',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);
    const membershipRequest = RabbitBatchMembershipRequest(
      houseId: 8,
      rabbitId: 31,
    );
    const taskRequest = RabbitReproTasksRequest(
      houseId: 8,
      rabbitId: 31,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          reproRepositoryProvider.overrideWithValue(harness.repository),
          rabbitBatchMembershipsProvider(membershipRequest).overrideWith(
            (_) async => const <RabbitBatchMembership>[],
          ),
          rabbitReproTasksProvider(taskRequest).overrideWith(
            (_) async => const <ReproTask>[],
          ),
          reproEntryPointsProvider.overrideWith(
            (ref, houseId) async => const [
              ReproEntryPoint(
                stage: 'AWAIT_ESTRUS',
                stageLabel: '待催情',
                requiredFacts: [
                  ReproRequiredFact(
                    fact: 'STAGE_ENTERED_AT',
                    label: '进入该阶段的日期',
                  ),
                ],
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const Scaffold(
            body: RabbitDetailSheet(
              houseId: 8,
              rabbit: _idleDoe,
              cageDisplay: 'D-01',
              canEdit: true,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final entry = find.byKey(const ValueKey('rabbit-repro-entry-31'));
    await tester.ensureVisible(entry);
    await tester.tap(entry);
    await tester.pumpAndSettle();

    final stage = find.byKey(
      const ValueKey('existing-rabbit-repro-stage'),
    );
    await tester.tap(stage);
    await tester.pumpAndSettle();
    await tester.tap(find.text('待催情').last);
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('existing-rabbit-repro-submit')),
    );
    await tester.pumpAndSettle();

    expect(harness.adapter.requests, hasLength(1));
    final body = harness.adapter.requests.single;
    expect(body['motherRabbitId'], 31);
    expect(body['stage'], 'AWAIT_ESTRUS');
    expect(body.containsKey('batchId'), isFalse);
    expect(find.textContaining('已从【待催情】入轨'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('doe detail exposes direct actions without a batch entry',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _pregnantDoe,
        loadTasks: () => [_palpationTask],
        stageActions: const {
          'AWAIT_PALPATION': ['PALPATION', 'ABORTION', 'POSTPONE', 'RETIRE'],
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-repro-task-action-805')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-repro-abortion-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-departure-31')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('view-only doe detail hides direct write actions',
      (tester) async {
    final harness = _RepositoryHarness();
    addTearDown(harness.dispose);

    await tester.pumpWidget(
      _rabbitDetailApp(
        repository: harness.repository,
        initialRabbit: _pregnantDoe,
        loadTasks: () => [_palpationTask],
        canEdit: false,
        stageActions: const {
          'AWAIT_PALPATION': ['PALPATION', 'ABORTION', 'POSTPONE', 'RETIRE'],
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-repro-task-action-805')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-repro-abortion-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-departure-31')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}

Future<void> _expectReminderLabel(
  WidgetTester tester, {
  required Key key,
  required String expected,
}) async {
  final label = find.byKey(key);
  final scrollable = find.byType(ListView).last;
  for (var attempt = 0; attempt < 10 && label.evaluate().isEmpty; attempt++) {
    await tester.drag(scrollable, const Offset(0, -180));
    await tester.pumpAndSettle();
  }
  expect(label, findsOneWidget);
  expect(tester.widget<Text>(label).data, expected);
}

Widget _rabbitDetailApp({
  required ReproRepository repository,
  required Rabbit initialRabbit,
  required List<ReproTask> Function() loadTasks,
  bool canEdit = true,
  Map<String, List<String>> stageActions = const {},
}) {
  const membershipRequest = RabbitBatchMembershipRequest(
    houseId: 8,
    rabbitId: 31,
  );
  const taskRequest = RabbitReproTasksRequest(
    houseId: 8,
    rabbitId: 31,
  );
  return ProviderScope(
    overrides: [
      reproRepositoryProvider.overrideWithValue(repository),
      rabbitBatchMembershipsProvider(membershipRequest).overrideWith(
        (_) async => const <RabbitBatchMembership>[],
      ),
      rabbitReproTasksProvider(taskRequest).overrideWith(
        (_) async => loadTasks(),
      ),
      reproStageActionsProvider(8).overrideWith((_) async => stageActions),
      houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
      houseSettingProvider(8).overrideWith(
        (_) async => HouseSettingState(
          setting: GlobalSetting.defaults(),
          customized: false,
        ),
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: RabbitDetailSheet(
          houseId: 8,
          rabbit: initialRabbit,
          cageDisplay: 'D-01',
          canEdit: canEdit,
        ),
      ),
    ),
  );
}

Widget _productionApp({
  required ReproRepository repository,
  required ReproTask task,
}) {
  return ProviderScope(
    overrides: [
      reproRepositoryProvider.overrideWithValue(repository),
      allActiveHouseRabbitsProvider(8).overrideWith(
        (_) async => const <Rabbit>[],
      ),
      houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
      houseSettingProvider(8).overrideWith(
        (_) async => HouseSettingState(
          setting: GlobalSetting.defaults(),
          customized: false,
        ),
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: FilledButton(
              key: const ValueKey('open-repro-task'),
              onPressed: () => showReproTaskActionSheet(
                context: context,
                houseId: 8,
                task: task,
              ),
              child: const Text('打开待办'),
            ),
          ),
        ),
      ),
    ),
  );
}

class _RepositoryHarness {
  _RepositoryHarness({Map<String, dynamic>? response})
      : adapter = _ReproActionAdapter(response: response) {
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    client = ApiClient(SessionStore(), dio: dio);
    repository = ReproRepository(client);
  }

  final _ReproActionAdapter adapter;
  late final ApiClient client;
  late final ReproRepository repository;

  void dispose() => client.dispose();
}

class _ReproActionAdapter implements HttpClientAdapter {
  _ReproActionAdapter({Map<String, dynamic>? response})
      : response = response ??
            {
              'cycleId': 701,
              'stage': 'AWAIT_MATING',
              'nextTaskId': 902,
              'nextDueTime': DateTime(2026, 2, 6).millisecondsSinceEpoch,
            };

  final Map<String, dynamic> response;
  final requests = <Map<String, dynamic>>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(Map<String, dynamic>.from(options.data as Map));
    return _json(response);
  }

  static ResponseBody _json(Object? data) {
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

final _weaningTask = ReproTask(
  id: 804,
  taskType: 'WEANING',
  taskLabel: '待断奶',
  action: ReproAction.weaning,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 10),
  status: 'PENDING',
);

final _estrusTask = ReproTask(
  id: 801,
  taskType: 'ESTRUS',
  taskLabel: '待催情',
  action: ReproAction.estrus,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 3),
  status: 'PENDING',
);

final _matingTask = ReproTask(
  id: 802,
  taskType: 'MATING',
  taskLabel: '待配种',
  action: ReproAction.mating,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 4),
  status: 'PENDING',
);

final _palpationTask = ReproTask(
  id: 805,
  taskType: 'PALPATION',
  taskLabel: '待摸胎',
  action: ReproAction.palpation,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 5),
  status: 'PENDING',
);

final _prepartumTask = ReproTask(
  id: 806,
  taskType: 'PREPARTUM',
  taskLabel: '待备产',
  action: ReproAction.prepartum,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 6),
  status: 'PENDING',
);

final _deliveryTask = ReproTask(
  id: 807,
  taskType: 'DELIVERY',
  taskLabel: '待分娩',
  action: ReproAction.delivery,
  cycleId: 701,
  rabbitId: 31,
  dueTime: DateTime(2026, 2, 7),
  status: 'PENDING',
);

const _pregnantDoe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
  currentStage: 'AWAIT_PALPATION',
  currentCycleId: 701,
);

const _concurrentWeaningDoe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
  currentStage: 'AWAIT_MATING',
  currentCycleId: 999,
);

const _weaningDoe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
  currentStage: 'AWAIT_WEANING',
  currentCycleId: 701,
);

const _readyDoe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
  currentStage: 'READY',
);

const _idleDoe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
);
