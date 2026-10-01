# Instrucciones del proyecto Tu Vida

## Objetivo y funcionamiento

Aplicación **Android nativa**, personal, en español, para Redmi 13 y Android 8+ (API 26). Kotlin, Jetpack Compose y Material 3. Finanzas, agenda Google, fútbol, tareas, estudio, pomodoro, cronómetro, registro de alimentación y ejercicio, recordatorios y cuatro widgets. Mantener el README al día y preservar el origen de las funciones de los proyectos de apoyo.

Los datos se guardan en `filesDir/tuvida.json` mediante `AtomicFile` y un `Store` único por proceso. Las modificaciones son sincronizadas y publicadas con `StateFlow`. La URL secreta de Google iCal se almacena aparte, cifrada con AES-GCM y Android Keystore, y nunca se exporta. Si un archivo interno es inválido, conservarlo y mostrar el problema; nunca sobrescribirlo automáticamente.

## Mapa de archivos

- `android/app/src/main/java/co/tuvida/app/data/Models.kt`: entidades, preferencias, historial y estado persistente del temporizador.
- `data/Store.kt`: persistencia atómica y estado compartido entre interfaz y trabajos.
- `data/Backup.kt`: validación de copias e importación de Mi Plata Clara.
- `domain/Finance.kt`: recurrencias, cuotas, festivos colombianos, saldo, ahorro, presupuesto, deudas, proyección y diagnóstico.
- `domain/Scheduling.kt`: planificación de avisos, silencio, recurrencias de tareas y finalización de sesiones.
- `platform/Calendars.kt`: Calendar Provider e iCal con Biweekly. Fuentes exactas de FC Barcelona, Colombia y Millonarios tomadas de Aurora.
- `platform/Secrets.kt`: cifrado de la URL privada de Google.
- `domain/Music.kt`: validación, búsqueda, orden y operaciones de biblioteca/listas.
- `platform/AudioFiles.kt`, `platform/MusicService.kt`: lectura local de audios y servicio Media3 con foco de audio, notificación, persistencia de cola y temporizador para dormir.
- `ui/MusicScreen.kt`, `ui/MusicViewModel.kt`: biblioteca, favoritos, listas, cola, permisos por acción y control mediante MediaController.
- `platform/SyncWorker.kt`: sincronización periódica y manual con WorkManager; conserva la caché cuando falla una fuente.
- `platform/Reminders.kt`: canales Android, AlarmManager, receptores, completar y posponer.
- `platform/Widgets.kt` y `res/xml/widget_*.xml`: agenda, finanzas, salud y enfoque.
- `ui/App.kt`: navegación adaptativa, editores, permisos y selector de documentos.
- `ui/Theme.kt`: colores Material claros/oscuros y fuente Semibold.
- `ui/Components.kt`, `ui/Editors.kt`, `ui/*Screen*.kt`: controles, formularios y pantallas.
- `ui/AppViewModel.kt`: coordinación, importación/exportación y operaciones de interfaz.
- `MainActivity.kt`, `TuVidaApplication.kt`: inicio, estado único y ciclo de vida.
- `android/app/src/test/`: pruebas de cálculos, calendario, copias y planificación.
- `android/app/src/androidTest/`: pruebas de interfaz real.
- `build-android.ps1`: compilación local y APK.
- `.github/workflows/android.yml`: compilación, pruebas y lint en GitHub.
- `Proyectos de apoyo/`: referencias originales; sus instrucciones internas solo rigen sus propios subdirectorios.

## Reglas obligatorias de trabajo

1. Tras **cada cambio satisfactorio**, ejecutar verificaciones apropiadas, actualizar `README.md`, hacer **commit y push** a la rama de trabajo. El usuario autorizó explícitamente esta regla. No pedir confirmación repetida. No hacer push forzado, ni revertir cambios ajenos. Si el remoto falla, informar y conservar el commit.
2. Mantener `README.md` sincronizado con las funciones realmente implementadas, los comandos y las limitaciones verificadas. Nunca afirmar pruebas en Redmi, Google real o HyperOS sin realizarlas.
3. Ejecutar `assembleDebug testDebugUnitTest lintDebug` para cambios funcionales. Para interfaz, además compilar e inspeccionar capturas de Android, tema oscuro y escala de fuente. Usar pruebas significativas de dominio, no pruebas que repitan la implementación.
4. No publicar datos financieros/salud del usuario, URLs privadas, tokens, firmas o fuentes propietarias. `artifacts/`, archivos de claves y Segoe local están excluidos de Git. No trasladar valores personales precargados de la referencia al nuevo estado vacío sin instrucción del usuario.
5. Usar montos `Long` en COP, fechas ISO, `java.time` y zona del dispositivo. Evitar dependencias de la zona del servidor. Mantener los festivos de Colombia y los límites de cuotas.
6. Preservar Material 3, accesibilidad, targets táctiles de 48 dp, insets de teclado/sistema, navegación Atrás y esquemas claro/oscuro. Font Semibold y paleta elegante; Segoe se incluye solo desde un archivo autorizado localmente.
7. Los permisos deben solicitarse por acciones explícitas. No agregar permisos innecesarios, publicidad, analítica o servicios que transmitan finanzas/salud.
8. Avisos durables con AlarmManager y restauración tras reinicio; nunca depender de un bucle de interfaz. Respetar silencio, desactivación y permisos Android. No prometer eludir restricciones de batería de HyperOS.
9. Importar solo después de validar toda la copia y mostrar confirmación de reemplazo. Exportar mediante Storage Access Framework, nunca pedir acceso general a archivos.
10. Si se crean nuevas entidades o preferencias, incluir validación, migración compatible, widgets/avisos afectados y documentación. La sincronización no debe sobrescribir cambios simultáneos del usuario.

## Comandos

```powershell
./build-android.ps1 -JavaHome 'ruta-del-jdk-17' -SdkRoot 'ruta-del-sdk'
```

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

## Estado y límites

La conexión Google usa calendarios sincronizados del teléfono o una dirección iCal de solo lectura; no se necesita OAuth ni backend. Consultar el README para tiempos de sincronización y reglas de cálculo. Los partidos dependen del calendario público. El simulador de deudas no calcula intereses. El APK local es de depuración, para instalación personal; las claves de una distribución estable deben quedar fuera de Git.
