#!/usr/bin/env python3
"""Builds install-yrs-files.sh with every icon size inside it (as base64), so the
installer is one self-contained file.

The large sizes come from source/folder.html (smoked glass over white pages, drawn
at 1024 px); 16 to 32 px are redrawn on the pixel grid by source/small.py.
"""
import base64
from pathlib import Path

HERE = Path(__file__).parent
SIZES = [16, 22, 24, 32, 48, 64, 96, 128, 256, 512]


def main():
    blocks = []
    for n in SIZES:
        data = base64.b64encode((HERE / "icons" / f"yrs-files-{n}.png").read_bytes()).decode()
        lines = "\n".join(data[i:i + 76] for i in range(0, len(data), 76))
        blocks.append(f"put {n} <<'PNG'\n{lines}\nPNG")
    script = (HERE / "install.sh.in").read_text().replace("@ICONS@", "\n".join(blocks))
    out = HERE / "install-yrs-files.sh"
    out.write_text(script)
    out.chmod(0o755)
    print(f"wrote {out.name} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
