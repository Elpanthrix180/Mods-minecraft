"""Genera las texturas PNG del mod (robots e iconos). Uso: python tools/generate_textures.py"""
from PIL import Image
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "robotsconstructores", "textures")


def box_faces(u, v, w, h, d):
    """Rectángulos (x0,y0,x1,y1) de las caras de un cubo de Minecraft con texOffs(u,v)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fill(img, rect, color):
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), color)


def shade(c, f):
    return tuple(max(0, min(255, int(ch * f))) for ch in c[:3]) + (255,)


def paint_box(img, u, v, w, h, d, color, edge=None):
    for name, r in box_faces(u, v, w, h, d).items():
        f = {"top": 1.15, "bottom": 0.7, "front": 1.0, "back": 0.85, "left": 0.9, "right": 0.9}[name]
        fill(img, r, shade(color, f))
        if edge:
            x0, y0, x1, y1 = r
            for x in range(x0, x1):
                img.putpixel((x, y0), edge)
                img.putpixel((x, y1 - 1), edge)


def robot(kind):
    purple = (122, 62, 196, 255)
    dark = (70, 40, 110, 255)
    grey = (138, 138, 150, 255)
    dgrey = (80, 80, 92, 255)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eye = (120, 255, 255, 255) if kind == "builder" else (255, 120, 255, 255)
    head_col = grey if kind == "builder" else dgrey
    body_col = purple if kind == "builder" else grey
    paint_box(img, 0, 0, 8, 8, 8, head_col, dark)
    paint_box(img, 0, 16, 8, 10, 6, body_col, dark)
    paint_box(img, 32, 16, 3, 9, 3, dgrey)
    paint_box(img, 44, 16, 3, 9, 3, dgrey)
    paint_box(img, 0, 32, 3, 6, 3, dark)
    paint_box(img, 12, 32, 3, 6, 3, dark)
    # panel del pecho
    fx, fy = 6, 22  # cara frontal del cuerpo
    fill(img, (fx + 2, fy + 2, fx + 6, fy + 5), dgrey if kind == "builder" else purple)
    fill(glow, (fx + 3, fy + 3, fx + 5, fy + 4), eye)
    # ojos (cara frontal de la cabeza en 8,8)
    for ex in (9, 10, 13, 14):
        img.putpixel((ex, 11), eye)
        glow.putpixel((ex, 11), eye)
    if kind == "builder":
        paint_box(img, 56, 0, 1, 3, 1, grey)
        paint_box(img, 56, 8, 2, 2, 2, eye)
        fill(glow, (56, 8, 64, 12), eye)
        paint_box(img, 24, 32, 4, 2, 5, (230, 190, 40, 255), dgrey)
    else:
        paint_box(img, 40, 0, 2, 2, 1, (255, 240, 150, 255))
        fill(glow, (40, 0, 46, 3), (255, 240, 150, 255))
        paint_box(img, 24, 40, 3, 3, 6, (60, 60, 70, 255), purple)
        paint_box(img, 44, 32, 1, 1, 3, (200, 200, 210, 255))
    return img, glow


def icon(kind):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    purple = (122, 62, 196, 255)
    grey = (138, 138, 150, 255)
    dark = (50, 30, 80, 255)
    eye = (120, 255, 255, 255) if kind == "builder" else (255, 120, 255, 255)
    fill(img, (4, 1, 12, 7), grey if kind == "builder" else (80, 80, 92, 255))
    fill(img, (5, 3, 7, 4), eye)
    fill(img, (9, 3, 11, 4), eye)
    fill(img, (3, 7, 13, 13), purple if kind == "builder" else grey)
    fill(img, (6, 9, 10, 11), dark)
    fill(img, (4, 13, 6, 16), dark)
    fill(img, (10, 13, 12, 16), dark)
    if kind == "builder":
        fill(img, (7, 0, 9, 1), eye)
        fill(img, (13, 8, 16, 11), (230, 190, 40, 255))
    else:
        fill(img, (13, 9, 16, 11), (60, 60, 70, 255))
        fill(img, (7, 0, 9, 1), (255, 240, 150, 255))
    return img


for kind in ("builder", "miner"):
    tex, glow = robot(kind)
    tex.save(os.path.join(ROOT, "entity", f"{kind}_robot.png"))
    glow.save(os.path.join(ROOT, "entity", f"{kind}_robot_glow.png"))
    icon(kind).save(os.path.join(ROOT, "item", f"{kind}_robot.png"))
print("Texturas generadas")
