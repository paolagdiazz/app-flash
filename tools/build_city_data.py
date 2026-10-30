#!/usr/bin/env python3
"""Construye los datos locales de Málaga a partir de OpenStreetMap (ODbL).

Genera en app/src/main/assets/city:
  graph.bwg   grafo peatonal con nivel de ruido ESTIMADO y afluencia ESTIMADA por tramo
  noise.geojson  calles coloreables por ruido para el mapa
  pois.json      bancos, cafeterías, arbolado, parques y lugares por categoría

Modelo de ruido: es una estimación orientativa por tipo de vía y distancia a vías con tráfico.
No es una medición. La app lo etiqueta siempre como "Estimado".

Uso: python tools/build_city_data.py <carpeta de caché> <carpeta de salida>
"""
import gzip
import json
import math
import struct
import sys
import time
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

CACHE, OUT = Path(sys.argv[1]), Path(sys.argv[2])
CACHE.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
BBOX = (36.685, -4.515, 36.755, -4.345)  # sur, oeste, norte, este
B = ",".join(map(str, BBOX))
HIGHWAYS = ("motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|"
            "tertiary|tertiary_link|unclassified|residential|living_street|pedestrian|service|footway|"
            "path|steps|track|cycleway")
QUERIES = {
    "ways": f'[out:json][timeout:240];(way["highway"~"^({HIGHWAYS})$"]({B}););out body geom qt;',
    "pois": f"""[out:json][timeout:240];(
      node["amenity"~"^(bench|cafe|restaurant|bar|pub|fast_food|ice_cream)$"]({B});
      node["shop"]({B});
      node["natural"="tree"]({B});
      way["natural"="tree_row"]({B});
      nwr["amenity"~"^(university|college|hospital|clinic|library|theatre|arts_centre|bus_station|ferry_terminal)$"]["name"]({B});
      nwr["tourism"~"^(museum|viewpoint|gallery)$"]["name"]({B});
      nwr["historic"="castle"]["name"]({B});
      nwr["railway"~"^(station|halt)$"]["name"]({B});
      nwr["natural"="beach"]["name"]({B});
    );out center tags qt;""",
    "parks": f"""[out:json][timeout:240];(
      way["leisure"~"^(park|garden|nature_reserve)$"]({B});
      relation["leisure"~"^(park|garden|nature_reserve)$"]({B});
      way["landuse"~"^(forest|village_green|recreation_ground)$"]({B});
      way["natural"="wood"]({B});
    );out body geom qt;""",
}


def fetch(name):
    path = CACHE / f"{name}.json"
    if not path.exists():
        for url in ("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"):
            try:
                req = urllib.request.Request(url, urllib.parse.urlencode({"data": QUERIES[name]}).encode(),
                                             {"User-Agent": "BlueWay-data-build/1.0"})
                path.write_bytes(urllib.request.urlopen(req, timeout=280).read())
                break
            except Exception as e:  # noqa: BLE001
                print("Overpass falló", url, e)
                time.sleep(5)
    return json.loads(path.read_text(encoding="utf-8"))["elements"]


KX = 111320 * math.cos(math.radians(36.72))
KY = 110574.0


def dist(a, b):
    return math.hypot((a[0] - b[0]) * KY, (a[1] - b[1]) * KX)


def cell(p, size):
    return int(p[0] * KY // size), int(p[1] * KX // size)


def simplify(pts, tol=3.0):
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        ax, ay = pts[i][1] * KX, pts[i][0] * KY
        bx, by = pts[j][1] * KX, pts[j][0] * KY
        dx, dy = bx - ax, by - ay
        ln = dx * dx + dy * dy
        best, bk = 0.0, -1
        for k in range(i + 1, j):
            px, py = pts[k][1] * KX, pts[k][0] * KY
            t = 0 if ln == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / ln))
            d = math.hypot(px - ax - t * dx, py - ay - t * dy)
            if d > best:
                best, bk = d, k
        if best > tol:
            keep[bk] = True
            stack += [(i, bk), (bk, j)]
    return [p for p, k in zip(pts, keep) if k]


