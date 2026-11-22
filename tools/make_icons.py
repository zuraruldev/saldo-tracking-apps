#!/usr/bin/env python3
import os
import sys
from PIL import Image, ImageDraw

def main():
    if len(sys.argv) != 3:
        print("usage: make_icons.py <src_jpg> <res_dir>", file=sys.stderr)
        sys.exit(2)
    src_path, res_dir = sys.argv[1], sys.argv[2]
    im = Image.open(src_path).convert("RGBA")
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    square = im.crop((left, top, left + side, top + side))

    # Compute average border pixel color for adaptive icon background
    border_pixels = []
    rgb_sq = square.convert("RGB")
    for x in range(side):
        border_pixels.append(rgb_sq.getpixel((x, 0)))
        border_pixels.append(rgb_sq.getpixel((x, side - 1)))
    for y in range(side):
        border_pixels.append(rgb_sq.getpixel((0, y)))
        border_pixels.append(rgb_sq.getpixel((side - 1, y)))
    r = sum(p[0] for p in border_pixels) // len(border_pixels)
    g = sum(p[1] for p in border_pixels) // len(border_pixels)
    b = sum(p[2] for p in border_pixels) // len(border_pixels)
    bg_hex = f"#{r:02X}{g:02X}{b:02X}"

    values_dir = os.path.join(res_dir, "values")
    os.makedirs(values_dir, exist_ok=True)
    with open(os.path.join(values_dir, "ic_launcher_background.xml"), "w") as f:
        f.write(f'<resources>\n    <color name="ic_launcher_background">{bg_hex}</color>\n</resources>\n')

    densities = [
        ("mipmap-mdpi", 48, 108),
        ("mipmap-hdpi", 72, 162),
        ("mipmap-xhdpi", 96, 216),
        ("mipmap-xxhdpi", 144, 324),
        ("mipmap-xxxhdpi", 192, 432),
    ]
    for folder, legacy_px, adaptive_px in densities:
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)

        legacy = square.resize((legacy_px, legacy_px), Image.Resampling.LANCZOS)
        legacy.save(os.path.join(out_dir, "ic_launcher.png"))

        mask = Image.new("L", (legacy_px, legacy_px), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, legacy_px - 1, legacy_px - 1), fill=255)
        round_im = Image.new("RGBA", (legacy_px, legacy_px), (0, 0, 0, 0))
        round_im.paste(legacy, (0, 0), mask)
        round_im.save(os.path.join(out_dir, "ic_launcher_round.png"))

        fg_canvas = Image.new("RGBA", (adaptive_px, adaptive_px), (0, 0, 0, 0))
        inner_px = round(adaptive_px * 76 / 108)
        inner = square.resize((inner_px, inner_px), Image.Resampling.LANCZOS)
        offset = (adaptive_px - inner_px) // 2
        fg_canvas.paste(inner, (offset, offset))
        fg_canvas.save(os.path.join(out_dir, "ic_launcher_foreground.png"))

if __name__ == "__main__":
    main()
