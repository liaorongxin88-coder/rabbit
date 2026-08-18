/// 账号：用户在「我的 → 账号设置」里能看到、可以报给别人的唯一标识。
///
/// 形如 `R3F9A0C21B7`。十六进制字母表里没有 O/I/L，所以口头传达时的
/// 「零还是欧」「一还是艾」可以在归一化阶段直接消掉。
///
/// 这份规则和后端 `UserCodes` 是一对，两边必须保持一致：客户端先归一化再判断，
/// 是为了在本地就把明显填错的输入拦下来，而不是发一趟请求等 400。
class UserCode {
  const UserCode._();

  static final RegExp _pattern = RegExp(r'^R[0-9A-F]{10}$');
  static final RegExp _separators = RegExp(r'[\s\-_]');
  static final RegExp _mainlandMobile = RegExp(r'^1[3-9]\d{9}$');

  /// 去掉空格连字符、转大写，并把十六进制里不存在的 O/I/L 当成 0/1/1。
  static String normalize(String raw) {
    return raw
        .trim()
        .toUpperCase()
        .replaceAll(_separators, '')
        .replaceAll('O', '0')
        .replaceAll('I', '1')
        .replaceAll('L', '1');
  }

  /// 归一化之后是不是一个账号。手机号是纯数字，不会命中。
  static bool looksLikeUserCode(String raw) => _pattern.hasMatch(normalize(raw));

  static bool looksLikeMobile(String raw) =>
      _mainlandMobile.hasMatch(raw.trim().replaceAll(_separators, ''));

  /// 邀请输入框能接受的两种形态之一。
  static bool isInvitable(String raw) =>
      looksLikeMobile(raw) || looksLikeUserCode(raw);
}
