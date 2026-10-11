# ☕ Java道場

Java と Spring Tool Suite(STS)/ Spring Boot をゼロから体系的に学ぶための、**自分のPCだけで動かす学習Webアプリ**です。
1つのレッスンの中で **読む → 動かす → 書く → AIに見てもらう → 復習** が回ります。

- **読む**: モカ先輩 ☕ と新人ハル 🐣 の掛け合いで進む、軽く読めて中身は硬派な解説(図は Mermaid)
- **動かす**: サンプルコードを画面上で編集・実行(JShell)
- **書く**: 演習を提出すると、別のJVMで JUnit が走って自動採点(10秒タイムアウト・メモリ上限つき)
- **AIに見てもらう**: コードレビュー / エラー翻訳 / 段階ヒント / AIチューター / 類題生成(Claude API)
- **復習**: クイズと演習を 1日後 → 3日後 → 7日後 → 14日後 に再出題(間隔反復)

ダッシュボードでは、部・章ごとの進捗、連続学習日数、苦手レッスンが見られます。提出履歴と AI レビューは後から見返せます。
PCでもスマホでも使えます(下の「📱 スマホで使う」)。

カリキュラムは全4部・50レッスン(第1部 Java基礎 22 / 第2部 開発ツール 6 / 第3部 Spring 14 / 第4部 総合演習 8)。
第3部・第4部の演習は Spring・MockMvc 入りで画面上で採点されます。

設計とカリキュラム、今後の計画は [docs/PLAN.md](docs/PLAN.md)、教材の書き方は [docs/CONTENT_FORMAT.md](docs/CONTENT_FORMAT.md) にあります。

---

## 📱 スマホで使う

スマホでの使い方は3通りあります。**PC が無くても、Wi-Fi が無くても**使えます。

| | できること | PC | 電波 |
|---|---|---|---|
| **A. スマホ版**(おすすめ) | 読む・サンプルを見る・クイズ・復習・演習の自己チェック・AI(キーがあれば) | 不要 | 最初の1回だけ必要。あとは**電波なしでOK**(AI だけは電波が必要) |
| **B. Codespaces 版** | 全部(コードの実行・自動採点・AI) | 不要 | 必要(モバイル通信でOK) |
| **C. LANモード** | 全部 | 家のPCを起動 | 同じ Wi-Fi |

### A. スマホ版(オフライン対応)

**https://junyeol928-hash.github.io/Sayana/m/** をスマホのブラウザで開くだけです。

1. 電波のある所で一度開く(全50レッスンが自動でスマホに保存されます。約4MB)
2. **ホーム画面に追加**するとアプリのように使えます
   - iPhone(Safari): 下の共有ボタン(□に↑)→「ホーム画面に追加」→「追加」
   - Android(Chrome): 右上の「⋮」→「ホーム画面に追加」(または「アプリをインストール」)
3. あとは地下鉄でも機内モードでも、読む・クイズ・復習ができます(右上の表示が「オフライン」になります)

- 学習記録(読んだ・クイズ・復習・自己チェック)は**そのスマホの中**に保存されます。PC版・Codespaces版とは別の記録です
- コードの実行と自動採点はできません(Java が要るため)。演習は「採点テスト」を読んで自分でチェックし、「できた/まだ」を記録します。本物の採点は B か C で
- 電波があるときは、スマホに保存した APIキーで AI(チューター・ヒント・コードレビュー)が使えます(下の「APIキーをスマホで設定する」)
  - モデルは設定画面で機能ごとに変えられます(既定: チューター Sonnet / コードレビュー Opus / ヒント Sonnet)
- 教材(`content/`)を変えたら `./mvnw -q compile exec:java` でスマホ版(`m/`)を作り直してコミットします(古いままだとテストが失敗して知らせます)

### B. PCなしで、全機能を使う(GitHub Codespaces)

GitHub のクラウド上の開発環境でアプリを動かし、スマホのブラウザで開きます。URL は**あなたのGitHubアカウントでしか開けません**。モバイル通信でも使えます。

1. スマホのブラウザ(Safari / Chrome)で、このリポジトリのページを開く
   - GitHub アプリではなくブラウザで。メニューの「デスクトップ用Webサイトを表示」にすると操作しやすい
