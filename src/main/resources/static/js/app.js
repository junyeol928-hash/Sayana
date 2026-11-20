// Java道場のフロント側。htmx で差し込まれた部分にも同じ初期化をかける。
// CodeMirror / Mermaid / highlight.js は CDN から読む。読めなくても textarea のまま動く。

// 依存パッケージを全部固定して、@codemirror/state などが1つのインスタンスに揃うようにする
const CM_V = {
  state: "@codemirror/state@6.7.6", view: "@codemirror/view@6.43.14", language: "@codemirror/language@6.13.1",
  commands: "@codemirror/commands@6.11.1", lint: "@codemirror/lint@6.9.7", autocomplete: "@codemirror/autocomplete@6.20.3",
  search: "@codemirror/search@6.7.2", common: "@lezer/common@1.5.3", highlight: "@lezer/highlight@1.2.5", lr: "@lezer/lr@1.4.11",
  java: "@lezer/java@1.1.5",
};
const CM_DEPS = "?deps=" + Object.values(CM_V).join(",");
// esm.sh は ?deps= に並べた依存の組ごとに別のファイル(= 別のインスタンス)を配る。basicSetup の中で使われているものと同じに
// なるよう、view・language などには自分の依存だけを渡す(全部を渡すと別物になり、keymap の Ctrl+Enter や色付けの指定が
// 黙って無視されていた)
const cmUrl = (pkg, ...deps) => `https://esm.sh/${pkg}?deps=${deps.map((d) => CM_V[d]).join(",")}`;

let cm = null;            // CodeMirror のモジュール群(読み込めたら入る)
let activeView = null;    // 最後に触ったエディタ
// 図(Mermaid)とコードの色付けと書体がそろったら解ける。着地(前回の続き・読み直す・用語集の語)は、そのあとにもう一度やり直す。
// 図はエディタの読み込みのあとに描かれ、上の段が最大 1,000px ほど伸びるので、先に着いた所から流されていた(28 回目の審査)
let pageSettled = Promise.resolve();
let userMoved = false;
let resumeLanding = false; // 前回の続きの位置へ戻しているとき(段の頭へのハッシュの着地で上書きしない)
for (const t of ["wheel", "touchstart", "keydown", "mousedown"]) addEventListener(t, () => { userMoved = true; }, { once: true, passive: true });
/** 落ち着いたあとに着地をやり直す。その間に読む人が自分で動かしていたら、何もしない */
function afterSettle(fn) {
  pageSettled.then(() => requestAnimationFrame(() => { if (!userMoved) fn(); }));
}
let mermaidModule = null;

// スマホ・タブレット(指で操作する端末か、狭い画面)
const isTouch = () => window.matchMedia("(pointer: coarse), (max-width: 760px)").matches;

// スマホのキーボードで打ちにくい記号。押すとカーソル位置に入る
const SYMBOL_KEYS = [
  ["⇥", "    "], ["{", "{"], ["}", "}"], ["(", "("], [")", ")"], [";", ";"], ["\"", "\""],
  ["=", "="], ["+", "+"], ["<", "<"], [">", ">"], ["[", "["], ["]", "]"], [".", "."],
  ["!", "!"], ["&", "&"], ["|", "|"], ["'", "'"], ["%", "%"], ["_", "_"],
];

async function loadCodeMirror() {
  try {
    const [core, view, commands, javaLang, language, lezerHighlight] = await Promise.all([
      import("https://esm.sh/codemirror@6.0.2" + CM_DEPS),
      import(cmUrl(CM_V.view, "state")),
      import(cmUrl(CM_V.commands, "language", "state", "view", "common", "highlight", "lr")),
      import(cmUrl("@codemirror/lang-java@6.0.2", "language", "state", "view", "common", "highlight", "java", "lr")),
      import(cmUrl(CM_V.language, "state", "view", "common", "highlight", "lr")),
      import(cmUrl(CM_V.highlight, "common")),
    ]);
    // 注釈(@Component など)は読むコードと同じ青竹に。lezer の Java は注釈の名前に色の印を付けないので、ここで .tok-meta を
    // 付ける(文字列・コメントの中の @ は除く)
    // あわせて、lezer の Java が予約語の色を付ける文脈キーワード(module 宣言の外の to・with など)と this・var は、読むコード
    // (highlight.js)と同じく地の文字の色に戻す(.tok-plain。引数名の to まで縹に塗られていた。28 回目の審査)
    const { MatchDecorator, Decoration, ViewPlugin } = view;
    const metaMark = Decoration.mark({ class: "tok-meta" });
    const plainMark = Decoration.mark({ class: "tok-plain" });
    const CONTEXTUAL = /^(to|with|open|opens|module|requires|exports|uses|provides|transitive)$/;
    const metaMatcher = new MatchDecorator({
      regexp: /@[A-Za-z_][\w.]*|\b(?:to|with|open|opens|module|requires|exports|uses|provides|transitive|this|var)\b/g,
      decorate: (add, from, to, m, v) => {
        const node = language.syntaxTree(v.state).resolveInner(from, 1);
        if (/String|Comment|TextBlock/.test(node.name)) return;
        if (m[0].startsWith("@")) { add(from, to, metaMark); return; }
        // module 宣言の中の文脈キーワードだけは予約語のまま
        if (CONTEXTUAL.test(m[0])) for (let n = node; n; n = n.parent) if (/^Module/.test(n.name)) return;
        add(from, to, plainMark);
      },
    });
    const annotations = ViewPlugin.fromClass(class {
      constructor(v) { this.decorations = metaMatcher.createDeco(v); }
      // 構文木は少しずつ組まれるので、木が育ったら付け直す(開いた直後は木が無く、コメントの中の @Autowired まで色が付いた)
      update(u) {
        this.decorations = language.syntaxTree(u.startState) !== language.syntaxTree(u.state)
          ? metaMatcher.createDeco(u.view) : metaMatcher.updateDeco(u, this.decorations);
      }
    }, { decorations: (p) => p.decorations });
    cm = { basicSetup: core.basicSetup, EditorView: core.EditorView, keymap: view.keymap,
           indentWithTab: commands.indentWithTab, java: javaLang.java, annotations,
           syntaxHighlighting: language.syntaxHighlighting, classHighlighter: lezerHighlight.classHighlighter };
  } catch (e) {
    console.warn("CodeMirror を読み込めなかったので、普通のテキストエリアで動かします", e);
  }
}

function submitForm(form) {
  if (form) form.requestSubmit();
}

function initEditor(ta) {
  if (ta.dataset.ready) return;
  ta.dataset.ready = "1";
  if (!cm) {
    // フォールバック: Tab でインデント、Ctrl+Enter で送信
    ta.classList.add("plain-editor");
    ta.addEventListener("keydown", (e) => {
      if (e.key === "Tab") {
        e.preventDefault();
        const s = ta.selectionStart;
        ta.setRangeText("    ", s, ta.selectionEnd, "end");
      } else if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        submitForm(ta.form);
      }
    });
    ta.addEventListener("focus", () => { activeView = { text: () => ta.value }; });
    ta.setAttribute("autocapitalize", "off");
    ta.setAttribute("autocorrect", "off");
    addSymbolBar(ta, (text) => {
      ta.focus();
      ta.setRangeText(text, ta.selectionStart, ta.selectionEnd, "end");
    });
    return;
  }
  const { EditorView, basicSetup, keymap, indentWithTab, java, syntaxHighlighting, classHighlighter, annotations } = cm;
  const view = new EditorView({
    doc: ta.value,
    extensions: [
      // basicSetup より前に置く(同じ強さでは前のものが勝つ。後ろだと basicSetup の Mod-Enter = 空行を入れる、が勝った)
      keymap.of([
        { key: "Mod-Enter", run: () => { submitForm(ta.form); return true; } },
        indentWithTab,
      ]),
      basicSetup,
      java(),
      // 色付けはほかのコードの面と同じ(予約語は縹・文字列は刈安・注釈は青竹)。色は app.css の .tok-* と --syn-* で決める。
      // 地は両テーマとも暗いので、CodeMirror の暗い基本の組(補完の吹き出し・検索の欄)を使う
      syntaxHighlighting(classHighlighter),
      annotations,
      EditorView.darkTheme.of(true),
      EditorView.updateListener.of((u) => {
        if (u.docChanged) ta.value = u.state.doc.toString();
        if (u.focusChanged && u.view.hasFocus) activeView = { text: () => u.state.doc.toString(), view: u.view };
      }),
      // スマホの自動大文字化・自動修正で public が Public にならないように。読み上げには何の欄かと、Tab で外へ出る方法を伝える
      // (中の Tab は字下げなので、Esc のあと Tab で出る。案内の文は各ページの [data-editor-help]。28 回目の審査)
      EditorView.contentAttributes.of({ autocapitalize: "off", autocorrect: "off", spellcheck: "false", ...editorA11y(ta) }),
      ...(isTouch() ? [EditorView.lineWrapping] : []),
    ],
  });
  ta.style.display = "none";
  ta.parentNode.insertBefore(view.dom, ta);
  ta._cmView = view;
  addSymbolBar(ta, (text) => {
    view.dispatch(view.state.replaceSelection(text));
    view.focus();
  });
}

/** 編集欄の読み上げの名前(サンプル・演習の題)と、Tab で外へ出る方法の案内(近くの [data-editor-help])を指す属性 */
function editorA11y(ta) {
  const title = ta.getAttribute("aria-label")
    // 演習は題だけ(先頭の札「演習」「類題」は名前に入れない。サンプルと同じ「「題」のコード」の形。29 回目の審査)
    || (ta.closest(".exercise")?.querySelector("h3 span:not(.ex-label)")?.textContent.replace(/\s+/g, " ").trim() || "").replace(/^(.+)$/, "「$1」のコード")
    || "コード";
  const help = (ta.closest(".lesson-section") || document).querySelector("[data-editor-help]")?.id;
  return { "aria-label": title, ...(help ? { "aria-describedby": help } : {}) };
}

// エディタの上に記号キーの列を出す(スマホのときだけ CSS で表示)
function addSymbolBar(ta, insert) {
  const bar = document.createElement("div");
  bar.className = "symbol-bar";
  bar.setAttribute("aria-label", "記号を入力");
  for (const [label, text] of SYMBOL_KEYS) {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = label;
    b.title = label === "⇥" ? "インデント(空白4つ)" : label;
    // pointerdown で処理して、エディタのフォーカス(=キーボード)を外さない
    b.addEventListener("pointerdown", (e) => { e.preventDefault(); insert(text); });
    b.addEventListener("click", (e) => e.preventDefault());
    bar.appendChild(b);
  }
  const editorDom = ta._cmView ? ta._cmView.dom : ta;
  editorDom.parentNode.insertBefore(bar, editorDom);
}

function setEditorText(ta, text) {
  ta.value = text;
  const view = ta._cmView;
  if (view) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } });
  }
}

// 横にはみ出す帯に、続きがある側だけフェードの印を付ける(はみ出していないのにフェードが残ると汚れに見える)
function watchOverflow(scroller, target = scroller) {
  if (!scroller || scroller.dataset.watched) return;
  scroller.dataset.watched = "1";
  const update = () => {
    const max = scroller.scrollWidth - scroller.clientWidth;
    target.classList.toggle("has-more", max > 1 && scroller.scrollLeft < max - 1);
    target.classList.toggle("has-prev", max > 1 && scroller.scrollLeft > 1);
  };
  scroller.addEventListener("scroll", update, { passive: true });
  if (window.ResizeObserver) new ResizeObserver(update).observe(scroller);
  update();
}

