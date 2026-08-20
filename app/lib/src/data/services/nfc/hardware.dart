import 'dart:async';
import 'dart:convert';
import 'dart:developer' as developer;

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:nfc_manager/platform_tags.dart';

import 'package:rabbit_flutter/src/data/services/nfc/ntag21x_writer.dart';

final nfcHardwareServiceProvider = Provider<NfcHardwareService>((ref) {
  return NfcHardwareService();
});

class NfcHardwareService {
  static const externalDomain = 'dzht.top';
  static const externalType = 'rabbit-cage';
  static const fullExternalType = '$externalDomain:$externalType';
  static const _verificationAttempts = 2;
  static const _verificationRetryDelay = Duration(milliseconds: 120);
  static const _ntag21xWriter = Ntag21xNdefWriter();
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
      await NfcManager.instance.startSession(
        pollingOptions: const {NfcPollingOption.iso14443},
        onDiscovered: (tag) async {
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
              final existing = managedNfcPayload(ndef.cachedMessage);
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

              // A previous write can succeed even when Android reports an I/O
              // failure during the immediate read-back. Rediscovery is the
              // reliable recovery boundary because cachedMessage was read by
              // Android before this callback.
              if (!hasExactManagedNfcPayload(ndef.cachedMessage, payload)) {
                await _writeNdef(ndef, message, uid);
                await _verifyNdef(ndef, payload, uid);
              }
            } else {
              final formatable = NdefFormatable.from(tag);
              if (formatable != null) {
                await _formatNdef(formatable, message, uid);
              } else {
                await _writeRawNtag21x(
                  tag: tag,
                  payload: payload,
                  tagUid: uid,
                  allowOverwrite: allowOverwrite,
                );
              }
            }

            await NfcManager.instance.stopSession();
            if (!completer.isCompleted) {
              completer.complete(NfcWriteResult(tagUid: uid, payload: payload));
            }
            if (identical(_activeCompleter, completer)) {
              _activeCompleter = null;
            }
          } on NfcWriteException catch (error) {
            await completeError(error);
          } catch (error, stackTrace) {
            await completeError(_operationFailure(
              operation: NfcWriteOperation.write,
              error: error,
              stackTrace: stackTrace,
              tagUid: uid,
            ));
          }
        },
      );
    } catch (error, stackTrace) {
      if (identical(_activeCompleter, completer)) _activeCompleter = null;
      if (!completer.isCompleted) {
        completer.completeError(_operationFailure(
          operation: NfcWriteOperation.startSession,
          error: error,
          stackTrace: stackTrace,
        ));
      }
    }
    return completer.future;
  }

  Future<void> _writeNdef(
    Ndef ndef,
    NdefMessage message,
    String tagUid,
  ) async {
    try {
      await ndef.write(message);
    } catch (error, stackTrace) {
      throw _operationFailure(
        operation: NfcWriteOperation.write,
        error: error,
        stackTrace: stackTrace,
        tagUid: tagUid,
        mayHaveWritten: true,
      );
    }
  }

  Future<void> _verifyNdef(
    Ndef ndef,
    String payload,
    String tagUid,
  ) async {
    Object? lastError;
    StackTrace? lastStackTrace;
    for (var attempt = 0; attempt < _verificationAttempts; attempt++) {
      try {
        final verified = await ndef.read();
        if (hasExactManagedNfcPayload(verified, payload)) return;
        lastError = null;
        lastStackTrace = null;
      } catch (error, stackTrace) {
        lastError = error;
        lastStackTrace = stackTrace;
      }
      if (attempt + 1 < _verificationAttempts) {
        await Future<void>.delayed(_verificationRetryDelay);
      }
    }
    if (lastError != null) {
      throw _operationFailure(
        operation: NfcWriteOperation.verify,
        error: lastError,
        stackTrace: lastStackTrace ?? StackTrace.current,
        tagUid: tagUid,
        mayHaveWritten: true,
      );
    }
    throw NfcWriteException(
      NfcWriteError.verifyFailed,
      '标签回读内容不完整，请保持贴卡并重试',
      tagUid: tagUid,
      operation: NfcWriteOperation.verify,
      mayHaveWritten: true,
    );
  }

  Future<void> _formatNdef(
    NdefFormatable formatable,
    NdefMessage message,
    String tagUid,
  ) async {
    try {
      await formatable.format(message);
    } catch (error, stackTrace) {
      throw _operationFailure(
        operation: NfcWriteOperation.format,
        error: error,
        stackTrace: stackTrace,
        tagUid: tagUid,
        mayHaveWritten: true,
      );
    }
  }

  Future<void> _writeRawNtag21x({
    required NfcTag tag,
    required String payload,
    required String tagUid,
    required bool allowOverwrite,
  }) async {
    Type2TagInspection inspection;
    try {
      inspection = await _ntag21xWriter.inspectDetailed(tag);
    } catch (error, stackTrace) {
      throw _operationFailure(
        operation: NfcWriteOperation.format,
        error: error,
        stackTrace: stackTrace,
        tagUid: tagUid,
      );
    }
    final snapshot = inspection.snapshot;
    if (snapshot == null) {
      final technologies = tag.data.keys.toList()..sort();
      final details = [
        'technologies=${technologies.join(',')}',
        inspection.diagnostic,
      ].join(', ');
      developer.log(
        'Unsupported NFC tag $details',
        name: 'rabbit.nfc',
      );
      throw NfcWriteException(
        NfcWriteError.notNdef,
        '标签未提供可安全写入的NDEF Type 2存储区',
        tagUid: tagUid,
        operation: NfcWriteOperation.format,
        platformDetails: details,
      );
    }

    final existing = snapshot.type2Data.managedPayload(fullExternalType);
    final hasForeignData = snapshot.type2Data.hasForeignData ||
        (snapshot.type2Data.ndefMessage?.isNotEmpty ?? false) &&
            existing == null;
    if (!allowOverwrite && existing != null && existing != payload) {
      throw NfcWriteException(
        NfcWriteError.existingManagedPayload,
        '标签已写入其他笼位',
        tagUid: tagUid,
        existingPayload: existing,
      );
    }
    if (!allowOverwrite && hasForeignData) {
      throw NfcWriteException(
        NfcWriteError.foreignPayload,
        '标签包含其他数据',
        tagUid: tagUid,
      );
    }
    if (existing == payload) return;

    final blocker = snapshot.writeBlocker;
    if (blocker != null) {
      final isLocked =
          blocker != Ntag21xWriteBlocker.invalidCapabilityContainer;
      throw NfcWriteException(
        isLocked ? NfcWriteError.readOnly : NfcWriteError.notNdef,
        isLocked
            ? 'NDEF Type 2标签已锁定或受密码保护，无法写入'
            : 'NDEF Type 2标签的容量信息异常，无法安全写入',
        tagUid: tagUid,
        operation: NfcWriteOperation.format,
        platformDetails:
            'model=${snapshot.modelName}, blocker=${blocker.name}, ${inspection.diagnostic}',
      );
    }

    final rawMessage = encodeExternalTypeNdef(
      domain: externalDomain,
      type: externalType,
      payload: payload,
    );
    final requiredBytes = snapshot.requiredBytesForMessage(rawMessage);
    if (requiredBytes > snapshot.ndefMemoryBytes) {
      throw NfcWriteException(
        NfcWriteError.tooSmall,
        '标签容量不足，需要至少 $requiredBytes 字节',
        tagUid: tagUid,
      );
    }
    try {
      await _ntag21xWriter.writeExternal(
        snapshot: snapshot,
        domain: externalDomain,
        type: externalType,
        payload: payload,
      );
    } catch (error, stackTrace) {
      throw _operationFailure(
        operation: NfcWriteOperation.format,
        error: error,
        stackTrace: stackTrace,
        tagUid: tagUid,
        mayHaveWritten: true,
        contextDetails: inspection.diagnostic,
      );
    }
  }

  NfcWriteException _operationFailure({
    required NfcWriteOperation operation,
    required Object error,
    required StackTrace stackTrace,
    String? tagUid,
    bool mayHaveWritten = false,
    String? contextDetails,
  }) {
    final platformError = error is PlatformException ? error : null;
    final recordedDetails = [
      if (platformError?.details != null) platformError!.details.toString(),
      if (contextDetails != null && contextDetails.isNotEmpty) contextDetails,
    ].join(', ');
    developer.log(
      'NFC ${operation.name} failed'
      ' tagUid=${tagUid ?? '-'}'
      ' code=${platformError?.code ?? '-'}'
      ' message=${platformError?.message ?? '-'}'
      ' details=${recordedDetails.isEmpty ? '-' : recordedDetails}',
      name: 'rabbit.nfc',
      error: error,
      stackTrace: stackTrace,
    );
    final code = platformError?.code;
    final codeSuffix = code == null || code.isEmpty ? '' : '（$code）';
    final message = switch (operation) {
      NfcWriteOperation.startSession => '无法启动NFC识别$codeSuffix',
      NfcWriteOperation.write => '标签写入发生通信中断，请保持标签贴近手机后重试$codeSuffix',
      NfcWriteOperation.verify => '标签可能已写入，但回读校验失败；请保持贴卡并重试$codeSuffix',
      NfcWriteOperation.format => '标签格式化写入失败，请保持标签贴近手机后重试$codeSuffix',
    };
    final kind = operation == NfcWriteOperation.verify
        ? NfcWriteError.verifyFailed
        : NfcWriteError.writeFailed;
    return NfcWriteException(
      kind,
      message,
      tagUid: tagUid,
      operation: operation,
      platformCode: code,
      platformMessage: platformError?.message,
      platformDetails: recordedDetails.isEmpty ? null : recordedDetails,
      mayHaveWritten: mayHaveWritten,
    );
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
}

