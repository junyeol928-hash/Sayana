# Java道場(Sayana)

Java / Spring Boot の学習アプリ。PC版(Spring Boot + Thymeleaf)と、GitHub Pages で配るスマホ版(`m/`、オフライン PWA)がある。
使い方は [README.md](README.md)、設計は [docs/PLAN.md](docs/PLAN.md)、教材の書式は [docs/CONTENT_FORMAT.md](docs/CONTENT_FORMAT.md)、
見た目の決めごとは [docs/design.md](docs/design.md)、引き継ぎは [docs/handoff.md](docs/handoff.md)。

## 検証
- JDK 21 以上が要る(`JAVA_HOME` を JDK 21+ に。例: `~/.jdks/openjdk-26`)
- テスト: `./mvnw test`(全演習の採点・全サンプルの実行を含むので 5 分ほどかかる)
- スマホ版の作り直し: `./mvnw -q compile exec:java`(`content/` や `src/main/resources/mobile/`・`static/css/app.css` を変えたら必ず。古いままだと `MobileSiteTest` が落ちる)
- 演習 1 問だけの確認: `scripts/verify-exercise.sh content/.../exercises/ex1`(Git Bash。Python の PyYAML が要る)
- スマホ版のオフライン動作(見た目や Service Worker を変えたとき): `node scripts/offline-check.mjs . <出力フォルダ>`(Edge と Node 22 以上が要る。保存を待ってからネットワークを断ち、図のあるレッスン・読解・用語集が開けるかを撮る)
- レビュー用の撮影: `node scripts/review-shots.mjs docs/screenshots/<回>`(スマホ版を 8811 番などで配り、PC 版を起動しておく)
- 終了前に `./mvnw test` を実行し、失敗していたら直してから報告する

## 決めごと
- 改行は LF(`.gitattributes` で固定。Windows の autocrlf でもスマホ版の鮮度テストが通るように)
- 教材の HTML 断片(SVG の図)は `<figure class="figure">` に入れ、色は CSS 変数だけを使う(暗い表示でも読めるように)
- 見た目を変えるときは `docs/design.md` の定数(色・文字・余白・角・動き)に従い、変えたら design.md も直す
- テックシードの講義資料の文・図・例題は使わない(転載禁止)
