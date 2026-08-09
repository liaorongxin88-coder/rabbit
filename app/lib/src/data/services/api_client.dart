import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/config/app_config.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';

final apiBaseUrlProvider = Provider<String>((_) => AppConfig.defaultBaseUrl);

final apiClientProvider = Provider<ApiClient>((ref) {
  final sessionStore = ref.watch(sessionStoreProvider);
  final client = ApiClient(
    sessionStore,
    baseUrl: ref.watch(apiBaseUrlProvider),
  );
  ref.onDispose(client.dispose);
  return client;
});

class ApiClient {
  ApiClient(
    this._sessionStore, {
    Dio? dio,
    String? baseUrl,
  }) : _dio = dio ?? _buildDio(baseUrl ?? AppConfig.defaultBaseUrl);

  final SessionStore _sessionStore;
  final Dio _dio;
  final _unauthorizedController = StreamController<void>.broadcast(sync: true);

  Stream<void> get unauthorizedEvents => _unauthorizedController.stream;

  bool get usesSecureTransport => isSecureBaseUrl(_dio.options.baseUrl);

  static bool isSecureBaseUrl(String baseUrl) {
    final uri = Uri.tryParse(baseUrl.trim());
    return uri != null &&
        uri.scheme.toLowerCase() == 'https' &&
        uri.hasAuthority &&
        uri.host.isNotEmpty;
  }

  static Dio _buildDio(String baseUrl) {
    return Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 30),
        sendTimeout: const Duration(seconds: 30),
        headers: const {'Content-Type': 'application/json'},
      ),
    );
  }

  Future<T> get<T>(
    String path, {
    int? houseId,
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
    required T Function(Object? data) decode,
  }) async {
    final options = await _options(houseId: houseId);
    return _request(
      () => _dio.get<Object?>(
        path,
        queryParameters: query,
        options: options,
        cancelToken: cancelToken,
      ),
      decode,
    );
  }

  Future<T> post<T>(
    String path, {
    int? houseId,
    Object? body,
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
  }) async {
    final options = await _options(houseId: houseId);
    return _request(
      () => _dio.post<Object?>(
        path,
        data: body ?? const <String, dynamic>{},
        queryParameters: query,
        options: options,
      ),
      decode,
    );
  }

  Future<T> put<T>(
    String path, {
    int? houseId,
    Object? body,
    required T Function(Object? data) decode,
  }) async {
    final options = await _options(houseId: houseId);
    return _request(
      () => _dio.put<Object?>(
        path,
        data: body ?? const <String, dynamic>{},
        options: options,
      ),
      decode,
    );
  }

  Future<T> delete<T>(
    String path, {
    int? houseId,
    Map<String, dynamic>? query,
    required T Function(Object? data) decode,
  }) async {
    final options = await _options(houseId: houseId);
    return _request(
      () => _dio.delete<Object?>(
        path,
        queryParameters: query,
        options: options,
      ),
      decode,
    );
  }

  Future<T> _request<T>(
    Future<Response<Object?>> Function() send,
    T Function(Object? data) decode,
  ) async {
    try {
      final response = await send();
      final body = response.data;
      if (body is! Map) {
        throw const ApiException('服务返回格式不正确');
      }

      final code = _intValue(body['code']);
      if (code != null && code != 0) {
        final exception = ApiException(
          _messageFrom(body),
          businessCode: code,
        );
        if (exception.invalidatesSession) {
          _unauthorizedController.add(null);
        }
        throw exception;
      }

      return decode(body['data']);
    } on DioException catch (error) {
      final exception = ApiException(
        _dioMessage(error),
        statusCode: error.response?.statusCode,
      );
      if (exception.invalidatesSession) {
        _unauthorizedController.add(null);
      }
      throw exception;
    }
  }

  void dispose() {
    _unauthorizedController.close();
    _dio.close(force: true);
  }

  Future<Options> _options({int? houseId}) async {
    final session = await _sessionStore.readSession();
    final headers = <String, dynamic>{};
    final token = session.token;
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }
    if (houseId != null && houseId > 0) {
      headers['X-House-Id'] = '$houseId';
    }
    return Options(headers: headers);
  }

  static int? _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value);
    }
    return null;
  }

  static String _messageFrom(Map<dynamic, dynamic> body) {
    final message = body['message'] ?? body['msg'] ?? body['error'];
    if (message is String && message.trim().isNotEmpty) {
      return message.trim();
    }
    return '请求未完成，请稍后重试';
  }

  static String _dioMessage(DioException error) {
    final data = error.response?.data;
    if (data is Map) {
      final message = data['message'] ?? data['msg'] ?? data['error'];
      if (message is String && message.trim().isNotEmpty) {
        return message.trim();
      }
    }
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return '连接超时，请检查后端服务是否可访问';
      case DioExceptionType.badResponse:
        if (error.response?.statusCode == 401) {
          return '登录已失效，请重新登录';
        }
        return '服务返回 ${error.response?.statusCode ?? ''}，请稍后重试';
      case DioExceptionType.connectionError:
        return '无法连接后端服务，请确认地址和网络';
      case DioExceptionType.cancel:
        return '请求已取消';
      case DioExceptionType.badCertificate:
      case DioExceptionType.unknown:
        return error.message ?? '网络请求失败';
    }
  }
}
