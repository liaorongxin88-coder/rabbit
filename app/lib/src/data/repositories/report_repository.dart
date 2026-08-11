import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';

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
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('数据面板格式不正确');
        }
        return DashboardSummary.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<DashboardReport> loadDashboard(int houseId) async {
    final feed = await _api.get<FeedSummary>(
      '/api/reports/feed-summary',
      houseId: houseId,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('投喂汇总格式不正确');
        }
        return FeedSummary.fromJson(Map<String, dynamic>.from(data));
      },
    );
    final breeding = await _api.get<BreedingSummary>(
      '/api/reports/breeding-summary',
      houseId: houseId,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('繁殖汇总格式不正确');
        }
        return BreedingSummary.fromJson(Map<String, dynamic>.from(data));
      },
    );
    return DashboardReport(feed: feed, breeding: breeding);
  }
}