// はみ出す図は、ノードの重心(中心の平均)が真ん中に来る位置から見せる。SVG の真ん中だと戻りの線の分だけ幹からずれ、
// 最初のノードだと横に出た枝(ループの本体など)が切れるため。「図の全体を見る」で縮めた全体と行き来できる
function fitFigures(nodes) {
  nodes.forEach((n) => {
    if (n.scrollWidth <= n.clientWidth + 1) return;
    const shapes = [...n.querySelectorAll("svg .node, svg .actor, svg .statediagram-state")];
    if (shapes.length) {
      const box = n.getBoundingClientRect();
      const mid = shapes.reduce((sum, el) => { const r = el.getBoundingClientRect(); return sum + r.left + r.width / 2; }, 0) / shapes.length;
      n.scrollLeft = Math.max(0, n.scrollLeft + mid - box.left - n.clientWidth / 2);
    }
    watchOverflow(n);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "fig-fit";
    btn.textContent = "図の全体を見る";
    btn.addEventListener("click", () => {
      const fit = n.classList.toggle("fit");
      btn.textContent = fit ? "文字の大きさに戻す" : "図の全体を見る";
      n.dispatchEvent(new Event("scroll"));
    });
    n.after(btn);
  });
}

// 手描きの SVG の図(.figure)も、はみ出す分は横スクロールにし、続きがある側だけフェードと「図の全体を見る」を付ける
// (Mermaid だけにしていたので、スマホで手描きの図 34 のうち 33 が右の 3 割を手がかりなく切られていた。27 回目の審査)。
// 図だけを包みに入れ、説明文は包みの外に残す(横に送っても読めるように)。畳みの中の図は開いたときに測り直す(watchOverflow)
function fitSvgFigures(root) {
  root.querySelectorAll("figure.figure > svg").forEach((svg) => {
    const wrap = document.createElement("div");
    wrap.className = "fig-scroll";
    svg.before(wrap);
    wrap.append(svg);
    watchOverflow(wrap);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "fig-fit";
    btn.textContent = "図の全体を見る";
    btn.addEventListener("click", () => {
      const fit = wrap.classList.toggle("fit");
      btn.textContent = fit ? "文字の大きさに戻す" : "図の全体を見る";
      wrap.dispatchEvent(new Event("scroll"));
    });
    wrap.after(btn);
  });
}

async function renderMermaid(root) {
  const blocks = root.querySelectorAll("pre > code.language-mermaid");
  if (blocks.length === 0) return;
  blocks.forEach((code) => {
    const div = document.createElement("div");
    div.className = "mermaid";
    div.textContent = code.textContent;
    code.parentElement.replaceWith(div);
  });
  try {
    if (!mermaidModule) {
      mermaidModule = (await import("https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs")).default;
    }
    mermaidModule.initialize(mermaidTheme());
    const nodes = [...root.querySelectorAll(".mermaid:not([data-processed])")];
    await mermaidModule.run({ nodes });
    fitFigures(nodes);
  } catch (e) {
    console.warn("Mermaid を描画できませんでした", e);
  }
}

// 図も藍の配色で描く(CSS の変数から色を読むので、暗い表示でもそろう)。スマホ版の app.js と同じ
function mermaidTheme() {
  const css = getComputedStyle(document.documentElement);
  // Mermaid は oklch() を読めない(「Unsupported color format」で図ごと描けなくなる)ので、色は 1 点塗って #rrggbb に直して渡す
  const ctx = document.createElement("canvas").getContext("2d", { willReadFrequently: true });
  const v = (name) => {
    const raw = css.getPropertyValue(name).trim();
    if (!raw.startsWith("oklch") || !ctx) return raw;
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = raw;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data;
    return "#" + [r, g, b].map((n) => n.toString(16).padStart(2, "0")).join("");
  };
  return {
    startOnLoad: false, securityLevel: "strict", theme: "base",
    themeVariables: {
      fontFamily: v("--font"), fontSize: "14px", background: "transparent",
      primaryColor: v("--surface"), primaryBorderColor: v("--line-strong"), primaryTextColor: v("--ink"),
      secondaryColor: v("--accent-soft"), tertiaryColor: v("--surface-2"), lineColor: v("--accent"),
      textColor: v("--ink"), mainBkg: v("--surface"), nodeBorder: v("--line-strong"), clusterBkg: v("--surface-2"),
      clusterBorder: v("--line"), edgeLabelBackground: v("--surface-2"), actorBkg: v("--surface"), actorBorder: v("--line-strong"),
      noteBkgColor: v("--ki-soft"), noteBorderColor: v("--line"),
    },
    // 狭い画面では図を縮めない(縮めると文字が 8px ほどになる)。14px のまま組み、はみ出す分は横にスクロールする
    ...(matchMedia("(max-width: 760px)").matches ? {
      flowchart: { useMaxWidth: false, wrappingWidth: 150, nodeSpacing: 20, rankSpacing: 28, padding: 8 }, sequence: { useMaxWidth: false }, class: { useMaxWidth: false },
      er: { useMaxWidth: false }, state: { useMaxWidth: false },
    } : {}),
  };
}

// 英語の文らしいか: Java の予約語でない英単語が 3 つ続くか、先頭が 2 語(「Cannot invoke」「cannot find」「Square is」)。
// 型と引数名(`String pattern`)は語の後ろに「,」「)」が付くので数えない
const JAVA_WORDS = new Set(("public private protected static final abstract void int long double float boolean byte char short var new "
  + "return if else for while do switch case default try catch finally throw throws class interface record enum extends implements "
  + "super this null true false instanceof import package").split(" "));