BASE_DB = {
    "motorway": 78, "motorway_link": 74, "trunk": 76, "trunk_link": 72, "primary": 73, "primary_link": 70,
    "secondary": 69, "secondary_link": 66, "tertiary": 64, "tertiary_link": 62, "unclassified": 60,
    "residential": 56, "living_street": 52, "pedestrian": 55, "service": 53, "footway": 51, "cycleway": 53,
    "path": 46, "track": 46, "steps": 49,
}
SOURCE_CLASSES = {k for k, v in BASE_DB.items() if v >= 62}
NOT_WALKABLE = {"motorway", "motorway_link", "trunk", "trunk_link"}
OVERLAY_CLASSES = {"motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary",
                   "secondary_link", "tertiary", "tertiary_link", "unclassified", "residential",
                   "living_street", "pedestrian"}

ways = fetch("ways")
pois = fetch("pois")
parks_raw = fetch("parks")
print("ways", len(ways), "pois", len(pois), "parks", len(parks_raw))


# --- Puntos de interés ---
def center(e):
    if "center" in e:
        return e["center"]["lat"], e["center"]["lon"]
    if "lat" in e:
        return e["lat"], e["lon"]
    return None


benches, cafes, trees, crowd_pts = [], [], [], []
places = []
CATEGORY = [
    ("education", lambda t: t.get("amenity") in ("university", "college")),
    ("health", lambda t: t.get("amenity") in ("hospital", "clinic")),
    ("transport", lambda t: t.get("railway") in ("station", "halt") or t.get("amenity") in ("bus_station", "ferry_terminal")),
    ("culture", lambda t: t.get("tourism") in ("museum", "gallery") or t.get("amenity") in ("theatre", "library", "arts_centre") or t.get("historic") == "castle"),
    ("calm", lambda t: t.get("tourism") == "viewpoint" or t.get("natural") == "beach"),
]
for e in pois:
    t = e.get("tags", {})
    if e["type"] == "way" and t.get("natural") == "tree_row":
        c = center(e)
        if c:
            trees.append(c)
        continue
    c = center(e)
    if not c:
        continue
    am = t.get("amenity")
    if am == "bench":
        benches.append(c)
    elif t.get("natural") == "tree":
        trees.append(c)
    elif am in ("cafe", "restaurant", "bar", "pub", "fast_food", "ice_cream") or "shop" in t:
        crowd_pts.append(c)
        if am == "cafe":
            cafes.append(c)
    if t.get("name"):
        for cat, test in CATEGORY:
            if test(t):
                detail = t.get("addr:street") or t.get("addr:suburb") or ""
                places.append({"n": t["name"], "c": cat, "d": detail, "la": round(c[0], 5), "lo": round(c[1], 5),
                               "w": 1 if "wikidata" in t else 0})
                break

crowd_hash = defaultdict(int)
for p in crowd_pts:
    crowd_hash[cell(p, 30)] += 1


def crowd_at(p):
    cx, cy = cell(p, 30)
    return min(1.0, sum(crowd_hash.get((cx + i, cy + j), 0) for i in range(-2, 3) for j in range(-2, 3)) / 15.0)


# --- Parques: rasterizados en rejilla de 20 m ---
def pip(pt, poly):
    y, x = pt
    inside = False
    n = len(poly)
    for i in range(n):
        y1, x1 = poly[i]
        y2, x2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside


