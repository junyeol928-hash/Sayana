::: haru
先輩、`User u = new User();` の `u` って、結局どこにあるんですか? `u` の中に User がまるごと入ってる感じ?
:::

::: sensei
入ってない。`u` が持ってるのは「User がどこにあるか」を指す矢印だけ。今日はその矢印と箱が、JVM の中のどこに置かれてるかを1枚の絵にしよう。参照、値渡し、null、static、GC……これまでバラバラに聞いた話が全部つながるよ。
:::

これまでのレッスンで、「変数には参照が入る」「Java は値渡し」「再帰は終了条件を忘れると `StackOverflowError`」と、メモリの話を少しずつ見てきました。今回はそれらを、**JVM が実行中に使うメモリの地図** の上に並べ直します。

## JVM のメモリをざっくり地図にする

`java` コマンドでプログラムを動かすと、JVM は OS からメモリを借りて、用途ごとに区切って使います。まず覚えるのは次の3つです。

| 領域 | 置かれるもの | いつ消えるか |
|---|---|---|
| **スタック** | メソッド呼び出し1回ごとの **フレーム**(引数・ローカル変数) | そのメソッドが `return` したとき |
| **ヒープ** | `new` で作ったオブジェクトや配列の **実体** | どこからも辿れなくなり、GC が回収したとき |
| **クラスの情報** | クラスの定義、メソッドのコード、static フィールド | クラスが使われている間ずっと |

スタックは **スレッドごとに1本ずつ**、ヒープは **全スレッドで1つを共有** します。スレッドは次のレッスン「並行処理」の主役です。「ローカル変数は自分専用、ヒープの実体はみんなのもの」という違いが、そこで効いてきます。

## スタック:メソッド1回分の「フレーム」

メソッドを呼び出すと、JVM はその呼び出し専用の作業場、**フレーム** を1つ作ってスタックに積みます。フレームには引数とローカル変数が入ります。呼び出しが連なるほどフレームが上へ積み重なり、`return` すると一番上のフレームが **まるごと消えます**。

```java
public static void main(String[] args) {
    User u = createUser("太郎");
    System.out.println(u.name);
}

static User createUser(String name) {
    User u = new User();   // 実体はヒープへ。u には矢印が入る
    u.name = name;
    return u;              // 矢印を返して、このフレームは消える
}
```

`createUser` を呼んだ瞬間のメモリは、次のようになっています。

<figure class="figure"><svg viewBox="0 0 640 300" xmlns="http://www.w3.org/2000/svg" font-family="inherit"><defs><marker id="jvmmem-arr-a" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="var(--accent)"/></marker></defs><text x="130" y="22" font-size="14" font-weight="700" text-anchor="middle" fill="var(--ink-muted)">スタック</text><text x="515" y="22" font-size="14" font-weight="700" text-anchor="middle" fill="var(--ink-muted)">ヒープ</text><rect x="20" y="38" width="240" height="66" rx="10" fill="var(--surface-2)" stroke="var(--line)" stroke-width="2"/><text x="30" y="58" font-size="13" fill="var(--ink)">実行中: main()</text><text x="30" y="80" font-size="12" fill="var(--ink-muted)">User u = ...(戻るまで待機)</text><rect x="20" y="128" width="240" height="112" rx="10" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="2"/><text x="30" y="150" font-size="13" font-weight="700" fill="var(--ink)">実行中: createUser("太郎")</text><text x="30" y="174" font-size="13" fill="var(--ink)">引数 name = "太郎"</text><text x="30" y="196" font-size="13" fill="var(--ink)">ローカル変数 u</text><text x="30" y="220" font-size="11" fill="var(--ink-muted)">(return するとこのフレームは消える)</text><path d="M262 196 L410 206" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jvmmem-arr-a)"/><rect x="415" y="168" width="200" height="92" rx="10" fill="var(--surface-2)" stroke="var(--accent)" stroke-width="2.5"/><text x="515" y="192" font-size="13" font-weight="700" text-anchor="middle" fill="var(--ink)">User インスタンス</text><text x="515" y="214" font-size="13" text-anchor="middle" fill="var(--ink)">name: "太郎"</text><text x="515" y="236" font-size="11" text-anchor="middle" fill="var(--ink-muted)">(住所 #A1)</text><text x="130" y="270" font-size="12" text-anchor="middle" fill="var(--ink-muted)">呼び出すたびにフレームが積まれ、</text><text x="130" y="288" font-size="12" text-anchor="middle" fill="var(--ink-muted)">return すると上から消えていく</text></svg><figcaption>スタックのフレームとヒープのオブジェクトを矢印(参照)でつなぐ</figcaption></figure>

