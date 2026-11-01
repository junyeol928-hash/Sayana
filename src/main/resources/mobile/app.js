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
  { name: "生成り", read: "きなり", meaning: "まだ" },
  { name: "甕覗き", read: "かめのぞき", meaning: "読んだ" },
  { name: "縹", read: "はなだ", meaning: "クイズ合格" },
  { name: "藍", read: "あい", meaning: "演習もできた" },
  { name: "褐", read: "かち", meaning: "復習をすべて卒業" },
];
// 染めの名前は読みにくいので、読みをふりがなで添える
const dyeRuby = (i) => `<ruby>${DYE[i].name}<rt>${DYE[i].read}</rt></ruby>`;

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
// 全レッスンと図を保存し終えたか(ヘッダーの印に使う)。ホームで確かめた結果を覚えておき、オフラインでレッスンから開いても使う
let savedAll = !!S.allSaved;
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
// 一度染まった段は戻さない(褐の卒業のあとに読解を解くと、新しい復習が増えて藍へ戻っていた。13 回目の Codex の指摘)。
// 復習の残りは復習の画面とバッジで見せ、染まりは「ここまで身についた」記録として残す
function dyeLevel(meta) {
  const now = dyeLevelNow(meta);
  S.dyeBest = S.dyeBest || {};
  if (now > (S.dyeBest[meta.id] || 0)) S.dyeBest[meta.id] = now;
  return Math.max(now, S.dyeBest[meta.id] || 0);
}

function dyeLevelNow(meta) {
  const read = !!S.read[meta.id];
  const passed = S.quiz[meta.id] && quizPassed(meta);
  if (!passed) return read || S.quiz[meta.id] ? 1 : 0;
  const selfPassed = Object.entries(S.exercise).filter(([k, v]) => k.startsWith(meta.id + "/") && v.selfPass).length;
  if (selfPassed < meta.exercises) return 2;
  const items = Object.values(S.review).filter((it) => it.lessonId === meta.id);
  return items.length > 0 && items.every((it) => it.graduated) ? 4 : 3;
}

const dyeSwatch = (level, extra = "") => `<span class="dye dye-${level} ${extra}" role="img" aria-label="${DYE[level].name}(${DYE[level].meaning})"></span>`;
const dyeDot = (level) => `<span class="dye dye-${level}"></span>`;
// 前回の続きのボタン: 「クイズの続きから」(括弧書きのラベルは硬いので、言葉でつなぐ)。PC 版 app.js と同じ
// 前回いた段が済んでいれば、段の並びで次のまだの段を返す(仕事のコード・サンプル・STS は「済み」を問わずそのまま)
function nextSectionFor(l, from) {
  const order = ["read", "atwork", "run", "quiz", "write", "reading", "sts"];
  const done = {
    read: !!S.read[l.id],
    quiz: !!(S.quiz[l.id] && quizPassed(l)),
    write: Array.from({ length: l.exercises || 0 }, (_, i) => S.exercise[`${l.id}/ex${i + 1}`]?.selfPass).every(Boolean),
    reading: Array.from({ length: l.reading || 0 }, (_, i) => S.reading[`${l.id}@${i}`]?.ok).every(Boolean),
  };
  const has = { write: (l.exercises || 0) > 0, reading: (l.reading || 0) > 0 };
  if (!(from in done) || !done[from]) return from;
  return order.slice(order.indexOf(from) + 1).find((k) => k in done && has[k] !== false && !done[k]) || from;
}
const resumeLabel = (section) => !section ? "続きから" : section === "読む" ? "続きを読む" : `${section}の続きから`;
// 採点の結果を問の順に刺し子の目で: 縫えた目(正解)は藍、空き(間違い)は弱い色。何問目を落としたかが並びで分かる
const scoreStitches = (results) => `<span class="score-stitches" aria-hidden="true">${results.map((ok) => `<i class="${ok ? "on" : ""}"></i>`).join("")}</span>`;
// 染まりの地図の 1 マス。前に見たときより深まったマスは染み込ませ、主役のレッスンには朱の印を付ける
const dyeCell = (l, levels, seen, deepened, hero) => `<span class="dye-cell${l.id === hero.id ? " is-here" : ""}">${deepened.has(l.id)
  ? `${dyeDot(seen[l.id])}<span class="dye dye-${levels[l.id]} ink"></span>` : dyeDot(levels[l.id])}</span>`;

/** 染まり具合が深まった瞬間を、押した場所のそばで見せる(古い色の上に新しい色が広がる) */
function dyeBurst(from, to) {
  return `<span class="dye-burst" role="img" aria-label="${DYE[to].name}に染まりました">${dyeDot(from)}<span class="dye dye-${to} ink"></span></span>`;
}

