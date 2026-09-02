import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// The ways a tag payload can break the
/// `r1.<houseId>.<cageId>.<keyId>.<signature>` contract that
/// `NfcPayloadTarget.parse` enforces. Each entry maps to one rejection branch.
enum NfcPayloadDefect {
  /// Not the versioned protocol at all, e.g. an old-style deep link.
  foreignProtocol,

  /// Right shape, wrong version prefix.
  unsupportedVersion,

  /// Signature segment missing entirely.
  tooFewSegments,

  /// An extra trailing segment after the signature.
  tooManySegments,

  /// Ids that are not base36 digits, so they decode to zero.
  unreadableIds,

  /// House id present but zero.
  zeroHouseId,

  /// Cage id present but zero.
  zeroCageId,

  /// Key id present but zero.
  zeroKeyId,

  /// Signature segment present but empty.
  emptySignature,
}

/// Drives the `com.rabbit.app.flutter/nfc_intents` MethodChannel in tests so a
/// tap-to-select flow can run without NFC hardware.
///
/// The channel carries traffic in both directions and this harness covers both:
///
///  * Flutter to platform: `initialize()` calls `takePendingIntent` to drain a
///    tag that launched the app. Configure the answer with the `stubPending*`
///    methods.
///  * Platform to Flutter: the native side calls `nfcIntent` when a tag is
///    tapped while the app is already running. Inject those with [tap],
///    [tapPayload], or [tapRaw].
///
/// Creating the harness installs the outbound stub and registers its own
/// teardown, so no test can leak a mock handler into the next one.
///
/// ```dart
/// final nfc = NfcHarness();
/// final service = NfcIntentService();
/// addTearDown(service.dispose);
/// await service.initialize();
///
/// expectLater(service.events, emits(isA<NfcLaunchEvent>()));
/// await nfc.tap(houseId: 8, cageId: 10);
/// ```
///
/// `NfcIntentService.events` is a broadcast stream, so subscribe before
/// injecting: a tap with no listener attached is dropped by the controller.
class NfcHarness {
  /// Installs the outbound stub for [channelName] and schedules teardown.
  ///
  /// The default pending-intent answer is [stubPendingNothing], which models
  /// the ordinary case of an app opened without a tag waiting for it. Call one
  /// of the other `stubPending*` methods to change it, before or after
  /// `initialize()`.
  NfcHarness() {
    _binding = TestWidgetsFlutterBinding.ensureInitialized();
    _binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _channel,
      _handleOutboundCall,
    );
    addTearDown(dispose);
  }

  /// The channel `NfcIntentService` talks over.
  static const String channelName = 'com.rabbit.app.flutter/nfc_intents';

  /// Method the platform invokes on Flutter when a tag is tapped.
  static const String tapMethod = 'nfcIntent';

  /// Method Flutter invokes on the platform to drain a launch tag.
  static const String pendingIntentMethod = 'takePendingIntent';

  /// Stand-in tag serial, shaped like the ones the reader reports.
  static const String defaultTagUid = '04AABBCC';

  /// Stand-in signature segment. The parser only requires it to be non-empty.
  static const String defaultSignature = 'signature';

  /// Key id used when a test does not care which signing key was used.
  static const int defaultKeyId = 1;

  static const MethodChannel _channel = MethodChannel(channelName);
  static const StandardMethodCodec _codec = StandardMethodCodec();

  late final TestWidgetsFlutterBinding _binding;

  Future<Object?> Function() _pendingIntent = _noPendingIntent;
  int _pendingIntentCalls = 0;
  bool _disposed = false;

  /// How many times `takePendingIntent` has been invoked.
  int get pendingIntentCalls => _pendingIntentCalls;

  /// Builds a well-formed payload so tests never hand-write base36.
  ///
  /// `payload(houseId: 8, cageId: 10)` produces `r1.8.a.1.signature`.
  static String payload({
    required int houseId,
    required int cageId,
    int keyId = defaultKeyId,
    String signature = defaultSignature,
  }) {
    final house = houseId.toRadixString(36);
    final cage = cageId.toRadixString(36);
    final key = keyId.toRadixString(36);
    return 'r1.$house.$cage.$key.$signature';
  }

  /// Builds a payload that `NfcPayloadTarget.parse` must reject.
  ///
  /// Every [NfcPayloadDefect] is derived from an otherwise valid payload for
  /// [houseId] and [cageId], so a rejection can only come from the defect.
  static String malformedPayload([
    NfcPayloadDefect defect = NfcPayloadDefect.foreignProtocol,
    int houseId = 8,
    int cageId = 10,
  ]) {
    final house = houseId.toRadixString(36);
    final cage = cageId.toRadixString(36);
    const key = defaultKeyId;
    switch (defect) {
      case NfcPayloadDefect.foreignProtocol:
        return 'rabbit://cage/$houseId/$cageId';
      case NfcPayloadDefect.unsupportedVersion:
        return 'r2.$house.$cage.$key.$defaultSignature';
      case NfcPayloadDefect.tooFewSegments:
        return 'r1.$house.$cage.$key';
      case NfcPayloadDefect.tooManySegments:
        return 'r1.$house.$cage.$key.$defaultSignature.extra';
      case NfcPayloadDefect.unreadableIds:
        return 'r1.**.$cage.$key.$defaultSignature';
      case NfcPayloadDefect.zeroHouseId:
        return 'r1.0.$cage.$key.$defaultSignature';
      case NfcPayloadDefect.zeroCageId:
        return 'r1.$house.0.$key.$defaultSignature';
      case NfcPayloadDefect.zeroKeyId:
        return 'r1.$house.$cage.0.$defaultSignature';
      case NfcPayloadDefect.emptySignature:
        return 'r1.$house.$cage.$key.';
    }
  }

  /// Builds the argument map the platform sends for one tag read.
  static Map<String, Object?> eventArguments({
    required String payload,
    String tagUid = defaultTagUid,
    DateTime? receivedAt,
  }) {
    return <String, Object?>{
      'payload': payload,
      'tagUid': tagUid,
      'receivedAt': (receivedAt ?? DateTime.now()).millisecondsSinceEpoch,
    };
  }

  /// Answers `takePendingIntent` with a tag that launched the app.
  void stubPendingTap({
    required int houseId,
    required int cageId,
    int keyId = defaultKeyId,
    String signature = defaultSignature,
    String tagUid = defaultTagUid,
    DateTime? receivedAt,
  }) {
    stubPendingPayload(
      payload(
        houseId: houseId,
        cageId: cageId,
        keyId: keyId,
        signature: signature,
      ),
      tagUid: tagUid,
      receivedAt: receivedAt,
    );
  }

  /// Answers `takePendingIntent` with an arbitrary payload string.
  void stubPendingPayload(
    String payload, {
    String tagUid = defaultTagUid,
    DateTime? receivedAt,
  }) {
    final arguments = eventArguments(
      payload: payload,
      tagUid: tagUid,
      receivedAt: receivedAt,
    );
    _pendingIntent = () async => arguments;
  }

  /// Answers `takePendingIntent` with `null`: no tag was waiting. The default.
  void stubPendingNothing() {
    _pendingIntent = _noPendingIntent;
  }

  /// Makes `takePendingIntent` throw [MissingPluginException], the way a test
  /// host or any non-Android build behaves with no native NFC bridge attached.
  void stubPendingMissingPlugin() {
    _pendingIntent = () async {
      throw MissingPluginException(
        'No implementation found for method $pendingIntentMethod '
        'on channel $channelName',
      );
    };
  }

  /// Makes `takePendingIntent` fail, modelling NFC being off or denied.
  void stubPendingFailure({
    String code = 'denied',
    String? message,
    Object? details,
  }) {
    _pendingIntent = () async {
      throw PlatformException(code: code, message: message, details: details);
    };
  }

  /// Injects a tag tap for [cageId] in [houseId] while capture is active.
  ///
  /// Returns the raw platform response, which is `null` when nothing is
  /// listening on the channel — useful for asserting a handler was cleared.
  Future<ByteData?> tap({
    required int houseId,
    required int cageId,
    int keyId = defaultKeyId,
    String signature = defaultSignature,
    String tagUid = defaultTagUid,
    DateTime? receivedAt,
  }) {
    return tapPayload(
      payload(
        houseId: houseId,
        cageId: cageId,
        keyId: keyId,
        signature: signature,
      ),
      tagUid: tagUid,
      receivedAt: receivedAt,
    );
  }

  /// Injects a tag tap carrying [payload] verbatim, valid or not.
  Future<ByteData?> tapPayload(
    String payload, {
    String tagUid = defaultTagUid,
    DateTime? receivedAt,
  }) {
    return tapRaw(
      eventArguments(
        payload: payload,
        tagUid: tagUid,
        receivedAt: receivedAt,
      ),
    );
  }

  /// Injects a `nfcIntent` call with [arguments] exactly as given, for the
  /// paths where the platform sends something unusable.
  Future<ByteData?> tapRaw(Object? arguments) {
    return _binding.defaultBinaryMessenger.handlePlatformMessage(
      channelName,
      _codec.encodeMethodCall(MethodCall(tapMethod, arguments)),
      null,
    );
  }

  /// Removes both directions of the channel wiring. Runs automatically at the
  /// end of the test and is safe to call again by hand.
  void dispose() {
    if (_disposed) {
      return;
    }
    _disposed = true;
    _binding.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
    _binding.defaultBinaryMessenger.setMessageHandler(channelName, null);
  }

  Future<Object?> _handleOutboundCall(MethodCall call) async {
    if (call.method == pendingIntentMethod) {
      _pendingIntentCalls++;
      return _pendingIntent();
    }
    throw MissingPluginException(
      'Unexpected call to ${call.method} on channel $channelName',
    );
  }

  static Future<Object?> _noPendingIntent() async => null;
}
