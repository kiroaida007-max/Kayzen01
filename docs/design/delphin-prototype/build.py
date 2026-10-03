"""Build delphin-prototype.html from src/ — one self-contained file.

    python3 build.py           write delphin-prototype.html
    python3 build.py --check   exit 1 if delphin-prototype.html is not up to date

src/index.html is the page; build markers in it are replaced by:
  fonts/fonts.css   with each font file inlined as a data URL
  styles.css        the stylesheet
  data.json         the demo data, with the images in assets/ added under DATA.assets
  app/*.js          the script, in file order
"""
import base64, json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
SRC = HERE / "src"
OUT = HERE / "delphin-prototype.html"
# Image files and the DATA.assets keys the script reads them from.
ASSETS = {
    "delphin-primary.png": "delphin-primary.png",
    "delphin-reverse.png": "delphin-reverse.png",
    "delphin_mediterranean_hero.png": "delphin-hero.jpg",
}
# Figma node export of the original frames (~550 KB): design reference, never read by the prototype.
OMIT = {"frames"}


def read(path):
    return (SRC / path).read_text(encoding="utf-8")


def b64(path):
    return base64.b64encode((SRC / path).read_bytes()).decode("ascii")


def build():
    fonts = re.sub(r"url\(([\w.-]+\.woff2)\)", lambda m: f"url(data:font/woff2;base64,{b64('fonts/' + m.group(1))})", read("fonts/fonts.css"))
    data = {k: v for k, v in json.loads(read("data.json")).items() if k not in OMIT}
    data["assets"] = {key: b64("assets/" + name) for key, name in ASSETS.items()}
    parts = {
        "fonts/fonts.css": fonts,
        "styles.css": read("styles.css"),
        "data.json": "const DATA=" + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";",
        "app/*.js": "".join(p.read_text(encoding="utf-8") for p in sorted((SRC / "app").glob("*.js"))),
    }
    html = read("index.html")
    for marker, text in parts.items():
        # Inlined text must not close its own <style> or <script> element.
        assert "</script" not in text.lower() and "</style" not in text.lower(), marker
        assert html.count(f"/* build: {marker} */") == 1, marker
        html = html.replace(f"/* build: {marker} */", text, 1)
    return html


if __name__ == "__main__":
    html = build()
    if "--check" in sys.argv:
        fresh = OUT.exists() and OUT.read_text(encoding="utf-8") == html
        print("up to date" if fresh else f"{OUT.name} is out of date: run python3 build.py")
        sys.exit(0 if fresh else 1)
    OUT.write_text(html, encoding="utf-8")
    print(f"{OUT.name}: {len(html.encode()):,} bytes")
