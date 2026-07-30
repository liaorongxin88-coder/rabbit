import 'package:flutter/material.dart';

class AppColors {
  static const background = Color(0xFFF7F8FA);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF17202A);
  static const muted = Color(0xFF667085);
  static const line = Color(0xFFE5E8EC);
  static const blue = Color(0xFF1577D2);
  static const red = Color(0xFFEF4444);
  static const green = Color(0xFF2F8F6B);
  static const amber = Color(0xFFF59E0B);
  static const softBlue = Color(0xFFEAF4FF);
  static const softGreen = Color(0xFFEAF7F1);
  static const softAmber = Color(0xFFFFF7E6);
  static const softRed = Color(0xFFFFEFEF);
}

class AppSpacing {
  static const pageHorizontal = 20.0;
  static const pageTop = 14.0;
  static const pageBottom = 24.0;

  static const pagePadding = EdgeInsets.fromLTRB(
    pageHorizontal,
    pageTop,
    pageHorizontal,
    pageBottom,
  );

  static const loginPagePadding = EdgeInsets.fromLTRB(
    pageHorizontal,
    24,
    pageHorizontal,
    pageBottom,
  );
}

@immutable
class AppPalette extends ThemeExtension<AppPalette> {
  const AppPalette({
    required this.background,
    required this.surface,
    required this.surfaceSubtle,
    required this.text,
    required this.muted,
    required this.line,
    required this.primary,
    required this.success,
    required this.warning,
    required this.danger,
    required this.primarySoft,
    required this.successSoft,
    required this.warningSoft,
    required this.dangerSoft,
    required this.shadow,
  });

  final Color background;
  final Color surface;
  final Color surfaceSubtle;
  final Color text;
  final Color muted;
  final Color line;
  final Color primary;
  final Color success;
  final Color warning;
  final Color danger;
  final Color primarySoft;
  final Color successSoft;
  final Color warningSoft;
  final Color dangerSoft;
  final Color shadow;

  static const light = AppPalette(
    background: AppColors.background,
    surface: AppColors.surface,
    surfaceSubtle: Color(0xFFF1F4F7),
    text: AppColors.ink,
    muted: AppColors.muted,
    line: AppColors.line,
    primary: AppColors.blue,
    success: AppColors.green,
    warning: AppColors.amber,
    danger: AppColors.red,
    primarySoft: AppColors.softBlue,
    successSoft: AppColors.softGreen,
    warningSoft: AppColors.softAmber,
    dangerSoft: AppColors.softRed,
    shadow: Color(0x14000000),
  );

  static const dark = AppPalette(
    background: Color(0xFF101713),
    surface: Color(0xFF17211D),
    surfaceSubtle: Color(0xFF1F2B26),
    text: Color(0xFFEFF6F2),
    muted: Color(0xFFA7B5AD),
    line: Color(0xFF304239),
    primary: Color(0xFF68B6FF),
    success: Color(0xFF58C994),
    warning: Color(0xFFF2B94B),
    danger: Color(0xFFFF7676),
    primarySoft: Color(0xFF12324A),
    successSoft: Color(0xFF12372B),
    warningSoft: Color(0xFF3A2D10),
    dangerSoft: Color(0xFF3D1D1F),
    shadow: Color(0x66000000),
  );

  static AppPalette of(BuildContext context) {
    return Theme.of(context).extension<AppPalette>() ?? light;
  }

  @override
  AppPalette copyWith({
    Color? background,
    Color? surface,
    Color? surfaceSubtle,
    Color? text,
    Color? muted,
    Color? line,
    Color? primary,
    Color? success,
    Color? warning,
    Color? danger,
    Color? primarySoft,
    Color? successSoft,
    Color? warningSoft,
    Color? dangerSoft,
    Color? shadow,
  }) {
    return AppPalette(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      surfaceSubtle: surfaceSubtle ?? this.surfaceSubtle,
      text: text ?? this.text,
      muted: muted ?? this.muted,
      line: line ?? this.line,
      primary: primary ?? this.primary,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      danger: danger ?? this.danger,
      primarySoft: primarySoft ?? this.primarySoft,
      successSoft: successSoft ?? this.successSoft,
      warningSoft: warningSoft ?? this.warningSoft,
      dangerSoft: dangerSoft ?? this.dangerSoft,
      shadow: shadow ?? this.shadow,
    );
  }

