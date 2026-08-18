import 'package:rabbit_flutter/src/domain/models/cage.dart';

/// 笼位地图的着色维度：**关注度**，回答「今天该去处理哪些笼」，
/// 而不是「这个笼是什么用途」——用途在格子里以文字呈现，不占颜色。
///
/// 顺序即优先级：一个笼可能同时满足多条（停用笼里还留着兔、待投喂又满笼），
/// 只呈现最需要人过去处理的那一条，否则地图会变成一堆并列徽标。
enum CageAttention {
  /// 数据自相矛盾：空笼却记着在栏、单兔笼超载、商品笼超上限、停用笼里还有兔。
  /// 这类不是「状态」而是「账不平」，必须最先看见。
  alert('异常', '账不平，需要核对'),

  /// 已停用且笼内无兔，属于正常的不可用。
  disabled('停用', '已停用，不可使用'),

  /// 有兔且今日未投喂——唯一每天都要清零的待办。
  needsFeeding('待投喂', '有兔但今日未投喂'),

  /// 放不下了：种兔/后备笼已有 1 只，或商品笼到上限。
  full('已满', '放不下了'),

  /// 还能放兔，含空笼与未满的商品笼。
  vacancy('有空位', '还能放兔');

  const CageAttention(this.label, this.hint);

  /// 图例与格子上的短标签。
  final String label;

  /// 图例里的一句话解释。
  final String hint;

  bool get isActionable =>
      this == CageAttention.alert || this == CageAttention.needsFeeding;
}

extension CageAttentionX on Cage {
  CageAttention get attention {
    if (attentionAlertReason != null) {
      return CageAttention.alert;
    }
    if (!isEnabled) {
      return CageAttention.disabled;
    }
    if (!isFed && rabbitCount > 0) {
      return CageAttention.needsFeeding;
    }
    return acceptsMoreRabbits ? CageAttention.vacancy : CageAttention.full;
  }

  /// 具体说明哪里账不平；没有异常时返回 null。
  /// 这些判断只依赖列表接口已有的字段，不额外请求——地图上要能一眼看到，
  /// 而不是点进详情才发现。
  String? get attentionAlertReason {
    if (rabbitCount < 0) {
      return '在栏数为负数（$rabbitCount）';
    }
    if (!isEnabled && rabbitCount > 0) {
      return '已停用但笼内仍有 $rabbitCount 只';
    }
    if (status == '0' && rabbitCount > 0) {
      return '标记为空闲却记着 $rabbitCount 只';
    }
    if ((status == '1' || status == '2') && rabbitCount > 1) {
      return '单兔笼记着 $rabbitCount 只';
    }
    if (status == '3' && rabbitCount > Cage.commodityCapacity) {
      return '超出商品兔笼上限（$rabbitCount / ${Cage.commodityCapacity}）';
    }
    return null;
  }

  /// 格子里的占用文字：空笼写「空」，商品笼写「3/10」，单兔笼只写只数。
  String get occupancyText {
    if (rabbitCount <= 0) {
      return '空';
    }
    if (status == '3') {
      return '$rabbitCount/${Cage.commodityCapacity}';
    }
    return '$rabbitCount 只';
  }
}
