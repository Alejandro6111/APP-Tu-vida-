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

## APK personal 1.0.0 (entrega anterior)

Archivo: `artifacts/Tu-Vida-Android.apk`, 18.709.181 bytes.

SHA-256: `5687FC762B01717A49912888C84522021081F3C2E79565D0FDEA5A918B83EF6B`.

## Alcance práctico

No se conectó la cuenta privada de Google del usuario, no se instaló en su Redmi y no se modificaron sus ajustes HyperOS. Esa configuración requiere acciones en el teléfono después de instalar. Los widgets están implementados y registrados en el APK; su colocación, redimensionado y política de refresco del launcher Redmi deben comprobarse allí. Los horarios de partidos dependen del servicio público y del siguiente refresco.

Los datos de pruebas se crean solo en el emulador; una instalación nueva inicia vacía. No se enviaron datos del usuario a servicios externos. Las pruebas notifican únicamente en el emulador.

## Actualización 1.1.0 · Música · 30 de septiembre de 2026

Verificación en la fecha del usuario, America/Bogota. El emulador muestra su propia hora UTC en las capturas.

`assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` completó correctamente sobre el código final.

| Comprobación | Resultado |
| --- | --- |
| APK versión 1.1.0, código 2 | Compilado |
| Pruebas JVM existentes | 58 aprobadas |
| Pruebas JVM de música | 12 aprobadas |
| Pruebas en Android 15 | 10 aprobadas (8 existentes y 2 nuevas) |
| Lint | 0 errores, 12 avisos no bloqueantes |

Las nuevas pruebas JVM verifican migración de copias v1 sin música, conservación de favoritos/listas/modos/posición, rechazo de referencias remotas y datos inválidos, importación sin duplicados, eliminación coherente, orden de listas y búsqueda sin acentos.

Las pruebas Android generan WAV locales de 60 segundos, leen metadatos y consultan MediaStore. Comprueban creación de listas, adición de canciones, favorito persistido, reproducción real con posición avanzando, continuidad al ir a la pantalla principal de Android, cambios de repetición/aleatorio, orden de cola y posición persistidos, y configuración/consulta del temporizador de cinco minutos en el servicio. Se observa el control multimedia Android con anterior, pausa, siguiente y progreso. No se esperó el vencimiento completo del temporizador ni se simularon llamadas o audífonos físicos.

Se inspeccionan capturas nativas de Música clara/oscura, fuente 1.3, lista, cola, minirreproductor y control multimedia del sistema, en `.impeccable/review/music-*.png`. La escala se restaura a 1.0. Cambiar escala recrea la Activity y conserva el destino Música; las acciones se distribuyen mediante FlowRow y las tabs son desplazables.

La revisión independiente pidió exponer el estado y acción del favorito del audio actual. Se cambió a `IconToggleButton` con `checked` y descripción dinámica, se repitieron las capturas y las verificaciones completas. El revisor puntuó esa corrección como resuelta y emitió `ship` para el hallazgo revisado.

Las pruebas existentes de notificaciones ahora esperan la notificación específica hasta cinco segundos y conceden el permiso mediante UiAutomation; evitan asumir que Android publica la notificación inmediatamente o que no existen otras notificaciones multimedia.

**APK 1.1.0 verificado en esa actualización:** `artifacts/Tu-Vida-Android.apk`, 21.965.543 bytes.

**SHA-256:** `7A6E7277D1E3512BE99D11534F1EA5589A364FCE780A6D8A8FD290A1140435BC`.

Se mantiene el alcance del emulador: no se instaló en Redmi ni se probó HyperOS. Los archivos de audio y permisos de documentos no viajan dentro de la copia JSON. Una biblioteca restaurada en otro teléfono puede requerir volver a seleccionar archivos y rehacer sus referencias en las listas.

## Actualización 1.2.0 · negro/dorado, carpetas y portadas

Verificada el **30 de septiembre de 2026**, fecha del usuario en America/Bogota.

`assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` completó correctamente: **77 pruebas JVM, 12 pruebas Android en emulador Android 15, cero errores de lint y 12 avisos no bloqueantes**. Los reportes están en `android/app/build/reports/`.

Las siete pruebas de dominio nuevas comprueban separación de carpetas por ruta/volumen, limpieza de favoritos/listas/cola/posición al excluir, exclusión persistente en futuras detecciones, reincorporación, actualización de referencias antiguas que todavía no tenían carpeta, compatibilidad de JSON anterior, conservación de exclusiones en copias y rechazo de ubicaciones/descripciones inválidas.

La prueba nativa de carpetas crea tres WAV de ejemplo en dos carpetas de MediaStore. Comprueba que la lectura individual y la detección informan la misma carpeta; abre y reproduce el conjunto, cancela una exclusión y verifica que no cambió la biblioteca; confirma la exclusión durante la reproducción y comprueba que el reproductor retira ambos audios, las referencias se limpian y los dos archivos siguen siendo legibles. Detectar otra vez omite los audios excluidos; volver a incluir la carpeta y detectar recupera los tres. El nombre «WhatsApp Audio» corresponde a una carpeta sintética bajo `Music/TuVidaTests`, no a datos reales de WhatsApp ni a una prueba en HyperOS.

La otra prueba nativa nueva verifica una portada local de 192 × 192 para una descarga sin imagen, bytes PNG válidos y estables, y presencia de la portada en los metadatos de Media3. La extracción de portadas incrustadas se implementa mediante MediaMetadataRetriever; esta prueba usa WAV sin imagen incrustada.

Se inspeccionaron 15 capturas nativas: Música clara/oscura, letra 1.3, lista, cola, minirreproductor, control multimedia Android, listado de carpetas, canciones de una carpeta, confirmación de exclusión, carpeta excluida, Hoy, Salud, Dinero oscuro y Ajustes oscuro. Se restauró la escala a 1.0. Las capturas completas quedan en `.impeccable/review/` y las representativas publicables en `docs/images/`. Los textos, vinilos y controles son legibles en las variantes inspeccionadas; las tabs y las acciones se desplazan según el tamaño del texto.

Los pares de texto de los roles principales, contenedores, superficies y errores en ambos esquemas tienen contraste calculado de **6,76:1 a 16,78:1**. No se ejecutó el detector web: el producto es Compose nativo. La paleta también se aplica al icono y a los recursos de widgets. Las instalaciones nuevas usan oscuro; la actualización conserva el tema que ya estaba guardado.

**APK actual 1.2.0:** `artifacts/Tu-Vida-Android.apk`, **22.229.640 bytes**.

**SHA-256:** `838640FD4266EE4EA89C8B765D1775A2B3E69CD48E94AEA20191F1ADBA28D59C`.

Alcance: teléfono emulado Android 15; sin instalación en Redmi, pruebas de tablet, audífonos físicos ni auditoría TalkBack. Los proveedores que no revelan la ubicación se agrupan en «Carpeta no disponible»; allí se pueden quitar canciones individualmente. Excluir una carpeta afecta su ruta exacta: las subcarpetas aparecen como grupos separados. Las portadas y agrupación consultan únicamente archivos locales; las referencias y permisos de documentos mantienen los límites de restauración descritos para 1.1.0.
