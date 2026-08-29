import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/operation_events/event.dart';

final operationEventsRepositoryProvider = Provider<OperationEventsRepository>(
  (ref) {
    return OperationEventsRepository(ref.watch(apiClientProvider));
  },
);

class OperationEventsRepository {
  OperationEventsRepository(this._api);

  final ApiClient _api;

  Future<OperationEventsPage> listOperationEvents({
    required int houseId,
    OperationEventsQuery query = const OperationEventsQuery(),
    CancelToken? cancelToken,
  }) {
    return _api.get<OperationEventsPage>(
      '/api/operation-events',
      houseId: houseId,
      query: query.toQueryParameters(),
      cancelToken: cancelToken,
      decode: (data) => OperationEventsPage.fromJson(
        requireJsonObject(data, message: '操作事件列表格式不正确'),
      ),
    );
  }
}
