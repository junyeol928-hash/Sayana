# 教材フォーマット

教材は `content/` 配下に **部 → 章 → レッスン** のディレクトリで置く。並び順はディレクトリ名の数字プレフィックスで決まる。
アプリは起動時にこれを読み込むだけで、実行時に教材を生成することはない。
教材を変えたら、スマホ版(`m/`)も `./mvnw -q compile exec:java` で作り直してコミットする(古いままだと `MobileSiteTest` が失敗する)。

```
content/
  01-java-basics/                 ← 部
    part.yml                      id / title / description
    01-first-steps/               ← 章
      chapter.yml                 id / title
      01-java-hello/              ← レッスン
        lesson.yml                メタデータ・サンプル・クイズ・コラム・STS手順・チェックリスト
        lesson.md                 解説本文(10〜15分で読める量)
        exercises/
          ex1/
            exercise.yml          title / className / difficulty / problem
            starter.java          雛形(コンパイルは通るが、テストは落ちる状態)
            solution.java         模範解答(テストに全部合格する)
            test.java             JUnit 5 の採点テスト(クラス名は <className>Test)
```

## lesson.yml

```yaml
id: java-hello            # 全体で一意なスラッグ。URLや進捗の保存キーになるので後から変えない
title: 環境とHello World
status: ready             # ready = 公開 / planned = 章立てのみ(本文なし)
minutes: 15               # 準備中のレッスンの読む目安(分)。公開(ready)のレッスンは lesson.md から数える(文章 500 字・コード 12 行で 1 分)
summary: 一行の説明
goals:                    # このレッスンのゴール(3〜5個)
  - ...
samples:                  # 画面上で実行できるサンプル(JShell で実行)
  - title: はじめての出力
    description: 補足(Markdown)。実行結果を開く前に何を予想するかを、「?」「だろうか」「でしょうか」で終わる 1 文で書く(40 字くらいまで)。スマホ版は最初のその文を「予想: 〜」としてコードの下に出す。答えはそのサンプルの実行結果から読み取れること
    code: |
      System.out.println("Hello");
quiz:                     # 確認クイズ(5問・選択式)
  - question: |           # Markdown 可(コードブロックも可)
      次のうち…
    choices: ["A", "B", "C", "D"]
    answer: 1             # 0始まりの正解インデックス
    why:                  # 選んだ誤答に向けた 1 文(choices と同じ数・同じ順。正解は "")。checks.yml の why と同じ書き方
      - "〇〇と読んだ答え。正しくは…"
      - ""
      - "…"
      - "…"
    explanation: なぜそれが正解か / 他の選択肢のどこが違うか
columns:                  # コラム
  - type: trivia          # trivia = 豆知識 / genba = 現場あるある / mistake = よくあるミス
    title: ...
    body: |               # Markdown
      ...
sts:                      # 「STSでやってみよう」(STS関連レッスンのみ。省略可)
  intro: 何をやるか(Markdown)
  steps:
    - title: 手順の見出し
      detail: メニュー名と操作順を具体的に(Markdown)
checklist:                # 画面上で実行できない作業の「やった」記録(省略可)
  - key: jdk-installed    # レッスン内で一意
    text: java -version で 21 が表示された
```

### サンプルコード(JShell)のルール
- JShell に流し込める形で書く(文・式・メソッド宣言・クラス宣言の並び)。
- `public static void main(String[] args)` を持つクラス**だけ**を宣言したコードは、実行時に `ClassName.main(new String[0])` が自動で呼ばれる。
- 標準入力は使わない。乱数・現在時刻などで結果が毎回変わるものは避ける(使うなら「毎回変わる」と説明する)。
- 1秒以内に終わること(Spring を使うサンプルは数秒かかってよい)。
- サンプルに `libraries: [spring]` を書くと、Spring Framework 入りの JShell で実行される(第3部用)。
  DI は `new AnnotationConfigApplicationContext(A.class, B.class)` のようにクラスを明示して登録する。

## 演習

