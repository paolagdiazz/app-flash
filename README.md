# BlueWay

**Dirección a la calma.** App Android que ayuda a personas autistas y con sensibilidad sensorial a planificar sus desplazamientos a pie por Málaga anticipando el entorno sonoro.

BlueWay informa, pero no garantiza que una ruta sea silenciosa, segura o adecuada para todas las personas.

## Probar la app en el móvil

### Opción 1: descargar el APK (sin cable)

1. En el móvil, abre GitHub con tu sesión iniciada y entra en **Releases → latest** de este repositorio.
2. Descarga `BlueWay.apk` y ábrelo. La primera vez, Android pedirá permitir instalar apps de ese navegador.

Cada push a `main` genera una versión nueva, que se instala encima de la anterior sin perder datos.

### Opción 2: por USB, desde el PC

1. En el Samsung: **Ajustes → Acerca del teléfono → Información de software**, y pulsa 7 veces en **Número de compilación**.
2. **Ajustes → Opciones de desarrollador → Depuración USB**, conecta el cable y acepta el aviso del teléfono.
3. Ejecuta:

```powershell
.\instalar-en-movil.ps1
```

## Qué incluye esta versión

| Pantalla del diseño | Estado |
| --- | --- |
| Bienvenida | Funcional, 4 páginas |
| Inicio con mapa y ruta | Mapa real de Málaga, ruido por calle, 3 tipos de ruta |
| Búsqueda de destino | Calles y lugares sin conexión, búsqueda ampliada con internet, voz |
| Navegación guiada | GPS, indicaciones paso a paso, recálculo, voz opcional |
| Ficha de zona | Nivel de ruido, equipamiento real, afluencia, curva de 24 h |
| Filtros de ruta | Cambian de verdad el recorrido |
| Avisos | Crear, confirmar, descartar, caducar y evitar duplicados; compartidos entre móviles si hay servidor configurado |
| Leyenda de ruido | Niveles y origen de cada dato |
| Perfil | Lugares, rutas guardadas, notificaciones, alto contraste, reducir movimiento |

## Qué datos son reales

| Dato | Origen | Cómo se muestra |
| --- | --- | --- |
| Calles, parques, bancos, cafeterías, arbolado | OpenStreetMap (descarga del 30/10/2026) | Real |
| Ruido por calle | Modelo propio según tipo de vía, cercanía al tráfico y hora | **Estimado** |
| Sensores | 8 puntos de ejemplo que derivan del modelo | **Sensor simulado** |
| Avisos marcados como «Ejemplo» | Creados al abrir la app, solo si no hay servidor | **Ejemplo** |
| Avisos de personas usuarias | Creados en los teléfonos | **Avisado** |

Limitaciones conocidas:

- Solo cubre Málaga capital.
- Los avisos solo se comparten entre móviles si `backend.properties` apunta a un servidor (ver [backend/README.md](backend/README.md)).
- El modelo de ruido no está calibrado con mediciones reales.
- La app no se ha validado con personas autistas.
- La firma del APK es de desarrollo: no sirve para publicar en Google Play.

## Arquitectura

- **Kotlin + Jetpack Compose**, sin API keys ni cuentas.
- **MapLibre** con teselas de OpenFreeMap y estilo propio (`app/src/main/assets/map_style.json`).
- **Rutas en el propio móvil**: A* sobre el grafo peatonal de Málaga con costes según ruido, afluencia, zonas verdes y accesibilidad (`engine/RouteEngine.kt`).
- **Persistencia local** en SharedPreferences: no hay recorridos GPS ni audio guardados.

```text
app/src/main/java/com/blueway/app/
  AppViewModel.kt   estado, navegación, avisos
  data/             modelos, almacenamiento, reglas de avisos, búsqueda
  engine/           grafo, modelo de ruido, cálculo de rutas
  ui/               mapa y pantallas
tools/              scripts que generan los datos y recursos
```

## Desarrollo

Requisitos: JDK 21 y Android SDK con la plataforma 37.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
```

Regenerar los datos de la ciudad:

```powershell
python tools\build_city_data.py <carpeta-cache> app\src\main\assets\city
```

## Créditos

Datos © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL). Mapa base: [OpenFreeMap](https://openfreemap.org) y OpenMapTiles. Búsqueda: [Photon](https://photon.komoot.io). Tipografía: Nunito (OFL, ver `docs/Nunito-OFL.txt`). Motor de mapa: MapLibre.
