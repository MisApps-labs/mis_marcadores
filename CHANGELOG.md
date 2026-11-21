# Changelog

## 1.0.1 — 2026-11-21

- Se elimina el cifrado de extremo a extremo y la contraseña maestra: los marcadores se sincronizan con Nextcloud como JSON legible (formato versión 2). Al actualizar, cada dispositivo vuelve a subir en claro los marcadores que tiene; `keyinfo.json` ya no se usa y puede borrarse.
- Al conectar Nextcloud se acepta también la URL WebDAV (`…/remote.php/dav/files/<usuario>/`) o la que se ve en el navegador; antes la URL WebDAV hacía que los ficheros se guardaran en la raíz de los archivos.
- Si la carpeta de Nextcloud se vacía o se borra, las carpetas ya no se pierden: el freno de borrados masivos las protege igual que a los marcadores.
- Un listado WebDAV vacío o no válido detiene la sincronización en lugar de interpretarse como una carpeta vacía.
- Las lápidas de borrados con más de 180 días se eliminan del servidor.
- Una sincronización cancelada ya no se muestra como error, y las peticiones que llegan mientras otra está en curso ya no se pierden.
- Actualizados Gradle, AGP, Kotlin y dependencias de AndroidX.

## 1.0.0 — 2026-11-16

Primera versión.

- Marcadores con carpetas anidadas, etiquetas, favoritos, "Leer más tarde", colores y notas.
- Guardado desde "Compartir", portapapeles y acceso directo; título, descripción, imagen e icono automáticos.
- Lista, tarjetas y vista compacta; 8 órdenes; búsqueda sin acentos; selección múltiple con deshacer.
- Aviso de duplicados, limpieza de rastreadores y comprobación de enlaces rotos.
- Importación y exportación en HTML de navegadores; copia automática diaria.
- Sincronización con Nextcloud cifrada de extremo a extremo (AES-256-GCM).
- Bloqueo con huella/PIN, tema AMOLED y 5 idiomas.
