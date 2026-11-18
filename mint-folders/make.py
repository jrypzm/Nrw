"""Builds install-yrs-folders.sh: the installer with the Nemo plugin inside."""
from pathlib import Path

here = Path(__file__).resolve().parent
plugin = (here / "yrs-folders.py").read_text(encoding="utf-8").rstrip("\n")
assert "\nPY\n" not in "\n" + plugin + "\n"
out = (here / "install.sh.in").read_text(encoding="utf-8").replace("@PLUGIN@", plugin)
(here / "install-yrs-folders.sh").write_text(out, encoding="utf-8")
print("wrote install-yrs-folders.sh,", len(out), "bytes")
