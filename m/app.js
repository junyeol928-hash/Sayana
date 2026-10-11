// Java道場 スマホ版(オフライン対応)
// - 教材は data/ の JSON。Service Worker が全部保存するので、一度開けば電波が無くても読める
// - 学習記録(読んだ・クイズ・復習・演習の自己チェック)はこのスマホの中(localStorage)に保存する
// - AI 機能は、電波があって APIキーを設定したときだけ使える(キーもこのスマホの中だけに保存)
// - コードの実行と自動採点は Java が必要なので、PC版か Codespaces 版で行う

const STORE_KEY = "javadojo-m-v1";
const INTERVALS = [1, 3, 7, 14];
const SDK_URL = "https://esm.sh/@anthropic-ai/sdk@0.127.0";
const MARKED_URL = "https://cdn.jsdelivr.net/npm/marked@12.0.2/lib/marked.esm.js";
const MERMAID_URL = "https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs";
// チューター・ヒント=速さと質のバランスで Sonnet / レビュー=深く見てほしいので Opus
const DEFAULT_MODELS = { tutor: "claude-sonnet-5-5", review: "claude-opus-5-5", hint: "claude-sonnet-5-5" };
const MODELS_VERSION = 3;

// ------------------------------------------------------------ 保存

function emptyState() {
  return { read: {}, quiz: {}, review: {}, checklist: {}, exercise: {}, chat: {}, days: [], settings: {} };
}

function loadState() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    return Object.assign(emptyState(), raw ? JSON.parse(raw) : {});
  } catch (e) {
    return emptyState();
  }
}

let S = loadState();
migrateSettings();

function migrateSettings() {
  const from = S.settings.modelsVersion ?? 1;
  if (from >= MODELS_VERSION) return;
  const m = S.settings.models;
  // v1: 以前は「チューター・レビュー」が1つの欄で、レビューにも同じモデルが入っていた
  if (m && from < 2 && (!m.review || m.review === m.tutor)) {
    m.review = DEFAULT_MODELS.review;
  }
  // v2: ヒントの既定が Haiku だった(自分で別のモデルにしていたらそのまま)
  if (m && from < 3 && (!m.hint || m.hint === "claude-haiku-5-5")) {
    m.hint = DEFAULT_MODELS.hint;
  }
  S.settings.modelsVersion = MODELS_VERSION;
  save();
}

function save() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(S));
  } catch (e) {
    toast("このブラウザでは記録を保存できませんでした(プライベートブラウズでは保存されません)");
  }
}

// ------------------------------------------------------------ 小道具

