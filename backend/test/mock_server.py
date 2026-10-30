#!/usr/bin/env python3
"""Imitación en memoria de la API de avisos, para probar la app sin servidor real.

Reproduce las reglas de backend/schema.sql, pero NO sustituye probar ese SQL contra PostgreSQL.
Uso: python backend/test/mock_server.py [puerto]
"""
import json
import math
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOURS = {"WORKS": 168, "EVENT": 6, "TRAFFIC": 2, "NOISE_EXTREME": 2, "NOISE_HIGH": 2,
         "NOISE_MODERATE": 2, "NOISE_NORMAL": 2, "NOISE_LOW": 2, "GENERAL": 3}
reports, votes = {}, set()


class ApiError(Exception):
    pass


def now_ms():
    return int(time.time() * 1000)


def meters(lat1, lon1, lat2, lon2):
    return math.hypot((lat1 - lat2) * 110574, (lon1 - lon2) * 111320 * math.cos(math.radians((lat1 + lat2) / 2)))


def public(r, device):
    row = {k: r[k] for k in ("id", "type", "lat", "lon", "description", "created_ms", "expires_ms", "confirmations", "rejections")}
    row["mine"] = r["device"] == device
    row["voted"] = (r["id"], device) in votes
    return row


def list_reports(a):
    rows = [r for r in reports.values() if r["expires_ms"] > now_ms() - 86_400_000]
    return [public(r, a["p_device"]) for r in sorted(rows, key=lambda r: -r["created_ms"])]


def create_report(a):
    device, type_ = a["p_device"], a["p_type"]
    if type_ not in HOURS:
        raise ApiError("invalid_type")
    lat, lon = round(a["p_lat"], 4), round(a["p_lon"], 4)
    if not (36.685 <= lat <= 36.755 and -4.515 <= lon <= -4.345):
        raise ApiError("out_of_area")
    recent = [r for r in reports.values() if r["device"] == device and r["created_ms"] > now_ms() - 600_000]
    if len(recent) >= 5:
        raise ApiError("rate_limited")
    for r in reports.values():
        dismissed = r["rejections"] >= 2 and r["rejections"] > r["confirmations"]
        if r["type"] == type_ and r["expires_ms"] > now_ms() and not dismissed and meters(r["lat"], r["lon"], lat, lon) <= 120:
            if r["device"] != device and (r["id"], device) not in votes:
                votes.add((r["id"], device))
                r["confirmations"] += 1
            return [{"id": r["id"], "merged": True}]
    rid = str(uuid.uuid4())
    text = " ".join((a.get("p_description") or "").split())[:120]
    reports[rid] = {"id": rid, "device": device, "type": type_, "lat": lat, "lon": lon, "description": text,
                    "created_ms": now_ms(), "expires_ms": now_ms() + HOURS[type_] * 3_600_000,
                    "confirmations": 1, "rejections": 0}
    return [{"id": rid, "merged": False}]


def vote_report(a):
    r = reports.get(a["p_id"])
    if not r or r["expires_ms"] <= now_ms():
        raise ApiError("not_found")
    if r["device"] == a["p_device"]:
        raise ApiError("own_report")
    if (r["id"], a["p_device"]) in votes:
        raise ApiError("already_voted")
    votes.add((r["id"], a["p_device"]))
    r["confirmations" if a["p_still_there"] else "rejections"] += 1


def delete_report(a):
    r = reports.get(a["p_id"])
    if not r or r["device"] != a["p_device"]:
        raise ApiError("not_found")
    del reports[r["id"]]


FUNCTIONS = {f.__name__: f for f in (list_reports, create_report, vote_report, delete_report)}


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body=None):
        raw = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        self._send(404, {"message": "not_found"})

    def do_POST(self):
        args = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        fn = FUNCTIONS.get(self.path.removeprefix("/rpc/")) if self.path.startswith("/rpc/") else None
        if fn is None:
            self._send(404, {"message": "not_found"})
            return
        try:
            result = fn(args)
            self._send(200 if result is not None else 204, result)
        except ApiError as e:
            self._send(400, {"message": str(e)})


port = int(sys.argv[1]) if len(sys.argv) > 1 else 54330
print(f"Mock de avisos en http://127.0.0.1:{port}", flush=True)
ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
