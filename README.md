# Drix v1.1.0
Proyecto completo (abre esta carpeta tal cual; no hace falta crear otro proyecto).

## Compilar sin Android Studio
- Nube: sube esta carpeta a GitHub > pestaña Actions > "Build APK" > descarga el artefacto Drix-apk.
- Local (VS Code): JDK 17 + Android cmdline-tools (ANDROID_HOME) + Gradle 8.9, luego `gradle assembleDebug`
  y `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Primer uso
Abre Drix > "Elegir app instalada" > activa el interruptor > concede "Mostrar sobre otras apps" y Accesibilidad.
Si el botón verde «Aceptar» no se pulsa, copia el texto de Extras > Diagnóstico para ajustar el lector.
