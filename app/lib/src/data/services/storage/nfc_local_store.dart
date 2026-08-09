import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';

final nfcLocalStoreProvider = Provider<NfcLocalStore>((ref) => NfcLocalStore());

class NfcLocalStore {
  static const _sessionKey = 'nfc.write.session.v1';
  static const _pendingBindingsKey = 'nfc.pending.bindings.v1';
  static const _pendingLaunchKey = 'nfc.pending.launch.v1';

  Future<NfcWriteSession?> readSession() async {
    final raw = (await SharedPreferences.getInstance()).getString(_sessionKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      return NfcWriteSession.fromJson(jsonDecode(raw));
    } on FormatException {
      return null;
    }
  }

  Future<void> saveSession(NfcWriteSession session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_sessionKey, jsonEncode(session.toJson()));
  }

  Future<void> clearSession() async {
    await (await SharedPreferences.getInstance()).remove(_sessionKey);
  }

  Future<List<NfcPendingBinding>> readPendingBindings() async {
    final raw =
        (await SharedPreferences.getInstance()).getString(_pendingBindingsKey);
    if (raw == null || raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map>()
          .map((item) => NfcPendingBinding.fromJson(
                Map<String, dynamic>.from(item),
              ))
          .toList();
    } on FormatException {
      return const [];
    }
  }

  Future<void> savePendingBindings(List<NfcPendingBinding> items) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _pendingBindingsKey,
      jsonEncode(items.map((item) => item.toJson()).toList()),
    );
  }

  Future<void> savePendingLaunch(NfcLaunchEvent event) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_pendingLaunchKey, jsonEncode(event.toJson()));
  }

  Future<NfcLaunchEvent?> takePendingLaunch() async {
    final event = await readPendingLaunch();
    await clearPendingLaunch();
    return event;
  }

  Future<NfcLaunchEvent?> readPendingLaunch() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_pendingLaunchKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      return NfcLaunchEvent.fromJson(jsonDecode(raw));
    } on FormatException {
      return null;
    }
  }

  Future<void> clearPendingLaunch() async {
    await (await SharedPreferences.getInstance()).remove(_pendingLaunchKey);
  }
}