const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function addDays(dateStr, n) {
  const [y, m, d] = dateStr.split("-").map(Number);
  const dt = new Date(y, m - 1, d + n);
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, "0")}-${String(dt.getDate()).padStart(2, "0")}`;
}

function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 3500);
}

function recordDay() {
  const t = today();
  if (!S.days.includes(t)) {
    S.days.push(t);
    S.days = S.days.slice(-400);
  }
  save();
  updateHeader();
}

function streak() {
  const set = new Set(S.days);
  let d = today();
  if (!set.has(d)) d = addDays(d, -1);
  let n = 0;
  while (set.has(d)) { n++; d = addDays(d, -1); }
  return n;
}

// ------------------------------------------------------------ 復習キュー(1→3→7→14日)

function practice(ref, type, lessonId, correct) {
  const t = today();
  const item = S.review[ref];
  if (!item) {
    S.review[ref] = { type, lessonId, stage: 0, due: addDays(t, INTERVALS[0]), lapses: correct ? 0 : 1, graduated: false };
  } else if (!correct) {
    Object.assign(item, { stage: 0, due: addDays(t, INTERVALS[0]), graduated: false, lapses: item.lapses + 1 });
  }
  save();
}

function reviewAnswer(ref, correct) {
  const item = S.review[ref];
  const t = today();
  if (!correct) {
    Object.assign(item, { stage: 0, due: addDays(t, INTERVALS[0]), graduated: false, lapses: item.lapses + 1 });
  } else {
    const next = item.stage + 1;
    if (next >= INTERVALS.length) {
      Object.assign(item, { stage: INTERVALS.length, graduated: true });
    } else {
      Object.assign(item, { stage: next, due: addDays(t, INTERVALS[next]) });
    }
  }
  save();
  return item;
}

function intervalLabel(item) {
  return item.graduated ? "卒業(もう復習しなくてOK)" : `${INTERVALS[Math.min(item.stage, 3)]}日後に復習`;
}

function dueItems() {
  const t = today();
  return Object.entries(S.review)
    .filter(([, it]) => !it.graduated && it.due <= t)
    .sort((a, b) => a[1].due.localeCompare(b[1].due));
}

// ------------------------------------------------------------ データ

const cache = { curriculum: null, prompts: null, lessons: {} };

async function getJson(path) {
  const res = await fetch(path);
  if (!res.ok) throw new Error(path + " を読めませんでした");
  return res.json();
}

async function curriculum() {
  return cache.curriculum ??= await getJson("data/curriculum.json");
}

async function lesson(id) {
  return cache.lessons[id] ??= await getJson(`data/lessons/${encodeURIComponent(id)}.json`);
}

async function prompts() {
  return cache.prompts ??= await getJson("data/prompts.json");
}

function lessonStatus(meta, data) {
  const quiz = S.quiz[meta.id];
  const exDone = data ? data.exercises.every((e) => S.exercise[e.key]?.selfPass) : false;
  const checks = data ? data.checklist.every((c) => (S.checklist[meta.id] || []).includes(c.key)) : false;
  const quizOk = meta.quiz === 0 || (quiz && quiz.best >= Math.ceil(meta.quiz * 0.8));
  if (data && exDone && quizOk && checks) return "done";
  if (S.read[meta.id] || quiz || Object.keys(S.exercise).some((k) => k.startsWith(meta.id + "/"))) return "doing";
  return "new";
}

// ------------------------------------------------------------ 画面

const view = () => $("#view");

async function render() {
  const hash = location.hash.replace(/^#/, "") || "/";
  document.body.classList.remove("sheet-open");
  view().onclick = null;
  document.querySelectorAll(".bottom-nav a").forEach((a) => a.classList.toggle("active", a.getAttribute("href") === "#" + hash));
  try {
    if (hash.startsWith("/lesson/")) {
      await renderLesson(decodeURIComponent(hash.slice("/lesson/".length)));
    } else if (hash === "/review") {
      await renderReview();
    } else if (hash === "/settings") {
      renderSettings();
    } else {
      await renderHome();
    }
  } catch (e) {
    console.error(e);
    view().innerHTML = `<div class="ai-error">⚠️ 読み込めませんでした: ${esc(e.message)}<br>
      電波が無いときは、一度電波のある所で開いてから使ってください(最初の1回で全レッスンが保存されます)。</div>`;
  }
  updateHeader();
}

function updateHeader() {
  $("#streak").innerHTML = `🔥 <b>${streak()}</b>日`;
  const net = $("#net");
  net.textContent = navigator.onLine ? "オンライン" : "オフライン";
  net.classList.toggle("off", !navigator.onLine);
  const due = dueItems().length;
  const badge = $("#due-count");
  badge.hidden = due === 0;
  badge.textContent = due;
}

async function renderHome() {
  const cur = await curriculum();
  const all = cur.parts.flatMap((p) => p.chapters.flatMap((c) => c.lessons));
  const done = all.filter((l) => S.quiz[l.id]?.best >= Math.ceil(l.quiz * 0.8)).length;
  const due = dueItems().length;
  const next = all.find((l) => !S.read[l.id]) || all[0];
  const standalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  view().innerHTML = `
    <div class="talk talk-sensei">
      <div class="talk-avatar"><span class="talk-icon">☕</span><span class="talk-name">モカ先輩</span></div>
      <div class="talk-bubble"><p>${streak() > 0 ? `連続 ${streak()} 日目。` : ""}スマホ版は電波が無くても読めるし、クイズと復習もできる。
      コードを動かして採点するのは PC版か Codespaces 版でね。</p></div>
    </div>
    <div class="dash-cards">
      <a class="stat card link-card" href="#/review"><div class="stat-label">🔁 今日の復習</div>
        <div class="stat-value">${due}<small>問</small></div></a>
      <div class="stat card"><div class="stat-label">📚 クイズ合格</div>
        <div class="stat-value">${done}<small> / ${all.length}</small></div></div>
    </div>
    ${next ? `<a class="btn primary" style="display:block;text-align:center;margin:12px 0" href="#/lesson/${esc(next.id)}">▶ 次はこれ:${esc(next.title)}</a>` : ""}
    ${standalone ? "" : `<div class="card install-hint small">📲 <b>ホーム画面に追加</b>するとアプリのように使えます。
      iPhone: Safari の共有ボタン → 「ホーム画面に追加」/ Android: Chrome のメニュー →「ホーム画面に追加」</div>`}
    ${cur.parts.map((p) => `
      <article class="part card">
        <h2>${esc(p.title)}</h2>
        <p class="muted small">${esc(p.description)}</p>
        ${p.chapters.map((c) => `
          <div class="chapter">
            <div class="chapter-head"><h3>${esc(c.title)}</h3></div>
            <ul class="lesson-list">
              ${c.lessons.map((l) => {
                const st = lessonStatus(l);
                const icon = S.quiz[l.id]?.best >= Math.ceil(l.quiz * 0.8) ? "✅" : st === "doing" ? "🟡" : "⚪";
                return `<li><span class="state-icon">${icon}</span><a class="lesson-link" href="#/lesson/${esc(l.id)}">${esc(l.title)}</a>
                  ${S.quiz[l.id] ? `<span class="small muted">クイズ ${S.quiz[l.id].best}/${l.quiz}</span>` : ""}</li>`;
              }).join("")}
            </ul>
          </div>`).join("")}
      </article>`).join("")}`;
}

function stars(n) {
  return "★".repeat(n) + "☆".repeat(3 - n);
}

async function renderLesson(id) {
  const L = await lesson(id);
  const checked = S.checklist[id] || [];
  const quiz = S.quiz[id];
  view().innerHTML = `
    <nav class="breadcrumb"><a href="#/">ホーム</a> › ${esc(L.part)} › ${esc(L.chapter)}</nav>
    <h1>${esc(L.title)}</h1>
    <p class="summary">${esc(L.summary)}</p>
    <p class="meta muted small">⏱ 読む目安 ${L.minutes} 分 ${quiz ? `・クイズ最高 ${quiz.best}/${L.quiz.length}` : ""}</p>
    ${L.goals.length ? `<div class="goals card"><div class="goals-title">🎯 このレッスンのゴール</div><ul>${L.goals.map((g) => `<li>${esc(g)}</li>`).join("")}</ul></div>` : ""}
    <nav class="stepper">
      <a href="javascript:void 0" data-scroll="read" class="step ${S.read[id] ? "done" : ""}"><span class="no">1</span>読む</a>
      ${L.samples.length ? `<a href="javascript:void 0" data-scroll="run" class="step"><span class="no">2</span>見る</a>` : ""}
      ${L.sts ? `<a href="javascript:void 0" data-scroll="sts" class="step ${L.checklist.length && L.checklist.every((c) => checked.includes(c.key)) ? "done" : ""}"><span class="no">STS</span>やってみる</a>` : ""}
      ${L.exercises.length ? `<a href="javascript:void 0" data-scroll="write" class="step ${L.exercises.some((e) => S.exercise[e.key]?.selfPass) ? "done" : ""}"><span class="no">3</span>書く</a>` : ""}
      <a href="javascript:void 0" data-scroll="quiz" class="step ${quiz && quiz.best >= Math.ceil(L.quiz.length * 0.8) ? "done" : ""}"><span class="no">4</span>確かめる</a>
    </nav>
    <section id="read" class="lesson-section">
      <article class="lesson-body md">${L.bodyHtml}</article>
      <div class="read-action">${S.read[id] ? `<div class="done">✅ 読了済み</div>` : `<button class="btn primary" data-action="read">📖 読んだ!</button>`}</div>
    </section>
    ${L.samples.length ? `
    <section id="run" class="lesson-section">
      <h2 class="section-title"><span class="no">2</span>サンプルを見る</h2>
      <p class="run-note">▶ スマホ版ではコードを実行できません。出力を予想しながら読んで、PC版か Codespaces 版で動かしてみよう。</p>
      ${L.samples.map((s) => `<div class="sample card"><h3>${esc(s.title)}</h3><div class="md small">${s.descriptionHtml}</div>
        <pre><code class="language-java">${esc(s.code)}</code></pre></div>`).join("")}
    </section>` : ""}
    ${L.sts || L.checklist.length ? `
    <section id="sts" class="lesson-section">
      <h2 class="section-title"><span class="no">STS</span>STSでやってみよう</h2>
      ${L.sts ? `<div class="md">${L.sts.introHtml}</div><ol class="sts-steps">${L.sts.steps.map((s) => `<li><div class="sts-step-title">${esc(s.title)}</div><div class="md">${s.detailHtml}</div></li>`).join("")}</ol>` : ""}
      ${L.checklist.length ? `<div class="checklist card"><div class="checklist-title">✅ やったらチェック</div><ul>
        ${L.checklist.map((c) => `<li class="check-item ${checked.includes(c.key) ? "checked" : ""}">
          <button class="check-button" data-action="check" data-key="${esc(c.key)}">${checked.includes(c.key) ? "✅" : "⬜"}</button>
          <span>${esc(c.text)}</span></li>`).join("")}</ul></div>` : ""}
    </section>` : ""}
    ${L.exercises.length ? `
    <section id="write" class="lesson-section">
      <h2 class="section-title"><span class="no">3</span>書く</h2>
      <p class="run-note">スマホ版は自動採点できないので、<b>採点テスト</b>を読んで自分でチェックし、「できた/まだ」を記録します。
      本物の採点は PC版か Codespaces 版で。電波と APIキーがあれば AI にも見てもらえます。</p>
      ${L.exercises.map((e, i) => exerciseCard(e, i)).join("")}
    </section>` : ""}
    <section id="quiz" class="lesson-section">
      <h2 class="section-title"><span class="no">4</span>確かめる</h2>
      <form class="quiz card" data-action="quiz">
        <ol>${L.quiz.map((q, i) => `<li class="quiz-q"><div class="md">${q.questionHtml}</div>
          ${q.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="q${i}" value="${j}"><span>${c}</span></label>`).join("")}</li>`).join("")}</ol>
        <button class="btn primary" type="submit">採点する</button>
      </form>
      <div id="quiz-result"></div>
    </section>
    ${L.columns.length ? `
    <section class="lesson-section">
      <h2 class="section-title">☕ コラム</h2>
      <div class="columns">${L.columns.map((c) => `<aside class="column card column-${esc(c.type)}"><div class="column-label">${esc(c.label)}</div>
        <h3>${esc(c.title)}</h3><div class="md">${c.bodyHtml}</div></aside>`).join("")}</div>
    </section>` : ""}
    <nav class="lesson-nav">
      ${L.prev ? `<a href="#/lesson/${esc(L.prev)}">← 前のレッスン</a>` : "<span></span>"}
      ${L.next ? `<a href="#/lesson/${esc(L.next)}">次のレッスン →</a>` : "<span></span>"}
    </nav>
    <button class="tutor-fab" data-action="tutor" aria-label="AIチューターに質問する">☕<span>質問</span></button>
    <div class="sheet-backdrop" data-action="tutor"></div>
    ${tutorSheet(L)}`;
  bindLesson(L);
  decorate(view());
}

