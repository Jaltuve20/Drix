# Drix v1.5.0

**Novedades 1.5.0**
- **Aviso flotante nuevo** (tarjeta oscura con logo y estado en color). Desaparece al pausar la automatización y al salir de la app de pedidos.
- **Icono de la app** = tu logo (adaptativo, también en iconos temáticos de Android 13+).
- **Registro**: nombres + apellidos + celular. Si entran con Google (o su cuenta no tiene perfil) se les pide completar los datos antes de usar la app; luego **ya no se pueden cambiar** (lo exigen las reglas de Firestore → **vuelve a publicarlas**, paso 5). Cambios solo por WhatsApp.
- **Tutorial de bienvenida** (parámetros y zonas) que ven **solo las cuentas nuevas**, una vez.

# (versión anterior) amarillo + login con Firebase

Proyecto Android completo (Kotlin + Jetpack Compose). Abre esta carpeta tal cual.

Lo nuevo en esta versión:
- Logo nuevo: la **D del logo** (con las líneas de velocidad) reemplaza la D de «Drix» → **[D]rix**.
- Pantallas: **Splash → Iniciar sesión → Crear cuenta → App**. La cuenta se ve en la pestaña **Perfil**.
- **Firebase Authentication** (correo + contraseña, y «Continuar con Google») y **Firestore** (guarda nombre y teléfono).
- Tema amarillo/negro.

> ⚠️ **La app NO compila hasta que hagas los pasos 1 a 4**: falta tu archivo `google-services.json`, que solo tú puedes generar.

---

## PASO A PASO

### 1. Crear el proyecto en Firebase
1. Entra a https://console.firebase.google.com con tu cuenta de Google.
2. **Agregar proyecto** → ponle nombre (ej. `drix`) → puedes desactivar Google Analytics → **Crear proyecto**.

### 2. Registrar la app Android
1. En la pantalla principal del proyecto toca el icono de Android (**Agregar app**).
2. **Nombre del paquete**: `com.drix.app` (debe ser exactamente ese).
3. **Apodo**: `Drix`.
4. **Certificado SHA-1** (necesario para «Continuar con Google»). Pega este, que es el de la llave incluida en el proyecto (`app/drix.keystore`):

   ```
   89:94:D8:C6:43:62:84:DF:FB:06:3A:E1:4F:A5:87:F1:99:8A:64:FB
   ```
5. **Registrar app**.

### 3. Descargar `google-services.json`
1. En el siguiente paso Firebase te ofrece **Descargar google-services.json**. Descárgalo.
2. Copia el archivo a **`app/google-services.json`** (dentro de la carpeta `app`, junto a `build.gradle.kts`).
3. Los demás pasos que muestra Firebase (agregar plugins/SDK) **ya están hechos** en este proyecto; solo pulsa **Siguiente** hasta terminar.

### 4. Activar el inicio de sesión
1. En Firebase: **Compilación → Authentication → Comenzar**.
2. Pestaña **Método de acceso**:
   - **Correo electrónico/contraseña** → activar → Guardar.
   - **Google** → activar → elige un correo de asistencia → Guardar.
3. ⚠️ **Después de activar Google, vuelve a descargar `google-services.json`** (Configuración del proyecto ⚙️ → Tus apps → Drix → descargar) y reemplaza el que ya tenías en `app/`. El nuevo incluye el «client id web» que necesita el botón de Google. Sin esto el botón de Google muestra «Falta activar Google…».