String? managedNfcPayload(NdefMessage? message) {
  if (message == null) return null;
  for (final record in message.records) {
    if (ascii.decode(record.type, allowInvalid: true) ==
        NfcHardwareService.fullExternalType) {
      return ascii.decode(record.payload, allowInvalid: true);
    }
  }
  return null;
}

bool hasExactManagedNfcPayload(NdefMessage? message, String payload) {
  return message != null &&
      message.records.length == 1 &&
      managedNfcPayload(message) == payload;
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

enum NfcWriteOperation { startSession, write, verify, format }

class NfcWriteException implements Exception {
  const NfcWriteException(
    this.kind,
    this.message, {
    this.tagUid,
    this.existingPayload,
    this.operation,
    this.platformCode,
    this.platformMessage,
    this.platformDetails,
    this.mayHaveWritten = false,
  });

  final NfcWriteError kind;
  final String message;
  final String? tagUid;
  final String? existingPayload;
  final NfcWriteOperation? operation;
  final String? platformCode;
  final String? platformMessage;
  final String? platformDetails;
  final bool mayHaveWritten;

  String get diagnosticMessage {
    final details = <String>[
      if (operation != null) 'stage=${operation!.name}',
      if (platformCode != null && platformCode!.isNotEmpty)
        'code=$platformCode',
      if (platformMessage != null && platformMessage!.isNotEmpty)
        'message=$platformMessage',
      if (platformDetails != null && platformDetails!.isNotEmpty)
        'details=$platformDetails',
      if (mayHaveWritten) 'writeOutcome=ambiguous',
    ];
    return details.isEmpty ? message : '$message [${details.join(', ')}]';
  }

  bool get requiresOverwriteConfirmation =>
      kind == NfcWriteError.existingManagedPayload ||
      kind == NfcWriteError.foreignPayload;

  @override
  String toString() => message;
}
