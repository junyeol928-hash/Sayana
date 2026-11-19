# 26 回目の審査(Claude・Awwwards の物差し)

見たもの: `docs/screenshots/round26/` の 39 枚すべてと、`round25/` の同名の撮影(m-sample-closed ほか)。物差しは `docs/review-rubric.md` の「18 回目から」と D・U・C・N・L。25 回目の 2 人の全文(`docs/reviews/round25-claude.md`・`round25-claude2.md`)、`docs/design.md`(全文)、`docs/design-review.md` の 25 回目の表を読んだ。もう 1 人の 26 回目は開いていない。
読んだソース: `mobile/app.js`(`renderHome`・`nextSectionFor`・`stepStates`・`renderLesson`・`trackLesson`・のぞき見の `pendingPeek`/`PEEK_BACK`・`phraseWrap`・`msgLike`/`proseLike`・decorate の code-msg と小文字→大文字の後付けの候補)、`static/css/app.css`(`.lesson-axis`・`.today-axis`・`.peek-back`・`.missed-more`・`.retry-done`)、`static/js/app.js`(前回の続きの差し替え・`trackLesson` の `?from=` と 1/3 の線)、`templates/review.html`・`fragments/review-parts.html`・`glossary.html`・`reading.html`、`ReviewService#retryOrder`、`application.yml`、`scripts/review-shots.mjs`。教材は `fd5c554` の checks.yml の差分(2 択に足した 3 つ目の誤答)。

撮り足し(リポジトリは変えていない。`./mvnw` も git も触っていない): 起動中のスマホ版(8811)と PC 版(8093)を、別のプロファイルのヘッドレス Edge(ポート 9681〜9689)で撮った。置き場所は `r26a/`(台本 `cdp.mjs`・`kinsoku26.mjs`・`peek26.mjs`・`axis26.mjs`・`axis-shots.mjs`・`homeaxis26.mjs`・`spots26.mjs`・`msg26.mjs`・`pc26.mjs`・`count26.py`、記録 `kinsoku26-out.txt`・`kinsoku26-summary.txt`・`kinsoku26-lists.txt`・`msg26-out.txt`・`notes-peek26.txt`・`notes-axis26.txt`・`notes-homeaxis26.txt`・`notes-spots26.txt`・`notes-pc26.txt`・`count26-out.txt`・`body-tfn.txt`・`where-msg.txt`)。
- **のぞき見を 4 通りで通した**(`peek26.mjs`。撮影用の進み具合): A 復習の「読み直す」だけ → ホーム、B のぞき見なしで spring-di のサンプルの終わりまで読む → ホーム、C 読解のタブの出どころ、D 用語集の「〇〇 で」。`m-back-to-review`・`m-sample-end-read`・`m-home-after-sample`・`m-peek-reading`・`m-peek-terms`
- **レッスンの冒頭の縦書きの軸**(`axis26.mjs`・`axis-shots.mjs`・`homeaxis26.mjs`): 全 55 レッスンを 390px・375px・1440px で。`m-axis-java-hello`・`s375-axis-java-hello`・`axis-java-if`・`axis-spring-security`・`axis-sts-debugger`・`axis-capstone-apply`・`axis-java-collections`・`axis-spring-di`
- **組版**(`kinsoku26.mjs`。390px、全 55 レッスン、クイズと手を止めては答えて解説も組んだ)と、1 行のブロック(code-msg)の中身の分け(`msg26.mjs`)。目で確かめた所: `m-msg-generics`・`m-msg-sealed`・`m-msg-approve`・`m-msg-arrays`・`m-sts-resolved`・`m-column-literal`
- 読解のカードの「答え合わせ」: `m-reading-chosen`
- **PC ののぞき見**(`pc26.mjs`。依頼書で許された記録の操作): `?from=review` と、比べとして `?from` なし。`p-peek-landing`