`createUser` のフレームが消えても、User の実体は消えません。返した矢印を `main` の `u` が受け取っているからです。逆に、メソッドの中で宣言した変数がメソッドの外から使えないのは、**フレームごと消えている** からです。

::: haru
じゃあ、`int` のローカル変数はどこに?
:::

::: sensei
`int` や `double` みたいな基本データ型のローカル変数は、値そのものがフレームの中に入る。矢印が入るのは参照型だけ。だから `int` を引数で渡すと値がコピーされて、呼ばれた側で `n++` しても呼び出し元は変わらない。
:::

## ヒープ:new した実体の置き場所

`new` で作ったオブジェクトは、すべてヒープに置かれます。**配列もオブジェクト** なので、`new int[1000]` もヒープです。フィールドはその実体の中にあるので、`int` のフィールドでも置き場所はヒープになります。

参照型の変数に入っているのは、ヒープ上の実体を指す **参照(矢印)** だけです。だから矢印は何本でもコピーできます。

```java
Cart a = new Cart("ハル", 100);
Cart b = a;          // 矢印をコピー。実体は1つのまま
b.points = 500;
System.out.println(a.points);   // 500
```

`a == b` が `true` になるのは、`==` が参照型では **同じ実体を指しているか** を比べるからです。中身が同じでも別に `new` したものは `false`。中身で比べたいときは `equals` を使い、自作クラスなら `equals` をオーバーライドします(文字列のレッスンで見た「文字列は必ず equals」と同じ理由です)。

## 値渡しをメモリの絵で見る

Java の引数は **常に値渡し** です。参照型の場合、コピーされる「値」は **矢印** です。呼び出し元の変数と引数は別々の箱ですが、同じ実体を指しています。