// 1 行のブロックにするメッセージらしいもの: 24 字以上で空白を含み、文や宣言の記号(; { } = && || ->)が無い。
// 呼び出しの括弧があるものは、英語の文らしいときだけ
const msgLike = (t) => t.length >= 24 && /\s/.test(t.trim()) && !/[;{}=]|&&|\|\||->/.test(t) && (!/\w\(/.test(t) || proseLike(t));
// 本文で 1 行のブロックにするもの: 英語の文らしいもの・大文字だけの文(APPLICATION FAILED TO START)・エラーの 1 行
// (`…Exception: …`・Caused by・Resolved)。宣言・総称型・キャスト(extends・implements・permits・<T・先頭の「(」)は除く
const ERROR_LINE = /\b[A-Z]\w*(?:Exception|Error)\b:|^(?:Caused by|Resolved|WARN|ERROR|Exception in thread)\b/;
const DECLARATION = /\s=\s|;\s*$|\b(?:extends|implements|instanceof|permits)\b|\bnew [A-Z]\w*[(<\[]|<[A-Z?]|^\(|^(?:(?:public|private|protected|static|final|abstract|sealed)\s+)*(?:class|interface|record|enum)\b/;
const bodyMsg = (t) => t.length >= 24 && /\s/.test(t.trim()) && !DECLARATION.test(t)
  && (proseLike(t) || /^[A-Z]{2,}(?: [A-Z]{2,}){2,}$/.test(t.trim()) || ERROR_LINE.test(t));
function proseLike(t) {
  const words = t.trim().split(/\s+/);
  const plain = (w) => /^[A-Za-z][a-z]+(?:-[a-z]+)*$/.test(w) && !JAVA_WORDS.has(w);
  let run = 0;
  for (const w of words) {
    run = plain(w) ? run + 1 : 0;
    if (run >= 3) return true;
  }
  return plain(words[0]) && plain(words[1] || "");
}

function highlight(root) {
  if (!window.hljs) return;
  // 読むためのコードは折り返す(スマホ版と同じ範囲)
  root.querySelectorAll(".quiz-q .md pre, .q-explain pre, .quiz-review pre, .review-quiz pre, .quiz-answer pre, .reading-answer pre, .check pre").forEach((pre) => pre.classList.add("code-lines"));
  root.querySelectorAll("pre.code-lines > code:not([data-lines])").forEach(numberLines);
  // 短いコード(case 3、i++ など)は語の途中で行をまたがせない(長いものは折り返す)
  // 16 字までのチップは折らない(「javac App.java」が空白で 2 行に割れていた)
  root.querySelectorAll(":not(pre) > code").forEach((c) => { if (c.textContent.length <= 16) c.classList.add("nb"); });
  // 8 字までの強調の語は語の途中で行をまたがせない(「ジャ / ンプ」「コンポー / ネントスキャン」と割れていた。27 回目の審査)
  root.querySelectorAll(".md strong").forEach((b) => { if (b.textContent.trim().length <= 8) b.classList.add("nb"); });
  inlineNotes(root);
  // 出力・識別子だけの選択肢(X3、IndexOutOfBoundsException など)はコードの字体で。
  // 同じ問いに ASCII だけの選択肢が 2 つ以上あれば、語の数によらず全部コードの字体に
  // (「0 3 6 9」は本文、「3 6 9」はコード、と同じ問いの中で書体が分かれ、1 つだけ形の違う選択肢が手がかりになっていた)
  const asciiChoices = new Map();
  root.querySelectorAll(".choice > span").forEach((sp) => {
    if (sp.querySelector("code") || !/^[!-~][ -~]*$/.test(sp.textContent.trim())) return;
    const group = sp.closest(".choice").parentElement;
    asciiChoices.set(group, [...(asciiChoices.get(group) || []), sp]);
  });
  asciiChoices.forEach((spans) => spans.forEach((sp) => {
    if (spans.length >= 2 || !/\s\S+\s\S+\s/.test(sp.textContent.trim())) sp.classList.add("code-choice");
  }));
  // 解説の中の長いメッセージ・出力(エラー文など)は、文の中のチップにせず独立した 1 行のブロックに(検索窓に貼る文なので途切れさせない)
  root.querySelectorAll(".explanation :not(pre) > code, .reading-answer :not(pre) > code, .q-explain :not(pre) > code").forEach((c) => {
    const t = c.textContent;
    // コードの式・宣言・呼び出し(; { } = や「名前(」、&& || ->)はチップのまま(段落にすると、文が 3 つに割れた。24 回目の審査)。
    // ただし英語の文らしいもの(`Cannot invoke "String.hashCode()"`・`method calc(int) is already defined`)は、
    // 呼び出しの括弧があってもメッセージ
    if (msgLike(t)) c.classList.add("code-msg");
  });
  // 本文の段落・箇条書き(STS の手順・コラム・演習の問題も)の長いメッセージも 1 行のブロックに(チップのまま 3〜6 本の帯に割れていた。
  // 25・26 回目の審査)。ただし本文では、英語の文らしいものとエラーの 1 行だけ(宣言・総称型・キャスト・式はチップのまま。
  // ブロックにすると文が 3 つに割れた)。括弧の中の補足・直後が「。、」・表の中はチップのまま(「(」が行末に、「。」が行頭に残る)
  root.querySelectorAll("p > code:not(.code-msg), li > code:not(.code-msg)").forEach((c) => {
    if (!bodyMsg(c.textContent) || c.closest(".explanation, .reading-answer, .q-explain, .choice, td, th, h1, h2, h3, .missed-list")) return;
    const after = c.nextSibling?.textContent || "";
    if (/^[\u2060]?[、。，．]/.test(after)) return;
    // 後ろが「です。」「を読む。」だけで文が終わるもの、「・」の並びの中の 1 つもチップのまま
    // (ブロックの下に述語だけの行が残り、並びの中の 1 つだけが行を占めた。27 回目の審査)
    if (/^[\u2060]?\s*[^\s。、]{1,5}。/.test(after) || /^[\u2060]?\s*・/.test(after)) return;
    let before = "";
    for (let n = c.previousSibling; n; n = n.previousSibling) before = n.textContent + before;
    if (/・\s*$/.test(before)) return;
    const open = (before.match(/[(（]/g) || []).length - (before.match(/[)）]/g) || []).length;
    // 組んだあとで、チップのままだと 2 行以上に割れるものだけブロックに(1 行に収まる短いものまでブロックにすると、
    // 前後の文が細切れになった。26 回目の審査)
    if (open <= 0) requestAnimationFrame(() => { if (c.getClientRects().length >= 2) c.classList.add("code-msg"); });
  });
  // チップの前後の禁則: 直後の「、。)」が次の行の頭に、直前の「(」が行末に残らないよう、語をつなぐ印(U+2060)で結ぶ
  // (コピーでは下の copy で取り除く)。括弧に接する側はチップの内側の余白を詰める(CSS の after-open など)
  root.querySelectorAll(":not(pre) > code:not(.code-msg)").forEach((c) => {
    const prev = c.previousSibling;
    const next = c.nextSibling;
    if (prev?.nodeType === 3 && /[(（「『【\[]$/.test(prev.data) && !prev.data.endsWith("\u2060")) {
      prev.data += "\u2060";
      if (/[（「『【]\u2060$/.test(prev.data)) c.classList.add("after-open");
      else if (/\(\u2060$/.test(prev.data)) c.classList.add("after-paren");
    }
    if (next?.nodeType === 3 && /^[、。，．,.)）」』】\]:;!?]/.test(next.data)) {
      if (/^[）」』】]/.test(next.data)) c.classList.add("before-close");
      else if (/^\)/.test(next.data)) c.classList.add("before-paren");
      else if (/^[、。，．]/.test(next.data)) c.classList.add("before-punct"); // 「`true` 。」と句点の前が 1 字近く空いていた
      next.data = "\u2060" + next.data;
    }
    if (next?.nodeType === 3 && /^[(（「『【]/.test(next.data)) c.after(document.createElement("wbr"));
  });

  // 本文の見出しでは、英字の名前・閉じ括弧・チップの直後の助詞(ひらがな)が行頭に来ないようにつなぐ
  // (「SpringApplication.run / で何が起きるか」「「Javaを入れる」/ って」と、太い見出しの行が助詞で始まっていた。27 回目の審査)。
  // 間の空白は改行しない空白に、空白が無ければ語をつなぐ印(U+2060)に
  const joinParticle = (_, head, space) => head + (space ? "\u00a0" : "\u2060");
  root.querySelectorAll(".md h2, .md h3").forEach((h) => {
    const walker = document.createTreeWalker(h, NodeFilter.SHOW_TEXT);
    const texts = [];
    for (let n = walker.nextNode(); n; n = walker.nextNode()) if (!n.parentElement.closest("code")) texts.push(n);
    for (const n of texts) {
      const prev = n.previousSibling;
      if (prev?.nodeName === "CODE") n.data = n.data.replace(/^[\u2060]?( ?)(?=[ぁ-ゖ])/, (_, space) => (space ? "\u00a0" : "\u2060"));
      n.data = n.data.replace(/([A-Za-z0-9_.)\]」』）])( ?)(?=[ぁ-ゖ])/g, joinParticle);
    }
  });
  // 選択肢の中身が 1 つのコードだけなら、チップの地を外してコードの書体だけに(地のある・無いの 2 通りが復習で続けて出た)
  root.querySelectorAll(".choice > span").forEach((sp) => {
    const only = sp.children.length === 1 && sp.firstElementChild.tagName === "CODE" ? sp.firstElementChild : null;
    if (only && sp.textContent.trim() === only.textContent.trim()) { sp.textContent = only.textContent; sp.classList.add("code-choice"); }
  });
  // 本文の表: 3 列までは、狭い画面で行ごとの積みにする(左の列が行の見出し、ほかの列は見出しを添えて全幅で)。横に送る表は 4 列から
  root.querySelectorAll(".md table").forEach((table) => {
    const heads = [...table.querySelectorAll("thead th")].map((th) => th.textContent.trim());
    if (!heads.length || heads.length > 3) return;
    table.classList.add("stack");
    table.querySelectorAll("tbody tr").forEach((tr) => [...tr.children].forEach((td, i) => { if (heads[i]) td.dataset.th = heads[i]; }));
  });

  root.querySelectorAll("pre code:not([data-highlighted]):not([data-lines])").forEach((el) => {
    // 仕事のコードの行の説明(コードの中に差し込んだ文)のチップは色付けしない(塊になり、説明が全幅の切れ端に割れた。25 回目の審査)
    if (el.closest(".ln-note")) return;
    if (!el.className.includes("language-")) el.classList.add("language-java");
    if (el.classList.contains("language-text")) return;
    window.hljs.highlightElement(el);
  });
}

// 動きを減らす設定の人には、スクロールも瞬時に
const reduceMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const escHtml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

/** コードを 1 行ずつの要素に分け、行番号を付ける(スマホ版 app.js の numberLines と同じ) */
// 和文の字(かな・漢字・全角の記号)。コメントの中で、和文の語の後ろの空白だけを折り返しの候補に残す
const JA_CHAR = /[\u3000-\u30ff\u3400-\u9fff\uff00-\uffef]/;
// s[i] の空白で折ってよいか: 和文の字か、和文の直後の半角の「?」「!」「:」「)」の後ろ(後ろが「/」なら除く)と、
// その後ろの「 / 」の後ろ(「どうなる? / 」)。英数字だけのコメント(// a = 2;)の中は今までどおり切らない
function jaSpace(s, i) {
  const ja = (k) => JA_CHAR.test(s[k] || "") || ("?!:)".includes(s[k] || "x") && JA_CHAR.test(s[k - 1] || ""));
  if (s[i + 1] !== "/" && ja(i - 1)) return true;
  return s[i - 1] === "/" && s[i - 2] === " " && ja(i - 3);
}

function numberLines(code) {
  // 400px 以下は字下げを半分にして 1 行の字数を稼ぐ(型と名前が折り返しで離れないように。行番号の列を詰めるより効く)
  const narrow = matchMedia("(max-width: 400px)").matches;
  const text = code.textContent.replace(/\n$/, "").replace(/^ +/gm, (m) => (narrow ? " ".repeat(Math.ceil(m.length / 2)) : m));
  const html = window.hljs ? window.hljs.highlight(text, { language: "java", ignoreIllegals: true }).value : escHtml(text);
  const notes = new Set((code.parentElement.dataset.notes || "").split(",").filter(Boolean).map(Number));
  const lines = [];
  const open = [];
  let cur = "";
  for (const part of html.split(/(<span[^>]*>|<\/span>|\n)/)) {
    if (part === "\n") { lines.push(cur + "</span>".repeat(open.length)); cur = open.join(""); }
    else if (part.startsWith("<span")) { open.push(part); cur += part; }
    else if (part === "</span>") { open.pop(); cur += part; }
    // 狭い画面で折り返すときは . の前、( と , の後ろで切る(識別子の途中や「()」の間で切らない)。
    // 短い名前の直後の . では切らない(q と .size() が離れると、何の呼び出しか追い直すことになる)
    // コメントの中は折り返しの候補を入れず、空白でも切らない(コメントの範囲を読ませる問題で、割れ方が答えに見えないように)。
    // ただし和文の語の後ろの空白(「のままだと NullPointerException」)だけは切ってよい。全部つなぐと、収まらない分を
    // 語の途中で割っていた(「のままだ / と」)。和文の中は CSS の auto-phrase(無ければ keep-all)で文節の切れ目だけで折る
    else if (open.some((o) => o.includes("hljs-comment"))) cur += part.replace(/ /g, (m, i, s) => (jaSpace(s, i) ? " " : "\u00a0"));
    // 添字の連なり(t[2][1])は「][」で割らない(語をつなぐ印 U+2060 を挟む。コピーのときは下の copy で取り除く)
    else cur += part.replace(/(\w{4,}|\))\./g, "$1<wbr>.").replace(/\((?!\))/g, "(<wbr>").replace(/,/g, ",<wbr>").replace(/\]\[/g, "]\u2060[");
  }
  lines.push(cur);
  // 行末の「 {」は前の語から離さない(「{」だけが次の行に落ちて、行の数が増えて見えないように)
  for (let i = 0; i < lines.length; i++) lines[i] = lines[i].replace(/ (\{(?:<\/span>)*)\s*$/, " $1");
  // 折り返した続きは、その行の字下げ + 2 文字の位置から始める(どこからが続きか見分けられるように)
  const indent = text.split("\n").map((t) => t.match(/^[ \t]*/)[0].replace(/\t/g, "    ").length);
  code.innerHTML = lines.map((l, i) => `<span class="ln${notes.has(i + 1) ? " note" : ""}" data-n="${i + 1}" style="--ind:${indent[i] || 0}">${l || " "}</span>`).join("");
  code.dataset.lines = "1";
  code.classList.add("hljs");
}

/** 仕事のコードの説明を、その行の直後に挟む(スマホ版 app.js の inlineNotes と同じ) */
function inlineNotes(root) {
  root.querySelectorAll(".atwork").forEach((art) => {
    const pre = art.querySelector("pre.code-lines");
    const list = art.querySelector(".line-notes");
    if (!pre || !list || pre.dataset.inline || !pre.querySelector(".ln")) return;
    list.querySelectorAll("li").forEach((li) => {
      const n = parseInt(li.querySelector(".pin-label").textContent, 10);
      const line = pre.querySelector(`.ln[data-n="${n}"]`);
      if (line) line.insertAdjacentHTML("afterend", `<span class="ln-note">${li.querySelector("span:last-child").innerHTML}</span>`);
    });
    pre.dataset.inline = "1";
    list.classList.add("has-inline");
  });
  applyNotesFold(root);
}

// 「説明をたたむ」: 行の間の説明を隠して、コードだけを通して読む(下に説明の一覧を出す)。選んだ形はこのブラウザに覚える
const FOLD_KEY = "javadojo-notes-folded";
function applyNotesFold(root) {
  let folded = false;
  try { folded = localStorage.getItem(FOLD_KEY) === "1"; } catch (e) { /* 覚えられなくても動く */ }
  root.querySelectorAll(".atwork").forEach((art) => {
    art.classList.toggle("notes-folded", folded);
    const b = art.querySelector("[data-notes-toggle]");
    if (b) { b.setAttribute("aria-pressed", String(folded)); b.textContent = folded ? "説明を行の間に出す" : "説明をたたむ"; }
  });
}
document.addEventListener("click", (e) => {
  if (!e.target.closest("[data-notes-toggle]")) return;
  const folded = !e.target.closest(".atwork").classList.contains("notes-folded");
  try { localStorage.setItem(FOLD_KEY, folded ? "1" : "0"); } catch (err) { console.warn("たたむ設定を保存できませんでした", err); }
  applyNotesFold(document);
});

// 染めの名前の読み(スマホ版 app.js の DYE の read と同じ)
const DYE_READ = { 生成り: "きなり", 甕覗き: "かめのぞき", 縹: "はなだ", 藍: "あい", 褐: "かち" };
const LAST_KEY = "javadojo-pc-last";
// 段の名前は記録せず、表示のたびに段の id から引く(名前を変えても古い名前が残らないように)。スマホ版 app.js と同じ表
const SECTION_LABEL = { read: "読む", atwork: "仕事のコード", run: "サンプル", sts: "STSで試す", write: "演習", reading: "読解", quiz: "クイズ" };
const RESUME_KEY = "javadojo-pc-resume";
const SECTIONS_KEY = "javadojo-pc-sections";
// 読解・用語集からレッスンをのぞき見したときの、押したリンクとその画面の上からの高さ(戻ったら一覧のその所へ)
const PEEK_RETURN_KEY = "javadojo-pc-peek-return";
function pcStore(key) {
  try { return JSON.parse(localStorage.getItem(key)) || {}; } catch (e) { return {}; }
}
function pcSave(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { console.warn("記録を保存できませんでした", e); }
}

// ---- 顔(ホーム)。スマホ版 app.js の titleSize・stepStates・unshin と同じ
function titleSize(title) {
  const width = [...title].reduce((w, c) => w + (c.charCodeAt(0) < 0x2000 ? 0.55 : 1), 0);
  return width <= 8 ? "size-xl" : width <= 14 ? "size-l" : "size-m";
}
// 一覧の行の data-*(サーバーの記録)に、このブラウザの記録(仕事のコードを読んだ・読解を全問)を足す
function stepStates(row) {
  const d = row.dataset;
  const id = d.lesson;
  const done = new Set((d.done || "").split(" ").filter(Boolean));
  (pcStore(SECTIONS_KEY)[id] || []).forEach((k) => { if (k === "atwork" || k === "run") done.add(k); });
  const rec = readingRecord();
  const total = Number(d.reading || 0);
  if (total > 0 && Array.from({ length: total }, (_, i) => rec[`${id}@${i}`]?.ok).every(Boolean)) done.add("reading");
  return (d.steps || "read quiz").split(" ").map((key) => ({ key, done: done.has(key) }));
}
// 運針(うんしん): 段を刺し子の針目で見せる。済んだ段は糸、いまの段は玉結び、まだの段は針穴
/** 今日はじめての表示か(ホームの運針を 1 回だけ縫う)。記録はこのブラウザの中だけ */
// 同じ画面で運針を描き直しても(PC は前回の続きで差し替える)、その表示の間は同じ答えを返す
let sewToday;
function firstOpenToday() {
  if (sewToday !== undefined) return sewToday;
  sewToday = false;
  const key = "javadojo-sewn-day";
  const d = new Date().toLocaleDateString("sv");
  try {
    if (localStorage.getItem(key) === d) return false;
    localStorage.setItem(key, d);
    return (sewToday = true);
  } catch (e) {
    return false;
  }
}

function unshin(steps, nowKey) {
  const n = steps.filter((st) => st.done).length;
  const label = `${steps.length} 段のうち ${n} 段済み${nowKey ? `。次は${SECTION_LABEL[nowKey]}` : ""}`;
  // その日はじめて開いたときだけ、済んだ段の糸を左から縫い、最後に朱の玉結びを染み込ませる(1 秒以内。動きの種類は縫う・染みるの 2 つのまま)
  const sew = firstOpenToday();
  return `<div class="unshin${sew ? " sewing" : ""}" role="img" aria-label="${label}" style="--steps: ${steps.length}">${steps.map((st, i) =>
    `<i class="${st.key === nowKey ? "now" : st.done ? "sewn" : ""}" style="--i: ${i}"></i>`).join("")}</div>
    <p class="unshin-cap" aria-hidden="true"><span>${steps.length} 段のうち <b>${n} 段</b>済み</span>${nowKey ? `<span>次は <b>${SECTION_LABEL[nowKey]}</b></span>` : ""}</p>`;
}
// 大きな題は語の途中で折らない(「コンテ/ナ」にならないように)。語ごとに折り返さない箱に入れ、助詞・記号は前の語につなぐ。
// word-break: auto-phrase が効かないブラウザでも同じに見えるように、Intl.Segmenter で分ける
/**
 * 大きな題が箱からはみ出さないようにする。大きさは字数の 3 段(titleSize)で決めるが、折らない 1 語(phraseWrap の .phrase)が
 * 長いと、「ポリモーフィズム」「トランザクション」のように 56px のまま札の外へ出て、ホームの幅まで広がった(27 回目の審査)。
 * 組んだあとで、はみ出す語があれば 1 段ずつ下げ、いちばん小さい段でも入らない語だけ折る(欠けるより折れる方がよい)
 */
function fitTitles() {
  const TITLE_STEPS = ["size-xl", "size-l", "size-m"]; // 関数の中に(描画が定義の行より先に走っても使えるように)
  document.querySelectorAll(".today-title, main h1:not(.today-title)").forEach((h) => {
    const phrases = [...h.querySelectorAll(".phrase")];
    if (!phrases.length) return;
    if (!h.dataset.size0) h.dataset.size0 = TITLE_STEPS.find((c) => h.classList.contains(c)) || "";
    let i = TITLE_STEPS.indexOf(h.dataset.size0);
    if (i >= 0) h.classList.remove(...TITLE_STEPS), h.classList.add(TITLE_STEPS[i]);
    phrases.forEach((p) => p.classList.remove("phrase-break"));
    const over = (p) => p.scrollWidth > p.clientWidth + 1;
    while (i >= 0 && i < TITLE_STEPS.length - 1 && phrases.some(over)) {
      h.classList.replace(TITLE_STEPS[i], TITLE_STEPS[i + 1]);
      i++;
    }
    phrases.forEach((p) => { if (over(p)) p.classList.add("phrase-break"); });
  });
}
addEventListener("resize", fitTitles);

function phraseWrap(el) {
  if (!el || el.dataset.phrased || el.children.length || !("Segmenter" in Intl)) return; // 中に要素がある見出しは崩さない
  const words = [];
  for (const { segment, isWordLike } of new Intl.Segmenter("ja", { granularity: "word" }).segment(el.textContent)) {
    if (words.length && (!isWordLike || /^[\u3041-\u309f]{1,2}$/.test(segment) || /[(「『]$/.test(words[words.length - 1]))) words[words.length - 1] += segment;
    else words.push(segment);
  }
  const latin = (c) => /[\x21-\x7e]/.test(c || "");
  el.innerHTML = words.map((w, i) => `<span class="phrase${i < words.length - 1 && latin(w.slice(-1)) !== latin(words[i + 1][0]) && !/\s$/.test(w) ? " gap" : ""}">${escHtml(w).replace(/ /g, "\u00a0").replace(/ー/g, '<span class="choon">ー</span>')}</span>`).join("");
  el.dataset.phrased = "1";
}
function drawUnshin(row, nowKey) {
  const slot = document.querySelector(".today .unshin-slot");
  if (!slot || !row) return;
  const steps = stepStates(row);
  slot.innerHTML = unshin(steps, nowKey || steps.find((st) => !st.done)?.key);
}

/** ダッシュボード: 前回の続きがあれば主役にし、新しいレッスンは控えめに添える。読解の正解数も出す */
function dashboardResume() {
  const ok = document.getElementById("reading-ok");
  const readingOk = Object.values(readingRecord()).filter((r) => r.ok).length;
  if (ok) ok.textContent = readingOk;
  // 読解の正解は糸の長さで(全問に対する割合)
  const thread = document.getElementById("reading-thread");
  if (thread && Number(thread.dataset.total)) thread.style.setProperty("--p", `${Math.round((readingOk / Number(thread.dataset.total)) * 100)}%`);
  const hero = document.querySelector(".today");
  const slot = document.querySelector(".today .unshin-slot");
  if (slot) drawUnshin(document.querySelector(`.lesson-row a[data-lesson="${CSS.escape(slot.dataset.lesson)}"]`));
  try {
    const last = pcStore(LAST_KEY);
    const row = last.id && document.querySelector(`.lesson-row a[data-lesson="${CSS.escape(last.id)}"]`);
    if (!hero || !row || last.end || !hero.querySelector(".today-title a")) return;
    const title = hero.querySelector(".today-title a");
    const href = `/lessons/${encodeURIComponent(last.id)}#${last.section || "read"}`;
    const btn = hero.querySelector(".today-actions a");
    btn.href = href;
    const label = SECTION_LABEL[last.section];
    // 前回いた段がもう済んでいたら、主ボタンは次のまだの段へ(「次:」の行と同じ段を指す)。染めの段で済んだかを見る
    const rd = row.dataset;
    const dye = Number(rd.dye);
    // 済んだかは運針と同じ表(stepStates: サーバーの記録 + このブラウザの仕事のコード・サンプル・読解)と染めの段で決める。
    // 済んでいれば、次のまだの段へ(スマホ版の nextSectionFor と同じ形。26・27 回目の審査)
    const states = stepStates(row);
    const doneBy = Object.fromEntries(states.map((st) => [st.key, st.done]));
    if (dye >= 1) doneBy.read = true;
    if (dye >= 2) doneBy.quiz = true;
    if (dye >= 3) doneBy.write = true;
    const keys = states.map((st) => st.key).filter((k) => k !== "sts"); // STS で試すは PC で手を動かす段なので、主ボタンでは指さない
    const go = doneBy[last.section]
      ? keys.slice(keys.indexOf(last.section) + 1).find((k) => !doneBy[k]) || last.section
      : last.section;
    // 「クイズの続きから」(括弧書きのラベルは硬いので、言葉でつなぐ)。スマホ版 app.js の resumeLabel と同じ
    btn.textContent = go !== last.section ? `${SECTION_LABEL[go]}へ進む` : !label ? "続きから" : label === "読む" ? "続きを読む" : `${label}の続きから`;
    if (go !== last.section) {
      btn.href = `/lessons/${encodeURIComponent(last.id)}#${go}`;
      hero.querySelector(".today-actions").insertAdjacentHTML("beforeend", `<a class="today-alt" data-back href="${href}">前回の位置(${escHtml(label)})へ戻る</a>`);
    }
    drawUnshin(row, go);
    // 染まりの地図の朱の印も、前回の続きのマスへ
    document.querySelectorAll(".dye-cell.is-here").forEach((c) => c.classList.remove("is-here"));
    document.querySelector(`.dye-cell[data-lesson="${CSS.escape(last.id)}"]`)?.classList.add("is-here");
    const here = document.querySelector(".dye-map-here");
    if (here) here.textContent = "前回の続き";
    // レッスンを開いたら、読んでいた位置へ戻す(レッスン側が読む)
    // 次の段へ進むボタンは段の先頭へ(位置は戻さない)
    [go === last.section ? btn : null, title, hero.querySelector("[data-back]")].filter(Boolean)
      .forEach((a) => a.addEventListener("click", () => sessionStorage.setItem(RESUME_KEY, last.id)));
    if (title.getAttribute("href").endsWith("/" + last.id)) return;
    const nextHref = title.getAttribute("href");
    const nextTitle = title.textContent;
    // 主役を前回の続きに差し替える。所属・時間・いまの染めと次の条件も、そのレッスンの値で組み立て直す
    const d = row.dataset;
    // 見出しの行は、主ボタンが指す段の量を言う(スマホ版の kicker と同じ。演習へ進む日に「読む 18 分」と言わない)
    const kicker = { quiz: "クイズ 5 問", write: `演習 ${d.exercises} 問`, reading: `読解 ${d.reading} 問`,
      atwork: "仕事のコードから", run: "サンプルから", sts: "STSで試す" }[go] || `読む ${d.minutes} 分`;
    hero.querySelector(".today-kicker").innerHTML = `<span>前回の続き</span><span class="visually-hidden">${escHtml(d.chapter)}</span><span>${escHtml(kicker)}</span>`;
    const [axisPart, axisChapter] = (d.axis || "|").split("|");
    const axis = hero.querySelector(".today-axis");
    if (axis) axis.innerHTML = `<span>${escHtml(axisPart)}</span><small>${escHtml(axisChapter)}</small>`;
    title.href = href;
    title.textContent = last.title;
    title.closest(".today-title").classList.remove("size-xl", "size-l", "size-m");
    title.closest(".today-title").classList.add(titleSize(last.title));
    slot.dataset.lesson = last.id;
    const next = hero.querySelector(".today-next");
    const [dyeName, dyeMeaning] = d.dyeLabel.split("・");
    if (next && d.hint) next.innerHTML = `<span class="dye dye-${d.dye}"></span><span><span class="tn-now"><ruby>${escHtml(dyeName)}<rt>${DYE_READ[dyeName] || ""}</rt></ruby>(${escHtml(dyeMeaning)})</span><span class="tn-hint">${escHtml(d.hint)}</span></span>`;
    else next?.remove();
    hero.querySelector(".today-actions").insertAdjacentHTML("afterend", `<a class="today-alt" href="${nextHref}">次の新しいレッスン: ${escHtml(nextTitle)}</a>`);
    // 先輩の一言も、続きの話に
    const talk = hero.querySelector(".talk-line");
    // 前回いた段が済んでいて次の段へ進む日は、そちらを言う(主ボタンと別の段を言わない)
    if (talk) talk.textContent = go !== last.section ? `モカ先輩「${label || "前回の段"}は済んだね。次は『${SECTION_LABEL[go]}』へ」`
      : `モカ先輩「続きは${label ? `『${label}』` : ""}から。前に読んでいた所へ戻るよ」`;
    // 一覧の札もヒーローと同じ言葉に(強調は主役の 1 行だけ)
    document.querySelectorAll(".lesson-row.is-next").forEach((li) => {
      li.classList.remove("is-next");
      const b = li.querySelector(".row-title b");
      if (b) b.textContent = "次の新しいレッスン";
    });
    const li = row.closest(".lesson-row");
    li.classList.add("is-next");
    li.querySelector(".row-title").insertAdjacentHTML("afterbegin", "<b>前回の続き</b>");
  } finally {
    document.body.classList.remove("resume-pending");
  }
}

/**
 * レッスンの冒頭の縦書きの軸に、部と章が入りきるようにする。入らなければ章を軸から外して現在地の行に出し、
 * まだ入らなければ字間を詰め、それでも入らなければ冒頭の高さを軸の字の長さまで伸ばす
 * (軸の高さは本文に合わせているので、本文の短いレッスンでは字が 2 列目へ折れ、「礎」だけが左に残った。26 回目の審査)
 */
function fitAxis(ax) {
  if (!ax) return;
  const hero = ax.closest(".lesson-hero");
  ax.classList.remove("part-only", "tight");
  if (hero) hero.style.minHeight = "";
  // 縦書きの字は軸の幅の中で 2 列目に回る(はみ出しにはならない)ので、部と章の字の列の数で見分ける
  const cols = (el) => {
    if (!el || getComputedStyle(el).display === "none") return 1;
    const r = document.createRange();
    r.selectNodeContents(el);
    return new Set([...r.getClientRects()].map((q) => Math.round(q.left))).size;
  };
  const over = () => cols(ax.querySelector("span")) > 1 || cols(ax.querySelector("small")) > 1;
  if (!over()) return;
  ax.classList.add("part-only");
  if (!over()) return;
  ax.classList.add("tight");
  if (!over() || !hero) return;
  const span = ax.querySelector("span");
  span.style.whiteSpace = "nowrap";
  const need = span.getBoundingClientRect().height + 40; // 軸の上下の余白 20px ずつ
  span.style.whiteSpace = "";
  hero.style.minHeight = `${Math.ceil(need)}px`;
}

fitAxis(document.querySelector(".lesson-axis"));
// 描いた直後は書体の読み込みやチップの組み直しで冒頭がまだ縮むので、冒頭の高さが変わるたびに合わせ直す
// (合わせ直すと高さがまた変わるので、次の描画で。同じ大きさなら何もしない)
(() => {
  const ax = document.querySelector(".lesson-axis");
  const hero = ax?.closest(".lesson-hero");
  if (!hero || !window.ResizeObserver) return;
  let last = "";
  new ResizeObserver(([e]) => {
    const size = `${Math.round(e.contentRect.width)}x${Math.round(e.contentRect.height)}`;
    if (size === last) return;
    last = size;
    requestAnimationFrame(() => fitAxis(ax));
  }).observe(hero);
})();
addEventListener("resize", () => fitAxis(document.querySelector(".lesson-axis")));

/**
 * 本文の軸(PC の 1024px 以上で、CSS が見せる。1360px 以上の 1 列は段の名前つき、ほかは細い軸): 冒頭の藍の軸を本文の終わりまで通し、部の縦書きと
 * 段ごとの縦の運針を載せる。段・済み・いまの段は段の帯(.stepper)をそのまま写し(帯は軸がある間 CSS で隠す)、
 * 帯の済みが変われば(サンプルを 3 秒読んだ・クイズに合格・読解を全問・読んだ)軸も描き直して、その目を縫う。
 * 玉結びの位置(--k)は trackLesson の update() が決める
 */
function buildRail(stepper, steps, backLink) {
  const layout = stepper.closest(".lesson-layout");
  const main = layout?.querySelector(".lesson-main");
  if (!main) return null;
  // 1024〜1359px の 1 列と AI の欄があるときは、段の名前を軸の左に下げる空きが無いので細い軸(CSS の .rail-thin)。
  // どちらも軸を出さず段の帯のままだったので、署名が続かなかった(30 回目の審査)
  const narrow = matchMedia("(max-width: 1359.98px)");
  const setMode = () => layout.classList.toggle("rail-thin", narrow.matches || !layout.classList.contains("no-tutor"));
  setMode();
  narrow.addEventListener("change", setMode);
  const part = document.querySelector(".lesson-axis span")?.textContent.trim() || "";
  const rail = document.createElement("div");
  rail.className = "lesson-rail";
  rail.innerHTML = `<div class="rail-inner">${backLink || ""}<p class="rail-part" aria-hidden="true">${escHtml(part)}</p>
    <nav class="rail-steps" aria-label="このレッスンの段">${steps.map((a) =>
      `<a class="rail-step" href="${a.getAttribute("href")}"><span class="rail-label"></span><i class="rail-stitch" aria-hidden="true"></i></a>`).join("")}
    <p class="rail-cap" aria-hidden="true"></p></nav></div>`;
  stepper.closest(".stepper-wrap").before(rail); // 読み上げとキーボードの順は、帯と同じく冒頭のすぐ後ろ
  const items = [...rail.querySelectorAll(".rail-step")];
  const cap = rail.querySelector(".rail-cap");
  const sync = () => {
    steps.forEach((a, i) => {
      const item = items[i];
      const done = a.classList.contains("done");
      // 見ている間に済んだ段だけ縫う(開いたときに済んでいた段は縫わずに糸で出す)
      if (done && item.dataset.sewn === "0") item.classList.add("just");
      item.dataset.sewn = done ? "1" : "0";
      item.classList.toggle("sewn", done);
      item.querySelector(".rail-label").innerHTML = a.innerHTML.trim() + (done ? '<span class="visually-hidden">(済み)</span>' : "");
    });
    // ホームの運針と同じ言葉で、済んだ段の数を 1 行(読み上げは各段の「(済み)」で足りるので隠す)
    cap.innerHTML = `${items.length} 段のうち <b>${items.filter((it) => it.classList.contains("sewn")).length} 段</b>済み`;
  };
  sync();
  new MutationObserver(sync).observe(stepper, { subtree: true, childList: true, attributes: true, attributeFilter: ["class"] });
  main.closest(".lesson-layout").classList.add("has-rail");
  return rail;
}

/** 読んでいる段を段の並びに示し、縫い線を進める */
function trackLesson() {
  const stepper = document.querySelector(".stepper");
  const sew = document.querySelector(".sew");
  if (!stepper) return;
  watchOverflow(stepper, stepper.closest(".stepper-wrap"));
  const steps = [...stepper.querySelectorAll(".step")];
  const sections = steps.map((a) => document.getElementById(a.getAttribute("href").slice(1))).filter(Boolean);
  if (!sections.length) return;
  let current = null;
  const h1 = document.querySelector("h1[data-lesson-id]");
  const lessonId = h1?.dataset.lessonId;
  // 復習・読解・用語集から開いた(?from=)ときは「のぞき見」: 前回の続き(ホームの主役)を書き換えず、戻り先を段の帯の右に出す
  // (「読み直す」で古いレッスンへ行くと、ホームの主役がそのレッスンに変わった。25 回目の審査)
  const back = { review: ["/review", "復習へ戻る"], reading: ["/reading", "読解へ戻る"], terms: ["/glossary", "用語集へ戻る"] }[new URLSearchParams(location.search).get("from")];
  // 読解・用語集へは普通のリンクで戻り、一覧の側で押したリンクの所へ送る(PEEK_RETURN_KEY)。ブラウザの「戻る」だと、
  // 着いたレッスンで段の帯を押したあとは同じレッスンの前の段へ戻るだけだった(27 回目の審査)
  const backLink = (cls) => back ? `<a class="${cls}" href="${back[0]}${back[0] === "/review" ? "" : "?ret=1"}">${back[1]}</a>` : "";
  if (back) {
    stepper.insertAdjacentHTML("afterend", backLink("peek-back"));
    // 細い軸のときの置き場(軸の左に空きが無い)。CSS が .rail-thin のときだけ見せる
    document.querySelector(".lesson-hero .breadcrumb")?.insertAdjacentHTML("beforeend", backLink("peek-back hero-back"));
  }
  const rail = buildRail(stepper, steps, backLink("peek-back rail-back"));
  const railItems = rail ? [...rail.querySelectorAll(".rail-step")] : [];
  const seen = pcStore(SECTIONS_KEY);
  const markDone = (key) => stepper.querySelector(`.step[href="#${key}"]`)?.classList.add("done");
  (seen[lessonId] || []).forEach(markDone);
  if (lessonId && sessionStorage.getItem(RESUME_KEY) === lessonId) {
    sessionStorage.removeItem(RESUME_KEY);
    const last = pcStore(LAST_KEY);
    const sec = last.id === lessonId && document.getElementById(last.section);
    if (sec && last.off > 0) {
      resumeLanding = true;
      const land = () => scrollTo({ top: sec.getBoundingClientRect().top + scrollY - 140 + last.off, behavior: "auto" });
      requestAnimationFrame(land);
      afterSettle(land); // 図が描けて上の段が伸びたら、もう一度(1 画面上のサンプルに着いていた。28 回目の審査)
    }
  }
  let saveTimer = null;
  const shownSince = {};
  const update = () => {
    const line = 140;
    // 仕事のコード・サンプルは、段が画面に 3 秒以上あって、その終わりが画面の中に見えたら「済み」(飛び越しただけでは付けない。
    // PC版の記録はこのブラウザの中。スマホ版と同じ。サンプルを済みにしないと、ホームが読み終えたサンプルを指した。27 回目の審査)
    for (const key of ["atwork", "run"]) {
      const sec = document.getElementById(key);
      const r = sec?.getBoundingClientRect();
      if (!(r && r.top < innerHeight && r.bottom > 0)) { delete shownSince[key]; continue; }
      if (shownSince[key] == null) { shownSince[key] = Date.now(); setTimeout(update, 3100); } // 止まって読んでいても 3 秒後に確かめ直す
      if (lessonId && r.bottom < innerHeight && Date.now() - shownSince[key] >= 3000 && !(seen[lessonId] || []).includes(key)) {
        seen[lessonId] = [...(seen[lessonId] || []), key];
        pcSave(SECTIONS_KEY, seen);
        markDone(key);
      }
    }
    // 前回の続き(ダッシュボードの主役にする)
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      if (!lessonId || (back && pcStore(LAST_KEY).id !== lessonId)) return;
      const end = document.querySelector(".lesson-nav")?.getBoundingClientRect().top < innerHeight;
      const top = document.getElementById(current)?.getBoundingClientRect().top ?? line;
      pcSave(LAST_KEY, { id: lessonId, title: h1.textContent.trim(), section: current, off: Math.round(line - top), end });
    }, 400);
    // 今の段は、上端が画面の上から 1/3 の線を越えた最後の段(「読み直す」でクイズの 1 問目に着くと、節の見出しが 140px の線より
    // 下に残り、段の印が前の「サンプル」のままだった。25 回目の審査)
    const pick = Math.max(line, innerHeight / 3);
    let key = sections[0].id;
    for (const sec of sections) if (sec.getBoundingClientRect().top <= pick) key = sec.id;
    if (key !== current) {
      current = key;
      steps.forEach((a) => {
        if (a.getAttribute("href") === "#" + key) {
          a.setAttribute("aria-current", "true");
          stepper.scrollTo({ left: a.offsetLeft - (stepper.clientWidth - a.offsetWidth) / 2 });
        } else a.removeAttribute("aria-current");
      });
    }
    const first = sections[0].getBoundingClientRect().top + scrollY;
    const end = sections[sections.length - 1].getBoundingClientRect().bottom + scrollY - innerHeight;
    const p = Math.max(0, Math.min(1, (scrollY - first + line) / Math.max(1, end - first + line)));
    // 縫い目(9px の目 + 6px の間)の切れ目で止める(目の途中で切れた半端な欠片を出さない)
    if (sew) sew.style.setProperty("--p", `${p >= 1 ? sew.clientWidth : Math.floor((p * sew.clientWidth) / 15) * 15 + (p > 0 ? 9 : 0)}px`);
    // 本文の軸: いまの段に朱の玉結び。段の頭が読む線を越えてから、段の終わりが画面の下端に来るまでの割合だけ玉結びを下ろし、
    // 通ったところに刺し子の目を出す(画面より短い段は、いまの段になった時点で下まで)。止めるのは目の切れ目(上の縫い線と同じ)
    if (rail?.offsetWidth) {
      const r = document.getElementById(current).getBoundingClientRect();
      const span = r.height - (innerHeight - pick);
      const ratio = span < 1 ? 1 : Math.max(0, Math.min(1, (pick - r.top) / span));
      const stitchTo = (item, q) => {
        const h = item.querySelector(".rail-stitch").clientHeight;
        return `${q >= 1 ? h : Math.min(h, Math.floor((q * h) / 15) * 15 + (q > 0 ? 9 : 0))}px`;
      };
      railItems.forEach((item) => {
        const now = item.getAttribute("href") === "#" + current;
        if (!now) item.removeAttribute("aria-current");
        else {
          item.setAttribute("aria-current", "true");
          item.dataset.reach = Math.max(Number(item.dataset.reach || 0), ratio);
          item.style.setProperty("--k", stitchTo(item, ratio));
        }
        // 通った所は覚えておき、ほかの段へ移っても弱い色の目で残す(「読んだ」を押さずに次の段へ進むと、縫った目が消えて
        // 針穴に戻っていた。28 回目の審査)。「読む」を最後まで通って押していなければ、名前の下に「『読んだ』で済み」
        const reach = Number(item.dataset.reach || 0);
        item.classList.toggle("passed", !now && reach > 0);
        if (!now && reach > 0) item.style.setProperty("--r", stitchTo(item, reach));
        item.classList.toggle("read-pending", !now && reach >= 1 && item.getAttribute("href") === "#read" && !item.classList.contains("sewn"));
      });
    }
  };
  let ticking = false;
  const onFrame = () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => { ticking = false; update(); });
  };
  addEventListener("scroll", onFrame, { passive: true });
  addEventListener("resize", onFrame); // 幅が 1024px・1360px をまたぐと軸が出たり消えたりするので、玉結びの位置も測り直す
  // 図(Mermaid)やコードの色付けで段の高さが変わったときも、いまの段と玉結びを測り直す(スクロールが起きないと古いままだった)
  if (window.ResizeObserver) new ResizeObserver(onFrame).observe(stepper.closest(".lesson-main") || document.body);
  update();
}

