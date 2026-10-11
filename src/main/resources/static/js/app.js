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
      EditorView.theme({ "&": { fontSize: "14px" } }),
    ],
  });
  ta.style.display = "none";
  ta.parentNode.insertBefore(view.dom, ta);
  ta._cmView = view;
}

function setEditorText(ta, text) {
  ta.value = text;
  const view = ta._cmView;
  if (view) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } });
  }
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
      mermaidModule.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });
    }
    await mermaidModule.run({ nodes: root.querySelectorAll(".mermaid:not([data-processed])") });
  } catch (e) {
    console.warn("Mermaid を描画できませんでした", e);
  }
}

function highlight(root) {
  if (!window.hljs) return;
  root.querySelectorAll("pre code:not([data-highlighted])").forEach((el) => {
    if (!el.className.includes("language-")) el.classList.add("language-java");
    if (el.classList.contains("language-text")) return;
    window.hljs.highlightElement(el);
  });
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
if (window.htmx) {
  window.htmx.onLoad((el) => initRoot(el));
}
