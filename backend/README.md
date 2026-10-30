# Servidor de avisos compartidos

Sin servidor, la app funciona igual, pero cada móvil solo ve sus avisos y los de ejemplo. Con servidor, los avisos son comunes a todas las personas usuarias.

## Puesta en marcha con Supabase

1. Crea una cuenta y un proyecto en [supabase.com](https://supabase.com). Elige una región europea.
2. En el proyecto, abre **SQL Editor**, pega el contenido de [schema.sql](schema.sql) y ejecútalo.
3. En **Project Settings → API**, copia la URL del proyecto y la clave pública (`anon` o `publishable`). **Nunca uses la `service_role` o `secret`.**
4. Rellena `backend.properties`:

   ```properties
   REPORTS_API_URL=https://TU-PROYECTO.supabase.co/rest/v1
   REPORTS_API_KEY=tu-clave-publica
   ```

5. Haz push a `main`: GitHub Actions genera el APK conectado al servidor.

## Seguridad

- La clave pública solo permite ejecutar `list_reports`, `create_report`, `vote_report` y `delete_report`. Las tablas no son accesibles directamente.
- El servidor valida el tipo, la zona (Málaga capital) y la longitud del texto, redondea la ubicación a unos 11 m y borra los avisos una semana después de caducar.
- No hay cuentas. Cada instalación usa un identificador aleatorio que nunca se devuelve a otros móviles.
- Límites: 5 avisos cada 10 minutos por instalación, 60 por minuto en total, un voto por instalación y aviso.
- **Limitación conocida:** el identificador se puede falsificar, así que estos límites frenan abusos casuales, no un ataque dirigido. Antes de un uso público conviene añadir autenticación anónima y moderación.

## Pruebas

```powershell
python backend\test\mock_server.py 54330
python backend\test\test_api.py http://localhost:54330
```

`mock_server.py` imita la API en memoria y sirve para probar la app, pero no ejecuta `schema.sql`. Para comprobar el SQL real, lanza los tests contra una base de datos de prueba. **Los tests crean datos que no se borran solos:**

```powershell
$env:BLUEWAY_API_KEY = "tu-clave-publica"
python backend\test\test_api.py https://TU-PROYECTO.supabase.co/rest/v1
```

También puedes usar la réplica local de `backend/test/docker-compose.yml`, que levanta PostgreSQL y PostgREST con `schema.sql`.

Para probar la app contra el mock desde el emulador:

```powershell
.\gradlew.bat :app:assembleDebug -PreportsApiUrl=http://10.0.2.2:54330
```