測った値:
- **のぞき見**(スマホ): A のあとホームは前と同じ「DI と IoC コンテナ · 今日の復習から まず 10 問 約 4 分」。着地は `#/lesson/java-if` の問 1 にフォーカス、段の帯に「復習へ戻る」(高さ 44px)。着いたレッスンで 1,800px 読み進めても `S.last` は `spring-di / quiz / 40` のまま。「復習へ戻る」で戻ると、復習の次の問い(「残り 19 問 今日 1 / 10」)から。C・D も「読解へ戻る」「用語集へ戻る」が出て、ホームは変わらない(着地はどちらもレッスンの頭)
- **サンプルを読み終えたあとのホーム**(B。のぞき見なし): サンプルの段にレ点が付き(`runDone: true`)、140px の線はまだサンプルの中(クイズの見出しは 852px)。ホームは「前回の続き サンプルから / 7 段のうち 4 段済み · 次は サンプル / サンプルの続きから」。「今日の復習から」は主ボタンから下の行へ落ちる。**公式の m-home-after-reread が m-home-light と違うのはこれ**(撮影の台本が読み直すの前に m-sample-closed でサンプルを開いている)
- **PC ののぞき見**: `?from=review#quiz-q0` は段の印「クイズ」、フォーカス `quiz-q0`、右に「復習へ戻る」。1,400px 読み進めてもホームは「DI と IoC コンテナ · 演習へ進む」のまま。`?from` なしで同じ所を開くと「条件分岐 · クイズの続きから」に替わる(正しい)
- **縦書きの軸**(レッスンの冒頭): 軸の高さに収まらず 2 列目へ折れるもの **390px で 33 / 55、375px で 27 / 55**、PC は 0。java-hello は「第一部 Java基」の左に「礎」1 字、「第一章 はじめの一」の左に「歩」1 字(m-axis-java-hello)。java-if は「第二章 制御構 / 文と配列」、sts-debugger は「デバッグとリファク / タリング」と語の途中で列が替わる。ホームの軸は 6 本試して 0
- **組版**(390px、インラインのコード 15,186 個): 行の説明(ln-note)の中のチップ 324 個のうち色付けの塊 **0**(25 回目は 13 個の説明がすべて塊)。本文の 1 行のブロック 90 個(25 回目 0)。3 行以上に割れるチップ **105**(25 回目 153。本文の段落・箇条書きは 13、ほかは解説・行の説明・STS の手順・演習の問題のコードの式が主)。20 字以下のチップが 2 行に割れる 176(241)。28 字未満の語が語の途中で割れる 20(33)のうち、9 は定数の `_` の後ろ、7 は後付けの小文字→大文字の境目で、意図どおり。本当に語の途中なのは 3(演習の問題の `IllegalArgumentExcepti` / `on` が 2、表の `DemoAppl` / `icationTests`)。チップの直後の約物が行頭 1・開き括弧が行末 1(どちらも表の中)
- **1 行のブロックの中身**(`msg26-out.txt`): ブロック 239 個(解説を組む前)のうち、Java の宣言・式・引数の並びが約 30(本文 14: `interface OrderRepository extends JpaRepository<Order, Long>`・`sealed interface Payment permits Card, Cash`・`BusinessRuleException extends Exception`・`(double) total / a.length`・`1000L * 60 * 60 * 24 * 30`・`@ModelAttribute Member member` ほか。本文の外 16: `implements Comparable<Item>`・`Map<String, PaymentGateway> gateways`・`@RequestBody CreateTaskRequest req` ほか)。設定の 1 行・コマンドが約 15。**ブロックの直後の文が「。」「、」で始まり、次の行の頭に約物が来るもの 11**
- **見出し**: サンプルの見出しで「@」だけが行末に残るもの 2(spring-di「`@Configuration + @Bean と、@` / `Primary / @Qualifier`」、capstone-release「API のエラーを @ / RestControllerAdvice …」)。助詞が行頭 1(spring-mockmvc「MockMvc / によるテスト」)
- **読解のカード**: 選ぶ前は押せない「選んでから答え合わせ」316×52px、選ぶと藍の「答え合わせ」
- **教材**(`count26-out.txt`): 問い・選択肢・why・解説・追跡の地の文の `true`・`false`・`null` **0**(チップ 571)。本文の地の文には 79 残る(まとめの「既定値(0 / false / null)」、見出し「参照の代入と null」など)。仕事のコードの行の説明・要点・前置き 390 のうち地の文の識別子 **5**(151。残りは「HelloWorld」「IntelliJ」など)。ゴール 273 のうち **1**(107。「GitHub」)。2 択の手を止めて **0**(22)。why がブロック 0。いちばん長い選択肢が正解: クイズ 19%・手を止めて 23%・読解 14%、いちばん短い: 12%・35%・14%。サンプルの説明でチップと地の文の CamelCase が混ざる 7、説明と予想で問いが 2 つ 47(どちらも 25 回目のまま)

## 1. 点数

| 軸 | 点 | 重み | 掛けた値 |
|---|---|---|---|
| Design | 8.0 | 0.4 | 3.20 |
| Usability | 8.5 | 0.3 | 2.55 |
| Creativity | 9.0 | 0.2 | 1.80 |
| Content | 9.0 | 0.1 | 0.90 |
| **加重平均** | **8.45** | | |
| 学びやすさ(L) | **8.5** | (別に付ける) | |

合格の線(加重平均 8.5・どの軸も 8.0 以上・L 8.5)に、**この審査員の採点では 0.05 届かない**。届かないのは加重平均だけ。

