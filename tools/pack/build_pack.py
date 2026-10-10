"""Construit la bibliothèque de la galerie : un paquet .dwp par œuvre de catalog.json, un aperçu, un index.

Étapes, pour chaque œuvre :
  1. cadrage au format d'écran 9:20 (sans agrandir une image trop petite) ;
  2. profondeur avec Depth Anything V2 Small (le modèle de l'app), en plus haute
     résolution que sur le téléphone ;
  3. même post-traitement que l'app (DepthPostProcessor.refine) : normalisation par
     percentiles, filtre guidé par l'image, dilatation du premier plan contre les halos ;
  4. écriture du paquet selon docs/format.md (image WebP, disparité en PNG 16 bits, vignette),
     et d'un aperçu 540 × 1200 pour les cadres de la galerie.

Sorties (voir docs/library.md) :
  tools/pack/out/library/          la bibliothèque à publier : index.json, works/, previews/
  app/src/main/assets/library/     ce que l'APK embarque : index.json, tous les aperçus et
                                   les paquets marqués « bundled » dans le catalogue

Usage :
  python tools/pack/build_pack.py crops   # planche des cadrages, pour les régler
  python tools/pack/build_pack.py build   # bibliothèque et assets de l'app

Les images sources (tools/pack/sources/, non versionnées) et le modèle
(tools/pack/models/da2s_fp32.onnx) sont téléchargés par fetch() s'ils manquent.
"""

import hashlib
import io
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
SOURCES = ROOT / "sources"
OUT = ROOT / "out"
LIBRARY = OUT / "library"
APP_LIBRARY = REPO / "app" / "src" / "main" / "assets" / "library"
MODEL = ROOT / "models" / "da2s_fp32.onnx"
MODEL_URL = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx"
MODEL_SHA256 = "afb6a5c28f3b6bf1618c6e43f02073ef9dfdc70e937502d51603e57b0a1df10c"

ASPECT = 9 / 20              # format d'un écran de téléphone
MAX_COLOR = (1080, 2400)     # au-delà, l'image est réduite
DEPTH_LONG_SIDE = 1200       # carte de disparité : 540 × 1200 pour un cadrage 9:20
MODEL_AREA = 518 * 1148      # pixels d'entrée du modèle (518² sur le téléphone)
THUMBNAIL = (360, 800)
PREVIEW = (540, 1200)        # aperçu des cadres de la galerie
PREVIEW_QUALITY = 78
WEBP_QUALITY = 88
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
USER_AGENT = "GlimmerView-pack/0.1 (open-source wallpaper app)"
APP = "GlimmerView pack builder"
FIXED_DATE = (2026, 1, 1, 0, 0, 0)


def load_catalog():
    return json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))


def download(url, target):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response:
        target.write_bytes(response.read())


def fetch(works):
    SOURCES.mkdir(parents=True, exist_ok=True)
    for work in works:
        path = SOURCES / f"{work['file']}.jpg"
        if not path.exists():
            print("téléchargement", work["url"])
            download(work["url"], path)
    if not MODEL.exists():
        MODEL.parent.mkdir(parents=True, exist_ok=True)
        print("téléchargement du modèle")
        download(MODEL_URL, MODEL)
    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if digest != MODEL_SHA256:
        sys.exit(f"Empreinte du modèle inattendue : {digest}")


def crop(image, spec):
    """Bande verticale [top, bottom] au format 9:20, centrée sur x (fractions de l'image)."""
    width, height = image.size
    top = round(spec["top"] * height)
    bottom = round(spec["bottom"] * height)
    crop_width = round((bottom - top) * ASPECT)
    if crop_width > width:
        sys.exit("Cadrage plus large que l'image")
    left = round(spec["x"] * width - crop_width / 2)
    left = min(max(left, 0), width - crop_width)
    return image.crop((left, top, left + crop_width, bottom))


def fit(image, size):
    """Réduit l'image pour qu'elle tienne dans size, sans jamais l'agrandir."""
    scale = min(1.0, size[0] / image.width, size[1] / image.height)
    if scale >= 1.0:
        return image
    return image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)


def model_input_size(width, height):
    """Dimensions multiples de 14, au format de l'image, d'environ MODEL_AREA pixels."""
    scale = (MODEL_AREA / (width * height)) ** 0.5
    return max(14, round(width * scale / 14) * 14), max(14, round(height * scale / 14) * 14)


def estimate(session, image):
    width, height = model_input_size(*image.size)
    pixels = np.asarray(image.resize((width, height), Image.BICUBIC), dtype=np.float32) / 255.0
    pixels = (pixels - MEAN) / STD
    tensor = pixels.transpose(2, 0, 1)[None].astype(np.float32)
    return session.run(None, {"pixel_values": tensor})[0][0]