park_cells = {}
parks = []
for e in parks_raw:
    rings = []
    if e["type"] == "way" and "geometry" in e:
        rings = [[(g["lat"], g["lon"]) for g in e["geometry"]]]
    elif e["type"] == "relation":
        rings = [[(g["lat"], g["lon"]) for g in m["geometry"]]
                 for m in e.get("members", []) if m.get("role") == "outer" and "geometry" in m]
    for ring in rings:
        if len(ring) < 4 or ring[0] != ring[-1]:
            continue
        la0, la1 = min(p[0] for p in ring), max(p[0] for p in ring)
        lo0, lo1 = min(p[1] for p in ring), max(p[1] for p in ring)
        area = abs(sum((ring[i][1] * KX) * (ring[i + 1][0] * KY) - (ring[i + 1][1] * KX) * (ring[i][0] * KY)
                       for i in range(len(ring) - 1))) / 2
        if area < 800 or area > 3_000_000:
            continue
        idx = len(parks)
        name = e.get("tags", {}).get("name", "")
        parks.append({"n": name, "la": round((la0 + la1) / 2, 5), "lo": round((lo0 + lo1) / 2, 5), "a": int(area)})
        cx0, cy0 = cell((la0, lo0), 20)
        cx1, cy1 = cell((la1, lo1), 20)
        for cx in range(cx0, cx1 + 1):
            for cy in range(cy0, cy1 + 1):
                if (cx, cy) in park_cells:
                    continue
                if pip(((cx + .5) * 20 / KY, (cy + .5) * 20 / KX), ring):
                    park_cells[(cx, cy)] = idx
print("parques", len(parks), "celdas", len(park_cells))

