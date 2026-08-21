import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';

void main() {
  test('requireJsonObject normalizes a dynamic map', () {
    final result = requireJsonObject(
      <Object, Object>{'id': 7, 'name': '兔舍'},
      message: '格式不正确',
    );

    expect(result, {'id': 7, 'name': '兔舍'});
    expect(result, isA<Map<String, dynamic>>());
  });

  test('requireJsonObjectList keeps maps and ignores unrelated values', () {
    final result = requireJsonObjectList(
      <Object?>[
        <Object, Object>{'id': 1},
        'invalid item',
        null,
        <String, Object>{'id': 2},
      ],
      message: '列表格式不正确',
    );

    expect(result, [
      {'id': 1},
      {'id': 2},
    ]);
  });

  test('response decoders retain the repository error message', () {
    expect(
      () => requireJsonObject([], message: '详情格式不正确'),
      throwsA(
        isA<ApiException>().having(
          (error) => error.message,
          'message',
          '详情格式不正确',
        ),
      ),
    );
    expect(
      () => requireJsonObjectList({}, message: '列表格式不正确'),
      throwsA(
        isA<ApiException>().having(
          (error) => error.message,
          'message',
          '列表格式不正确',
        ),
      ),
    );
  });
}
