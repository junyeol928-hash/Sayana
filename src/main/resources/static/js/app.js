// Java道場のフロント側。htmx で差し込まれた部分にも同じ初期化をかける。
// CodeMirror / Mermaid / highlight.js は CDN から読む。読めなくても textarea のまま動く。

// 依存パッケージを全部固定して、@codemirror/state などが1つのインスタンスに揃うようにする
const CM_DEPS = "?deps=" + [
  "@codemirror/state@6.7.6", "@codemirror/view@6.43.14", "@codemirror/language@6.13.1",
  "@codemirror/commands@6.11.1", "@codemirror/lint@6.9.7", "@codemirror/autocomplete@6.20.3",
  "@codemirror/search@6.7.2", "@lezer/common@1.5.3", "@lezer/highlight@1.2.5", "@lezer/lr@1.4.11",
  "@lezer/java@1.1.5",
].join(",");

let cm = null;            // CodeMirror のモジュール群(読み込めたら入る)
let activeView = null;    // 最後に触ったエディタ
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
    const [core, view, commands, javaLang] = await Promise.all([
      import("https://esm.sh/codemirror@6.0.2" + CM_DEPS),
      import("https://esm.sh/@codemirror/view@6.43.14" + CM_DEPS),
      import("https://esm.sh/@codemirror/commands@6.11.1" + CM_DEPS),
      import("https://esm.sh/@codemirror/lang-java@6.0.2" + CM_DEPS),
    ]);
    cm = { basicSetup: core.basicSetup, EditorView: core.EditorView, keymap: view.keymap,
           indentWithTab: commands.indentWithTab, java: javaLang.java };
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
  const { EditorView, basicSetup, keymap, indentWithTab, java } = cm;
  const view = new EditorView({
    doc: ta.value,
    extensions: [
      basicSetup,
      java(),
      keymap.of([
        { key: "Mod-Enter", run: () => { submitForm(ta.form); return true; } },
        indentWithTab,
      ]),
      EditorView.updateListener.of((u) => {
        if (u.docChanged) ta.value = u.state.doc.toString();
        if (u.focusChanged && u.view.hasFocus) activeView = { text: () => u.state.doc.toString(), view: u.view };
      }),
      // スマホの自動大文字化・自動修正で public が Public にならないように
      EditorView.contentAttributes.of({ autocapitalize: "off", autocorrect: "off", spellcheck: "false" }),
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

function highlight(root) {
  if (!window.hljs) return;
  // 読むためのコードは折り返す(スマホ版と同じ範囲)
  root.querySelectorAll(".quiz-q .md pre, .q-explain pre, .quiz-review pre, .quiz-answer pre, .reading-answer pre").forEach((pre) => pre.classList.add("code-lines"));
  root.querySelectorAll("pre.code-lines > code:not([data-lines])").forEach(numberLines);
  inlineNotes(root);
  // 出力・識別子だけの選択肢(X3、IndexOutOfBoundsException など)はコードの字体で
  root.querySelectorAll(".choice > span").forEach((sp) => {
    const t = sp.textContent.trim();
    if (!sp.querySelector("code") && /^[!-~][ -~]*$/.test(t) && !/\s\S+\s\S+\s/.test(t)) sp.classList.add("code-choice");
  });
  // 解説の中の長いメッセージ・出力(エラー文など)は、文の中のチップにせず独立した 1 行のブロックに(検索窓に貼る文なので途切れさせない)
  root.querySelectorAll(".explanation :not(pre) > code, .reading-answer :not(pre) > code, .q-explain :not(pre) > code").forEach((c) => {
    const t = c.textContent;
    if (t.length >= 24 && /\s/.test(t.trim())) c.classList.add("code-msg");
  });

  root.querySelectorAll("pre code:not([data-highlighted]):not([data-lines])").forEach((el) => {
    if (!el.className.includes("language-")) el.classList.add("language-java");
    if (el.classList.contains("language-text")) return;
    window.hljs.highlightElement(el);
  });
}

// 動きを減らす設定の人には、スクロールも瞬時に
const reduceMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const escHtml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

/** コードを 1 行ずつの要素に分け、行番号を付ける(スマホ版 app.js の numberLines と同じ) */
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
    // コメントの中は折り返しの候補を入れず、空白でも切らない(コメントの範囲を読ませる問題で、割れ方が答えに見えないように)
    else if (open.some((o) => o.includes("hljs-comment"))) cur += part.replace(/ /g, " ");
    else cur += part.replace(/(\w{4,}|\))\./g, "$1<wbr>.").replace(/\((?!\))/g, "(<wbr>").replace(/,/g, ",<wbr>");
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
  if ((pcStore(SECTIONS_KEY)[id] || []).includes("atwork")) done.add("atwork");
  const rec = readingRecord();
  const total = Number(d.reading || 0);
  if (total > 0 && Array.from({ length: total }, (_, i) => rec[`${id}@${i}`]?.ok).every(Boolean)) done.add("reading");
  return (d.steps || "read quiz").split(" ").map((key) => ({ key, done: done.has(key) }));
}
// 運針(うんしん): 段を刺し子の針目で見せる。済んだ段は糸、いまの段は玉結び、まだの段は針穴
function unshin(steps, nowKey) {
  const n = steps.filter((st) => st.done).length;
  const label = `${steps.length} 段のうち ${n} 段済み${nowKey ? `。次は${SECTION_LABEL[nowKey]}` : ""}`;
  return `<div class="unshin" role="img" aria-label="${label}" style="--steps: ${steps.length}">${steps.map((st) =>
    `<i class="${st.key === nowKey ? "now" : st.done ? "sewn" : ""}"></i>`).join("")}</div>
    <p class="unshin-cap" aria-hidden="true"><span>${steps.length} 段のうち <b>${n} 段</b>済み</span>${nowKey ? `<span>次は <b>${SECTION_LABEL[nowKey]}</b></span>` : ""}</p>`;
}
// 大きな題は語の途中で折らない(「コンテ/ナ」にならないように)。語ごとに折り返さない箱に入れ、助詞・記号は前の語につなぐ。
// word-break: auto-phrase が効かないブラウザでも同じに見えるように、Intl.Segmenter で分ける
function phraseWrap(el) {
  if (!el || el.dataset.phrased || !("Segmenter" in Intl)) return;
  const words = [];
  for (const { segment, isWordLike } of new Intl.Segmenter("ja", { granularity: "word" }).segment(el.textContent)) {
    if (words.length && (!isWordLike || /^[\u3041-\u309f]{1,2}$/.test(segment) || /[(「『]$/.test(words[words.length - 1]))) words[words.length - 1] += segment;
    else words.push(segment);
  }
  el.innerHTML = words.map((w) => `<span class="phrase">${escHtml(w).replace(/ /g, " ")}</span>`).join("");
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
  if (ok) ok.textContent = Object.values(readingRecord()).filter((r) => r.ok).length;
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
    const doneBy = { read: dye >= 1, quiz: dye >= 2, write: dye >= 3 };
    const order = ["read", "atwork", "run", "quiz", "write", "reading", "sts"];
    const has = { write: rd.exercises !== "0", reading: rd.reading !== "0" };
    const go = doneBy[last.section]
      ? order.slice(order.indexOf(last.section) + 1).find((k) => ["quiz", "write", "reading"].includes(k) && has[k] !== false && !doneBy[k]) || last.section
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
    hero.querySelector(".today-kicker").innerHTML = `<span>前回の続き</span><span class="visually-hidden">${escHtml(d.chapter)}</span><span>読む ${escHtml(d.minutes)} 分</span>`;
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
    if (talk) talk.textContent = `モカ先輩「続きは${label ? `『${label}』` : ""}から。前に読んでいた所へ戻るよ」`;
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
  const seen = pcStore(SECTIONS_KEY);
  const markDone = (key) => stepper.querySelector(`.step[href="#${key}"]`)?.classList.add("done");
  (seen[lessonId] || []).forEach(markDone);
  if (lessonId && sessionStorage.getItem(RESUME_KEY) === lessonId) {
    sessionStorage.removeItem(RESUME_KEY);
    const last = pcStore(LAST_KEY);
    const sec = last.id === lessonId && document.getElementById(last.section);
    if (sec && last.off > 0) requestAnimationFrame(() => scrollTo({ top: sec.getBoundingClientRect().top + scrollY - 140 + last.off, behavior: "auto" }));
  }
  let saveTimer = null;
  let atworkSince = null;
  const update = () => {
    const line = 140;
    // 仕事のコードは、段が画面に 3 秒以上あって、その終わりが画面の中に見えたら「済み」(飛び越しただけでは付けない。PC版の記録はこのブラウザの中)
    const atwork = document.getElementById("atwork");
    const ar = atwork?.getBoundingClientRect();
    if (!(ar && ar.top < innerHeight && ar.bottom > 0)) atworkSince = null;
    else if (atworkSince == null) { atworkSince = Date.now(); setTimeout(update, 3100); } // 止まって読んでいても 3 秒後に確かめ直す
    if (atwork && lessonId && ar.bottom > 0 && ar.bottom < innerHeight && atworkSince && Date.now() - atworkSince >= 3000 && !(seen[lessonId] || []).includes("atwork")) {
      seen[lessonId] = [...(seen[lessonId] || []), "atwork"];
      pcSave(SECTIONS_KEY, seen);
      markDone("atwork");
    }
    // 前回の続き(ダッシュボードの主役にする)
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      if (!lessonId) return;
      const end = document.querySelector(".lesson-nav")?.getBoundingClientRect().top < innerHeight;
      const top = document.getElementById(current)?.getBoundingClientRect().top ?? line;
      pcSave(LAST_KEY, { id: lessonId, title: h1.textContent.trim(), section: current, off: Math.round(line - top), end });
    }, 400);
    let key = sections[0].id;
    for (const sec of sections) if (sec.getBoundingClientRect().top <= line) key = sec.id;
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
  };
  let ticking = false;
  addEventListener("scroll", () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => { ticking = false; update(); });
  }, { passive: true });
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

