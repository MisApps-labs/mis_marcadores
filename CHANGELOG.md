# Changelog

## Sin publicar

- Se elimina el cifrado de extremo a extremo y la contraseña maestra: los marcadores se sincronizan con Nextcloud como JSON legible (formato versión 2). Al actualizar, cada dispositivo vuelve a subir en claro los marcadores que tiene; `keyinfo.json` ya no se usa y puede borrarse.

## 1.0.0 — 2026-11-16

Primera versión.

- Marcadores con carpetas anidadas, etiquetas, favoritos, "Leer más tarde", colores y notas.
- Guardado desde "Compartir", portapapeles y acceso directo; título, descripción, imagen e icono automáticos.
- Lista, tarjetas y vista compacta; 8 órdenes; búsqueda sin acentos; selección múltiple con deshacer.
- Aviso de duplicados, limpieza de rastreadores y comprobación de enlaces rotos.
- Importación y exportación en HTML de navegadores; copia automática diaria.
- Sincronización con Nextcloud cifrada de extremo a extremo (AES-256-GCM).
- Bloqueo con huella/PIN, tema AMOLED y 5 idiomas.
