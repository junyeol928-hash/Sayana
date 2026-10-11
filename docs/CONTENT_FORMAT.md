# 教材フォーマット

教材は `content/` 配下に **部 → 章 → レッスン** のディレクトリで置く。並び順はディレクトリ名の数字プレフィックスで決まる。
アプリは起動時にこれを読み込むだけで、実行時に教材を生成することはない。

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
minutes: 15               # 読む目安(分)
summary: 一行の説明
goals:                    # このレッスンのゴール(3〜5個)
  - ...
samples:                  # 画面上で実行できるサンプル(JShell で実行)
  - title: はじめての出力
    description: 補足(Markdown, 省略可)
    code: |
      System.out.println("Hello");
quiz:                     # 確認クイズ(5問・選択式)
  - question: |           # Markdown 可(コードブロックも可)
      次のうち…
    choices: ["A", "B", "C", "D"]
    answer: 1             # 0始まりの正解インデックス
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
- 1秒以内に終わること。

## 演習

- クラスはパッケージなし(default package)。`starter.java` と `solution.java` には `public class <className>` を1つだけ書く。
- `test.java` は `class <className>Test`。JUnit 5(Jupiter)の `org.junit.jupiter.api.*` のみ使う。
  `@DisplayName` と assert のメッセージは日本語で、**落ちたときに学習者が何を直せばよいか分かる文**にする。
- 標準出力を検証したいときは `System.setOut` で差し替えて、`@AfterEach` で元に戻す。
- `starter.java` はコンパイルが通り、かつ少なくとも1つのテストが失敗すること(自動テストで確認している)。
- `solution.java` は全テストに合格すること(自動テストで確認している)。
- まだ習っていない文法が必要なら、雛形側で用意しておき「ここは後のレッスンで説明する」と problem に書く。

### exercise.yml

```yaml
title: 自己紹介メッセージを作る
className: Greeting
difficulty: 1            # 1〜3
problem: |               # Markdown。何を作るか・入出力の例・制約
  ...
```

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
