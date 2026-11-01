# 画像の出典とライセンス

このサイトで使っている画像・図形の出どころ。外部から取ってきた写真・イラストは使っていない(2026-11 時点)。

| もの | 場所 | 出どころ | ライセンス |
|---|---|---|---|
| アプリのアイコン(藍の布に麻の葉の刺し子) | `src/main/resources/mobile/icon.svg`、`src/main/resources/static/img/icon.svg` | このリポジトリで作成(手書きの SVG) | リポジトリと同じ |
| ヒーローの麻の葉の地模様 | `src/main/resources/static/css/app.css` の `.today::before`(データ URI の SVG) | このリポジトリで作成。麻の葉は日本の伝統文様で、図形そのものに著作権はない | リポジトリと同じ |
| 線のアイコン(ホーム・読解・復習・用語・設定など 11 個) | `src/main/resources/mobile/index.html` と `templates/fragments/layout.html` の `<symbol>` | このリポジトリで作成(24×24 の線画) | リポジトリと同じ |
| 教材の図(SVG) | `content/**/lesson.md` の `<figure class="figure">` | ローカル版 Java道場(同じ作者の教材)の図を移したもの、または新しく作成 | リポジトリと同じ |
| 教材の図(Mermaid) | `content/**/lesson.md` の ```mermaid | 文字で書いた図を Mermaid(MIT License、CDN)で描画 | 図の内容はリポジトリと同じ |

書体は端末に入っているもの(ヒラギノ角ゴシック・BIZ UDPゴシック・游ゴシック・Noto Sans JP など)だけを指定していて、Web フォントは配信していない(オフラインでも同じ見た目にするため)。
