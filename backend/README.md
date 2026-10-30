# Servidor de avisos compartidos

Sin servidor, la app funciona igual, pero cada móvil solo ve sus avisos y los de ejemplo. Con servidor, los avisos son comunes a todas las personas usuarias.

El servidor es un único fichero, [worker.js](worker.js), que corre en Cloudflare Workers con una base de datos D1. Las tablas se crean solas.

## Puesta en marcha en Cloudflare

1. Crea una cuenta gratuita en [dash.cloudflare.com](https://dash.cloudflare.com).
2. Crea la base de datos: **Storage & Databases → D1 → Create**, con el nombre `blueway`.
3. Crea el Worker: **Compute → Workers & Pages → Create → Start with Hello World**, con el nombre `blueway`, y pulsa **Deploy**.
4. Abre **Edit code**, sustituye todo el código por el de `worker.js` y pulsa **Deploy**.
5. En el Worker, abre **Settings → Bindings → Add → D1 database**. Pon `DB` como nombre de variable, elige `blueway` y guarda.
6. Copia la dirección del Worker, del tipo `https://blueway.TU-USUARIO.workers.dev`, y ponla en `backend.properties`:

   ```properties
   REPORTS_API_URL=https://blueway.TU-USUARIO.workers.dev
   ```

7. Haz push a `main`: GitHub Actions genera el APK conectado al servidor.

Los nombres de los menús de Cloudflare pueden cambiar con el tiempo. Lo importante es que la base de datos quede enlazada con el nombre de variable exacto `DB`.

## Seguridad

- Solo hay cuatro operaciones: `list_reports`, `create_report`, `vote_report` y `delete_report`. La base de datos no es accesible desde fuera.
- El servidor valida identificadores, tipo, zona (Málaga capital) y tamaño de la petición, redondea la ubicación a unos 11 m y borra los avisos una semana después de caducar.
- No hay cuentas ni claves en la app. Cada instalación usa un identificador aleatorio que nunca se devuelve a otros móviles.
- Límites: 5 avisos cada 10 minutos por instalación, 60 por minuto en total, un voto por instalación y aviso.
- **Limitación conocida:** el identificador se puede falsificar, así que estos límites frenan abusos casuales, no un ataque dirigido. Antes de un uso público conviene añadir autenticación y moderación.

## Pruebas

`local_server.mjs` ejecuta el `worker.js` real sobre SQLite, el motor de D1, con Node 24 o superior. Reinícialo antes de cada pasada, porque los tests esperan una base de datos vacía:

```powershell
node backend\test\local_server.mjs 54330
python backend\test\test_api.py http://localhost:54330
```

Para probar la app contra él desde el emulador:

```powershell
.\gradlew.bat :app:assembleDebug -PreportsApiUrl=http://10.0.2.2:54330
```