function exerciseCard(e, i) {
  const st = S.exercise[e.key] || {};
  return `
    <article class="exercise card" data-key="${esc(e.key)}">
      <header class="exercise-head">
        <h3><span class="ex-label">演習</span>${esc(e.title)}</h3>
        <div class="exercise-meta"><span class="stars">${stars(e.difficulty)}</span>
          ${st.selfPass ? `<span class="pill ok">できた</span>` : ""}<code class="class-name">${esc(e.className)}.java</code></div>
      </header>
      <div class="problem md">${e.problemHtml}</div>
      <textarea class="code-input" spellcheck="false" autocapitalize="off" autocorrect="off" data-key="${esc(e.key)}">${esc(st.code ?? e.starter)}</textarea>
      <div class="actions">
        <button class="btn ghost small" data-action="reset-code" data-index="${i}">雛形に戻す</button>
        <button class="btn small" data-action="ai-hint" data-index="${i}">💡 AIヒント</button>
        <button class="btn small" data-action="ai-review" data-index="${i}">🤖 AIに見てもらう</button>
      </div>
      <details class="solution"><summary>採点テストを読む(何が確かめられるか)</summary><pre><code class="language-java">${esc(e.test)}</code></pre></details>
      <details class="solution"><summary>模範解答を見る(ネタバレ注意)</summary><pre><code class="language-java">${esc(e.solution)}</code></pre></details>
      <div class="self-check">
        <button class="btn" data-action="self" data-index="${i}" data-ok="1">✅ できた</button>
        <button class="btn" data-action="self" data-index="${i}" data-ok="0">🔁 まだ</button>
      </div>
      <div class="ai-slot"></div>
    </article>`;
}

