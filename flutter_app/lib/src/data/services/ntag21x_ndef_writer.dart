import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:nfc_manager/nfc_manager.dart';
import 'package:nfc_manager/platform_tags.dart';

enum Ntag21xModel { ntag213, ntag215, ntag216 }

extension Ntag21xModelDetails on Ntag21xModel {
  int get storageSizeCode => switch (this) {
        Ntag21xModel.ntag213 => 0x0F,
        Ntag21xModel.ntag215 => 0x11,
        Ntag21xModel.ntag216 => 0x13,
      };

  int get capabilitySize => switch (this) {
        Ntag21xModel.ntag213 => 0x12,
        Ntag21xModel.ntag215 => 0x3E,
        Ntag21xModel.ntag216 => 0x6D,
      };

  int get ndefMemoryBytes => capabilitySize * 8;

  int get dynamicLockPage => switch (this) {
        Ntag21xModel.ntag213 => 0x28,
        Ntag21xModel.ntag215 => 0x82,
        Ntag21xModel.ntag216 => 0xE2,
      };
}

Ntag21xModel? identifyNtag21x(Uint8List version) {
  if (version.length != 8 ||
      version[0] != 0x00 ||
      version[1] != 0x04 ||
      version[2] != 0x04 ||
      version[3] != 0x02 ||
      version[4] != 0x01 ||
      version[7] != 0x03) {
    return null;
  }
  for (final model in Ntag21xModel.values) {
    if (model.storageSizeCode == version[6]) return model;
  }
  return null;
}

Uint8List encodeExternalTypeNdef({
  required String domain,
  required String type,
  required String payload,
}) {
  final typeBytes = ascii.encode(
    '${domain.trim().toLowerCase()}:${type.trim().toLowerCase()}',
  );
  final payloadBytes = ascii.encode(payload);
  if (typeBytes.isEmpty || typeBytes.length > 0xFF) {
    throw const FormatException('NDEF external type length is invalid');
  }
  if (payloadBytes.length > 0xFF) {
    throw const FormatException('NDEF payload is too large for a short record');
  }
  return Uint8List.fromList([
    0xD4, // MB, ME, SR and TNF External.
    typeBytes.length,
    payloadBytes.length,
    ...typeBytes,
    ...payloadBytes,
  ]);
}

class Ntag21xType2Data {
  const Ntag21xType2Data({
    required this.controlPrefix,
    required this.ndefMessage,
    required this.hasForeignData,
  });

  final Uint8List controlPrefix;
  final Uint8List? ndefMessage;
  final bool hasForeignData;

  String? managedPayload(String fullExternalType) {
    final message = ndefMessage;
    if (message == null || message.isEmpty) return null;
    return decodeExternalTypeNdef(message, fullExternalType);
  }
}

Ntag21xType2Data inspectType2Data(Uint8List data) {
  final controlPrefix = <int>[];
  var offset = 0;
  while (offset < data.length) {
    final tag = data[offset++];
    if (tag == 0x00) continue;
    if (tag == 0xFE) {
      return Ntag21xType2Data(
        controlPrefix: Uint8List.fromList(controlPrefix),
        ndefMessage: null,
        hasForeignData: false,
      );
    }
    if (offset >= data.length) {
      return _foreignType2Data(controlPrefix);
    }
    final lengthStart = offset;
    var length = data[offset++];
    if (length == 0xFF) {
      if (offset + 1 >= data.length) {
        return _foreignType2Data(controlPrefix);
      }
      length = (data[offset] << 8) | data[offset + 1];
      offset += 2;
    }
    if (offset + length > data.length) {
      return _foreignType2Data(controlPrefix);
    }
    final valueEnd = offset + length;
    if (tag == 0x01 || tag == 0x02) {
      controlPrefix
        ..add(tag)
        ..addAll(data.sublist(lengthStart, valueEnd));
      offset = valueEnd;
      continue;
    }
    if (tag == 0x03) {
      final message = Uint8List.fromList(data.sublist(offset, valueEnd));
      offset = valueEnd;
      while (offset < data.length) {
        final trailingTag = data[offset++];
        if (trailingTag == 0x00) continue;
        if (trailingTag == 0xFE) break;
        return Ntag21xType2Data(
          controlPrefix: Uint8List.fromList(controlPrefix),
          ndefMessage: message,
          hasForeignData: true,
        );
      }
      return Ntag21xType2Data(
        controlPrefix: Uint8List.fromList(controlPrefix),
        ndefMessage: message,
        hasForeignData: false,
      );
    }
    return _foreignType2Data(controlPrefix);
  }
  return Ntag21xType2Data(
    controlPrefix: Uint8List.fromList(controlPrefix),
    ndefMessage: null,
    hasForeignData: false,
  );
}