// 質問ボタンは、下へ読んでいる間は隠す(本文に重ならないように)
{
  let lastY = scrollY;
  addEventListener("scroll", () => {
    const fab = document.querySelector(".tutor-fab");
    if (!fab) return;
    if (scrollY > lastY + 6 && scrollY > 200) fab.classList.add("away");
    else if (scrollY < lastY - 6) fab.classList.remove("away");
    lastY = scrollY;
  }, { passive: true });
}

/** 開いた畳みの記憶(レッスンの URL!問いの番号)。PC はこのブラウザの中だけ */
const GATES_KEY = "javadojo-pc-gates";
const gatePage = () => location.pathname;
function gateOpened(key) { try { return !!(JSON.parse(localStorage.getItem(GATES_KEY)) || {})[key]; } catch (e) { return false; } }
function rememberGate(key) {
  try { const g = JSON.parse(localStorage.getItem(GATES_KEY)) || {}; g[key] = 1; localStorage.setItem(GATES_KEY, JSON.stringify(g)); } catch (e) { console.warn("畳みの記録を保存できませんでした", e); }
}

/**
 * 手を止めて(本文の途中の 1 問): 押したら正解とあなたの答えを付け、選んだ誤答に向けた 1 文と理由を出す。
 * 正解なら枠の上の針目が糸になる。フォーカスは理由へ移す。スマホ版の bindChecks と同じ形
 */
