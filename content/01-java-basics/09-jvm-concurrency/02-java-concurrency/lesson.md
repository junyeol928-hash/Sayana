::: haru
先輩、アクセス数を数える Service を作ったんです。テストでは 100 回呼んだらちゃんと 100 だったのに、本番だと実際のアクセスより少ないって言われて……。
:::

::: sensei
テストは1人で順番に呼んでたでしょ。本番は大勢が **同時に** 呼ぶ。今日はその「同時」の話、スレッドと並行処理だよ。
:::

前回の「JVM とメモリ」で、**スタックはスレッドごと、ヒープは全スレッドで共有** だと見ました。今回は、その「スレッド」が複数同時に動くと何が起きるのか、どう守るのかを扱います。

## スレッドとは:同時に動く「実行の流れ」

これまでのコードは、1本の道を上から下へ順番に実行する前提で読んできました。この **1本の実行の流れ** を **スレッド** と呼びます。`main` メソッドも、JVM が用意した main スレッドの上で動いています。

スレッドは自分で増やせます。やりたい処理をラムダ(`Runnable`)で渡して `Thread` を作り、`start()` で動かします。

```java
Thread worker = new Thread(() -> {
    System.out.println("別のスレッドで動いています");
});
worker.start();   // ここから worker と main が同時に進む
worker.join();    // worker が終わるまで main が待つ
System.out.println("worker は終わりました");
```

押さえることは2つです。

- **`start()` と `run()` は別物**。`start()` は新しいスレッドを作ってその上で処理を動かします。`run()` と書くと、ただのメソッド呼び出しになり、今のスレッドで普通に実行されるだけです。
- **`join()` で終わりを待つ**。待たずに結果を読むと、まだ計算し終わっていない値を見てしまうことがあります。

各スレッドは自分専用のスタックを持つので、**ローカル変数はスレッドごとに別々** です。一方、`new` したインスタンスはヒープにあり、参照さえ渡せばどのスレッドからでも同じものを触れます。問題が起きるのは、この「みんなで同じものを触る」ところです。

## Web アプリは「1リクエスト=1スレッド」

Spring Boot のような Web アプリでは、届いたリクエストごとに、サーバー(Tomcat)がスレッドプールからスレッドを1本割り当てて処理します。10人が同時にアクセスすれば、10本のスレッドが同時に動きます。

ここで大事なのは、`@Service` や `@Repository` の Bean は既定で **アプリ全体に1個だけ**(シングルトン)だということです。リクエストごとに `new` されるわけではありません。