- クラスはパッケージなし(default package)。`starter.java` と `solution.java` には `public class <className>` を1つだけ書く。
- `test.java` は `class <className>Test`。JUnit 5(Jupiter)の `org.junit.jupiter.api.*` のみ使う。
  `@DisplayName` と assert のメッセージは日本語で、**落ちたときに学習者が何を直せばよいか分かる文**にする。
- 標準出力を検証したいときは `System.setOut` で差し替えて、`@AfterEach` で元に戻す。
- `starter.java` はコンパイルが通り、かつ少なくとも1つのテストが失敗すること(自動テストで確認している)。
- `solution.java` は全テストに合格すること(自動テストで確認している)。
- 雛形(`starter.java`)とサンプルのコメントは、PC の編集欄で横に送らずに読める幅にする(`CurriculumTest#commentsFitEditor`)。
  - コメントだけの行は 72 列まで(和字は 2 列で数える)。長い `// TODO:` は意味の切れ目で 2 行に折り、続きは `//       ` と TODO の後ろにそろえる。
  - 行末のコメントがある行は 80 列まで。超えるなら、コメントを上の行へ移す(雛形の期待出力は `// 出力: 〜`)。
- まだ習っていない文法が必要なら、雛形側で用意しておき「ここは後のレッスンで説明する」と problem に書く。

### exercise.yml

```yaml
title: 自己紹介メッセージを作る
className: Greeting
difficulty: 1            # 1〜3
libraries: [spring]      # 省略可。spring を指定すると Spring 入りで採点する(下記)
problem: |               # Markdown。何を作るか・入出力の例・制約
  ...
```

### Spring を使う演習(`libraries: [spring]`)

採点用JVMのクラスパスに、Spring Framework(core / beans / context / aop / tx / web / webmvc)、
spring-test(MockMvc)、Jackson、json-path、Bean Validation(Hibernate Validator)が入る。タイムアウトは最低20秒。

- Spring Boot の自動設定(`@SpringBootTest`)は使わない(重いので)。DI は `new AnnotationConfigApplicationContext(A.class, B.class)` のように**クラスを明示して登録**する
  (default package なのでコンポーネントスキャンは使わない)。
- Controller は `MockMvcBuilders.standaloneSetup(new XxxController(...))` でテストする。日本語を返すなら次を付ける
  (付けないと ISO-8859-1 になって文字化けする。Spring Boot 本体では自動で UTF-8 になる):

  ```java
  .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
  .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
  ```
- `@Valid` を効かせるには `LocalValidatorFactoryBean` を作って `afterPropertiesSet()` し、`.setValidator(...)` で渡す。
- JPA・DB は動かさない(採点JVMにDBはない)。Repository はインターフェース + テスト用のメモリ実装で表す。
- `libraries: [spring, jpa]` で JPA のアノテーション(`jakarta.persistence.*`)と Spring Data(`JpaRepository`、
  メソッド名を解析する `org.springframework.data.repository.query.parser.PartTree`)が使える。テストはリフレクションや PartTree で確かめる。
- `libraries: [spring, thymeleaf]` で Thymeleaf が使える。エンジンは `org.thymeleaf.spring6.SpringTemplateEngine`
  (素の `TemplateEngine` は OGNL が無いので動かない)+ `StringTemplateResolver` で文字列テンプレートを描画して確かめる。

## 解説本文(lesson.md)の拡張記法