25 回目に私が挙げた 8 件は、読解の選択肢の長さの偏り(小さな 1 行)を除いて、全部言ったとおりに入った。行の説明のチップの塊 13 → 0、読み直すで前回の続きが替わる穴はスマホ・PC・読解・用語集の 4 つの入口とも塞がり、PC の段の印・右の列の「次の復習へ」・why の化け・仕事のコードとゴールの識別子(151 → 5、107 → 1)も直った。もう 1 人の筆頭(読解の答え合わせ・2 択・`true`/`false`/`null`・PC のセッション)も入っている。Content は N4 の「一部」が消えて 9.0 に上げた。
それでも Design が 8.0 のままなのは、撮影に写らない所に 2 つ、見て分かる崩れがあるから。1 つは**スマホのレッスンの冒頭の縦書きの軸が、55 本のうち 33 本で 2 列目へ折れる**こと(最初のレッスン java-hello で「礎」「歩」が 1 字ずつ左の列へ。14 回目からあったもので、今回見つけた。D5)。もう 1 つは今回の直しの副作用で、**本文のメッセージをブロックにする判定が Java の宣言まで拾い、行頭に「。」が来る**ことと、**サンプルの見出しで「@」だけが行末に残る**こと(公式の m-sample-closed に写っている。D1)。
Usability は、**仕事のコード・サンプルを読み終えて閉じると、ホームの主ボタンが済んだ段の「サンプルの続きから」になり、「今日の復習から」が主役から落ちる**(U1)。公式の m-home-after-reread がのぞき見で主役が替わったように見えるのは、この別の不具合のせい。
下の 4 の 1(軸)を直せば D5 が消え、Design は一部 1 つで 8.5、加重平均 8.65 で線を越える。4 の 2 を直せば L は 9.0 に戻る。

## 2. 項目の判定

### Design
- D1 書体: **一部**。(1) サンプルの見出しの「@」が前の語に付き、「`@Configuration + @Bean と、@` / `Primary / @Qualifier`」と名前の頭の記号だけが行末に残る(m-sample-closed・m-sample-result。25 回目は「と、」が行頭だったが、今度は「@」と「Primary」が離れた)。(2) 本文のメッセージを 1 行のブロックにする判定(`msgLike`)が、`; { } =` の無い宣言・式も拾う。`sealed interface Payment permits Card, Cash` が藍の線のブロックになって文が 3 つに割れ(r26a/m-msg-sealed)、手を止めての問いは「`interface OrderRepository extends JpaRepository<Order, Long>`」のブロックの次の行が「。親の…」と句点で始まる(r26a/m-msg-generics)。宣言・式が約 30、行頭の「。」「、」が 11。(3) STS の手順・コラムのメッセージは、`;` や括弧のためにチップのまま 3〜6 行(r26a/m-sts-resolved の「`Resolved [org.springframework.web.HttpMediaType…` 」6 行、r26a/m-column-literal 3 行)。よい所: 本文の段落・箇条書きで 3 行以上に割れるチップは 13 まで減り、行の説明のチップは文の中の語になった。終わりの 1 行は「仮縫い / できた。」と語の境目で折れる
- D2 色: 満たす。行の説明のチップは白 10% の地で、暗いコードの面の上でも文の中の語に見える(m-lnnote-chip)
- D3 余白: 満たす。細かい所: 区切りの画面の下 35% の空き(m-review-break。25 回目のまま、本物の流れでは行が入る)
- D4 一貫性: 満たす。読解のカードの「答え合わせ」が復習の帯と同じ形になった(r26a/m-reading-chosen)。細かい所: PC の復習の「答え合わせ」だけ、選ぶ前から押せる小さな塗りのボタン(pc-review)。答えたあとの「次の復習へ」は右の列の幅いっぱい(pc-review-answer)で、同じ場所のボタンが前後で大きさを変える
- D5 細部: **一部**。スマホのレッスンの冒頭の縦書きの軸が、`contain: size; overflow: hidden` で本文の高さに切られ、入りきらない字が 2 列目へ折れる(33 / 55。r26a/m-axis-java-hello は「礎」「歩」の 1 字の泣き別れ、r26a/axis-java-if は「制御構 / 文と配列」、r26a/axis-sts-debugger は「リファク / タリング」)。48px の軸に 2 列が詰まり、右の縫い目の破線に字が寄る。軸は署名(C2)の部品で、ほぼ全レッスンの最初の画面に出る。よい所: 行の説明の塊は消えた

### Usability
- U1 次の一手: **一部**。仕事のコード・サンプルを読み終えた所(段にレ点が付き、140px の線はまだその段の中)で閉じると、ホームが「次は サンプル / サンプルの続きから」になる(r26a/m-home-after-sample。公式の m-home-after-reread も同じ)。運針は「7 段のうち 4 段済み」と言いながら、朱の玉結びを済んだ段に置く。復習の日は主ボタンの「今日の復習から」が下の行に落ちる。原因は `nextSectionFor`(スマホ `app.js` 368 行)の `done` に `atwork`・`run` が無く、`!(from in done)` で前回の段をそのまま返すこと。よい所: のぞき見は 4 つの入口とも主役を変えず、「復習へ戻る」で復習の次の問いへ戻れる(r26a/m-back-to-review)。PC は右の列にも「次の復習へ」
- U2 現在地: 満たす。のぞき見の段の帯の右に「復習へ戻る」「読解へ戻る」「用語集へ戻る」(m-reread-landing・r26a/m-peek-reading)。PC は 1/3 の線で、`#quiz-q0` に着くと段の印が「クイズ」(r26a/p-peek-landing)
- U3 読みやすさ: 満たす。細かい所: 解説・STS の手順・演習の問題のコードの式が 3 行以上に割れる(92。式は見送りの判定どおり)、メッセージのうち括弧の中と `;` を含むものは 3〜6 行(D1 の 3)
- U4 操作: 満たす(帯 52px、のぞき見の戻るリンク 44px、読解の答え合わせ 52px。確認の四角 44px は実装から、測っていない)
- U5 状態: 満たす(✓ 正解・× あなたの答え・ほどけた・仮縫いできた・まだ。読解の答え合わせも押せない形が文字で言う)

