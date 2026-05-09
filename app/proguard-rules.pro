# Configuração de ProGuard/R8 para PDFShield
# Objetivo: Minificação, obfuscação de strings e remoção de debug symbols
#
# Documentação: https://developer.android.com/build/shrink-code

# ============================================================
# MANUTENÇÃO: Classes essenciais do Android que não devem ser obfuscadas
# ============================================================

# Manter Activities, Services, Broadcast Receivers, Providers (Android exige nomes)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# Manter handlers (usados em intents)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Manter construtores padrão (Android reflection)
-keepclassmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ============================================================
# ROOM DATABASE: Manter entities e DAOs (reflection)
# ============================================================
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ============================================================
# CRIPTOGRAFIA: Manter classes de segurança
# ============================================================
-keep class org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils
-keep class org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils
-keep class org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils
-keepclassmembers class org.ferreiratechlab.leitordepdfseguro.utils.* {
    public <methods>;
    public <fields>;
}

# ============================================================
# SEGURANÇA: Obfuscação de strings
# ============================================================
# R8 automaticamente obfusca strings quando minifyEnabled=true
# Remover chamadas de debug logging
-assumenosideeffects class android.util.Log {
    public *** d(...);
    public *** v(...);
    public *** i(...);
}

# ============================================================
# OTIMIZAÇÃO: Remover debug symbols
# ============================================================
# NÃO manter informações de linha (protege stack traces)
# -keepattributes SourceFile,LineNumberTable é COMENTADO por design

# Se absolutamente necessário para crash reporting (ex: Firebase Crashlytics),
# descomente abaixo E use Proguard mapping file no servidor:
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# ============================================================
# COMPATIBILIDADE: AndroidX
# ============================================================
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# ============================================================
# BIBLIOTECAS EXTERNAS
# ============================================================

# PDFBox
-dontwarn org.apache.pdfbox.**
-keep class org.apache.pdfbox.** { *; }

# AndroidPdfViewer
-dontwarn com.github.barteksc.pdfviewer.**
-keep class com.github.barteksc.pdfviewer.** { *; }

# PdfiumAndroid / classes carregadas em runtime pelo viewer
-dontwarn com.shockwave.pdfium.**
-keep class com.shockwave.pdfium.** { *; }

# Regras geradas automaticamente pelo R8 para dependências transitivas opcionais
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.GeneralPath
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D$Float
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D
-dontwarn javax.annotation.concurrent.GuardedBy

# ============================================================
# VERBOSE (debug purpose only; remove in production)
# ============================================================
# Descomente para ver detalhes da minificação:
# -verbose
# -printmapping mapping.txt