import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/abnormal.dart';

void main() {
  testWidgets(
      'business failure keeps abnormal text and image with the same requestId',
      (tester) async {
    final repository = _FakeRabbitRepository();
    var pickCount = 0;

    await tester.pumpWidget(
      ProviderScope(
        overrides: [rabbitRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                key: const ValueKey('open-abnormal-sheet'),
                onPressed: () => showRabbitAbnormalSheet(
                  context: context,
                  houseId: 8,
                  rabbit: _rabbit,
                  pickImage: (source) async {
                    pickCount += 1;
                    expect(source, ImageSource.gallery);
                    return XFile('/tmp/abnormal.jpg', name: 'abnormal.jpg');
                  },
                ),
                child: const Text('打开异常记录'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-abnormal-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('rabbit-abnormal-add-image')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('从相册选择'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('rabbit-abnormal-remark')),
      '发现左后腿外伤',
    );
    await tester.tap(
      find.byKey(const ValueKey('rabbit-abnormal-submit')),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('rabbit-abnormal-error')), findsOneWidget);
    expect(find.text('兔只已离场或换笼，请刷新后重试'), findsOneWidget);
    expect(find.text('abnormal.jpg'), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(
            find.byKey(const ValueKey('rabbit-abnormal-remark')),
          )
          .controller!
          .text,
      '发现左后腿外伤',
    );
    expect(pickCount, 1);
    expect(repository.calls, hasLength(1));

    await tester.tap(
      find.byKey(const ValueKey('rabbit-abnormal-submit')),
    );
    await tester.pumpAndSettle();

    expect(repository.calls, hasLength(2));
    expect(repository.uploadCount, 1);
    expect(repository.calls[0].requestId, repository.calls[1].requestId);
    expect(repository.calls[0].imageFileId, repository.calls[1].imageFileId);
    expect(repository.calls[0].rabbitId, 801);
    expect(repository.calls[1].rabbitId, 801);
    expect(repository.calls[0].warningStatus, '外伤');
    expect(repository.calls[0].remark, '发现左后腿外伤');
    expect(pickCount, 1);
    expect(find.text('新增异常记录'), findsNothing);
  });
}

class _FakeRabbitRepository extends RabbitRepository {
  _FakeRabbitRepository()
      : super(
          ApiClient(
            SessionStore(),
            dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test')),
          ),
        );

  final calls = <_AbnormalCall>[];
  var _uploadCount = 0;

  int get uploadCount => _uploadCount;

  @override
  Future<String> uploadImage({
    required int houseId,
    required String filePath,
    String? fileName,
  }) async {
    _uploadCount += 1;
    return 'image-$_uploadCount';
  }

  @override
  Future<void> createAbnormalCondition({
    required int houseId,
    required int rabbitId,
    required String warningStatus,
    required String imageFileId,
    required String remark,
    required String requestId,
  }) async {
    calls.add(
      _AbnormalCall(
        rabbitId: rabbitId,
        warningStatus: warningStatus,
        imageFileId: imageFileId,
        remark: remark,
        requestId: requestId,
      ),
    );
    if (calls.length == 1) {
      throw const ApiException('兔只已离场或换笼，请刷新后重试');
    }
  }
}

class _AbnormalCall {
  const _AbnormalCall({
    required this.rabbitId,
    required this.warningStatus,
    required this.imageFileId,
    required this.remark,
    required this.requestId,
  });

  final int rabbitId;
  final String warningStatus;
  final String imageFileId;
  final String remark;
  final String requestId;
}

const _rabbit = Rabbit(
  id: 801,
  houseId: 8,
  cageId: 10,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.8,
  isActive: true,
);