### Creativity
- C1 コンセプト: 満たす(染まり → 縫う・ほどける → 仮縫い → 本縫いは明日 → 褐の卒業、ホーム下の布の言葉)
- C2 署名: 満たす(縦書きの札・刺し子の枠・運針・仮縫いの糸)。弱い所: レッスンの冒頭の軸の折れ(D5)は、署名そのものの崩れとして目に入る
- C3 既視感: 満たす。細かい所: PC のホームの下の白いカード(見送りのまま)
- C4 動き: 満たす(実装から判断。撮影も撮り足しも reduced motion)
- 9 点の理由: 項目をすべて満たし、間違いを「その日の仮縫い」と「明日の本縫い」に分け、寄り道(のぞき見)しても今日の稽古の糸を切らない形は、ほかの学習サイトに無い

### Content
- N1 正しさ: 満たす。2 択に足した 3 つ目の誤答を十数問読んだ(`-5 % 2` は `-1`、`&&` は `||` より強い、空の Optional を `==` で比べても例外は出ない、`getAnnotation` が `null` を返して NPE、`@NotEmpty` は空白 3 つを通す、PUT は送らなかった項目を補わない)。どれも正しく、もっともらしい
- N2 順序: 満たす
- N3 仕事とのつながり: 満たす
- N4 文: 満たす。問い・選択肢・why・解説・追跡の `true`/`false`/`null` 0、行の説明 5 / 390、ゴール 1 / 273 で、すぐ上のコードと説明の書体がそろった(m-lesson-atwork の「`RestTemplate` や `WebClient`」「`java.time.Clock`」)。細かい所: 本文の地の文の `true`/`false`/`null` 79(r26a/m-msg-arrays のまとめ「既定値(0 / false / null)」)、レッスンの冒頭の説明文の「@Primary / @Qualifier」(m-lesson-top・pc-lesson-top)、サンプルの説明の混在 7(m-sample-closed の「どちらの Notifier が」)、用語集の「String の比較では」「Future で」(m-terms)、解説の「一致した case に」(m-review-answer。同じ文の `switch` はチップ)
- 9 点の理由: 項目をすべて満たし、選んだ誤答ごとの読み違えの 1 文・仕事のコードの行に挟む説明・2 択を無くした手を止めて、と「どう読み違えたか」まで教材にしている所は、ほかの学習サイトに無い

### 学びやすさ
- L1 思い出す練習: 満たす
- L2 間隔と交互: 満たす。2 択が 0 になり、解き直しが消去法で当たる問いが無くなった。細かい所: クイズ・読解で、いちばん長い・短い選択肢がどちらも当てずっぽうより正解になりにくい(19% / 12%、14% / 14%。25 回目のまま)
- L3 手応え: 満たす。why の化けが消え、畳みの中の行も崩れない
- L4 負荷: 満たす。細かい所: 宣言をブロックにして文が 3 つに割れる所(D1 の 2)
- L5 続けやすさ: **一部**。仕事のコード・サンプルを読み終えて閉じた翌朝、ホームの主役が済んだ段を指し、「今日の復習から」が主ボタンから落ちる(U1 と同じ不具合)。よい所: 読み直す・読解・用語集の寄り道で、いま習っている章が主役から外れなくなった
- 8.5 の理由: 一部が 1 つ。寄り道の穴は塞がったが、いちばん普通の読み方(仕事のコードを読み終えて電車を降りる)で次の一手がずれる

## 3. 25 回目からの変化

(依頼文は「24 回目からの変化」だが、24 回目の指摘は 25 回目の審査で判定済みなので、直前の 25 回目の 2 人の指摘と比べた)

