# Configurar el build automático y el auto-update

El workflow [build-apk.yml](build-apk.yml) compila y **firma** la APK en cada push a
`master`, y publica una **GitHub Release** con la APK adjunta. La app, al abrir,
consulta la última release y ofrece actualizarse.

Para que todo funcione hay que configurar **4 secrets** en el repositorio.

## 1. Crear el keystore (una sola vez)

> [!IMPORTANT]
> Guarda este fichero `.jks` y las contraseñas en un sitio seguro. Si lo pierdes,
> no podrás firmar actualizaciones que instalen sobre la app ya instalada.

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias scrollfree \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=ScrollFree, O=ScrollFree, C=ES"
```

En Windows, si `keytool` no está en el PATH, usa el del JDK de Android Studio:
`"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.

## 2. Convertir el keystore a Base64

**Linux/macOS:**
```bash
base64 -w0 release.keystore > keystore.b64
```

**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Content -NoNewline keystore.b64
```

## 3. Añadir los secrets en GitHub

En `Settings → Secrets and variables → Actions → New repository secret`:

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | el contenido del fichero `keystore.b64` |
| `KEYSTORE_PASSWORD` | la contraseña del store (la que pusiste al crear el keystore) |
| `KEY_ALIAS` | el alias, p. ej. `scrollfree` |
| `KEY_PASSWORD` | la contraseña de la clave (normalmente igual que la del store) |

## 4. Cómo funciona el versionado

- El workflow calcula `versionCode = 100 + github.run_number` y
  `versionName = 1.0.<run_number>`. Así el `versionCode` **siempre sube**.
- La app compara el `versionCode` de la última release con el suyo
  ([UpdateManager.kt](../app/src/main/java/es/tortugaselectronicas/scrollfree/update/UpdateManager.kt))
  y, si hay uno mayor, muestra el diálogo de actualización.
- El `versionCode` se publica en el cuerpo de la release (`- versionCode: N`) y el
  updater lo lee de ahí (con fallback al sufijo del tag `v1.0.N`).

## 5. Instalación manual la primera vez

La primera vez hay que instalar la APK manualmente (descargándola de la release de
GitHub). A partir de ahí, la app se actualiza sola. Si Android muestra "instalar apps
desconocidas", hay que conceder el permiso una vez (la app abre el ajuste automáticamente).
