import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';

final nfcIntentServiceProvider = Provider<NfcIntentService>((ref) {
  final service = NfcIntentService();
  ref.onDispose(service.dispose);
  return service;
});

class NfcIntentService {
  static const _channel = MethodChannel('com.rabbit.app.flutter/nfc_intents');
  final _events = StreamController<NfcLaunchEvent>.broadcast();
  var _initialized = false;

  Stream<NfcLaunchEvent> get events => _events.stream;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'nfcIntent') {
        _emit(call.arguments);
      }
    });
    try {
      _emit(await _channel.invokeMethod<Object?>('takePendingIntent'));
    } on MissingPluginException {
      // Widget tests and non-Android hosts do not expose the native NFC bridge.
    }
  }

  void _emit(Object? raw) {
    final event = NfcLaunchEvent.fromJson(raw);
    if (event != null && !_events.isClosed) {
      _events.add(event);
    }
  }

  void dispose() {
    _channel.setMethodCallHandler(null);
    _events.close();
  }
}