### 25 回目の 1 人目(この審査員)の指摘
1. 「読み直す」で古いレッスンへ行くと、ホームの前回の続きが替わる — **直った**。スマホは復習・今日の終わりの行・読解の出どころ・用語集の 4 つとも `data-peek`、着いたレッスンを読み進めても `S.last` は動かない。「復習へ戻る」で次の問いへ。PC も `?from=review` で替わらない(r26a/p-peek-landing)。**ただし**公式の m-home-after-reread は主役が替わって見える(別の不具合。4 の 2)
2. 行の説明のチップが色付けの塊 — **直った**(324 個のうち 0。m-lnnote-chip)
3. 本文の長いメッセージが 3〜5 行 — **大きく良くなった**(本文の段落・箇条書きで 3 行以上 13、全体 153 → 105、語の途中の割れの本物は 3)。**副作用**: 宣言・式約 30 がブロックになり、行頭の「。」「、」11(4 の 3)。STS の手順・コラムは残る(4 の 5)
4. why の「+」の化け — **直った**(why のブロック 0)
5. 仕事のコードの説明とゴールの識別子 — **直った**(151 → 5、107 → 1。どちらも残りは誤検知に近い)
6. PC の「次の復習へ」が画面の外 — **直った**(右の列の選択肢の下。pc-review-answer)
7. PC の段の印が「サンプル」のまま — **直った**(1/3 の線。`#quiz-q0` で「クイズ」)
8. 小さな組版 — 「仮縫い / できた」は語の境目で折れる(**直った**。m-review-retry-end)、「`javac App.java`」は 1 行(**直った**。m-reading)、「`true`。」の空きは詰まった(**直った**。pc-review-answer)、「:」の後ろの行頭の「、」は消えた(**直った**)。読解の長さの偏りは**そのまま**(14% / 14%)
9. 9.5 への余地 — 見送りのまま

### 25 回目の 2 人目の指摘
1. 本文のエラーメッセージ・語の途中の割れ — **大きく良くなった**(上の 3 と同じ)。`/` `?` `&` `::` と定数の `_` の候補が入り、`LOGGING_LEVEL_COM_EXAMPLE_PAYMENT` は `_` の後ろで折れる。演習の問題の `IllegalArgumentExcepti` / `on` は**そのまま**(後付けの境目は `<wbr>` を持つチップを飛ばすため)
2. 読み直すの寄り道で前回の続きが替わる・PC の段の印 — **直った**
3. 読解のページの「答え合わせ」 — **直った**(316×52px、選ぶまで押せない、選ぶと藍。r26a/m-reading-chosen)
4. `true`/`false`/`null` と仕事のコードの説明 — 問い・答え・解説は **直った**(0)。本文の地の文に 79 残る
5. 2 択の手を止めて — **直った**(0 / 173)
6. 小さな食い違い — PC のセッション 12 時間(**直った**。`application.yml`)、PC の 3 回目の解き直しの並び(**直った**。`retryOrder` が使っていない位置へ回す。実装から)、「ほか n 問」の山形(**直った**。CSS から。撮影には写っていない)、確認の四角 44px・「PC」の札 12px(実装の記録から。測っていない)、説明と予想で問いが 2 つのサンプル 47 は**そのまま**
7. 見送り(色止め・染めの順・褐の二重の意味・予想のチップ) — 見送りのまま

### 見送ったものの判定
- 卒業のあとの色止め: 見送ってよい(L 9.5 への余地)
- 予想を 1 つ選んで決めるチップ: 見送ってよい
- 暗い表示の染めの順: 見送ってよい(目の形で区別できる)。Creativity 9.5 の壁なのは 25 回目と同じ
- PC のホームの下の白いカード: 見送ってよい(C3 の細かい所)
- 本文の 40 字を超える式をブロックへ: 見送ってよい。むしろ今回は逆向きの問題(宣言・式までブロックにした。4 の 3)
- 読解の trace の変数名など地の文のコードの語: 次の回でよい(m-reading-dark「run と表示される」、r26a/m-reading-chosen「コメントの中の println」)

### 良くなった
- 間違えた問題を読み直しに行っても、翌朝のホームが今日の稽古(Spring の章と今日の復習)を言い続ける。戻るリンクで復習の次の問いへ 1 回で戻れる
- 仕事のコードの行の説明が、Stream や Generics のレッスンでも文として読める
- 起動に失敗したときのメッセージが、本文でも検索窓に貼れる 1 本のブロックになった
- 手を止めての 2 択が無くなり、仮縫いの札が「思い出せた」印になった
- 読解のカードと復習で、「答え合わせ」が同じ手触りになった

### 悪くなった・新しく出た
- (今回の直しの副作用)サンプルの見出しの「@」が行末に残る(m-sample-closed)。本文のブロックが宣言まで拾い、行頭に「。」が来る(r26a/m-msg-generics)
- (前からあって今回見つけた)スマホのレッスンの冒頭の軸が 2 列目へ折れる(33 / 55)。仕事のコード・サンプルを読み終えて閉じると、ホームの主ボタンが済んだ段を指す

### 撮影について
- m-home-after-reread は m-home-light と主役が違う(「今日の復習から」→「サンプルの続きから」)。台本が読み直すの前に spring-di のサンプルを開いている(m-sample-closed)ためで、のぞき見のせいではない。台本の確かめは `last.id` だけなので、主役が替わっても通る。読み直すの直前にホームを撮り直すか、`S.last` を入れ直してから読み直し、比べるのは見出しの行と主ボタンの文字(`.today-kicker`・`.today-actions .btn`)にする
- 公式の撮影に、軸が折れるレッスン(java-hello の冒頭)と、本文のブロックの手を止めて(java-generics)を足すと、今回の 2 つを毎回見張れる。台本は `r26a/axis26.mjs`・`msg26.mjs`

