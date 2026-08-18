/// 邀请成员的结果。
///
/// 两条通道的结局本来就不一样，界面不能都说一句「邀请已提交」了事：
/// 手机号邀请可能对着一个还没注册的人，只能挂起；账号邀请的对象一定已经存在，
/// 当场就进来了。角色也可能和请求的不同——重复邀请只抬权限、不降权限。
class HouseInvitationResult {
  const HouseInvitationResult({required this.status, required this.role});

  final String status;
  final String role;

  /// 对方已经进来了（按账号邀请）。
  bool get joined => status == 'JOINED';

  static HouseInvitationResult fromJson(Map<String, dynamic> json) {
    return HouseInvitationResult(
      status: json['status'] as String? ?? 'SUBMITTED',
      role: json['role'] as String? ?? '',
    );
  }
}
