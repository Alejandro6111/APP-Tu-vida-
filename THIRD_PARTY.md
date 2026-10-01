# Motores incluidos para música

El buscador y descargador incluyen dependencias de código abierto. El repositorio contiene la integración Kotlin; los motores se resuelven mediante Maven Central, sin añadir binarios o claves al control de versiones.

| Componente | Proyecto y atribución | Licencia declarada |
| --- | --- | --- |
| youtubedl-android 0.18.1, módulos library y ffmpeg | [yausername y colaboradores](https://github.com/yausername/youtubedl-android/tree/0.18.1) | [GPL-3.0](https://github.com/yausername/youtubedl-android/blob/0.18.1/LICENSE) |
| yt-dlp integrado y actualizaciones estables | [yt-dlp y colaboradores](https://github.com/yt-dlp/yt-dlp) | [Licencia del proyecto y dependencias](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE) |
| Python, FFmpeg y QuickJS empaquetados por la biblioteca | [Fuentes y construcción de youtubedl-android](https://github.com/yausername/youtubedl-android/tree/0.18.1) | Las licencias correspondientes a cada componente y a la compilación incluida |

Para revisar o reconstruir los motores, consulta [BUILD_PYTHON.md](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_PYTHON.md) y [BUILD_FFMPEG.md](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md). Este archivo documenta las atribuciones; no sustituye los textos de licencia de las dependencias ni los requisitos aplicables al distribuir los binarios. La fuente Segoe autorizada para el APK local permanece fuera del repositorio.