2. 緑の **「<> Code」** ボタン → **「Codespaces」** タブ → **「Create codespace on main」**
3. VS Code の画面が開き、自動でアプリが起動します。初回は依存ライブラリのダウンロードで数分かかります
   - 画面下のターミナルに `Started JavaDojoApplication` と出たら準備完了
4. アプリの画面が新しいタブで開きます。開かないときは、VS Code の **「ポート」**(PORTS)タブで 8080 番の行の 🌐(ブラウザで開く)を押す
   - URL は `https://<codespace名>-8080.app.github.dev` の形。**ブックマークかホーム画面に追加**しておくと便利
5. 次から使うときは https://github.com/codespaces を開き、作った codespace を選んで開く → 自動でアプリが起動します

注意:
- 学習記録は codespace の中(`data/`)に保存されます。**codespace を削除すると消えます**。GitHub は長く使っていない(既定で30日)停止中の codespace を自動削除するので、ときどき開くか、https://github.com/codespaces で「Keep codespace」を設定してください
- 個人アカウントには毎月の無料枠があります(2コアのマシンで月数十時間程度)。使わないときは止めておきましょう(しばらく操作しないと自動で止まります)

### C. 家のPCで動かして、同じWi-Fiのスマホで開く(LANモード)

PCで次のように起動します。

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=lan"
```

起動ログに次のような行が出るので、その URL をスマホで開きます(合言葉つき。一度開けば、そのスマホのブラウザは以後 URL だけで入れます)。

```text
  スマホで開く: http://192.168.1.23:8080/?key=xxxxxxxxxxxxxxxxxxxxxxxx
