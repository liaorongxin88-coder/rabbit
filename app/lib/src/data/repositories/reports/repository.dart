import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/reports/dashboard.dart';

final reportRepositoryProvider = Provider<ReportRepository>((ref) {
  return ReportRepository(ref.watch(apiClientProvider));
});

class ReportRepository {
  ReportRepository(this._api);

  final ApiClient _api;

  Future<DashboardSummary> loadDashboardSummary({
    required int? houseId,
    required int year,
    CancelToken? cancelToken,
  }) {
    return _api.get<DashboardSummary>(
      '/api/reports/dashboard',
      query: {
        if (houseId != null && houseId > 0) 'houseId': houseId,
        'year': year,
      },
      cancelToken: cancelToken,
      decode: (data) => DashboardSummary.fromJson(
        requireJsonObject(data, message: '数据面板格式不正确'),
      ),
    );
  }

  Future<DashboardReport> loadDashboard(int houseId) async {
    final feed = await _api.get<FeedSummary>(
      '/api/reports/feed-summary',
      houseId: houseId,
      decode: (data) => FeedSummary.fromJson(
        requireJsonObject(data, message: '投喂汇总格式不正确'),
      ),
    );
    final breeding = await _api.get<BreedingSummary>(
      '/api/reports/breeding-summary',
      houseId: houseId,
      decode: (data) => BreedingSummary.fromJson(
        requireJsonObject(data, message: '繁殖汇总格式不正确'),
      ),
    );
    return DashboardReport(feed: feed, breeding: breeding);
  }
}
