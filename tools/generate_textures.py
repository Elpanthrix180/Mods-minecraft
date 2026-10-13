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


import random


def noisy(img, rect, color, amount=12, seed=0):
    """Rellena con pequeñas variaciones de tono (aspecto metálico)."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            d = rnd.randint(-amount, amount)
            img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in color[:3]) + (255,))


def paint_box_noisy(img, u, v, w, h, d, color, seed, edge=None):
    for name, r in box_faces(u, v, w, h, d).items():
        f = {"top": 1.15, "bottom": 0.7, "front": 1.0, "back": 0.85, "left": 0.9, "right": 0.9}[name]
        noisy(img, r, shade(color, f), seed=seed + sum(map(ord, name)))
        if edge:
            x0, y0, x1, y1 = r
            for x in range(x0, x1):
                img.putpixel((x, y0), edge)
                img.putpixel((x, y1 - 1), edge)


def hazard(img, rect, offset=0):
    """Franjas de peligro amarillas y negras."""
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            yellow = ((x + y + offset) // 2) % 2 == 0
            img.putpixel((x, y), (235, 190, 30, 255) if yellow else (30, 30, 34, 255))


def miner():
    purple = (122, 62, 196, 255)
    dpurple = (70, 40, 110, 255)
    steel = (150, 152, 162, 255)
    dsteel = (70, 72, 82, 255)
    black = (28, 28, 32, 255)
    yellow = (236, 186, 34, 255)
    visor = (110, 240, 255, 255)
    lamp = (255, 246, 190, 255)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 64), (0, 0, 0, 0))

    # cabeza: metal oscuro con visera luminosa
    paint_box_noisy(img, 0, 0, 8, 8, 8, dsteel, 1)
    fill(img, (8, 10, 16, 13), (20, 20, 26, 255))          # marco de la visera
    fill(img, (9, 11, 15, 12), visor)
    fill(glow, (9, 11, 15, 12), visor)
    fill(img, (10, 14, 14, 15), (40, 40, 48, 255))         # rejilla de la boca
    for x in (10, 12):
        img.putpixel((x, 14), (90, 90, 100, 255))
    # casco amarillo
    paint_box_noisy(img, 64, 0, 9, 3, 9, yellow, 2, edge=(160, 120, 10, 255))
    fill(img, (73, 9, 82, 10), (160, 120, 10, 255))        # franja del casco (frente)
    paint_box_noisy(img, 64, 12, 9, 1, 1, (190, 145, 20, 255), 3)
    # linterna
    paint_box_noisy(img, 100, 0, 3, 3, 2, dsteel, 4)
    fill(img, (102, 2, 105, 5), lamp)
    fill(glow, (102, 2, 105, 5), lamp)

    # cuerpo: morado con placa de pecho y cinturón de peligro
    paint_box_noisy(img, 0, 16, 8, 10, 6, purple, 5)
    fx, fy = 6, 22                                         # cara frontal del cuerpo
    fill(img, (fx + 1, fy + 1, fx + 7, fy + 6), steel)     # placa
    fill(img, (fx + 2, fy + 2, fx + 6, fy + 5), dsteel)
    fill(img, (fx + 3, fy + 3, fx + 5, fy + 4), (255, 120, 255, 255))   # núcleo
    fill(glow, (fx + 3, fy + 3, fx + 5, fy + 4), (255, 120, 255, 255))
    for (x, y) in ((fx + 1, fy + 1), (fx + 6, fy + 1), (fx + 1, fy + 5), (fx + 6, fy + 5)):
        img.putpixel((x, y), (200, 200, 210, 255))         # remaches
    hazard(img, (0, 30, 28, 32))                           # cinturón (todas las caras)
    # mochila del mineral, con ventanita
    paint_box_noisy(img, 64, 16, 6, 7, 3, steel, 6, edge=dsteel)
    bx, by = 64 + 3 + 6 + 3, 16 + 3                        # cara trasera
    fill(img, (bx + 1, by + 1, bx + 5, by + 2), dpurple)
    fill(img, (bx + 2, by + 3, bx + 4, by + 6), (20, 30, 40, 255))
    fill(img, (bx + 2, by + 4, bx + 4, by + 5), (80, 230, 220, 255))
    fill(glow, (bx + 2, by + 4, bx + 4, by + 5), (80, 230, 220, 255))
    # hombreras
    paint_box_noisy(img, 84, 16, 4, 3, 4, purple, 7, edge=dpurple)
    paint_box_noisy(img, 100, 16, 4, 3, 4, purple, 8, edge=dpurple)
    # brazos con articulación
    paint_box_noisy(img, 32, 16, 3, 9, 3, steel, 9)
    paint_box_noisy(img, 44, 16, 3, 9, 3, steel, 10)
    fill(img, (32, 22, 44, 23), black)
    fill(img, (44, 22, 56, 23), black)
    # piernas y orugas
    paint_box_noisy(img, 0, 32, 4, 6, 4, dsteel, 11)
    paint_box_noisy(img, 16, 32, 4, 6, 4, dsteel, 12)
    for u in (64, 86):
        paint_box_noisy(img, u, 32, 5, 2, 6, black, 13)
        for x in range(u, u + 22, 2):                      # dientes de la oruga
            for y in range(32, 40):
                if img.getpixel((x, y))[3]:
                    img.putpixel((x, y), (75, 75, 82, 255))
    # taladro: base con franjas, conos de acero con espiral
    for name, r in box_faces(32, 32, 4, 4, 4).items():
        hazard(img, r, offset=1 if name in ("left", "right") else 0)
    for (u, v, w, h, d, seed) in ((32, 40, 3, 3, 3, 14), (44, 40, 2, 2, 2, 15), (48, 32, 1, 1, 2, 16)):
        paint_box_noisy(img, u, v, w, h, d, (175, 178, 188, 255), seed)
        for name, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if (x - y) % 3 == 0:
                        img.putpixel((x, y), (110, 112, 122, 255))
    return img, glow


def builder():
    purple = (122, 62, 196, 255)
    dpurple = (70, 40, 110, 255)
    steel = (150, 152, 162, 255)
    dsteel = (70, 72, 82, 255)
    black = (28, 28, 32, 255)
    leather = (120, 78, 40, 255)
    visor = (120, 255, 200, 255)
    bulb = (255, 220, 90, 255)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 64), (0, 0, 0, 0))

    # cabeza gris clara con visera verde agua y "boca" de altavoz
    paint_box_noisy(img, 0, 0, 8, 8, 8, steel, 21)
    fill(img, (8, 8, 16, 9), purple)                       # franja morada arriba
    fill(img, (8, 10, 16, 13), (20, 20, 26, 255))
    fill(img, (9, 11, 15, 12), visor)
    fill(glow, (9, 11, 15, 12), visor)
    for x in (10, 11, 12, 13):
        img.putpixel((x, 14), dsteel if x % 2 else (40, 40, 48, 255))
    # antena, bombilla y tornillos
    paint_box_noisy(img, 100, 0, 1, 3, 1, dsteel, 22)
    paint_box_noisy(img, 104, 0, 2, 2, 2, bulb, 23)
    fill(glow, (104, 0, 112, 4), bulb)
    paint_box_noisy(img, 112, 0, 1, 2, 2, dpurple, 24)
    paint_box_noisy(img, 118, 0, 1, 2, 2, dpurple, 25)

    # cuerpo morado con placa y núcleo
    paint_box_noisy(img, 0, 16, 8, 10, 6, purple, 26)
    fx, fy = 6, 22
    fill(img, (fx + 1, fy + 1, fx + 7, fy + 6), steel)
    fill(img, (fx + 2, fy + 2, fx + 6, fy + 5), dsteel)
    fill(img, (fx + 3, fy + 3, fx + 5, fy + 4), visor)
    fill(glow, (fx + 3, fy + 3, fx + 5, fy + 4), visor)
    for (x, y) in ((fx + 1, fy + 1), (fx + 6, fy + 1), (fx + 1, fy + 5), (fx + 6, fy + 5)):
        img.putpixel((x, y), (200, 200, 210, 255))
    # cinturón de cuero con herramientas (martillo, regla, llave)
    paint_box_noisy(img, 64, 0, 9, 2, 7, leather, 27)
    ffx, ffy = 64 + 7, 7                                   # cara frontal del cinturón
    fill(img, (ffx + 3, ffy, ffx + 6, ffy + 2), (200, 170, 60, 255))   # hebilla
    fill(img, (ffx + 4, ffy, ffx + 5, ffy + 2), leather)
    for (tx, col) in ((ffx, (110, 110, 120, 255)), (ffx + 7, (230, 200, 60, 255)), (ffx + 8, (170, 60, 50, 255))):
        img.putpixel((tx, ffy), col)
        img.putpixel((tx, ffy + 1), col)
    # caja de bloques a la espalda (aspecto de madera con bloques asomando)
    paint_box_noisy(img, 64, 16, 6, 6, 3, (150, 105, 60, 255), 28, edge=(90, 60, 30, 255))
    tx0, ty0 = 64 + 3, 16                                  # tapa (arriba)
    fill(img, (tx0, ty0, tx0 + 3, ty0 + 3), (125, 125, 125, 255))      # piedra
    fill(img, (tx0 + 3, ty0, tx0 + 6, ty0 + 3), (95, 160, 60, 255))    # hierba
    # hombreras
    paint_box_noisy(img, 84, 16, 4, 3, 4, purple, 29, edge=dpurple)
    paint_box_noisy(img, 100, 16, 4, 3, 4, purple, 30, edge=dpurple)
    # brazos
    paint_box_noisy(img, 32, 16, 3, 9, 3, steel, 31)
    paint_box_noisy(img, 44, 16, 3, 9, 3, steel, 32)
    fill(img, (32, 22, 44, 23), black)
    fill(img, (44, 22, 56, 23), black)
    # piernas y botas
    paint_box_noisy(img, 0, 32, 4, 6, 4, dsteel, 33)
    paint_box_noisy(img, 16, 32, 4, 6, 4, dsteel, 34)
    paint_box_noisy(img, 64, 32, 5, 2, 5, dpurple, 35, edge=black)
    paint_box_noisy(img, 84, 32, 5, 2, 5, dpurple, 36, edge=black)
    # pinza (amarilla de obra) y bloque de ladrillos de piedra sujeto
    paint_box_noisy(img, 32, 32, 4, 2, 5, (236, 186, 34, 255), 37, edge=(160, 120, 10, 255))
    for name, (x0, y0, x1, y1) in box_faces(32, 40, 4, 4, 4).items():
        noisy(img, (x0, y0, x1, y1), (128, 128, 128, 255), seed=38)
        for x in range(x0, x1):
            img.putpixel((x, y0 + (y1 - y0) // 2), (90, 90, 90, 255))
        img.putpixel((x0 + 1, y0), (90, 90, 90, 255))
        img.putpixel((x0 + 3 if x1 - x0 > 3 else x0, y0 + (y1 - y0) // 2 + 1), (90, 90, 90, 255))
    return img, glow


def builder_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fill(img, (7, 0, 9, 1), (255, 220, 90, 255))           # bombilla
    fill(img, (4, 1, 12, 7), (150, 152, 162, 255))         # cabeza
    fill(img, (4, 1, 12, 2), (122, 62, 196, 255))
    fill(img, (5, 3, 11, 4), (120, 255, 200, 255))         # visera
    fill(img, (3, 7, 13, 12), (122, 62, 196, 255))         # cuerpo
    fill(img, (3, 10, 13, 11), (120, 78, 40, 255))         # cinturón
    fill(img, (7, 10, 9, 11), (200, 170, 60, 255))
    fill(img, (4, 12, 7, 16), (70, 40, 110, 255))          # botas
    fill(img, (9, 12, 12, 16), (70, 40, 110, 255))
    fill(img, (12, 8, 16, 12), (128, 128, 128, 255))       # bloque sujeto
    fill(img, (12, 10, 16, 11), (90, 90, 90, 255))
    return img


def miner_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fill(img, (4, 1, 12, 3), (236, 186, 34, 255))          # casco
    fill(img, (7, 0, 9, 1), (255, 246, 190, 255))          # linterna
    fill(img, (4, 3, 12, 7), (70, 72, 82, 255))            # cabeza
    fill(img, (5, 4, 11, 5), (110, 240, 255, 255))         # visera
    fill(img, (3, 7, 13, 12), (122, 62, 196, 255))         # cuerpo
    fill(img, (6, 8, 10, 10), (150, 152, 162, 255))
    fill(img, (3, 11, 13, 12), (235, 190, 30, 255))        # cinturón
    fill(img, (4, 12, 7, 16), (28, 28, 32, 255))           # orugas
    fill(img, (9, 12, 12, 16), (28, 28, 32, 255))
    fill(img, (12, 8, 14, 11), (235, 190, 30, 255))        # taladro
    fill(img, (14, 9, 16, 10), (175, 178, 188, 255))
    return img


for kind in ("builder", "miner"):
    tex, glow = builder() if kind == "builder" else miner()
    tex.save(os.path.join(ROOT, "entity", f"{kind}_robot.png"))
    glow.save(os.path.join(ROOT, "entity", f"{kind}_robot_glow.png"))
    (builder_icon() if kind == "builder" else miner_icon()).save(os.path.join(ROOT, "item", f"{kind}_robot.png"))
print("Texturas generadas")
