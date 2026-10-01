# Diseño de Tu Vida

Aplicación Android de operación cotidiana. El usuario eligió construir directamente y delegó las decisiones de implementación; la paleta elegante y Segoe UI Semibold son las restricciones visuales. La interfaz se adapta mediante Material 3 al flujo de un teléfono personal.

## Sistema

- Colores claros: fondo marfil `#F7F5F0`, texto petróleo `#182D31`, primario verde `#245B50`, contenedor verde `#CCEADF` y dorado sobrio `#715B36`.
- Colores oscuros: fondo petróleo `#10252B`, texto `#E3ECEA`, verde `#A2D5C4`, dorado `#DCC59A`.
- Roles Material para superficies, contenedores, error, contornos y texto; no invertir colores manualmente.
- Semibold en escala tipográfica Material, tamaños sp y fuente configurable localmente; Sans Serif Semibold como alternativa al faltar Segoe.
- Formas estándar Material; agrupaciones por propósito, listas de información y divisores. Estado vacío con una acción concreta.
- Navegación de cinco destinos: Hoy, Dinero, Agenda, Enfoque y Salud. Ajustes accesible desde la barra superior. Rail desde 600 dp, barra inferior en teléfono.
- Música es una superficie secundaria accesible desde la barra superior y Hoy. Cuando hay una canción actual, un minirreproductor tonal aparece en los demás destinos, encima de la barra inferior en teléfono, con acceso al reproductor, pausa/reanudación y siguiente canción.
- Formularios en hojas inferiores con scroll e insets de teclado; fechas y horas mediante selectores Android.
- Iconos Material Outlined; destinos con etiqueta; 48 dp por objetivo táctil. Calendario de salud con texto accesible además del verde/rojo/neutro.
- Tema claro, oscuro y según Android. Atrás del sistema devuelve a Hoy o cierra la hoja.

## Superficies

Hoy prioriza las acciones del día, el presupuesto y los próximos planes. Finanzas separa ocho vistas con tabs desplazables. Agenda separa eventos, tareas y partidos. Enfoque prioriza un único contador con controles visibles. Salud dedica el espacio al calendario mensual y al registro seleccionado. Ajustes agrupa cuenta/calendarios, notificaciones, estudio, widgets y datos.

Música mantiene cuatro tabs desplazables: Biblioteca, Favoritos, Listas y Cola. El contenido usa una lista vertical con margen lateral de 16 dp y separación de 12 dp. Las acciones de carga y los controles auxiliares usan FlowRow para acomodar ancho reducido o texto aumentado. El panel de la canción actual usa primaryContainer, shapes.large y relleno de 16 dp; el minirreproductor usa secondaryContainer. Los botones tonales, chips de aleatorio/repetición y menús siguen Material 3. Las filas exponen título, artista/álbum y duración; la canción actual combina fondo tonal con icono y descripción explícita. Las listas y la cola conservan su orden al buscar; Biblioteca y Favoritos ofrecen ordenación.

Los controles musicales tienen etiquetas de acción; el favorito actual usa un control con estado seleccionado, y las filas anuncian marcar/quitar favorito y reproducir el título correspondiente. El progreso es un Slider etiquetado «Posición de la canción», acompañado de tiempo transcurrido y duración. Los títulos de filas admiten dos líneas; el título del panel admite tres y el minirreproductor una, con elipsis al excederlas. La reproducción en segundo plano usa la notificación multimedia nativa de Android. Las capturas nativas de Música incluyen tema claro, oscuro, texto aumentado, listas, cola, minirreproductor y notificación; no acreditan pruebas en Redmi ni tablet.

## Decisiones y límites

El verde/red solo denota un registro explícito de salud; un día desconocido no se convierte en ausencia de ejercicio. El gráfico financiero siempre acompaña sus valores con texto. Las fuentes y permisos faltantes producen estados claros. La precisión de avisos depende de los permisos y la configuración del teléfono.

Música comunica carga, conexión, vacío, búsqueda sin coincidencias y archivo inaccesible con texto y acciones concretas. Detectar audios solicita el permiso tras pulsar la acción; Añadir canciones permite elegir archivos sin acceso a toda la música. Quitar una canción o eliminar una lista muestra confirmación y explica que el archivo del teléfono se conserva.