<figure class="figure"><svg viewBox="0 0 600 220" xmlns="http://www.w3.org/2000/svg" font-family="inherit"><defs><marker id="jconc-arr" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="var(--accent)"/></marker></defs><text x="20" y="24" font-size="13" fill="var(--ink-muted)">リクエストA</text><rect x="20" y="32" width="150" height="40" rx="8" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="2"/><text x="95" y="57" font-size="13" text-anchor="middle" fill="var(--ink)">スレッドA</text><text x="20" y="96" font-size="13" fill="var(--ink-muted)">リクエストB</text><rect x="20" y="104" width="150" height="40" rx="8" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="2"/><text x="95" y="129" font-size="13" text-anchor="middle" fill="var(--ink)">スレッドB</text><path d="M170 52 L330 90" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jconc-arr)"/><path d="M170 124 L330 100" stroke="var(--accent)" stroke-width="2.5" marker-end="url(#jconc-arr)"/><rect x="336" y="55" width="220" height="90" rx="10" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="2.5"/><text x="446" y="85" font-size="14" font-weight="700" text-anchor="middle" fill="var(--ink)">UserService(1個)</text><text x="446" y="108" font-size="12" text-anchor="middle" fill="var(--ink-muted)">Spring の Bean はシングルトン</text><text x="446" y="128" font-size="12" text-anchor="middle" fill="var(--ink-muted)">全リクエストが同じインスタンスを使う</text><text x="300" y="185" font-size="13" text-anchor="middle" fill="var(--ink)">だから Service や Repository のフィールドに「状態」を持たせてはいけない</text></svg><figcaption>Web アプリは「1リクエスト=1スレッド」。同じ Service インスタンスを全スレッドが共有する</figcaption></figure>

ハルの Service はこうなっていました。

```java
@Service
public class AccessCounterService {
    private int count = 0;    // 全リクエストで共有されるフィールド

    public int increment() {
        count++;              // 複数スレッドが同時に実行すると値がずれる
        return count;
    }
}
```

`count` は1個しかない `AccessCounterService` のフィールドなので、全スレッドが同じ `count` を書き換えます。複数のスレッドから同時に使われても正しい結果になることを **スレッドセーフ** と言いますが、このコードはスレッドセーフではありません。

::: haru
でも `count++` って1行ですよ? 1行の途中で割り込まれるなんてことあります?
:::

::: sensei
1行に見えるだけ。中身は3つの手順なんだ。
:::

## 競合状態:count++ は3ステップ

`count++` は、内部では **①読む → ②1を足す → ③書き戻す** の3ステップです。2つのスレッドがこの途中で入れ替わると、加算が1回分消えます。

<figure class="figure"><svg viewBox="0 0 600 200" xmlns="http://www.w3.org/2000/svg" font-family="inherit"><text x="20" y="24" font-size="13" fill="var(--ink-muted)">スレッドA(count=0から開始)</text><rect x="20" y="32" width="90" height="34" rx="6" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="1.5"/><text x="65" y="54" font-size="12" text-anchor="middle" fill="var(--ink)">読む: 0</text><rect x="130" y="32" width="110" height="34" rx="6" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="1.5"/><text x="185" y="54" font-size="12" text-anchor="middle" fill="var(--ink)">1を足す: 1</text><rect x="460" y="32" width="110" height="34" rx="6" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="1.5"/><text x="515" y="54" font-size="12" text-anchor="middle" fill="var(--ink)">書く: count=1</text><text x="20" y="104" font-size="13" fill="var(--ink-muted)">スレッドB(同じタイミングで開始)</text><rect x="250" y="112" width="90" height="34" rx="6" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="1.5"/><text x="295" y="134" font-size="12" text-anchor="middle" fill="var(--ink)">読む: 0</text><rect x="350" y="112" width="110" height="34" rx="6" fill="var(--surface-2)" stroke="var(--ink)" stroke-width="1.5"/><text x="405" y="134" font-size="12" text-anchor="middle" fill="var(--ink)">1を足す: 1</text><rect x="470" y="112" width="110" height="34" rx="6" fill="var(--accent-soft)" stroke="var(--ink)" stroke-width="1.5"/><text x="525" y="134" font-size="12" text-anchor="middle" fill="var(--ink)">書く: count=1</text><text x="300" y="175" font-size="13" text-anchor="middle" fill="var(--ink)">2回 increment したのに、最終的な count は 1 のまま(1回分が消える)</text></svg><figcaption>count++ の3ステップに、別スレッドが割り込むと加算が消える</figcaption></figure>

A が書き戻す前に B が読んだので、2人とも「0 に 1 を足して 1」を書いています。このように、**実行される順番によって結果が変わってしまう** 状態を **競合状態**(race condition)と呼びます。

厄介なのは、毎回起きるわけではないことです。割り込みのタイミングはその時々で変わるので、1万回数えて 9,998 になる日もあれば、ぴったり合う日もあります。1人で試すテストでは絶対に再現しません。だから「テストでは合っていた」のです。

## synchronized:1度に1スレッドだけ通す

いちばん素朴な対策は、3ステップの間ほかのスレッドを入れないことです。メソッドに **`synchronized`** を付けると、あるスレッドが実行している間、ほかのスレッドは入り口で待たされます。

```java
public synchronized int increment() {   // 1度に1スレッドしか実行できない
    count++;
    return count;
}
```

`synchronized (this) { ... }` のように、メソッドの一部だけを **ブロック** で守ることもできます。読むときは「どこからどこまでが1度に1スレッドか」「何を鍵(ロック)にしているか」を確認します。範囲が広すぎると待ち時間が増えて遅くなり、狭すぎると守り漏れが起きます。

なお、書き込みだけでなく **読むメソッドにも** `synchronized` が要ります。付けないと、ほかのスレッドが書いた最新の値が見える保証がありません。

## volatile:値の「見え方」だけを保証する

各スレッドは高速化のため、フィールドの値を CPU のキャッシュなどに持ったまま使うことがあります。そのため、あるスレッドが書き換えた値が、ほかのスレッドから **すぐには見えない** ことがあります。

`volatile` を付けたフィールドは、書いた値がほかのスレッドに必ず見えるようになります。定番は「止まれ」の合図です。

```java
public class Worker implements Runnable {
    private volatile boolean stopped = false;

    public void run() {
        while (!stopped) {   // 毎回、最新の stopped を読む
            doWork();
        }
    }

    public void stop() {
        stopped = true;      // 別のスレッドから呼ばれる
    }
}
```

`volatile` が無いと、`stop()` を呼んでもループが気づかずに回り続けることがあります。

::: warn volatile では count++ は守れない
`volatile` が保証するのは **1つの値の見え方** だけです。`volatile int count;` にしても、`count++` の「読む・足す・書く」は相変わらず割り込まれます。
- 1回書いて、ほかが読むだけのフラグ → `volatile`
- 複数ステップをまとめて守りたい → `synchronized` か、次の `AtomicInteger`
:::

## java.util.concurrent:安全な部品を使う

自分で `synchronized` を書くより、**最初からスレッドセーフに作られた部品** を使う方が、安全で読みやすくなります。`java.util.concurrent` パッケージにそろっています。

| クラス | 役割 |
|---|---|
| `AtomicInteger` / `AtomicLong` | 1つの数値を **割り込まれない1操作** で増減できる。カウンタの定番 |
| `ConcurrentHashMap<K, V>` | 複数スレッドから同時に読み書きできる Map。共有するなら `HashMap` の代わりに |
| `ExecutorService` | スレッドを使い回す「作業員のプール」 |
| `Future<T>` | 「あとで返ってくる結果」の引換券 |
| `CompletableFuture<T>` | 非同期処理をつなげて書ける Future |

ハルのカウンタは `AtomicInteger` で直せます。

```java
@Service
public class AccessCounterService {
    private final AtomicInteger count = new AtomicInteger(0);

    public int increment() {
        return count.incrementAndGet();   // 読む・足す・返すが1操作
    }
}
```

フィールドは `final` で構いません。`AtomicInteger` という箱自体は差し替えず、箱のメソッドで中の数値を変えるからです。`synchronized` も書かずに済みます。

## ExecutorService と Future:スレッドは使い回す

`new Thread(...)` を処理のたびに作るのは、作る・捨てるのにコストがかかり、数の上限もありません。実務では **ExecutorService**(スレッドプール)に仕事を投げます。

```java
ExecutorService executor = Executors.newFixedThreadPool(4);   // 4人の作業員
Future<String> future = executor.submit(() -> callExternalApi());
try {
    String result = future.get(3, TimeUnit.SECONDS);  // 3秒待って来なければ諦める
    System.out.println(result);
} catch (TimeoutException e) {
    future.cancel(true);   // 待つのをやめ、処理にも中断を伝える
    log.warn("external api timed out");
} finally {
    executor.shutdown();   // 使い終わったら必ず止める
}
```

- `submit` は処理を投げるとすぐに `Future`(引換券)を返します。結果はまだ来ていないかもしれません。
- `future.get(時間, 単位)` で、結果が来るまで待って受け取ります。**引数なしの `get()` は無期限に待つ** ので、外部 API が返事をしないとスレッドが止まったままになります。
- `shutdown()` を忘れると、プールのスレッドが残り続けます。

複数の仕事を並べたいときは、**先に全部 submit してから、順番に get** します。投げるたびに get すると、結局1件ずつ待つことになります。投げた順に get すれば、どれが先に終わっても結果の順番は保たれます。

## CompletableFuture:非同期処理をつなぐ

`CompletableFuture` は、別スレッドで動く処理を **パイプのようにつなげて** 書けます。

```java
CompletableFuture<Integer> priceFuture = CompletableFuture
        .supplyAsync(() -> fetchPrice(itemId))         // 別スレッドで実行 → Integer
        .thenApply(price -> price * quantity)           // 普通の関数で加工
        .thenCompose(total -> applyDiscountAsync(total)); // CompletableFuture を返す関数をつなぐ

int finalPrice = priceFuture.join();   // 完了を待って取り出す
```

`thenApply` と `thenCompose` の使い分けは、Stream や Optional の `map` と `flatMap` と同じです。**渡す関数がふつうの値を返すなら `thenApply`、`CompletableFuture` を返すなら `thenCompose`**。`thenApply` で非同期を返す関数をつなぐと、`CompletableFuture<CompletableFuture<Integer>>` と二重になってしまいます。

`join()` は `get()` と同じく完了を待ちます(チェック例外を投げない代わりに `CompletionException` で包みます)。こちらも、外部を待つなら `orTimeout(3, TimeUnit.SECONDS)` で時間制限を付けるのが安全です。

## シングルトン Bean に「状態」を持たせない

ここまでの話を、業務コードの読み方にまとめます。Bean は全スレッドで共有されるので、**書き換え可能なフィールドを持った瞬間に競合状態の候補** になります。

```java
@Service
public class CartService {
    private final PriceRepository priceRepository;   // OK: DI された不変の部品
    private int sum;                                 // NG: 計算途中の値を共有している

    public int total(List<String> items) {
        sum = 0;
        for (String item : items) {
            int price = priceRepository.findPrice(item);
            sum += price;   // 途中で別のリクエストが sum = 0 にすると、合計が狂う
        }
        return sum;
    }
}
```

直し方は `synchronized` ではありません(全リクエストが1列に並んで遅くなります)。**`int sum = 0;` をメソッドの中に移す** だけです。ローカル変数はスレッドごとのスタックにあるので、何人が同時に呼んでも混ざりません。

::: point 安全な Bean の形
- フィールドは **`final` の依存(Repository など)だけ**
- リクエストごとのデータは **引数・戻り値・ローカル変数** でやり取りする
- どうしても共有の状態が要るなら `AtomicInteger` や `ConcurrentHashMap` を使う
:::

レビューで並行処理の危険を探すときは、次の順で見ます。

1. **static で書き換え可能なフィールド** はないか(アプリ全体で1つ)
2. **`@Service` / `@Component` のフィールド** が final の依存だけか。カウンタや `HashMap` のキャッシュが混じっていないか
3. `synchronized` があれば、その **範囲と鍵** は何か
4. `Future.get()` / `join()` に **タイムアウト** があるか

::: trivia ThreadLocal とデッドロック
- **ThreadLocal** は、同じ変数でも **スレッドごとに別々の値** を持てる入れ物です。Spring Security の「今ログインしているのは誰か」(`SecurityContextHolder`)は、これでリクエストごとの情報を持っています。スレッドはプールで使い回されるので、使い終わったら `remove()` しないと次のリクエストに前の値が漏れます。
- **デッドロック** は、スレッドAが鍵1を持って鍵2を待ち、スレッドBが鍵2を持って鍵1を待つ、という「お互い待ち」で永遠に止まる状態です。**鍵を取る順番をプログラム全体でそろえる**(例:必ず ID の小さい方から)のが基本の対策です。
:::

::: warn よくあるミス
- `thread.run()` と書いて **別スレッドで動いていない**(ただのメソッド呼び出し)→ `start()`
- `join()` や `get()` で待たずに結果を読む → 終わる前の値を見る
- `volatile` を付けたから `count++` も安全だと思う → `AtomicInteger` か `synchronized`
- `ExecutorService` を `shutdown()` し忘れる → スレッドが残り続ける
- `future.get()` にタイムアウトを付けない → 外部が止まると道連れで止まる
:::

::: point まとめ
- スレッドは1本の実行の流れ。Web アプリは **1リクエスト=1スレッド** で、シングルトンの Bean を全スレッドが共有する
- `count++` は「読む・足す・書く」の3ステップ。途中で割り込まれて加算が消えるのが **競合状態**
- `synchronized` は範囲全体を1度に1スレッドに、`volatile` は1つの値の見え方だけを保証する
- カウンタは `AtomicInteger`、共有の Map は `ConcurrentHashMap`
- スレッドは `ExecutorService` で使い回し、結果は `Future` を **タイムアウト付き** で受け取り、最後に `shutdown()`
- `thenApply`(ふつうの関数)と `thenCompose`(非同期を返す関数)は `map` / `flatMap` と同じ判断
- Bean には **final の依存だけ**。リクエストごとのデータはローカル変数へ
:::

::: sensei
第3部の Spring では、DI で Bean を組み立てていく。そのとき「この Bean は全員で共有される1個だ」という今日の感覚が、そのまま効いてくるよ。
:::