通常の Markdown(表・コードブロック・```mermaid の図)に加えて、次のブロックが使える。

```
::: sensei
モカ先輩のセリフ(Markdown)
:::

::: haru
新人ハルのセリフ
:::

::: point 見出し(省略可)
要点のまとめ
:::

::: warn 見出し
よくあるミス・注意
:::

::: genba 見出し
現場あるある
:::

::: trivia 見出し
豆知識
:::
```

登場人物:
- **モカ先輩** ☕ … 現場15年のJavaエンジニア。コーヒー好き。軽口を叩くが、説明は正確で硬派。
- **ハル** 🐣 … 新人エンジニア。素朴な疑問をぶつけ、よく間違える(読者の代弁者)。

### 本文の地の文の書き方

- 本文(図とコードブロックを除く段落・箇条書き・表・見出し)・サンプルの説明・仕事のコードの説明では、レッスンのコード(本文の Java のコードブロック・サンプル・仕事のコード)に出る名前をコードにする: 大文字で始まり小文字を含む名前(`List`・`Notifier`・`RuntimeException`)・ドットでつないだ名前・途中に大文字のある名前(`findById`)・定数(`MAX_SIZE`)と、注釈(`@Service`)・完全修飾名(`java.time.Clock`)。すぐ上のコードと同じ語が、本文の書体で出ないように
- 見出し(`##`・`###`)では、コードで呼んでいるメソッド名もコードに(「文字列の分岐:`equals` と `null` に注意」)
- 層や役割の一般名(Service・Controller・Repository・Bean・Entity・Builder)と、英語の名前の一部(前か後ろが英単語: Bean Validation・Hello World・Stream API)、製品名・画面の名前は地の文のまま
- 枠の見出し(`::: warn 見出し` の見出し)は Markdown にならない素の文字なので、コードの名前を置かない(要るなら枠の中の文へ)
- 絵文字と記号(U+2600〜27BF・U+1F300〜1FAFF。☕・✅・✔・★ など)は、本文・説明文・ゴール・コラム・STS・演習・サンプル・仕事のコード・クイズ・読解・手を止めて・用語集の地の文に置かない(design.md の「珈琲の記号と絵文字を外す」)。コードブロックとサンプルのコード・出力の中はよい。出力の記号を地の文で指すときはコードに(`` `✔` ``)、表の印は ◯ と ×
- 和字にはさまれた太字は、前後に空白を置かない(`に**ジャンプ**して`。空白があると画面で半角の空きになる)。中身の端か外の隣が英数字・約物・コードのときは空白のままでよい(`**Bean** を`)。`**` の内側の端が約物やコードで、外側に空白が無いと太字にならない(CommonMark の決まり)ので、そのときは空白を残すか言い方を変える
- 本文・コラム・STS・演習の問題文・解説で、24 字以上の英語の文らしいメッセージ(英単語が 3 つ続く文・エラーの 1 行 `…Exception:`・`Caused by`)を引くときは、直後を「。」か 5 字以下の述語(「です。」「を読む。」)で終わらせない。「次のコンパイルエラーになります: `…`」の前置きの形にする(画面ではメッセージが 1 行のブロックになり、下に述語だけの行が残るか、チップのまま 3 行に割れる)
- これらは `CurriculumTest#proseRules` が `./mvnw test` で落とす

## 読解・仕事のコード・用語集(省略可)

ローカル版 Java道場から移した教材。レッスンのディレクトリに置く(無ければその欄は出ない)。

### reading.yml(コード読解問題のリスト)

```yaml
- title: 参照のコピー
  level: 2                 # 1=やさしい 2=ふつう 3=むずかしい
  code: |                  # 実行せずに読むコード
    int[] a = {1, 2, 3};
    int[] b = a;
    b[0] = 99;
    System.out.println(a[0]);
  question: 出力は?         # 省略すると「出力は?」
  choices: ["1", "99", "コンパイルエラー", "実行時エラー"]
  answer: 1                # 0 始まり
  why:                     # 選んだ誤答に向けた 1 文(choices と同じ数・同じ順。正解は "")
    - "b = a で配列が複製されると読んだ答え。同じ配列を指す"
    - ""
    - "…"
    - "…"
  explanation: |           # Markdown(生 HTML はエスケープされる)
    配列は参照型なので…
  trace:                   # 1 行ずつの追跡(省略可)
    - "1行目: 配列 {1,2,3} を作り a が指す"
```

### checks.yml(手を止めて。本文の途中で思い出す 1 問)

読むだけで 15 分が終わらないように、本文の節(`##`)の終わり(`after`)か、本文が答えを見せる直前(`before`)に、押して答える 1 問を置く。答えるまで理由は隠れ、
スマホ版では答えた問いが復習(1・3・7・14 日)にも入る。1 レッスンに 2〜3 問。

```yaml
- after: switch 式(Java 14+)――現場の新標準   # lesson.md の ## 見出しの文字そのまま。その節の終わり(次の ## の前)に置く
  question: |               # Markdown。1〜2 行。コードを読ませるなら 3〜6 行の短いコードブロック
    `n` が `1` のとき、出力は?
  choices: ["XY", "X", "XYZ"]  # 3 個以上(2 択は当てずっぽうでも半分当たり、間違えた直後の解き直しが消去法で必ず当たる)。短く
  answer: 0                 # 0 始まり。画面では問いごとに決まった並びに入れ替わる(書いた順のままだと正解の位置が偏る)
  why:                      # choices と同じ数・同じ順。正解の所は ""。誤答を選んだ人に、どう読み違えたかを 1 文(80 字まで)
    - ""
    - "case 1 で止まると読んだ答え。break が無いので下の case へ落ちる"
    - "default まで落ちると読んだ答え。case 3 の break で抜ける"
  explanation: |            # 答えたあとに出す正解の理由。1〜2 行。誤答の話は why に書き、ここでは繰り返さない
    …
- before: インデントだけ見ると  # after の代わりに。本文の塊(段落・コード・箇条書き。見出しは不可。吹き出しは名札の文字から始まる)の書き出しの文字(` と ** は外す)。その塊の直前に置く
  hides: 3                  # before のとき、答えるまで畳む塊の数(既定 1、1〜5)。答えが入っている塊までを数える。次の ## は越えない
  question: |               # 本文が「実行すると……」と問いかけて、次の塊で答えを見せる所で、答えの前に予想させる
    …
