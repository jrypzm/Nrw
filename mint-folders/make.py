"""Builds yrs. folders.

  python3 make.py          draws the hundred designs (needs Node and Playwright),
                           packs them into yrs-folders-designs.tar.gz and writes
                           install-yrs-folders.sh with the plugin and the pack's checksum.
  python3 make.py --pack   skips the drawing and packs designs/png as it is.
"""
import hashlib
import io
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path



here = Path(__file__).resolve().parent
png = here / "designs" / "png"

if "--pack" not in sys.argv:
    from PIL import Image
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["node", str(here / "designs" / "draw.js"), tmp], check=True)
        png.mkdir(parents=True, exist_ok=True)
        for old in png.glob("*.png"):
            old.unlink()
        for f in sorted(Path(tmp).glob("*.png")):
            Image.open(f).convert("RGBA").resize((256, 256), Image.LANCZOS).save(png / f.name, optimize=True)

files = sorted(png.glob("*.png"))
assert len(files) == 100, len(files)

# a tarball that is the same every time for the same pictures
pack = here / "yrs-folders-designs.tar.gz"
buf = io.BytesIO()
with tarfile.open(fileobj=buf, mode="w", format=tarfile.USTAR_FORMAT) as tar:
    for f in files:
        info = tarfile.TarInfo(f.name)
        data = f.read_bytes()
        info.size, info.mtime, info.mode = len(data), 0, 0o644
        tar.addfile(info, io.BytesIO(data))
import gzip
with open(pack, "wb") as out:
    with gzip.GzipFile(fileobj=out, mode="wb", mtime=0, filename="") as gz:
        gz.write(buf.getvalue())
sha = hashlib.sha256(pack.read_bytes()).hexdigest()

plugin = (here / "yrs-folders.py").read_text(encoding="utf-8").rstrip("\n")
assert "\nPY\n" not in "\n" + plugin + "\n"
out = (here / "install.sh.in").read_text(encoding="utf-8").replace("@PLUGIN@", plugin).replace("@SHA256@", sha)
(here / "install-yrs-folders.sh").write_text(out, encoding="utf-8")
print(f"{len(files)} designs, pack {pack.stat().st_size // 1024} KB, sha256 {sha[:12]}")
