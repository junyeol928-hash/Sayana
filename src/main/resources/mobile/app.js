// Java道場 スマホ版(オフライン対応)
// - 教材は data/ の JSON。Service Worker が全部保存するので、一度開けば電波が無くても読める
// - 学習記録(読んだ・クイズ・読解・復習・演習の自己チェック)はこのスマホの中(localStorage)に保存する
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
// 染まり具合の 5 段(docs/design.md)。色だけで伝えないよう、名前と意味を必ず添える
const DYE = [
  { name: "生成り", meaning: "まだ" },
  { name: "甕覗き", meaning: "読んだ" },
  { name: "縹", meaning: "クイズ合格" },
  { name: "藍", meaning: "演習もできた" },
  { name: "褐", meaning: "復習をすべて卒業" },
];

// ------------------------------------------------------------ 保存

function emptyState() {
  return { read: {}, quiz: {}, reading: {}, review: {}, checklist: {}, exercise: {}, chat: {}, days: [], settings: {} };
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
applyTheme();

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

/** 表示の明るさ: auto(端末に合わせる)/ light / dark */
function applyTheme() {
  const t = S.settings.theme;
  if (t === "light" || t === "dark") document.documentElement.dataset.theme = t;
  else delete document.documentElement.dataset.theme;
}

// ------------------------------------------------------------ 小道具

const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const icon = (name, cls = "") => `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;

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

const cache = { curriculum: null, prompts: null, glossary: null, reading: null, lessons: {} };

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

async function glossary() {
  return cache.glossary ??= await getJson("data/glossary.json");
}

async function readingAll() {
  return cache.reading ??= await getJson("data/reading.json");
}

function allLessons(cur) {
  return cur.parts.flatMap((p) => p.chapters.flatMap((c) => c.lessons.map((l) => ({ ...l, part: p, chapter: c }))));
}

const quizPassed = (meta) => meta.quiz === 0 || (S.quiz[meta.id]?.best ?? -1) >= Math.ceil(meta.quiz * 0.8);

/** 染まり具合(0〜4)。meta は curriculum.json のレッスン */
function dyeLevel(meta) {
  const read = !!S.read[meta.id];
  const passed = S.quiz[meta.id] && quizPassed(meta);
  if (!passed) return read || S.quiz[meta.id] ? 1 : 0;
  const selfPassed = Object.entries(S.exercise).filter(([k, v]) => k.startsWith(meta.id + "/") && v.selfPass).length;
  if (selfPassed < meta.exercises) return 2;
  const items = Object.values(S.review).filter((it) => it.lessonId === meta.id);
  return items.length > 0 && items.every((it) => it.graduated) ? 4 : 3;
}

const dyeSwatch = (level, extra = "") => `<span class="dye dye-${level} ${extra}" role="img" aria-label="${DYE[level].name}(${DYE[level].meaning})"></span>`;

// ------------------------------------------------------------ 画面

const view = () => $("#view");

async function render() {
  const hash = location.hash.replace(/^#/, "") || "/";
  document.body.classList.remove("sheet-open");
  view().onclick = null;
  const tab = hash.startsWith("/lesson/") ? "/" : hash;
  document.querySelectorAll(".bottom-nav a").forEach((a) => {
    if (a.getAttribute("href") === "#" + tab) a.setAttribute("aria-current", "page");
    else a.removeAttribute("aria-current");
  });
  try {
    if (hash.startsWith("/lesson/")) {
      await renderLesson(decodeURIComponent(hash.slice("/lesson/".length)));
    } else if (hash === "/review") {
      await renderReview();
    } else if (hash === "/reading") {
      await renderReadingPage();
    } else if (hash === "/terms") {
      await renderGlossary();
    } else if (hash === "/settings") {
      renderSettings();
    } else {
      await renderHome();
    }
  } catch (e) {
    console.error(e);
    view().innerHTML = `<div class="ai-error">読み込めませんでした: ${esc(e.message)}<br>
      電波が無いときは、一度電波のある所で開いてから使ってください(最初の1回で全レッスンが保存されます)。</div>`;
  }
  updateHeader();
}

function updateHeader() {
  $("#streak").innerHTML = `連続 <b>${streak()}</b> 日`;
  const net = $("#net");
  net.textContent = navigator.onLine ? "オンライン" : "オフライン";
  net.classList.toggle("off", !navigator.onLine);
  const due = dueItems().length;
  const badge = $("#due-count");
  badge.hidden = due === 0;
  badge.textContent = due;
}

// ------------------------------------------------------------ ホーム

async function renderHome() {
  const cur = await curriculum();
  const all = allLessons(cur);
  const levels = Object.fromEntries(all.map((l) => [l.id, dyeLevel(l)]));
  const due = dueItems().length;
  const next = all.find((l) => levels[l.id] === 0) || all.find((l) => levels[l.id] < 2) || all[0];
  const counts = [0, 0, 0, 0, 0];
  all.forEach((l) => counts[levels[l.id]]++);
  const deep = counts[2] + counts[3] + counts[4];
  const upcoming = Object.values(S.review).filter((it) => !it.graduated && it.due > today()).length;
  const readingDone = Object.keys(S.reading).length;
  const readingTotal = all.reduce((s, l) => s + (l.reading || 0), 0);
  const standalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  const nextIndex = all.indexOf(next) + 1;
  view().innerHTML = `
    <section class="today" aria-labelledby="today-title">
      <p class="today-kicker"><span>今日の稽古</span><span>${esc(next.part.title.replace(/^第\d部\s*/, ""))} · ${esc(next.chapter.title)}</span></p>
      <h1 class="today-title" id="today-title"><a href="#/lesson/${esc(next.id)}">${esc(next.title)}</a></h1>
      <p class="today-meta">${nextIndex} / ${all.length} レッスン目 · 読む ${next.minutes} 分 · 演習 ${next.exercises} · クイズ ${next.quiz}${next.reading ? ` · 読解 ${next.reading}` : ""}</p>
      <div class="today-actions"><a class="btn primary" href="#/lesson/${esc(next.id)}">${levels[next.id] ? "続きから稽古する" : "稽古をはじめる"}</a></div>
      <a class="today-review" href="#/review"><span>今日の復習</span>
        <span>${due ? `<b>${due}</b>問 ${icon("arrow", "sm")}` : `<span class="zero">今日はなし${upcoming ? ` · この先 ${upcoming} 問` : ""}</span>`}</span></a>
    </section>

    <section class="progress-block" aria-labelledby="dye-title">
      <div class="progress-head">
        <h2 id="dye-title">染まり具合</h2>
        <p><b>${deep}</b> / ${all.length} が縹より深い</p>
      </div>
      <div class="dye-strip" role="list">
        ${all.map((l) => `<a role="listitem" href="#/lesson/${esc(l.id)}" title="${esc(l.title)}:${DYE[levels[l.id]].meaning}">${dyeSwatch(levels[l.id])}<span class="visually-hidden">${esc(l.title)}</span></a>`).join("")}
      </div>
      <ul class="dye-legend">${DYE.map((d, i) => `<li>${dyeSwatch(i)}${d.meaning} ${counts[i]}</li>`).join("")}</ul>
      <div class="home-stats">
        <a href="#/review"><div class="stat-label">今日の復習</div><div class="stat-value">${due}<small>問</small></div></a>
        <a href="#/reading"><div class="stat-label">読解</div><div class="stat-value">${readingDone}<small>/ ${readingTotal}</small></div></a>
        <div><div class="stat-label">連続</div><div class="stat-value">${streak()}<small>日</small></div></div>
      </div>
      ${standalone ? "" : `<div class="install-hint">${icon("phone")}<p><b>ホーム画面に追加</b>するとアプリのように開けます。
        iPhone は Safari の共有ボタン →「ホーム画面に追加」、Android は Chrome のメニュー →「ホーム画面に追加」。</p></div>`}
    </section>

    <section class="curriculum" aria-label="レッスン一覧">
      ${cur.parts.map((p, pi) => `
        <div class="part-block">
          <div class="part-tag" aria-hidden="true">${["第一部", "第二部", "第三部", "第四部"][pi] ?? esc(p.title)}</div>
          <div>
            <h2 class="part-title">${esc(p.title)}</h2>
            <p class="part-desc">${esc(p.description)}</p>
            ${p.chapters.map((c) => `
              <h3 class="chapter-name"><span>${esc(c.title)}</span></h3>
              <ul class="lesson-rows">
                ${c.lessons.map((l) => {
                  const isNext = l.id === next.id;
                  return `<li class="lesson-row ${isNext ? "is-next" : ""}"><a href="#/lesson/${esc(l.id)}">${dyeSwatch(levels[l.id])}
                    <span class="row-title">${isNext ? "<b>今日の稽古</b>" : ""}${esc(l.title)}</span>
                    <span class="row-meta">${S.quiz[l.id] ? `クイズ ${S.quiz[l.id].best}/${l.quiz}` : `${l.minutes}分`}</span></a></li>`;
                }).join("")}
              </ul>`).join("")}
          </div>
        </div>`).join("")}
    </section>`;
}

function stars(n) {
  return "★".repeat(n) + "☆".repeat(3 - n);
}

// ------------------------------------------------------------ レッスン

async function lessonMeta(id) {
  const cur = await curriculum();
  return allLessons(cur).find((l) => l.id === id);
}

async function renderLesson(id) {
  const [L, meta, terms] = await Promise.all([lesson(id), lessonMeta(id), glossary().catch(() => [])]);
  const myTerms = terms.filter((t) => t.lesson === id);
  const checked = S.checklist[id] || [];
  const quiz = S.quiz[id];
  const level = meta ? dyeLevel(meta) : 0;
  const steps = [
    ["read", "読む", !!S.read[id]],
    L.atWork?.length ? ["atwork", "仕事のコード", false] : null,
    L.samples.length ? ["run", "見る", false] : null,
    L.sts || L.checklist.length ? ["sts", "STS", L.checklist.length > 0 && L.checklist.every((c) => checked.includes(c.key))] : null,
    L.exercises.length ? ["write", "書く", L.exercises.some((e) => S.exercise[e.key]?.selfPass)] : null,
    L.reading?.length ? ["reading", "読解", L.reading.some((_, i) => S.reading[`${id}@${i}`]?.ok)] : null,
    ["quiz", "確かめる", !!(quiz && quiz.best >= Math.ceil(L.quiz.length * 0.8))],
  ].filter(Boolean);
  let no = 0;
  const title = (key, text) => `<h2 class="section-title" id="${key}-title"><span class="no">${String(++no).padStart(2, "0")}</span>${text}</h2>`;
  view().innerHTML = `
    <header class="lesson-hero">
      <nav class="breadcrumb" aria-label="現在地"><a href="#/">ホーム</a> / ${esc(L.part)} / ${esc(L.chapter)}</nav>
      <h1>${esc(L.title)}</h1>
      <p class="summary">${esc(L.summary)}</p>
      <p class="meta"><span class="lesson-dye">${dyeSwatch(level, "lesson-swatch")} <span class="dye-name">${DYE[level].name}・${DYE[level].meaning}</span></span>
        <span>読む ${L.minutes} 分</span>${quiz ? `<span>クイズ最高 ${quiz.best}/${L.quiz.length}</span>` : ""}</p>
      ${L.goals.length ? `<div class="goals"><div class="goals-title">このレッスンのゴール</div><ul>${L.goals.map((g) => `<li>${esc(g)}</li>`).join("")}</ul></div>` : ""}
    </header>
    <nav class="stepper" aria-label="このレッスンの段">
      ${steps.map(([key, label, done]) => `<a href="#${key}" data-scroll="${key}" class="step ${done ? "done" : ""}">${label}</a>`).join("")}
    </nav>
    <section id="read" class="lesson-section" aria-labelledby="read-title">
      <h2 class="visually-hidden" id="read-title">解説</h2>
      <article class="lesson-body md">${L.bodyHtml}</article>
      <div class="read-action">${S.read[id] ? `<div class="done">${icon("check")}読了済み</div>` : `<button class="btn primary" data-action="read">${icon("check")}読んだ</button>`}</div>
    </section>
    ${L.atWork?.length ? `
    <section id="atwork" class="lesson-section" aria-labelledby="atwork-title">
      ${title("atwork", "仕事のコードではこう出てくる")}
      <p class="section-lead">業務の Spring Boot アプリで、このレッスンの知識が現れる場所です。番号の行を読んでから、下の説明と照らし合わせましょう。</p>
      ${L.atWork.map(atWorkBlock).join("")}
    </section>` : ""}
    ${L.samples.length ? `
    <section id="run" class="lesson-section" aria-labelledby="run-title">
      ${title("run", "サンプルを見る")}
      <p class="run-note">スマホ版ではコードを実行できません。出力を予想しながら読んで、PC版か Codespaces 版で動かしてみましょう。</p>
      ${L.samples.map((s) => `<div class="sample"><h3>${esc(s.title)}</h3><div class="md">${s.descriptionHtml}</div>
        <pre><code class="language-java">${esc(s.code)}</code></pre></div>`).join("")}
    </section>` : ""}
    ${L.sts || L.checklist.length ? `
    <section id="sts" class="lesson-section" aria-labelledby="sts-title">
      ${title("sts", "STSでやってみよう")}
      ${L.sts ? `<div class="md">${L.sts.introHtml}</div><ol class="sts-steps">${L.sts.steps.map((s) => `<li><div class="sts-step-title">${esc(s.title)}</div><div class="md">${s.detailHtml}</div></li>`).join("")}</ol>` : ""}
      ${L.checklist.length ? `<div class="checklist card"><div class="checklist-title">やったらチェック</div><ul>
        ${L.checklist.map((c) => `<li class="check-item ${checked.includes(c.key) ? "checked" : ""}">
          <button class="check-button" data-action="check" data-key="${esc(c.key)}" aria-pressed="${checked.includes(c.key)}" aria-label="${esc(c.text)}"></button>
          <span>${esc(c.text)}</span></li>`).join("")}</ul></div>` : ""}
    </section>` : ""}
    ${L.exercises.length ? `
    <section id="write" class="lesson-section" aria-labelledby="write-title">
      ${title("write", "書く")}
      <p class="run-note">スマホ版は自動採点できないので、<b>採点テスト</b>を読んで自分で確かめ、「できた/まだ」を記録します。
      本物の採点は PC版か Codespaces 版で。電波と APIキーがあれば AI にも見てもらえます。</p>
      ${L.exercises.map((e, i) => exerciseCard(e, i)).join("")}
    </section>` : ""}
    ${L.reading?.length ? `
    <section id="reading" class="lesson-section" aria-labelledby="reading-title">
      ${title("reading", "コードを読む")}
      <p class="section-lead">実行せずに、頭の中で 1 行ずつ追って答えます。答えたあとに「1 行ずつの追跡」で答え合わせ。</p>
      <ol class="reading-list">${L.reading.map((r, i) => readingCard(r, `${id}@${i}`)).join("")}</ol>
    </section>` : ""}
    <section id="quiz" class="lesson-section" aria-labelledby="quiz-title">
      ${title("quiz", "確かめる")}
      <form class="quiz" data-action="quiz">
        <ol>${L.quiz.map((q, i) => `<li class="quiz-q"><div class="md">${q.questionHtml}</div>
          ${q.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="q${i}" value="${j}"><span>${c}</span></label>`).join("")}</li>`).join("")}</ol>
        <button class="btn primary" type="submit">採点する</button>
      </form>
      <div id="quiz-result" aria-live="polite"></div>
    </section>
    ${myTerms.length ? `
    <section class="lesson-section lesson-terms" aria-labelledby="terms-title">
      ${title("terms", "このレッスンの用語")}
      <ul class="term-list">${myTerms.map((t) => termItem(t, false)).join("")}</ul>
    </section>` : ""}
    ${L.columns.length ? `
    <section class="lesson-section" aria-labelledby="columns-title">
      ${title("columns", "コラム")}
      <div class="columns">${L.columns.map((c) => `<aside class="column column-${esc(c.type)}"><div class="column-label">${esc(c.label)}</div>
        <h3>${esc(c.title)}</h3><div class="md">${c.bodyHtml}</div></aside>`).join("")}</div>
    </section>` : ""}
    <nav class="lesson-nav" aria-label="前後のレッスン">
      ${L.prev ? `<a href="#/lesson/${esc(L.prev)}"><small>前のレッスン</small>${esc(L.prevTitle || "")}</a>` : "<span></span>"}
      ${L.next ? `<a class="next" href="#/lesson/${esc(L.next)}"><small>次のレッスン</small>${esc(L.nextTitle || "")}</a>` : ""}
    </nav>
    <button class="tutor-fab" data-action="tutor" aria-label="AIチューターに質問する">${icon("chat")}質問する</button>
    <div class="sheet-backdrop" data-action="tutor"></div>
    ${tutorSheet(L)}`;
  bindLesson(L, meta);
  decorate(view());
}

