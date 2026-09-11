# Add project specific ProGuard rules here.
# 在此处添加项目特定的 ProGuard 规则。

# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
# 您可以通过 build.gradle 文件中的 proguardFiles 配置项，
# 来控制实际应用的规则文件集合。

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
# 更多详细信息，请参阅官方文档：
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
# 如果您的项目中使用了 WebView 并与 JavaScript 进行交互，
# 请取消下面规则的注释，并将 "fqcn.of.javascript.interface.for.webview"
# 替换为您实际定义的 JavaScript 接口类的完整限定名。
#
# -keepclassmembers class fqcn.of.javascript.interface.for.webview {
#    public *;
# }
#
# 作用说明：
# 避免 WebView 的 JS 接口类中的 public 方法因未被 Java 代码显式调用
# 而被混淆移除，导致 H5 页面调用 Native 方法失败。

# Uncomment this to preserve the line number information for
# debugging stack traces.
# 取消下面这行的注释，可以在混淆后的 APK 中保留源代码文件名和行号信息，
# 便于分析线上崩溃日志。
# -keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# 如果您保留了行号信息，但希望隐藏原始源文件的真实名称，
# 可以取消下面这行的注释，ProGuard 会将所有源文件名统一替换为 "SourceFile"，
# 以增加逆向分析难度。
# -renamesourcefileattribute SourceFile