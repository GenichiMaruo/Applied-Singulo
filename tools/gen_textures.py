"""Generate Applied Singulo textures (original pixel art: AE2-like interface structure, Singulo palette).

Usage: python tools/gen_textures.py   (writes into src/main/resources/assets)
Only the standard library is used; output PNGs are committed, this script documents how they were made.
"""
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets"

# Palette: the AE2 ME interface colour scheme (white frame, slate ring, blue screen) with Singulo accents
# (the sleeping android face of Singulo's work-block screens in the centre, purple corner rivets). Pixels are drawn here, not copied from AE2.
T = (0, 0, 0, 0)
WHITE = (242, 242, 242, 255)
WHITE2 = (232, 232, 234, 255)
SLATE = (65, 63, 84, 255)
SLATE2 = (77, 77, 103, 255)
BLUE = (125, 169, 210, 255)
BLUE_L = (156, 211, 255, 255)
BLUE_D = (112, 140, 186, 255)
SIDE = (193, 195, 208, 255)
SIDE2 = (213, 214, 220, 255)
PURPLE = (126, 92, 178, 255)
SCREEN = (16, 22, 30, 255)
SCREEN_GLOW = (40, 70, 86, 255)
EYE = (76, 116, 131, 255)
BLUSH = (75, 50, 63, 255)
MOUTH = (56, 85, 97, 255)


def write(path, px):
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)

    def chunk(t, c):
        return struct.pack(">I", len(c)) + t + c + struct.pack(">I", zlib.crc32(t + c) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(data)


def grid(w, h, fill):
    return [[fill for _ in range(w)] for _ in range(h)]


def ring(px, i, color, shade=None):
    """Square ring at inset i on a 16x16 face; shade (if given) for the bottom/right edges."""
    n = len(px)
    for k in range(i, n - i):
        px[i][k] = color
        px[k][i] = color
        px[n - 1 - i][k] = shade or color
        px[k][n - 1 - i] = shade or color


def screen(px):
    """AE2-style blue rim around a Singulo android screen: dark display with the sleeping android face
    (closed eyes, blush, small mouth) used on Singulo's android work blocks."""
    ring(px, 3, BLUE)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):   # light rim corners
        px[y][x] = BLUE_L
    for y in range(4, 12):
        for x in range(4, 12):
            px[y][x] = SCREEN
    for x in range(5, 11):                                 # cyan glow along the top, like Singulo's screens
        px[4][x] = SCREEN_GLOW
    for (x, y) in ((4, 6), (6, 6), (5, 7), (9, 6), (11, 6), (10, 7)):   # closed eyes
        px[y][x] = EYE
    for (x, y) in ((4, 8), (11, 8)):                       # blush
        px[y][x] = BLUSH
    for x in (7, 8):                                       # mouth
        px[9][x] = MOUTH


def block_face():
    px = grid(16, 16, WHITE)
    for k in (1, 5, 9, 12):             # light frame variation
        px[0][k] = px[15][15 - k] = WHITE2
        px[k][0] = px[15 - k][15] = WHITE2
    ring(px, 1, SLATE)
    for k in (4, 7, 8, 11):             # notches in the slate ring
        px[1][k] = px[14][k] = SLATE2
        px[k][1] = px[k][14] = SLATE2
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[y][x] = PURPLE
    ring(px, 2, WHITE, WHITE2)
    screen(px)
    return px


def part_front():
    px = grid(16, 16, T)
    for y in range(2, 14):
        for x in range(2, 14):
            px[y][x] = WHITE
    ring(px, 2, WHITE, WHITE2)
    screen(px)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = PURPLE
    return px


def part_sides():
    px = grid(16, 16, SIDE)
    for k in range(16):
        px[0][k] = px[15][k] = WHITE
        px[1][k] = px[14][k] = SLATE
        px[k][0] = WHITE2
        px[k][15] = WHITE2
    for k in range(2, 14):
        px[k][1] = px[k][14] = SLATE2
    for y in range(3, 13):
        px[y][7] = px[y][8] = SIDE2
    return px


