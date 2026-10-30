#!/usr/bin/env python3
"""Pruebas de la API de avisos.

Uso: python backend/test/test_api.py [url]
Necesita una base de datos sin avisos cerca de los puntos de prueba. Al terminar borra lo que ha creado.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:54330"
LARIOS = {"p_lat": 36.71923, "p_lon": -4.42133}
fails = []


def call(path, body=None, method="POST"):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "User-Agent": "BlueWay-tests"}
    req = urllib.request.Request(BASE + path, data, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, json.loads(raw) if raw else None


def rpc(name, **args):
    return call(f"/rpc/{name}", args)


def check(name, ok, detail=""):
    print(("OK   " if ok else "FALLO"), name, detail if not ok else "")
    if not ok:
        fails.append(name)


def error_of(result):
    return (result[1] or {}).get("message") if result[0] >= 400 else None


def new_device():
    return str(uuid.uuid4())


created = []


def create(device, type_="WORKS", lat=LARIOS["p_lat"], lon=LARIOS["p_lon"], text=""):
    result = rpc("create_report", p_device=device, p_type=type_, p_lat=lat, p_lon=lon, p_description=text)
    if result[0] == 200 and not result[1][0]["merged"]:
        created.append((device, result[1][0]["id"]))
    return result


def find(device, rid):
    return next((r for r in rpc("list_reports", p_device=device)[1] if r["id"] == rid), None)


a, b, c = new_device(), new_device(), new_device()

# Acceso directo a las tablas: debe estar prohibido.
for table in ("bw_reports", "bw_votes"):
    check(f"lectura directa de {table} prohibida", call(f"/{table}", method="GET")[0] in (401, 403, 404))
    check(f"escritura directa en {table} prohibida", call(f"/{table}", {"device_id": a})[0] in (401, 403, 404))
check("funciones internas no expuestas", rpc("bw_type_hours", p_type="WORKS")[0] in (401, 403, 404))

# Alta y privacidad
status, rows = create(a, text="  Obras\n en Larios ")
check("crear aviso", status == 200 and rows[0]["merged"] is False, str(rows))
rid = rows[0]["id"]
mine = find(a, rid)
check("aparece para quien lo crea", mine and mine["mine"] is True)
other = find(b, rid)
check("aparece para otra persona", other and other["mine"] is False)
check("device_id no se expone", other and "device_id" not in other)
check("ubicación aproximada", other and other["lat"] == 36.7192 and other["lon"] == -4.4213, str(other))
check("descripción limpia", other and other["description"] in ("Obras  en Larios", "Obras en Larios"), str(other))

# Validación
check("tipo no válido", error_of(create(a, "HACK")) == "invalid_type")
check("fuera de Málaga", error_of(create(a, lat=40.4, lon=-3.7)) == "out_of_area")
long_id = create(c, "GENERAL", 36.7300, -4.4000, "x" * 500)
check("descripción recortada", find(c, long_id[1][0]["id"])["description"] == "x" * 120)
inj = create(c, "EVENT", 36.7310, -4.4010, "'); drop table bw_reports; --")
check("inyección SQL inofensiva", inj[0] == 200 and rpc("list_reports", p_device=c)[0] == 200)

# Peticiones malformadas
check("identificador no válido", error_of(rpc("list_reports", p_device="hola")) == "invalid_device")
check("coordenadas no numéricas", error_of(rpc("create_report", p_device=a, p_type="WORKS", p_lat="x", p_lon="y", p_description="")) == "out_of_area")
check("tipo heredado de JS rechazado", error_of(create(a, "constructor")) == "invalid_type")
check("cuerpo demasiado grande", call("/rpc/list_reports", {"p_device": a, "relleno": "x" * 3000})[0] == 413)
check("método GET no permitido", call("/rpc/list_reports", method="GET")[0] == 404)

# Duplicados
dup = create(b)
check("duplicado cercano se fusiona", dup[1][0]["merged"] is True and dup[1][0]["id"] == rid, str(dup))
check("la fusión suma confirmación", find(a, rid)["confirmations"] == 2)
create(b)
check("no se puede reforzar dos veces", find(a, rid)["confirmations"] == 2)
create(a)
check("no se refuerza el propio", find(a, rid)["confirmations"] == 2)
other_type = create(b, "TRAFFIC")
check("otro tipo no se fusiona", other_type[1][0]["merged"] is False)
far = create(b, "WORKS", 36.7000, -4.4800)
check("lejos no se fusiona", far[1][0]["merged"] is False)

# Votos
d = new_device()
check("voto propio rechazado", error_of(rpc("vote_report", p_device=a, p_id=rid, p_still_there=True)) == "own_report")
check("voto válido", rpc("vote_report", p_device=d, p_id=rid, p_still_there=True)[0] in (200, 204))
check("voto suma", find(a, rid)["confirmations"] == 3)
check("marca como votado", find(d, rid)["voted"] is True)
check("doble voto rechazado", error_of(rpc("vote_report", p_device=d, p_id=rid, p_still_there=False)) == "already_voted")
check("voto a aviso inexistente", error_of(rpc("vote_report", p_device=d, p_id=str(uuid.uuid4()), p_still_there=True)) == "not_found")

e, f = new_device(), new_device()
target = create(new_device(), "NOISE_HIGH", 36.7400, -4.4500)[1][0]["id"]
rpc("vote_report", p_device=e, p_id=target, p_still_there=False)
rpc("vote_report", p_device=f, p_id=target, p_still_there=False)
check("dos rechazos cuentan", find(e, target)["rejections"] == 2)

# Borrado
check("borrar ajeno rechazado", error_of(rpc("delete_report", p_device=b, p_id=rid)) == "not_found")
check("borrar propio", rpc("delete_report", p_device=a, p_id=rid)[0] in (200, 204))
check("ya no aparece", find(b, rid) is None)

# Límites
g = new_device()
results = [create(g, "GENERAL", 36.70 + i * 0.005, -4.50 + i * 0.005) for i in range(6)]
check("límite por dispositivo", results[4][0] == 200 and error_of(results[5]) == "rate_limited", str(results[5]))

for device, report_id in created:
    rpc("delete_report", p_device=device, p_id=report_id)
left = {r["id"] for r in rpc("list_reports", p_device=new_device())[1]} & {i for _, i in created}
check("limpieza de los datos de prueba", not left, str(left))

print("\nResultado:", "TODO OK" if not fails else f"{len(fails)} fallos: {fails}")
sys.exit(1 if fails else 0)