function tutorSheet(L) {
  const msgs = S.chat[L.id] || [];
  return `
    <aside class="sheet tutor" id="tutor">
      <div class="tutor-head"><span>☕ AIチューター</span>
        <button class="link-button small" data-action="clear-chat" style="color:#f6e6d8">会話をクリア</button>
        <button class="tutor-close" data-action="tutor" aria-label="閉じる" style="display:inline-block">✕</button></div>
      <div class="chat-log" id="chat-log">
        <div class="chat-msg from-ai"><div class="chat-who">☕ モカ先輩(AI)</div>
          <div class="chat-body">分からないところは気軽に聞いて。答えをそのまま教えるんじゃなくて、考え方を一緒に整理するからね。</div></div>
        ${msgs.map(chatHtml).join("")}
      </div>
      <form class="chat-form" data-action="chat">
        <textarea name="q" rows="3" placeholder="例: なぜ int 同士の割り算は小数にならないの?" required></textarea>
        <div class="chat-actions">
          <label class="small"><input type="checkbox" name="withCode" checked> 書いているコードも見せる</label>
          <button class="btn primary small" type="submit">送信</button>
        </div>
      </form>
    </aside>`;
}

function chatHtml(m) {
  return m.role === "user"
    ? `<div class="chat-msg from-user"><div class="chat-who">あなた</div><div class="chat-body">${esc(m.content)}</div></div>`
    : `<div class="chat-msg from-ai"><div class="chat-who">☕ モカ先輩(AI)</div><div class="chat-body md" data-md="${esc(m.content)}">${esc(m.content)}</div></div>`;
}

let lastCode = "";

function bindLesson(L) {
  const root = view();
  root.querySelectorAll("textarea.code-input").forEach((ta) => {
    addSymbolBar(ta);
    ta.addEventListener("focus", () => { lastCode = ta.value; });
    ta.addEventListener("input", () => {
      lastCode = ta.value;
      const key = ta.dataset.key;
      S.exercise[key] = Object.assign(S.exercise[key] || {}, { code: ta.value });
      save();
    });
  });

  root.onclick = async (ev) => {
    const jump = ev.target.closest("[data-scroll]");
    if (jump) {
      ev.preventDefault();
      document.getElementById(jump.dataset.scroll)?.scrollIntoView({ behavior: "smooth" });
      return;
    }
    const el = ev.target.closest("[data-action]");
    if (!el || el.tagName === "FORM") return;
    const action = el.dataset.action;
    const ex = el.dataset.index != null ? L.exercises[Number(el.dataset.index)] : null;
    if (action === "read") {
      S.read[L.id] = true;
      recordDay();
      el.outerHTML = `<div class="done">✅ 読了!次はサンプルを読んで、演習に挑戦しよう。</div>`;
    } else if (action === "check") {
      const list = new Set(S.checklist[L.id] || []);
      list.has(el.dataset.key) ? list.delete(el.dataset.key) : list.add(el.dataset.key);
      S.checklist[L.id] = [...list];
      recordDay();
      const on = list.has(el.dataset.key);
      el.textContent = on ? "✅" : "⬜";
      el.closest("li").classList.toggle("checked", on);
    } else if (action === "reset-code") {
      if (!confirm("書いたコードを消して雛形に戻しますか?")) return;
      const ta = el.closest("article").querySelector("textarea");
      ta.value = ex.starter;
      S.exercise[ex.key] = Object.assign(S.exercise[ex.key] || {}, { code: ex.starter });
      save();
    } else if (action === "self") {
      const ok = el.dataset.ok === "1";
      S.exercise[ex.key] = Object.assign(S.exercise[ex.key] || {}, { selfPass: ok || S.exercise[ex.key]?.selfPass === true });
      practice(ex.key, "exercise", L.id, ok);
      recordDay();
      toast(ok ? "記録しました。1日後に復習で出ます" : "記録しました。明日もう一度やってみよう");
    } else if (action === "ai-review" || action === "ai-hint") {
      const card = el.closest("article");
      const code = card.querySelector("textarea").value;
      const slot = card.querySelector(".ai-slot");
      await runAi(slot, el, L, () => action === "ai-review" ? reviewRequest(L, ex, code) : hintRequest(L, ex, code));
    } else if (action === "tutor") {
      document.body.classList.toggle("sheet-open");
      const log = $("#chat-log");
      if (log) log.scrollTop = log.scrollHeight;
    } else if (action === "clear-chat") {
      if (!confirm("このレッスンの会話を消しますか?")) return;
      S.chat[L.id] = [];
      save();
      $("#chat-log").querySelectorAll(".chat-msg:not(:first-child)").forEach((n) => n.remove());
    }
  };

  root.querySelector("form[data-action=quiz]").onsubmit = (ev) => {
    ev.preventDefault();
    const form = ev.target;
    let score = 0;
    const rows = L.quiz.map((q, i) => {
      const v = form.querySelector(`input[name=q${i}]:checked`);
      const chosen = v ? Number(v.value) : -1;
      const ok = chosen === q.answer;
      if (ok) score++;
      if (chosen >= 0) practice(`${L.id}#${i}`, "quiz", L.id, ok);
      return `<li class="${ok ? "correct" : "wrong"}"><div class="q-mark">${ok ? "⭕ 正解" : chosen < 0 ? "— 未回答" : "❌ 不正解"}</div>
        <div class="md">${q.questionHtml}</div><p class="small">正解: <b>${q.choicesHtml[q.answer]}</b></p>
        <div class="explanation md">${q.explanationHtml}</div></li>`;
    });
    const prev = S.quiz[L.id];
    S.quiz[L.id] = { best: Math.max(score, prev?.best ?? 0), last: score };
    recordDay();
    const pass = Math.ceil(L.quiz.length * 0.8);
    $("#quiz-result").innerHTML = `<div class="quiz-result"><div class="quiz-score"><span class="big">${score} / ${L.quiz.length}</span>
      ${score === L.quiz.length ? "満点!🎉" : score >= pass ? "合格ライン突破!" : `合格ラインは ${pass} 問`}
      <p class="muted small">解いた問題は復習キューに入り、1日後 → 3日後 → 7日後 → 14日後 に出題されます。</p></div>
      <ol class="quiz-review">${rows.join("")}</ol></div>`;
    decorate($("#quiz-result"));
    $("#quiz-result").scrollIntoView({ behavior: "smooth" });
  };

  root.querySelector("form[data-action=chat]").onsubmit = async (ev) => {
    ev.preventDefault();
    const form = ev.target;
    const q = form.q.value.trim();
    if (!q) return;
    const code = form.withCode.checked ? lastCode : "";
    const log = $("#chat-log");
    const history = (S.chat[L.id] || []).slice(-20);
    const pending = document.createElement("div");
    pending.className = "chat-msg from-user";
    pending.innerHTML = `<div class="chat-who">あなた</div><div class="chat-body">${esc(q)}</div>`;
    log.appendChild(pending);
    const thinking = document.createElement("div");
    thinking.className = "muted small";
    thinking.textContent = "モカ先輩が考え中…";
    log.appendChild(thinking);
    log.scrollTop = log.scrollHeight;
    const button = form.querySelector("button");
    button.disabled = true;
    try {
      const text = await askClaude("tutor", L, [...history.map((m) => ({ role: m.role, content: m.content })),
        { role: "user", content: (code ? "【いまエディタにある私のコード】\n```java\n" + code.trim() + "\n```\n\n" : "") + q }], "low");
      S.chat[L.id] = [...(S.chat[L.id] || []), { role: "user", content: q }, { role: "assistant", content: text }];
      save();
      thinking.remove();
      log.insertAdjacentHTML("beforeend", chatHtml({ role: "assistant", content: text }));
      form.q.value = "";
      await renderMarkdown(log);
    } catch (e) {
      thinking.className = "ai-error";
      thinking.textContent = "⚠️ " + e.message;
      pending.remove();
    } finally {
      button.disabled = false;
      log.scrollTop = log.scrollHeight;
    }
  };
  renderMarkdown(root.querySelector("#chat-log"));
}

