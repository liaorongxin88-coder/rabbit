import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/domain/models/outbound.dart';

final outboundLocalStoreProvider =
    Provider<OutboundLocalStore>((_) => OutboundLocalStore());

class OutboundLocalSnapshot {
  const OutboundLocalSnapshot({
    required this.task,
    required this.saleTime,
    required this.totalWeight,
    required this.unitPrice,
    required this.customer,
    required this.remark,
  });

  final OutboundTask task;
  final DateTime? saleTime;
  final String totalWeight;
  final String unitPrice;
  final String customer;
  final String remark;

  Map<String, dynamic> toJson() => {
        'task': task.toJson(),
        'saleTime': saleTime?.toIso8601String(),
        'totalWeight': totalWeight,
        'unitPrice': unitPrice,
        'customer': customer,
        'remark': remark,
      };

  factory OutboundLocalSnapshot.fromJson(Map<String, dynamic> json) {
    return OutboundLocalSnapshot(
      task:
          OutboundTask.fromJson(Map<String, dynamic>.from(json['task'] as Map)),
      saleTime: DateTime.tryParse(json['saleTime'] as String? ?? ''),
      totalWeight: json['totalWeight'] as String? ?? '',
      unitPrice: json['unitPrice'] as String? ?? '',
      customer: json['customer'] as String? ?? '',
      remark: json['remark'] as String? ?? '',
    );
  }
}

class OutboundLocalStore {
  static final Map<String, Future<void>> _sharedWriteQueues = {};

  String _scope(int userId, int houseId) => '$userId.$houseId';

  String _taskKey(int userId, int houseId) => 'outbound.task.$userId.$houseId';
  String _requestKey(int userId, int houseId) =>
      'outbound.request.$userId.$houseId';

  Future<void> saveSnapshot(int userId, OutboundLocalSnapshot snapshot) {
    _validateScope(userId, snapshot.task.houseId);
    return _enqueue(_scope(userId, snapshot.task.houseId), () async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_taskKey(userId, snapshot.task.houseId),
          jsonEncode(snapshot.toJson()));
    });
  }

  Future<OutboundLocalSnapshot?> readSnapshot(int userId, int houseId) async {
    _validateScope(userId, houseId);
    final scope = _scope(userId, houseId);
    await (_sharedWriteQueues[scope] ?? Future<void>.value());
    final prefs = await SharedPreferences.getInstance();
    final key = _taskKey(userId, houseId);
    final value = prefs.getString(key);
    if (value == null || value.isEmpty) return null;
    try {
      return OutboundLocalSnapshot.fromJson(
          Map<String, dynamic>.from(jsonDecode(value) as Map));
    } catch (_) {
      await _enqueue(scope, () => prefs.remove(key));
      return null;
    }
  }

  Future<void> savePendingRequest(int userId, int houseId, String requestId) {
    _validateScope(userId, houseId);
    return _enqueue(_scope(userId, houseId), () async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_requestKey(userId, houseId), requestId);
    });
  }

  Future<String?> readPendingRequest(int userId, int houseId) async {
    _validateScope(userId, houseId);
    await (_sharedWriteQueues[_scope(userId, houseId)] ?? Future<void>.value());
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_requestKey(userId, houseId));
  }

  Future<void> clearPendingRequest(int userId, int houseId) {
    _validateScope(userId, houseId);
    return _enqueue(_scope(userId, houseId), () async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_requestKey(userId, houseId));
    });
  }

  Future<void> clear(int userId, int houseId) {
    _validateScope(userId, houseId);
    return _enqueue(_scope(userId, houseId), () async {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_taskKey(userId, houseId));
      await prefs.remove(_requestKey(userId, houseId));
    });
  }

  Future<void> flush() => Future.wait(_sharedWriteQueues.values);

  Future<void> _enqueue(String scope, Future<void> Function() operation) {
    final previous = _sharedWriteQueues[scope] ?? Future<void>.value();
    final result = previous.then((_) => operation());
    _sharedWriteQueues[scope] = result.then<void>((_) {}, onError: (_) {});
    return result;
  }

  void _validateScope(int userId, int houseId) {
    if (userId <= 0 || houseId <= 0) {
      throw ArgumentError('Outbound local scope requires userId and houseId');
    }
  }
}