## 4. SOTD に届かない理由(効き目の大きい順)

Duolingo・Brilliant・Execute Program と並べると、間違えたあとの扱いと寄り道の扱いは、もうこの道場の方が細かい。25 回目に言った穴も全部塞がった。負けるのは、**ほぼ全レッスンの最初の画面で、署名の縦書きの札が 2 列に崩れる**ことと、**今回の組版の直しが、宣言と見出しの記号で新しい切れ目を作った**こと、そして**いちばん普通の読み方で、ホームの次の一手が済んだ段を指す**こと。SOTD の審査員はスマホで最初のレッスン「環境と Hello World」を開き、軸の「礎」の泣き別れを最初の 5 秒で見る。1 を直せば Design 8.5・加重平均 8.65、2 を直せば L 9.0。

### 1. スマホのレッスンの冒頭の縦書きの軸が、本文の高さに切られて 2 列目へ折れる
- 軸・項目: Design D5(C2 の署名の部品)
- 画面: r26a/m-axis-java-hello(「第一部 Java基」の左に「礎」、「第一章 はじめの一」の左に「歩」)、r26a/axis-java-if(「第一部 / Java基礎」と「第二章 制御構 / 文と配列」)、r26a/axis-sts-debugger(「デバッグとリファク / タリング」)、r26a/axis-spring-security(「セキュリティ / とテスト」)、r26a/s375-axis-java-hello。390px で 33 / 55、375px で 27 / 55。折れないのは本文の長い spring-di など(r26a/axis-spring-di)。公式の撮影は spring-di なので写らない
- 問題: `.lesson-axis { contain: size; overflow: hidden; }`(`static/css/app.css` 530 行)で軸の高さを本文に合わせたので、縦書きの字の長さが本文(題 1 行 + 説明 3〜4 行 ≒ 255〜347px)より長いと、行が 48px の軸の中で左の列へ回る。部の札は 14px・字間 .24em、章は 12px・.18em で、2 列が軸の幅いっぱいに詰まり、右の縫い目の破線に字が寄る。PC は「章は現在地の行に出す」で避けたが、スマホには同じ逃げ道が無い
- 直し方:
  - スマホ(1023px 以下)も PC と同じく、軸に入りきらないときは章を軸から外し、現在地の行に出す: 描いたあとに `const ax = $(".lesson-axis"); if (ax.scrollWidth > ax.clientWidth + 1) ax.classList.add("part-only")`、`.lesson-axis.part-only small { display: none }`、`.lesson-hero:has(.part-only) .breadcrumb .visually-hidden` を見える形に(「ホーム / 第二章 制御構文と配列」)
  - それでも部の札が入らないときは字間を .24em → .1em に詰める(`.lesson-axis.tight { letter-spacing: .1em }`)。それでも入らなければ軸の下に余白を足す(`.lesson-hero { min-height: <軸の字の長さ> }`。題の下が少し空くのは、字が泣き別れるより軽い)
  - `review-shots.mjs` で「2 列目へ折れる軸の数」を出す(今 33、目標 0。数え方は `r26a/axis26.mjs` の `cols`)

### 2. 仕事のコード・サンプルを読み終えて閉じると、ホームの主ボタンが済んだ段を指す
- 軸・項目: Usability U1、学びやすさ L5
- 画面: r26a/m-home-after-sample(spring-di のサンプルの終わりまで読み、段にレ点が付いた直後にホームへ。「前回の続き サンプルから / 7 段のうち 4 段済み · 次は サンプル / サンプルの続きから」、今日の復習は下の行へ)、公式の m-home-after-reread(同じ形。m-home-light の「復習 → 演習 / 今日の復習から まず 10 問 約 4 分」から替わっている)
- 問題: スマホの `nextSectionFor`(`app.js` 368〜379 行)の `done` は `read`・`quiz`・`write`・`reading` だけで、前回の段が `atwork`(仕事のコード)か `run`(サンプル)だと `!(from in done)` でそのまま返す。運針と「4 段済み」は `stepStates` で数えるので、同じ画面の中で「済み」と「次はサンプル」が食い違う。design.md の「前回いた段がもう済んでいたら、主ボタンは次のまだの段」にも合わない。仕事のコードは「段の終わりが画面に見えて 3 秒」で済みになり、その時点の 140px の線はまだ仕事のコードの中なので、通勤の読み方ではこれが普通に起きる。復習の日は「今日の復習から」も主ボタンから落ちる。PC の差し替え(`static/js/app.js` 486 行の `doneBy`)も `atwork` を知らない
- 直し方:
  - `nextSectionFor` を `stepStates` から組む: `const st = stepStates(l); const i = st.findIndex((s) => s.key === from); if (i < 0 || !st[i].done) return from; return st.slice(i + 1).find((s) => !s.done)?.key || from;`(運針・「次は」・主ボタンが同じ表を見る)
  - PC は `doneBy` に `atwork: (pcStore(SECTIONS_KEY)[last.id] || []).includes("atwork")` を足す
  - `review-shots.mjs` の読み直すの確かめを、主ボタンの文字の比べにする(「撮影について」)。「サンプルを読み終えて閉じた → ホームの主ボタンが今日の復習から / 演習へ進む」も 1 枚足す(台本は `r26a/peek26.mjs` の B)