String? decodeExternalTypeNdef(
  Uint8List message,
  String fullExternalType,
) {
  if (message.length < 3) return null;
  final header = message[0];
  final isSingleShortExternalRecord = header & 0xF7 == 0xD4;
  if (!isSingleShortExternalRecord) return null;
  final typeLength = message[1];
  final payloadLength = message[2];
  final hasIdentifier = header & 0x08 != 0;
  var offset = 3;
  var identifierLength = 0;
  if (hasIdentifier) {
    if (offset >= message.length) return null;
    identifierLength = message[offset++];
  }
  final expectedLength = offset + typeLength + identifierLength + payloadLength;
  if (expectedLength != message.length) return null;
  final typeValue = ascii.decode(
    message.sublist(offset, offset + typeLength),
    allowInvalid: true,
  );
  if (typeValue != fullExternalType) return null;
  offset += typeLength + identifierLength;
  return ascii.decode(message.sublist(offset), allowInvalid: true);
}

Ntag21xType2Data _foreignType2Data(List<int> controlPrefix) {
  return Ntag21xType2Data(
    controlPrefix: Uint8List.fromList(controlPrefix),
    ndefMessage: null,
    hasForeignData: true,
  );
}

enum Ntag21xWriteBlocker {
  invalidCapabilityContainer,
  readOnly,
  staticLocked,
  dynamicLocked,
  passwordProtected,
}

abstract interface class Ntag21xIo {
  Future<Uint8List> getVersion();

  Future<Uint8List> readPages(int pageOffset);

  Future<void> writePage(int pageOffset, Uint8List data);
}

abstract interface class Type2TagSnapshot {
  Ntag21xIo get io;

  Uint8List get userMemory;

  Ntag21xType2Data get type2Data;

  Ntag21xWriteBlocker? get writeBlocker;

  int get ndefMemoryBytes;

  String get modelName;

  int requiredBytesForMessage(Uint8List message);
}

class Ntag21xSnapshot implements Type2TagSnapshot {
  const Ntag21xSnapshot({
    required this.model,
    required this.io,
    required this.userMemory,
    required this.type2Data,
    required this.writeBlocker,
  });

  final Ntag21xModel model;
  @override
  final Ntag21xIo io;
  @override
  final Uint8List userMemory;
  @override
  final Ntag21xType2Data type2Data;
  @override
  final Ntag21xWriteBlocker? writeBlocker;

  @override
  int get ndefMemoryBytes => model.ndefMemoryBytes;

  @override
  String get modelName => model.name;

  @override
  int requiredBytesForMessage(Uint8List message) {
    return type2Data.controlPrefix.length + 2 + message.length + 1;
  }
}

class CompatibleType2Snapshot implements Type2TagSnapshot {
  const CompatibleType2Snapshot({
    required this.io,
    required this.userMemory,
    required this.type2Data,
    required this.writeBlocker,
    required this.ndefMemoryBytes,
  });

  @override
  final Ntag21xIo io;
  @override
  final Uint8List userMemory;
  @override
  final Ntag21xType2Data type2Data;
  @override
  final Ntag21xWriteBlocker? writeBlocker;
  @override
  final int ndefMemoryBytes;

  @override
  String get modelName => 'type2-compatible';

  @override
  int requiredBytesForMessage(Uint8List message) {
    return type2Data.controlPrefix.length + 2 + message.length + 1;
  }
}

class Type2TagInspection {
  const Type2TagInspection({
    required this.snapshot,
    required this.transport,
    this.atqa,
    this.sak,
    this.version,
    this.capabilityContainer,
    this.probeError,
  });