  @override
  AppPalette lerp(ThemeExtension<AppPalette>? other, double t) {
    if (other is! AppPalette) {
      return this;
    }
    return AppPalette(
      background: Color.lerp(background, other.background, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceSubtle: Color.lerp(surfaceSubtle, other.surfaceSubtle, t)!,
      text: Color.lerp(text, other.text, t)!,
      muted: Color.lerp(muted, other.muted, t)!,
      line: Color.lerp(line, other.line, t)!,
      primary: Color.lerp(primary, other.primary, t)!,
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      primarySoft: Color.lerp(primarySoft, other.primarySoft, t)!,
      successSoft: Color.lerp(successSoft, other.successSoft, t)!,
      warningSoft: Color.lerp(warningSoft, other.warningSoft, t)!,
      dangerSoft: Color.lerp(dangerSoft, other.dangerSoft, t)!,
      shadow: Color.lerp(shadow, other.shadow, t)!,
    );
  }
}

ThemeData buildAppTheme({Brightness brightness = Brightness.light}) {
  final isDark = brightness == Brightness.dark;
  final palette = isDark ? AppPalette.dark : AppPalette.light;
  final scheme = ColorScheme.fromSeed(
    seedColor: palette.primary,
    brightness: brightness,
    primary: palette.primary,
    secondary: palette.success,
    error: palette.danger,
    surface: palette.surface,
  ).copyWith(
    onSurface: palette.text,
    surfaceContainerHighest: palette.surfaceSubtle,
    outline: palette.line,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: palette.background,
    fontFamily: 'Roboto',
    extensions: [palette],
    textTheme: TextTheme(
      headlineLarge: TextStyle(
        color: palette.text,
        fontSize: 34,
        fontWeight: FontWeight.w800,
        height: 1.12,
      ),
      headlineMedium: TextStyle(
        color: palette.text,
        fontSize: 26,
        fontWeight: FontWeight.w800,
        height: 1.18,
      ),
      titleLarge: TextStyle(
        color: palette.text,
        fontSize: 20,
        fontWeight: FontWeight.w800,
      ),
      titleMedium: TextStyle(
        color: palette.text,
        fontSize: 16,
        fontWeight: FontWeight.w700,
      ),
      bodyLarge: TextStyle(
        color: palette.text,
        fontSize: 15,
        height: 1.45,
      ),
      bodyMedium: TextStyle(
        color: palette.muted,
        fontSize: 13,
        height: 1.4,
      ),
      labelLarge: TextStyle(
        color: palette.text,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: palette.background,
      surfaceTintColor: Colors.transparent,
      centerTitle: true,
      elevation: 0,
      titleTextStyle: TextStyle(
        color: palette.text,
        fontSize: 18,
        fontWeight: FontWeight.w800,
      ),
      iconTheme: IconThemeData(color: palette.text),
      actionsIconTheme: IconThemeData(color: palette.text),
    ),
    cardTheme: CardTheme(
      color: palette.surface,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: palette.line),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: palette.surface,
      labelStyle: TextStyle(color: palette.muted),
      hintStyle: TextStyle(color: palette.muted),
      prefixIconColor: palette.muted,
      suffixIconColor: palette.muted,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: palette.line),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: palette.line),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: palette.primary, width: 1.4),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: palette.danger),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: palette.danger, width: 1.4),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: palette.primary,
        foregroundColor: Colors.white,
        elevation: 0,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: palette.text,
        minimumSize: const Size.fromHeight(48),
        side: BorderSide(color: palette.line),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: palette.primary),
    ),
    iconTheme: IconThemeData(color: palette.text),
    listTileTheme: ListTileThemeData(
      iconColor: palette.muted,
      textColor: palette.text,
      titleTextStyle: TextStyle(
        color: palette.text,
        fontSize: 16,
        fontWeight: FontWeight.w700,
      ),
      subtitleTextStyle: TextStyle(
        color: palette.muted,
        fontSize: 13,
        height: 1.35,
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 74,
      backgroundColor: palette.surface,
      indicatorColor: palette.primarySoft,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          color: states.contains(WidgetState.selected)
              ? palette.primary
              : palette.muted,
        ),
      ),
      iconTheme: WidgetStateProperty.resolveWith(
        (states) => IconThemeData(
          color: states.contains(WidgetState.selected)
              ? palette.primary
              : palette.muted,
        ),
      ),
    ),
    dividerTheme: DividerThemeData(color: palette.line),
    tabBarTheme: TabBarTheme(
      labelColor: palette.text,
      unselectedLabelColor: palette.muted,
      indicatorColor: palette.danger,
      dividerColor: palette.line,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: palette.surface,
      modalBackgroundColor: palette.surface,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(8)),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: palette.surfaceSubtle,
      contentTextStyle: TextStyle(color: palette.text),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    ),
  );
}
