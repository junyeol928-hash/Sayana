::: haru
先輩、第3部はいよいよ Spring ですね! で、DI って何すか? 求人票に毎回書いてあるやつ。
:::

::: sensei
Spring の心臓部だよ。今日は「`new` を自分で書かない」という、ちょっと不思議な世界に入る。まずは `new` だらけのコードのつらさから見よう。
:::

## new だらけのコードの何がつらいのか

注文を受けたら確認メールを送るサービスを書いてみます。

```java
class OrderService {
    // 自分で部品を new している
    private final SmtpMailSender mail =
            new SmtpMailSender("smtp.example.com");

    void placeOrder(String item) {
        // ...注文の保存...
        mail.send("ご注文ありがとうございます: " + item);
    }
}
```

動きはします。でも問題が2つあります。

- **テストしにくい**:`placeOrder` の単体テストを実行すると、本物の SMTP サーバにメールが飛びます。
- **差し替えられない**:「開発中はメールを送らずログに出したい」「Slack 通知に変えたい」となったら、`OrderService` 本体を書き換えるしかありません。

`OrderService` が `SmtpMailSender` という **具体的なクラスと、その作り方(接続先まで!)** をがっちり握っているのが原因です。このように、あるクラスが仕事をするために使う別のオブジェクトを **依存(dependency)** と呼びます。

## 依存性注入(DI):まずは素の Java で

解決策は「部品は自分で作らず、**外から渡してもらう**」ことです。これを **依存性注入(Dependency Injection, DI)** と呼びます。

```java
interface MailSender {
    void send(String body);
}

class OrderService {
    private final MailSender mail;

    // 依存はコンストラクタで受け取る
    OrderService(MailSender mail) {
        this.mail = mail;
    }

    void placeOrder(String item) {
        mail.send("ご注文ありがとうございます: " + item);
    }
}
```

組み立ては呼び出す側(`main` など)がやります。

```java
// 本番
var service = new OrderService(
        new SmtpMailSender("smtp.example.com"));
// テスト:ラムダで偽物を渡せる(MailSender は関数型インターフェース)
var test = new OrderService(
        body -> System.out.println("[送信したつもり] " + body));
```

`OrderService` は **インターフェース `MailSender` にだけ依存** し、どの実装が来るかは知りません。第1部のポリモーフィズムそのものです。DI は魔法ではなく、「コンストラクタで渡す」という設計の話だとまず押さえてください。

::: haru
あれ、これなら Spring いらなくないですか?
:::

::: sensei
クラスが3つなら、ね。業務アプリは数百クラスが絡み合う。その `new` の順番と配線を全部 `main` に書くのは地獄だよ。
:::

## IoC コンテナに組み立てを任せる

Spring は、この「`new` して配線する」仕事を肩代わりします。Spring が管理するオブジェクトを **Bean**、Bean を作って保持する入れ物を **IoC コンテナ**(実体は `ApplicationContext`)と呼びます。

**IoC(Inversion of Control, 制御の反転)** とは、「自分が部品を呼び出して組み立てる」のではなく「フレームワークが作って渡してくれる」ように、制御の主導権が逆転していることを指す言葉です。DI は IoC を実現する手段の1つです。

```mermaid
flowchart LR
  A["クラスを登録<br>(@Component など)"] --> B["IoC コンテナ<br>ApplicationContext"]
  B --> C["依存関係を解析<br>(コンストラクタの引数)"]
  C --> D["部品から順に new"]
  D --> E["コンストラクタに<br>渡して配線"]
  E --> F["Bean 完成<br>getBean で取り出せる"]
```

コンテナに「これは Bean だよ」と教える方法は2つあります。

**① クラスにアノテーションを付ける**

```java
@Repository
class JdbcOrderRepository implements OrderRepository { }

@Service
class OrderService {
    private final OrderRepository repo;
    OrderService(OrderRepository repo) {
        this.repo = repo;
    }
}
```

`@Component` が基本形で、`@Service`(業務ロジック)・`@Repository`(データアクセス)・`@Controller`(画面)はその **役割別の別名** です。どれも中身に `@Component` を含んでいて、Bean になる点は同じです。ただし `@Repository` は DB の例外を Spring の例外に変換する仕組みの対象にもなります。

**② `@Configuration` クラスの `@Bean` メソッドで作る**

```java
@Configuration
class MailConfig {
    @Bean
    MailSender mailSender() {
        return new SmtpMailSender("smtp.example.com");
    }
}
```

