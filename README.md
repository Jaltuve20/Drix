# Drix v1.7.0 — pago único + sesión única

# (anterior) Drix v1.5.0

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

   Copia TODO el contenido del archivo `firestore.rules` de esta carpeta y pégalo ahí (incluye el control de acceso, ver abajo).
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

## Novedades v1.8.0
- **Botón flotante:** un toque activa/pausa Drix; mantenlo pulsado para «Abrir Drix» o ✕ ocultarlo. Se vuelve a mostrar en Perfil → «Mostrar botón flotante».
- **Top Drix del mes (panel admin):** pedidos aceptados por cada cliente en el mes, ordenados de mayor a menor, con selector de mes y el total por cliente. Se guarda en la colección `monthly` (**vuelve a publicar `firestore.rules`**).
- **Recordatorios cada hora** (solo con Drix pausado, dentro del horario del cliente, se borran solos a los 20 min, con botón «Activar ahora»). El cliente puede apagarlos en Perfil. Los textos están en `notify/Nudges.kt`.
- **Tutorial** con una intro animada y una página nueva del botón flotante.

## Control de acceso (pago único, de por vida) y sesión única (v1.7.0)
- **Venta única, sin meses ni prueba gratis.** El cliente se registra en la app, queda en **«Acceso pendiente»** y te escribe por WhatsApp con su correo. Tú verificas el pago y lo activas: **acceso de por vida**.
- **Sin tu activación nadie entra**, aunque tenga el APK. Así el APK no sirve de nada si lo comparten: cada persona necesita su propio correo verificado.
- **Una cuenta = un teléfono.** Si alguien inicia sesión con la misma cuenta en otro teléfono, la sesión del primero se cierra sola (en segundos si tiene internet; si estaba sin internet, al volver a abrir la app o recuperar conexión) y ve el aviso «Sesión cerrada». La automatización también se apaga. La sesión nueva siempre gana. Los **administradores** quedan exentos (no se les cierra la sesión).
- **Tú (administrador)**: **Perfil → 🛠 Panel de administrador**. Los correos **pendientes** salen primero; con **«Verificar y activar»** (o escribiendo el correo arriba) confirmas el pago y listo. También puedes escribirles por WhatsApp o **quitarles el acceso**. Tu precio por defecto está en `app/src/main/java/com/drix/app/ui/Admin.kt` → `PRICE`.

**Configuración de UNA sola vez (consola de Firebase → Firestore → Datos):**
1. Publica las reglas del archivo `firestore.rules` (pestaña Reglas). **Vuelve a publicarlas: hay una colección nueva (`sessions`).**
2. Crea la colección **`admins`** → ID del documento = **tu correo en minúsculas** → cualquier campo (ej. `rol` = `admin`). Solo se puede crear desde la consola.
3. Abre la app (con internet) con ese correo: te aparecerá el botón del panel.

**Si ya tenías clientes con el sistema anterior:**
- Los que tenían suscripción por meses (`allowed/{correo}` con `until`) ahora tienen acceso **sin vencimiento**. Si no quieres eso, bórrales el documento en `allowed` o quítales el acceso desde el panel.
- Las cuentas de **prueba gratis** (`trial = true`) quedan **sin acceso** hasta que las actives desde el panel.
- Las colecciones `trialClaims` ya no se usan; puedes borrarlas.

**Límites honestos:** la sesión única la hace cumplir la propia app (con reglas de Firestore que protegen el identificador). Alguien con conocimientos que modifique el APK podría saltársela; para cerrarlo del todo haría falta un servidor propio (Cloud Functions, plan Blaze). Para el uso normal (compartir cuenta entre amigos) funciona bien.

**Tiempo real:** mientras el cliente tiene la app abierta (o el servicio de accesibilidad activo) y hay internet, si le **quitas el acceso** la pantalla cambia a «Acceso pendiente» y la automatización se apaga. Necesita internet al abrir la app (el acceso se consulta siempre al servidor).

## Actualizaciones automáticas (cómo funciona)
1. Tú subes cambios a la rama **main** de GitHub. El workflow compila el APK y lo publica como una **Release** junto con `update.json` (versión, enlace al APK, huella SHA-256 y notas).
2. La app, cada vez que se abre, lee `update.json`. Si la versión es mayor que la instalada, muestra **«Nueva versión»** → el usuario toca **Actualizar ahora**, se descarga con barra de progreso y se abre el instalador de Android. También hay un botón manual en **Perfil → Actualizaciones**.
3. La versión sube sola en cada compilación (`100 + número de ejecución`). Las notas que ve el usuario salen de `RELEASE_NOTES.txt` (si no existe, del mensaje del commit). Para una actualización **obligatoria**: Actions → Build APK → Run workflow → marcar *mandatory*.
4. **El APK que ya tienen instalado no sabe dónde buscar**: todos deben instalar UNA vez manualmente el APK de la primera Release (o del artefacto) generado con este workflow. Desde ahí, todo es automático.
5. **Dónde se publica:** por defecto, en este mismo repositorio, que debe ser **público** (si es privado, el celular no puede descargar). Si tu código es privado: crea otro repositorio público (con un README), crea un token (Settings → Developer settings → Fine-grained tokens, permiso *Contents: read & write* sobre ese repositorio) y guárdalo como secreto **RELEASES_TOKEN**; en Settings → Variables crea **RELEASES_REPO** = `tuusuario/drix-releases`.
6. Android solo instala la actualización si el APK está firmado con la **misma llave** (`app/drix.keystore`). No la cambies o tendrán que reinstalar.