```

- 直前の節を読んだ人が、本文を見返さずに答えられる問いにする(先の節の知識を使わない)
- クイズ(lesson.yml)・読解(reading.yml)と同じ問いにしない。本文の「答えが直後に書いてある問いかけ」を、答えを隠した問いにするのはよい(`before` で答えの前に置く)
- 解説と why で選択肢を位置(「2 番目」)で指さない(並びが入れ替わる)。中身で指す
- 画面では、誤答を選ぶと「× 不正解」のすぐ下に、その誤答の why が出て、続いて explanation が出る。復習でも同じ(クイズ・読解の why も同じ)
- why と explanation は役目を分け、同じ話を 2 回しない: why は「どう読み違えたか」(「〜と読んだ答え。実際は ××」の短い形)、explanation は正解の筋(規則と、なぜそうなるか)。explanation に誤答ごとの「〇〇を選んだ人は〜」は書かない。目安は、コードの外の地の文で 12 字以上同じ並びを持たないこと。why の 1 文目は、復習の終わりの「間違えた問題」の行にもそのまま出る
- 選択肢・why・explanation・question・読解の trace の地の文に、ASCII の `"…"` で文字列を書かない(本文の書体で両方とも閉じ括弧の形に出て、行の途中で折れる)。画面に出る出力は引用符を外してコードに(`while:10`)、ソース上の文字列リテラルは引用符ごとコードに(`"abc"`)、日本語の言葉を括るだけなら「」
- why と選択肢の中のコードの字句(呼び出し `println()`、演算子 `++`・`i += 3`・`==`・`->`、例外名 `NullPointerException`、式)は `` ` `` で包む。同じ問いの選択肢は書体をそろえる(1 つだけコード書体だと手がかりになる)。why の 1 文目は復習の終わりの「間違えた問題」の行にもそのまま出る
- 比較と代入(`x = 10`・`10 < 5`・`T = Integer`)と計算の式は、why・選択肢・解説・読解の trace・用語集の説明でもコードに(空白を入れて `x = 10`)。trace の呼び出し・例外名もコードに
- 同じ問いの問い・選択肢・why・解説・読解の trace のどこかでコード書式にした語(`total`・`null`・`static`・`switch`)は、why でも解説でもコード書式に(答えた直後の箱で、同じ語がチップと地の文の 2 通りに出ないように。終わりの画面では why の 1 文目が主役)。サンプルの説明でも同じ。用語集では完全修飾名(`java.util.ArrayList`)と `true`・`false`・`null` もコードに
- 解説の長いメッセージ(24 字以上で空白を含むコード)は画面で独立した 1 行のブロックになるので、括弧の中に置かない。「STS では次のメッセージが出る: `…`」のように前置きする(本文の段落・箇条書きでも、括弧の外のメッセージは同じく 1 行のブロックになる)
- `true`・`false`・`null` と Java の予約語・基本型(`case`・`switch`・`default`・`static`・`final`・`new`・`return`・`int`・`double`、`if`・`else`・`for`・`while`・`do`・`try`・`catch`・`finally`・`throw`・`throws`、`class`・`interface`・`enum`・`record`・`import`・`package`・`abstract`・`public`・`private`・`protected`・`instanceof` など)は、問い・選択肢・why・解説・読解の trace の地の文でもコードに。用語として使う所も(「`switch` 文」「`static` メソッド」「`int` 型」「`catch` ブロック」)。英語の句の中(前後とも英単語。STS のメニュー「Check out new branch」)は地の文のまま。本文(lesson.md)の `true`・`false`・`null` も
- 読解の trace では、そのコードに出る名前(変数・メソッド・出力される文字)をコードに(「`region` は」「`two` を表示」)
- 問いのコード(読解の code と、問いの中の Java のブロック)に出る 1〜2 字の名前(`n`・`i`・`a`・`id`)は、問い・選択肢・why・解説・trace の地の文でもコードに(「`n` が増え続けて」)。英語の短い語(at・by・of・to・in・is・or・on・an・as)と英語の句の中は地の文のまま
- lesson.yml の summary(冒頭の説明文)も goals と同じく、コードの語は `` `…` `` で囲む(画面ではコードの書体になる)
- 本文で長い英語のメッセージを引くときは、括弧の中に入れず「STS では次のメッセージが出る: `…`」のように前置きし、直後に「。」「、」や 5 字以下の述語(「です。」)を置かない(画面ではブロックになり、次の行が句読点や述語だけになる。解説・コラム・STS・演習の問題文でも同じ)
- why と選択肢は 1 文。行頭に `+ ` `- ` `# ` を置かない(Markdown の箇条書き・見出しに化けて記号が消える)。記号はコードに(`` `+` の記号は… ``)
- 正解がいちばん長い選択肢になる問は、レッスンのクイズ 5 問のうち 2 問まで(長さで当てられないように)。読解もレッスンごとに 2 問まで。読解で正解がただ 1 つのいちばん長い選択肢になる問と、ただ 1 つのいちばん短い選択肢になる問は、どちらも全体で 20〜32%(当てずっぽうは 4 択で 25%。「いちばん長いのを選ぶ」「いちばん短いのを捨てる」で当たらず、長い側・短い側を避けることも手がかりにならないように)。手を止めて(checks.yml)で正解がただ 1 つのいちばん短い選択肢になる問は、全体で 38% まで(3 択の当てずっぽうは 33%)。正解の補足は解説へ移すか、誤答に読み違えの中身を足して伸ばす。短すぎる正解は、解説と同じ中身で言い足す(「できない(haru・mocha だけになる)」)。choices の並びと answer の番号は変えない
- サンプルの説明に「予想してから見よう」のような促すだけの文は書かない(スマホでは閉じた欄の見出し「予想したら、実行結果を見る」が言う)。問いは `?` で終わる文にし、説明の最後の文に置く(最後の問いが「予想: 〜」に出て、説明からは外れる。後ろに文が続くと同じ問いが 2 回出る)
- これらと why の重なり・予想の問いは `CurriculumTest#proseRules` が `./mvnw test` で落とす
- before の問いは、答えるまで後ろの hides 個の塊を畳む(「答えると続きが開きます · 先に読む」)。問いの中では「上のコード」「本文の〇〇」と指さず、要るコードは問いに書く(復習では本文が無いので)
- `after` に合う見出しが無いとき、`before` で始まる塊が無いか 2 つ以上あるとき、why の数が choices と違うときは、起動時(と `./mvnw test`)にエラーになる。一度公開した問いの**順と数は変えない**(復習の記録のキーが「レッスン id!番号」なので)。足すときは末尾に。選択肢を足すときも末尾に(answer の番号を変えない)