ライブラリのクラスのように **自分でアノテーションを付けられないクラス** や、作り方に引数や条件が要るときはこちらです。メソッドの戻り値が Bean として登録されます。

試すときは、コンテナにクラスを直接渡します。

```java
var ctx = new AnnotationConfigApplicationContext(
        MailConfig.class, OrderService.class);
OrderService s = ctx.getBean(OrderService.class);
```

`OrderService` のコンストラクタが `MailSender` を要求しているのを見て、コンテナが `mailSender()` の結果を渡してくれます。

## コンストラクタインジェクションが推奨される理由

古い記事ではこう書かれていることがあります。

```java
@Service
class OrderService {
    @Autowired  // フィールドインジェクション
    private MailSender mail;
}
```

短くて楽ですが、現場では **コンストラクタインジェクション** が推奨です(Spring 公式も同じ立場)。

| 観点 | フィールド | コンストラクタ |
|---|---|---|
| `final` | 付けられない | 付けられる |
| テストで `new` | 依存が null のまま | 引数で渡せる |
| 依存の多さ | 見えにくい | 引数の数で一目瞭然 |

フィールドインジェクションのクラスを `new OrderService()` でテストすると、`mail` が `null` のまま `NullPointerException` になります。Spring はリフレクションで `private` フィールドに直接値を入れているので、Spring 抜きでは正しく作れないのです。

なお **コンストラクタが1つだけなら `@Autowired` は省略できます**(Spring 4.3 以降)。現場では省略が普通です。

## 同じ型の Bean が2つあるとき

`MailSender` の実装 Bean が2つあると、コンテナはどちらを渡すか決められず、起動時に失敗します。

```text
NoUniqueBeanDefinitionException: No qualifying bean of type
'MailSender' available: expected single matching bean
but found 2: smtpMailSender,logMailSender
```

解決策は2つです。

```java
@Component
@Primary  // 迷ったらこれ(既定)
class SmtpMailSender implements MailSender { ... }

@Component("logMail")  // Bean 名を付ける
class LogMailSender implements MailSender { ... }

@Service
class AuditService {
    AuditService(
            @Qualifier("logMail") MailSender mail) { ... }
}
```

- `@Primary`:候補が複数あるとき **既定で選ばれる** Bean。
- `@Qualifier("名前")`:注入する側で **Bean 名を指定** する。`@Primary` より優先されます。

Bean 名は、`@Component` ならクラス名の先頭を小文字にしたもの(`smtpMailSender`)、`@Bean` ならメソッド名が既定です。

## Bean のスコープ:既定はシングルトン

`getBean` を何回呼んでも、同じ Bean は **同じインスタンス** が返ります。これを **singleton スコープ** と呼び、Bean の既定です(コンテナ1つにつき1個。デザインパターンの Singleton とは別物)。

だから、**Bean のフィールドにリクエストごとの状態を持つと事故ります**。

```java
@Service
class CartService {
    private int total;  // 全ユーザーで共有される!

    void add(int price) { total += price; }
}
```

Web アプリでは複数のリクエストが同じ `CartService` を同時に使うので、Aさんのカート金額にBさんの商品が足されます。しかもスレッドが同時に書き換えるので値が壊れることもあります。Bean は **状態を持たない(ステートレス)** 作りにし、データは引数・戻り値・DB で受け渡すのが鉄則です。毎回新しいインスタンスが欲しい場合は `@Scope("prototype")` もありますが、出番は多くありません。

::: warn よくあるミス
- `@Service` を付け忘れて `NoSuchBeanDefinitionException`(Bean が見つからない)。
- 自分で `new OrderService(...)` したオブジェクトに `@Autowired` が効くと思い込む。コンテナが作った Bean にしか注入されません。
- Bean のフィールドに「今のユーザー」「合計金額」などを保存してしまう。
:::

::: point まとめ
- 依存を自分で `new` せず外から受け取るのが DI。素の Java ならコンストラクタで渡すだけ。
- IoC コンテナ(`ApplicationContext`)が Bean を `new` して配線してくれる。登録は `@Component` 系か `@Configuration` + `@Bean`。
- 注入はコンストラクタで。フィールドを `final` にでき、テストで `new` できる。
- 同じ型が複数なら `@Primary`(既定)か `@Qualifier`(名前指定)。
- Bean は既定で singleton。フィールドに状態を持たせない。
:::