// 「視差効果を減らす」設定のときは、スクロールも即時に
const smooth = () => (matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth");

/** 次の段へ進む条件(ヒーローで「次は何色へ・何をすれば」を見せる) */
function nextDyeHint(meta, level) {
  if (level === 0) return "読み終えると甕覗きに";
  if (level === 1) return `クイズで ${Math.ceil(meta.quiz * 0.8)} 問以上取ると縹に`;
  if (level === 2) return "演習ができたら藍に";
  if (level === 3) return "復習をすべて卒業すると褐に";
  return null;
}

const SECTION_LABEL = { read: "読む", atwork: "仕事のコード", run: "サンプル", sts: "STSで試す", write: "演習", reading: "読解", quiz: "クイズ" };

// ------------------------------------------------------------ 画面

const view = () => $("#view");

async function render() {
  const hash = location.hash.replace(/^#/, "") || "/";
  document.body.classList.remove("sheet-open");
  view().onclick = null;
  lessonTracker?.disconnect();
  const tab = hash.startsWith("/lesson/") ? null : hash;
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
  const days = streak();
  $("#streak").innerHTML = `連続 <b>${days}</b> 日`;
  $("#streak").classList.toggle("zero", days === 0);
  const net = $("#net");
  // 全部保存できたら、ヒーローの説明の代わりにヘッダーの小さな印で知らせる(ヒーローは次の一手だけにする)
  // 電波がなくても全部保存してあれば、警告ではなく「読める」と言う(朱は、保存が欠けていて読めないものがあるときだけ)
  net.textContent = !navigator.onLine ? (savedAll ? "電波なし・保存済み" : "オフライン") : savedAll ? "保存済み" : "オンライン";
  net.title = savedAll ? "全レッスンと図を保存済み。電波なしで読めます" : "";
  net.classList.toggle("off", !navigator.onLine && !savedAll);
  net.classList.toggle("saved", savedAll);
  // 電波がない間は、AI に聞く「質問」を弱くして先に知らせる
  document.body.classList.toggle("is-offline", !navigator.onLine);
  document.querySelectorAll(".ask").forEach((b) => { b.title = navigator.onLine ? "" : "電波が戻ったら使えます"; });
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
  const graduated = Object.values(S.review).filter((it) => it.graduated).length;
  const readingOk = Object.values(S.reading).filter((r) => r.ok).length;
  const pending = Object.values(S.review).filter((it) => !it.graduated).map((it) => it.due).sort();
  const nextReview = due ? "今日" : pending.length ? pending[0].slice(5).replace("-", "/") : "なし";
  const readingTotal = all.reduce((s, l) => s + (l.reading || 0), 0);
  const weekDays = S.days.filter((d) => d > addDays(today(), -7)).length;
  const standalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  // 前回の続き(最後まで読み切っていないレッスン)があれば、それを主役にする。新しいレッスンは控えめに添える
  const last = S.last && !S.last.end && all.find((l) => l.id === S.last.id);
  const hero = last || next;
  const section = last && SECTION_LABEL[S.last.section];
  // 前回いた段がもう済んでいたら、主ボタンは次のまだの段へ(「次:」の行と同じ段を指す)。前回の位置へは副リンクで戻れる
  const go = last ? nextSectionFor(hero, S.last.section) : null;
  const hint = nextDyeHint(hero, levels[hero.id]);
  // ホームに戻ったとき、前に見たときより深まったマスだけ染み込ませる
  const seen = S.seenLevels || {};
  const deepened = new Set(all.filter((l) => seen[l.id] != null && levels[l.id] > seen[l.id]).map((l) => l.id));
  S.seenLevels = levels;
  save();
  view().innerHTML = `
    <section class="today" aria-labelledby="today-title">
      <p class="today-kicker"><span>${last ? "前回の続き" : "今日の稽古"}</span><span>${esc(hero.part.title.replace(/^第\d部\s*/, ""))} · ${esc(hero.chapter.title)}</span></p>
      <h1 class="today-title" id="today-title"><a href="#/lesson/${esc(hero.id)}" data-resume="${last ? esc(S.last.section || "") : ""}">${esc(hero.title)}</a></h1>
      <p class="today-meta"><span>読む ${hero.minutes} 分</span><span>演習 ${hero.exercises}</span><span>クイズ ${hero.quiz}</span>${hero.reading ? `<span>読解 ${hero.reading}</span>` : ""}</p>
      ${hint ? `<p class="today-next">${dyeSwatch(levels[hero.id])}<span><span class="tn-now">いま ${dyeRuby(levels[hero.id])}(${DYE[levels[hero.id]].meaning})</span><span class="tn-hint">次: ${hint}</span></span></p>` : ""}
      <div class="today-actions"><a class="btn primary" href="#/lesson/${esc(hero.id)}" data-resume="${last ? esc(go || "") : ""}">${!last ? (levels[hero.id] ? "続きから稽古する" : "稽古をはじめる") : go !== S.last.section ? `${SECTION_LABEL[go]}へ進む` : resumeLabel(section)}</a></div>
      <div class="today-links">${last && go !== S.last.section ? `<a class="today-alt" href="#/lesson/${esc(hero.id)}" data-resume="${esc(S.last.section)}">前回の位置(${section})へ戻る</a>` : ""}<a class="today-alt" href="#curriculum">レッスン一覧へ</a></div>
      <a class="today-review" href="#/review"><span>今日の復習</span>
        <span>${due ? `<b>${due}</b>問<small class="review-min">約 ${Math.max(1, Math.ceil(due / 3))} 分</small> ${icon("arrow", "sm")}` : `<span class="zero">なし${upcoming ? ` · この先 ${upcoming} 問` : ""}</span>`}</span></a>
      <p class="save-state" id="save-state" aria-live="polite">保存の状態を確かめています…</p>
    </section>

    <section class="progress-block" aria-labelledby="dye-title">
      <div class="progress-head">
        <h2 id="dye-title">染まり具合</h2>
        <p>クイズ合格以上 <b>${deep}</b> / ${all.length}</p>
      </div>
      <nav class="dye-map" aria-label="部ごとの染まり具合">${cur.parts.map((p, pi) => {
        const ls = p.chapters.flatMap((c) => c.lessons);
        const n = [0, 0, 0, 0, 0];
        ls.forEach((l) => n[levels[l.id]]++);
        return `<a class="dye-part" href="#part-${pi + 1}" aria-label="${esc(p.title)}: ${ls.length} レッスン。${DYE.map((d, i) => n[i] ? `${d.meaning} ${n[i]}` : "").filter(Boolean).join("、")}。一覧へ">
          <span class="dye-part-tag" aria-hidden="true">${["一", "二", "三", "四"][pi] ?? pi + 1}</span>
          <span class="dye-cells" aria-hidden="true">${p.chapters.map((c) => `<span class="dye-ch">${c.lessons.map((l) => dyeCell(l, levels, seen, deepened, hero)).join("")}</span>`).join("")}</span></a>`;
      }).join("")}</nav>
      <p class="dye-map-note"><span class="dye-here" aria-hidden="true"></span>${last ? "前回の続き" : "今日の稽古"}。部の行を押すと、その部の一覧へ</p>
      <details class="dye-key"${Object.keys(S.read).length ? "" : " open"}><summary><span class="dye-key-marks" aria-hidden="true">${DYE.map((_, i) => dyeSwatch(i)).join("")}</span>染まり方の見方</summary>
        <ul class="dye-legend">${DYE.map((d, i) => `<li>${dyeSwatch(i)}<span><span class="dye-name-sm">${dyeRuby(i)} <b class="dye-count">${counts[i]}</b></span><span class="dye-meaning">${d.meaning}</span></span></li>`).join("")}</ul></details>
      <div class="home-stats">
        ${due ? `<div><div class="stat-label">復習で卒業</div><div class="stat-value">${graduated}<small>問</small></div></div>`
          : `<a href="#/review"><div class="stat-label">次の復習</div><div class="stat-value">${nextReview}</div></a>`}
        <a href="#/reading"><div class="stat-label">読解の正解</div><div class="stat-value">${readingOk}<small>/ ${readingTotal}</small></div></a>
        <div><div class="stat-label">この7日の稽古</div><div class="stat-value">${weekDays}<small>日</small></div></div>
      </div>
      ${standalone || S.settings.hideInstall ? "" : `<div class="install-hint" id="install-hint"><details><summary>ホーム画面に追加する方法</summary>
        <p>iPhone は Safari の共有ボタン →「ホーム画面に追加」、Android は Chrome のメニュー →「ホーム画面に追加」。</p></details>
        <button class="close" type="button" data-hide-install aria-label="この案内を閉じる">${icon("cross")}</button></div>`}
    </section>

    <section class="curriculum" id="curriculum" aria-label="レッスン一覧">
      ${cur.parts.map((p, pi) => `
        <div class="part-block" id="part-${pi + 1}">
          <div class="part-tag" aria-hidden="true">${["第一部", "第二部", "第三部", "第四部"][pi] ?? esc(p.title)}</div>
          <div>
            <h2 class="part-title">${esc(p.title)}</h2>
            <p class="part-desc">${esc(p.description)}</p>
            ${p.chapters.map((c) => `
              <h3 class="chapter-name"><span>${esc(c.title)}</span></h3>
              <ul class="lesson-rows">
                ${c.lessons.map((l) => {
                  const isHero = l.id === hero.id;
                  const tag = isHero ? (last ? "前回の続き" : "今日の稽古") : last && l.id === next.id ? "次の新しいレッスン" : "";
                  return `<li class="lesson-row ${isHero ? "is-next" : ""}"><a href="#/lesson/${esc(l.id)}">${dyeSwatch(levels[l.id])}
                    <span class="row-title">${tag ? `<b>${tag}</b>` : ""}${esc(l.title)}</span>
                    <span class="row-meta">${levels[l.id] ? DYE[levels[l.id]].meaning : `${l.minutes}分`}</span></a></li>`;
                }).join("")}
              </ul>`).join("")}
          </div>
        </div>`).join("")}
    </section>`;
  view().onclick = (ev) => {
    if (ev.target.closest("[data-hide-install]")) {
      S.settings.hideInstall = true;
      save();
      $("#install-hint")?.remove();
      return;
    }
    const a = ev.target.closest("[data-resume]");
    if (a && a.dataset.resume) pendingSection = a.dataset.resume;
  };
  homeSaveState(all);
}

let pendingSection = null;
// 図の部品の URL を数えるので、読み込みの記録を多めに残す
performance.setResourceTimingBufferSize?.(1000);

/** ホームの「この端末に保存済みか」。全レッスンの JSON がキャッシュにあるかを数える */
async function homeSaveState(all) {
  const el = $("#save-state");
  if (!el) return;
  if (!("caches" in window) || !("serviceWorker" in navigator)) { el.textContent = "このブラウザはオフライン保存に対応していません"; el.className = "save-state ng"; return; }
  try {
    const shell = ["./", "app.js", "app.css", "mobile.css", "data/curriculum.json", "data/reading.json", "data/glossary.json"];
    const files = [...shell, ...all.map((l) => `data/lessons/${encodeURIComponent(l.id)}.json`)];
    const hits = await Promise.all(files.map((f) => caches.match(f)));
    const n = hits.slice(shell.length).filter(Boolean).length;
    const ready = !!navigator.serviceWorker.controller && hits.every(Boolean);
    const figures = await mermaidCached();
    if (!$("#save-state")) return;
    // 準備中は失敗ではないので、注意の朱ではなく静かな色の点
    el.className = "save-state " + (ready ? "ok" : navigator.serviceWorker.controller ? "ng" : "");
    // 保存し終えたら説明はヘッダーの印に任せ、読み上げ用に残す。準備中や失敗のときだけヒーローに見せる
    savedAll = ready && figures;
    S.allSaved = savedAll;
    save();
    el.classList.toggle("visually-hidden", savedAll);
    updateHeader();
    el.textContent = ready && figures ? `保存済み(全 ${all.length} レッスンと図)。電波なしで読めます`
      : ready ? `保存済み(全 ${all.length} レッスン)。図は次に電波のある所で開くと保存`
      : !navigator.serviceWorker.controller ? "保存の準備中。次に電波のある所で開くと、電波なしで読めます"
      : `保存済み ${n} / ${all.length}。次に電波のある所で開くと残りも保存`;
  } catch (e) {
    console.warn("保存の状態を確かめられませんでした", e);
    el.textContent = "保存の状態を確かめられませんでした";
  }
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
  const readingDone = (L.reading || []).filter((_, i) => S.reading[`${id}@${i}`]?.ok).length;
  const writeDone = L.exercises.filter((e) => S.exercise[e.key]?.selfPass).length;
  const steps = [
    ["read", "読む", !!S.read[id]],
    L.atWork?.length ? ["atwork", "仕事のコード", seenSection(id, "atwork")] : null,
    L.samples.length ? ["run", SECTION_LABEL.run, seenSection(id, "run")] : null,
    // 段の順は染まりの順(読む → クイズ → 演習)。読解は染まりの条件に入らない腕試しなので、その後ろに置く
    // (合格後の「次へ」と、ヒーローの「演習ができたら藍に」が別の段を指さないように)。段の名前は条件の言葉と同じ
    ["quiz", SECTION_LABEL.quiz, !!(quiz && quiz.best >= Math.ceil(L.quiz.length * 0.8))],
    // 演習・読解は全問そろって「済み」。途中は「1/3」のように数を添える
    L.exercises.length ? ["write", SECTION_LABEL.write, writeDone === L.exercises.length, false, writeDone] : null,
    L.reading?.length ? ["reading", "読解", readingDone === L.reading.length, false, readingDone] : null,
    L.sts || L.checklist.length ? ["sts", "STSで試す", L.checklist.length > 0 && L.checklist.every((c) => checked.includes(c.key)), true] : null,
  ].filter(Boolean);
  // 見出しの番号は段の並びの位置(読むが 01)。段にない節(用語など)は番号なし
  const title = (key, text) => {
    const n = steps.findIndex((st) => st[0] === key) + 1;
    return `<h2 class="section-title" id="${key}-title">${n ? `<span class="no">${String(n).padStart(2, "0")}</span>` : ""}${text}</h2>`;
  };
  const total = { reading: L.reading?.length, write: L.exercises.length };
  view().innerHTML = `
    <header class="lesson-hero">
      <nav class="breadcrumb" aria-label="現在地"><a href="#/">ホーム</a> / ${esc(L.part)} / ${esc(L.chapter)}</nav>
      <h1>${esc(L.title)}</h1>
      <p class="summary">${esc(L.summary)}</p>
      <p class="meta"><span class="lesson-dye">${dyeSwatch(level, "lesson-swatch")} <span class="dye-name">${DYE[level].name}(${DYE[level].meaning})</span></span>
        <span>読む ${L.minutes} 分</span>${quiz ? `<span>クイズ最高 ${quiz.best}/${L.quiz.length}</span>` : ""}</p>
    </header>
    <div class="stepper-wrap">
      <div class="stepper-row">
      <nav class="stepper" aria-label="このレッスンの段">
        ${steps.map(([key, label, done, pcOnly, part]) => `<a href="#${key}" data-scroll="${key}" data-step="${key}" class="step ${done ? "done" : ""}">${label}${pcOnly ? ` <span class="pc-only" title="パソコンの STS で手を動かす段">PC</span>` : ""}${!done && part ? ` <small>${part}/${total[key]}</small>` : ""}${done ? `<span class="visually-hidden">(済み)</span>` : ""}</a>`).join("")}
      </nav>
      <details class="step-menu"><summary><span class="visually-hidden">段の一覧。いまの段は</span><span id="step-count">${steps.length}段中1</span><span id="step-now">${steps[0][1]}</span></summary>
        <ol>${steps.map(([key, label, done, pcOnly, part]) => `<li class="${done ? "is-done" : ""}" data-menu-step="${key}"><a href="#${key}" data-scroll="${key}">${label}${!done && part ? ` <small>${part}/${total[key]}</small>` : ""}${pcOnly ? ` <span class="pc-only">PC</span>` : ""}${done ? `<span class="visually-hidden">(済み)</span>` : ""}</a></li>`).join("")}</ol></details>
      <button class="ask" type="button" data-action="tutor" aria-label="AIチューターに質問する">${icon("chat")}<span>質問</span></button>
      </div>
      <div class="sew" aria-hidden="true"></div>
    </div>
    ${L.goals.length ? `<details class="goals"${S.seen?.[id] ? "" : " open"}><summary class="goals-title">このレッスンのゴール(${L.goals.length})</summary><ul>${L.goals.map((g) => `<li>${esc(g)}</li>`).join("")}</ul></details>` : ""}
    <section id="read" class="lesson-section" aria-labelledby="read-title">
      <h2 class="visually-hidden" id="read-title">解説</h2>
      <article class="lesson-body md">${L.bodyHtml}</article>
      <div class="read-action">${S.read[id] ? `<div class="done">${icon("check")}読了済み</div>` : `<button class="btn primary" data-action="read">${icon("check")}読んだ</button>`}</div>
    </section>
    ${L.atWork?.length ? `
    <section id="atwork" class="lesson-section" aria-labelledby="atwork-title">
      ${title("atwork", "仕事のコードではこう出てくる")}
      <p class="section-lead">業務の Spring Boot アプリで、このレッスンの知識が現れる場所です。番号の行と、その直後の説明を照らし合わせながら読みましょう。</p>
      ${L.atWork.map(atWorkBlock).join("")}
    </section>` : ""}
    ${L.samples.length ? `
    <section id="run" class="lesson-section" aria-labelledby="run-title">
      ${title("run", "サンプルを見る")}
      <p class="run-note">スマホ版ではコードを実行できません。出力を予想しながら読んで、PC版か Codespaces 版で動かしてみましょう。</p>
      ${L.samples.map((s) => `<div class="sample"><h3>${esc(s.title)}</h3><div class="md">${s.descriptionHtml}</div>
        <pre><code class="language-java">${esc(s.code)}</code></pre></div>`).join("")}
    </section>` : ""}
    <section id="quiz" class="lesson-section" aria-labelledby="quiz-title">
      ${title("quiz", "クイズ")}
      <form class="quiz" data-action="quiz">
        <ol>${L.quiz.map((q, i) => `<li class="quiz-q"><div class="md">${q.questionHtml}</div>
          ${q.choicesHtml.map((c, j) => `<label class="choice"><input type="radio" name="q${i}" value="${j}"><span>${c}</span></label>`).join("")}</li>`).join("")}</ol>
        <button class="btn primary" type="submit">採点する</button>
      </form>
      <div id="quiz-result" aria-live="polite"></div>
    </section>
    ${L.exercises.length ? `
    <section id="write" class="lesson-section" aria-labelledby="write-title">
      ${title("write", "演習")}
      <p class="run-note">スマホ版は自動採点できないので、<b>採点テスト</b>を読んで自分で確かめ、「できた/まだ」を記録します。
      本物の採点は PC版か Codespaces 版で。電波と APIキーがあれば AI にも見てもらえます。</p>
      ${L.exercises.map((e, i) => exerciseCard(e, i)).join("")}
    </section>` : ""}
    ${L.reading?.length ? `
    <section id="reading" class="lesson-section" aria-labelledby="reading-title">
      ${title("reading", "読解 — コードを読む")}
      <p class="section-lead">実行せずに、頭の中で 1 行ずつ追って答えます。答えたあとに「1 行ずつの追跡」で答え合わせ。</p>
      <ol class="reading-list">${L.reading.map((r, i) => readingCard(r, `${id}@${i}`)).join("")}</ol>
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
    <div class="sheet-backdrop" data-action="tutor"></div>
    ${tutorSheet(L)}`;
  bindLesson(L, meta);
  decorate(view());
  trackLesson(id, steps.map(([key]) => key));
}

const seenSection = (id, key) => !!S.sections?.[id]?.includes(key);

/** 読んでいる段を段の並びに示し、縫い線を進め、「前回の続き」を覚える */
let lessonTracker = null;
function trackLesson(id, keys) {
  lessonTracker?.disconnect();
  const sections = keys.map((k) => document.getElementById(k)).filter(Boolean);
  const stepper = $(".stepper");
  const sew = $(".sew");
  if (!stepper || !sections.length) return;
  // 右端のフェードを、段の一覧と質問ボタンの手前に合わせる
  const wrap = $(".stepper-wrap");
  wrap?.style.setProperty("--tail", `${wrap.offsetWidth - stepper.offsetWidth}px`);
  watchOverflow(stepper, wrap);
  // 前回の位置へ戻るのは、前回いた段を指して開いたときだけ(次の段を指したときは、その段の先頭へ)
  const resume = S.last?.id === id && pendingSection && pendingSection === S.last.section ? { section: S.last.section, off: S.last.off } : null;
  S.last = { id, section: S.last?.id === id ? S.last.section : "read", off: S.last?.id === id ? S.last.off || 0 : 0 };
  S.seen = Object.assign(S.seen || {}, { [id]: true });
  save();
  let saveTimer = null;
  let current = null;
  let pos = null;
  // 位置は「段 + 段の先頭からの距離」で覚える(図の描画で高さが変わっても、同じ所へ戻れるように)
  const flush = () => {
    clearTimeout(saveTimer);
    if (!pos || S.last?.id !== id) return;
    Object.assign(S.last, pos);
    save();
  };
  const setCurrent = (key) => {
    if (key === current) return;
    current = key;
    document.querySelectorAll(".step-menu a[data-scroll]").forEach((a) => {
      if (a.dataset.scroll === key) a.setAttribute("aria-current", "true"); else a.removeAttribute("aria-current");
    });
    stepper.querySelectorAll(".step").forEach((a) => {
      if (a.dataset.step === key) {
        a.setAttribute("aria-current", "true");
        // 横に並んだ段の中で、今の段が見える位置まで送る(縦のスクロールは動かさない)
        const left = a.offsetLeft - (stepper.clientWidth - a.offsetWidth) / 2;
        stepper.scrollTo({ left, behavior: smooth() });
      } else a.removeAttribute("aria-current");
    });
    if (S.last?.id === id && S.last.section !== key) { S.last.section = key; save(); }
  };
  // 仕事のコード・サンプルを「読んだ」とみなすのは、段が画面に 3 秒以上あって、その終わりが画面の中に見えたとき
  // (段の一覧で飛び越した・続きから後ろへ戻っただけの段に、レ点を付けないように)
  const shownSince = {};
  const update = () => {
    const line = 140; // ヘッダーと段の並びの下あたりを「今読んでいる位置」とみなす
    let key = keys[0];
    for (const sec of sections) {
      const r = sec.getBoundingClientRect();
      if (r.top <= line) key = sec.id;
      // 仕事のコード・見るは、段の最後が画面に入ったら「済み」
      const onScreen = r.top < innerHeight && r.bottom > 0;
      if (!onScreen) delete shownSince[sec.id];
      else if (shownSince[sec.id] == null) {
        shownSince[sec.id] = Date.now();
        // スクロールを止めて読んでいても 3 秒後に確かめ直す
        setTimeout(() => { if (document.contains(stepper)) update(); }, 3100);
      }
      if ((sec.id === "atwork" || sec.id === "run") && r.bottom > 0 && r.bottom < innerHeight && Date.now() - shownSince[sec.id] >= 3000 && !seenSection(id, sec.id)) {
        S.sections = Object.assign(S.sections || {}, { [id]: [...(S.sections?.[id] || []), sec.id] });
        save();
        markStep(sec.id);
      }
    }
    setCurrent(key);
    const count = $("#step-count");
    // 分数の形は「済んだ問の数」だけに使うので、いまの位置は「7段中3」と言葉で
    if (count) count.textContent = `${keys.length}段中${keys.indexOf(key) + 1}`;
    const now = $("#step-now");
    if (now) now.textContent = SECTION_LABEL[key];
    // 読んでいる位置を覚える(「続きから」で同じ所へ戻る)。最後の前後リンクまで来たら読み切り
    const secTop = document.getElementById(key).getBoundingClientRect().top;
    pos = { section: key, off: Math.round(line - secTop), end: $(".lesson-nav")?.getBoundingClientRect().top < innerHeight };
    clearTimeout(saveTimer);
    saveTimer = setTimeout(flush, 400);
    const first = sections[0].getBoundingClientRect().top + scrollY;
    const lastSec = sections[sections.length - 1];
    const end = lastSec.getBoundingClientRect().bottom + scrollY - innerHeight;
    const p = Math.max(0, Math.min(1, (scrollY - first + line) / Math.max(1, end - first + line)));
    // 縫い目(9px の目 + 6px の間)の切れ目で止める(目の途中で切れた半端な欠片を出さない)
    if (sew) sew.style.setProperty("--p", `${p >= 1 ? sew.clientWidth : Math.floor((p * sew.clientWidth) / 15) * 15 + (p > 0 ? 9 : 0)}px`);
  };
  let ticking = false;
  const menu = $(".step-menu");
  // 閉じたら、一覧の中にあったフォーカスを段のボタンへ戻す(キーボードで迷子にならないように)
  menu?.addEventListener("toggle", () => {
    if (!menu.open && menu.querySelector("ol")?.contains(document.activeElement)) menu.querySelector("summary").focus({ preventScroll: true });
  });
  const onScroll = () => {
    if (menu?.open) menu.open = false;
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => { ticking = false; if (document.contains(stepper)) update(); });
  };
  addEventListener("scroll", onScroll, { passive: true });
  lessonTracker = { disconnect: () => { removeEventListener("scroll", onScroll); flush(); } };
  update();
  if (pendingSection) {
    const target = document.getElementById(pendingSection);
    requestAnimationFrame(() => {
      const sec = resume && document.getElementById(resume.section);
      if (sec && resume.off > 0) scrollTo({ top: sec.getBoundingClientRect().top + scrollY - 140 + resume.off, behavior: "auto" });
      else if (pendingSection !== "read") target?.scrollIntoView();
    });
  }
  pendingSection = null;
}

/** 仕事のコード。番号の付いた行を、下の説明と結びつける */
function atWorkBlock(a) {
  const notes = Object.entries(a.notesHtml || {});
  return `<article class="atwork">
    ${a.title ? `<h3>${esc(a.title)}</h3>` : ""}
    <div class="atwork-intro md">${a.introHtml}</div>
    ${notes.length ? `<button class="notes-toggle" type="button" data-notes-toggle aria-pressed="false">説明をたたむ</button>` : ""}
    <div class="code-notes"><pre class="code-lines" data-notes="${notes.map(([n]) => Number(n)).join(",")}"><code class="language-java">${esc(a.code)}</code></pre></div>
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
    <pre class="code-lines"><code class="language-java">${esc(r.code)}</code></pre>
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
    <div class="md">${r.explanationHtml}</div>
    ${r.traceHtml?.length ? `<p class="trace-title">1 行ずつの追跡</p><ol class="trace">${r.traceHtml.map((t) => `<li>${t}</li>`).join("")}</ol>` : ""}`;
}

/** 読解フォームの答え合わせ。記録して復習キューに入れる */
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
      const out = li.querySelector(".reading-answer");
      lockAnswer(form, r.answer, choice, () => { out.hidden = true; out.innerHTML = ""; });
      out.innerHTML = readingAnswerHtml(r, ok);
      out.hidden = false;
      decorate(out);
      // レッスンの中なら、段の並びの「読解 n/全部」を進める
      const refs = [...root.querySelectorAll(".reading-q[data-ref]")].map((x) => x.dataset.ref);
      if ($('.step[data-step="reading"]')) stepCount("reading", refs.filter((x) => S.reading[x]?.ok).length, refs.length);
    };
  });
}

function termItem(t, withLesson, lessonTitle) {
  return `<li class="term"><p class="term-name">${esc(t.term)}${t.read ? `<small>${esc(t.read)}</small>` : ""}${t.en ? `<small lang="en">${esc(t.en)}</small>` : ""}</p>
    <p class="term-def">${t.defHtml}</p>
    ${withLesson && t.lesson ? `<a class="term-from" href="#/lesson/${esc(t.lesson)}">${esc(lessonTitle || t.lesson)}</a>` : ""}</li>`;
}

/** 用語集の 1 枚。同じ語がいくつかのレッスンで出るときは、レッスンごとの説明を 1 枚の中に並べる */
function termCard(g, titles) {
  const t = g[0];
  // 英語が見出しと同じ綴りなら繰り返さない(「final(クラス・メソッド)」の括弧の前とも比べる)
  const base = t.term.replace(/[((].*$/, "").trim().toLowerCase();
  const en = g.map((x) => x.en).find((e) => e && e.toLowerCase() !== t.term.toLowerCase() && e.toLowerCase() !== base);
  const head = `<p class="term-name">${esc(t.term)}${t.read ? `<small>${esc(t.read)}</small>` : ""}${en ? `<small lang="en">${esc(en)}</small>` : ""}${g.length > 1 ? ` <span class="term-count">${g.length} レッスン</span>` : ""}</p>`;
  // どの語も「〇〇 で」(レッスン名)を説明の前に置く
  if (g.length === 1) return `<li class="term">${head}${t.lesson ? `<p class="term-from-line"><a class="term-from term-from-head" href="#/lesson/${esc(t.lesson)}">${esc(titles[t.lesson] || t.lesson)}</a><span class="term-de">で</span></p>` : ""}<p class="term-def">${t.defHtml}</p></li>`;
  return `<li class="term">${head}<ul class="term-defs">${g.map((x) => `<li>${x.lesson ? `<p class="term-from-line"><a class="term-from term-from-head" href="#/lesson/${esc(x.lesson)}">${esc(titles[x.lesson] || x.lesson)}</a><span class="term-de">で</span></p>` : ""}
    <p class="term-def">${x.defHtml}</p></li>`).join("")}</ul></li>`;
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

/** 染まり具合を測り直して名札を更新する。深まったら { from, to } を返す(押した場所で染み込みを見せるため) */
function refreshDye(meta) {
  if (!meta) return null;
  const level = dyeLevel(meta);
  const wrap = $(".lesson-dye");
  if (!wrap || wrap.dataset.level === String(level)) return null;
  const from = Number(wrap.dataset.level || 0);
  wrap.dataset.level = level;
  // 深まったときは、メタ行の色見本もボタンのそばと同じ 560ms で 1 回だけ染み込ませる
  const swatch = level > from ? `<span class="dye-burst lesson-swatch" role="img" aria-label="${DYE[level].name}">${dyeDot(from)}<span class="dye dye-${level} ink"></span></span>` : dyeSwatch(level, "lesson-swatch");
  wrap.innerHTML = `${swatch} <span class="dye-name">${DYE[level].name}(${DYE[level].meaning})</span>`;
  return level > from ? { from, to: level } : null;
}

/** 段の並びの「n/全部」を進め、全部そろったら「済み」にする */
function stepCount(key, done, total) {
  if (done >= total) return markStep(key);
  for (const a of [$(`.step[data-step="${key}"]`), $(`[data-menu-step="${key}"] a`)]) {
    if (!a || !done) continue;
    const small = a.querySelector("small") || a.appendChild(document.createElement("small"));
    small.textContent = `${done}/${total}`;
  }
}

/** 段の並びの印を「済み」にする */
function markStep(key) {
  for (const a of [$(`.step[data-step="${key}"]`), $(`[data-menu-step="${key}"]`)]) {
    const cls = a?.matches("li") ? "is-done" : "done";
    if (!a || a.classList.contains(cls)) continue;
    a.classList.add(cls);
    a.querySelector("small")?.remove();
    (a.querySelector("a") || a).insertAdjacentHTML("beforeend", `<span class="visually-hidden">(済み)</span>`);
  }
}

const dyedLine = (d) => d ? `<span class="dyed-line">${dyeBurst(d.from, d.to)}<span>${DYE[d.to].name}に染まった(${DYE[d.to].meaning})</span></span>` : "";

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
      jump.closest("details")?.removeAttribute("open");
      document.getElementById(jump.dataset.scroll)?.scrollIntoView({ behavior: smooth() });
      return;
    }
    const el = ev.target.closest("[data-action]");
    if (!el || el.tagName === "FORM") return;
    const action = el.dataset.action;
    const ex = el.dataset.index != null ? L.exercises[Number(el.dataset.index)] : null;
    if (action === "read") {
      S.read[L.id] = true;
      recordDay();
      const dyed = refreshDye(meta);
      el.outerHTML = `<div class="done">${icon("check")}読了。次はクイズで縹へ</div>${dyedLine(dyed)}`;
      markStep("read");
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
      const dyed = refreshDye(meta);
      el.closest(".self-check").querySelector(".dyed-line")?.remove();
      if (dyed) el.closest(".self-check").insertAdjacentHTML("beforeend", dyedLine(dyed));
      stepCount("write", L.exercises.filter((e) => S.exercise[e.key]?.selfPass).length, L.exercises.length);
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
    } else if (action === "quiz-reset") {
      const form = root.querySelector("form[data-action=quiz]");
      form.reset();
      form.querySelectorAll(".q-explain").forEach((n) => n.remove());
      form.querySelectorAll("label.choice").forEach((l) => l.classList.remove("is-answer", "is-wrong"));
      form.querySelectorAll(".choice-tag").forEach((n) => n.remove());
      form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = false; });
      form.querySelector("button[type=submit]").hidden = false;
      $("#quiz-result").innerHTML = "";
      form.scrollIntoView({ behavior: smooth() });
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
    form.querySelectorAll(".q-explain").forEach((n) => n.remove());
    L.quiz.forEach((q, i) => {
      const v = form.querySelector(`input[name=q${i}]:checked`);
      const chosen = v ? Number(v.value) : -1;
      const ok = chosen === q.answer;
      if (ok) score++;
      if (chosen >= 0) practice(`${L.id}#${i}`, "quiz", L.id, ok);
      const li = form.querySelectorAll(".quiz-q")[i];
      li.querySelectorAll("label.choice").forEach((label, j) => {
        label.classList.toggle("is-answer", j === q.answer);
        label.classList.toggle("is-wrong", j === chosen && !ok);
        // 色だけに頼らない: 選んだ誤答には「あなたの答え」、正解には「正解」と文字で出す
        label.querySelector(".choice-tag")?.remove();
        if (j === chosen && !ok) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
        if (j === q.answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
      });
      li.insertAdjacentHTML("beforeend", `<div class="q-explain ${ok ? "correct" : "wrong"}"><span class="q-mark">${icon(ok ? "check" : "cross", "sm")} ${ok ? "正解" : chosen < 0 ? "未回答" : "不正解"}</span>
        <div class="explanation md">${q.explanationHtml}</div></div>`);
    });
    decorate(form);
    form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = true; });
    const submit = form.querySelector("button[type=submit]");
    submit.hidden = true;
    const prev = S.quiz[L.id];
    S.quiz[L.id] = { best: Math.max(score, prev?.best ?? 0), last: score };
    recordDay();
    const dyed = refreshDye(meta);
    const pass = Math.ceil(L.quiz.length * 0.8);
    if (score >= pass) markStep("quiz");
    const wrong = L.quiz.map((q, i) => i).filter((i) => !form.querySelectorAll(".quiz-q")[i].querySelector(".q-explain.correct"));
    form.querySelectorAll(".quiz-q").forEach((li, i) => { li.id = `q-${i + 1}`; });
    // 次の一手: 合格なら段の並びの次(演習 → 読解)、届かなければ間違えた問の見直しと解き直し
    const nextKey = score >= pass ? (L.exercises.length ? "write" : L.reading?.length ? "reading" : null) : null;
    const level = meta ? dyeLevel(meta) : 0;
    $("#quiz-result").innerHTML = `<div class="quiz-result"><div class="quiz-score"><span class="big">${score}<span class="slash">/</span>${L.quiz.length}</span>${scoreStitches(L.quiz.map((_, i) => !wrong.includes(i)))}
      <span>${score === L.quiz.length ? "満点" : score >= pass ? `合格(${pass} 問以上)` : `あと ${pass - score} 問で合格(${pass} 問以上)`}</span></div>${dyedLine(dyed)}
      ${wrong.length ? `<p class="quiz-wrong">間違えた問: ${wrong.map((i) => `<a href="#q-${i + 1}" data-scroll="q-${i + 1}">問 ${i + 1}</a>`).join(" ")}</p>` : ""}
      <div class="quiz-next">${nextKey ? `<a class="btn primary" href="#${nextKey}" data-scroll="${nextKey}">${nextKey === "write" ? "演習へ進む" : "読解へ進む"}</a>` : ""}
        <button class="btn" type="button" data-action="quiz-reset">もう一度解く</button></div>
      ${score >= pass && level === 2 && L.exercises.length ? `<p class="quiz-ai">${dyeSwatch(3)}演習ができたら藍に</p>` : ""}
      <p class="muted small">各問の下に解説を出しました。解いた問題は復習に入り、1日後 → 3日後 → 7日後 → 14日後 に出題されます。</p></div>`;
    decorate($("#quiz-result"));
    $("#quiz-result").scrollIntoView({ behavior: smooth(), block: "center" });
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
  const lessons = allLessons(cur);
  const partOf = Object.fromEntries(lessons.map((l) => [l.id, cur.parts.indexOf(l.part)]));
  const filter = S.settings.readingFilter || "todo";
  const part = S.settings.readingPart ?? "all";
  const level = S.settings.readingLevel ?? "all";
  const pick = (r) => {
    const st = S.reading[r.ref];
    if (part !== "all" && String(partOf[r.lesson]) !== part) return false;
    if (level !== "all" && String(Math.max(1, Math.min(3, r.level || 1))) !== level) return false;
    if (filter === "todo") return !st;
    if (filter === "wrong") return st && !st.ok;
    return true;
  };
  const shown = items.filter(pick);
  const correct = items.filter((r) => S.reading[r.ref]?.ok).length;
  const PAGE = 20;
  view().innerHTML = `
    <h1 class="page-title">コードを読む</h1>
    <p class="page-lead">業務のコードを読む力を、実行せずに頭の中で追って鍛えます。全 ${items.length} 問のうち <b>${correct}</b> 問に正解。答えた問題は復習にも出ます。</p>
    <div class="reading-filter" role="group" aria-label="表示する問題">
      ${[["todo", "まだ解いていない"], ["wrong", "間違えた"], ["all", "すべて"]].map(([k, label]) =>
        `<button type="button" data-filter="${k}" aria-pressed="${filter === k}">${label}</button>`).join("")}
      <select data-select="readingPart" aria-label="部で絞る"><option value="all">すべての部</option>
        ${cur.parts.map((p, i) => `<option value="${i}" ${part === String(i) ? "selected" : ""}>${esc(p.title)}</option>`).join("")}</select>
      <select data-select="readingLevel" aria-label="難しさで絞る"><option value="all">すべての難しさ</option>
        ${["やさしい", "ふつう", "むずかしい"].map((t, i) => `<option value="${i + 1}" ${level === String(i + 1) ? "selected" : ""}>${t}</option>`).join("")}</select>
    </div>
    <p class="glossary-count">${shown.length} 問</p>
    ${shown.length ? `<ol class="reading-list" id="reading-list"></ol><div class="more"><button class="btn" type="button" id="more" hidden></button></div>`
      : `<div class="card empty-state"><p>${filter === "wrong" ? "間違えた問題はありません。" : "この条件の問題はありません。"}</p></div>`}`;
  view().querySelectorAll("[data-filter]").forEach((b) => {
    b.onclick = () => { S.settings.readingFilter = b.dataset.filter; save(); renderReadingPage(); };
  });
  view().querySelectorAll("[data-select]").forEach((sel) => {
    sel.onchange = () => { S.settings[sel.dataset.select] = sel.value; save(); renderReadingPage(); };
  });
  const byRef = Object.fromEntries(items.map((r) => [r.ref, r]));
  const list = $("#reading-list");
  const more = $("#more");
  let count = 0;
  // 少しずつ描く(171 問を一度に描くと、スマホでコードの色付けが重い)
  const showMore = () => {
    const chunk = shown.slice(count, count + PAGE);
    const tmp = document.createElement("ol");
    tmp.innerHTML = chunk.map((r) => readingCard(r, r.ref, `<a href="#/lesson/${esc(r.lesson)}">${esc(titles[r.lesson] || r.lesson)}</a>`)).join("");
    bindReading(tmp, (ref) => byRef[ref]);
    decorate(tmp);
    list.append(...tmp.children);
    count += chunk.length;
    more.hidden = count >= shown.length;
    more.textContent = `もっと見る(残り ${shown.length - count} 問)`;
  };
  if (list) { more.onclick = showMore; showMore(); }
}

