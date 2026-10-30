#!/usr/bin/env python3
"""Adapta el estilo Positron de OpenFreeMap a los colores del diseño de BlueWay.

Uso: python tools/build_map_style.py <positron.json> <salida map_style.json>
"""
import json
import sys

style = json.load(open(sys.argv[1], encoding="utf-8"))
paints = {
    "background": {"background-color": "#F2F5F9"},
    "park": {"fill-color": "#CDEFD8"},
    "water": {"fill-color": "#8FCDF7"},
    "landcover_wood": {"fill-color": "#CDEFD8"},
    "waterway": {"line-color": "#8FCDF7"},
    "building": {"fill-color": "#EDF0F5", "fill-outline-color": "#E2E7EF"},
    "landuse_residential": {"fill-color": "#EEF1F6"},
    "road_area_pier": {"fill-color": "#E8ECF2"},
    "road_pier": {"line-color": "#E8ECF2"},
    "highway_minor": {"line-color": "#FFFFFF"},
    "highway_path": {"line-color": "#FFFFFF"},
}
kept = []
for layer in style["layers"]:
    if layer["id"] in ("boundary_3", "boundary_2", "boundary_disputed", "label_country_1", "label_country_2", "label_country_3"):
        continue
    if layer["id"] in paints:
        layer.setdefault("paint", {}).update(paints[layer["id"]])
    if layer["type"] == "symbol" and layer["id"].startswith("label"):
        layer.setdefault("paint", {})["text-color"] = "#1B2A6B"
    kept.append(layer)
style["layers"] = kept
style["name"] = "BlueWay"
json.dump(style, open(sys.argv[2], "w", encoding="utf-8"), separators=(",", ":"), ensure_ascii=False)
print("capas", len(kept))
