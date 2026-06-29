"""
Genera texturas PLACEHOLDER de 16x16 para los dos objetos de este modulo.
No son arte final: solo evitan la textura "missing" (rosa/negro) mientras
no exista el arte definitivo del mod.
"""
from PIL import Image, ImageDraw
import random

random.seed(42)

def mind_storage_texture(path):
    # Bloque: textura de piedra purpura oscura con vetas, como un
    # "almacen" denso y solido.
    size = 16
    img = Image.new("RGB", (size, size))
    base = (46, 34, 58)
    for y in range(size):
        for x in range(size):
            n = random.randint(-10, 10)
            r = max(0, min(255, base[0] + n))
            g = max(0, min(255, base[1] + n))
            b = max(0, min(255, base[2] + n + 6))
            img.putpixel((x, y), (r, g, b))
    draw = ImageDraw.Draw(img)
    # un par de "vetas" claras para dar textura
    for _ in range(6):
        x = random.randint(0, size - 1)
        y0 = random.randint(0, size - 4)
        y1 = min(size - 1, y0 + random.randint(2, 5))
        draw.line([(x, y0), (x, y1)], fill=(98, 74, 130), width=1)
    img.save(path)

def episodic_comparator_texture(path):
    # Item: icono simple, dos "marcos" de recuerdo enfrentados con una
    # flecha de comparacion entre ellos.
    size = 16
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    frame = (210, 196, 160, 255)
    glass = (120, 170, 200, 255)
    accent = (235, 200, 90, 255)
    # marco izquierdo
    draw.rectangle([1, 3, 6, 10], outline=frame, width=1)
    draw.rectangle([2, 4, 5, 9], fill=glass)
    # marco derecho
    draw.rectangle([9, 3, 14, 10], outline=frame, width=1)
    draw.rectangle([10, 4, 13, 9], fill=glass)
    # flecha de comparacion en el centro
    draw.line([(7, 6), (9, 6)], fill=accent, width=1)
    draw.point((6, 6), fill=accent)
    draw.point((9, 6), fill=accent)
    img.save(path)

mind_storage_texture("/home/claude/backrooms-mod/src/main/resources/assets/backrooms/textures/block/mind_storage.png")
episodic_comparator_texture("/home/claude/backrooms-mod/src/main/resources/assets/backrooms/textures/item/episodic_comparator.png")
print("OK")
