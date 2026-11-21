<!--
  SPDX-License-Identifier: GPL-3.0-or-later
  Copyright (C) 2026 ferlagod
  Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores
-->

# Formato de sincronización

Mis Marcadores guarda en Nextcloud (WebDAV) una carpeta `MisMarcadores/` con un fichero por elemento:

```
MisMarcadores/
├── folder_<uuid>.json        una carpeta
└── bookmark_<uuid>.json      un marcador
```

Los ficheros son JSON en claro: no se cifran.

## Marcadores y carpetas

```json
{ "format": "mismarcadores", "version": 2, "type": "bookmark", "id": "<uuid>", "data": { "url": "https://…", "title": "…" } }
```

Campos de `data`:

| Marcador (`type: bookmark`) | Carpeta (`type: folder`) |
|---|---|
| `folderId`, `url`, `title`, `description`, `notes`, `tags` (array), `imageUrl`, `faviconUrl`, `color`, `isFavorite`, `isReadLater`, `createdAt`, `updatedAt`, `isTrashed` | `parentId`, `name`, `color`, `updatedAt` |

No se sincronizan (son estadísticas de cada dispositivo): número de veces abierto, última apertura y resultado de la comprobación de enlaces.

## Borrados

Un borrado definitivo sustituye el contenido del fichero por una lápida:

```json
{ "format": "mismarcadores", "version": 2, "type": "bookmark", "id": "<uuid>", "deleted": true, "deletedAt": 0 }
```

Las lápidas con más de 180 días (según su fecha de modificación en el servidor) las borra el primer dispositivo que las haya procesado. Si un dispositivo pasa más tiempo sin sincronizar y aún tenía ese marcador, lo verá desaparecer sin lápida y lo llevará a su papelera.

## Versión 1 (cifrada, retirada)

La versión 1 guardaba el contenido cifrado con AES-256-GCM en `payload` y los parámetros de la clave en `keyinfo.json`. La app actual no los lee: si un dispositivo tiene ese marcador o carpeta, lo vuelve a subir en claro encima; si no, el fichero se ignora (cuenta como aviso). `keyinfo.json` se ignora y puede borrarse.

## Reglas de fusión

- Las subidas usan `If-Match` (ETag conocido) o `If-None-Match: *` (fichero nuevo): nunca se pisa un cambio remoto que no se haya visto.
- Si un marcador cambió a la vez en dos dispositivos, gana la versión más reciente campo a campo, las **etiquetas se unen** y, si las notas personales difieren, **se conservan las dos**.
- Si un marcador desaparece del servidor sin lápida, pasa a la papelera y se vuelve a subir.
- Si una carpeta sincronizada desaparece del servidor sin lápida, se elimina y sus marcadores quedan sin carpeta.
- Freno de seguridad: si el servidor retiraría de golpe 5 o más marcadores visibles y son al menos la cuarta parte (o 50 o más), o lo mismo con las carpetas, o han desaparecido carpetas y no queda ninguna en el servidor, no se aplica nada de eso: carpetas y marcadores se conservan y se vuelven a subir.
- Un listado WebDAV que no sea un `multistatus` con al menos una respuesta (vacío, página de error de un proxy…) detiene la sincronización sin tocar nada.