// ------------------------------------------------------------ 用語集

// 読みの最初の文字から「あ行」「か行」…の見出しを決める(英字・記号で始まるものは「A-Z」にまとめて先頭に)
const KANA_ROWS = ["あいうえおぁぃぅぇぉゔ", "かきくけこがぎぐげご", "さしすせそざじずぜぞ", "たちつてとだぢづでどっ",
  "なにぬねの", "はひふへほばびぶべぼぱぴぷぺぽ", "まみむめも", "やゆよゃゅょ", "らりるれろ", "わをん"];
function kanaRow(t) {
  // 英字で始まる語(equals・Iterator・@Bean など)は読みではなく「A-Z」に(索引の A-Z から探せるように)
  if (/^[A-Za-z@]/.test(t.term.trim())) return "A-Z";
  const c = (t.read || t.term).trim().charAt(0);
  const hira = String.fromCharCode(c >= "ァ" && c <= "ヶ" ? c.charCodeAt(0) - 0x60 : c.charCodeAt(0));
  const i = KANA_ROWS.findIndex((row) => row.includes(hira));
  return i >= 0 ? "あかさたなはまやらわ"[i] + "行" : /[A-Za-z@]/.test(c) ? "A-Z" : "その他";
}

async function renderGlossary() {
  const [terms, cur] = await Promise.all([glossary(), curriculum()]);
  const titles = Object.fromEntries(allLessons(cur).map((l) => [l.id, l.title]));
  const plain = (html) => html.replace(/<[^>]+>/g, "");
  // 同じ語(大文字小文字は区別しない)を 1 枚にまとめ、読み順に並べる
  const groups = new Map();
  [...terms].sort((a, b) => (a.read || a.term).localeCompare(b.read || b.term, "ja")).forEach((t) => {
    const k = t.term.trim().toLowerCase();
    groups.set(k, [...(groups.get(k) || []), t]);
  });
  const ROW_ORDER = ["A-Z", ..."あかさたなはまやらわ".split("").map((k) => k + "行"), "その他"];
  const index = [...groups.values()].map((g) => ({ g, row: kanaRow(g[0]),
    key: g.map((t) => [t.term, t.read, t.en, plain(t.defHtml), titles[t.lesson]].join(" ")).join(" ").toLowerCase() }))
    // 行の順に並べ、A-Z の中は綴りの順
    .sort((a, b) => ROW_ORDER.indexOf(a.row) - ROW_ORDER.indexOf(b.row)
      || (a.row === "A-Z" ? a.g[0].term.replace(/^@/, "").localeCompare(b.g[0].term.replace(/^@/, ""), "en", { sensitivity: "base" }) : 0));
  const rows = [...new Set(index.map((x) => x.row))];
  view().innerHTML = `
    <h1 class="page-title">用語集</h1>
    <p class="page-lead">全レッスンの用語 ${index.length} 語を読み順に。読み・英語・説明のどれでも探せます。いくつかのレッスンに出る語は、レッスンごとの説明を 1 枚にまとめています。</p>
    <div class="search">${icon("search")}<input type="search" id="q" placeholder="例: Bean、ちゅうにゅう、injection" aria-label="用語を探す" autocomplete="off"></div>
    <div class="kana-wrap"><nav class="kana-index" id="kana-index" aria-label="行で飛ぶ"><a href="#q" class="kana-search" aria-label="用語を探す">${icon("search", "sm")}</a>${rows.map((r, i) => `<a href="#g-${i}" data-row="${i}">${r.replace("行", "")}</a>`).join("")}</nav></div>
    <p class="glossary-count" id="count" aria-live="polite"></p>
    <ul class="term-list" id="terms"></ul>`;
  const list = $("#terms");
  const count = $("#count");
  const show = (q) => {
    const words = q.toLowerCase().split(/\s+/).filter(Boolean);
    const hit = index.filter((x) => words.every((w) => x.key.includes(w)));
    count.textContent = q ? `${hit.length} 語` : "";
    $("#kana-index").hidden = !!q;
    let row = "";
    list.innerHTML = hit.map((x) => {
      const head = !q && x.row !== row ? `<li class="term-group" id="g-${rows.indexOf(x.row)}"><h2>${x.row}</h2></li>` : "";
      row = x.row;
      return head + termCard(x.g, titles);
    }).join("");
  };
  $("#q").addEventListener("input", (e) => show(e.target.value.trim()));
  // 索引の虫めがね: 先頭まで戻って検索欄へ
  $(".kana-search").onclick = (e) => {
    e.preventDefault();
    scrollTo({ top: 0, behavior: smooth() });
    $("#q").focus({ preventScroll: true });
  };
  show("");
  // いま読んでいる行を索引で強調し、索引の見える位置に送る
  const index$ = $("#kana-index");
  watchOverflow(index$);
  const onScroll = () => {
    if (!document.contains(index$)) return removeEventListener("scroll", onScroll);
    const line = index$.getBoundingClientRect().bottom + 8;
    let cur = null;
    for (const h of list.querySelectorAll(".term-group")) { if (h.getBoundingClientRect().top <= line) cur = h.id.slice(2); else break; }
    index$.querySelectorAll("a[data-row]").forEach((a) => {
      const on = a.dataset.row === cur;
      if (on && a.getAttribute("aria-current") !== "true") index$.scrollTo({ left: a.offsetLeft - index$.clientWidth / 2, behavior: smooth() });
      on ? a.setAttribute("aria-current", "true") : a.removeAttribute("aria-current");
    });
  };
  addEventListener("scroll", onScroll, { passive: true });
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
      const nodes = [...root.querySelectorAll(".mermaid:not([data-processed])")];
      await mermaidModule.run({ nodes });
      fitFigures(nodes);
    } catch (e) {
      // 図を描けないとき(初回オフラインなど)は、図の元のテキストのまま見せる
      root.querySelectorAll(".mermaid:not([data-processed])").forEach((d) => { d.style.whiteSpace = "pre"; d.style.textAlign = "left"; });
    }
  }
  // 読むためのコードは折り返す。書き写す・実行するもの(サンプル・演習・本文の例)はそのまま
  root.querySelectorAll(".quiz-q .md pre, .q-explain pre, .review-quiz pre, .quiz-answer pre, .reading-answer pre").forEach((pre) => pre.classList.add("code-lines"));
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
  root.querySelectorAll(".choice span, :not(pre) > code").forEach(softBreaks);
  if (window.hljs) {
    root.querySelectorAll("pre code:not([data-highlighted]):not([data-lines])").forEach((el) => {
      if (!el.className.includes("language-")) el.classList.add("language-java");
      if (!el.classList.contains("language-text")) window.hljs.highlightElement(el);
    });
  }
}

