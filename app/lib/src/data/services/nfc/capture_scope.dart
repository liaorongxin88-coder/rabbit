import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 「碰一下目标笼位」期间的独占标记。
///
/// 默认情况下任何 NFC 事件都会被 `app.dart` 接管并跳到该笼位详情页——这对「贴一下看
/// 这个笼子」是对的，但对「贴一下选中换笼目标」是致命的：页面会被顶掉，正在填的表单
/// 直接消失。因此需要一个明确的独占窗口：打开采集的界面把它置为 true，自己消费事件，
/// 关闭时复位。
///
/// 刻意用最朴素的布尔标记而不是「注册回调」：同一时刻只可能有一个采集界面在前台，
/// 多一层注册表只会让「谁还开着」变得更难查。
final nfcCaptureActiveProvider = StateProvider<bool>((ref) => false);
