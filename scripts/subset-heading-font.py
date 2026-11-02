"""見出しの書体(Zen Kaku Gothic New)を、画面で使う文字だけに絞って woff2 にする。

教材の題(部・章・レッスン)と、画面のテンプレート・スクリプトに出てくる漢字、かな・英数字・記号を残す。
絞った文字の一覧は heading-chars.txt に書き、HeadingFontTest が「題の文字がすべて入っているか」を確かめる。
教材の題を変えてテストが落ちたら、これを実行し直す。

使い方(fonttools と brotli が要る。pip install fonttools brotli):
  python scripts/subset-heading-font.py <ZenKakuGothicNew の TTF があるフォルダ>
TTF の入手先: https://github.com/google/fonts/tree/main/ofl/zenkakugothicnew(SIL Open Font License 1.1)
"""
import pathlib
import sys

from fontTools import subset

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/static/css/fonts"
WEIGHTS = {"700": "ZenKakuGothicNew-Bold.ttf", "900": "ZenKakuGothicNew-Black.ttf"}
# 今は使っていなくても題に入りやすい記号は先に入れておく(全角の括弧・中黒・長音など)
BASE = "".join(chr(c) for c in range(0x20, 0x7F)) + "、。・「」『』()〜ー―…!?:/+%&=#〇一二三四五六七八九十" \
    + "".join(chr(c) for c in range(0x3041, 0x3097)) + "".join(chr(c) for c in range(0x30A1, 0x30FB)) + "ー"


def is_kanji(c):
    return "一" <= c <= "鿿" or c in "々〆"


def heading_chars():
    chars = set(BASE)
    for p in (ROOT / "content").rglob("*.yml"):
        if p.name in ("lesson.yml", "chapter.yml", "part.yml"):
            for line in p.read_text(encoding="utf-8").splitlines():
                if line.startswith("title:"):
                    chars.update(line[len("title:"):].strip().strip('"'))
    ui = [*(ROOT / "src/main/resources/templates").rglob("*.html"),
          ROOT / "src/main/resources/mobile/app.js", ROOT / "src/main/resources/static/js/app.js"]
    for p in ui:
        chars.update(c for c in p.read_text(encoding="utf-8") if is_kanji(c))
    return "".join(sorted(c for c in chars if c.isprintable()))


def main():
    src = pathlib.Path(sys.argv[1])
    text = heading_chars()
    OUT.mkdir(parents=True, exist_ok=True)
    for weight, name in WEIGHTS.items():
        opts = subset.Options()
        opts.flavor = "woff2"
        opts.layout_features = ["palt", "kern", "vert", "vrt2"]
        opts.name_IDs = ["*"]  # 著作権とライセンスの名前レコードを残す
        font = subset.load_font(str(src / name), opts)
        sub = subset.Subsetter(opts)
        sub.populate(text=text)
        sub.subset(font)
        out = OUT / f"zen-kaku-gothic-new-{weight}.woff2"
        subset.save_font(font, str(out), opts)
        print(out.name, out.stat().st_size // 1024, "KB")
    (OUT / "heading-chars.txt").write_text(text + "\n", encoding="utf-8", newline="\n")
    print("文字数", len(text))


if __name__ == "__main__":
    main()
