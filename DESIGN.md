# Diseño de Tu Vida

Aplicación Android de operación cotidiana. El usuario eligió construir directamente y delegó las decisiones de implementación; la paleta elegante y Segoe UI Semibold son las restricciones visuales. La interfaz se adapta mediante Material 3 al flujo de un teléfono personal.

## Sistema

- Colores claros: fondo marfil `#FAF8F2`, texto negro `#1C1B18`, primario dorado oscuro `#735719`, contenedor dorado `#F3E2AF`, acento salvia `#456454`.
- Colores oscuros: fondo negro `#0D0D0E`, texto marfil `#F2EEE5`, primario dorado `#E9C66F`, contenedor oro profundo `#443719`, acento salvia `#A9CFB5`. Superficies elevadas carbón entre `#161617` y `#30302E`; contornos cálidos.
- Roles Material para superficies, contenedores, error, contornos y texto; no invertir colores manualmente.
- Semibold en escala tipográfica Material, tamaños sp y fuente configurable localmente; Sans Serif Semibold como alternativa al faltar Segoe.
- Formas estándar Material; agrupaciones por propósito, listas de información y divisores. Estado vacío con una acción concreta.
- Navegación de cinco destinos: Hoy, Dinero, Agenda, Enfoque y Salud. Ajustes accesible desde la barra superior. Rail desde 600 dp, barra inferior en teléfono.
- Música es una superficie secundaria accesible desde la barra superior y Hoy. Cuando hay una canción actual, un minirreproductor tonal aparece en los demás destinos, encima de la barra inferior en teléfono, con acceso al reproductor, pausa/reanudación y siguiente canción.
- Formularios en hojas inferiores con scroll e insets de teclado; fechas y horas mediante selectores Android.
- Iconos Material Outlined; destinos con etiqueta; 48 dp por objetivo táctil. Calendario de salud con texto accesible además del verde/rojo/neutro.
- Tema claro, oscuro y según Android; oscuro por defecto en instalaciones nuevas, conservando la elección existente al actualizar. Atrás del sistema devuelve a Hoy, cierra la hoja o vuelve de una carpeta a su listado. Icono y widgets comparten negro y dorado.

## Superficies

Hoy prioriza las acciones del día, el presupuesto y los próximos planes. Finanzas separa ocho vistas con tabs desplazables. Agenda separa eventos, tareas y partidos. Enfoque prioriza un único contador con controles visibles. Salud dedica el espacio al calendario mensual y al registro seleccionado. Ajustes agrupa cuenta/calendarios, notificaciones, estudio, widgets y datos.

Música mantiene seis tabs desplazables: Biblioteca, Favoritos, Listas, Cola, Carpetas y Descubrir. Biblioteca expone «Agrupar por carpetas». El contenido usa una lista vertical con margen lateral de 16 dp y separación de 12 dp. Las acciones de carga y los controles auxiliares usan FlowRow para acomodar ancho reducido o texto aumentado. El panel de la canción actual usa primaryContainer, shapes.large y relleno de 16 dp; el minirreproductor usa secondaryContainer. Los botones tonales, chips de aleatorio/repetición y menús siguen Material 3. Las filas exponen título, artista/álbum, duración y portada de 56 dp; la canción actual combina fondo tonal con descripción explícita. El reproductor usa portada de 72 dp y el mini de 40 dp. Las listas y la cola conservan su orden al buscar; Biblioteca, Favoritos y el contenido de carpetas ofrecen ordenación.

Carpetas agrupa por ruta completa y volumen, muestra el nombre corto, ruta, cantidad y duración; el icono Folder abre su contenido y FolderOff permite excluirlo mediante confirmación. La sección de excluidas permite volver a incluir una carpeta y explica que hay que detectar otra vez. La ubicación desconocida se identifica con texto y no ofrece exclusión de carpeta. Las exclusiones limpian favoritos/listas/cola y se guardan también en las copias.

Las portadas incrustadas se leen fuera del hilo principal con dos lectores simultáneos y caché de 8 MB. Las canciones sin portada usan un vinilo dibujado mediante geometría vectorial local, con cuatro combinaciones estables: dorado, cobre, salvia y ciruela. Las imágenes tienen función decorativa y el título permanece como identificación accesible. Los controles multimedia de Android reciben el vinilo generado sin acceso a red.

Los controles musicales tienen etiquetas de acción; el favorito actual usa un control con estado seleccionado, y las filas anuncian marcar/quitar favorito y reproducir el título correspondiente. El progreso es un Slider etiquetado «Posición de la canción», acompañado de tiempo transcurrido y duración. Los títulos de filas admiten dos líneas; el título del panel admite tres y el minirreproductor una, con elipsis al excederlas. La reproducción en segundo plano usa la notificación multimedia nativa de Android. Las capturas nativas de Música incluyen tema claro, oscuro, texto aumentado, listas, cola, minirreproductor y notificación; no acreditan pruebas en Redmi ni tablet.

## Decisiones y límites

Descubrir es la sexta pestaña de Música y se abre también con «Buscar y descargar» desde Biblioteca. Mantiene el campo de búsqueda al volver a otras pestañas y al recrear la Activity. Los resultados usan tarjetas Material con título completo, artista/canal y duración; la acción Descargar cambia a Escuchar descargada cuando el audio está en la biblioteca. El estado de descarga se observa desde WorkManager, con progreso o espera indeterminada, cancelación y acción de escucha al terminar. La notificación de descarga usa un canal separado de la reproducción. Buscar oculta el teclado y admite la acción de búsqueda del teclado Android. Los estados inicial, sin coincidencias, error y actualización tienen texto y acciones explícitos.

El verde/red solo denota un registro explícito de salud; un día desconocido no se convierte en ausencia de ejercicio. El gráfico financiero siempre acompaña sus valores con texto. Las fuentes y permisos faltantes producen estados claros. La precisión de avisos depende de los permisos y la configuración del teléfono.

Música comunica carga, conexión, vacío, búsqueda sin coincidencias y archivo inaccesible con texto y acciones concretas. Detectar audios solicita el permiso tras pulsar la acción; Añadir canciones permite elegir archivos sin acceso a toda la música. Quitar una canción, excluir una carpeta o eliminar una lista muestra confirmación y explica que el archivo del teléfono se conserva. Excluir comunica el número de audios, la ruta, las referencias que se retiran y cómo volver a incluirla.