  final Type2TagSnapshot? snapshot;
  final String transport;
  final Uint8List? atqa;
  final int? sak;
  final Uint8List? version;
  final Uint8List? capabilityContainer;
  final String? probeError;

  String get diagnostic {
    final values = <String>[
      'transport=$transport',
      if (atqa != null) 'atqa=${_hex(atqa!)}',
      if (sak != null) 'sak=0x${sak!.toRadixString(16).padLeft(2, '0')}',
      'version=${version == null ? 'unavailable' : _hex(version!)}',
      if (capabilityContainer != null) 'cc=${_hex(capabilityContainer!)}',
      if (probeError != null && probeError!.isNotEmpty)
        'probeError=$probeError',
    ];
    return values.join(', ');
  }
}

class Ntag21xNdefWriter {
  const Ntag21xNdefWriter();

  Future<Type2TagInspection> inspectDetailed(NfcTag tag) {
    final nfcA = NfcA.from(tag);
    final ultralight = MifareUltralight.from(tag);
    if (ultralight != null) {
      return inspectIoDetailed(
        _MifareUltralightIo(ultralight),
        transport: 'mifareultralight',
        atqa: nfcA?.atqa,
        sak: nfcA?.sak,
      );
    }
    if (nfcA != null) {
      return inspectIoDetailed(
        _NfcAIo(nfcA),
        transport: 'nfca',
        atqa: nfcA.atqa,
        sak: nfcA.sak,
      );
    }
    return Future<Type2TagInspection>.value(const Type2TagInspection(
      snapshot: null,
      transport: 'unavailable',
      probeError: 'no Type 2 compatible transport',
    ));
  }

  Future<Ntag21xSnapshot?> inspect(NfcTag tag) async {
    final inspection = await inspectDetailed(tag);
    final snapshot = inspection.snapshot;
    return snapshot is Ntag21xSnapshot ? snapshot : null;
  }

  Future<Ntag21xSnapshot?> inspectIo(Ntag21xIo io) async {
    final inspection = await inspectIoDetailed(io);
    final snapshot = inspection.snapshot;
    return snapshot is Ntag21xSnapshot ? snapshot : null;
  }

  Future<Type2TagInspection> inspectIoDetailed(
    Ntag21xIo io, {
    String transport = 'test',
    Uint8List? atqa,
    int? sak,
  }) async {
    Uint8List? version;
    String? versionError;
    try {
      version = await io.getVersion();
    } catch (error) {
      versionError = _errorSummary(error);
    }
    final model = version == null ? null : identifyNtag21x(version);
    if (model != null) {
      return Type2TagInspection(
        snapshot: await _inspectNtag21x(io, model),
        transport: transport,
        atqa: atqa,
        sak: sak,
        version: version,
      );
    }

    Uint8List? header;
    String? readError;
    try {
      header = await _readFourPages(io, 2);
    } catch (error) {
      readError = _errorSummary(error);
    }
    final capability =
        header == null ? null : Uint8List.fromList(header.sublist(4, 8));
    final baseInspection = Type2TagInspection(
      snapshot: null,
      transport: transport,
      atqa: atqa,
      sak: sak,
      version: version,
      capabilityContainer: capability,
      probeError: [
        if (versionError != null) 'getVersion:$versionError',
        if (readError != null) 'readPages:$readError',
      ].join('; '),
    );
    if (header == null || !_isSupportedType2Capability(capability!)) {
      return baseInspection;
    }

    final readAccess = capability[3] >> 4;
    if (readAccess != 0) {
      return Type2TagInspection(
        snapshot: null,
        transport: transport,
        atqa: atqa,
        sak: sak,
        version: version,
        capabilityContainer: capability,
        probeError: '${baseInspection.probeError}; ccReadAccess=$readAccess',
      );
    }

    final memoryBytes = capability[2] * 8;
    Uint8List userMemory;
    try {
      userMemory = await _readBytes(
        io,
        startPage: 4,
        byteLength: memoryBytes,
      );
    } catch (error) {
      return Type2TagInspection(
        snapshot: null,
        transport: transport,
        atqa: atqa,
        sak: sak,
        version: version,
        capabilityContainer: capability,
        probeError:
            '${baseInspection.probeError}; readUserMemory:${_errorSummary(error)}',
      );
    }
    final writeAccess = capability[3] & 0x0F;
    final blocker = header[2] != 0 || header[3] != 0
        ? Ntag21xWriteBlocker.staticLocked
        : writeAccess == 0x0F
            ? Ntag21xWriteBlocker.readOnly
            : writeAccess != 0
                ? Ntag21xWriteBlocker.passwordProtected
                : null;
    return Type2TagInspection(
      snapshot: CompatibleType2Snapshot(
        io: io,
        userMemory: userMemory,
        type2Data: inspectType2Data(userMemory),
        writeBlocker: blocker,
        ndefMemoryBytes: memoryBytes,
      ),
      transport: transport,
      atqa: atqa,
      sak: sak,
      version: version,
      capabilityContainer: capability,
      probeError: baseInspection.probeError,
    );
  }

