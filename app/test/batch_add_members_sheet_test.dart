import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/add_batch_members_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets('filters taggable rabbits, searches and multi-selects',
      (tester) async {
    final repository = _RecordingBatchRepository();
    addTearDown(repository.dispose);

    await tester.pumpWidget(
      _testApp(
        overrides: [
          batchRepositoryProvider.overrideWithValue(repository),
          allActiveHouseRabbitsProvider(8).overrideWith(
            (_) async => [
              _rabbit(101, type: '0'),
              _rabbit(102, type: '1'),
              _rabbit(103, gender: '1'),
              _rabbit(104, type: '2'),
              _rabbit(105, isActive: false),
              _rabbit(106, type: '0'),
            ],
          ),
        ],
        onOpen: (context) => showAddBatchMembersSheet(
          context: context,
          houseId: 8,
          batchId: 11,
          currentMemberIds: const {101},
        ),
      ),
    );

    await _openSheet(tester);

    expect(find.byKey(const ValueKey('batch-add-member-option-101')),
        findsNothing);
    expect(find.byKey(const ValueKey('batch-add-member-option-102')),
        findsOneWidget);
    expect(find.byKey(const ValueKey('batch-add-member-option-103')),
        findsNothing);
    expect(find.byKey(const ValueKey('batch-add-member-option-104')),
        findsOneWidget);
    expect(find.byKey(const ValueKey('batch-add-member-option-105')),
        findsNothing);
    expect(find.byKey(const ValueKey('batch-add-member-option-106')),
        findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('batch-add-member-option-102')),
    );
    await tester.tap(
      find.byKey(const ValueKey('batch-add-member-option-104')),
    );
    await tester.tap(
      find.byKey(const ValueKey('batch-add-member-option-106')),
    );
    await tester.pump();
    expect(find.text('为 3 只兔添加该批次标签'), findsOneWidget);

    final search = find.byKey(const ValueKey('batch-add-members-search'));
    await tester.enterText(search, '106');
    await tester.pump();
    expect(find.byKey(const ValueKey('batch-add-member-option-102')),
        findsNothing);
    expect(find.byKey(const ValueKey('batch-add-member-option-106')),
        findsOneWidget);
    expect(find.text('结果 1 / 3 只'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('batch-add-members-clear-search')),
    );
    await tester.pump();
    await tester.tap(
      find.byKey(const ValueKey('batch-add-members-submit')),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump(const Duration(milliseconds: 350));

    expect(repository.requests, hasLength(1));
    expect(repository.requests.single['rabbitIds'], [102, 104, 106]);
    expect(
      find.byKey(const ValueKey('batch-add-members-list')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('shows load error, retries and renders the empty state',
      (tester) async {
    final firstAttempt = Completer<List<Rabbit>>();
    final retryAttempt = Completer<List<Rabbit>>();
    var attempts = 0;

    await tester.pumpWidget(
      _testApp(
        overrides: [
          allActiveHouseRabbitsProvider(8).overrideWith((_) {
            attempts += 1;
            return attempts == 1 ? firstAttempt.future : retryAttempt.future;
          }),
        ],
        onOpen: (context) => showAddBatchMembersSheet(
          context: context,
          houseId: 8,
          batchId: 11,
          currentMemberIds: const {},
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-add-batch-members')));
    await tester.pump();
    expect(find.byKey(const ValueKey('batch-sheet-loading')), findsOneWidget);

    firstAttempt.completeError(StateError('technical rabbit payload'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('batch-sheet-error')), findsOneWidget);
    expect(find.text('无法加载兔只信息，请检查网络后重试。'), findsOneWidget);
    expect(find.textContaining('technical rabbit payload'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('batch-sheet-error-retry')));
    await tester.pump();
    expect(attempts, 2);
    expect(find.byKey(const ValueKey('batch-sheet-loading')), findsOneWidget);

    retryAttempt.complete(const []);
    await tester.pumpAndSettle();
    expect(find.text('暂无可添加的兔只'), findsOneWidget);
    expect(find.text('当前兔舍没有符合条件的在栏兔只，或它们已绑定本批次。'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('batch-add-members-submit')),
          )
          .onPressed,
      isNull,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('retries a failed write with the same draft requestId',
      (tester) async {
    final repository = _RecordingBatchRepository(failuresBeforeSuccess: 1);
    addTearDown(repository.dispose);

    await tester.pumpWidget(
      _testApp(
        overrides: [
          batchRepositoryProvider.overrideWithValue(repository),
          allActiveHouseRabbitsProvider(8).overrideWith(
            (_) async => [_rabbit(202)],
          ),
        ],
        onOpen: (context) => showAddBatchMembersSheet(
          context: context,
          houseId: 8,
          batchId: 11,
          currentMemberIds: const {},
        ),
      ),
    );

    await _openSheet(tester);
    final submit = find.byKey(const ValueKey('batch-add-members-submit'));
    await tester.tap(find.byKey(const ValueKey('batch-add-member-option-202')));
    await tester.pump();
    await tester.tap(submit);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    expect(repository.requests, hasLength(1));
    final retry = tester.widget<FilledButton>(submit).onPressed;
    expect(retry, isNotNull);

    retry!();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();

    expect(repository.requests, hasLength(2));
    expect(repository.requests[0]['requestId'], isNotEmpty);
    expect(
      repository.requests[1]['requestId'],
      repository.requests[0]['requestId'],
    );
    expect(repository.requests[0]['rabbitIds'], [202]);
    expect(
      find.byKey(const ValueKey('batch-add-members-list')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}

Future<void> _openSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('open-add-batch-members')));
  await tester.pumpAndSettle();
}

Widget _testApp({
  required List<Override> overrides,
  required Future<bool> Function(BuildContext context) onOpen,
}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: ElevatedButton(
              key: const ValueKey('open-add-batch-members'),
              onPressed: () => onOpen(context),
              child: const Text('添加批次标签'),
            ),
          ),
        ),
      ),
    ),
  );
}

Rabbit _rabbit(
  int id, {
  String type = '0',
  String gender = '0',
  bool isActive = true,
}) {
  return Rabbit(
    id: id,
    houseId: 8,
    cageId: id,
    motherId: null,
    type: type,
    gender: gender,
    breed: '新西兰白',
    arrivalMethod: '自繁',
    arrivalDate: DateTime(2025, 1, 1),
    weight: 4.2,
    isActive: isActive,
  );
}

class _RecordingBatchRepository extends BatchRepository {
  factory _RecordingBatchRepository({int failuresBeforeSuccess = 0}) {
    final client = ApiClient(SessionStore());
    return _RecordingBatchRepository._(
      client,
      failuresBeforeSuccess: failuresBeforeSuccess,
    );
  }

  _RecordingBatchRepository._(
    this.client, {
    required this.failuresBeforeSuccess,
  }) : super(client);

  final ApiClient client;
  final int failuresBeforeSuccess;
  final requests = <Map<String, Object?>>[];

  @override
  Future<void> addBatchRabbits({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    String? requestId,
  }) async {
    requests.add({
      'houseId': houseId,
      'batchId': batchId,
      'rabbitIds': List<int>.of(rabbitIds),
      'requestId': requestId,
    });
    if (requests.length <= failuresBeforeSuccess) {
      throw const ApiException('fixture add members failure');
    }
  }

  void dispose() => client.dispose();
}
