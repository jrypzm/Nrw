# yrs. folders: one hundred material folders for Nemo. Denim, leather,
# marble, felt, puffer, wood, see-through plastic, metal and more. Every
# folder gets its own, and folders side by side never share one.
#
# Nemo shows a folder's "custom icon" when one is set in its metadata. For
# each folder Nemo shows, this picks a design and sets it. Folders that
# already have an icon you chose yourself are left alone.

import hashlib
import json
import os

import gi

gi.require_version("Nemo", "3.0")
from gi.repository import GLib, GObject, Gio, Nemo  # noqa: E402

KEY = "metadata::custom-icon"
HOME = os.path.join(GLib.get_user_data_dir(), "yrs-folders")
DESIGNS = os.path.join(HOME, "designs")
TAGGED = os.path.join(HOME, "tagged")
CHOSEN = os.path.join(HOME, "chosen.json")


def ours(uri):
    return "/yrs-folders/" in uri


class Book:
    """Which design each folder has, grouped by the folder it sits in."""

    def __init__(self):
        try:
            self.designs = sorted(f for f in os.listdir(DESIGNS) if f.endswith(".png"))
        except OSError:
            self.designs = []
        try:
            with open(CHOSEN, encoding="utf-8") as f:
                self.by_parent = json.load(f)
        except (OSError, ValueError):
            self.by_parent = {}
        self.saving = 0

    def pick(self, path):
        parent, name = os.path.split(path.rstrip("/"))
        here = self.by_parent.setdefault(parent, {})
        have = here.get(name)
        if have in self.designs:
            return have
        taken = set(here.values())
        # a fixed order for this folder, so the choice does not depend on luck
        order = sorted(self.designs, key=lambda d: hashlib.sha1(f"{path}\0{d}".encode()).digest())
        choice = next((d for d in order if d not in taken), order[0])
        here[name] = choice
        if not self.saving:
            self.saving = GLib.timeout_add(1500, self.save)
        return choice

    def save(self):
        self.saving = 0
        try:
            os.makedirs(HOME, exist_ok=True)
            tmp = CHOSEN + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.by_parent, f)
            os.replace(tmp, CHOSEN)
        except OSError:
            pass
        return False


def remember(path):
    try:
        os.makedirs(HOME, exist_ok=True)
        with open(TAGGED, "a", encoding="utf-8") as f:
            f.write(path + "\n")
    except OSError:
        pass


class YrsFolders(GObject.GObject, Nemo.InfoProvider):
    def __init__(self):
        super().__init__()
        self.book = Book()

    def update_file_info(self, file):
        if not self.book.designs:
            return
        if file.get_uri_scheme() != "file" or not file.is_directory():
            return
        path = file.get_location().get_path()
        if not path or path == "/":
            return
        try:
            self.dress(path)
        except Exception:  # never take Nemo down with us
            pass

    def dress(self, path):
        loc = Gio.File.new_for_path(path)
        try:
            info = loc.query_info(KEY, Gio.FileQueryInfoFlags.NONE, None)
        except GLib.Error:
            return
        current = info.get_attribute_string(KEY)
        if current and not ours(current):
            return  # an icon you chose yourself
        uri = Gio.File.new_for_path(os.path.join(DESIGNS, self.book.pick(path))).get_uri()
        if current == uri:
            return
        try:
            loc.set_attribute_string(KEY, uri, Gio.FileQueryInfoFlags.NONE, None)
        except GLib.Error:
            return
        if not current:
            remember(path)
