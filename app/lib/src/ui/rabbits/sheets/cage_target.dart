import 'package:rabbit_flutter/src/domain/cages/cage.dart';

class RabbitCageTargetValidation {
  const RabbitCageTargetValidation._({this.cage, this.message});

  const RabbitCageTargetValidation.valid(Cage cage) : this._(cage: cage);

  const RabbitCageTargetValidation.invalid(String message)
      : this._(message: message);

  final Cage? cage;
  final String? message;

  bool get isValid => cage != null && message == null;
}

RabbitCageTargetValidation validateRabbitCageTarget({
  required List<Cage> cages,
  required int houseId,
  required int cageId,
  required String rabbitType,
  bool requireEmpty = false,
}) {
  Cage? target;
  for (final cage in cages) {
    if (cage.id == cageId) {
      target = cage;
      break;
    }
  }
  if (target == null) {
    return const RabbitCageTargetValidation.invalid(
      '目标笼位已不存在，请刷新后重新选择',
    );
  }
  if (target.houseId != houseId) {
    return const RabbitCageTargetValidation.invalid(
      '目标笼位不属于当前兔舍，请重新选择',
    );
  }
  if (!target.isEnabled) {
    return const RabbitCageTargetValidation.invalid(
      '目标笼位已停用，请重新选择',
    );
  }
  if (!target.acceptsRabbitType(rabbitType)) {
    return const RabbitCageTargetValidation.invalid(
      '目标笼位用途已变更，不能接收该类型兔只',
    );
  }
  if (requireEmpty && target.rabbitCount > 0) {
    return const RabbitCageTargetValidation.invalid(
      '目标笼位已有兔只，请重新选择空闲笼位',
    );
  }
  if (!target.canAcceptRabbit(rabbitType)) {
    return RabbitCageTargetValidation.invalid(
      target.entryBlockedReason ?? '目标笼位已无可用容量，请重新选择',
    );
  }
  return RabbitCageTargetValidation.valid(target);
}

bool isReplacementCageTarget(Cage cage, int houseId) {
  return cage.houseId == houseId &&
      cage.isEnabled &&
      cage.rabbitCount == 0 &&
      (cage.status == '0' || cage.status == '2') &&
      cage.canAcceptRabbit('1');
}
