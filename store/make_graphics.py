"""Renders Play Store graphics for Wisp.

    python3 store/make_graphics.py [raw_screenshot_dir]

- icon.png (512x512) and featureGraphic.png (1024x500), drawn with the same geometry
  as app/src/main/res/drawable/ic_launcher_foreground.xml.
- With a directory of raw 1080x2400 screenshots named NN_*.png, frames each one with its caption
  from store/captions.txt on a 1080x1920 canvas (Play requires an aspect ratio of at most 2:1).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "fastlane", "metadata", "android", "en-US", "images")
BG = (11, 95, 96)          # @color/launcher_background
BG_DARK = (6, 58, 59)
WHITE = (255, 255, 255)
FONT = "/System/Library/Fonts/Helvetica.ttc"


def draw_mark(img, ox, oy, s):
    """Draws the icon foreground (108x108 viewport) at offset (ox, oy) with scale s."""
    d = ImageDraw.Draw(img, "RGBA")
    P = lambda x, y: (ox + x * s, oy + y * s)

    def line(points, w):
        pts = [P(*p) for p in points]
        d.line(pts, fill=WHITE, width=round(w * s), joint="curve")
        for p in (pts[0], pts[-1]):  # round caps
            r = w * s / 2
            d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=WHITE)

    def dot(cx, cy, r, alpha):
        d.ellipse([*P(cx - r, cy - r), *P(cx + r, cy + r)], fill=(255, 255, 255, round(255 * alpha)))

    # Face: stroke centred on r=17, width 5
    d.ellipse([*P(50 - 19.5, 57 - 19.5), *P(50 + 19.5, 57 + 19.5)], outline=WHITE, width=round(5 * s))
    line([(50, 48), (50, 57), (56, 61)], 4.5)
    # Bells: circle segments (r=10) matching the "A10,10 ... Z" paths in the vector.
    d.chord([*P(39.79 - 10, 40.79 - 10), *P(39.79 + 10, 40.79 + 10)], 158.2, 291.8, fill=WHITE)
    d.chord([*P(60.21 - 10, 40.79 - 10), *P(60.21 + 10, 40.79 + 10)], 248.2, 381.8, fill=WHITE)
    line([(39, 72), (35.5, 76.5)], 4.5)
    line([(61, 72), (64.5, 76.5)], 4.5)
    dot(73, 46, 2.8, 0.85)
    dot(78.5, 39.5, 2.1, 0.55)
    dot(83, 33.5, 1.5, 0.3)


def gradient(w, h):
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG, BG_DARK)))
    return img


def icon():
    img = gradient(512, 512)
    # The adaptive icon's 108dp canvas maps to the full 512px square, with the same group transform
    # as the vector: scale 0.82 about (54,54), then translate (-3, 1).
    S = 512 / 108
    draw_mark(img, S * (54 * 0.18 - 3), S * (54 * 0.18 + 1), S * 0.82)
    img.save(os.path.join(OUT, "icon.png"))


def feature_graphic():
    img = gradient(1024, 500)
    draw_mark(img, 30, 20, 4.2)
    d = ImageDraw.Draw(img)
    d.text((480, 140), "Wisp", font=ImageFont.truetype(FONT, 120), fill=WHITE)
    d.text((486, 290), "Alarms that tidy up", font=ImageFont.truetype(FONT, 40), fill=(210, 240, 238))
    d.text((486, 340), "after themselves", font=ImageFont.truetype(FONT, 40), fill=(210, 240, 238))
    img.save(os.path.join(OUT, "featureGraphic.png"))


def fit_font(draw, text, max_width, size=72):
    """Largest font (down to 40px) that fits; longer captions wrap onto two lines."""
    while size > 40:
        font = ImageFont.truetype(FONT, size)
        if draw.textlength(text, font=font) <= max_width:
            return font, [text]
        size -= 4
    font = ImageFont.truetype(FONT, 56)
    words, lines = text.split(), [""]
    for w in words:
        trial = (lines[-1] + " " + w).strip()
        if draw.textlength(trial, font=font) <= max_width:
            lines[-1] = trial
        else:
            lines.append(w)
    return font, lines


def screenshots(raw_dir):
    out_dir = os.path.join(OUT, "phoneScreenshots")
    for old in os.listdir(out_dir):
        os.remove(os.path.join(out_dir, old))
    with open(os.path.join(os.path.dirname(__file__), "captions.txt"), encoding="utf-8") as f:
        captions = dict(line.strip().split("=", 1) for line in f if "=" in line)
    for name in sorted(f for f in os.listdir(raw_dir) if f.endswith(".png")):
        caption = captions[name[:2]]
        canvas = gradient(1080, 1920)
        shot = Image.open(os.path.join(raw_dir, name)).convert("RGB")
        h = 1560
        shot = shot.resize((round(shot.width * h / shot.height), h), Image.LANCZOS)
        mask = Image.new("L", shot.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, *shot.size], radius=40, fill=255)
        canvas.paste(shot, ((1080 - shot.width) // 2, 1920 - h - 60), mask)
        d = ImageDraw.Draw(canvas)
        font, lines = fit_font(d, caption, 960)
        line_h = font.size * 1.2
        y = (1920 - h - 60 - line_h * len(lines)) / 2
        for line in lines:
            d.text(((1080 - d.textlength(line, font=font)) / 2, y), line, font=font, fill=WHITE)
            y += line_h
        canvas.save(os.path.join(out_dir, name[:2] + ".png"))


if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "phoneScreenshots"), exist_ok=True)
    icon()
    feature_graphic()
    if len(sys.argv) > 1:
        screenshots(sys.argv[1])