  Future<Ntag21xSnapshot> _inspectNtag21x(
    Ntag21xIo io,
    Ntag21xModel model,
  ) async {
    final header = await _readFourPages(io, 2);
    final access = await _readFourPages(io, model.dynamicLockPage);
    final userMemory = await _readBytes(
      io,
      startPage: 4,
      byteLength: model.ndefMemoryBytes,
    );
    final expectedCapability = [
      0xE1,
      0x10,
      model.capabilitySize,
      0x00,
    ];
    final capability = header.sublist(4, 8);
    final blocker = !_listEquals(capability, expectedCapability)
        ? capability.length == 4 && capability[3] & 0x0F == 0x0F
            ? Ntag21xWriteBlocker.readOnly
            : Ntag21xWriteBlocker.invalidCapabilityContainer
        : header[2] != 0 || header[3] != 0
            ? Ntag21xWriteBlocker.staticLocked
            : access[0] != 0 || access[1] != 0 || access[2] != 0
                ? Ntag21xWriteBlocker.dynamicLocked
                : access[7] != 0xFF
                    ? Ntag21xWriteBlocker.passwordProtected
                    : null;
    return Ntag21xSnapshot(
      model: model,
      io: io,
      userMemory: userMemory,
      type2Data: inspectType2Data(userMemory),
      writeBlocker: blocker,
    );
  }

  Future<void> writeExternal({
    required Type2TagSnapshot snapshot,
    required String domain,
    required String type,
    required String payload,
  }) async {
    if (snapshot.writeBlocker != null) {
      throw StateError('Type 2 write is blocked: ${snapshot.writeBlocker}');
    }
    final message = encodeExternalTypeNdef(
      domain: domain,
      type: type,
      payload: payload,
    );
    if (message.length > 0xFE) {
      throw const FormatException(
        'Type 2 writer supports short NDEF TLV messages only',
      );
    }
    final prefix = snapshot.type2Data.controlPrefix;
    final finalData = Uint8List.fromList([
      ...prefix,
      0x03,
      message.length,
      ...message,
      0xFE,
    ]);
    if (finalData.length > snapshot.ndefMemoryBytes) {
      throw Ntag21xCapacityException(
        requiredBytes: finalData.length,
        availableBytes: snapshot.ndefMemoryBytes,
      );
    }

    final staging = Uint8List.fromList(finalData);
    final lengthOffset = prefix.length + 1;
    staging[lengthOffset] = 0;
    final pageCount = (finalData.length + 3) ~/ 4;
    final paddedStaging = Uint8List(pageCount * 4)
      ..setRange(0, staging.length, staging);
    final paddedFinal = Uint8List(pageCount * 4)
      ..setRange(0, finalData.length, finalData);
    final commitPageIndex = lengthOffset ~/ 4;
    final invalidPage = _pageAt(paddedStaging, commitPageIndex);
    final lengthIndexInPage = lengthOffset % 4;
    if (lengthIndexInPage < 3) {
      for (var index = lengthIndexInPage + 1; index < 4; index++) {
        invalidPage[index] = 0;
      }
      invalidPage[lengthIndexInPage + 1] = 0xFE;
    }

    await snapshot.io.writePage(
      4 + commitPageIndex,
      invalidPage,
    );
    for (var pageIndex = 0; pageIndex < pageCount; pageIndex++) {
      if (pageIndex == commitPageIndex) continue;
      await snapshot.io.writePage(
        4 + pageIndex,
        _pageAt(paddedStaging, pageIndex),
      );
    }
    await snapshot.io.writePage(
      4 + commitPageIndex,
      _pageAt(paddedFinal, commitPageIndex),
    );

    final verified = await _readBytes(
      snapshot.io,
      startPage: 4,
      byteLength: finalData.length,
    );
    final verifiedData = inspectType2Data(verified);
    if (verifiedData.managedPayload('$domain:$type') != payload) {
      throw const FormatException('Type 2 raw NDEF verification failed');
    }
  }
}

