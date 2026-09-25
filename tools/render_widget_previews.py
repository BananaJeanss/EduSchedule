#!/usr/bin/env python3
"""Rebuild widget-picker artwork. Requires Pillow and DejaVu Sans (Debian fonts-dejavu-core).

These explicitly labelled preview-only examples never enter the timetable model/cache.
All launcher paths use the same artwork: previewImage, previewLayout and generated previews.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
FONTS = Path('/usr/share/fonts/truetype/dejavu')
SCALE = 3
DATA = {
    '': {
        'example': 'Example schedule',
        'today': ('Today', ['08:45  Maths · 204', '10:00  English · 105', '11:05  Physics · 301']),
        'next': ('Up next', ['10:00  English · 105']),
        'upcoming': ('Upcoming lessons', ['10:00  English · 105', '11:05  Physics · 301', '12:10  Art · 202']),
        'week': ('Coming days', ['Mon 14 · 6 lessons', 'Tue 15 · 5 lessons', 'Wed 16 · 7 lessons']),
    },
    'et': {
        'example': 'Näidistunniplaan',
        'today': ('Täna', ['08:45  Matemaatika', '10:00  Inglise keel', '11:05  Füüsika · 301']),
        'next': ('Järgmine tund', ['10:00  Inglise keel']),
        'upcoming': ('Tulevased tunnid', ['10:00  Inglise keel', '11:05  Füüsika · 301', '12:10  Kunst · 202']),
        'week': ('Järgmised päevad', ['E 14 · 6 tundi', 'T 15 · 5 tundi', 'K 16 · 7 tundi']),
    },
}


def fit(draw, text, font, width):
    while draw.textlength(text, font=font) > width and text:
        text = text[:-2].rstrip() + '…'
    return text


def render(locale, dark, mode):
    title, lines = DATA[locale][mode]
    width, height = 180 * SCALE, (100 if mode == 'next' else 180) * SCALE
    image = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    surface, text, accent = ('#253024', '#E2E9DE', '#B5CEA8') if dark else ('#F1F5EB', '#1A2819', '#426437')
    draw.rounded_rectangle((0, 0, width-1, height-1), radius=28*SCALE, fill=surface)
    title_font = ImageFont.truetype(str(FONTS/'DejaVuSans-Bold.ttf'), 14*SCALE)
    body_font = ImageFont.truetype(str(FONTS/'DejaVuSans.ttf'), 14*SCALE)
    caption_font = ImageFont.truetype(str(FONTS/'DejaVuSans.ttf'), 10*SCALE)
    x, y, content_width = 16*SCALE, 16*SCALE, 148*SCALE
    draw.text((x, y), fit(draw, title, title_font, content_width), font=title_font, fill=accent)
    y += 28*SCALE
    for line in lines:
        draw.text((x, y), fit(draw, line, body_font, content_width), font=body_font, fill=text)
        y += 27*SCALE
    draw.text((x, height-26*SCALE), DATA[locale]['example'], font=caption_font, fill=accent)
    qualifiers = ('-'+locale if locale else '') + ('-night' if dark else '')
    output = RES / ('drawable'+qualifiers+'-nodpi') / f'widget_preview_{mode}.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, optimize=True)
    return image


if __name__ == '__main__':
    for locale in DATA:
        for dark in (False, True):
            for mode in ('today', 'next', 'upcoming', 'week'):
                render(locale, dark, mode)