def normalize(raw):
    """Comme DepthPostProcessor.normalize : percentiles 1 % et 99 % ramenés à 0 et 1."""
    low, high = np.percentile(raw, [1, 99])
    if high - low < 1e-6:
        return np.zeros_like(raw)
    return np.clip((raw - low) / (high - low), 0, 1)


def guided_filter(depth, guide, radius, eps):
    """Comme DepthPostProcessor.guidedFilter (He et al., 2010), guidé par la luminance."""
    size = (2 * radius + 1, 2 * radius + 1)
    mean = lambda x: cv2.boxFilter(x, cv2.CV_32F, size, borderType=cv2.BORDER_REFLECT)
    mean_i, mean_p = mean(guide), mean(depth)
    var_i = mean(guide * guide) - mean_i * mean_i
    cov_ip = mean(guide * depth) - mean_i * mean_p
    a = cov_ip / (var_i + eps)
    b = mean_p - a * mean_i
    return np.clip(mean(a) * guide + mean(b), 0, 1)


def refine(raw, color):
    """Chaîne de DepthPostProcessor.refine, à la taille de la carte du paquet."""
    scale = DEPTH_LONG_SIDE / max(color.size)
    width, height = round(color.width * scale), round(color.height * scale)
    depth = cv2.resize(normalize(raw).astype(np.float32), (width, height), interpolation=cv2.INTER_LINEAR)
    rgb = np.asarray(color.resize((width, height), Image.BILINEAR), dtype=np.float32) / 255.0
    luma = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]).astype(np.float32)
    depth = guided_filter(depth, luma, radius=max(2, width // 80), eps=2e-3)
    return cv2.dilate(depth, np.ones((5, 5), np.uint8))


def subject_focus(depth):
    """Comme subjectFocus dans l'app : profondeur médiane du centre de l'image."""
    height, width = depth.shape
    xs = (0.25 + 0.5 * (np.arange(20) + 0.5) / 20) * width
    ys = (0.3 + 0.5 * (np.arange(20) + 0.5) / 20) * height
    samples = depth[ys.astype(int)[:, None], xs.astype(int)[None, :]]
    return float(np.median(samples))


def png16(depth):
    values = np.round(np.clip(depth, 0, 1) * 65535).astype(np.uint16)
    buffer = io.BytesIO()
    Image.fromarray(values, mode="I;16").save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def webp(image, quality):
    buffer = io.BytesIO()
    image.save(buffer, format="WEBP", quality=quality, method=6)
    return buffer.getvalue()


def manifest(work, color, focus):
    focus = work.get("focus", focus)
    data = {
        "format": "dwp",
        "formatVersion": 1,
        "id": work["id"],
        "title": work["title"],
        "author": {"name": work["author"]},
        "license": work["license"],
        "sourceUrl": work["sourceUrl"],
        "aiGenerated": False,
        "medium": work["medium"],
        "language": "en",
        "created": work["created"],
        "generator": {"app": APP, "depthModel": "depth-anything-v2-small"},
        "width": color.width,
        "height": color.height,
        "layers": [{"color": "color.webp", "depth": "depth.png"}],
        "defaults": {"strength": 0.035, "focus": round(focus, 4), "zoom": 1, "motion": "gyro"},
        "thumbnail": "thumbnail.webp",
        "tags": work["tags"],
    }
    return json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")


def write_package(path, manifest_bytes, color_bytes, depth_bytes, thumbnail_bytes):
    """Archive reproductible : date fixe, pour qu'une reconstruction à l'identique ne change pas le fichier."""
    entries = (("manifest.json", manifest_bytes, zipfile.ZIP_DEFLATED), ("color.webp", color_bytes, zipfile.ZIP_STORED),
               ("depth.png", depth_bytes, zipfile.ZIP_STORED), ("thumbnail.webp", thumbnail_bytes, zipfile.ZIP_STORED))
    with zipfile.ZipFile(path, "w") as archive:
        for name, data, method in entries:
            info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
            info.compress_type = method
            archive.writestr(info, data)


def cyanotype(depth):
    """Aperçu de la profondeur aux couleurs de l'app : bleu de Prusse au loin, papier au premier plan."""
    far = np.array([30, 58, 95], dtype=np.float32)
    near = np.array([238, 233, 222], dtype=np.float32)
    rgb = far + (near - far) * depth[..., None]
    return Image.fromarray(rgb.astype(np.uint8))


def sheet(tiles, path, columns=6):
    width, height = tiles[0][1].size
    rows = (len(tiles) + columns - 1) // columns
    board = Image.new("RGB", (columns * (width + 10), rows * (height + 24)), "white")
    for i, (label, tile) in enumerate(tiles):
        x, y = (i % columns) * (width + 10), (i // columns) * (height + 24)
        board.paste(tile.resize((width, height)), (x, y + 18))
        ImageDraw.Draw(board).text((x, y + 2), label, fill="black")
    board.save(path, quality=85)
    print("planche :", path)


def crops(catalog):
    works = catalog["works"]
    OUT.mkdir(exist_ok=True)
    tiles = []
    for work in works:
        image = crop(Image.open(SOURCES / f"{work['file']}.jpg").convert("RGB"), work["crop"])
        tiles.append((f"{work['file']} {image.width}×{image.height}", image.resize((225, 500), Image.LANCZOS)))
    sheet(tiles, OUT / "crops.jpg")


def entry(work, package, preview, focus):
    """Fiche de l'œuvre dans l'index de la bibliothèque (docs/library.md)."""
    data = package.read_bytes()
    return {
        "id": work["id"],
        "room": work["room"].lower(),
        "title": work["titles"],
        "author": work["author"],
        "medium": work["mediums"],
        "year": work["year"],
        "circa": work["circa"],
        "detail": work["detail"],
        "license": work["license"],
        "sourceUrl": work["sourceUrl"],
        "focus": round(work.get("focus", focus), 4),
        "package": {"path": f"works/{package.name}", "size": len(data), "sha256": hashlib.sha256(data).hexdigest()},
        "preview": f"previews/{preview.name}",
    }


def clean(directory, keep):
    directory.mkdir(parents=True, exist_ok=True)
    for path in directory.iterdir():
        if path.is_file() and path.name not in keep:
            path.unlink()


def build(catalog):
    import onnxruntime

    works = catalog["works"]
    for directory in (LIBRARY / "works", LIBRARY / "previews", APP_LIBRARY / "works", APP_LIBRARY / "previews"):
        directory.mkdir(parents=True, exist_ok=True)
    session = onnxruntime.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    tiles, entries = [], []
    total = bundled = 0
    for work in works:
        color = fit(crop(Image.open(SOURCES / f"{work['file']}.jpg").convert("RGB"), work["crop"]), MAX_COLOR)
        depth = refine(estimate(session, color), color)
        color_bytes = webp(color, WEBP_QUALITY)
        depth_bytes = png16(depth)
        thumbnail_bytes = webp(color.resize(THUMBNAIL, Image.LANCZOS), 85)
        package = LIBRARY / "works" / f"{work['id']}.dwp"
        write_package(package, manifest(work, color, subject_focus(depth)), color_bytes, depth_bytes, thumbnail_bytes)
        preview = LIBRARY / "previews" / f"{work['id']}.webp"
        preview.write_bytes(webp(color.resize(PREVIEW, Image.LANCZOS), PREVIEW_QUALITY))
        (APP_LIBRARY / "previews" / preview.name).write_bytes(preview.read_bytes())
        if work.get("bundled"):
            (APP_LIBRARY / "works" / package.name).write_bytes(package.read_bytes())
            bundled += package.stat().st_size
        entries.append(entry(work, package, preview, subject_focus(depth)))
        size = package.stat().st_size
        total += size
        print(f"{work['id']:<28} {color.width}×{color.height}  paquet {size / 1e3:6.0f} Ko"
              f"  aperçu {preview.stat().st_size / 1e3:4.0f} Ko{'  (APK)' if work.get('bundled') else ''}")
        tiles.append((work["file"], color.resize((225, 500), Image.LANCZOS)))
        tiles.append((work["file"] + " (profondeur)", cyanotype(cv2.resize(depth, (225, 500)))))
    clean(APP_LIBRARY / "works", {f"{w['id']}.dwp" for w in works if w.get("bundled")})
    clean(APP_LIBRARY / "previews", {f"{w['id']}.webp" for w in works})
    index = {"format": "glimmerview-library", "version": 1, "revision": catalog["revision"], "works": entries}
    text = json.dumps(index, ensure_ascii=False, indent=2) + "\n"
    (LIBRARY / "index.json").write_text(text, encoding="utf-8")
    (APP_LIBRARY / "index.json").write_text(text, encoding="utf-8")
    previews = sum(p.stat().st_size for p in (APP_LIBRARY / "previews").iterdir())
    print(f"bibliothèque : {total / 1e6:.1f} Mo pour {len(works)} œuvres (révision {catalog['revision']})")
    print(f"dans l'APK : {bundled / 1e6:.1f} Mo de paquets + {previews / 1e6:.1f} Mo d'aperçus")
    sheet(tiles, OUT / "pack.jpg", columns=8)

if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "build"
    catalog = load_catalog()
    fetch(catalog["works"])
    {"crops": crops, "build": build}[command](catalog)
