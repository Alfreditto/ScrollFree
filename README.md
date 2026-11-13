# ScrollFree

**ScrollFree** es una app Android libre y de código abierto (FLOSS) para descargar y
reproducir vídeos e imágenes de redes sociales **sin anuncios, sin rastreo y sin cuentas**.

Pega un enlace (o compártelo desde otra app) y ScrollFree descarga el contenido con
[yt-dlp](https://github.com/yt-dlp/yt-dlp) y lo abre en su propio reproductor.

> ⚠️ **Uso responsable**: usa ScrollFree solo con contenido propio o para el que tengas
> derechos. Descargar contenido de algunas plataformas puede infringir sus términos de
> servicio. La app es solo una interfaz; tú eres responsable del uso que hagas de ella.

---

## Características

- 📥 **Descarga con yt-dlp** — compatible con TikTok, Instagram, X/Twitter, YouTube,
  Facebook, Twitch, Vimeo, Reddit, Dailymotion, Snapchat y cualquier sitio que soporte yt-dlp.
- 🖼️ **Carruseles de fotos** — descarga los posts de imágenes (TikTok/Instagram) con su
  audio de fondo y los muestra en un carrusel a pantalla completa (auto-avance de 5 s,
  botón de pausa/continuar).
- ▶️ **Reproductor propio** (Media3/ExoPlayer) — pantalla completa, controles que se
  ocultan solos, **±10 segundos**, y **x2 mientras mantienes pulsado**.
- 🔄 **Actualización del extractor** — actualiza yt-dlp sin esperar a una nueva versión de la app.
- 🔗 **Recibe enlaces** — desde "Compartir" o abriendo un enlace directamente.
- 🕘 **Historial ligero** — solo guarda título, plataforma y fecha (nunca el enlace ni el archivo).
- 🧹 **Privacidad** — los vídeos se guardan temporalmente y se borran al cerrar la app.
  Sin telemetría, sin analítica, sin publicidad.
- 🛡️ **Sin permisos innecesarios** — solo `INTERNET` y `ACCESS_NETWORK_STATE`.

## Capturas

_Próximamente._

## Descarga

Las APK se publican en la página de [Releases](../../releases).

## Compilar desde el código

Requisitos: JDK 17+ y el Android SDK (compileSdk 37).

```bash
git clone https://github.com/Alfreditto/ScrollFree.git
cd ScrollFree
./gradlew :app:assembleDebug
```

El APK de debug se genera en `app/build/outputs/apk/debug/`.

Para una build de release firmada necesitas un keystore propio y definir las variables
de entorno `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.

## Estructura del proyecto

```
app/src/main/java/es/tortugaselectronicas/scrollfree/
├── data/        # Descarga (yt-dlp, scraper de fotos), historial
├── ui/          # Pantallas Compose (home, player, carrusel, historial)
├── update/      # Comprobación de nuevas versiones (aviso)
└── util/        # Utilidades de URL
```

## Tecnologías

[Kotlin](https://kotlinlang.org/) · [Jetpack Compose](https://developer.android.com/jetpack/compose) ·
[Media3/ExoPlayer](https://developer.android.com/media/media3) ·
[youtubedl-android](https://github.com/yausername/youtubedl-android) (fork de junkfood02) ·
[Coil](https://coil-kt.github.io/coil/)

## Contribuir

Las contribuciones son bienvenidas. Abre un *issue* para reportar bugs o proponer mejoras,
y envía *pull requests* con cambios enfocados. Al contribuir aceptas que tu código se
publique bajo la misma licencia del proyecto (GPL-3.0).

## Licencia

ScrollFree es **software libre**: puedes usarlo, estudiarlo, modificarlo y redistribuirlo
bajo los términos de la **GNU General Public License v3.0**.
Consulta el fichero [LICENSE](LICENSE) para el texto completo.

```
Copyright (C) 2026 Alfreditto

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los
términos de la Licencia Pública General de GNU publicada por la Free Software
Foundation, en su versión 3.

Este programa se distribuye con la esperanza de que sea útil, pero SIN NINGUNA
GARANTÍA; ni siquiera la garantía implícita de COMERCIALIZACIÓN o IDONEIDAD PARA
UN PROPÓSITO PARTICULAR. Véase la Licencia Pública General de GNU para más detalles.
```

## Agradecimientos

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — el motor de descarga.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) y su
  [fork de junkfood02](https://github.com/junkfood02/youtubedl-android).
- [Seal](https://github.com/JunkFood02/Seal) — inspiración para la integración de yt-dlp.
