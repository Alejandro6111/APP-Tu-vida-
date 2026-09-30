# Verificación de la entrega

Verificación realizada el **29 de septiembre de 2026**, fecha del usuario en America/Bogota.

## Resultado funcional

`assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` completó correctamente.

| Comprobación | Resultado |
| --- | --- |
| Compilación de APK Android | Correcta |
| Pruebas JVM de finanzas | 27 aprobadas |
| Pruebas JVM de planificación | 16 aprobadas |
| Pruebas JVM de copias e importación | 9 aprobadas |
| Pruebas JVM de iCal | 6 aprobadas |
| Pruebas en emulador Android | 8 aprobadas |
| Android lint | Sin errores; avisos no bloqueantes de versiones, fuente opcional y textos XML |

Las pruebas en Android verifican guardar gastos, registrar comidas/ejercicio, crear y completar tareas, cronómetro con vuelta y pausa, entrega real de una notificación por AlarmManager al terminar un pomodoro con la Activity cerrada, acción de completar una tarea y persistencia de la acción de posponer. La prueba de capturas recorre los cinco destinos y Ajustes, con datos explícitamente marcados como ejemplos.

## Entorno

- JDK Temurin 17; Gradle 8.9; Android Gradle Plugin 8.7.3; Kotlin 2.0.21.
- SDK/API 35 y build-tools 35.0.0.
- Emulador Pixel 7, Android 15, 1080 × 2400, aceleración WHPX, sin ventana visible.
- APK local con Segoe UI Semibold aportada por el entorno Windows del usuario. La fuente y el APK personal quedan excluidos de Git.
- Capturas nativas en `.impeccable/review/`: Hoy, Finanzas, Agenda, Enfoque, Salud, Salud oscura, Finanzas oscuras y Ajustes oscuros. No se usó navegador para simular Android ni el detector web.
- Se repitió la captura de los destinos con escala de fuente de sistema **1.3**: recorrido correcto, texto legible y layouts desplazables; la escala del emulador se restauró a 1.0.
- La revisión visual independiente aprobó las 12 capturas nativas y su código de interfaz. Su alcance es la presentación en teléfono emulado, incluido tema oscuro y texto ampliado; no certifica servicios externos ni comportamiento del Redmi.

## APK personal

Archivo: `artifacts/Tu-Vida-Android.apk`, 18.709.181 bytes.

SHA-256: `5687FC762B01717A49912888C84522021081F3C2E79565D0FDEA5A918B83EF6B`.

## Alcance práctico

No se conectó la cuenta privada de Google del usuario, no se instaló en su Redmi y no se modificaron sus ajustes HyperOS. Esa configuración requiere acciones en el teléfono después de instalar. Los widgets están implementados y registrados en el APK; su colocación, redimensionado y política de refresco del launcher Redmi deben comprobarse allí. Los horarios de partidos dependen del servicio público y del siguiente refresco.

Los datos de pruebas se crean solo en el emulador; una instalación nueva inicia vacía. No se enviaron datos del usuario a servicios externos. Las pruebas notifican únicamente en el emulador.