# --- Fuentes de ruido y nombres cercanos ---
sources = {}
named = defaultdict(list)
for w in ways:
    t = w.get("tags", {})
    hw = t.get("highway")
    if "geometry" not in w:
        continue
    geom = [(g["lat"], g["lon"]) for g in w["geometry"]]
    level = BASE_DB.get(hw, 50)
    is_source = hw in SOURCE_CLASSES
    has_name = t.get("name") and hw in OVERLAY_CLASSES
    if not (is_source or has_name):
        continue
    for a, b in zip(geom, geom[1:]):
        steps = max(1, int(dist(a, b) // 20))
        for s in range(steps + 1):
            p = (a[0] + (b[0] - a[0]) * s / steps, a[1] + (b[1] - a[1]) * s / steps)
            if is_source:
                c = cell(p, 50)
                if c not in sources or sources[c][1] < level:
                    sources[c] = (p, level)
            if has_name:
                named[cell(p, 30)].append((p, t["name"]))


def noise_from_sources(p):
    cx, cy = cell(p, 50)
    best = 0.0
    for i in range(-3, 4):
        for j in range(-3, 4):
            s = sources.get((cx + i, cy + j))
            if not s:
                continue
            d = dist(p, s[0])
            if d > 170:
                continue
            level = s[1] - 10 * math.log10(max(d, 7) / 7)
            if d > 30:
                level -= 4  # los edificios y la distancia apantallan parte del sonido
            best = max(best, level)
    return best


def nearby_name(p):
    cx, cy = cell(p, 30)
    best, bd = "", 22.0
    for i in range(-1, 2):
        for j in range(-1, 2):
            for q, n in named.get((cx + i, cy + j), ()):
                d = dist(p, q)
                if d < bd:
                    best, bd = n, d
    return best


# --- Grafo peatonal ---
def walkable(t):
    hw = t.get("highway")
    if hw in NOT_WALKABLE and t.get("foot") not in ("yes", "designated"):
        return False
    if t.get("foot") == "no" or t.get("access") in ("private", "no") and t.get("foot") not in ("yes", "designated"):
        return False
    if t.get("service") in ("driveway", "parking_aisle"):
        return False
    if t.get("area") == "yes" and hw != "pedestrian":
        return False
    return True


node_use = defaultdict(int)
walk_ways = [w for w in ways if "geometry" in w and walkable(w.get("tags", {}))]
for w in walk_ways:
    ns = w["nodes"]
    for n in ns:
        node_use[n] += 1
    node_use[ns[0]] += 1
    node_use[ns[-1]] += 1

edges = []
for w in walk_ways:
    t = w.get("tags", {})
    ns, geom = w["nodes"], [(g["lat"], g["lon"]) for g in w["geometry"]]
    start = 0
    for i in range(1, len(ns)):
        if node_use[ns[i]] >= 2 or i == len(ns) - 1:
            edges.append((ns[start], ns[i], geom[start:i + 1], t, w["id"]))
            start = i

adj = defaultdict(list)
for i, e in enumerate(edges):
    adj[e[0]].append(i)
    adj[e[1]].append(i)
seen, best = set(), set()
for start in adj:
    if start in seen:
        continue
    comp, stack = {start}, [start]
    seen.add(start)
    while stack:
        n = stack.pop()
        for ei in adj[n]:
            m = edges[ei][1] if edges[ei][0] == n else edges[ei][0]
            if m not in seen:
                seen.add(m)
                comp.add(m)
                stack.append(m)
    if len(comp) > len(best):
        best = comp
edges = [e for e in edges if e[0] in best]
print("aristas", len(edges), "nodos", len(best))

node_index, node_pts = {}, []
names, name_index = [""], {"": 0}
E_FROM, E_TO, E_LEN, E_DB, E_CROWD, E_FLAG, E_NAME, E_PARK = [], [], [], [], [], [], [], []
GEOM_OFF, GEOM = [0], []
FLAG_STEPS, FLAG_GREEN, FLAG_ROUGH, FLAG_BAD_WHEEL = 1, 2, 4, 8
overlay = defaultdict(list)


def node_id(n, p):
    if n not in node_index:
        node_index[n] = len(node_pts)
        node_pts.append(p)
    return node_index[n]


db_hist = defaultdict(float)
for a, b, geom, t, wid in edges:
    hw = t.get("highway")
    length = sum(dist(p, q) for p, q in zip(geom, geom[1:]))
    if length < 0.5:
        continue
    mid = geom[len(geom) // 2]
    crowd = crowd_at(mid)
    base = BASE_DB.get(hw, 50)
    src = noise_from_sources(mid)
    db = max(float(base), src)
    if hw in ("pedestrian", "footway", "living_street", "steps"):
        db += 9 * crowd
    elif hw in ("residential", "service", "unclassified"):
        db += 3 * crowd
    park = park_cells.get(cell(mid, 20), -1)
    flags = 0
    if park >= 0:
        flags |= FLAG_GREEN
        db -= 3
    if hw == "steps":
        flags |= FLAG_STEPS | FLAG_BAD_WHEEL
    if t.get("wheelchair") == "no":
        flags |= FLAG_BAD_WHEEL
    if t.get("surface") in ("gravel", "ground", "dirt", "sand", "unpaved", "cobblestone", "sett", "mud", "grass") or hw in ("track", "path"):
        flags |= FLAG_ROUGH
    db = max(40.0, min(85.0, db))
    db_hist[int(db // 5) * 5] += length
    name = t.get("name") or (nearby_name(mid) if hw in ("footway", "cycleway", "path", "service", "steps") else "")
    if name not in name_index:
        name_index[name] = len(names)
        names.append(name)
    E_FROM.append(node_id(a, geom[0]))
    E_TO.append(node_id(b, geom[-1]))
    E_LEN.append(length)
    E_DB.append(int(round(db)))
    E_CROWD.append(int(crowd * 100))
    E_FLAG.append(flags)
    E_NAME.append(name_index[name])
    E_PARK.append(park)
    interior = simplify(geom)[1:-1]
    for p in interior:
        GEOM += [int(round(p[0] * 1e5)), int(round(p[1] * 1e5))]
    GEOM_OFF.append(len(GEOM) // 2)
    if hw in OVERLAY_CLASSES:
        overlay[(wid, int(round(db)))].append(simplify(geom))

# Vías con tráfico no peatonales: se pintan en el mapa como fuente de ruido, pero no se camina por ellas.
for w in ways:
    t = w.get("tags", {})
    if "geometry" in w and not walkable(t) and t.get("highway") in OVERLAY_CLASSES:
        geom = [(g["lat"], g["lon"]) for g in w["geometry"]]
        overlay[(w["id"], BASE_DB[t["highway"]])].append(simplify(geom))

head = struct.pack(">4i", *(int(v * 1e5) for v in BBOX))
buf = bytearray(b"BWG1") + head
buf += struct.pack(">i", len(node_pts))
buf += struct.pack(f">{len(node_pts)}i", *(int(round(p[0] * 1e5)) for p in node_pts))
buf += struct.pack(f">{len(node_pts)}i", *(int(round(p[1] * 1e5)) for p in node_pts))
n = len(E_FROM)
buf += struct.pack(">i", n)
buf += struct.pack(f">{n}i", *E_FROM)
buf += struct.pack(f">{n}i", *E_TO)
buf += struct.pack(f">{n}f", *E_LEN)
buf += struct.pack(f">{n}i", *E_NAME)
buf += struct.pack(f">{n}i", *E_PARK)
buf += bytes(E_DB) + bytes(E_CROWD) + bytes(E_FLAG)
buf += struct.pack(f">{n + 1}i", *GEOM_OFF)
buf += struct.pack(">i", len(GEOM))
buf += struct.pack(f">{len(GEOM)}i", *GEOM)
blob = "\n".join(names).encode("utf-8")
buf += struct.pack(">i", len(blob)) + blob
(OUT / "graph.bwg").write_bytes(gzip.compress(bytes(buf), 9))

features = []
for (wid, db), parts in overlay.items():
    coords = [[[round(p[1], 5), round(p[0], 5)] for p in part] for part in parts]
    features.append({"type": "Feature", "properties": {"d": db},
                     "geometry": {"type": "MultiLineString", "coordinates": coords}})
(OUT / "noise.geojson").write_text(json.dumps({"type": "FeatureCollection", "features": features},
                                              separators=(",", ":")), encoding="utf-8")


def ints(points):
    out = []
    for p in points:
        out += [int(round(p[0] * 1e5)), int(round(p[1] * 1e5))]
    return out


# Lugares: sin duplicados, priorizando los que tienen referencia en Wikidata.
uniq = {}
for p in sorted(places, key=lambda x: -x["w"]):
    uniq.setdefault((p["c"], p["n"].lower()), {k: p[k] for k in ("n", "c", "d", "la", "lo")})
place_list = list(uniq.values())
named_parks = sorted((p for p in parks if p["n"]), key=lambda p: -p["a"])
seen_parks, clean_parks = set(), []
for p in named_parks:
    if p["n"].lower() not in seen_parks:
        seen_parks.add(p["n"].lower())
        clean_parks.append(p)
(OUT / "pois.json").write_text(json.dumps({
    "benches": ints(benches), "cafes": ints(cafes), "trees": ints(trees),
    "parks": clean_parks[:80], "places": place_list,
}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

for f in OUT.iterdir():
    print(f.name, f.stat().st_size)
print("bancos", len(benches), "cafes", len(cafes), "arboles", len(trees), "lugares", len(place_list), "parques", len(clean_parks))
total = sum(db_hist.values())
for k in sorted(db_hist):
    print(f"{k} dB: {db_hist[k] / total:.1%}")


def street_db(q):
    vals = [(E_DB[i], E_LEN[i]) for i in range(n) if names[E_NAME[i]].lower() == q.lower()]
    if not vals:
        return None
    return round(sum(a * b for a, b in vals) / sum(b for _, b in vals), 1)


for q in ("Calle Larios", "Alameda Principal", "Calle Alcazabilla", "Paseo del Parque", "Avenida de Andalucía", "Calle Granada", "Calle Mármoles", "Muelle Uno"):
    print(q, street_db(q))
