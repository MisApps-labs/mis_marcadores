# Seguridad

## Informar de una vulnerabilidad

**No abras una incidencia pública** para fallos de seguridad. Usa el aviso privado de GitHub:
pestaña **Security → Report a vulnerability** del repositorio
([enlace directo](https://github.com/ferlagod/mis_marcadores/security/advisories/new)).

Indica la versión de la app, los pasos para reproducirlo y el impacto. Intentaré responder en un plazo de 7 días.

Este es un proyecto personal mantenido en el tiempo libre y **no ha pasado una auditoría de seguridad independiente**.

## Modelo de amenazas

Los marcadores **no se cifran de extremo a extremo**: se guardan en tu Nextcloud como JSON legible (ver [docs/SYNC_FORMAT.md](docs/SYNC_FORMAT.md)). Quien administre o acceda al servidor puede ver y modificar tus enlaces, así que usa un servidor de tu confianza.

| Protegido | No protegido |
|-----------|--------------|
| Tráfico entre el teléfono y el servidor (HTTPS) | Contenido de los marcadores frente a quien controla el servidor |
| Credenciales de Nextcloud en el dispositivo (Android Keystore) | Un teléfono desbloqueado y comprometido (root, malware) |

En el teléfono la base de datos de marcadores **no está cifrada por la app**: depende del cifrado de almacenamiento de Android y del aislamiento entre apps. Los marcadores se excluyen de la copia de seguridad en la nube de Google.

## Diseño

### Almacenamiento local

- Credenciales de Nextcloud: `EncryptedSharedPreferences` (AES-256-GCM / AES-256-SIV con clave maestra en Android Keystore).
- El bloqueo opcional de la app (huella, cara o PIN) solo impide abrirla; no cifra los datos.

### Transporte

- HTTPS con la validación de certificados por defecto de Android. La app no acepta tráfico HTTP sin cifrar ni certificados autofirmados que no estén instalados en el sistema.
- Autenticación con *contraseña de aplicación* de Nextcloud (HTTP Basic sobre TLS).

### Conexiones a las webs guardadas

- Para obtener el título, la descripción y la imagen de un enlace, la app descarga la cabecera de **esa misma página** (como haría el navegador). No se usa ningún servicio intermedio, así que ningún tercero recibe la lista de tus enlaces. Se puede desactivar en Ajustes → Enlaces → «Obtener datos de la página».
- Los iconos y las imágenes de vista previa se descargan de cada web. Se puede desactivar en Ajustes → Enlaces → «Mostrar iconos y vistas previas».
- «Comprobar enlaces» solo se ejecuta cuando lo pides y hace una petición a cada dirección guardada.

## Limitaciones conocidas

- El servidor ve y puede modificar el contenido de los marcadores.