class Ntag21xCapacityException implements Exception {
  const Ntag21xCapacityException({
    required this.requiredBytes,
    required this.availableBytes,
  });

  final int requiredBytes;
  final int availableBytes;
}

class _MifareUltralightIo implements Ntag21xIo {
  const _MifareUltralightIo(this.tag);

  final MifareUltralight tag;

  @override
  Future<Uint8List> getVersion() {
    return tag.transceive(data: Uint8List.fromList([0x60]));
  }

  @override
  Future<Uint8List> readPages(int pageOffset) {
    return tag.readPages(pageOffset: pageOffset);
  }

  @override
  Future<void> writePage(int pageOffset, Uint8List data) {
    return tag.writePage(pageOffset: pageOffset, data: data);
  }
}

class _NfcAIo implements Ntag21xIo {
  const _NfcAIo(this.tag);

  final NfcA tag;

  @override
  Future<Uint8List> getVersion() {
    return tag.transceive(data: Uint8List.fromList([0x60]));
  }

  @override
  Future<Uint8List> readPages(int pageOffset) {
    return tag.transceive(data: Uint8List.fromList([0x30, pageOffset]));
  }

  @override
  Future<void> writePage(int pageOffset, Uint8List data) async {
    if (data.length != 4) {
      throw ArgumentError.value(data.length, 'data.length', 'must be 4');
    }
    final response = await tag.transceive(
      data: Uint8List.fromList([0xA2, pageOffset, ...data]),
    );
    if (response.length != 1 || response[0] & 0x0F != 0x0A) {
      throw const FormatException('NTAG21x write was not acknowledged');
    }
  }
}

Future<Uint8List> _readFourPages(Ntag21xIo io, int pageOffset) async {
  final value = await io.readPages(pageOffset);
  if (value.length < 16) {
    throw const FormatException('NTAG21x read returned fewer than four pages');
  }
  return Uint8List.fromList(value.sublist(0, 16));
}

Future<Uint8List> _readBytes(
  Ntag21xIo io, {
  required int startPage,
  required int byteLength,
}) async {
  final result = BytesBuilder(copy: false);
  for (var offset = 0; offset < byteLength; offset += 16) {
    final pages = await _readFourPages(io, startPage + offset ~/ 4);
    result.add(pages.sublist(0, (byteLength - offset).clamp(0, 16)));
  }
  return result.takeBytes();
}

Uint8List _pageAt(Uint8List data, int pageIndex) {
  final offset = pageIndex * 4;
  return Uint8List.fromList(data.sublist(offset, offset + 4));
}

bool _listEquals(List<int> left, List<int> right) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}

bool _isSupportedType2Capability(Uint8List capability) {
  if (capability.length != 4) return false;
  final mappingMajorVersion = capability[1] >> 4;
  final memoryBytes = capability[2] * 8;
  return capability[0] == 0xE1 &&
      mappingMajorVersion == 1 &&
      memoryBytes > 0 &&
      memoryBytes <= 0xFF * 8;
}

String _errorSummary(Object error) {
  if (error is PlatformException) {
    final message = error.message;
    return message == null || message.isEmpty
        ? error.code
        : '${error.code}:$message';
  }
  return error.runtimeType.toString();
}

String _hex(Iterable<int> values) {
  return values
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join()
      .toUpperCase();
}
