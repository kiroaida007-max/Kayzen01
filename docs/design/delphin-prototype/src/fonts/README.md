# Fonts

Subsets of three open-source families, licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org), as WOFF2. `build.py` inlines them.

| File | Family | Version | Copyright |
|---|---|---|---|
| `inter-400/500/600/700.woff2` | Inter (static instances) | 4.001 | 2016 The Inter Project Authors (https://github.com/rsms/inter) |
| `cormorant-garamond-600.woff2` | Cormorant Garamond (weight 600 instance) | 4.001 | 2015 The Cormorant Project Authors (github.com/CatharsisFonts/Cormorant) |
| `noto-sans-arabic-400/700.woff2` | Noto Sans Arabic | 2.010 | 2022 The Noto Project Authors (https://github.com/notofonts/arabic) |

Subsets (all OpenType features kept), made with fontTools from the full TTF files:

```bash
pip install fonttools brotli
LATIN="U+0000-024F,U+0259,U+02B0-02FF,U+0300-036F,U+1E00-1EFF,U+2000-206F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2200-22FF,U+25A0-25FF,U+2713-2717,U+FEFF,U+FFFD"
ARABIC="U+0000-007F,U+00A0-00FF,U+0600-06FF,U+0750-077F,U+08A0-08FF,U+200C-200F,U+2010-2027,U+FB50-FDFF,U+FE70-FEFF"
pyftsubset Inter-Regular.ttf --unicodes="$LATIN" --layout-features='*' --name-IDs='*' --flavor=woff2 --output-file=inter-400.woff2
pyftsubset NotoSansArabic-Regular.ttf --unicodes="$ARABIC" --layout-features='*' --name-IDs='*' --flavor=woff2 --output-file=noto-sans-arabic-400.woff2
```

A character outside these ranges falls back to the system font; re-subset to add it.
