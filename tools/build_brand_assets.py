#!/usr/bin/env python3
"""Genera los recursos gráficos de la app a partir de los materiales de marca.

Uso: python tools/build_brand_assets.py "<carpeta HACKATLÓN>" <word/media extraído> <carpeta res>
No modifica el logotipo: solo le quita el fondo blanco exterior y cambia el tamaño.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

src, media, res = map(Path, sys.argv[1:4])
drawable = res / "drawable-nodpi"
drawable.mkdir(parents=True, exist_ok=True)


def transparent_outside(img, thresh=28):
    """Quita el blanco conectado con el borde; los blancos interiores se conservan."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    marker = (255, 0, 255)
    flood = rgb.copy()
    for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        ImageDraw.floodfill(flood, corner, marker, thresh=thresh)
    mask = Image.new("L", (w, h), 255)
    fp, mp = flood.load(), mask.load()
    for y in range(h):
        for x in range(w):
            if fp[x, y] == marker:
                mp[x, y] = 0
    mask = mask.filter(ImageFilter.GaussianBlur(1.2))
    out = rgb.convert("RGBA")
    out.putalpha(mask)
    return out


def save(img, name, size=None, quality=90):
    if size:
        img = img.resize(size, Image.LANCZOS)
    path = drawable / f"{name}.webp"
    img.save(path, "WEBP", quality=quality, method=6)
    print(name, img.size, path.stat().st_size)


# Iconos de avisos: disco blanco con el icono original, como los marcadores del diseño.
ICONS = {
    "Evento cercano": "ic_report_event",
    "Nivel de ruido alto": "ic_report_noise_high",
    "Nivel de ruido bajo": "ic_report_noise_low",
    "Nivel de ruido excesivo": "ic_report_noise_extreme",
    "Nivel de ruido moderado": "ic_report_noise_moderate",
    "Nivel de ruido normal": "ic_report_noise_normal",
    "Notificación general": "ic_report_general",
    "Obras en la zona": "ic_report_works",
    "Trafico elevado": "ic_report_traffic",
}
for file, name in ICONS.items():
    im = Image.open(src / "ICONOS DE LA APP" / f"{file}.png").convert("RGBA")
    s = im.size[0]
    mask = Image.new("L", (s * 2, s * 2), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, s * 2 - 1, s * 2 - 1), fill=255)
    im.putalpha(mask.resize((s, s), Image.LANCZOS))
    save(im, name, (256, 256), 92)

# Logotipos
logo = transparent_outside(Image.open(src / "LOGOTIPO" / "LOGOTIPO COMPLETO" / "LOGOTIPO COMPLETO .png"))
logo = logo.crop(logo.getbbox())
save(logo, "logo_blueway", (900, int(900 * logo.height / logo.width)), 95)
iso = transparent_outside(Image.open(src / "LOGOTIPO" / "ICONO APLICACIÓN" / "ISOTIPO ICONO APP .png"))
iso = iso.crop(iso.getbbox())
save(iso, "logo_isotipo", (int(400 * iso.width / iso.height), 400), 95)

# Recortes de las pantallas de diseño
screens = src / "PANTALLAS VISTA APP"
start = Image.open(screens / "Pantalla de inicio de la APP.png").convert("RGB")
save(start.crop((0, 570, 941, 1278)), "illustration_welcome", None, 90)
zone = Image.open(screens / "Calcul de ruta APP.png").convert("RGB")
save(zone.crop((0, 190, 941, 530)), "photo_zone", None, 88)
legend = Image.open(screens / "Niveles de ruido leyenda APP.png").convert("RGB")
save(legend.crop((700, 1180, 912, 1340)), "illustration_city", None, 90)
profile = Image.open(screens / "Pestaña d eperfil personal dentro de la app.png").convert("RGBA")
cx, cy, r = 470, 218, 130
av = profile.crop((cx - r, cy - r, cx + r, cy + r))
m = Image.new("L", (r * 4, r * 4), 0)
ImageDraw.Draw(m).ellipse((0, 0, r * 4 - 1, r * 4 - 1), fill=255)
av.putalpha(m.resize((r * 2, r * 2), Image.LANCZOS))
save(av, "avatar_default", (256, 256), 92)

# Icono adaptativo: huella blanca sobre el degradado de marca.
ic = Image.open(media / "image10.png").convert("RGB")
w, h = ic.size
a = Image.new("L", ic.size, 0)
ip, ap = ic.load(), a.load()
for y in range(h):
    for x in range(w):
        v = min(ip[x, y])
        ap[x, y] = max(0, min(255, int((v - 110) * 255 / 110)))
foot = Image.new("RGBA", ic.size, (255, 255, 255, 0))
foot.putalpha(a)
foot = foot.crop(foot.getbbox())
canvas = 432
scale = int(canvas * 0.60) / max(foot.size)
foot = foot.resize((int(foot.width * scale), int(foot.height * scale)), Image.LANCZOS)
fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
fg.paste(foot, ((canvas - foot.width) // 2, (canvas - foot.height) // 2), foot)
fg.save(drawable / "ic_launcher_foreground.webp", "WEBP", quality=95, method=6)
print("ic_launcher_foreground", fg.size, foot.size)