### atwork.yml(仕事のコードではこう出てくる)

```yaml
- title: クラスとインスタンス
  intro: 業務の Spring Boot アプリでは…    # Markdown
  code: |
    @Service
    public class UserService { … }
  notes:                   # 行番号(1 始まり) → その行の説明。画面ではコードの左に番号の印が付く
    1: "@Service で Bean として登録される"
  points:                  # 読むときの着眼点
    - コンストラクタの引数が依存の一覧
```

- intro・notes・points でも、コードに出る名前はコードに(`` `OrderRepository` ``・`` `findById` ``・`` `@Service` ``・`` `java.time.Clock` ``・`` `MAX_SIZE` ``、`final`・`static`・`null`)。すぐ上のコードと同じ語が、説明では本文の書体で出ないように。大文字 1 つの名前でも、レッスンのコードに出るもの(`List`・`Optional`)はコードに。役割の一般名(Service・Controller・Repository・Bean・Entity・Builder)と製品名は地の文のまま
- lesson.yml の goals でも、注釈・完全修飾名・`final` などと、レッスンのコード(仕事のコード・サンプル)に出る名前は `` `…` `` で囲む(画面ではコードの書体になる)
- これらは `CurriculumTest#proseRules` が落とす

### content/glossary.yml(用語集)

