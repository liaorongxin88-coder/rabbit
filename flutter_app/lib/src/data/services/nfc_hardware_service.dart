import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:nfc_manager/platform_tags.dart';

final nfcHardwareServiceProvider = Provider<NfcHardwareService>((ref) {
  return NfcHardwareService();
});

class NfcHardwareService {
  static const externalDomain = 'dzht.top';
  static const externalType = 'rabbit-cage';
  static const fullExternalType = '$externalDomain:$externalType';
  Completer<NfcWriteResult>? _activeCompleter;

  Future<bool> isAvailable() => NfcManager.instance.isAvailable();

  Future<NfcWriteResult> writePayload({
    required String payload,
    String? previousCompletedUid,
    bool allowOverwrite = false,
  }) async {
    if (!await isAvailable()) {
      throw const NfcWriteException(
        NfcWriteError.unavailable,
        '设备不支持NFC或NFC未开启',
      );
    }
    final completer = Completer<NfcWriteResult>();
    final previous = _activeCompleter;
    if (previous != null && !previous.isCompleted) {
      try {
        await NfcManager.instance.stopSession();
      } catch (_) {
        // The platform may already have stopped the previous session.
      }
      previous.completeError(const NfcWriteException(
        NfcWriteError.cancelled,
        'NFC识别已取消',
      ));
    }
    _activeCompleter = completer;
    var handling = false;

    Future<void> completeError(NfcWriteException error) async {
      try {
        await NfcManager.instance.stopSession(errorMessage: error.message);
      } finally {
        if (!completer.isCompleted) completer.completeError(error);
        if (identical(_activeCompleter, completer)) _activeCompleter = null;
      }
    }

    try {
      await NfcManager.instance.startSession(onDiscovered: (tag) async {
        if (handling || completer.isCompleted) return;
        handling = true;
        final uid = _tagUid(tag);
        try {
          if (uid.isEmpty) {
            throw const NfcWriteException(
              NfcWriteError.missingUid,
              '无法读取标签UID',
            );
          }
          if (isRepeatedNfcTag(previousCompletedUid, uid)) {
            throw NfcWriteException(
              NfcWriteError.sameTag,
              '请移开手机后触碰下一张标签',
              tagUid: uid,
            );
          }

          final message = NdefMessage([
            NdefRecord.createExternal(
              externalDomain,
              externalType,
              Uint8List.fromList(ascii.encode(payload)),
            ),
          ]);
          final ndef = Ndef.from(tag);
          if (ndef != null) {
            if (!ndef.isWritable) {
              throw NfcWriteException(
                NfcWriteError.readOnly,
                '标签已锁定，无法写入',
                tagUid: uid,
              );
            }
            if (message.byteLength > ndef.maxSize) {
              throw NfcWriteException(
                NfcWriteError.tooSmall,
                '标签容量不足，需要至少 ${message.byteLength} 字节',
                tagUid: uid,
              );
            }
            final existing = _managedPayload(ndef.cachedMessage);
            final hasForeignData = existing == null &&
                (ndef.cachedMessage?.records.isNotEmpty ?? false);
            if (!allowOverwrite && existing != null && existing != payload) {
              throw NfcWriteException(
                NfcWriteError.existingManagedPayload,
                '标签已写入其他笼位',
                tagUid: uid,
                existingPayload: existing,
              );
            }
            if (!allowOverwrite && hasForeignData) {
              throw NfcWriteException(
                NfcWriteError.foreignPayload,
                '标签包含其他数据',
                tagUid: uid,
              );
            }
            await ndef.write(message);
            final verified = await ndef.read();
            if (_managedPayload(verified) != payload) {
              throw NfcWriteException(
                NfcWriteError.verifyFailed,
                '标签回读校验失败',
                tagUid: uid,
              );
            }
          } else {
            final formatable = NdefFormatable.from(tag);
            if (formatable == null) {
              throw NfcWriteException(
                NfcWriteError.notNdef,
                '标签不支持NDEF格式',
                tagUid: uid,
              );
            }
            await formatable.format(message);
          }

          await NfcManager.instance.stopSession();
          if (!completer.isCompleted) {
            completer.complete(NfcWriteResult(tagUid: uid, payload: payload));
          }
          if (identical(_activeCompleter, completer)) _activeCompleter = null;
        } on NfcWriteException catch (error) {
          await completeError(error);
        } catch (error) {
          await completeError(NfcWriteException(
            NfcWriteError.writeFailed,
            '写入失败：$error',
            tagUid: uid,
          ));
        }
      });
    } catch (error) {
      if (identical(_activeCompleter, completer)) _activeCompleter = null;
      if (!completer.isCompleted) {
        completer.completeError(NfcWriteException(
          NfcWriteError.writeFailed,
          '无法启动NFC识别：$error',
        ));
      }
    }
    return completer.future;
  }

  Future<void> stop() async {
    try {
      await NfcManager.instance.stopSession();
    } finally {
      final active = _activeCompleter;
      _activeCompleter = null;
      if (active != null && !active.isCompleted) {
        active.completeError(const NfcWriteException(
          NfcWriteError.cancelled,
          'NFC识别已取消',
        ));
      }
    }
  }

  String _tagUid(NfcTag tag) {
    final bytes = NfcA.from(tag)?.identifier ??
        MifareUltralight.from(tag)?.identifier ??
        NdefFormatable.from(tag)?.identifier;
    if (bytes == null) return '';
    return bytes
        .map((value) => value.toRadixString(16).padLeft(2, '0'))
        .join()
        .toUpperCase();
  }

  String? _managedPayload(NdefMessage? message) {
    if (message == null) return null;
    for (final record in message.records) {
      if (ascii.decode(record.type, allowInvalid: true) == fullExternalType) {
        return ascii.decode(record.payload, allowInvalid: true);
      }
    }
    return null;
  }
}

bool isRepeatedNfcTag(String? previousCompletedUid, String currentUid) {
  return previousCompletedUid != null &&
      previousCompletedUid.isNotEmpty &&
      currentUid.isNotEmpty &&
      previousCompletedUid.toUpperCase() == currentUid.toUpperCase();
}

class NfcWriteResult {
  const NfcWriteResult({required this.tagUid, required this.payload});

  final String tagUid;
  final String payload;
}

enum NfcWriteError {
  cancelled,
  unavailable,
  missingUid,
  sameTag,
  readOnly,
  tooSmall,
  existingManagedPayload,
  foreignPayload,
  notNdef,
  verifyFailed,
  writeFailed,
}

class NfcWriteException implements Exception {
  const NfcWriteException(
    this.kind,
    this.message, {
    this.tagUid,
    this.existingPayload,
  });

  final NfcWriteError kind;
  final String message;
  final String? tagUid;
  final String? existingPayload;

  bool get requiresOverwriteConfirmation =>
      kind == NfcWriteError.existingManagedPayload ||
      kind == NfcWriteError.foreignPayload;

  @override
  String toString() => message;
}