### 3. 本文のメッセージのブロックが Java の宣言・式まで拾い、文が 3 つに割れて行頭に「。」が来る
- 軸・項目: Design D1、学びやすさ L4
- 画面: r26a/m-msg-generics(手を止めての問いが「`interface OrderRepository extends JpaRepository<Order, Long>`」のブロックで始まり、次の行が「。親の `Optional<T> findById(ID id)` は…」)、r26a/m-msg-sealed(「`sealed interface Payment permits Card, Cash`」がブロック、続きが「は「Payment を実装できるのは…」)、r26a/m-msg-approve(よくあるミスの「`BusinessRuleException extends Exception`」)、r26a/m-msg-arrays(「`(double) total / a.length`」)。一覧は `r26a/msg26-out.txt`
- 問題: 本文に広げた判定 `msgLike`(`app.js` 2288 行)は「24 字以上・空白あり・`; { } = && || ->` が無い・呼び出しの括弧があれば英文らしいとき」で、宣言・総称型・キャスト・掛け算・引数の並びは `; { } =` を持たないので、英文でなくても通る。design.md の「コードの式・宣言・呼び出しはチップのまま(段落にすると文が 3 つに割れた)」の逆になった。ブロックの直後の「。」「、」は語をつなぐ印の外なので、次の行の頭に落ちる(11 か所)
- 直し方:
  - 本文の道(2349 行)は「英文らしい」ことを必須に: `if (!msgLike(t) || !(proseLike(t) || /^[\w.]+(Exception|Error)\b|^(warning|error|Caused by)\b/i.test(t)))` でチップのまま。加えて `/\b(extends|implements|interface|instanceof|permits|sealed|record|enum|new)\b|<[A-Z?]|^\(|^@\w+ \w+ \w+$/` に当たるものはチップ(約 30 → 0。`Content-Type: …`・`POST /…` のような要求行と設定の 1 行もチップのままでよい)
  - ブロックの直後が「。」「、」なら、ブロックにしない(チップのまま。11 → 0)。どうしてもブロックにするなら、直後の「。」を消して文をそこで終える
  - `review-shots.mjs` に「本文の code-msg のうち `extends|implements|<` を含むもの」と「直後が約物のもの」を数えて出す(台本は `r26a/msg26.mjs`)

### 4. サンプルの見出しで「@」だけが行末に残る
- 軸・項目: Design D1
- 画面: m-sample-closed・m-sample-result(公式。「`@Configuration + @Bean と、@` / `Primary / @Qualifier`」)、capstone-release のサンプルの見出し(「API のエラーを @ / RestControllerAdvice で 1 か所にまとめる」)。ほかに spring-mockmvc「MockMvc / によるテスト」(助詞 3 字が行頭)
- 問題: 見出しに足した `phraseWrap`(`app.js` 457 行)は、語でない区切り(`@`・` + `・` / `)を前の語の後ろに付ける。`@` は名前の頭なので、前に付くと「と、@」と「Primary」の間が折れ目になる。助詞は 1〜2 字だけを前に付けるので「による」が行頭に残る
- 直し方: `@` は開き括弧と同じく次の語へ: ループの頭で `if (segment === "@") { words.push(segment); continue; }`、つなぐ条件を `/[(「『@]$/.test(words.at(-1))` に。助詞は `/^[ぁ-ゟ]{1,3}$/` まで前に付ける(「MockMvcによる / テスト」)。`kinsoku26.mjs` の `h3at`・`h3head` を記録に出す(今 2・1、目標 0)

### 5. STS の手順・コラム・演習の問題では、メッセージがチップのまま 3〜6 行に割れる
- 軸・項目: Design D1、Usability U3
- 画面: r26a/m-sts-resolved(spring-rest の STS の手順「`Resolved [org.springframework.web.HttpMediaType` / `NotSupportedException: Content-Type` / … / `supported]`」6 行)、r26a/m-column-literal(java-variables のコラム「`The literal 09012345678L of type long is out of range`」3 行)。ほかに capstone-apply の STS「`No property 'applicantld' found for type …`」4 行。演習の問題の `IllegalArgumentExcepti` / `on`(spring-layers)
- 問題: ブロックにしないのは「`;` を含む」「括弧の中」のとき。メッセージそのものが `;charset=UTF-8` を含む・括弧の中で引く形だと、検索窓に貼る文が 6 本の帯になる。後付けの小文字→大文字の候補(2458 行)は、すでに `<wbr>` を持つチップ(`(` の後ろの候補)を飛ばすので、`IllegalArgumentException("…")` は語の途中で割れたまま
- 直し方:
  - 英文らしい(`proseLike`)メッセージは、`;` を含んでもブロックに(`;` で除くのは英文らしくないときだけ)。括弧の中のメッセージは、40 字を超えるときだけ括弧を外して教材で「〜という 1 行」に書き直す(STS 3 か所・コラム 1 か所)
  - 後付けの候補は「`<wbr>` を持つ」で飛ばさず、「割れ目の前後が英字のときだけ」に: `if (/\s/.test(t) || c.getClientRects().length < 2) return;` のあと、割れ目の 2 字を見て判定