```

- Windows ファイアウォールの確認が出たら「プライベート ネットワーク」を許可してください
- このアプリは提出されたコードを PC 上で実行します。そのため LAN モードでは、**PC以外からのアクセスには合言葉が必要**です(合言葉は `data/lan-key.txt`。消すと次の起動で作り直されます)。合言葉付きの URL は人に教えないでください
- カフェなどの公共 Wi-Fi では使わないでください。LAN モードでないとき(普段の起動)は PC 自身からしか開けません

---

## 🔑 APIキーをスマホで設定する

AI機能(チューター・段階ヒント・コードレビュー・エラー翻訳・類題生成)を使うには、Claude の APIキーが必要です。**全部スマホだけでできます。**
(画面の表記は変わることがあります。見当たらないときは近い名前のメニューを探してください)

### 1. APIキーを作る(Anthropic Console)

1. スマホのブラウザで **https://console.anthropic.com/** を開き、メールアドレスか Google アカウントで登録・ログインする
2. **クレジットを購入する**:メニュー(☰)→ **Settings** → **Billing**(Plans & Billing)→ **Buy credits** でカードを登録して少額(例: 5ドル)を購入
   - API は前払い制です。残高が無いと「利用上限」のエラーになります
   - 使いすぎが心配なら、同じ画面の上限設定(Usage limits / Spend limits)で月の上限を決めておくと安心です
3. **キーを作る**:メニュー → **Settings** → **API Keys** → **Create Key** → 名前(例: `java-dojo`)を入れて作成
4. 表示された `sk-ant-` で始まる長い文字列を **「Copy」でコピー**する
   - **この画面を閉じると二度と表示されません**。すぐ次の手順で貼り付けます(メモアプリに一時的に貼るなら、使い終わったら消す)
   - スクリーンショットや LINE などに貼らないこと。漏れたら同じ API Keys の画面で削除(無効化)して作り直せば大丈夫です

### 2-A. スマホ版に登録する

1. スマホ版(https://junyeol928-hash.github.io/Sayana/m/)を開き、下の **⚙️ 設定**
2. 「APIキー」欄を長押し → 貼り付け → **保存**
3. **接続テスト** を押して「✅ つながりました」と出れば完了
   - キーはそのスマホのブラウザの中にだけ保存され、Claude API にだけ送られます(GitHub には送られません)
   - 家族などとスマホを共有している場合は保存しないでください。消すときは同じ画面の **削除**

### 2-B. Codespaces 版に登録する

1. スマホのブラウザで **https://github.com/settings/codespaces** を開く(GitHub にログインした状態で。右上のアイコン → Settings → 左メニューの Codespaces でも同じ)
2. 「**Codespaces secrets**」の **New secret** を押す
3. 次のように入力して **Add secret**
   - **Name**: `ANTHROPIC_API_KEY`(この通りに。大文字・アンダースコア)
   - **Value**: コピーしたキーを貼り付け
   - **Repository access**: **Select repositories** で `junyeol928-hash/Sayana` を選ぶ
4. すでに codespace を開いている場合は、画面に出る「Reload to apply」を押すか、https://github.com/codespaces で codespace を止めてから開き直す
5. アプリ右上が **AI ON** になっていれば完了(**AI OFF** のままなら 4 をやり直す)

### PC で使う場合

下の「APIキーの設定(AI機能を使う場合)」を見てください(Windows の環境変数に設定します)。

## 必要なもの

- **JDK 21**(JRE ではなく JDK。採点に `javac`、サンプル実行に `jshell` を使います)
  - 例: [Eclipse Temurin 21](https://adoptium.net/) の Windows x64 用 `.msi`。インストーラで「Add to PATH」「Set JAVA_HOME variable」を有効にする
  - 確認: PowerShell で `java -version` と `javac -version` が 21 を表示すること
- インターネット接続(初回の Maven 依存ダウンロード、画面のエディタ CodeMirror・図の Mermaid の CDN、AI 機能)
- (任意)Claude API の APIキー。**なくても AI 以外の機能はすべて使えます**

Maven を別途インストールする必要はありません(Maven Wrapper 同梱)。

---

## 起動方法

PowerShell でプロジェクトのフォルダに移動して:

```powershell
./mvnw spring-boot:run
```

起動したらブラウザで **http://127.0.0.1:8080** を開きます。止めるときは `Ctrl + C`。

- 初回は依存ライブラリのダウンロードで数分かかります。
- **プロジェクトのルート(`pom.xml` のあるフォルダ)で起動してください。** 教材は `./content`、学習記録は `./data` を相対パスで使います。
- アプリは `127.0.0.1` だけで待ち受けるので、同じネットワークの他のPCからは見えません。
- ポートを変えたいとき: `./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`

### テスト

```powershell
./mvnw test
```

全演習の模範解答が採点テストに合格すること・雛形が不合格になること・全サンプルが動くこと・画面の通し・AI 機能(Claude API はモックに差し替え)を確認します。実APIには通信しません。

---

## APIキーの設定(AI機能を使う場合)

[Claude Console](https://console.anthropic.com/) で APIキーを発行し、環境変数 `ANTHROPIC_API_KEY` に設定します。
APIキーはファイルに書かないでください(リポジトリにコミットされないよう、アプリは環境変数からだけ読みます)。

### Windows PowerShell の例

**そのウィンドウだけで使う場合**(ウィンドウを閉じると消えます):

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-api03-xxxxxxxx"
./mvnw spring-boot:run
```

**ずっと使う場合**(ユーザー環境変数に保存。設定後に PowerShell と STS を開き直す):

```powershell
[Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-api03-xxxxxxxx", "User")
```

消すとき:

```powershell
[Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", $null, "User")
```

画面右上が **AI ON** になっていれば有効です(未設定なら **AI OFF**)。

### 使うモデル

`src/main/resources/application.yml` の `dojo.ai.models` で機能ごとに変えられます。

| 機能 | 既定のモデル |
|---|---|
| AIチューター / エラー翻訳 | `claude-sonnet-5-5` |
| コードレビュー | `claude-opus-5-5`(Sonnet の約2倍の料金で、より深く見る) |
| 段階ヒント | `claude-sonnet-5-5` |
| 類題生成 | `claude-haiku-5-5` |

レッスン本文はプロンプトキャッシュに載せているので、同じレッスンで何度質問しても教材部分の入力コストは抑えられます。
システムプロンプトは `src/main/resources/prompts/*.md` にあり、自由に調整できます。

---

## STS(Spring Tool Suite)で開く

### 1. JDK 21 を STS に登録する

STS 本体に同梱の Java は実行用(JRE)で、`javac` や `jshell` が入っていないことがあります。**JDK 21 を登録して、このプロジェクトで使います。**