/** コードを 1 行ずつの要素に分け、行番号を付ける(色付けの span が行をまたいでも、行ごとに閉じて開き直す) */
function numberLines(code) {
  // 400px 以下は字下げを半分にして 1 行の字数を稼ぐ(型と名前が折り返しで離れないように。行番号の列を詰めるより効く)
  const narrow = matchMedia("(max-width: 400px)").matches;
  const text = code.textContent.replace(/\n$/, "").replace(/^ +/gm, (m) => (narrow ? " ".repeat(Math.ceil(m.length / 2)) : m));
  const html = window.hljs ? window.hljs.highlight(text, { language: "java", ignoreIllegals: true }).value : esc(text);
  const notes = new Set((code.parentElement.dataset.notes || "").split(",").filter(Boolean).map(Number));
  const lines = [];
  let open = [];
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

/** 長い英字(例外名・修飾した名前)に折り返しの候補を入れる。. ( , の後ろと、小文字→大文字の境目 */
function softBreaks(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  for (let n = walker.nextNode(); n; n = walker.nextNode()) if (/[A-Za-z0-9_.(,]{20,}/.test(n.data)) nodes.push(n);
  for (const n of nodes) {
    const span = document.createElement("span");
    span.innerHTML = esc(n.data).replace(/[A-Za-z0-9_.(,]{20,}/g, (w) => w.replace(/([.(,])/g, "$1<wbr>").replace(/([a-z])([A-Z])/g, "$1<wbr>$2"));
    n.replaceWith(...span.childNodes);
  }
}

/** 仕事のコードの説明を、その行の直後に挟む(下の説明まで往復しないように)。「説明をたたむ」でコードだけ通して読める */
function inlineNotes(root) {
  root.querySelectorAll(".atwork").forEach((art) => {
    const pre = art.querySelector("pre.code-lines");
    const list = art.querySelector(".line-notes");
    if (!pre || !list || pre.dataset.inline) return;
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

// 「説明をたたむ」: 行の間の説明を隠して、コードだけを通して読む(下に説明の一覧を出す)。選んだ形は覚えておく
function applyNotesFold(root) {
  const folded = !!S.settings.foldNotes;
  root.querySelectorAll(".atwork").forEach((art) => {
    art.classList.toggle("notes-folded", folded);
    const b = art.querySelector("[data-notes-toggle]");
    if (b) { b.setAttribute("aria-pressed", String(folded)); b.textContent = folded ? "説明を行の間に出す" : "説明をたたむ"; }
  });
}
document.addEventListener("click", (e) => {
  if (!e.target.closest("[data-notes-toggle]")) return;
  S.settings.foldNotes = !S.settings.foldNotes;
  save();
  applyNotesFold(document);
});

/** 電波のあるうちに、図の描画に使う部品を読み込んでおく(地下鉄で初めて図を開いても描けるように) */
const MERMAID_KEY = "javadojo-m-mermaid-files";
/** 図の部品(入口 + 図の種類ごとに分割された部品)が全部この端末のキャッシュにあるか */
async function mermaidCached() {
  let files = [];
  try { files = JSON.parse(localStorage.getItem(MERMAID_KEY) || "[]"); } catch (e) { files = []; }
  if (!files.includes(MERMAID_URL)) return false;
  const hits = await Promise.all(files.map((f) => caches.match(f)));
  return hits.every(Boolean);
}

async function warmMermaid() {
  if (!navigator.onLine || !navigator.serviceWorker?.controller || await mermaidCached()) return;
  const run = async () => {
    try {
      if (!mermaidModule) mermaidModule = (await import(MERMAID_URL)).default;
      mermaidModule.initialize(mermaidTheme());
      const samples = ["flowchart LR\n a-->b", "sequenceDiagram\n a->>b: x", "classDiagram\n A <|-- B", "erDiagram\n A ||--o{ B : has", "stateDiagram-v2\n [*] --> A"];
      for (const [i, src] of samples.entries()) await mermaidModule.render(`warm-${i}`, src);
      // 実際に読み込んだ部品の URL を覚える(Service Worker が同じ URL でキャッシュする)。少し待ってから数える
      await new Promise((r) => setTimeout(r, 1500));
      const base = MERMAID_URL.slice(0, MERMAID_URL.lastIndexOf("/") + 1);
      const files = [...new Set([MERMAID_URL, ...performance.getEntriesByType("resource").map((e) => e.name).filter((n) => n.startsWith(base))])];
      // 以前に Service Worker の外で読んだ部品は、ここで取り直してキャッシュに入れる
      await Promise.all(files.map((f) => fetch(f, { mode: "cors" }).catch(() => null)));
      localStorage.setItem(MERMAID_KEY, JSON.stringify(files));
      localStorage.removeItem("javadojo-m-mermaid");
      if ($("#save-state")) homeSaveState(allLessons(await curriculum()));
    } catch (e) {
      console.warn("図の部品を先に読み込めませんでした", e);
    }
  };
  (window.requestIdleCallback || ((f) => setTimeout(f, 3000)))(run);
}

// ------------------------------------------------------------ 起動

// 画面の切り替えは「#/…」。本文中の「#見出し」のようなリンクは、画面を変えずにその場所へスクロールする
let currentRoute = location.hash.startsWith("#/") ? location.hash : "#/";
window.addEventListener("hashchange", () => {
  if (location.hash && !location.hash.startsWith("#/")) {
    const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    history.replaceState(null, "", currentRoute);
    target?.scrollIntoView({ behavior: smooth() });
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
  if (e.key === "Escape") document.querySelector(".step-menu[open]")?.removeAttribute("open");
});
// 段の一覧は、外側を押す・項目を選ぶと閉じる
document.addEventListener("click", (e) => {
  const menu = document.querySelector(".step-menu[open]");
  // 後ろの幕(details 自身の ::before)を押したときは、押した先が details そのものになる
  if (menu && (!menu.contains(e.target) || e.target === menu || e.target.closest("ol a"))) menu.removeAttribute("open");
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
warmMermaid();
navigator.serviceWorker?.addEventListener("controllerchange", async () => {
  warmMermaid();
  if ($("#save-state")) homeSaveState(allLessons(await curriculum()));
});