function initRoot(root) {
  root.querySelectorAll("textarea.code-editor").forEach(initEditor);
  renderMermaid(root).then(() => highlight(root));
  root.querySelectorAll(".chat-log").forEach((log) => { log.scrollTop = log.scrollHeight; });
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
    li.querySelectorAll("label.choice").forEach((label, j) => {
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
  form.scrollIntoView({ behavior: reduceMotion() ? "auto" : "smooth" });
});

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
  form.querySelectorAll("label.choice").forEach((label, j) => {
    label.classList.toggle("is-answer", j === answer);
    label.classList.toggle("is-wrong", j === choice && !ok);
    label.querySelector(".choice-tag")?.remove();
    if (j === choice && !ok) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
    if (j === answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
  });
  form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = true; });
  const submit = form.querySelector("button[type=submit]");
  submit.hidden = true;
  let again = form.querySelector("[data-answer-reset]");
  if (!again) {
    submit.insertAdjacentHTML("afterend", `<button class="btn" type="button" data-answer-reset>もう一度解く</button>`);
    again = form.querySelector("[data-answer-reset]");
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
  out.hidden = false;
  const rec = readingRecord();
  rec[li.dataset.ref] = { ok, at: new Date().toISOString().slice(0, 10) };
  try { localStorage.setItem(READING_KEY, JSON.stringify(rec)); } catch (err) { console.warn("読解の記録を保存できませんでした", err); }
  readingStep();
});

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
initRoot(document);
trackLesson();
dashboardResume();
document.querySelectorAll(".today-title a, .lesson-hero h1").forEach(phraseWrap);
if (window.htmx) {
  window.htmx.onLoad((el) => initRoot(el));
}
