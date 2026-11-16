<p align="center">
  <img src="docs/logo.png" alt="Mis Marcadores" width="160">
</p>

<h1 align="center">Mis Marcadores</h1>

<p align="center">
  Marcadores para Android con carpetas, etiquetas y sincronización con tu propio Nextcloud.
</p>

<p align="center">
  <a href="LICENSE"><img alt="Licencia: GPL v3" src="https://img.shields.io/badge/licencia-GPLv3-blue.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg">
</p>

---

Mis Marcadores forma parte de la familia **Mis Apps** (junto a Mis Notas y Mis Podcast): mismo diseño, misma filosofía local-first y sincronización con Nextcloud. Tus enlaces viven en el teléfono y, si quieres, se sincronizan con tu propio servidor.

## Características

- **Guarda desde cualquier app**: pulsa *Compartir* en el navegador (o en cualquier app) y elige Mis Marcadores. También detecta enlaces en el portapapeles y tiene acceso directo "Nuevo marcador".
- **Datos automáticos**: título, descripción, imagen de vista previa e icono de la web, leídos directamente de la página (sin servicios de terceros).
- **Organización completa**: carpetas anidadas con colores, **etiquetas** (renombrar y borrar en bloque), favoritos, **"Leer más tarde"**, colores por marcador y notas personales.
- **Vistas rápidas**: todos, favoritos, leer más tarde, sin carpeta, enlaces rotos y papelera (con vaciado automático a los 30 días, desactivable). Añadidos recientemente en el inicio.
- **Tres vistas** (lista, tarjetas con imagen y compacta) y 8 órdenes: fecha de alta, modificación, título, sitio web, más abiertos, abiertos recientemente…
- **Búsqueda instantánea** en títulos, direcciones, descripciones, notas y etiquetas, sin distinguir acentos.
- **Selección múltiple**: favoritos, mover, etiquetar, colorear, compartir o borrar varios a la vez, con **"Deshacer"**.
- **Aviso de duplicados** al guardar un enlace que ya tienes, y **limpieza de rastreadores** (`utm_*`, `fbclid`…).
- **Comprobación de enlaces rotos** (páginas que ya no existen o dominios caducados).
- **Abre los enlaces** en una pestaña del navegador integrada con el color de la app, o en tu navegador.
- **Importa y exporta** en el formato HTML estándar de los navegadores (Chrome, Firefox, Safari, Edge, linkding, Raindrop…) conservando las carpetas. También importa las copias JSON de Firefox (`.json` y `.jsonlz4`) y el fichero `Bookmarks` del perfil de Chrome. Copia de seguridad automática diaria en la carpeta que elijas.
- **Clasificación en carpetas**: carpetas y subcarpetas con color; mueve marcadores uno a uno o en bloque, crea carpetas al vuelo al mover o editar, y navega por la jerarquía dentro de cada carpeta.
- **Sincronización con Nextcloud** (WebDAV) automática: al abrir la app, al editar y cada ~30 minutos en segundo plano. Los marcadores se guardan sin cifrar (ver [SECURITY.md](SECURITY.md) y [docs/SYNC_FORMAT.md](docs/SYNC_FORMAT.md)).
- **Bloqueo de la app** con huella, cara o PIN, y contenido oculto en las apps recientes.
- Tema claro, oscuro y **AMOLED**; en español, inglés, francés, alemán e italiano.

## Compilar

Requisitos: Android Studio reciente o JDK 21 y el SDK de Android (API 37).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. Para firmar versiones de publicación copia `keystore.properties.example` como `keystore.properties` (ver [CONTRIBUTING.md](CONTRIBUTING.md)).

## Sincronizar con Nextcloud

1. En Nextcloud: *Configuración personal → Seguridad → Crear contraseña de aplicación*.
2. En la app: *Ajustes → Sincronización*: dirección del servidor (`https://…`), usuario y contraseña de aplicación.

Los datos se guardan en la carpeta `MisMarcadores/` de tu Nextcloud.

## Licencia

[GPL-3.0-or-later](LICENSE). Tipografías Outfit e Inter bajo la SIL Open Font License 1.1.
