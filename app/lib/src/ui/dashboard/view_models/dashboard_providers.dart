import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/report_repository.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

typedef DashboardQuery = ({int? houseId, int year});

final dashboardSummaryProvider = FutureProvider.autoDispose
    .family<DashboardSummary, DashboardQuery>((ref, query) {
  ref.watch(authenticatedUserIdProvider);
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
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return DashboardReport.empty();
  }
  return ref.watch(reportRepositoryProvider).loadDashboard(houseId);
});
