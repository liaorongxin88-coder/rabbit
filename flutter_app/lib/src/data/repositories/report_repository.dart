import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final reportRepositoryProvider = Provider<ReportRepository>((ref) {
  return ReportRepository(ref.watch(apiClientProvider));
});

final currentHouseReportProvider = FutureProvider<DashboardReport>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
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
}

class ReportRepository {
  ReportRepository(this._api);

  final ApiClient _api;

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
