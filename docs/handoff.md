# 引き継ぎメモ

Claude Code と Codex で作業を引き継ぐときのメモ。作業の前に読み、ファイルを変えたら終わる前に直す。

## 現状
- ブランチ `claude/redesign-infra`(`main` から分岐。push はまだしていない)。中身は 2 つ
  - ローカル版 Java道場(読解 171 問・仕事のコード・用語集)と、Artifact「Spring Boot 入門ガイド」を教材に移した
  - 見た目を「藍の稽古着」に作り直した(スマホ版 `m/` と PC 版。決めごとは [design.md](design.md))
- 動いているもの: PC 版(`./mvnw spring-boot:run`)、スマホ版(`m/`。オフラインで全 55 レッスン・図・読解・用語集が開ける。`scripts/offline-check.mjs` で確認済み)、`./mvnw test` は全部通る
- Claude ↔ Codex の相互レビューを、両方が Awwwards の 4 項目すべて 8.5 以上になるまで回している。点数と直したことは [design-review.md](design-review.md)、全文は `reviews/`、撮影は `screenshots/roundN/`
  - Codex は利用上限で 5〜8 回目を審査できていない
- 公開前の確認事項(ユーザーの判断待ち)
  - 公開リポジトリに、仕事のコードに見える名前(HsmClient・BackApiClient・SessionStartService・APDU・cardChallenge・Front/Back サーバ)が `content/` の atwork・reading・spring-reading-code に入っている。一般的な名前に置き換えるかを決めてもらう
  - push・PR・GitHub Pages への公開は、ユーザーが見て OK を出してから
  - ローカル版 Java道場(`C:\Users\yeol0\Claude\Work\Java道場`)とガイドの Artifact を消してよいか(こちらでは消さない)

## 変更内容(直近)
- 9 回目のレビューの指摘を直した: 用語集を 1 列の辞書の組みに、図を重心から見せて角丸・藍の線に、ループの図を縦 1 列に、朱と緑の数を減らした、地図は章の切れ目でだけ折る、段のボタンは「7段中3」、暗い表示の褐を藍に寄せた、解説の長いエラーメッセージをブロックに、縫い進む線を目の切れ目で止める、セレクトの角・難しさの位置・質問ボタンの枠
- 8 回目: 染まり具合を部ごとの地図に、暗い表示の段の目立ち方、正解の文字、読む時間を本文から数える、PC の前回の続きの段の名前を段の id から引く
- レビュー用の撮影は `scripts/review-shots.mjs`、オフラインの確認は `scripts/offline-check.mjs`(どちらもヘッドレスの Edge)

## 次に依頼すること
- 10 回目の相互レビュー(Claude と Codex に同じ依頼文で。撮影は `docs/screenshots/round10/`)を回し、指摘を直す。両方が全項目 8.5 以上になったら終わり
- 終わったら、上の「公開前の確認事項」をユーザーに聞く
- 後回しにしていること: Spring Boot 3.5 の OSS サポートは 2026-06-30 で終わっている見込み(endoflife.date による。spring.io のページには表が出ず、一次資料では未確認)。4.x への移行は、spring.io の公式の表で確かめてから別の作業として相談
