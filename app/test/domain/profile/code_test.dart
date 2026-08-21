import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/profile/code.dart';

void main() {
  test('accepts a code however the farmer copies it down', () {
    // 账号是靠嘴说、靠手抄传出去的，所以这几种写法必须都认。
    const written = <String>[
      'R3F9A0C21B7',
      'r3f9a0c21b7',
      '  R3F9A0C21B7  ',
      'R3F9-A0C2-1B7',
      'R3F9 A0C2 1B7',
    ];
    for (final input in written) {
      expect(UserCode.looksLikeUserCode(input), isTrue, reason: '应认出 $input');
    }
  });

  test('treats O/I/L as the digits they are mistaken for', () {
    // 十六进制里根本没有 O、I、L，所以有人写成这三个字母时，
    // 意思一定是 0、1、1，直接归一化掉，不该让用户重填。
    expect(UserCode.normalize('rO3f9aOc2lb'), 'R03F9A0C21B');
    expect(UserCode.looksLikeUserCode('rIf9a0c2lb7'), isTrue);
  });

  test('does not mistake other strings for a code', () {
    for (final input in <String>[
      'R3F9A0C21', // 少一位
      'R3F9A0C21B77', // 多一位
      'X3F9A0C21B7', // 前缀不对
      'R3F9A0C21BZ', // Z 不是十六进制
      '隔壁老王',
      '',
    ]) {
      expect(UserCode.looksLikeUserCode(input), isFalse, reason: '不该认 $input');
    }
  });

  test('still recognises plain mainland mobiles', () {
    expect(UserCode.looksLikeMobile('13800138000'), isTrue);
    expect(UserCode.looksLikeMobile('138 0013 8000'), isTrue);
    expect(UserCode.looksLikeMobile('12345'), isFalse);
    // 手机号是纯数字，不该被当成账号
    expect(UserCode.looksLikeUserCode('13800138000'), isFalse);
  });
}
