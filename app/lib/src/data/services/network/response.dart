import 'package:rabbit_flutter/src/data/services/network/exception.dart';

Map<String, dynamic> requireJsonObject(
  Object? data, {
  required String message,
}) {
  if (data is! Map) {
    throw ApiException(message);
  }
  return Map<String, dynamic>.from(data);
}

List<Map<String, dynamic>> requireJsonObjectList(
  Object? data, {
  required String message,
}) {
  if (data is! List) {
    throw ApiException(message);
  }
  return data
      .whereType<Map>()
      .map(Map<String, dynamic>.from)
      .toList(growable: false);
}