<figure class="figure"><svg viewBox="0 0 640 260" xmlns="http://www.w3.org/2000/svg" font-family="inherit"><defs><marker id="jvmmem-arr-b" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="var(--accent)"/></marker></defs><text x="130" y="20" font-size="13" fill="var(--ink-muted)">main() のフレーム</text><rect x="20" y="30" width="220" height="60" rx="10" fill="var(--surface-2)" stroke="var(--line)" stroke-width="2"/><text x="30" y="56" font-size="13" fill="var(--ink)">cart</text><text x="30" y="78" font-size="11" fill="var(--ink-muted)">(住所 #C9 を持つ)</text><text x="130" y="126" font-size="13" fill="var(--ink-muted)">addPoints(cart, 50) のフレーム</text><rect x="20" y="136" width="220" height="60" rx="10" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="2"/><text x="30" y="162" font-size="13" fill="var(--ink)">c(引数)</text><text x="30" y="184" font-size="11" fill="var(--ink-muted)">(#C9 という値がコピーされる)</text><rect x="420" y="88" width="200" height="90" rx="10" fill="var(--surface-2)" stroke="var(--accent)" stroke-width="2.5"/><text x="520" y="112" font-size="13" font-weight="700" text-anchor="middle" fill="var(--ink)">Cart インスタンス</text><text x="520" y="134" font-size="13" text-anchor="middle" fill="var(--ink)">points: 100 → 150</text><text x="520" y="156" font-size="11" text-anchor="middle" fill="var(--ink-muted)">(住所 #C9)</text><path d="M242 62 L418 118" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jvmmem-arr-b)"/><path d="M242 166 L418 148" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jvmmem-arr-b)"/><text x="320" y="228" font-size="12" text-anchor="middle" fill="var(--ink-muted)">cart も c も同じ実体を指すので、c 経由の書き換えは cart 側にも見える</text></svg><figcaption>引数として渡っても、コピーされるのは矢印。実体は1つを指したまま</figcaption></figure>

この絵から、次の2つの違いが読み取れます。

```java
static void addPoints(Cart c, int p) {
    c.points += p;            // 矢印の先を書き換える → 呼び出し元にも見える
}

static void replace(Cart c) {
    c = new Cart("別人", 0);  // 引数の矢印を付け替えるだけ → 呼び出し元は変わらない
}
```

`addPoints` は矢印を辿って **同じ実体の中身** を書き換えるので、呼び出し元から見ても変わっています。`replace` は引数 `c` という箱の矢印を新しい実体に向け直しただけで、呼び出し元の変数は元の実体を指したままです。

::: sensei
「中身を書き換える」と「矢印を付け替える」は別物。これが区別できれば、値渡しの話はもう迷わない。
:::

実務では逆に、「渡したオブジェクトが、呼んだメソッドの中で書き換えられているかもしれない」と疑って読むことになります。演習1のように、元を変えたくないときは **新しい実体を作って返す** のが安全です。

### null は「矢印がない」状態

参照型の変数に `null` が入っているときは、**どこも指していません**。矢印がないのに `.` で先へ進もうとすると、行き先がないので `NullPointerException` です。「空っぽの実体がある」のではなく「実体がない」と覚えてください。

## ガベージコレクション:辿れなくなったら回収

ヒープに `new` し続けたら、いつかメモリが尽きそうです。Java ではそれを **ガベージコレクション(GC)** が防ぎます。C 言語の `free` のような解放処理を、プログラマーは書きません。

GC は「もう要らないもの」を、**到達可能かどうか** で判断します。実行中のメソッドのローカル変数や static フィールドなどを出発点(**GC ルート**)にして矢印を辿り、行き着けるオブジェクトは「まだ使われている」、行き着けないものは「ゴミ」とみなして回収します。

<figure class="figure"><svg viewBox="0 0 620 260" xmlns="http://www.w3.org/2000/svg" font-family="inherit"><defs><marker id="jvmmem-arr-e" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="var(--accent)"/></marker></defs><rect x="20" y="40" width="150" height="60" rx="10" fill="var(--surface-2)" stroke="var(--line)" stroke-width="2"/><text x="95" y="62" font-size="12" fill="var(--ink-muted)" text-anchor="middle">static フィールド</text><text x="95" y="84" font-size="13" font-weight="700" fill="var(--ink)" text-anchor="middle">UserCache.cache</text><path d="M172 70 L228 70" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jvmmem-arr-e)"/><rect x="230" y="40" width="150" height="60" rx="10" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="2"/><text x="305" y="64" font-size="13" font-weight="700" text-anchor="middle" fill="var(--ink)">Map(キャッシュ本体)</text><text x="305" y="86" font-size="11" text-anchor="middle" fill="var(--ink-muted)">エントリを保持</text><path d="M382 70 L428 70" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jvmmem-arr-e)"/><rect x="430" y="40" width="160" height="60" rx="10" fill="var(--surface-2)" stroke="var(--accent)" stroke-width="2.5"/><text x="510" y="64" font-size="13" font-weight="700" text-anchor="middle" fill="var(--ink)">User インスタンス</text><text x="510" y="86" font-size="11" text-anchor="middle" fill="var(--ink-muted)">cache 経由でまだ到達可能</text><rect x="230" y="160" width="160" height="70" rx="10" fill="var(--surface-2)" stroke="var(--line)" stroke-width="2" stroke-dasharray="6 4"/><text x="310" y="186" font-size="13" font-weight="700" text-anchor="middle" fill="var(--ink-muted)">Order インスタンス</text><text x="310" y="206" font-size="11" text-anchor="middle" fill="var(--ink-muted)">どの変数からも指されていない</text><text x="310" y="226" font-size="13" font-weight="700" text-anchor="middle" fill="var(--accent)">→ GC 対象</text></svg><figcaption>到達できるオブジェクトは残る。誰からも辿れないオブジェクトが GC の対象になる</figcaption></figure>

ポイントは2つです。

- **いつ回収されるかは決められない**。GC は JVM が必要だと判断したときに動きます。「辿れなくなった=回収される資格ができた」であって、その瞬間に消えるわけではありません。
- **辿れる限り絶対に回収されない**。もう使わないつもりでも、どこかに矢印が1本残っていれば、そのオブジェクトは生き続けます。

## Java でもメモリリークは起きる

本来は要らなくなったオブジェクトが、矢印が残っているせいで回収されず、溜まり続けることを **メモリリーク** と呼びます。GC がある Java でも普通に起きます。典型は次の2つです。

1. **static なコレクションに入れっぱなし**。static フィールドはクラスが使われている間ずっと生きているので、そこに `add` / `put` したものは、削除しない限り永遠に到達可能です。「キャッシュのつもりで static な Map に入れた」が一番多いパターンです。
2. **登録したものを解除し忘れる**。イベントのリスナーやコールバックを登録して、不要になっても `remove` しないと、登録先から辿れる状態が残ります。

対策は「溜めるなら上限を決める」「使い終わったら削除する」です。演習2では、件数の上限を持った履歴クラスを作ります。

::: genba キャッシュは「消す仕組み」とセットで作る
「何度も DB を読むのは遅いから、static な Map に覚えておこう」――発想は正しいのですが、件数の上限や有効期限を決めないと、何日か動いたあとで必ず `OutOfMemoryError` になります。現場では自作せず、上限や期限を設定できるキャッシュのライブラリ(Caffeine など)を使うのが普通です。
:::

