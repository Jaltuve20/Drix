# ─── Drix · reglas de R8 (versión release) ───
# Trazas legibles si algo falla
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Código de Drix que usa Android/Firebase por nombre (servicio de accesibilidad, modelos de datos)
-keep class com.drix.app.service.** { *; }
-keep class com.drix.app.data.** { *; }
-keep class com.drix.app.auth.** { *; }
-keep class com.drix.app.update.** { *; }

# Mapa (OpenStreetMap) e inicio de sesión con Google
-keep class org.osmdroid.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# Red de seguridad: no abortar el build por avisos de clases opcionales ausentes
-ignorewarnings