function bindChecks(root) {
  root.querySelectorAll?.("aside.check").forEach((box) => {
    const answer = Number(box.dataset.answer);
    // 答えを見せる前に置いた問いは、答えるまで後ろの塊を畳む。「先に読む」でいつでも開ける
    // 一度開いた畳みは覚えておき、読み直しでは開いたまま(復習の「読み直す」から来た人を止めない)
    const gate = root.querySelector(`.check-gate[data-gate="${box.dataset.check}"]`);
    const gateKey = `${gatePage()}!${box.dataset.check}`;
    const openGate = (focus) => {
      if (!gate) return;
      gate.hidden = false;
      box.querySelector(".check-gate-note")?.remove();
      box.nextElementSibling?.classList.contains("gate-fold") && box.nextElementSibling.remove();
      rememberGate(gateKey);
      // 「先に読む」はボタンごと消えるので、フォーカスを開いた続きへ移す(読み上げが迷子にならないように)
      if (focus) { gate.tabIndex = -1; gate.focus({ preventScroll: true }); }
    };
    if (gate && !box.querySelector(".choice:disabled") && !gateOpened(gateKey)) {
      gate.hidden = true;
      box.insertAdjacentHTML("beforeend", `<p class="check-gate-note">答えると続きが開きます<button type="button" class="check-skip">先に読む</button></p>`);
      box.insertAdjacentHTML("afterend", `<p class="gate-fold" aria-hidden="true"><span>続き(答えを見せる所)を畳んでいます</span></p>`);
      box.querySelector(".check-skip").onclick = () => openGate(true);
    }
    box.querySelectorAll(".check-choices .choice").forEach((b) => {
      b.onclick = () => {
        const choice = Number(b.dataset.c);
        const ok = choice === answer;
        box.querySelectorAll(".check-choices .choice").forEach((x) => {
          const j = Number(x.dataset.c);
          const text = x.querySelector(".choice-text") || x.querySelector("span");
          x.disabled = true;
          x.classList.toggle("is-answer", j === answer);
          x.classList.toggle("is-wrong", j === choice && !ok);
          if (j === choice && !ok) text.insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
          if (j === answer) text.insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
        });
        const ex = box.querySelector(".check-explain");
        ex.classList.add(ok ? "correct" : "wrong");
        ex.querySelectorAll(".check-why").forEach((w) => { w.hidden = ok || Number(w.dataset.for) !== choice; });
        ex.insertAdjacentHTML("afterbegin", `<span class="q-mark">${ok ? "✓ 正解" : "× 不正解"}</span>`);
        ex.hidden = false;
        box.classList.add(ok ? "sewn" : "missed");
        // 答えの前に置いた問いは、本文の続きが種明かしをするので、ここでは理由を繰り返さず続きへ送る
        if (gate) {
          ex.querySelector(".check-ex-body")?.setAttribute("hidden", "");
          ex.insertAdjacentHTML("beforeend", `<p class="check-gate-after">続きの本文で、答えを確かめよう</p>`);
        }
        openGate(false);
        ex.focus({ preventScroll: true });
      };
    });
  });
}

