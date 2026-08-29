# Flutter plugins bundled by the app provide their own consumer rules. Keep this
# file for Rabbit-specific rules discovered by release smoke tests.

# 1.0.8 真机故障：release 包点更新，下载完成后报「当前设备不支持应用更新」。
# 堆栈是 FileProvider.getUriForFile -> parsePathStrategy -> XmlBlock$Parser.setInput，
# 抛 XmlPullParserException: setInput() not supported。debug 包同一路径正常，
# 差异只有 R8。XmlPullParserException 是受检异常，Flutter 的 MethodChannel 只
# catch RuntimeException，于是异常逃到 DartMessenger，它回了个 null，
# Dart 侧把 null 回包当成 MissingPluginException，最后显示成「设备不支持」。
#
# FileProvider 解析清单元数据的那段代码经不起 R8 的优化，这里整类保留。
-keep class androidx.core.content.FileProvider { *; }
-keep class * extends androidx.core.content.FileProvider { *; }

# 同一类问题：解析 XML 元数据依赖 XmlPullParser 的接口形状，别让 R8 动它。
-keep interface org.xmlpull.v1.** { *; }
-keep class org.xmlpull.v1.** { *; }