/** 仕事のコード。番号の付いた行を、下の説明と結びつける */
function atWorkBlock(a) {
  const notes = Object.entries(a.notesHtml || {});
  return `<article class="atwork">
    ${a.title ? `<h3>${esc(a.title)}</h3>` : ""}
    <div class="atwork-intro md">${a.introHtml}</div>
    <div class="code-notes">${notes.map(([n]) => `<span class="pin" style="--n:${Number(n)}" aria-hidden="true">${Number(n)}</span>`).join("")}
      <pre><code class="language-java">${esc(a.code)}</code></pre></div>
    ${notes.length ? `<ol class="line-notes">${notes.map(([n, html]) => `<li><span class="pin-label">${Number(n)}行</span><span>${html}</span></li>`).join("")}</ol>` : ""}
    ${a.pointsHtml?.length ? `<div class="points"><div class="points-title">読むときの着眼点</div><ul>${a.pointsHtml.map((p) => `<li>${p}</li>`).join("")}</ul></div>` : ""}
  </article>`;
}

/** 読解の1問。ref は記録と復習のキー(lessonId@番号) */
function readingCard(r, ref, from) {
  const done = S.reading[ref];
  const lv = Math.max(1, Math.min(3, r.level || 1));
  return `<li class="reading-q" data-ref="${esc(ref)}">
    <div class="reading-head"><h3>${esc(r.title)}</h3>
      <span class="level" aria-label="難しさ ${lv} / 3">${[1, 2, 3].map((n) => `<i class="${n <= lv ? "on" : ""}"></i>`).join("")}<span>${["やさしい", "ふつう", "むずかしい"][lv - 1]}</span></span></div>
    ${from ? `<p class="reading-from muted">${from}</p>` : ""}
    <pre><code class="language-java">${esc(r.code)}</code></pre>
    <form data-action="reading">
      <fieldset style="border:0;padding:0;margin:0"><legend class="reading-question">${r.questionHtml}</legend>
      ${r.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="c" value="${j}" required><span>${c}</span></label>`).join("")}</fieldset>
      <button class="btn" type="submit">答え合わせ</button>
      ${done ? `<span class="small muted" style="margin-left:8px">前回: ${done.ok ? "正解" : "不正解"}</span>` : ""}
    </form>
    <div class="reading-answer" hidden aria-live="polite"></div>
  </li>`;
}

function readingAnswerHtml(r, ok) {
  return `<div class="verdict ${ok ? "correct" : "wrong"}">${icon(ok ? "check" : "cross")}${ok ? "正解" : "不正解"}</div>
    <p class="small">正解: <b>${r.choicesHtml[r.answer]}</b></p>
    <div class="md">${r.explanationHtml}</div>
    ${r.traceHtml?.length ? `<p class="trace-title">1 行ずつの追跡</p><ol class="trace">${r.traceHtml.map((t) => `<li>${t}</li>`).join("")}</ol>` : ""}`;
}

/** 読解フォームの答え合わせ。記録して復習キューに入れる */
function bindReading(root, findQuestion) {
  root.querySelectorAll("form[data-action=reading]").forEach((form) => {
    form.onsubmit = (ev) => {
      ev.preventDefault();
      const li = form.closest(".reading-q");
      const ref = li.dataset.ref;
      const r = findQuestion(ref);
      const choice = Number(new FormData(form).get("c"));
      const ok = choice === r.answer;
      const lessonId = ref.split("@")[0];
      S.reading[ref] = { ok, at: today() };
      practice(ref, "reading", lessonId, ok);
      recordDay();
      form.querySelectorAll("label.choice").forEach((label, j) => {
        label.classList.toggle("is-answer", j === r.answer);
        label.classList.toggle("is-wrong", j === choice && !ok);
      });
      const out = li.querySelector(".reading-answer");
      out.innerHTML = readingAnswerHtml(r, ok);
      out.hidden = false;
      decorate(out);
    };
  });
}

function termItem(t, withLesson, lessonTitle) {
  return `<li class="term"><p class="term-name">${esc(t.term)}${t.read ? `<small>${esc(t.read)}</small>` : ""}${t.en ? `<small lang="en">${esc(t.en)}</small>` : ""}</p>
    <p class="term-def">${t.defHtml}</p>
    ${withLesson && t.lesson ? `<a class="term-from" href="#/lesson/${esc(t.lesson)}">${esc(lessonTitle || t.lesson)}</a>` : ""}</li>`;
}

function exerciseCard(e, i) {
  const st = S.exercise[e.key] || {};
  return `
    <article class="exercise card" data-key="${esc(e.key)}">
      <header class="exercise-head">
        <h3><span class="ex-label">演習 ${i + 1}</span>${esc(e.title)}</h3>
        <div class="exercise-meta"><span class="stars" aria-label="難しさ ${e.difficulty} / 3">${stars(e.difficulty)}</span>
          ${st.selfPass ? `<span class="pill ok">できた</span>` : ""}<code class="class-name">${esc(e.className)}.java</code></div>
      </header>
      <div class="problem md">${e.problemHtml}</div>
      <textarea class="code-input" spellcheck="false" autocapitalize="off" autocorrect="off" aria-label="${esc(e.className)}.java のコード" data-key="${esc(e.key)}">${esc(st.code ?? e.starter)}</textarea>
      <div class="actions">
        <button class="btn ghost small" data-action="reset-code" data-index="${i}">雛形に戻す</button>
        <button class="btn small" data-action="ai-hint" data-index="${i}">AIヒント</button>
        <button class="btn small" data-action="ai-review" data-index="${i}">AIに見てもらう</button>
      </div>
      <details class="solution"><summary>採点テストを読む(何が確かめられるか)</summary><pre><code class="language-java">${esc(e.test)}</code></pre></details>
      <details class="solution"><summary>模範解答を見る(ネタバレ注意)</summary><pre><code class="language-java">${esc(e.solution)}</code></pre></details>
      <div class="self-check">
        <button class="btn" data-action="self" data-index="${i}" data-ok="1">${icon("check")}できた</button>
        <button class="btn" data-action="self" data-index="${i}" data-ok="0">まだ</button>
      </div>
      <div class="ai-slot" aria-live="polite"></div>
    </article>`;
}

function tutorSheet(L) {
  const msgs = S.chat[L.id] || [];
  return `
    <aside class="sheet tutor" id="tutor" aria-label="AIチューター">
      <div class="tutor-head"><span>AIチューター</span>
        <button class="link-button small" data-action="clear-chat">会話をクリア</button>
        <button class="tutor-close" data-action="tutor" aria-label="閉じる">${icon("cross")}</button></div>
      <div class="chat-log" id="chat-log">
        <div class="chat-msg from-ai"><div class="chat-who">モカ先輩(AI)</div>
          <div class="chat-body">分からないところは気軽に聞いて。答えをそのまま教えるんじゃなくて、考え方を一緒に整理するからね。</div></div>
        ${msgs.map(chatHtml).join("")}
      </div>
      <form class="chat-form" data-action="chat">
        <textarea name="q" rows="3" placeholder="例: なぜ int 同士の割り算は小数にならないの?" aria-label="質問" required></textarea>
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
    : `<div class="chat-msg from-ai"><div class="chat-who">モカ先輩(AI)</div><div class="chat-body md" data-md="${esc(m.content)}">${esc(m.content)}</div></div>`;
}