def part_back():
    px = grid(16, 16, SIDE)
    ring(px, 0, SIDE2)
    ring(px, 2, SLATE)
    ring(px, 3, SLATE2)
    for y in range(4, 12):
        for x in range(4, 12):
            px[y][x] = BLUE_D
    ring(px, 5, BLUE)
    for y in (7, 8):
        for x in (7, 8):
            px[y][x] = PURPLE
    return px


def gui_background():
    """176x252 screen background in AE2's interface style (AE2 palette, own layout); 256x256 canvas.

    Slot frames: orders (items at y 72), keep in stock (104), waiting for ME (136), player inventory (168/186/204, hotbar 226).
    """
    W, H = 176, 252
    outline, hi, panel, lo = (65, 63, 84, 255), (242, 242, 242, 255), (203, 204, 212, 255), (135, 143, 165, 255)
    slot, slot_edge = (173, 176, 196, 255), (154, 159, 180, 255)
    px = grid(256, 256, T)
    for y in range(H):
        for x in range(W):
            px[y][x] = panel
    for k in range(W):
        px[0][k] = px[H - 1][k] = outline
        px[1][k] = hi
        px[H - 2][k] = lo
    for k in range(H):
        px[k][0] = px[k][W - 1] = outline
        px[k][1] = hi
        px[k][W - 2] = lo
    for (x, y) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):   # rounded corners like AE2 panels
        px[y][x] = T

    def cell(x, y):   # x, y = item position; the frame is 18x18 around it
        for j in range(y - 1, y + 17):
            for i in range(x - 1, x + 17):
                px[j][i] = slot
        for i in range(x - 1, x + 17):
            px[y - 1][i] = slot_edge
            px[y + 16][i] = hi
        for j in range(y - 1, y + 17):
            px[j][x - 1] = slot_edge
            px[j][x + 16] = hi

    for row_y in (72, 104, 136, 168, 186, 204, 226):
        for c in range(9):
            cell(8 + 18 * c, row_y)
    return px


def emitter(lit):
    """Status emitter lamp: AE2-like white/slate frame around a lamp (red when emitting)."""
    px = grid(16, 16, T)
    for y in range(4, 12):
        for x in range(4, 12):
            px[y][x] = WHITE
    for k in range(4, 12):
        px[4][k] = px[11][k] = px[k][4] = px[k][11] = SLATE
    core = (230, 60, 50, 255) if lit else (90, 40, 46, 255)
    glow = (255, 150, 120, 255) if lit else (120, 60, 66, 255)
    for y in range(6, 10):
        for x in range(6, 10):
            px[y][x] = core
    px[6][6] = glow
    for (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px[y][x] = PURPLE
    return px


def monitor_front():
    """Order monitor: AE2-like frame around a dark Singulo screen (text and icon are drawn dynamically)."""
    px = grid(16, 16, T)
    for y in range(2, 14):
        for x in range(2, 14):
            px[y][x] = SCREEN
    ring(px, 2, WHITE, WHITE2)
    ring(px, 3, BLUE)
    for x in range(4, 12):
        px[4][x] = SCREEN_GLOW
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = PURPLE
    return px


def main():
    write(ROOT / "applied_singulo/textures/block/android_me_interface.png", block_face())
    write(ROOT / "applied_singulo/textures/part/android_me_interface_panel.png", part_front())
    write(ROOT / "applied_singulo/textures/part/android_me_interface_panel_sides.png", part_sides())
    write(ROOT / "applied_singulo/textures/part/android_me_interface_panel_back.png", part_back())
    write(ROOT / "applied_singulo/textures/part/android_status_emitter_off.png", emitter(False))
    write(ROOT / "applied_singulo/textures/part/android_status_emitter_on.png", emitter(True))
    write(ROOT / "applied_singulo/textures/part/android_order_monitor.png", monitor_front())
    # AE2 resolves screen textures in its own namespace, so the GUI lives under ae2/…/applied_singulo/.
    write(ROOT / "ae2/textures/guis/applied_singulo/android_interface.png", gui_background())


if __name__ == "__main__":
    main()