```yaml
- term: 変数
  read: へんすう
  en: variable
  def: 値を入れておく名前付きの箱。   # Markdown 1 行
  lesson: java-variables              # その用語を扱うレッスン(存在しない ID だと起動時にエラー)
```

- `lesson` を付けた語は、候補のどれかが、そのレッスンの本文(段落・箇条書き・見出し・表・図の説明・太字。手を止めての中は除く)・仕事のコード(導入・行の説明・要点)・サンプル(題・説明)にそのまま出ること。用語集の「〇〇 で」はそこへ飛んで印を付ける(無いとレッスンの頭に着いて「同じ言い方では出てきません」と出る)。候補は、括弧の外を「/」で分けた語(「フロントエンド / バックエンド」の両方)と、半角の括弧の中を「、」「/」で分けた言い換え(`関係演算子(比較演算子)`・`標準クラス(API、標準ライブラリ)`)。英字の大小と空白の数は区別しない
- 着かないときは、(1) 本文で概念を説明している所の言い方を用語集の語にそろえる、(2) その語を説明しているレッスンへ `lesson` を付け替える、(3) 本文での言い方を括弧に足す、のどれか(意味は変えない)。どのレッスンの本文・仕事のコード・サンプルでも扱っていない語と、同じ語がほかのレッスンにもあって着地はそちらで足りる語は、`lesson` を外す(用語集には残る)
- 説明(def)にも、絵文字と、和字にはさまれた太字の前後の空白を置かない
- 着地は `CurriculumTest#glossaryTermsLand`、説明の書き方は `CurriculumTest#proseRules` が落とす

## 図(SVG)

`lesson.md` に SVG の図をそのまま書ける。前後に空行を入れ、SVG は改行なしの 1 行にする。

```
<figure class="figure"><svg viewBox="0 0 600 220" …>…</svg><figcaption>説明</figcaption></figure>
```

- `viewBox` を必ず付け、`width` / `height` 属性は付けない
- 色は CSS 変数だけ: `var(--ink)` `var(--ink-muted)` `var(--line)` `var(--accent)` `var(--accent-soft)` `var(--surface-2)`(暗い表示でも読めるように)
- `<marker id>` などの id は、他のレッスンと重ならない接頭辞を付ける
