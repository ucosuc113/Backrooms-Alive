"""
Genera texturas PLACEHOLDER de 16x16 para los bloques/items del mod.
Creará texturas para:
 - back_wall
 - back_floor
 - back_ceiling
 - back_light

Usar: desde la raiz del repositorio ejecutar `python scripts/gen_placeholder_textures.py`
Requiere Pillow: `pip install pillow`.
"""
import os
import random
from PIL import Image, ImageDraw


random.seed(42)


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


def save_png(img, path):
    ensure_dir(os.path.dirname(path))
    img.save(path)


def make_textured_tile(base_color, variance=12):
    size = 16
    img = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        for x in range(size):
            n = random.randint(-variance, variance)
            r = max(0, min(255, base_color[0] + n))
            g = max(0, min(255, base_color[1] + n))
            b = max(0, min(255, base_color[2] + n))
            img.putpixel((x, y), (r, g, b))
    # light streaks
    for i in range(3):
        x = random.randint(0, size - 1)
        y0 = random.randint(0, size - 4)
        y1 = min(size - 1, y0 + random.randint(1, 4))
        draw.line([(x, y0), (x, y1)], fill=tuple(min(255, c + 30) for c in base_color), width=1)
    return img


def gen_back_wall(path):
    base = (200, 170, 80)
    img = make_textured_tile(base, variance=18)
    save_png(img, path)


def gen_back_floor(path):
    base = (180, 150, 70)
    img = make_textured_tile(base, variance=10)
    save_png(img, path)


def gen_back_ceiling(path):
    base = (210, 180, 90)
    img = make_textured_tile(base, variance=8)
    save_png(img, path)


def gen_back_light(path):
    size = 16
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # bright center
    for y in range(size):
        for x in range(size):
            dx = x - size // 2
            dy = y - size // 2
            d = (dx * dx + dy * dy) ** 0.5
            intensity = max(0, 255 - int(d * 48))
            img.putpixel((x, y), (255, 230, 120, intensity))
    save_png(img, path)


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    block_dir = os.path.join(root, 'src', 'main', 'resources', 'assets', 'backrooms', 'textures', 'block')
    item_dir = os.path.join(root, 'src', 'main', 'resources', 'assets', 'backrooms', 'textures', 'item')

    gen_back_wall(os.path.join(block_dir, 'back_wall.png'))
    gen_back_floor(os.path.join(block_dir, 'back_floor.png'))
    gen_back_ceiling(os.path.join(block_dir, 'back_ceiling.png'))
    gen_back_light(os.path.join(block_dir, 'back_light.png'))

    # existing placeholders
    gen_back_wall(os.path.join(block_dir, 'mind_storage.png'))

    print('Placeholder textures generated in:', block_dir)


if __name__ == '__main__':
    main()