function initRoot(root) {
  bindChecks(root);
  root.querySelectorAll("textarea.code-editor").forEach(initEditor);
  fitSvgFigures(root);
  const drawn = renderMermaid(root).then(() => highlight(root));
  root.querySelectorAll(".chat-log").forEach((log) => { log.scrollTop = log.scrollHeight; });
  return drawn; // 図と色付けが済んだら解ける(着地のやり直しが待つ)
}

// 「雛形に戻す」「元に戻す」
document.addEventListener("click", (e) => {
  const btn = e.target.closest("[data-reset-starter]");
  if (!btn) return;
  const form = btn.closest("form");
  const starter = form.querySelector("textarea.starter-code");
  const editor = form.querySelector("textarea.code-editor");
  if (starter && editor && confirm("エディタの内容を最初の状態に戻しますか?")) {
    setEditorText(editor, starter.value);
  }
});

// クイズ: 採点結果の解説を各問の直下へ移し、答えを止める(スマホ版と同じ答え合わせ)
document.addEventListener("htmx:afterSwap", (e) => {
  const out = e.detail.target;
  const tpl = out.id === "quiz-result" && out.querySelector("template.q-explains");
  if (!tpl) return;
  const form = document.querySelector("form.quiz");
  const items = form.querySelectorAll(".quiz-q");
  form.querySelectorAll(".q-explain").forEach((n) => n.remove());
  tpl.content.querySelectorAll(".q-explain").forEach((ex) => {
    const i = Number(ex.dataset.q);
    const li = items[i];
    if (!li) return;
    li.id = `q-${i + 1}`;
    const ok = ex.classList.contains("correct");
    li.querySelectorAll("label.choice").forEach((label) => {
      const j = Number(label.querySelector("input").value); // 並びを入れ替えているので、位置ではなく値で見る
      label.classList.toggle("is-answer", j === Number(ex.dataset.answer));
      label.classList.toggle("is-wrong", !ok && j === Number(ex.dataset.chosen));
      // 色だけに頼らない: 選んだ誤答には「あなたの答え」、正解には「正解」と文字で出す
      label.querySelector(".choice-tag")?.remove();
      if (!ok && j === Number(ex.dataset.chosen)) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
      if (j === Number(ex.dataset.answer)) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
    });
    li.appendChild(document.importNode(ex, true));
  });
  tpl.remove();
  form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = true; });
  form.querySelector("button[type=submit]").hidden = true;
  // 合格なら段の並びの「クイズ」にレ点
  if (out.querySelector(".quiz-next .btn.primary") || out.textContent.includes("満点")) document.querySelector('.stepper .step[href="#quiz"]')?.classList.add("done");
  highlight(form);
  out.scrollIntoView({ behavior: reduceMotion() ? "auto" : "smooth", block: "center" });
});
// 「読んだ」を押したら、段の並び(と本文の軸)の「読む」も済みに(開き直すまでレ点が付かなかった)
document.addEventListener("htmx:afterSwap", (e) => {
  if (document.querySelector("#read .read-done")) document.querySelector('.stepper .step[href="#read"]')?.classList.add("done");
  // 押したボタンごと差し替わってフォーカスが消え、「読了。次は…」が読み上げられなかった(29 回目の審査)。新しい 1 行へ移す
  if (e.target.matches?.("#read .read-done")) { e.target.tabIndex = -1; e.target.focus({ preventScroll: true }); }
});
// 演習の採点結果は判定の行へフォーカスを移す(採点のあとも「提出して採点」に残り、合格・不合格も「藍に染まった」も
// 読み上げられなかった。29 回目の審査)。手を止めて・復習の判定と同じ形
document.addEventListener("htmx:afterSwap", (e) => {
  const banner = e.target.querySelector?.(".grade-result .grade-banner");
  if (!banner) return;
  banner.tabIndex = -1;
  banner.focus({ preventScroll: true });
  if (banner.getBoundingClientRect().bottom > innerHeight) banner.scrollIntoView({ block: "center", behavior: reduceMotion() ? "auto" : "smooth" });
});
// 演習の採点・クイズの採点のあとは、段の帯(と本文の軸)の「演習 n/全部」と、冒頭の色見本をその場で書き換える
// (演習に合格して藍に染まっても、開き直すまで帯も冒頭も前のままだった。28 回目の審査)。値は採点結果の data-* から
// (「読んだ」はボタンごと outerHTML で差し替わるので、イベントの起きた新しい要素(e.target)そのものも見る)
document.addEventListener("htmx:afterSwap", (e) => {
  const sel = ":is(.read-done, .grade-result, .quiz-result)[data-dye]"; // ホームの行の data-dye とは別もの
  const res = e.target.closest?.(sel) || e.target.querySelector?.(sel);
  if (!res) return;
  const ex = res.dataset.exTotal && document.querySelector('.stepper .step[href="#write"]');
  if (ex) {
    const passed = Number(res.dataset.exPassed);
    const total = Number(res.dataset.exTotal);
    ex.querySelector("small")?.remove();
    ex.classList.toggle("done", passed >= total);
    if (passed > 0 && passed < total) ex.insertAdjacentHTML("beforeend", `<small>${passed}/${total}</small>`);
  }
  const sw = document.querySelector(".lesson-hero .lesson-swatch");
  const label = res.dataset.dyeLabel;
  if (!sw || !label) return;
  sw.className = sw.className.replace(/\bdye-\d+\b/, `dye-${res.dataset.dye}`);
  sw.setAttribute("aria-label", label);
  const [name, what] = label.split("・");
  const b = sw.closest(".lesson-dye")?.querySelector("b");
  if (b) b.textContent = `${name}(${what ?? ""})`;
});
// STS のチェック: 全部付いたら段の帯の「STSで試す」も済みに、見出しの「n / 全部」も数え直す(開き直すまで古いままだった)
// (outerHTML の差し替えなので、detail.target は外れた古い行。イベントは新しい行(e.target)で起きる)
document.addEventListener("htmx:afterSwap", (e) => {
  const list = e.target.closest?.("#sts .checklist");
  if (!list) return;
  const items = list.querySelectorAll(".check-item");
  const done = list.querySelectorAll(".check-item.checked").length;
  const count = list.querySelector(".checklist-title .muted");
  if (count) count.textContent = `${done} / ${items.length}`;
  document.querySelector('.stepper .step[href="#sts"]')?.classList.toggle("done", items.length > 0 && done === items.length);
});
// 復習: 選んだら「答え合わせ」を押せるように(選ぶ前から押せる小さなボタンで、スマホ・読解と言い方が違った。26 回目の審査)
document.addEventListener("change", (e) => {
  const b = e.target.closest?.(".review-quiz form")?.querySelector(".arm-submit");
  if (b && b.disabled) { b.disabled = false; b.textContent = "答え合わせ"; }
});
// 復習: 答えたら、選択肢に正解とあなたの答えを付ける(並びを入れ替えているので値で見る)
document.addEventListener("htmx:afterSwap", (e) => {
  const v = e.detail.target.id === "review-answer" && e.detail.target.querySelector(".review-verdict");
  // 上の行の差し替え(hx-swap-oob)でも afterSwap が来るので、1 回の答えにつき 1 回だけ印を付ける
  if (!v || v.dataset.marked) return;
  v.dataset.marked = "1";
  // 2 列の画面では、右の列の選択肢の下にも「次へ」(左の判定と解説が長いと、次へが画面の外に落ちた。25 回目の審査)
  const nextLink = v.querySelector(".review-next .btn.primary");
  const form = v.closest(".review-quiz")?.querySelector("form");
  if (nextLink && form && !form.querySelector(".review-next-side")) form.insertAdjacentHTML("beforeend", `<div class="review-next-side">${nextLink.outerHTML}</div>`);
  const answer = Number(v.dataset.answer);
  const chosen = Number(v.dataset.chosen);
  v.closest(".review-quiz")?.querySelectorAll("label.choice").forEach((label) => {
    const j = Number(label.querySelector("input").value);
    label.classList.toggle("is-answer", j === answer);
    label.classList.toggle("is-wrong", j === chosen && j !== answer);
    if (j === chosen && j !== answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
    if (j === answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
  });
  const cap = document.querySelector(".review-stitch .unshin-cap");
  const baste = document.querySelector(".retry-stitch");
  if (baste) {
    // 解き直し: 正しく解けたら 1 目の仮縫いを破線の糸に(本縫いは明日の復習。記録はしない)
    const ok = !!v.querySelector(".q-explain.correct");
    baste.querySelector(".unshin i")?.classList.toggle("sewn", ok);
    if (cap) cap.innerHTML = ok ? "<span><b>仮縫いした</b>。本縫いは明日の復習で</span>" : "<span>明日の復習で、もう一度</span>";
  } else if (v.querySelector(".q-explain.correct")) {
    const now = document.querySelector(".review-stitch .unshin i.now");
    now?.classList.replace("now", "sewn");
    now?.classList.add("just");
    if (cap) cap.innerHTML = v.dataset.graduated === "true" ? '<span class="grad-line"><span class="dye-burst grad-burst" aria-hidden="true"><span class="dye dye-0"></span><span class="dye dye-4 ink"></span></span><span><b>卒業</b>。褐に染まった(この問題はもう出ません)</span></span>'
      : `<span>縫えた。次は <b>${Number(v.dataset.nextDays)} 日後</b>(4 回のうち ${Number(v.dataset.nextStep)} 回目)</span>`;
  } else {
    // 間違えたら、縫えていた目をほどいて 1 目めに戻す(下の「次は 1 日後」と上の表示を食い違わせない)。
    // 縫えた目が無い(1 回目で間違えた)ときは、ほどくものが無いので「もう一度」だけ言う
    const had = document.querySelectorAll(".review-stitch .unshin i.sewn").length > 0;
    document.querySelectorAll(".review-stitch .unshin i").forEach((m, i) => { m.classList.remove("sewn", "now", "just"); m.classList.toggle("now", i === 0); m.classList.add("undone"); });
    document.querySelector(".review-stitch .unshin")?.setAttribute("aria-label", had ? "ほどけて 1 回目に戻った" : "1 回目のまま");
    if (cap) cap.innerHTML = had ? "<span><b>ほどけた</b>。明日、1 回目からもう一度</span>" : "<span>明日、もう一度</span>";
  }
  // 判定へフォーカスを移し、選んだ答えが画面の上の方に来るまで送る(解説が長くても「× あなたの答え」を見失わない)
  const verdict = v.querySelector(".q-explain");
  verdict?.focus({ preventScroll: true });
  const card = v.closest(".review-quiz");
  const chosenLabel = card?.querySelector("label.is-wrong, label.is-answer");
  // 2 列のときは右の列の写しの「次へ」(左の方は隠れている)
  const next = card?.querySelector(".review-next-side .btn") || v.querySelector(".review-next .btn");
  if (matchMedia("(min-width: 1200px)").matches && card && getComputedStyle(card).display === "grid") {
    // 2 列の PC: 選択肢は右上にあるので、「次へ」まで画面に収まるなら送らない(運針の縫う・ほどけるを隠さない)。
    // はみ出すときも、カードの上端(運針)がヘッダーのすぐ下に来る所まで
    if (next && next.getBoundingClientRect().bottom > innerHeight) {
      const header = document.querySelector(".site-header, header")?.getBoundingClientRect().bottom ?? 64;
      scrollTo({ top: scrollY + card.getBoundingClientRect().top - header - 8, behavior: reduceMotion() ? "auto" : "smooth" });
    }
    return;
  }
  const top = (chosenLabel || verdict)?.getBoundingClientRect().top ?? 0;
  if (top > 140) scrollTo({ top: scrollY + top - 120, behavior: reduceMotion() ? "auto" : "smooth" });
});
document.addEventListener("click", (e) => {
  if (!e.target.closest("[data-quiz-reset]")) return;
  const form = document.querySelector("form.quiz");
  form.reset();
  form.querySelectorAll(".q-explain").forEach((n) => n.remove());
  form.querySelectorAll("label.choice").forEach((l) => l.classList.remove("is-answer", "is-wrong"));
  form.querySelectorAll(".choice-tag").forEach((n) => n.remove());
  form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = false; });
  form.querySelector("button[type=submit]").hidden = false;
  document.getElementById("quiz-result").innerHTML = "";
  quizRound++;
  shuffleChoices(form);
  form.scrollIntoView({ behavior: reduceMotion() ? "auto" : "smooth" });
});

