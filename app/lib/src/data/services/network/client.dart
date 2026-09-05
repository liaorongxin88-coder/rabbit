import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/config/app.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';

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

class ProtectedDownloadResult {
  const ProtectedDownloadResult({
    required this.contentType,
    required this.contentDisposition,
  });

  final String? contentType;
  final String? contentDisposition;
}

class ApiClient {
  ApiClient(
    this._sessionStore, {
    Dio? dio,
    String? baseUrl,
    Future<String?> Function()? appBuildLoader,
  })  : _dio = dio ?? _buildDio(baseUrl ?? AppConfig.defaultBaseUrl),
        _appBuildLoader = appBuildLoader ?? _installedAppBuild;

  final SessionStore _sessionStore;
  final Dio _dio;
  final Future<String?> Function() _appBuildLoader;
  Future<String?>? _appBuild;
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

  Future<void> download(
    Uri uri,
    String savePath, {
    required void Function(int received, int total) onReceiveProgress,
    CancelToken? cancelToken,
  }) async {
    try {
      await _dio.downloadUri(
        uri,
        savePath,
        onReceiveProgress: onReceiveProgress,
        cancelToken: cancelToken,
      );
    } on DioException catch (error) {
      throw ApiException(
        _dioMessage(error),
        statusCode: error.response?.statusCode,
      );
    }
  }

  Future<ProtectedDownloadResult> downloadProtected(
    String path,
    String savePath, {
    required int houseId,
    void Function(int received, int total)? onReceiveProgress,
    CancelToken? cancelToken,
  }) async {
    final target = File(savePath);
    try {
      final options = await _options(houseId: houseId);
      final response = await _dio.download(
        path,
        savePath,
        options: options,
        onReceiveProgress: onReceiveProgress,
        cancelToken: cancelToken,
      );
      final result = ProtectedDownloadResult(
        contentType: response.headers.value(Headers.contentTypeHeader),
        contentDisposition: response.headers.value('content-disposition'),
      );
      if (_isJsonContentType(result.contentType)) {
        final body = await target.readAsString();
        final decoded = jsonDecode(body);
        if (decoded is Map) {
          final code = _intValue(decoded['code']);
          if (code != null && code != 0) {
            final exception = ApiException(
              _messageFrom(decoded),
              businessCode: code,
            );
            if (exception.invalidatesSession) {
              _unauthorizedController.add(null);
            }
            throw exception;
          }
        }
      }
      return result;
    } on ApiException {
      await _deleteDownload(target);
      rethrow;
    } on DioException catch (error) {
      await _deleteDownload(target);
      final exception = ApiException(
        _dioMessage(error),
        statusCode: error.response?.statusCode,
      );
      if (exception.invalidatesSession) {
        _unauthorizedController.add(null);
      }
      throw exception;
    } on FormatException {
      await _deleteDownload(target);
      throw const ApiException('服务返回的下载结果格式不正确');
    }
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

  Future<T> postMultipart<T>(
    String path, {
    required int houseId,
    required FormData body,
    required T Function(Object? data) decode,
  }) async {
    final options = await _options(houseId: houseId);
    options.contentType = Headers.multipartFormDataContentType;
    return _request(
      () => _dio.post<Object?>(
        path,
        data: body,
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
    final pendingAppBuild = _appBuild ??= _loadAppBuild();
    final appBuild = await pendingAppBuild;
    if ((appBuild == null || appBuild.trim().isEmpty) &&
        identical(_appBuild, pendingAppBuild)) {
      _appBuild = null;
    }
    headers['X-App-Build'] =
        appBuild?.trim().isNotEmpty == true ? appBuild!.trim() : 'UNKNOWN';
    return Options(headers: headers);
  }

  Future<String?> _loadAppBuild() async {
    try {
      return await _appBuildLoader();
    } catch (_) {
      return null;
    }
  }

  static Future<String?> _installedAppBuild() async {
    if (Platform.environment['FLUTTER_TEST'] == 'true') return null;
    try {
      final version =
          await const MethodChannelAppUpdateInstaller().currentVersion();
      return '${version.buildNumber}';
    } catch (_) {
      return null;
    }
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

  static bool _isJsonContentType(String? contentType) =>
      contentType?.toLowerCase().contains('application/json') == true;

  static Future<void> _deleteDownload(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {
      // Preserve the original transport or contract failure.
    }
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
