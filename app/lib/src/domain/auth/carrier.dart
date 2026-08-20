enum CarrierAuthAvailability {
  available,
  disabled,
  unavailable,
}

class CarrierAuthCapability {
  const CarrierAuthCapability({
    required this.availability,
    this.provider = '',
    this.message = '',
  });

  const CarrierAuthCapability.available({required String provider})
      : this(
          availability: CarrierAuthAvailability.available,
          provider: provider,
        );

  const CarrierAuthCapability.disabled()
      : this(availability: CarrierAuthAvailability.disabled);

  const CarrierAuthCapability.unavailable({String message = ''})
      : this(
          availability: CarrierAuthAvailability.unavailable,
          message: message,
        );

  final CarrierAuthAvailability availability;
  final String provider;
  final String message;

  bool get isAvailable =>
      availability == CarrierAuthAvailability.available &&
      provider.trim().isNotEmpty;
}

class CarrierAuthCredential {
  const CarrierAuthCredential({
    required this.provider,
    required this.accessToken,
  });

  final String provider;
  final String accessToken;
}

enum CarrierAuthFailureReason {
  cancelled,
  unavailable,
  timeout,
  failed,
}

class CarrierAuthException implements Exception {
  const CarrierAuthException(this.reason, this.message);

  final CarrierAuthFailureReason reason;
  final String message;

  @override
  String toString() => message;
}