1. **Window > Preferences**(日本語化している場合は **ウィンドウ > 設定**)
2. **Java > Installed JREs**(**Java > インストール済みの JRE**)で **Add...** → **Standard VM** → **Directory...** で JDK 21 のフォルダ(例 `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`)を選んで **Finish**
3. 追加した JDK 21 にチェックを入れて **Apply and Close**

### 2. プロジェクトをインポートする

1. **File > Import...**(**ファイル > インポート**)
2. **Maven > Existing Maven Projects**(**Maven > 既存 Maven プロジェクト**)を選んで **Next**
3. **Root Directory** にこのフォルダ(`pom.xml` のある場所)を指定し、`pom.xml` にチェックが入っていることを確認して **Finish**
4. 右下の進捗バーが終わるまで待つ(依存ライブラリのダウンロード)。エラー表示が残ったら、プロジェクトを右クリック > **Maven > Update Project...**(**Maven > プロジェクトの更新**)

### 3. 起動する

- **Boot Dashboard**(左下のビュー。見当たらなければ **Window > Show View > Other... > Spring > Boot Dashboard**)で `java-dojo` を選んで ▶(Start)
- または `JavaDojoApplication.java`(`src/main/java/com/sayana/dojo/`)を右クリック > **Run As > Spring Boot App**

起動したら http://127.0.0.1:8080 を開きます。

### STS から起動するときの APIキー

ユーザー環境変数に設定した場合は、**STS を再起動**すれば読み込まれます。
STS の中だけで設定したい場合は、**Run > Run Configurations...** > 左の **Spring Boot App > JavaDojoApplication** > **Environment** タブ > **Add...** で
`ANTHROPIC_API_KEY` を追加します(この設定はワークスペースに保存され、リポジトリには入りません)。

### 開発時のヒント

- `spring-boot-devtools` 入りなので、Java を保存すると自動で再起動します。テンプレート(`templates/`)・CSS・JS は再起動なしで反映されます。
- 教材(`content/`)を編集したら、画面下の **「教材を再読み込み」** で反映できます。
- 演習は `scripts/verify-exercise.sh content/.../exercises/ex1`(Git Bash など)で単体確認できます。

---

## データの保存場所

| もの | 場所 | Git |
|---|---|---|
| 教材 | `content/`(Markdown + YAML + Java) | コミットする |
| 進捗・提出履歴・AIレビュー・会話 | `data/dojo.mv.db`(H2) | `.gitignore` 済み |
| APIキー | 環境変数 `ANTHROPIC_API_KEY` | ファイルには書かない |

学習記録をリセットしたいときは、アプリを止めて `data/` フォルダを消します。

---

## 構成

```
content/                 教材(部 → 章 → レッスン)
docs/PLAN.md             全体設計・カリキュラム・フェーズ計画
docs/CONTENT_FORMAT.md   教材の書式
src/main/java/com/sayana/dojo/
  content/   教材の読み込みと Markdown 描画
  exec/      JShell 実行と採点(別JVM + JUnit)
  progress/  進捗・提出・復習キュー(H2 + JPA)
  ai/        Claude API(チューター・ヒント・レビュー・エラー翻訳・類題生成)
  web/       画面(Thymeleaf + htmx)
src/main/resources/
  prompts/   AI のシステムプロンプト
  templates/ 画面
  static/    CSS / JS(htmx は同梱、CodeMirror・Mermaid・highlight.js は CDN)
```

### うまく動かないとき

| 症状 | 対処 |
|---|---|
| 「javac が見つかりません」「JShell を起動できませんでした」 | JRE で起動しています。JDK 21 で起動してください(STS は上の「JDK 21 を STS に登録する」) |
| 「教材ディレクトリが見つかりません」 | プロジェクトのルートで起動していません。`pom.xml` のあるフォルダで起動 |
| エディタがただのテキスト欄になる | CDN(esm.sh)に繋がらない状態です。そのままでも入力・実行・採点はできます(Tab でインデント、Ctrl+Enter で実行) |
| AI ボタンが押せない | `ANTHROPIC_API_KEY` が未設定です。設定してアプリ(STS)を再起動 |
| ポート 8080 が使用中 | 上の「ポートを変えたいとき」を参照 |
