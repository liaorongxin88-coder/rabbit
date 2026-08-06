import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final reportRepositoryProvider = Provider<ReportRepository>((ref) {
  return ReportRepository(ref.watch(apiClientProvider));
});

typedef DashboardQuery = ({int? houseId, int year});

final dashboardSummaryProvider = FutureProvider.autoDispose
    .family<DashboardSummary, DashboardQuery>((ref, query) {
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref.watch(reportRepositoryProvider).loadDashboardSummary(
        houseId: query.houseId,
        year: query.year,
        cancelToken: cancelToken,
      );
});

final currentHouseReportProvider = FutureProvider<DashboardReport>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    return DashboardReport.empty();
  }
  return ref.watch(reportRepositoryProvider).loadDashboard(houseId);
});

final houseReportProvider =
    FutureProvider.family<DashboardReport, int>((ref, houseId) async {
  if (houseId <= 0) {
    return DashboardReport.empty();
  }
  return ref.watch(reportRepositoryProvider).loadDashboard(houseId);
});

class DashboardReport {
  const DashboardReport({
    required this.feed,
    required this.breeding,
  });

  final FeedSummary feed;
  final BreedingSummary breeding;

  factory DashboardReport.empty() {
    return const DashboardReport(
      feed: FeedSummary(recordCount: 0, totalAmount: 0),
      breeding: BreedingSummary(
        totalLitters: 0,
        totalKits: 0,
        totalLiveKits: 0,
        totalWeaned: 0,
        successBreedingCount: 0,
        failedBreedingCount: 0,
      ),
    );
  }

  factory DashboardReport.sum(Iterable<DashboardReport> reports) {
    var feedRecordCount = 0;
    var feedTotalAmount = 0.0;
    var totalLitters = 0;
    var totalKits = 0;
    var totalLiveKits = 0;
    var totalWeaned = 0;
    var successBreedingCount = 0;
    var failedBreedingCount = 0;

    for (final report in reports) {
      feedRecordCount += report.feed.recordCount;
      feedTotalAmount += report.feed.totalAmount;
      totalLitters += report.breeding.totalLitters;
      totalKits += report.breeding.totalKits;
      totalLiveKits += report.breeding.totalLiveKits;
      totalWeaned += report.breeding.totalWeaned;
      successBreedingCount += report.breeding.successBreedingCount;
      failedBreedingCount += report.breeding.failedBreedingCount;
    }

    return DashboardReport(
      feed: FeedSummary(
        recordCount: feedRecordCount,
        totalAmount: feedTotalAmount,
      ),
      breeding: BreedingSummary(
        totalLitters: totalLitters,
        totalKits: totalKits,
        totalLiveKits: totalLiveKits,
        totalWeaned: totalWeaned,
        successBreedingCount: successBreedingCount,
        failedBreedingCount: failedBreedingCount,
      ),
    );
  }
}

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
