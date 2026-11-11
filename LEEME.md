# Alerta Bloqueo

App Android que, cuando otro celular le envía una alerta, **enciende la pantalla y muestra un mensaje con imagen sobre la pantalla de bloqueo**, con **vibración fuerte y sin sonido**. Se apaga con el botón **Apagar**.

## Obtener el instalador (APK) sin Android Studio

GitHub compila la app gratis en la nube.

1. Crea una cuenta en https://github.com si no tienes.
2. Pulsa **+ → New repository**. Nombre: `AlertaBloqueo`, marca **Public** y pulsa **Create repository**.
3. En el repositorio vacío, pulsa **uploading an existing file**.
4. Descomprime `AlertaBloqueo.zip` en tu PC. Entra a la carpeta `AlertaBloqueo` y **arrastra todo su contenido** a la página (incluida la carpeta `.github`). Pulsa **Commit changes**.
5. Ve a la pestaña **Actions**. Verás "Compilar APK" en marcha; tarda unos 5 minutos. Cuando tenga ✓ verde, ve a **Releases** (columna derecha de la página principal).
6. Desde el celular, abre esa página de Releases y descarga **AlertaBloqueo.apk**. Al abrirlo, Android pedirá permitir "instalar apps desconocidas": acepta.

Si en *Actions* no aparece nada, la carpeta `.github` no se subió (Windows a veces la oculta). Arréglalo así: **Add file → Create new file**, nombre `.github/workflows/compilar-apk.yml`, pega el contenido del archivo `compilar-apk.yml` y pulsa **Commit**.

Play Protect puede avisar "app no reconocida": pulsa **Instalar de todas formas**.

## Usarla

Instala la app en **los dos celulares**.

1. **En el celular que recibe:** abre la app y concede los permisos de la pestaña **Permisos**. Luego, en **Recibir**, pulsa **Compartir mi código**.
2. **En el celular que envía:** en **Enviar**, escribe ese código, el título y el mensaje, elige una imagen (opcional) y pulsa **Enviar alerta**.
3. **Para probar con un solo celular:** escribe tu propio código y bloquea el teléfono después de enviar.

## Cómo se apaga

- **Botón Apagar:** en la pantalla de alerta o en la notificación.
- **Sola:** la vibración se detiene a los 2 minutos.
- **Dejar de recibir:** interruptor **Recibir alertas**, en la pestaña Recibir.

## Importante

- Los mensajes viajan por el servicio público gratuito **ntfy.sh**. Tu código funciona como contraseña: quien lo tenga puede enviarte alertas, así que compártelo solo con gente de confianza. No envíes información privada.
- Las imágenes enviadas se borran del servidor a las pocas horas. Las alertas con más de 30 minutos de antigüedad se ignoran.
- En Xiaomi, Huawei, Oppo, Vivo o Samsung: activa **Sin límite de batería** e **Inicio automático** para la app. Si no lo haces, la alerta puede llegar tarde o no llegar.
- La alerta ocupa toda la pantalla solo si el teléfono está bloqueado o con la pantalla apagada. Si se está usando, aparece como notificación; al tocarla se abre.
- Requiere Android 8.0 o superior.

## Personalizar

| Qué | Archivo |
|---|---|
| Diseño de la alerta | `app/src/main/assets/alerta.html` |
| Panel de la app | `app/src/main/assets/panel.html` |
| Imagen por defecto | `app/src/main/assets/imagen.png` |
| Patrón de vibración | `PATRON` en `Programador.kt` |

Cada vez que cambies un archivo en GitHub se genera un APK nuevo en *Releases*. Se instala encima del anterior.
