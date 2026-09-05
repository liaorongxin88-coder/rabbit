import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/statistics.dart';

import 'statistics_fixture.dart';

void main() {
  test('parses the independently frozen schema version 1 contract', () {
    final statistics = BatchStatistics.fromJson(testStatisticsPayload());

    expect(statistics.schemaVersion, 1);
    expect(statistics.batchId, 11);
    expect(statistics.houseName, '一号兔舍');
    expect(statistics.batchCode, 'STATS-11');
    expect(statistics.metrics, hasLength(28));
    expect(
      statistics.metrics.map((metric) => metric.code),
      testBatchMetricCodes,
    );
    for (var index = 0; index < testBatchMetricContracts.length; index++) {
      final expected = testBatchMetricContracts[index];
      final actual = statistics.metrics[index];
      expect(actual.code, expected.code);
      expect(actual.order, expected.order, reason: expected.code);
      expect(actual.stage, expected.stage, reason: expected.code);
      expect(actual.stageName, expected.stageName, reason: expected.code);
      expect(actual.name, expected.name, reason: expected.code);
      expect(
        actual.excelColumnName,
        expected.excelColumnName,
        reason: expected.code,
      );
      expect(actual.valueType.name, _valueTypeName(expected.valueType));
      expect(actual.unit, expected.unit, reason: expected.code);
      expect(actual.format, expected.format, reason: expected.code);
    }
    expect(statistics.totalLitters, 3);
    expect(statistics.totalKits, 28);
    expect(statistics.totalLiveKits, 26);
    expect(statistics.totalWeaned, 22);

    final rate = statistics.metricsByCode['CONCEPTION_RATE']!;
    expect(rate.visibleValue, '86.10%');
    expect(rate.formula, '确认怀孕周期数 / 已配种周期数');
    expect(rate.numerator?.value, 1059);
    expect(rate.denominator?.value, 1230);
    expect(rate.components.single.code, 'PREGNANT_CYCLES');
  });

  test('parses date ranges and every ordered missing cause', () {
    final payload = testStatisticsPayload();
    final metrics = payload['metrics']! as List<Map<String, Object?>>;
    final soldWeight = metrics[20];
    soldWeight
      ..['status'] = 'DATA_MISSING'
      ..['numericValue'] = null
      ..['displayValue'] = null
      ..['missingCauses'] = [
        {'code': 'MISSING_BATCH_SALE_ALLOCATION', 'message': '缺少批次重量'},
        {'code': 'MISSING_SALE_UNIT_PRICE', 'message': '缺少统一单价'},
      ];

    final statistics = BatchStatistics.fromJson(payload);
    expect(statistics.metrics.first.dateValue?.dateCount, 2);
    expect(
      statistics.metrics.first.dateValue?.dailyCycleCounts.last.cycleCount,
      3,
    );
    expect(
      statistics.metricsByCode['SOLD_WEIGHT']!.missingCauses
          .map((cause) => cause.code),
      ['MISSING_BATCH_SALE_ALLOCATION', 'MISSING_SALE_UNIT_PRICE'],
    );
  });

  test('ignores unknown extra metric codes for forward compatibility', () {
    final payload = testStatisticsPayload();
    final metrics = payload['metrics']! as List<Map<String, Object?>>;
    metrics.insert(1, {
      'code': 'FUTURE_METRIC',
      'status': 'FUTURE_STATUS',
      'valueType': 'FUTURE_TYPE',
    });

    final statistics = BatchStatistics.fromJson(payload);

    expect(statistics.metrics, hasLength(28));
    expect(
        statistics.metrics.map((metric) => metric.code), testBatchMetricCodes);
  });

  test('rejects missing or duplicate fixed metrics', () {
    final missingPayload = testStatisticsPayload();
    final missingMetrics =
        missingPayload['metrics']! as List<Map<String, Object?>>;
    missingMetrics.removeWhere(
      (metric) => metric['code'] == 'CARCASS_YIELD_RATE',
    );
    expect(
      () => BatchStatistics.fromJson(missingPayload),
      throwsA(isA<FormatException>()),
    );

    final duplicatePayload = testStatisticsPayload();
    final duplicateMetrics =
        duplicatePayload['metrics']! as List<Map<String, Object?>>;
    duplicateMetrics.add(Map<String, Object?>.from(duplicateMetrics.first));
    expect(
      () => BatchStatistics.fromJson(duplicatePayload),
      throwsA(isA<FormatException>()),
    );
  });

  test('rejects fixed metrics returned out of order', () {
    final payload = testStatisticsPayload();
    final metrics = payload['metrics']! as List<Map<String, Object?>>;
    final first = metrics.removeAt(0);
    metrics.insert(1, first);

    expect(
      () => BatchStatistics.fromJson(payload),
      throwsA(isA<FormatException>()),
    );
  });

  test('rejects unknown status and value type values on fixed metrics', () {
    final statusPayload = testStatisticsPayload();
    (statusPayload['metrics']! as List<Map<String, Object?>>)[1]['status'] =
        'PARTIAL';
    expect(
      () => BatchStatistics.fromJson(statusPayload),
      throwsA(isA<FormatException>()),
    );

    final typePayload = testStatisticsPayload();
    (typePayload['metrics']! as List<Map<String, Object?>>)[1]['valueType'] =
        'TEXT';
    expect(
      () => BatchStatistics.fromJson(typePayload),
      throwsA(isA<FormatException>()),
    );
  });

  for (final drift in <({String field, Object value})>[
    (field: 'order', value: 999),
    (field: 'stage', value: 'BIRTH'),
    (field: 'stageName', value: '产崽'),
    (field: 'name', value: '旧指标名'),
    (field: 'excelColumnName', value: '旧表头'),
    (field: 'unit', value: 'KG'),
    (field: 'format', value: 'DECIMAL_2'),
  ]) {
    test('rejects fixed metadata drift in ${drift.field}', () {
      final payload = testStatisticsPayload();
      (payload['metrics']! as List<Map<String, Object?>>)[1][drift.field] =
          drift.value;

      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
      );
    });
  }

  test('rejects date and numeric values on the wrong metric type', () {
    final numberPayload = testStatisticsPayload();
    (numberPayload['metrics']! as List<Map<String, Object?>>)[1]
        ['dateValue'] = {
      'firstDate': '2024-04-22',
      'lastDate': '2024-04-22',
      'dateCount': 1,
      'dailyCycleCounts': [
        {'date': '2024-04-22', 'cycleCount': 1},
      ],
    };
    expect(
      () => BatchStatistics.fromJson(numberPayload),
      throwsA(isA<FormatException>()),
    );

    final datePayload = testStatisticsPayload();
    (datePayload['metrics']! as List<Map<String, Object?>>)
        .first['numericValue'] = 1;
    expect(
      () => BatchStatistics.fromJson(datePayload),
      throwsA(isA<FormatException>()),
    );
  });

  for (final field in const [
    'numericValue',
    'dateValue',
    'displayValue',
    'numerator',
    'denominator',
    'components',
    'missingCauses',
  ]) {
    test('rejects a fixed metric with missing nullable key $field', () {
      final payload = testStatisticsPayload();
      (payload['metrics']! as List<Map<String, Object?>>)[1].remove(field);

      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
      );
    });
  }

  test('requires formula to be present and string typed', () {
    for (final mutate in <void Function(Map<String, Object?>)>[
      (metric) => metric.remove('formula'),
      (metric) => metric['formula'] = 42,
    ]) {
      final payload = testStatisticsPayload();
      final metric = (payload['metrics']! as List<Map<String, Object?>>)[1];
      mutate(metric);
      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
      );
    }
  });

  test('rejects fixed formula drift and invalid missing-cause states', () {
    final formulaPayload = testStatisticsPayload();
    (formulaPayload['metrics']! as List<Map<String, Object?>>)[1]['formula'] =
        '错误口径';
    expect(
      () => BatchStatistics.fromJson(formulaPayload),
      throwsA(isA<FormatException>()),
    );

    final availableCausePayload = testStatisticsPayload();
    (availableCausePayload['metrics']! as List<Map<String, Object?>>)[1]
        ['missingCauses'] = [
      {'code': 'MISSING_WEANING_WEIGHT', 'message': '错误原因'},
    ];
    expect(
      () => BatchStatistics.fromJson(availableCausePayload),
      throwsA(isA<FormatException>()),
    );

    final missingWithoutCausePayload = testStatisticsPayload();
    final missing = (missingWithoutCausePayload['metrics']!
        as List<Map<String, Object?>>)[20];
    missing
      ..['status'] = 'DATA_MISSING'
      ..['numericValue'] = null
      ..['displayValue'] = null
      ..['missingCauses'] = <Map<String, Object?>>[];
    expect(
      () => BatchStatistics.fromJson(missingWithoutCausePayload),
      throwsA(isA<FormatException>()),
    );

    final unorderedPayload = testStatisticsPayload();
    final unordered =
        (unorderedPayload['metrics']! as List<Map<String, Object?>>)[25];
    unordered
      ..['status'] = 'DATA_MISSING'
      ..['numericValue'] = null
      ..['displayValue'] = null
      ..['missingCauses'] = [
        {'code': 'MISSING_FEED_ALLOCATION', 'message': '饲料缺失'},
        {'code': 'MISSING_BATCH_SALE_ALLOCATION', 'message': '销售缺失'},
      ];
    expect(
      () => BatchStatistics.fromJson(unorderedPayload),
      throwsA(isA<FormatException>()),
    );
  });

  test('requires a timezone-qualified UTC calculatedAt', () {
    for (final value in const [
      '2026-09-05T08:30:00',
      '2026-09-05T16:30:00+08:00',
      '2026-09-05',
      'not-a-date',
    ]) {
      final payload = testStatisticsPayload()..['calculatedAt'] = value;
      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
        reason: value,
      );
    }

    final payload = testStatisticsPayload()
      ..['calculatedAt'] = '2026-09-05T08:30:00+00:00';
    expect(BatchStatistics.fromJson(payload).calculatedAt.isUtc, isTrue);
  });

  test('rejects NaN and infinity from every numeric parser position', () {
    for (final invalid in [double.nan, double.infinity]) {
      for (final field in const [
        'schemaVersion',
        'batchId',
        'totalLitters',
        'totalKits',
        'totalLiveKits',
        'totalWeaned',
      ]) {
        final payload = testStatisticsPayload()..[field] = invalid;
        expect(
          () => BatchStatistics.fromJson(payload),
          throwsA(isA<FormatException>()),
          reason: '$field=$invalid',
        );
      }

      final orderPayload = testStatisticsPayload();
      (orderPayload['metrics']! as List<Map<String, Object?>>)[1]['order'] =
          invalid;
      expect(
        () => BatchStatistics.fromJson(orderPayload),
        throwsA(isA<FormatException>()),
      );

      final numericPayload = testStatisticsPayload();
      (numericPayload['metrics']! as List<Map<String, Object?>>)[1]
          ['numericValue'] = invalid;
      expect(
        () => BatchStatistics.fromJson(numericPayload),
        throwsA(isA<FormatException>()),
      );

      for (final field in const ['numerator', 'denominator']) {
        final operandPayload = testStatisticsPayload();
        final metric =
            (operandPayload['metrics']! as List<Map<String, Object?>>)[2];
        (metric[field]! as Map<String, Object?>)['value'] = invalid;
        expect(
          () => BatchStatistics.fromJson(operandPayload),
          throwsA(isA<FormatException>()),
          reason: '$field.value=$invalid',
        );
      }

      final componentPayload = testStatisticsPayload();
      final componentMetric =
          (componentPayload['metrics']! as List<Map<String, Object?>>)[2];
      ((componentMetric['components']! as List<Map<String, Object?>>)
          .first)['value'] = invalid;
      expect(
        () => BatchStatistics.fromJson(componentPayload),
        throwsA(isA<FormatException>()),
      );

      final dateCountPayload = testStatisticsPayload();
      final dateMetric =
          (dateCountPayload['metrics']! as List<Map<String, Object?>>).first;
      final dateValue = dateMetric['dateValue']! as Map<String, Object?>;
      dateValue['dateCount'] = invalid;
      expect(
        () => BatchStatistics.fromJson(dateCountPayload),
        throwsA(isA<FormatException>()),
      );

      final cycleCountPayload = testStatisticsPayload();
      final cycleMetric =
          (cycleCountPayload['metrics']! as List<Map<String, Object?>>).first;
      final cycleDate = cycleMetric['dateValue']! as Map<String, Object?>;
      ((cycleDate['dailyCycleCounts']! as List<Map<String, Object?>>)
          .first)['cycleCount'] = invalid;
      expect(
        () => BatchStatistics.fromJson(cycleCountPayload),
        throwsA(isA<FormatException>()),
      );
    }
  });

  test('enforces numeric format and status value invariants', () {
    final integerPayload = testStatisticsPayload();
    (integerPayload['metrics']! as List<Map<String, Object?>>)[1]
        ['numericValue'] = 1.5;
    expect(
      () => BatchStatistics.fromJson(integerPayload),
      throwsA(isA<FormatException>()),
    );

    final percentPayload = testStatisticsPayload();
    (percentPayload['metrics']! as List<Map<String, Object?>>)[2]
        ['numericValue'] = 1.01;
    expect(
      () => BatchStatistics.fromJson(percentPayload),
      throwsA(isA<FormatException>()),
    );

    final unavailablePayload = testStatisticsPayload();
    final unavailable =
        (unavailablePayload['metrics']! as List<Map<String, Object?>>)[1];
    unavailable
      ..['status'] = 'DATA_MISSING'
      ..['displayValue'] = null;
    expect(
      () => BatchStatistics.fromJson(unavailablePayload),
      throwsA(isA<FormatException>()),
    );

    final availablePayload = testStatisticsPayload();
    (availablePayload['metrics']! as List<Map<String, Object?>>)[1]
        ['numericValue'] = null;
    expect(
      () => BatchStatistics.fromJson(availablePayload),
      throwsA(isA<FormatException>()),
    );
  });

  test('rejects negative compatibility totals and available metric values', () {
    for (final field in const [
      'totalLitters',
      'totalKits',
      'totalLiveKits',
      'totalWeaned',
    ]) {
      final payload = testStatisticsPayload()..[field] = -1;
      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
        reason: field,
      );
    }

    for (final index in const [1, 20, 25]) {
      final payload = testStatisticsPayload();
      (payload['metrics']! as List<Map<String, Object?>>)[index]
          ['numericValue'] = -0.01;
      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
        reason: 'metric index $index',
      );
    }
  });

  test('rejects malformed mating date invariants', () {
    final mutations = <void Function(Map<String, Object?>)>[
      (date) => date['firstDate'] = '2024-4-22',
      (date) => date['firstDate'] = '2024-02-30',
      (date) => date['dateCount'] = 3,
      (date) => date['lastDate'] = '2024-04-24',
      (date) => date['dailyCycleCounts'] = [
            {'date': '2024-04-22', 'cycleCount': 2},
            {'date': '2024-04-22', 'cycleCount': 3},
          ],
      (date) => date['dailyCycleCounts'] = [
            {'date': '2024-04-23', 'cycleCount': 3},
            {'date': '2024-04-22', 'cycleCount': 2},
          ],
      (date) => date['dailyCycleCounts'] = [
            {'date': '2024-04-22', 'cycleCount': 0},
            {'date': '2024-04-23', 'cycleCount': 3},
          ],
    ];

    for (final mutate in mutations) {
      final payload = testStatisticsPayload();
      final metric = (payload['metrics']! as List<Map<String, Object?>>).first;
      final date = metric['dateValue']! as Map<String, Object?>;
      mutate(date);
      expect(
        () => BatchStatistics.fromJson(payload),
        throwsA(isA<FormatException>()),
      );
    }
  });
}

String _valueTypeName(String wire) => switch (wire) {
      'NUMBER' => 'number',
      'DATE_RANGE' => 'dateRange',
      _ => throw StateError('Unexpected test value type $wire'),
    };