### 5. Activar Firestore (guarda nombre y teléfono)
1. **Compilación → Firestore Database → Crear base de datos**.
2. Elige ubicación (ej. `southamerica-east1`) y **modo de producción**.
3. Pestaña **Reglas** → reemplaza todo por esto y pulsa **Publicar**:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {

       function validName(s) { return s is string && s.size() >= 2 && s.size() <= 60; }
       function validData(d) {
         return d.keys().hasOnly(['firstName', 'lastName', 'name', 'phone', 'email', 'tutorialDone', 'createdAt'])
           && validName(d.firstName) && validName(d.lastName)
           && d.phone is string && d.phone.matches('^\+[0-9]{9,15}$');
       }
       function complete(d) {
         return d.get('firstName', '') != '' && d.get('lastName', '') != '' && d.get('phone', '') != '';
       }

       match /users/{uid} {
         allow read: if request.auth != null && request.auth.uid == uid;
         allow create: if request.auth != null && request.auth.uid == uid && validData(request.resource.data);
         // Nombres y teléfono se pueden escribir UNA vez (mientras el perfil está incompleto).
         // Después solo se permite marcar el tutorial como visto. Cambios de datos: por WhatsApp (consola de Firebase).
         allow update: if request.auth != null && request.auth.uid == uid && (
             (!complete(resource.data) && validData(request.resource.data))
             || (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['tutorialDone'])
                 && request.resource.data.tutorialDone == true));
         allow delete: if false;
       }
     }
   }
   ```
   (Cada usuario solo lee su propio perfil. **Nombres y teléfono se guardan una vez y ya no se pueden cambiar desde la app**; si alguien pide un cambio por WhatsApp, edítalo tú en Firestore → users → su documento. Este mismo texto está en `firestore.rules`.)

> Si te saltas el paso 5, el registro funciona igual, pero el teléfono no se guardará.

### 6. Compilar el APK
**Opción nube (la tuya, sin Android Studio):**
1. Sube TODA la carpeta (con `app/google-services.json` y `app/drix.keystore`) a tu repositorio de GitHub.
2. Pestaña **Actions → Build APK → Run workflow**.
3. Descarga el artefacto **Drix-apk**, instala el APK en tu celular.

**Opción local:** JDK 17 + Android cmdline-tools + Gradle 8.9 → `gradle assembleDebug` y `adb install app/build/outputs/apk/debug/app-debug.apk`.

### 7. Probar
1. Abre la app: verás el splash y luego **Iniciar sesión**.
2. Toca **Regístrate** → llena nombre, correo, teléfono y contraseña → **Registrarse**.
3. Entras a la app; en **Perfil** aparecen tu nombre, correo y teléfono y puedes **editarlos y guardarlos**. **Cambiar contraseña** te envía un enlace al correo. **Cerrar sesión** te devuelve al login.
4. En Firebase → Authentication → Usuarios verás tu cuenta; en Firestore → `users` verás tu perfil.

---

## Notas importantes
- **Llave de firma (`app/drix.keystore`)**: es una llave de *pruebas* incluida para que el SHA-1 sea siempre el mismo (si no, GitHub Actions genera una distinta en cada compilación y Google deja de funcionar). Contraseña: `drixdebug`, alias `drix`. **No la uses para publicar en Play Store**; para eso crea una llave propia y privada.
- **Si usas otro SHA-1** (otra llave): Firebase → Configuración del proyecto → Tus apps → **Agregar huella digital**, y vuelve a descargar `google-services.json`.
- **Login con teléfono/SMS y Apple**: no incluidos (Apple no existe en Android; SMS requiere activar facturación en Firebase). El campo «Correo o teléfono» del diseño es solo correo.
- **Recuperar contraseña**: escribe tu correo en el login y toca «¿Olvidaste tu contraseña?»; Firebase envía el enlace por correo.

## Errores comunes
| Mensaje | Solución |
|---|---|
| `File google-services.json is missing` al compilar | Falta el paso 3 (archivo en `app/`). |
| Google: «Falta activar Google en Firebase…» | Paso 4.3: reactivar Google y volver a descargar el json. |
| Google: «No credentials available» / error 10 | El SHA-1 del paso 2 no coincide, o el celular no tiene cuenta de Google. |
| «Correo o contraseña incorrectos» | Revisa los datos; usa «Olvidaste tu contraseña». |
| Registro OK pero no aparece el teléfono | Falta el paso 5 (Firestore y reglas). |

## Primer uso de la automatización
Abre Drix > Inicio > «Elegir app instalada» > activa el interruptor > concede «Mostrar sobre otras apps» y Accesibilidad.
Si el botón verde «Aceptar» no se pulsa, copia el texto de Perfil > Diagnóstico para ajustar el lector.