// 選択肢の並び: 書いた順のままだと正解の位置が偏る(読解は 2 番目が半分以上)ので、問いごとに決まった並びに入れ替える。
// クイズは「もう一度解く」のたびに並びを変える。値は元の番号のまま(採点は値で見る)。スマホ版の choiceOrder と同じ
function choiceOrder(n, seed) {
  let h = 2166136261;
  for (const ch of seed) h = Math.imul(h ^ ch.codePointAt(0), 16777619);
  const order = Array.from({ length: n }, (_, i) => i);
  for (let i = n - 1; i > 0; i--) {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    const j = ((h ^ (h >>> 16)) >>> 0) % (i + 1);
    [order[i], order[j]] = [order[j], order[i]];
  }
  return order;
}
let quizRound = 0;
function reorderChoices(parent, seed) {
  const labels = [...parent.querySelectorAll(":scope > label.choice")];
  if (labels.length < 2) return;
  const byValue = new Map(labels.map((l) => [Number(l.querySelector("input").value), l]));
  const anchor = labels[labels.length - 1].nextSibling;
  choiceOrder(labels.length, seed).forEach((j) => parent.insertBefore(byValue.get(j), anchor));
}
function shuffleChoices(root) {
  const lessonId = document.querySelector("h1[data-lesson-id]")?.dataset.lessonId || location.pathname;
  root.querySelectorAll(".quiz-q").forEach((li, i) => reorderChoices(li, `${lessonId}#${i}|${quizRound}`));
  root.querySelectorAll("form[data-reading] fieldset").forEach((fs, i) => reorderChoices(fs, fs.closest("[data-ref]")?.dataset.ref || `${lessonId}@${i}`));
}

// スマホ: AIチューターを開く/閉じる
document.addEventListener("click", (e) => {
  const toggle = e.target.closest("[data-tutor-toggle]");
  if (!toggle) return;
  e.preventDefault();
  const open = document.body.classList.toggle("tutor-open");
  if (open) {
    const log = document.getElementById("chat-log");
    if (log) log.scrollTop = log.scrollHeight;
  }
});

// 読解: その場で答え合わせする。PC版の記録はこのブラウザの中(localStorage)だけ
const READING_KEY = "javadojo-pc-reading";
function readingRecord() {
  try { return JSON.parse(localStorage.getItem(READING_KEY)) || {}; } catch (e) { return {}; }
}
/** レッスンの段の並びの「読解 n/全部」(全問そろったらレ点)。スマホ版と同じ数え方 */
function readingStep() {
  const step = document.querySelector('.stepper .step[href="#reading"]');
  if (!step) return;
  const rec = readingRecord();
  const refs = [...document.querySelectorAll("#reading .reading-q[data-ref]")].map((li) => li.dataset.ref);
  const done = refs.filter((r) => rec[r]?.ok).length;
  step.querySelector("small")?.remove();
  if (refs.length && done >= refs.length) step.classList.add("done");
  else if (done > 0) step.insertAdjacentHTML("beforeend", ` <small>${done}/${refs.length}</small>`);
}
// 見出しの番号は段の並びの位置(スマホ版と同じ。読むが 01)
document.querySelectorAll(".stepper .step").forEach((a, i) => {
  const h = document.getElementById(a.getAttribute("href").slice(1) + "-title");
  if (h && !h.querySelector(".no") && !h.classList.contains("visually-hidden")) h.insertAdjacentHTML("afterbegin", `<span class="no">${String(i + 1).padStart(2, "0")}</span>`);
});
readingStep();