## Avisos del administrador
Panel de administrador → **Enviar notificación a todos**. Se guarda en Firestore (`announcements`) y cada celular con la app la recibe como notificación (en tiempo real si la app está abierta o el servicio de accesibilidad activo; si no, al abrir la app). **Vuelve a publicar `firestore.rules`.** Para notificaciones instantáneas con la app totalmente cerrada haría falta Cloud Functions + FCM (plan Blaze de Firebase).

## Botón flotante (activar / pausar sin abrir la app)
Con el servicio de accesibilidad activo y la sesión iniciada aparece un botón con el logo de Drix sobre cualquier app. Tócalo y salen **▶ activar** y **✕ detener** (se cierran solos a los 4 s). Arrástralo para moverlo: se pega al borde y recuerda su posición. El anillo es **verde** cuando Drix está activo y **rojo** cuando está pausado.

## Pausa para leer
Parámetros → **Pausa para leer**: con el interruptor activo, el pedido se abre normal, pero antes de pulsar «Aceptar» el aviso flotante cuenta 3, 2, 1. Al activarla se pide confirmación porque se pueden perder pedidos por esos segundos. Si el cliente cancela durante la cuenta, no se acepta nada.

## Filtro por género
Parámetros → **Género del pasajero**: Todos / Sin hombres / Sin mujeres. La app de pedidos solo muestra el nombre, así que se deduce por el nombre; si es dudoso, no se bloquea. Lista de nombres en `core/Gender.kt`.

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

## Rediseño de interfaz (UI/animaciones)
- Misma paleta (amarillo / carbón / niebla); nuevas utilidades de movimiento en `ui/Motion.kt`.
- Cabeceras vivas con burbujas y «líneas de velocidad», tarjetas que solapan la cabecera y entran escalonadas.
- Barra inferior con píldora amarilla animada, transiciones entre pestañas y entre pantallas (splash → login → app).
- Botones con rebote + vibración ligera, contadores que suben, dígitos que ruedan, radar de estado en Inicio.
- Iconos Material en lugar de emojis en botones y paneles.

## Palabras bloqueadas y zonas (v. siguiente)
- Parámetros → «Palabras bloqueadas»: frases que, si aparecen en la tarjeta o en el detalle del pedido, lo rechazan (ej. «casco doble»).
- Zonas rojas ahora se aplican al recojo **y** al destino, detectando todos los distritos del pedido y sub-zonas de Ate (Vitarte, Huaycán, Salamanca, Santa Clara, Ceres…).
- Al abrir el detalle de un pedido ya aceptado en la lista se vuelven a revisar zonas rojas y palabras antes de pulsar «Aceptar».

## Actualizaciones automáticas + APK firmado y optimizado

**Cómo funciona:** cada vez que publicas una versión, GitHub compila un APK *firmado* y *optimizado* (R8) y lo sube a
**Releases** junto con `update.json`. La app, al abrirse, lee ese archivo; si hay una versión nueva muestra un aviso,
descarga el APK con barra de progreso, verifica su huella SHA-256 y abre el instalador. Nadie vuelve a pasarse el APK.
También hay un botón *Perfil → Buscar actualizaciones*.

**Publicar una versión** (cualquiera de las dos):
1. Etiqueta: `git tag v1.7.1 && git push origin v1.7.1`
2. GitHub → *Actions* → *Release APK* → *Run workflow* (escribes «qué hay de nuevo» y si es obligatoria).

El número interno de versión (`versionCode`) sube solo en cada ejecución.

**Requisitos**
- El repositorio debe ser **público** (la app descarga sin iniciar sesión). Si es privado, publica `update.json` y `Drix.apk`
  en otro hosting y define la variable `UPDATE_URL` (Settings → Variables) con la dirección de `update.json`.
- La **primera vez** hay que instalar este APK a mano; desde ahí todo se actualiza solo.
- Android pedirá una sola vez «permitir instalar apps de esta fuente» para Drix.

**Llave de firma (importante)**
- Por defecto el release se firma con `app/drix.keystore` (la misma de siempre): las instalaciones actuales se actualizan
  sin desinstalar y «Continuar con Google» sigue funcionando. Esa llave está dentro del repositorio con contraseña simple;
  si el repo es público, cámbiala por una privada:
  ```
  keytool -genkeypair -v -keystore release.keystore -alias drix -keyalg RSA -keysize 2048 -validity 10000
  base64 -w0 release.keystore > keystore.b64
  ```
  Crea estos *Secrets* en GitHub (Settings → Secrets and variables → Actions): `RELEASE_KEYSTORE_B64` (contenido de
  keystore.b64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  Después añade el SHA-1 nuevo en Firebase (Configuración del proyecto → tu app Android) y, como la firma cambia,
  los usuarios deberán desinstalar/instalar **una sola vez**. Guarda `release.keystore` y sus contraseñas en un lugar
  seguro: si la pierdes, nadie podrá actualizar la app.
- Si algo falla solo en release, compila con `gradle assembleRelease -PnoMinify` para descartar la optimización.