## OutOfMemoryError と StackOverflowError

メモリが足りなくなったときのエラーは、**どの領域が足りないか** で2種類あります。

| | OutOfMemoryError | StackOverflowError |
|---|---|---|
| 足りない領域 | ヒープ(実体の置き場所) | スタック(フレームの置き場所) |
| 典型的な原因 | メモリリーク、巨大なデータを一度に読み込む | 終了条件のない再帰、深すぎる再帰 |
| メッセージの例 | `java.lang.OutOfMemoryError: Java heap space` | `java.lang.StackOverflowError` |
| 上限を変える起動オプション | `-Xmx`(ヒープの最大サイズ) | `-Xss`(スタック1本のサイズ) |

どちらも `Exception` ではなく **`Error`** の仲間です。例外処理のレッスンで見たとおり、アプリが catch して続行するものではありません。

```java
static int countDown(int n) {
    System.out.println(n);
    return countDown(n - 1);   // 止まる条件がない
}
```

`StackOverflowError` のスタックトレースは、同じメソッドの行が何百行も繰り返されるのが特徴です。繰り返されているメソッド名が、終了条件を確かめるべき犯人です。

::: haru
`-Xss` でスタックを大きくすれば、深い再帰も動きますよね?
:::

::: sensei
動くけど、入力がもう少し大きくなったらまた落ちる。データの量に比例して深くなる再帰は、**ループに書き直す** のが根本対策。ループならフレームは1つのままだからね。演習3でやってみよう。
:::

再帰が向いているのは、フォルダの階層のように **深さがそれほど大きくならない木構造** をたどるときです。「1 から n まで」「配列の先頭から末尾まで」のように、データの数だけ深くなる処理は、ループで書きます。

## static はどこに置かれるか

static フィールドはインスタンスの中ではなく、**クラスの情報と一緒に1か所だけ** 置かれます。だから、どのインスタンスから触っても同じ箱を見ることになり、寿命は「クラスが使われている間ずっと」です。

| | static フィールド | インスタンスフィールド |
|---|---|---|
| いくつあるか | クラスに1つ | 実体ごとに1つ |
| 置き場所 | クラスの情報と一緒 | ヒープ上の、その実体の中 |
| 寿命 | クラスが使われている間ずっと | その実体が GC されるまで |

この「ずっと生きている」性質が、メモリリークの温床にも、複数のスレッドで値を取り合う不具合の温床にもなります。

::: trivia 「クラスの情報」は実際どこにある?
JVM の仕様書では、クラスごとの情報を置く場所を **メソッド領域** と呼びます。いま主流の HotSpot JVM の実装では、クラスの定義やメソッドのコードは **Metaspace** というヒープの外の領域に、static フィールドの値はヒープ上にあるクラスごとのオブジェクトと一緒に置かれています。実装の細かい話なので、最初は「static はクラスに1つ、ずっと生きている」と押さえておけば十分です。
:::

::: warn よくあるミス
- `int[] copy = scores;` で **コピーしたつもり** → 矢印が増えただけ。別の配列が欲しければ `new` する
- メソッドの中で引数に `new` したものを代入して、**呼び出し元も変わったつもり** → 付け替えたのは引数の矢印だけ
- static な `List` / `Map` に **入れっぱなし** → 削除しない限り GC されず、いずれ OutOfMemoryError
- `StackOverflowError` を `-Xss` で **ごまかす** → データ量に比例する再帰はループに直す
:::

::: point まとめ
- **スタック** はメソッド呼び出しごとのフレーム(引数・ローカル変数)。`return` で消える。スレッドごとに1本
- **ヒープ** は `new` した実体の置き場所。全スレッドで共有
- 参照型の変数に入っているのは **矢印**。代入も引数渡しも矢印のコピー。中身の書き換えは伝わり、付け替えは伝わらない
- GC は **到達可能かどうか** で回収を決める。いつ回収されるかは決められない
- 矢印が残っている限り回収されない。static なコレクションへの溜め込みが **メモリリーク** の典型
- ヒープ不足は **OutOfMemoryError**、スタック不足は **StackOverflowError**。どちらも catch して続行しない
:::

::: sensei
スタックはスレッドごと、ヒープはみんなで共有。次は、その「みんな」が同時に動く **並行処理** の話だ。共有の実体を2つのスレッドが同時に書き換えると何が起きるか、楽しみにしてて。
:::
