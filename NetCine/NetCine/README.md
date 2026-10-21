# NetCine — catálogo del proveedor en el Fire Stick

App para Fire TV / Android TV que abre el catálogo de películas y series de tu proveedor
(`http://10.1.1.219/?index`) y reproduce los videos con un reproductor nativo hecho para el control remoto.

## Cómo funciona

- **Catálogo:** se muestra la misma web del proveedor (ya viene preparada para navegar con flechas),
  con retoques para TV: borde naranja grueso en lo que está seleccionado, el foco siempre a la vista
  y sin el botón de "descargar app".
- **Reproducción:** cuando elegís "Reproducir" o un episodio, la app lee la página de reproducción,
  saca la URL directa del MP4 y lo abre en ExoPlayer (el reproductor de Google). Así no dependemos del
  reproductor web, que es lo que suele fallar en el Fire Stick.
- Como la app usa la web en vivo, si el proveedor agrega contenido o cambia el diseño, se ve solo.

## Control remoto

| Dónde | Botón | Qué hace |
|---|---|---|
| Catálogo | Flechas + OK | Navegar y elegir |
| Catálogo | ☰ (Menú) | Abre el menú (géneros, búsqueda, etc.) |
| Catálogo | Atrás | Vuelve; en el inicio, dos veces para salir |
| Video | OK | Pausa / sigue |
| Video | ◀ / ▶ | −10 s / +30 s |
| Video | ⏪ ⏯ ⏩ | Igual que en cualquier reproductor |
| Video | Atrás | Sale del video (recuerda por dónde ibas) |

En series, al terminar un episodio arranca solo el siguiente.

## Compilar el APK

**Opción A — GitHub (sin instalar nada):** subí esta carpeta a un repo de GitHub. El workflow
`.github/workflows/build.yml` compila solo; el APK queda en la pestaña **Actions → (la corrida) → Artifacts → NetCine-apk**.

**Opción B — Android Studio:** abrir la carpeta, esperar que sincronice y `Build → Build APK(s)`.
O por consola: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Instalar en el Fire Stick

1. En el Fire Stick: **Configuración → Mi Fire TV → Opciones para desarrolladores** y activá
   **Depuración ADB** y **Apps de origen desconocido** (si no ves "Opciones para desarrolladores",
   entrá a *Mi Fire TV → Acerca de* y apretá OK 7 veces sobre el nombre del dispositivo).
2. Anotá la IP del Fire Stick (*Mi Fire TV → Acerca de → Red*).
3. Desde la PC, en la misma red:
   ```
   adb connect IP_DEL_FIRESTICK:5555
   adb install -r app-release.apk
   ```
   (La primera vez el Fire Stick pregunta si permitís la depuración: aceptá.)

Alternativa sin PC: instalá la app **Downloader** desde la tienda de Amazon y descargá el APK desde
un link (por ejemplo, subiéndolo a Google Drive o Dropbox con link directo).

La app aparece en **Tus apps y canales** como *NetCine*; podés moverla a la fila de inicio.

## Ajustes

- Si cambia la dirección del catálogo: editar `BASE_URL` en `app/build.gradle.kts`.
- Solo funciona conectado al WiFi del proveedor (es una dirección de su red interna).
- El progreso de "seguir viendo" se guarda en el Fire Stick; no se sincroniza con la app del celular.
