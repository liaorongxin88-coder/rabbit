import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

void main() {
  test('app theme uses the system font and ergonomic weights', () {
    final theme = buildAppTheme();

    expect(theme.textTheme.bodyLarge?.fontFamily, isNot('NotoSansSC'));
    expect(theme.textTheme.headlineLarge?.fontWeight, FontWeight.w700);
    expect(theme.textTheme.titleMedium?.fontWeight, FontWeight.w600);
    expect(theme.textTheme.labelLarge?.fontWeight, FontWeight.w600);
    expect(theme.appBarTheme.titleTextStyle?.fontWeight, FontWeight.w700);
    expect(
      AppTypography.ergonomicTextScaler(const TextScaler.linear(2)).scale(10),
      20,
    );
    expect(
      AppTypography.ergonomicTextScaler(const TextScaler.linear(2.5)).scale(10),
      20,
    );
  });
}