// 答え合わせのあとは回答を固定し、正解・あなたの答えを文字でも付ける(クイズと同じ規則)。「もう一度解く」で解き直せる
function lockAnswer(form, answer, choice, onReset) {
  const ok = choice === answer;
  form.querySelectorAll("label.choice").forEach((label) => {
    const j = Number(label.querySelector("input").value);
    label.classList.toggle("is-answer", j === answer);
    label.classList.toggle("is-wrong", j === choice && !ok);
    label.querySelector(".choice-tag")?.remove();
    if (j === choice && !ok) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
    if (j === answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
  });
  form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = true; });
  const submit = form.querySelector("button[type=submit]");
  submit.hidden = true;
  // 読解は「もう一度解く」を判定・解説・追跡の後ろに置く(答えた直後の目線を判定に向ける)
  const out = form.nextElementSibling?.classList.contains("reading-answer") ? form.nextElementSibling : null;
  const host = out ? form.parentElement : form;
  let again = host.querySelector("[data-answer-reset]");
  if (!again) {
    (out || submit).insertAdjacentHTML("afterend", `<button class="btn" type="button" data-answer-reset>もう一度解く</button>`);
    again = host.querySelector("[data-answer-reset]");
  }
  again.hidden = false;
  again.onclick = () => {
    form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = false; x.checked = false; });
    form.querySelectorAll("label.choice").forEach((l) => { l.classList.remove("is-answer", "is-wrong"); l.querySelector(".choice-tag")?.remove(); });
    submit.hidden = false;
    again.hidden = true;
    onReset();
  };
}
document.addEventListener("submit", (e) => {
  const form = e.target.closest("form[data-reading]");
  if (!form) return;
  e.preventDefault();
  const li = form.closest(".reading-q");
  const checked = form.querySelector("input:checked");
  if (!checked) return;
  const choice = Number(checked.value);
  const answer = Number(form.dataset.answer);
  const ok = choice === answer;
  const out = li.querySelector(".reading-answer");
  lockAnswer(form, answer, choice, () => { out.hidden = true; });
  const verdict = out.querySelector(".verdict");
  verdict.className = "verdict " + (ok ? "correct" : "wrong");
  verdict.textContent = ok ? "✔ 正解" : "✘ 不正解";
  out.querySelectorAll(".check-why").forEach((w) => { w.hidden = ok || Number(w.dataset.for) !== choice; });
  out.hidden = false;
  const rec = readingRecord();
  rec[li.dataset.ref] = { ok, at: new Date().toISOString().slice(0, 10) };
  try { localStorage.setItem(READING_KEY, JSON.stringify(rec)); } catch (err) { console.warn("読解の記録を保存できませんでした", err); }
  readingStep();
});

// 読解・用語集からのぞき見に出るときは、押したリンクと高さを控え、「〇〇へ戻る」(?ret=1)で戻ったらその所へ送る
document.addEventListener("click", (e) => {
  const a = e.target.closest?.("a[href*='from=reading'], a[href*='from=terms']");
  if (!a || !document.body.matches(".page-reading, .page-glossary")) return;
  try {
    sessionStorage.setItem(PEEK_RETURN_KEY, JSON.stringify({ path: location.pathname, href: a.getAttribute("href"),
      top: a.getBoundingClientRect().top, q: document.getElementById("glossary-q")?.value || "" }));
  } catch { /* 控えられなければ一覧の先頭へ戻るだけ */ }
}, true);
if (new URLSearchParams(location.search).get("ret") === "1") {
  history.replaceState(null, "", location.pathname + location.hash);
  let back = null;
  try { back = JSON.parse(sessionStorage.getItem(PEEK_RETURN_KEY)); sessionStorage.removeItem(PEEK_RETURN_KEY); } catch { back = null; }
  if (back && back.path === location.pathname) {
    const go = () => {
      // 絞り込みの受け手はこの下で登録するので、絞り込みも送るのと同じ時に
      const q = document.getElementById("glossary-q");
      if (q && back.q) { q.value = back.q; q.dispatchEvent(new Event("input", { bubbles: true })); }
      const link = [...document.querySelectorAll("a[href]")].find((x) => x.getAttribute("href") === back.href);
      if (!link) return;
      scrollTo({ top: link.getBoundingClientRect().top + scrollY - back.top, behavior: "auto" });
      const spot = link.closest(".term, .lesson-section") || link;
      spot.classList.add("spot-flash");
      setTimeout(() => spot.classList.remove("spot-flash"), 1200);
    };
    // コードの色付けや書体で高さが変わってから合わせる
    (document.fonts?.ready || Promise.resolve()).then(() => requestAnimationFrame(go));
  }
}

// 用語集: 読み・英語・説明のどれでも絞り込む
document.addEventListener("input", (e) => {
  if (!e.target.matches("#glossary-q")) return;
  const words = e.target.value.toLowerCase().split(/\s+/).filter(Boolean);
  let n = 0;
  document.querySelectorAll("#terms .term").forEach((li) => {
    const hit = words.every((w) => li.textContent.toLowerCase().includes(w));
    li.hidden = !hit;
    if (hit) n++;
  });
  document.querySelectorAll("#terms .term-group").forEach((g) => { g.hidden = words.length > 0; });
  const count = document.getElementById("glossary-count");
  if (count) count.textContent = words.length ? `${n} 語` : "";
});

// 用語集の索引: 虫めがねで検索欄へ、いま読んでいる行を強調して見える位置へ送る
{
  const index = document.getElementById("kana-index");
  if (index) {
    watchOverflow(index);
    index.querySelector("[data-kana-search]")?.addEventListener("click", (e) => {
      e.preventDefault();
      scrollTo({ top: 0, behavior: reduceMotion() ? "auto" : "smooth" });
      document.getElementById("glossary-q")?.focus({ preventScroll: true });
    });
    const groups = [...document.querySelectorAll("#terms .term-group")];
    let ticking = false;
    addEventListener("scroll", () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        const line = index.getBoundingClientRect().bottom + 8;
        let cur = null;
        for (const h of groups) { if (h.getBoundingClientRect().top <= line) cur = h.id.slice(2); else break; }
        index.querySelectorAll("a[data-row]").forEach((a) => {
          const on = a.dataset.row === cur;
          if (on && a.getAttribute("aria-current") !== "true") index.scrollTo({ left: a.offsetLeft - index.clientWidth / 2 });
          if (on) a.setAttribute("aria-current", "true"); else a.removeAttribute("aria-current");
        });
      });
    }, { passive: true });
  }
}

// いまいる画面のメニューに印を付ける(レッスンはダッシュボードの中)
{
  // レッスンはどのメニューの画面でもないので印を付けない(スマホ版と同じ)
  const path = location.pathname.startsWith("/lessons/") ? null : location.pathname;
  document.querySelectorAll(".main-nav a, .bottom-nav a").forEach((a) => {
    if (a.getAttribute("href") === path) a.setAttribute("aria-current", "page");
  });
}

// シートは Escape で閉じる
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && document.body.classList.contains("tutor-open")) document.body.classList.remove("tutor-open");
});

// チャット欄は Ctrl+Enter で送信
document.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && (e.ctrlKey || e.metaKey) && e.target.matches(".chat-form textarea")) {
    e.preventDefault();
    submitForm(e.target.form);
  }
});

// チューターに渡すコード(「いま書いているコードも見せる」がONのとき)
window.dojo = {
  codeForTutor() {
    const box = document.getElementById("send-code");
    if (box && !box.checked) return "";
    if (activeView) return activeView.text();
    const first = document.querySelector("textarea.code-editor");
    return first ? first.value : "";
  },
};

// highlight.js は defer なので、読み込み後にもう一度かける
window.addEventListener("load", () => highlight(document));

await loadCodeMirror();
pageSettled = Promise.all([initRoot(document), document.fonts?.ready]).catch((e) => console.warn("図か書体の準備に失敗しました", e));
shuffleChoices(document);
trackLesson();
dashboardResume();
document.querySelectorAll(".today-title a, main h1:not(.today-title)").forEach(phraseWrap);
fitTitles();
document.fonts?.ready.then(fitTitles);
if (window.htmx) {
  window.htmx.onLoad((el) => initRoot(el));
}

// コードを写すときは、折り返しのために挟んだ語をつなぐ印(U+2060)を取り除く(Java に貼るとコンパイルエラーになるので)
document.addEventListener("copy", (e) => {
  const text = getSelection()?.toString() || "";
  if (!text.includes("\u2060")) return;
  e.clipboardData.setData("text/plain", text.replace(/\u2060/g, ""));
  e.preventDefault();
});

// 用語集の「〇〇 で」の着地: 語の候補(「A / B」は両方、括弧の中の言い換えも)を、本文の太字 → 本文の段落・見出し・表 →
// 仕事のコードの行の説明 → サンプルの説明の順に探す(本文の p・li だけを探していたので、3 語に 1 語はレッスンの頭に
// 黙って着いた。27 回目の審査)。CurriculumTest#glossaryTermsLand が、どの語も本文・仕事のコード・サンプルに出ることを確かめる
function termWords(term) {
  const alt = [];
  const base = term.replace(/[((](.*?)[))]/g, (_, x) => { alt.push(...x.split(/\s*[/／、,]\s*/)); return ""; }).trim();
  return [...base.split(/\s*[/／]\s*/), ...alt].map((w) => w.replace(/\s+/g, " ").trim().toLowerCase()).filter(Boolean);
}
const plainText = (el) => el.textContent.replace(/[\u2060\u00ad]/g, "").replace(/\s+/g, " ").trim().toLowerCase();
function findTerm(term) {
  const words = termWords(term);
  const visible = (el) => el.getClientRects().length > 0;
  const bold = [...document.querySelectorAll("#read .md strong")].find((b) => words.includes(plainText(b)) && visible(b));
  if (bold) return bold.closest("p, li, h2, h3, h4, td, th") || bold;
  for (const sel of ["#read .md :is(p, li, h2, h3, h4, td, th, figcaption)", "#atwork :is(.ln-note, p, li)", "#run :is(h3, p, li)"]) {
    const hit = [...document.querySelectorAll(sel)].find((el) => visible(el) && words.some((w) => plainText(el).includes(w)));
    if (hit) return hit;
  }
  return null;
}
function landTerm(term) {
  let hit = findTerm(term);
  if (!hit) {
    // 見つからなければ黙って頭に着かず、本文の頭に 1 行で知らせる
    const md = document.querySelector("#read .md");
    if (!md) return;
    hit = document.createElement("p");
    hit.className = "term-miss";
    hit.textContent = `「${term}」は、このレッスンには同じ言い方では出てきません。用語集の説明と合わせて読んでください。`;
    md.prepend(hit);
  }
  requestAnimationFrame(() => {
    hit.scrollIntoView({ block: "center" });
    hit.classList.add("spot-flash");
    setTimeout(() => hit.classList.remove("spot-flash"), 1200);
  });
  afterSettle(() => hit.scrollIntoView({ block: "center" }));
}

// 復習の「読み直す」で問いそのもの(#quiz-q2)へ来たときは、その問いにフォーカスを置き、1.2 秒だけ藍の枠で示す(スマホ版と同じ)。
// このモジュールは上の await loadCodeMirror() を待つので、その間に load が済んでいることがある(load だけを待つと一度も走らなかった)。
// 色付けとエディタで上の高さが変わったあとなので、もう一度その問いの頭まで送る
const spotQuiz = () => {
  // 用語集の「〇〇 で」から来たら、本文でその語を太字にした最初の所(無ければ語が最初に出る段落)へ
  const term = new URLSearchParams(location.search).get("term");
  if (term && !location.hash) {
    landTerm(term);
    return;
  }
  // 段の頭へのハッシュ(ホームの「演習へ進む」の /lessons/〇〇#write など)も、軸ができて編集欄が替わったあとに着地し直す。
  // ブラウザは先に段の頭へ着くが、そのあと軸の組み立てで上の段の高さが変わり、1440px で 3 本に 1 本は見出しが隠れた(29 回目の審査)。
  // 前回の続き(段の中の位置)へ戻すときは、trackLesson の着地に任せる
  const sec = /^#[a-z][\w-]*$/.test(location.hash) && !resumeLanding && document.querySelector(`.lesson-section${location.hash}`);
  if (sec) {
    sec.scrollIntoView({ block: "start" });
    afterSettle(() => sec.scrollIntoView({ block: "start" }));
    return;
  }
  if (!/^#quiz-q\d+$/.test(location.hash)) return;
  const q = document.getElementById(location.hash.slice(1));
  if (!q) return;
  q.tabIndex = -1;
  q.scrollIntoView({ block: "start" });
  q.focus({ preventScroll: true });
  q.classList.add("spot-flash");
  setTimeout(() => q.classList.remove("spot-flash"), 1200);
  afterSettle(() => q.scrollIntoView({ block: "start" }));
};
if (document.readyState === "complete") spotQuiz();
else addEventListener("load", spotQuiz);
