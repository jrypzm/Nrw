#!/usr/bin/env python3
"""Builds install-yrs-mpv.sh: the pink sleepy icon for mpv, every size inside.

  python3 make.py           paints the icon with source/sleepy.py (numpy, scipy, Pillow) at 1024 px, scales it to
                            every size into icons/, and writes the installer
  python3 make.py --pack    writes the installer from icons/ as it is
"""
import base64
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).parent
SIZES = [16, 22, 24, 32, 48, 64, 96, 128, 256, 512]


def draw():
    from PIL import Image
    with tempfile.TemporaryDirectory() as tmp:
        master = Path(tmp) / "master.png"
        subprocess.run([sys.executable, str(HERE / "source" / "sleepy.py"), "pink", str(master), "1024"], check=True)
        big = Image.open(master).convert("RGBA")
        for n in SIZES:
            big.resize((n, n), Image.LANCZOS).save(HERE / "icons" / f"mpv-{n}.png", optimize=True)


def pack():
    blocks = []
    for n in SIZES:
        data = base64.b64encode((HERE / "icons" / f"mpv-{n}.png").read_bytes()).decode()
        lines = "\n".join(data[i:i + 76] for i in range(0, len(data), 76))
        blocks.append(f"put {n} <<'PNG'\n{lines}\nPNG")
    script = (HERE / "install.sh.in").read_text().replace("@ICONS@", "\n".join(blocks))
    out = HERE / "install-yrs-mpv.sh"
    out.write_text(script)
    out.chmod(0o755)
    print(f"wrote {out.name} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    if "--pack" not in sys.argv:
        draw()
    pack()