/** 染まり具合が深まったら、名札の色を染み込ませる */
function refreshDye(meta) {
  if (!meta) return;
  const level = dyeLevel(meta);
  const wrap = $(".lesson-dye");
  if (!wrap || wrap.dataset.level === String(level)) return;
  wrap.dataset.level = level;
  wrap.innerHTML = `${dyeSwatch(level, "lesson-swatch dyeing")} <span class="dye-name">${DYE[level].name}・${DYE[level].meaning}</span>`;
}

let lastCode = "";

function bindLesson(L, meta) {
  const root = view();
  const dyeWrap = $(".lesson-dye");
  if (dyeWrap && meta) dyeWrap.dataset.level = dyeLevel(meta);
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
  bindReading(root, (ref) => L.reading[Number(ref.split("@")[1])]);

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
      el.outerHTML = `<div class="done">${icon("check")}読了。次はサンプルを読んで、演習に挑戦しよう</div>`;
      refreshDye(meta);
    } else if (action === "check") {
      const list = new Set(S.checklist[L.id] || []);
      list.has(el.dataset.key) ? list.delete(el.dataset.key) : list.add(el.dataset.key);
      S.checklist[L.id] = [...list];
      recordDay();
      const on = list.has(el.dataset.key);
      el.setAttribute("aria-pressed", on);
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
      refreshDye(meta);
      toast(ok ? "記録しました。1日後に復習で出ます" : "記録しました。明日もう一度やってみよう");
    } else if (action === "ai-review" || action === "ai-hint") {
      const card = el.closest("article");
      const code = card.querySelector("textarea").value;
      const slot = card.querySelector(".ai-slot");
      await runAi(slot, el, L, () => action === "ai-review" ? reviewRequest(L, ex, code) : hintRequest(L, ex, code));
    } else if (action === "tutor") {
      const open = document.body.classList.toggle("sheet-open");
      const log = $("#chat-log");
      if (log) log.scrollTop = log.scrollHeight;
      if (open) $("#tutor textarea")?.focus({ preventScroll: true });
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
      return `<li class="${ok ? "correct" : "wrong"}"><div class="q-mark">問 ${i + 1} ${ok ? "正解" : chosen < 0 ? "未回答" : "不正解"}</div>
        <div class="md">${q.questionHtml}</div><p class="small">正解: <b>${q.choicesHtml[q.answer]}</b></p>
        <div class="explanation md">${q.explanationHtml}</div></li>`;
    });
    const prev = S.quiz[L.id];
    S.quiz[L.id] = { best: Math.max(score, prev?.best ?? 0), last: score };
    recordDay();
    refreshDye(meta);
    const pass = Math.ceil(L.quiz.length * 0.8);
    $("#quiz-result").innerHTML = `<div class="quiz-result"><div class="quiz-score"><span class="big">${score} / ${L.quiz.length}</span>
      <span>${score === L.quiz.length ? "満点。縹に染まりました" : score >= pass ? "合格ライン突破。縹に染まりました" : `合格ラインは ${pass} 問`}</span></div>
      <p class="muted small">解いた問題は復習に入り、1日後 → 3日後 → 7日後 → 14日後 に出題されます。</p>
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
      thinking.textContent = e.message;
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
  bar.setAttribute("aria-label", "記号を入力");
  for (const [label, text] of SYMBOLS) {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = label;
    b.setAttribute("aria-label", label === "⇥" ? "インデント" : label);
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

// ------------------------------------------------------------ 読解(全レッスン)

async function renderReadingPage() {
  const [items, cur] = await Promise.all([readingAll(), curriculum()]);
  const titles = Object.fromEntries(allLessons(cur).map((l) => [l.id, l.title]));
  const filter = S.settings.readingFilter || "todo";
  const pick = (r) => {
    const st = S.reading[r.ref];
    if (filter === "todo") return !st;
    if (filter === "wrong") return st && !st.ok;
    return true;
  };
  const shown = items.filter(pick);
  const correct = items.filter((r) => S.reading[r.ref]?.ok).length;
  view().innerHTML = `
    <h1 class="page-title">コードを読む</h1>
    <p class="page-lead">業務のコードを読む力を、実行せずに頭の中で追って鍛えます。全 ${items.length} 問のうち <b>${correct}</b> 問に正解。答えた問題は復習にも出ます。</p>
    <div class="reading-filter" role="group" aria-label="表示する問題">
      ${[["todo", "まだ解いていない"], ["wrong", "間違えた"], ["all", "すべて"]].map(([k, label]) =>
        `<button type="button" data-filter="${k}" aria-pressed="${filter === k}">${label}</button>`).join("")}
    </div>
    ${shown.length ? `<ol class="reading-list">${shown.slice(0, 40).map((r) => readingCard(r, r.ref,
      `<a href="#/lesson/${esc(r.lesson)}">${esc(titles[r.lesson] || r.lesson)}</a>`)).join("")}</ol>
      ${shown.length > 40 ? `<p class="muted small">ほかに ${shown.length - 40} 問あります。解き進めると次が出てきます。</p>` : ""}`
      : `<div class="card empty-state"><p>${filter === "wrong" ? "間違えた問題はありません。" : "この条件の問題はありません。"}</p></div>`}`;
  view().querySelectorAll("[data-filter]").forEach((b) => {
    b.onclick = () => { S.settings.readingFilter = b.dataset.filter; save(); renderReadingPage(); };
  });
  const byRef = Object.fromEntries(items.map((r) => [r.ref, r]));
  bindReading(view(), (ref) => byRef[ref]);
  decorate(view());
}

// ------------------------------------------------------------ 用語集

// 読みの最初の文字から「あ行」「か行」…の見出しを決める(英字・記号で始まるものはまとめて先頭に)
const KANA_ROWS = ["あいうえおぁぃぅぇぉゔ", "かきくけこがぎぐげご", "さしすせそざじずぜぞ", "たちつてとだぢづでどっ",
  "なにぬねの", "はひふへほばびぶべぼぱぴぷぺぽ", "まみむめも", "やゆよゃゅょ", "らりるれろ", "わをん"];
function kanaRow(t) {
  const c = (t.read || t.term).trim().charAt(0);
  const hira = String.fromCharCode(c >= "ァ" && c <= "ヶ" ? c.charCodeAt(0) - 0x60 : c.charCodeAt(0));
  const i = KANA_ROWS.findIndex((row) => row.includes(hira));
  return i >= 0 ? "あかさたなはまやらわ"[i] + "行" : /[A-Za-z@]/.test(c) ? "英字・記号" : "その他";
}

async function renderGlossary() {
  const [terms, cur] = await Promise.all([glossary(), curriculum()]);
  const titles = Object.fromEntries(allLessons(cur).map((l) => [l.id, l.title]));
  view().innerHTML = `
    <h1 class="page-title">用語集</h1>
    <p class="page-lead">全レッスンの用語 ${terms.length} 語を読み順に。読み・英語・説明のどれでも探せます。同じ用語はレッスンごとの説明が並びます。</p>
    <div class="search">${icon("search")}<input type="search" id="q" placeholder="例: Bean、ちゅうにゅう、injection" aria-label="用語を探す" autocomplete="off"></div>
    <p class="glossary-count" id="count" aria-live="polite"></p>
    <ul class="term-list" id="terms"></ul>`;
  const list = $("#terms");
  const count = $("#count");
  const plain = (html) => html.replace(/<[^>]+>/g, "");
  // 読み順に並べる(同じ用語がレッスンごとに別の説明で出るので、隣り合わせにする)
  const index = [...terms].sort((a, b) => (a.read || a.term).localeCompare(b.read || b.term, "ja")).map((t) => ({ t, key: [t.term, t.read, t.en, plain(t.defHtml), titles[t.lesson]].join(" ").toLowerCase() }));
  const show = (q) => {
    const words = q.toLowerCase().split(/\s+/).filter(Boolean);
    const hit = index.filter((x) => words.every((w) => x.key.includes(w))).map((x) => x.t);
    count.textContent = q ? `${hit.length} 語` : "";
    let row = "";
    // 405 語なら全部描いても軽いので、区切らずに出す(行の見出しで飛べるように)
    list.innerHTML = hit.map((t) => {
      const r = kanaRow(t);
      const head = !q && r !== row ? `<li class="term-group"><h2>${r}</h2></li>` : "";
      row = r;
      return head + termItem(t, true, titles[t.lesson]);
    }).join("");
  };
  $("#q").addEventListener("input", (e) => show(e.target.value.trim()));
  show("");
}

// ------------------------------------------------------------ 復習

async function renderReview() {
  const due = dueItems();
  const upcoming = Object.entries(S.review).filter(([, it]) => !it.graduated && it.due > today()).length;
  if (due.length === 0) {
    view().innerHTML = `<h1 class="page-title">復習</h1>
      <div class="card empty-state"><div class="talk talk-sensei"><div class="talk-avatar"><span class="talk-icon">モカ</span><span class="talk-name">モカ先輩</span></div>
      <div class="talk-bubble"><p>今日の復習はおしまい。${upcoming ? `この先 ${upcoming} 問が、1・3・7・14 日の間隔で順番に出てくるよ。` : "レッスンでクイズや読解を解くと、ここに出てくるよ。"}</p></div></div>
      <a class="btn primary" href="#/">ホームへ</a></div>`;
    return;
  }
  const [ref, item] = due[0];
  const L = await lesson(item.lessonId);
  const head = `<h1 class="page-title">復習</h1><p class="review-progress">残り <b>${due.length}</b> 問 ·
    <a href="#/lesson/${esc(L.id)}">${esc(L.title)}</a> <span class="pill">段階 ${item.stage + 1} / 4</span></p>`;
  const next = (it) => `<p class="review-note">次回: <b>${intervalLabel(it)}</b></p><button class="btn primary" id="next">次の復習へ</button>`;
  if (item.type === "quiz") {
    const q = L.quiz[Number(ref.split("#")[1])];
    if (!q) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<div class="card review-quiz"><div class="md">${q.questionHtml}</div>
      <form id="rq">${q.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="c" value="${j}" required><span>${c}</span></label>`).join("")}
      <button class="btn primary" type="submit">答える</button></form><div id="ra" aria-live="polite"></div></div>`;
    $("#rq").onsubmit = (ev) => {
      ev.preventDefault();
      const choice = Number(new FormData(ev.target).get("c"));
      const ok = choice === q.answer;
      const it = reviewAnswer(ref, ok);
      recordDay();
      ev.target.querySelectorAll("input,button").forEach((x) => { x.disabled = true; });
      $("#ra").innerHTML = `<div class="quiz-answer ${ok ? "correct" : "wrong"}"><div class="q-mark big">${ok ? "正解" : "不正解"}</div>
        <p>正解: <b>${q.choicesHtml[q.answer]}</b></p><div class="explanation md">${q.explanationHtml}</div>${next(it)}</div>`;
      decorate($("#ra"));
      $("#next").onclick = () => renderReview();
    };
  } else if (item.type === "reading") {
    const r = (L.reading || [])[Number(ref.split("@")[1])];
    if (!r) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<ol class="reading-list">${readingCard(r, ref)}</ol><div id="ra"></div>`;
    const form = view().querySelector("form[data-action=reading]");
    form.onsubmit = (ev) => {
      ev.preventDefault();
      const choice = Number(new FormData(form).get("c"));
      const ok = choice === r.answer;
      S.reading[ref] = { ok, at: today() };
      const it = reviewAnswer(ref, ok);
      recordDay();
      form.querySelectorAll("input,button").forEach((x) => { x.disabled = true; });
      form.querySelectorAll("label.choice").forEach((label, j) => {
        label.classList.toggle("is-answer", j === r.answer);
        label.classList.toggle("is-wrong", j === choice && !ok);
      });
      const out = view().querySelector(".reading-answer");
      out.innerHTML = readingAnswerHtml(r, ok) + next(it);
      out.hidden = false;
      decorate(out);
      $("#next").onclick = () => renderReview();
    };
  } else {
    const e = L.exercises.find((x) => x.key === ref);
    if (!e) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<p class="muted small">何も見ずに雛形から書いてみよう。書けたら模範解答と見比べて「できた/まだ」を選びます。</p>
      <article class="exercise card"><h3>${esc(e.title)}</h3><div class="problem md">${e.problemHtml}</div>
      <textarea class="code-input" spellcheck="false" autocapitalize="off" autocorrect="off" aria-label="コード">${esc(e.starter)}</textarea>
      <details class="solution"><summary>模範解答と見比べる</summary><pre><code class="language-java">${esc(e.solution)}</code></pre></details>
      <div class="self-check"><button class="btn" data-ok="1">${icon("check")}できた</button><button class="btn" data-ok="0">まだ</button></div>
      <div id="ra" aria-live="polite"></div></article>`;
    addSymbolBar(view().querySelector("textarea"));
    view().querySelectorAll(".self-check .btn").forEach((b) => {
      b.onclick = () => {
        const it = reviewAnswer(ref, b.dataset.ok === "1");
        recordDay();
        $("#ra").innerHTML = next(it);
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
  const theme = st.theme || "auto";
  view().innerHTML = `
    <h1 class="page-title">設定</h1>
    <section class="card settings">
      <div class="card-title">表示</div>
      <p class="small" id="theme-label">画面の明るさ</p>
      <div class="theme-switch" role="group" aria-labelledby="theme-label">
        ${[["auto", "端末に合わせる"], ["light", "明るい"], ["dark", "暗い"]].map(([k, label]) =>
          `<button type="button" data-theme="${k}" aria-pressed="${theme === k}">${label}</button>`).join("")}
      </div>
    </section>
    <section class="card settings">
      <div class="card-title">AI機能(Claude API)</div>
      <p class="small">APIキーは<b>このスマホのブラウザの中だけ</b>に保存され、Claude API(api.anthropic.com)にだけ送られます。
      GitHub やこのサイトには送られません。他の人とスマホを共有している場合は保存しないでください。</p>
      <label for="key">APIキー</label>
      <input id="key" type="password" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="sk-ant-..." value="${esc(st.apiKey || "")}">
      <div class="actions">
        <button class="btn primary" id="save-key">保存</button>
        <button class="btn" id="test-key">接続テスト</button>
        <button class="btn ghost" id="del-key">削除</button>
      </div>
      <div id="key-result" aria-live="polite"></div>
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
      <label class="small" for="m-tutor">AIチューター(質問への回答)</label><input type="text" id="m-tutor" value="${esc(models.tutor)}" autocapitalize="off">
      <label class="small" for="m-review">コードレビュー(AIに見てもらう)</label><input type="text" id="m-review" value="${esc(models.review)}" autocapitalize="off">
      <label class="small" for="m-hint">AIヒント</label><input type="text" id="m-hint" value="${esc(models.hint)}" autocapitalize="off">
      <p class="small muted">例: claude-opus-5-5 / claude-sonnet-5-5 / claude-haiku-5-5
        <button class="link-button small" id="reset-models" type="button">おすすめに戻す</button></p>
      <button class="btn small" id="save-models">モデルを保存</button>
    </section>
    <section class="card settings">
      <div class="card-title">学習記録</div>
      <p class="small">記録はこのスマホの中にあります。機種変更やブラウザの切り替えのときは、書き出して貼り付けてください(APIキーは含みません)。</p>
      <div class="actions"><button class="btn" id="export">書き出す(コピー)</button><button class="btn" id="import">貼り付けて読み込む</button></div>
      <label for="backup" class="visually-hidden">書き出した記録</label>
      <textarea id="backup" placeholder="ここに書き出されます / 読み込む記録を貼り付け"></textarea>
      <button class="btn ghost small" id="reset">学習記録をすべて消す</button>
    </section>
    <section class="card">
      <div class="card-title">オフライン</div>
      <p class="small" id="offline-status">確認中…</p>
      <p class="small muted">コードの実行と自動採点は Java が必要なので、PC版か GitHub Codespaces 版で行います(README の「スマホで使う」)。</p>
    </section>`;
  view().querySelectorAll("[data-theme]").forEach((b) => {
    b.onclick = () => {
      S.settings.theme = b.dataset.theme;
      save();
      applyTheme();
      view().querySelectorAll("[data-theme]").forEach((x) => x.setAttribute("aria-pressed", x === b));
    };
  });
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
      out.innerHTML = `<p class="done">${icon("check")}つながりました(${esc(res.model)}): ${esc(textOf(res))}</p>`;
    } catch (e) {
      out.innerHTML = `<div class="ai-error">${esc(friendlyError(e))}</div>`;
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
      applyTheme();
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
  el.textContent = `全レッスンをこのスマホに保存済みです(${n}ファイル)。電波が無くても読めます。`;
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
    slot.innerHTML = `<div class="ai-answer"><div class="ai-answer-title">${esc(req.title)}</div><div class="md" data-md="${esc(text)}">${esc(text)}</div></div>`;
    await renderMarkdown(slot);
  } catch (e) {
    slot.innerHTML = `<div class="ai-error">${esc(e.message)}</div>`;
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

function mermaidTheme() {
  // 図も藍の配色で描く(CSS の変数から色を読むので、暗い表示でもそろう)
  const css = getComputedStyle(document.documentElement);
  const v = (name) => css.getPropertyValue(name).trim();
  return {
    startOnLoad: false, securityLevel: "strict", theme: "base",
    themeVariables: {
      fontFamily: v("--font"), fontSize: "14px", background: "transparent",
      primaryColor: v("--surface"), primaryBorderColor: v("--line-strong"), primaryTextColor: v("--ink"),
      secondaryColor: v("--accent-soft"), tertiaryColor: v("--surface-2"), lineColor: v("--ink-muted"),
      textColor: v("--ink"), mainBkg: v("--surface"), nodeBorder: v("--line-strong"), clusterBkg: v("--surface-2"),
      clusterBorder: v("--line"), edgeLabelBackground: v("--surface-2"), actorBkg: v("--surface"), actorBorder: v("--line-strong"),
      noteBkgColor: v("--ki-soft"), noteBorderColor: v("--line"),
    },
  };
}

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
      if (!mermaidModule) mermaidModule = (await import(MERMAID_URL)).default;
      mermaidModule.initialize(mermaidTheme());
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
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && document.body.classList.contains("sheet-open")) document.body.classList.remove("sheet-open");
});

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