### 6. PC の復習の「答え合わせ」だけ、選ぶ前から押せる小さなボタン
- 軸・項目: Design D4、Usability U1
- 画面: pc-review(右の列の選択肢の下に、選ぶ前から塗りの「答え合わせ」。幅は字なり)と pc-review-answer(答えたあとの「次の復習へ」は列の幅いっぱい)。スマホの復習と読解のカードは、選ぶまで押せない「選んでから答え合わせ」→ 藍、幅いっぱい 52px
- 問題: 同じ場所のボタンが、答える前と後で大きさを変え、スマホ・読解と状態の言い方が違う。選ばずに押すとブラウザの必須の吹き出しが出る
- 直し方: `review.html` 63 行のボタンに `disabled` と「選んでから答え合わせ」を入れ、`change` で外す(スマホの `armSubmit` と同じ言葉)。幅は 2 列のとき右の列いっぱい・高さ 48px(「次の復習へ」と同じ箱)

### 7. 小さな食い違い(1 行ずつ)
- 本文の地の文の `true`・`false`・`null` 79(r26a/m-msg-arrays のまとめ「既定値(0 / false / null)」)→ 問い・答えと同じく `proseRules` を本文にも(見出しは除いてよい)
- レッスンの冒頭の説明文の識別子が地の文(m-lesson-top・pc-lesson-top「@Primary / @Qualifier」)→ ゴールと同じく `` `…` `` をコードの書体で出す
- 解説の「一致した case に ジャンプ して」の `case` が地の文で、同じ文の `switch` はチップ(m-review-answer)→ 教材を `` `case` `` に
- 用語集の「String の比較では」「Future で結果を受け取る」(m-terms)→ 用語集の検査にクラス名(CamelCase)も足す
- サンプルの説明でチップと地の文が混ざる 7 本(m-sample-closed「どちらの Notifier が」)、説明と予想で問いが 2 つ 47 本 → 25 回目の直し方のまま
- 読解の選択肢の長さの偏り(いちばん長い・短いとも 14%)→ `proseRules` の下限を 18% に(25 回目のまま)
- 読解・用語集からののぞき見は、レッスンの頭に着く(r26a/m-peek-reading・m-peek-terms)→ 読解は出どころの読解の段(`data-resume="reading"`)、用語集は本文の中でその語が最初に出る段落へ

### 8. 9.5 への余地(見送りのまま)
- 卒業した問の色止め(30 日後・90 日後に 1 回ずつ、1 日 2 問まで)
- 暗い表示の染めの順(縹 → 藍 → 褐で地との差が増える並び)。断り書きを消せる
- PC のホームの下の白いカードを布の言葉の行に
- 予想を 1 つ選んで決めるチップ

## 5. 見ていないこと
- 実機(iPhone の Safari・Android の Chrome)とホーム画面に追加した PWA。撮影・撮り足しとも 390×844・375×667・1440×900 のヘッドレス Edge(Windows)。縦書きの軸の折れは Windows の書体(見出しは同梱の Zen Kaku Gothic New なので、端末によらず同じ長さのはず)で数えた。ヒラギノでの本文の組みは見ていない
- 動きのすべて(縫う・ほどける・仮縫いの糸・褐の染み込み・帯の送り)。撮り足しは `prefers-reduced-motion: reduce`
- 読み上げの実際(VoiceOver・TalkBack・NVDA)。のぞき見の着地のフォーカスは `document.activeElement` を見ただけ。軸の章を外したときの読み上げ用の現在地は未確認
- スマホの復習を 20 問通す流れと、解き直しの 2 回目から(25 回目に通し、今回の直しはそこに触れていないと判断して省いた。公式の m-review-retry-end の数は合っている)。PC の今日の復習を答える流れ、PC の 10 問の区切り・暗い表示
- 確認の四角 44px・「PC」の札 12px・「ほか n 問」の山形は、CSS と記録で確かめただけで、撮っていない
- PC の解き直しの 3 回目の並び(`retryOrder` を読んだだけ)、セッション 12 時間が実際に効くか
- オフラインの動作(`scripts/offline-check.mjs`)と `./mvnw test`(読むだけの審査のため)
- 教材の実行(サンプルの出力が控えと合うか)。事実は 2 択に足した誤答を中心に十数問読み、書体・偏り・ブロックの中身は機械で数えた(宣言・式の分けは正規表現の目安で、数個の誤差がある)
