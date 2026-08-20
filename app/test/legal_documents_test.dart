import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/config/legal.dart';

void main() {
  test('privacy policy discloses carrier authentication', () {
    const policy = LegalDocuments.privacyPolicy;

    expect(policy, contains('当登录页提供运营商本机号码一键登录入口时'));
    expect(policy, contains('未提供该入口时不会调用号码认证服务'));
    expect(policy, contains('阿里云号码认证服务'));
    expect(policy, contains('中国移动'));
    expect(policy, contains('中国联通'));
    expect(policy, contains('中国电信'));
    expect(policy, contains('网络类型'));
    expect(policy, contains('IP 地址'));
    expect(policy, contains('设备型号及设备标识'));
    expect(policy, contains('操作系统及版本'));
    expect(policy, contains('SIM 卡状态'));
    expect(policy, contains('经核验的手机号结果'));
    expect(policy, contains('仅通过 HTTPS'));
    expect(policy, contains('不在本地持久化'));
    expect(policy, contains('短信验证码或账号密码登录'));
    expect(policy, contains('撤回本次授权'));
  });
}