// エディタの上に記号キーを並べる(スマホのキーボードで打ちにくい記号)
const SYMBOLS = [["⇥", "    "], ["{", "{"], ["}", "}"], ["(", "("], [")", ")"], [";", ";"], ['"', '"'], ["=", "="],
  ["+", "+"], ["<", "<"], [">", ">"], ["[", "["], ["]", "]"], [".", "."], ["!", "!"], ["&", "&"], ["|", "|"], ["'", "'"], ["%", "%"], ["_", "_"]];

function addSymbolBar(ta) {
  const bar = document.createElement("div");
  bar.className = "symbol-bar";
  for (const [label, text] of SYMBOLS) {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = label;
    b.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      ta.focus();
      ta.setRangeText(text, ta.selectionStart, ta.selectionEnd, "end");
      ta.dispatchEvent(new Event("input"));
    });
    bar.appendChild(b);
  }
  ta.parentNode.insertBefore(bar, ta);
}

// ------------------------------------------------------------ 復習

async function renderReview() {
  const due = dueItems();
  const upcoming = Object.entries(S.review).filter(([, it]) => !it.graduated && it.due > today()).length;
  if (due.length === 0) {
    view().innerHTML = `<h1>🔁 復習</h1>
      <div class="card empty-state"><div class="talk talk-sensei"><div class="talk-avatar"><span class="talk-icon">☕</span><span class="talk-name">モカ先輩</span></div>
      <div class="talk-bubble"><p>今日の復習はおしまい!この先 ${upcoming} 問が順番に出てくるよ。</p></div></div>
      <a class="btn primary" href="#/">ホームへ</a></div>`;
    return;
  }
  const [ref, item] = due[0];
  const L = await lesson(item.lessonId);
  const head = `<h1>🔁 復習</h1><p class="review-progress">残り <b>${due.length}</b> 問 ・
    <a href="#/lesson/${esc(L.id)}">${esc(L.title)}</a> <span class="pill small">段階 ${item.stage + 1}/4</span></p>`;
  if (item.type === "quiz") {
    const q = L.quiz[Number(ref.split("#")[1])];
    if (!q) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<div class="card review-quiz"><div class="md">${q.questionHtml}</div>
      <form id="rq">${q.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="c" value="${j}" required><span>${c}</span></label>`).join("")}
      <button class="btn primary" type="submit">答える</button></form><div id="ra"></div></div>`;
    $("#rq").onsubmit = (ev) => {
      ev.preventDefault();
      const choice = Number(new FormData(ev.target).get("c"));
      const ok = choice === q.answer;
      const it = reviewAnswer(ref, ok);
      recordDay();
      ev.target.querySelectorAll("input,button").forEach((x) => { x.disabled = true; });
      $("#ra").innerHTML = `<div class="quiz-answer ${ok ? "correct" : "wrong"}"><div class="q-mark big">${ok ? "⭕ 正解!" : "❌ 不正解"}</div>
        <p>正解: <b>${q.choicesHtml[q.answer]}</b></p><div class="explanation md">${q.explanationHtml}</div>
        <p class="review-note">次回: <b>${intervalLabel(it)}</b></p><button class="btn primary" id="next">次の復習へ →</button></div>`;
      decorate($("#ra"));
      $("#next").onclick = () => renderReview();
    };
  } else {
    const e = L.exercises.find((x) => x.key === ref);
    if (!e) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<p class="muted small">何も見ずに雛形から書いてみよう。書けたら模範解答と見比べて「できた/まだ」を選びます。</p>
      <article class="exercise card"><h3>${esc(e.title)}</h3><div class="problem md">${e.problemHtml}</div>
      <textarea class="code-input" spellcheck="false" autocapitalize="off" autocorrect="off">${esc(e.starter)}</textarea>
      <details class="solution"><summary>模範解答と見比べる</summary><pre><code class="language-java">${esc(e.solution)}</code></pre></details>
      <div class="self-check"><button class="btn" data-ok="1">✅ できた</button><button class="btn" data-ok="0">🔁 まだ</button></div>
      <div id="ra"></div></article>`;
    addSymbolBar(view().querySelector("textarea"));
    view().querySelectorAll(".self-check .btn").forEach((b) => {
      b.onclick = () => {
        const it = reviewAnswer(ref, b.dataset.ok === "1");
        recordDay();
        $("#ra").innerHTML = `<p class="review-note">次回: <b>${intervalLabel(it)}</b></p><button class="btn primary" id="next">次の復習へ →</button>`;
        $("#next").onclick = () => renderReview();
      };
    });
  }
  decorate(view());
}

// ------------------------------------------------------------ 設定

function renderSettings() {
  const st = S.settings;
  const models = Object.assign({}, DEFAULT_MODELS, st.models || {});
  view().innerHTML = `
    <h1>⚙️ 設定</h1>
    <section class="card settings">
      <div class="card-title">🤖 AI機能(Claude API)</div>
      <p class="small">APIキーは<b>このスマホのブラウザの中だけ</b>に保存され、Claude API(api.anthropic.com)にだけ送られます。
      GitHub やこのサイトには送られません。他の人とスマホを共有している場合は保存しないでください。</p>
      <label for="key">APIキー</label>
      <input id="key" type="password" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="sk-ant-..." value="${esc(st.apiKey || "")}">
      <div class="actions">
        <button class="btn primary" id="save-key">保存</button>
        <button class="btn" id="test-key">接続テスト</button>
        <button class="btn ghost" id="del-key">削除</button>
      </div>
      <div id="key-result"></div>
      <details class="steps-help"><summary><b>APIキーの取り方(スマホでできます)</b></summary>
        <ol>
          <li>ブラウザで <a href="https://console.anthropic.com/" target="_blank" rel="noopener">console.anthropic.com</a> を開き、アカウントを作るかログインする</li>
          <li>料金(Billing / Plans &amp; Billing)の画面でクレジットを購入する(API は前払い制。最初は少額で十分)</li>
          <li>「API Keys」の画面で「Create Key」を押し、名前(例: java-dojo-phone)を付けて作る</li>
          <li>表示されたキー(sk-ant- で始まる)をコピーする。<b>この画面を閉じると二度と表示されない</b>ので、すぐ次へ</li>
          <li>この画面の「APIキー」欄に貼り付けて「保存」→「接続テスト」</li>
        </ol>
        <p class="small muted">画面の表記は変わることがあります。もしキーが漏れたら、同じ API Keys の画面で無効化(削除)して作り直してください。
        もっと詳しい手順(Codespaces 版への登録も)は <a href="https://github.com/junyeol928-hash/Sayana#readme" target="_blank" rel="noopener">README の「APIキーをスマホで設定する」</a>。</p>
      </details>
      <label>モデル</label>
      <p class="small muted">機能ごとに使うモデルです。Opus は Sonnet の約2倍の料金ですが、より深く見てくれます。</p>
      <p class="small">AIチューター(質問への回答):<input type="text" id="m-tutor" value="${esc(models.tutor)}" autocapitalize="off"></p>
      <p class="small">コードレビュー(AIに見てもらう):<input type="text" id="m-review" value="${esc(models.review)}" autocapitalize="off"></p>
      <p class="small">AIヒント:<input type="text" id="m-hint" value="${esc(models.hint)}" autocapitalize="off"></p>
      <p class="small muted">例: claude-opus-5-5 / claude-sonnet-5-5 / claude-haiku-5-5
        <button class="link-button small" id="reset-models" type="button">おすすめに戻す</button></p>
      <button class="btn small" id="save-models">モデルを保存</button>
    </section>
    <section class="card settings">
      <div class="card-title">📦 学習記録</div>
      <p class="small">記録はこのスマホの中にあります。機種変更やブラウザの切り替えのときは、書き出して貼り付けてください(APIキーは含みません)。</p>
      <div class="actions"><button class="btn" id="export">書き出す(コピー)</button><button class="btn" id="import">貼り付けて読み込む</button></div>
      <textarea id="backup" placeholder="ここに書き出されます / 読み込む記録を貼り付け"></textarea>
      <button class="btn ghost small" id="reset">学習記録をすべて消す</button>
    </section>
    <section class="card">
      <div class="card-title">📶 オフライン</div>
      <p class="small" id="offline-status">確認中…</p>
      <p class="small muted">コードの実行と自動採点は Java が必要なので、PC版か GitHub Codespaces 版で行います(README の「スマホで使う」)。</p>
    </section>`;
  const keyInput = $("#key");
  $("#save-key").onclick = () => { S.settings.apiKey = keyInput.value.trim(); save(); toast("APIキーを保存しました"); };
  $("#del-key").onclick = () => { delete S.settings.apiKey; keyInput.value = ""; save(); toast("APIキーを削除しました"); };
  $("#test-key").onclick = async () => {
    S.settings.apiKey = keyInput.value.trim();
    save();
    const out = $("#key-result");
    out.innerHTML = `<p class="muted small">接続しています…</p>`;
    try {
      const client = await anthropic();
      const res = await client.messages.create({ model: models.hint, max_tokens: 200,
        messages: [{ role: "user", content: "「接続OK」とだけ返してください。" }] });
      out.innerHTML = `<p class="done">✅ つながりました(${esc(res.model)}): ${esc(textOf(res))}</p>`;
    } catch (e) {
      out.innerHTML = `<div class="ai-error">⚠️ ${esc(friendlyError(e))}</div>`;
    }
  };
  $("#save-models").onclick = () => {
    S.settings.models = { tutor: $("#m-tutor").value.trim() || DEFAULT_MODELS.tutor,
      review: $("#m-review").value.trim() || DEFAULT_MODELS.review, hint: $("#m-hint").value.trim() || DEFAULT_MODELS.hint };
    save();
    toast("モデルを保存しました");
  };
  $("#reset-models").onclick = () => {
    $("#m-tutor").value = DEFAULT_MODELS.tutor;
    $("#m-review").value = DEFAULT_MODELS.review;
    $("#m-hint").value = DEFAULT_MODELS.hint;
    S.settings.models = { ...DEFAULT_MODELS };
    save();
    toast("おすすめのモデルに戻しました");
  };
  $("#export").onclick = async () => {
    const data = Object.assign({}, S, { settings: Object.assign({}, S.settings, { apiKey: undefined }) });
    const text = JSON.stringify(data);
    $("#backup").value = text;
    try { await navigator.clipboard.writeText(text); toast("コピーしました"); } catch (e) { toast("下の欄の文字を長押ししてコピーしてください"); }
  };
  $("#import").onclick = () => {
    try {
      const data = JSON.parse($("#backup").value);
      const key = S.settings.apiKey;
      S = Object.assign(emptyState(), data);
      S.settings = Object.assign({}, data.settings || {}, { apiKey: key });
      save();
      toast("読み込みました");
      updateHeader();
    } catch (e) {
      toast("読み込めませんでした。書き出した文字をそのまま貼り付けてください");
    }
  };
  $("#reset").onclick = () => {
    if (!confirm("学習記録をすべて消します。よろしいですか?(APIキーは残ります)")) return;
    S = Object.assign(emptyState(), { settings: S.settings });
    save();
    toast("消しました");
    updateHeader();
  };
  offlineStatus();
}

async function offlineStatus() {
  const el = $("#offline-status");
  if (!("serviceWorker" in navigator)) { el.textContent = "このブラウザはオフライン保存に対応していません。"; return; }
  const keys = await caches.keys();
  const name = keys.find((k) => k.startsWith("javadojo-m-") && k !== "javadojo-m-runtime");
  if (!name) { el.textContent = "まだ保存されていません。電波のある所で一度開き直してください。"; return; }
  const n = (await (await caches.open(name)).keys()).length;
  el.textContent = `✅ 全レッスンをこのスマホに保存済みです(${n}ファイル)。電波が無くても読めます。`;
}

// ------------------------------------------------------------ AI(Claude API)

async function anthropic() {
  const key = S.settings.apiKey;
  if (!key) throw new Error("APIキーが設定されていません。下の「設定」から登録してください。");
  if (!navigator.onLine) throw new Error("オフラインです。AI は電波があるときに使えます。");
  const { default: Anthropic } = await import(SDK_URL);
  // スマホのブラウザから直接呼ぶ(キーはこの端末の中だけ)
  return new Anthropic({ apiKey: key, dangerouslyAllowBrowser: true, maxRetries: 1 });
}

function textOf(res) {
  return res.content.filter((b) => b.type === "text").map((b) => b.text).join("\n").trim();
}

function friendlyError(e) {
  const status = e?.status;
  if (status === 401) return "APIキーが無効です。設定のキーを確認してください。";
  if (status === 403) return "このAPIキーでは使えません(権限)。";
  if (status === 404) return "モデルが見つかりません。設定のモデル名を確認してください。";
  if (status === 429) return "利用上限に達しました。少し待つか、残高(クレジット)を確認してください。";
  if (status >= 500) return "Claude API 側で一時的なエラーが起きました。少し待ってから試してください。";
  if (status === 400) return "リクエストが受け付けられませんでした: " + (e.message || "");
  return e?.message || String(e);
}

/** feature: tutor / review / hint / explain-error */
async function askClaude(feature, L, messages, effort) {
  const P = await prompts();
  const models = Object.assign({}, DEFAULT_MODELS, S.settings.models || {});
  const model = feature === "hint" ? models.hint : feature === "review" ? models.review : models.tutor;
  try {
    const client = await anthropic();
    const res = await client.messages.create({
      model,
      // 考える時間(thinking)も上限に含まれるので、深く考えるレビューは多めにとる
      max_tokens: feature === "review" ? 12000 : 4000,
      // レッスン教材はキャッシュ対象(同じレッスンなら2回目以降は安く速い)
      system: [
        { type: "text", text: L.aiContext, cache_control: { type: "ephemeral" } },
        { type: "text", text: P[feature] },
      ],
      messages,
      output_config: { effort },
    });
    if (res.stop_reason === "refusal") return "(この内容には回答できませんでした。言い回しを変えて試してください)";
    return textOf(res) + (res.stop_reason === "max_tokens" ? "\n\n(長くなったので途中で切れました)" : "");
  } catch (e) {
    throw new Error(friendlyError(e));
  }
}

function reviewRequest(L, e, code) {
  const msg = `【演習】${e.title}(クラス名: ${e.className})\n${e.problemMarkdown.trim()}\n\n【私のコード】\n\`\`\`java\n${code.trim()}\n\`\`\`\n\n` +
    `【採点テスト】(スマホなので自動採点はしていません。このテストに合格しそうかも見てください)\n\`\`\`java\n${e.test.trim()}\n\`\`\`\n\n` +
    `【模範解答(参考。学習者にはまだ見せていない可能性があるので、丸写しを促さないこと)】\n\`\`\`java\n${e.solution.trim()}\n\`\`\`\n\n` +
    "総評の最初に、採点テストに合格しそうかどうか(しそうにない場合はどのテストが落ちそうか)を1〜2文で書いてから、講評をお願いします。";
  return { feature: "review", messages: [{ role: "user", content: msg }], effort: "medium", title: "AIコードレビュー" };
}

function hintRequest(L, e, code) {
  const st = S.exercise[e.key] || {};
  const hints = st.hints || [];
  if (hints.length >= 3) return { done: true };
  const msg = `【演習】${e.title}(クラス名: ${e.className})\n${e.problemMarkdown.trim()}\n\n【私のいまのコード】\n\`\`\`java\n${code.trim()}\n\`\`\`\n\n` +
    (hints.length ? "【これまでに出たヒント】\n" + hints.map((h, i) => `- レベル${i + 1}: ${h.replace(/\n/g, " ")}`).join("\n") + "\n\n" : "") +
    `レベル${hints.length + 1}のヒントをください。`;
  return { feature: "hint", messages: [{ role: "user", content: msg }], effort: "low", title: `ヒント${hints.length + 1}`, exerciseKey: e.key };
}

async function runAi(slot, button, L, makeRequest) {
  const req = makeRequest();
  if (req.done) {
    slot.innerHTML = `<div class="ai-error">ヒントは3段階で全部です。模範解答かAIチューターを使ってみよう。</div>`;
    return;
  }
  button.disabled = true;
  slot.innerHTML = `<p class="muted small">AIが読んでいます…</p>`;
  try {
    const text = await askClaude(req.feature, L, req.messages, req.effort);
    if (req.exerciseKey) {
      const st = S.exercise[req.exerciseKey] = S.exercise[req.exerciseKey] || {};
      st.hints = [...(st.hints || []), text];
      save();
    }
    slot.innerHTML = `<div class="ai-answer"><div class="ai-answer-title">🤖 ${esc(req.title)}</div><div class="md" data-md="${esc(text)}">${esc(text)}</div></div>`;
    await renderMarkdown(slot);
  } catch (e) {
    slot.innerHTML = `<div class="ai-error">⚠️ ${esc(e.message)}</div>`;
  } finally {
    button.disabled = false;
  }
}

// AI の回答(Markdown)を描画する。生HTMLはエスケープする
let markedModule = null;

async function renderMarkdown(root) {
  if (!root) return;
  const nodes = root.querySelectorAll("[data-md]");
  if (nodes.length === 0) return;
  try {
    if (!markedModule) {
      markedModule = await import(MARKED_URL);
      markedModule.marked.use({ renderer: { html: (token) => esc(typeof token === "string" ? token : token.text) } });
    }
    nodes.forEach((n) => {
      n.innerHTML = markedModule.marked.parse(n.dataset.md);
      n.removeAttribute("data-md");
    });
    decorate(root);
  } catch (e) {
    nodes.forEach((n) => { n.style.whiteSpace = "pre-wrap"; });
  }
}

// ------------------------------------------------------------ 図とコードの色付け

let mermaidModule = null;

async function decorate(root) {
  const blocks = root.querySelectorAll("pre > code.language-mermaid");
  blocks.forEach((code) => {
    const div = document.createElement("div");
    div.className = "mermaid";
    div.textContent = code.textContent;
    code.parentElement.replaceWith(div);
  });
  if (root.querySelector(".mermaid:not([data-processed])")) {
    try {
      if (!mermaidModule) {
        mermaidModule = (await import(MERMAID_URL)).default;
        mermaidModule.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });
      }
      await mermaidModule.run({ nodes: root.querySelectorAll(".mermaid:not([data-processed])") });
    } catch (e) {
      // 図を描けないとき(初回オフラインなど)は、図の元のテキストのまま見せる
      root.querySelectorAll(".mermaid:not([data-processed])").forEach((d) => { d.style.whiteSpace = "pre"; d.style.textAlign = "left"; });
    }
  }
  if (window.hljs) {
    root.querySelectorAll("pre code:not([data-highlighted])").forEach((el) => {
      if (!el.className.includes("language-")) el.classList.add("language-java");
      if (!el.classList.contains("language-text")) window.hljs.highlightElement(el);
    });
  }
}

// ------------------------------------------------------------ 起動

// 画面の切り替えは「#/…」。本文中の「#見出し」のようなリンクは、画面を変えずにその場所へスクロールする
let currentRoute = location.hash.startsWith("#/") ? location.hash : "#/";
window.addEventListener("hashchange", () => {
  if (location.hash && !location.hash.startsWith("#/")) {
    const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    history.replaceState(null, "", currentRoute);
    target?.scrollIntoView({ behavior: "smooth" });
    return;
  }
  currentRoute = location.hash || "#/";
  render();
  window.scrollTo(0, 0);
});
window.addEventListener("online", updateHeader);
window.addEventListener("offline", updateHeader);
window.addEventListener("load", () => decorate(document));

if ("serviceWorker" in navigator) {
  const hadController = !!navigator.serviceWorker.controller;
  let reloaded = false;
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    // 新しい版の Service Worker に入れ替わったら、1回だけ読み直して新しい画面にする
    if (hadController && !reloaded) {
      reloaded = true;
      location.reload();
    }
  });
  navigator.serviceWorker.register("sw.js").catch((e) => console.warn("Service Worker を登録できませんでした", e));
}

render();
