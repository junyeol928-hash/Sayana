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

function reviewAnswer(ref, correct, choice) {
  const item = S.review[ref];
  const t = today();
  item.last = Date.now();
  // 今日の復習の数と正解(復習の画面の上と、終わったときに見せる)
  const log = S.reviewLog = S.reviewLog || {};
  // 同じ問題を 1 日に 2 回解いたら 1 問と数える(PC版の ReviewService#todayTally と同じ)。最後の答えの正誤で数える
  // 選んだ答えも残す(今日の終わりの「間違えた問題」で、どう読み違えたかを出すため。演習は無し)
  log[t] = { refs: Object.assign({}, log[t]?.refs, { [ref]: correct }),
    choices: Object.assign({}, log[t]?.choices, choice == null ? {} : { [ref]: choice }),
    ...(log[t]?.retry ? { retry: log[t].retry } : {}) };
  for (const d of Object.keys(log)) if (d < addDays(t, -60)) delete log[d];
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

function dueItems() {
  const t = today();
  return Object.entries(S.review)
    .filter(([, it]) => !it.graduated && it.due <= t)
    .sort((a, b) => a[1].due.localeCompare(b[1].due));
}

/**
 * 次に出す復習。同じレッスンの問題を続けて出さず、いちばん長く復習していないレッスンから出す(交互に混ぜる)。
 * 似た問題を続けて解くより、混ぜて解くほうが「どの知識を使う問題か」を見分ける力がつく。PC版の ReviewService と同じ規則
 */
function nextDue(due) {
  const lastByLesson = {};
  for (const it of Object.values(S.review)) {
    if (it.last && (lastByLesson[it.lessonId] || 0) < it.last) lastByLesson[it.lessonId] = it.last;
  }
  let best = due[0];
  for (const e of due) if ((lastByLesson[e[1].lessonId] || 0) < (lastByLesson[best[1].lessonId] || 0)) best = e;
  return best;
}

/** 選択肢の並び。復習では位置で覚えてしまわないよう入れ替える。同じ日は同じ並び(開き直しても変わらない) */
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

/** 今日の復習の数と正解。results は答えた順の正誤(運針に並べる)、missed は間違えたままの問題 */
const todayReviewed = () => {
  const entries = Object.entries(S.reviewLog?.[today()]?.refs || {});
  const results = entries.map(([, ok]) => ok);
  return { n: entries.length, ok: results.filter(Boolean).length, results, entries, missed: entries.filter(([, ok]) => !ok).map(([r]) => r) };
};

/**
 * 今日の正誤を運針で並べる(10 目で 1 行。正解は糸、間違えた問は針穴に朱の ×。色だけでなく形でも分かる)。
 * basted は、解き直しで正しく解けた問(× の上に仮縫いの破線を重ねる。本縫いは明日の復習)
 */
function stitchRows(results, basted = []) {
  const rows = [];
  const marks = results.map((x, i) => (x ? "sewn" : basted[i] ? "miss basted" : "miss"));
  for (let i = 0; i < marks.length; i += 10) rows.push(marks.slice(i, i + 10));
  const ok = results.filter(Boolean).length;
  const b = basted.filter(Boolean).length;
  return `<div class="break-rows" role="img" aria-label="${results.length} 問のうち ${ok} 問正解${b ? `、解き直しで ${b} 問を仮縫い` : ""}">${rows.map((r) =>
    `<div class="unshin break-unshin" style="--steps: 10">${r.map((c) => `<i class="${c}"></i>`).join("")}</div>`).join("")}</div>`;
}

/** 復習の問いの、レッスンの中の場所(読み直すで、その問いそのものへ飛ぶ。演習は段の頭) */
function reviewSpot(it, ref) {
  return { quiz: `qq-${ref.split("#")[1]}`, check: `check-${ref.split("!")[1]}`, reading: `rq-${ref.replace("@", "-")}` }[it.type] || "write";
}

/**
 * 間違えた問題の行: レッスン名と、どう読み違えたか(選んだ誤答の why の 1 文目)・読み直す(3 件まで。ほかは数だけ)。
 * why の無い型(演習)と、選んだ答えの記録が無い日の分は、問いの書き出し
 */
// shown 件まで行に出し、超える分は「ほか n 問」の畳みの中に同じ行で(今日の終わりは 5 件。区切りは 3 件)。
// 畳みの見出しに「まだ」の数も添える(「まだの 2 問を、もう一度」のボタンと、見える「まだ」の数が合わなかった)
async function missedList(refs, shown = 3) {
  const rows = [];
  const chosen = S.reviewLog?.[today()]?.choices || {};
  const stillFlags = []; // 行と同じ順の「まだ」か(L の無い問は行にならないので、refs の添字では数えない)
  for (const ref of refs) {
    const it = S.review[ref];
    const L = it && (await lesson(it.lessonId).catch(() => null));
    if (!L) continue;
    const src = it.type === "quiz" ? L.quiz[Number(ref.split("#")[1])]
      : it.type === "check" ? L.checks?.[Number(ref.split("!")[1])]
      : it.type === "reading" ? L.reading?.[Number(ref.split("@")[1])] : null;
    const why = src?.whyHtml?.[chosen[ref]];
    // why の 1 文目は HTML のまま出す(`i += 3` のようなコードの書体を落とさない)。「。」はタグの中に無い
    let body;
    if (why) body = why.split("。")[0];
    else {
      const d = document.createElement("div");
      d.innerHTML = it.type === "reading" ? esc(src?.title || "") : src ? src.questionHtml : esc(L.exercises.find((x) => x.key === ref)?.title || "");
      const text = d.textContent.replace(/\s+/g, " ").trim();
      body = esc(text.slice(0, 30)) + (text.length > 30 ? "…" : "");
    }
    const r = retry?.day === today() ? retry.results?.[ref] : undefined;
    const tag = r === true ? `<span class="missed-basted">仮縫いできた</span>` : r === false ? `<span class="missed-again">まだ</span>` : "";
    stillFlags.push(r === false);
    rows.push(`<li><span class="missed-mark" aria-hidden="true"></span><span class="missed-body"><b>${esc(L.title)}${tag}</b><span class="missed-read">${why ? `<span class="missed-why">あなたの読み</span>` : ""}${body}</span></span><a class="missed-link" href="#/lesson/${esc(L.id)}" data-resume="${reviewSpot(it, ref)}" data-peek="review">読み直す</a></li>`);
  }
  if (!rows.length) return "";
  const more = rows.slice(shown);
  const stillHidden = stillFlags.slice(shown).filter(Boolean).length;
  return `<ul class="missed-list" aria-label="間違えた問題">${rows.slice(0, shown).join("")}</ul>${more.length ? `<details class="missed-more"><summary>ほか ${more.length} 問${stillHidden ? `(まだ ${stillHidden})` : ""}</summary>
    <ul class="missed-list" aria-label="間違えた問題の続き">${more.join("")}</ul></details>` : ""}`;
}

/**
 * 今日の復習の目安。10 問の区切りの単位で言う。コードを読む問題は長めに数える
 * (クイズ・手を止めて 25 秒、読解 45 秒、演習の書き直し 3 分)
 */
function reviewMinutes() {
  const due = dueItems();
  const secs = { quiz: 25, check: 25, reading: 45, exercise: 180 };
  const first = due.slice(0, 10).reduce((t, [, it]) => t + (secs[it.type] || 30), 0);
  const min = Math.max(1, Math.round(first / 60));
  return due.length > 10 ? `まず 10 問 約 ${min} 分` : `約 ${min} 分`;
}

/** 復習の上の行: 残りの数と、次の区切り(10 問)までの進み */
function reviewProgress(left, n, answered) {
  // 区切り(10 問)の中で何問目か。答えた直後は今の問を含めて数え、新しい問を出すときは区切りの頭から。
  // 分母は区切りの 10 か、今日の残りがそれより少なければ残りまで(残り 0 で「2 / 10」と出さない)
  const inBlock = answered ? ((n - 1) % 10) + 1 : n % 10;
  if (!n) return `残り <b>${left}</b> 問`;
  return `残り <b>${left}</b> 問<span>今日 ${inBlock} / ${Math.min(10, inBlock + left)}${n >= 10 ? `(${n} 問済み)` : ""}</span>`;
}

/** 答えたあとの「次は」。押せる札に見えないよう、ボタンの横に置く小さな文字 */
function nextLabel(it) {
  return it.graduated ? "卒業。もう出ません" : `次は ${INTERVALS[Math.min(it.stage, INTERVALS.length - 1)]} 日後`;
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
/**
 * ホームの下の数。枠の 3 マス(汎用のダッシュボードの形)をやめ、藍の地図と同じ布の言葉で 1 行ずつ:
 * この 7 日は 7 目の運針(稽古した日は糸、しない日は針穴)、復習で卒業は褐のマスを数だけ、読解の正解は糸の長さ
 */
function homeCloth({ weekDays, graduated, readingOk, readingTotal, nextReview }) {
  const set = new Set(S.days);
  const week = [6, 5, 4, 3, 2, 1, 0].map((k) => set.has(addDays(today(), -k)));
  const cells = Math.min(graduated, 24);
  return `<div class="home-cloth">
    <div class="cloth-row"><span class="cloth-label">この 7 日</span>
      <div class="unshin" style="--steps: 7" role="img" aria-label="この 7 日のうち ${weekDays} 日稽古した">${week.map((on) => `<i${on ? ` class="sewn"` : ""}></i>`).join("")}</div>
      <span class="cloth-num">${weekDays}<small>日</small></span></div>
    ${nextReview ? `<a class="cloth-row" href="#/review"><span class="cloth-label">次の復習</span><span></span><span class="cloth-num">${nextReview}</span></a>` : ""}
    <div class="cloth-row"><span class="cloth-label">復習で卒業</span>
      <span class="grad-cells" aria-hidden="true">${graduated ? "<span class=\"dye dye-4\"></span>".repeat(cells) + (graduated > cells ? "<small>…</small>" : "") : "<small>まだ</small>"}</span>
      <span class="cloth-num">${graduated}<small>問</small></span></div>
    <a class="cloth-row" href="#/reading"><span class="cloth-label">読解の正解</span>
      <span class="cloth-thread" style="--p: ${readingTotal ? Math.round((readingOk / readingTotal) * 100) : 0}%" aria-hidden="true"></span>
      <span class="cloth-num">${readingOk}<small>/ ${readingTotal}</small></span></a>
  </div>`;
}
// 前回の続きのボタン: 「クイズの続きから」(括弧書きのラベルは硬いので、言葉でつなぐ)。PC 版 app.js と同じ
// 前回いた段が済んでいれば、段の並びで次のまだの段を返す。運針・「次は」と同じ表(stepStates)を見る
// (仕事のコード・サンプルを読み終えても「サンプルの続きから」を指し、今日の復習が主役から落ちていた。26 回目の審査)
function nextSectionFor(l, from) {
  const st = stepStates(l);
  const i = st.findIndex((s) => s.key === from);
  if (i < 0 || !st[i].done) return from;
  return st.slice(i + 1).find((s) => !s.done)?.key || from;
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

// ---- 顔(ホームとレッスンの冒頭)。PC版 app.js にも同じものがある
// 題の長さで見出しの大きさを 3 段に分ける(長い英単語が画面からはみ出さないように)。英数字は和字の約半分の幅で数える
function titleSize(title) {
  const width = [...title].reduce((w, c) => w + (c.charCodeAt(0) < 0x2000 ? 0.55 : 1), 0);
  return width <= 8 ? "size-xl" : width <= 14 ? "size-l" : "size-m";
}
// 縦書きの軸では数字を漢数字に(「第3部」→「第三部」)。横倒しの算用数字は読みにくいので
const KANJI_DIGIT = ["〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"];
const kanjiOrdinal = (text) => text.replace(/第(\d+)/, (_, n) => "第" + (n < 10 ? KANJI_DIGIT[n] : n < 20 ? "十" + (n % 10 ? KANJI_DIGIT[n % 10] : "") : n));
// レッスンの段ごとの済み具合(段の並びはレッスン画面と同じ)。curriculum.json の steps にある段だけ
function stepStates(l) {
  const keys = l.steps || ["read", "quiz"];
  const done = {
    read: !!S.read[l.id],
    atwork: seenSection(l.id, "atwork"),
    run: seenSection(l.id, "run"),
    quiz: !!(S.quiz[l.id] && quizPassed(l)),
    write: Array.from({ length: l.exercises || 0 }, (_, i) => S.exercise[`${l.id}/ex${i + 1}`]?.selfPass).every(Boolean),
    reading: Array.from({ length: l.reading || 0 }, (_, i) => S.reading[`${l.id}@${i}`]?.ok).every(Boolean),
    sts: (l.checklist || 0) > 0 && (S.checklist[l.id] || []).length >= l.checklist,
  };
  return keys.map((key) => ({ key, done: done[key] }));
}
// 運針(うんしん): 段を刺し子の針目で見せる。済んだ段は糸、いまの段は玉結び、まだの段は針穴
/** 今日はじめての表示か(ホームの運針を 1 回だけ縫う)。記録はこのブラウザの中だけ */
// スマホ版は 1 枚の画面でホームを何度も描くので、縫うのはこの表示で最初の 1 回だけ
let sewToday;
function firstOpenToday() {
  if (sewToday !== undefined) return false;
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
function phraseWrap(el) {
  if (!el || el.dataset.phrased || el.children.length || !("Segmenter" in Intl)) return; // 中に要素がある見出しは崩さない
  const words = [];
  for (const { segment, isWordLike } of new Intl.Segmenter("ja", { granularity: "word" }).segment(el.textContent)) {
    // @ は名前の頭なので、開き括弧と同じく次の語へつなぐ(「と、@ / Primary」と @ だけが行末に残った。26 回目の審査)
    if (segment === "@") { words.push(segment); continue; }
    // 助詞は 3 字まで前の語へ(「MockMvc / による」と行頭に残った)
    if (words.length && (!isWordLike || /^[\u3041-\u309f]{1,3}$/.test(segment) || /[(「『@]$/.test(words[words.length - 1]))) words[words.length - 1] += segment;
    else words.push(segment);
  }
  const latin = (c) => /[\x21-\x7e]/.test(c || "");
  el.innerHTML = words.map((w, i) => `<span class="phrase${i < words.length - 1 && latin(w.slice(-1)) !== latin(words[i + 1][0]) && !/\s$/.test(w) ? " gap" : ""}">${esc(w).replace(/ /g, "\u00a0").replace(/ー/g, '<span class="choon">ー</span>')}</span>`).join("");
  el.dataset.phrased = "1";
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
  // サンプルの見出しも語の途中・助詞の前で折らない(「@Bean / と、@Primary」と助詞で行が始まっていた)
  document.querySelectorAll(".today-title a, main h1:not(.today-title), .sample > h3").forEach(phraseWrap);
  fitTitles();
  document.fonts?.ready.then(fitTitles);
  updateHeader();
}

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

function updateHeader() {
  const days = streak();
  $("#streak").innerHTML = `連続 <b>${days}</b> 日`;
  $("#streak").classList.toggle("zero", days === 0);
  const net = $("#net");
  // 全部保存できたら、ヒーローの説明の代わりにヘッダーの小さな印で知らせる(ヒーローは次の一手だけにする)
  // 電波がなくても全部保存してあれば、警告ではなく「読める」と言う(朱は、保存が欠けていて読めないものがあるときだけ)
  // 電波があって保存し終えていない間は「保存中」(輪の印。失敗ではないので朱にしない)
  const saving = navigator.onLine && !savedAll && "caches" in window && "serviceWorker" in navigator;
  net.textContent = !navigator.onLine ? (savedAll ? "電波なし・保存済み" : "オフライン") : savedAll ? "保存済み" : saving ? "保存中" : "オンライン";
  net.title = savedAll ? "全レッスンと図を保存済み。電波なしで読めます" : saving ? "電波なしでも読めるように、レッスンと図を保存しています" : "";
  net.classList.toggle("saving", saving);
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
  // 今日の復習を終えた日は、ホームでも今日の稽古を言う(「なし」だけだと、14 問解いて 3 問仮縫いしたことが消える)
  const doneToday = todayReviewed();
  const tomorrowDue = Object.values(S.review).filter((it) => !it.graduated && it.due === addDays(today(), 1)).length;
  const bastedToday = Object.values(S.reviewLog?.[today()]?.retry?.results || {}).filter(Boolean).length;
  // 解き直しの途中で閉じた日は、残りの数も言う(「済み 12 問」だけだと、途中の解き直しに戻る入口が見えなかった。27 回目の審査)
  const retryNow = S.reviewLog?.[today()]?.retry;
  const retryLeft = retryNow?.refs && retryNow.i < retryNow.refs.length ? retryNow.refs.length - retryNow.i : 0;
  const standalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  // 前回の続き(最後まで読み切っていないレッスン)があれば、それを主役にする。新しいレッスンは控えめに添える
  const last = S.last && !S.last.end && all.find((l) => l.id === S.last.id);
  const hero = last || next;
  const section = last && SECTION_LABEL[S.last.section];
  // 前回いた段がもう済んでいたら、主ボタンは次のまだの段へ(「次:」の行と同じ段を指す)。前回の位置へは副リンクで戻れる
  const go = last ? nextSectionFor(hero, S.last.section) : null;
  const hint = nextDyeHint(hero, levels[hero.id]);
  // 期限の来た復習があって、次の段が演習(スマホでは自己申告)の日は、主ボタンを今日の復習に。
  // 通勤の数分でいちばん効くのは、忘れかけたところで思い出すこと。演習は副リンクで(PC で書くのが本番)
  const nextKey = last ? go : stepStates(hero).find((st) => !st.done)?.key;
  const reviewFirst = due > 0 && nextKey === "write";
  // 見出しの行は、主ボタンが指す次の一手の量を言う(読み終えたレッスンで「読む 18 分」と言わない)
  const kicker = reviewFirst ? "復習 → 演習"
    : { quiz: `クイズ ${hero.quiz} 問`, write: `演習 ${hero.exercises} 問`, reading: `読解 ${hero.reading} 問`,
        atwork: "仕事のコードから", run: "サンプルから", sts: "STSで試す" }[nextKey] || `読む ${hero.minutes} 分`;
  // ホームに戻ったとき、前に見たときより深まったマスだけ染み込ませる
  const seen = S.seenLevels || {};
  const deepened = new Set(all.filter((l) => seen[l.id] != null && levels[l.id] > seen[l.id]).map((l) => l.id));
  S.seenLevels = levels;
  save();
  view().innerHTML = `
    <section class="today" aria-labelledby="today-title">
      <p class="today-axis" aria-hidden="true"><span>${esc(kanjiOrdinal(hero.part.title))}</span><small>${esc(kanjiOrdinal(hero.chapter.title))}</small></p>
      <div class="today-body">
      <p class="today-kicker"><span>${last ? "前回の続き" : "今日の稽古"}</span><span class="visually-hidden">${esc(hero.part.title)} · ${esc(hero.chapter.title)}</span><span>${kicker}</span></p>
      <h1 class="today-title ${titleSize(hero.title)}" id="today-title"><a href="#/lesson/${esc(hero.id)}" data-resume="${last ? esc(S.last.section || "") : ""}">${esc(hero.title)}</a></h1>
      ${unshin(stepStates(hero), last ? go : stepStates(hero).find((st) => !st.done)?.key)}
      ${hint ? `<p class="today-next">${dyeSwatch(levels[hero.id])}<span><span class="tn-now">${dyeRuby(levels[hero.id])}(${DYE[levels[hero.id]].meaning})</span><span class="tn-hint">${hint}</span></span></p>` : ""}
      <div class="today-actions">${reviewFirst ? `<a class="btn primary btn-stack" href="#/review">今日の復習から<span class="btn-sub">${reviewMinutes()}</span></a>` : `<a class="btn primary" href="#/lesson/${esc(hero.id)}" data-resume="${last ? esc(go || "") : ""}">${!last ? (levels[hero.id] ? "続きから稽古する" : "稽古をはじめる") : go !== S.last.section ? `${SECTION_LABEL[go]}へ進む` : resumeLabel(section)}</a>`}</div>
      <div class="today-links">${reviewFirst ? `<a class="today-alt" href="#/lesson/${esc(hero.id)}" data-resume="write">演習へ進む(書くのは PC 版がおすすめ)</a>` : ""}${!reviewFirst && last && go !== S.last.section ? `<a class="today-alt" href="#/lesson/${esc(hero.id)}" data-resume="${esc(S.last.section)}">前回の位置(${section})へ戻る</a>` : ""}<a class="today-alt" href="#curriculum">レッスン一覧へ</a></div>
      </div>
      ${reviewFirst ? "" : `<a class="today-review" href="#/review"><span>今日の復習</span>
        <span>${due ? `<b>${due}</b>問<small class="review-min">${reviewMinutes()}</small> ${icon("arrow", "sm")}`
          : doneToday.n ? `<span class="zero">済み ${doneToday.n} 問${bastedToday ? `(仮縫い ${bastedToday})` : ""}${retryLeft ? ` · 解き直し 残り ${retryLeft} 問` : tomorrowDue ? ` · 明日 ${tomorrowDue} 問` : ""}</span>`
          : `<span class="zero">なし${upcoming ? ` · この先 ${upcoming} 問` : ""}</span>`}</span></a>`}
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
        <ul class="dye-legend">${DYE.map((d, i) => `<li>${dyeSwatch(i)}<span><span class="dye-name-sm">${dyeRuby(i)} <b class="dye-count">${counts[i]}</b></span><span class="dye-meaning">${d.meaning}</span></span></li>`).join("")}</ul>
        <p class="dye-dark-note">暗い表示では、色の明るさは段の順に並びません。段はマスの中の目の形(1 本・2 本・四角)で見分けます</p></details>
      ${homeCloth({ weekDays, graduated, readingOk, readingTotal, nextReview: due ? "" : nextReview })}
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
    }
  };
  homeSaveState(all);
}

let pendingSection = null;
// 「読み直す」などの data-resume は、どの画面から押してもレッスンのその段(手を止めては問い)へ飛ぶ
// 復習・読解・用語集からレッスンを開くのは「のぞき見」。前回の続き(ホームの主役)を書き換えず、戻り先を段の帯に出す
// (「読み直す」で古いレッスンへ行くと、ホームの主役がそのレッスンに変わり、いま習っている章へ戻れなかった。25 回目の審査)
let pendingPeek = null;
// 読解・用語集からのぞき見したときの元の位置(戻ったら一覧の先頭からではなく、押したカード・語の所へ。26 回目の審査)
let peekReturn = null;
let returning = null;
let linkNav = false; // 次の hashchange がリンクを押したものか(端末の「戻る」と見分ける)
addEventListener("resize", () => fitAxis(document.querySelector(".lesson-axis")));
const PEEK_BACK = { review: ["#/review", "復習へ戻る"], reading: ["#/reading", "読解へ戻る"], terms: ["#/terms", "用語集へ戻る"] };
document.addEventListener("click", (ev) => {
  const a = ev.target.closest?.("[data-resume]");
  if (a && a.dataset.resume) pendingSection = a.dataset.resume;
  const link = ev.target.closest?.("a[href^='#/']");
  if (link) pendingPeek = PEEK_BACK[link.dataset.peek] ? link.dataset.peek : null;
  if (link && (link.dataset.peek === "reading" || link.dataset.peek === "terms")) {
    const card = link.closest(".reading-q, li.term");
    // 次のカードも控える(着いたレッスンでその問いに答えると「まだ解いていない」の一覧から外れ、戻ると先頭だった。27 回目の審査)
    const next = [];
    for (let c = card?.nextElementSibling; c && next.length < 8; c = c.nextElementSibling) if (c.id) next.push(c.id);
    peekReturn = { tab: link.dataset.peek, at: card?.id || null, next, y: scrollY, q: document.getElementById("q")?.value || "" };
  }
  returning = link?.classList.contains("peek-back") ? peekReturn : null;
  if (link) linkNav = true;
}, true);
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
    // 保存の途中(初めて開いた直後・図だけ残っている)はヘッダーの「保存中」に任せ、ヒーローには出さない(読み上げ用に残す)。
    // ヒーローに文で出すのは、Service Worker が動いているのにレッスンが欠けているときだけ(18 回目の審査)
    const partial = !ready && !!navigator.serviceWorker.controller;
    el.className = "save-state " + (partial ? "ng" : ready ? "ok" : "");
    savedAll = ready && figures;
    S.allSaved = savedAll;
    save();
    el.classList.toggle("visually-hidden", !partial);
    updateHeader();
    el.textContent = ready && figures ? `保存済み(全 ${all.length} レッスンと図)。電波なしで読めます`
      : ready ? (navigator.onLine ? `保存済み(全 ${all.length} レッスン)。図を保存しています` : `保存済み(全 ${all.length} レッスン)。図は次に電波のある所で開くと保存`)
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
  const peek = pendingPeek;
  pendingPeek = null;
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
      <p class="lesson-axis" aria-hidden="true"><span>${esc(kanjiOrdinal(L.part))}</span><small>${esc(kanjiOrdinal(L.chapter))}</small></p>
      <div class="lesson-hero-body">
      <nav class="breadcrumb" aria-label="現在地"><a href="#/">ホーム</a><span class="visually-hidden"><span class="crumb-part"> / ${esc(L.part)}</span> / ${esc(L.chapter)}</span></nav>
      <h1 class="${titleSize(L.title)}">${esc(L.title)}</h1>
      <p class="summary">${esc(L.summary).replace(/`([^`]+)`/g, "<code>$1</code>").replace(/([A-Za-z0-9_.)\]」』）]|<\/code>)( ?)(?=[ぁ-ゖ])/g, (_, head, space) => head + (space ? "&nbsp;" : "\u2060"))}</p>
      <p class="meta"><span class="lesson-dye">${dyeSwatch(level, "lesson-swatch")} <span class="dye-name">${DYE[level].name}(${DYE[level].meaning})</span></span>
        <span>読む ${L.minutes} 分</span>${quiz ? `<span>クイズ最高 ${quiz.best}/${L.quiz.length}</span>` : ""}</p>
      </div>
    </header>
    <div class="stepper-wrap">
      <div class="stepper-row">
      <nav class="stepper" aria-label="このレッスンの段">
        ${steps.map(([key, label, done, pcOnly, part]) => `<a href="#${key}" data-scroll="${key}" data-step="${key}" class="step ${done ? "done" : ""}">${label}${pcOnly ? ` <span class="pc-only" title="パソコンの STS で手を動かす段">PC</span>` : ""}${!done && part ? ` <small>${part}/${total[key]}</small>` : ""}${done ? `<span class="visually-hidden">(済み)</span>` : ""}</a>`).join("")}
      </nav>
      <details class="step-menu"><summary><span class="visually-hidden">段の一覧。いまの段は</span><span id="step-count">${steps.length}段中1</span><span id="step-now">${steps[0][1]}</span></summary>
        <ol>${steps.map(([key, label, done, pcOnly, part]) => `<li class="${done ? "is-done" : ""}" data-menu-step="${key}"><a href="#${key}" data-scroll="${key}">${label}${!done && part ? ` <small>${part}/${total[key]}</small>` : ""}${pcOnly ? ` <span class="pc-only">PC</span>` : ""}${done ? `<span class="visually-hidden">(済み)</span>` : ""}</a></li>`).join("")}</ol></details>
      ${peek ? `<a class="peek-back" href="${PEEK_BACK[peek][0]}">${PEEK_BACK[peek][1]}</a>` : ""}
      <button class="ask" type="button" data-action="tutor" aria-label="AIチューターに質問する">${icon("chat")}<span>質問</span></button>
      </div>
      <div class="sew" aria-hidden="true"></div>
    </div>
    ${L.goals.length ? `<details class="goals"${S.seen?.[id] ? "" : " open"}><summary class="goals-title">このレッスンのゴール(${L.goals.length})</summary><ul>${L.goals.map((g) => `<li>${esc(g).replace(/`([^`]+)`/g, "<code>$1</code>")}</li>`).join("")}</ul></details>` : ""}
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
      <p class="run-note">まず出力を<b>予想</b>してから、「実行結果を見る」で答え合わせ。予想と違った所が、いちばん身につく所です。書き換えて試すのは PC版か Codespaces 版で。</p>
      ${L.samples.map((s) => `<div class="sample"><h3>${esc(s.title)}</h3><div class="md">${s.result ? descriptionWithoutPredict(s.descriptionHtml) : s.descriptionHtml}</div>
        <pre class="code-lines"><code class="language-java">${esc(s.code)}</code></pre>${sampleResult(s.result, s.descriptionHtml)}</div>`).join("")}
    </section>` : ""}
    <section id="quiz" class="lesson-section" aria-labelledby="quiz-title">
      ${title("quiz", "クイズ")}
      <form class="quiz" data-action="quiz">
        <ol>${L.quiz.map((q, i) => `<li class="quiz-q"><div class="md" id="qq-${i}">${q.questionHtml}</div>
          <div class="quiz-choices" role="radiogroup" aria-labelledby="qq-${i}">${choiceOrder(q.choicesHtml.length, `${L.id}#${i}|${S.quiz[L.id]?.n || 0}`).map((j) => `<label class="choice"><input type="radio" name="q${i}" value="${j}"><span>${q.choicesHtml[j]}</span></label>`).join("")}</div></li>`).join("")}</ol>
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
  trackLesson(id, steps.map(([key]) => key), !!peek);
  fitAxis($(".lesson-axis"));
  watchAxis($(".lesson-axis"));
}

const seenSection = (id, key) => !!S.sections?.[id]?.includes(key);

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
}

/** 読んでいる段を段の並びに示し、縫い線を進め、「前回の続き」を覚える */
let lessonTracker = null;
/**
 * レッスンの冒頭の縦書きの軸に、部と章が入りきるようにする。入らなければ章を軸から外して現在地の行に出し、
 * まだ入らなければ字間を詰め、それでも入らなければ冒頭の高さを軸の字の長さまで伸ばす
 * (軸の高さは本文に合わせているので、本文の短いレッスンでは字が 2 列目へ折れ、「礎」だけが左に残った。26 回目の審査)
 */
/**
 * 冒頭の高さが変わるたびに軸を合わせ直す。描いた直後は書体の読み込みやチップの組み直しで冒頭がまだ縮むので、
 * その時点で入っていても、縮んだあとに 2 列目へ折れていた(27 回目の撮影で 55 本中 2 本)
 */
let axisWatch = null;
function watchAxis(ax) {
  axisWatch?.disconnect();
  const hero = ax?.closest(".lesson-hero");
  if (!hero || !window.ResizeObserver) return;
  let last = "";
  axisWatch = new ResizeObserver(([e]) => {
    const size = `${Math.round(e.contentRect.width)}x${Math.round(e.contentRect.height)}`;
    if (size === last) return;
    last = size;
    // 合わせ直すと冒頭の高さがまた変わるので、次の描画で(同じ描画の中で観測が回り続けないように)
    requestAnimationFrame(() => fitAxis(ax));
  });
  axisWatch.observe(hero);
}

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

function trackLesson(id, keys, peek = false) {
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
  // のぞき見で別のレッスンを開いたときは、前回の続きを書き換えない(下の段と位置の記録も、S.last.id が違うので書かない)
  if (!peek || S.last?.id === id) S.last = { id, section: S.last?.id === id ? S.last.section : "read", off: S.last?.id === id ? S.last.off || 0 : 0 };
  S.seen = Object.assign(S.seen || {}, { [id]: true });
  save();
  let saveTimer = null;
  let current = null;
  let pos = null;
  // 位置は「段 + 段の先頭からの距離」で覚える(図の描画で高さが変わっても、同じ所へ戻れるように)
  // のぞき見のあいだは、同じレッスンでも前回の段と位置を書き換えない(演習の途中で同じレッスンの問いを読み直すと、
  // 前回の位置がクイズに替わり、ホームの「前回の位置へ戻る」が演習を指さなくなった。27 回目の審査)
  const flush = () => {
    clearTimeout(saveTimer);
    if (peek || !pos || S.last?.id !== id) return;
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
    if (!peek && S.last?.id === id && S.last.section !== key) { S.last.section = key; save(); }
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
  let skipClose = false; // 一覧を開くために段の帯を送ったときのスクロールでは閉じない
  // 閉じたら、一覧の中にあったフォーカスを段のボタンへ戻す(キーボードで迷子にならないように)
  menu?.addEventListener("toggle", () => {
    if (!menu.open && menu.querySelector("ol")?.contains(document.activeElement)) menu.querySelector("summary").focus({ preventScroll: true });
    // 開いた位置から下のメニューの上端までに収め、入りきらない分は一覧の中でスクロールする
    // (冒頭で開くと一覧が下のメニューの裏へ伸び、6・7 段目を押せなかった。14 回目の指摘)
    // 残りが 3 段分に満たないときは、段の帯を上に貼り付く位置まで送ってから測る。
    // 高さは「段の行 × n + 半行」にして、続きがあることを半分見えている次の段で示す
    if (menu.open) {
      const nav = $(".bottom-nav")?.getBoundingClientRect().height || 0;
      const ol = menu.querySelector("ol");
      ol.style.maxHeight = "";
      const row = ol.querySelector("li")?.getBoundingClientRect().height || 44;
      const room = () => innerHeight - menu.querySelector("summary").getBoundingClientRect().bottom - nav - 8;
      if (room() < row * 3) {
        skipClose = true;
        menu.closest(".stepper-wrap").scrollIntoView({ block: "start", behavior: "instant" });
      }
      const lis = ol.querySelectorAll("li");
      const pad = lis[0]?.offsetTop || 0; // 一覧の上の余白(ol が基準の箱)
      const step = lis[1] ? lis[1].offsetTop - lis[0].offsetTop : row;
      const r = room();
      ol.style.maxHeight = ol.scrollHeight <= r ? "" : `${pad + Math.max(1, Math.floor((r - pad) / step - 0.5) + 0.5) * step}px`;
    }
  });
  const onScroll = () => {
    if (skipClose) skipClose = false;
    else if (menu?.open) menu.open = false;
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => { ticking = false; if (document.contains(stepper)) update(); });
  };
  addEventListener("scroll", onScroll, { passive: true });
  lessonTracker = { disconnect: () => { removeEventListener("scroll", onScroll); flush(); } };
  update();
  if (pendingSection?.startsWith("term:")) {
    landTerm(pendingSection.slice(5));
    pendingSection = "";
  }
  if (pendingSection) {
    const target = document.getElementById(pendingSection);
    requestAnimationFrame(() => {
      const sec = resume && document.getElementById(resume.section);
      if (sec && resume.off > 0) scrollTo({ top: sec.getBoundingClientRect().top + scrollY - 140 + resume.off, behavior: "auto" });
      else if (pendingSection !== "read") target?.scrollIntoView();
      // 復習の「読み直す」で問いそのものへ来たときは、その問いにフォーカスを置き、1.2 秒だけ藍の枠で示す
      const spot = /^(qq|check|rq)-/.test(target?.id || "") && (target.closest("li.quiz-q, aside.check, li.reading-q") || target);
      if (spot) {
        spot.tabIndex = -1;
        spot.focus({ preventScroll: true });
        spot.classList.add("spot-flash");
        setTimeout(() => spot.classList.remove("spot-flash"), 1200);
      }
    });
  }
  pendingSection = null;
}

/**
 * 出力の各行を語の切れ目で折れるようにする(<wbr> を入れ、CSS の keep-all で語の途中では折らない)。
 * 句読点・かっこ・助詞は前の語に付け、記号の連なり(---)はひとまとまりにする。見出しの phraseWrap と同じ分け方
 */
function phraseLines(text) {
  if (!("Segmenter" in Intl)) return text.split("\n").map((line) => `<span class="out-line">${esc(line)}</span>`).join("");
  const seg = new Intl.Segmenter("ja", { granularity: "word" });
  return text.split("\n").map((line) => {
    const words = [];
    for (const { segment, isWordLike } of seg.segment(line)) {
      const prev = words[words.length - 1];
      if (prev !== undefined && (!isWordLike || /^[ぁ-ゟ]{1,2}$/.test(segment) || /[(「『\[(]$/.test(prev))) words[words.length - 1] += segment;
      else words.push(segment);
    }
    // 1 行ずつ包み、折り返した続きを字下げする(本物の改行と見分けられるように)
    return `<span class="out-line">${words.map((w) => esc(w).replace(/[-─=_*]{2,}/g, (m) => (m.length <= 12 ? `<span class="nb">${m}</span>` : m))).join("<wbr>")}</span>`;
  }).join("");
}

/** サンプルの説明の中の問い(「〜だろうか。」「〜?」)。開く所で問いを繰り返す(説明はコードの 40 行上にあることがある) */
function predictLine(html) {
  const d = document.createElement("div");
  d.innerHTML = html || "";
  // コードの中の ?(`? extends Number`)は文の終わりではない(「予想: extends Number、…」と崩れていた)
  d.querySelectorAll("code").forEach((c) => { c.textContent = c.textContent.replace(/[?？]/g, "\uE000"); });
  const text = d.textContent.replace(/\s+/g, " ").trim();
  // 最後の問いを取る(説明の最後の文なら説明から外せる。問いが 2 つある説明で、最初の問いを出すと 2 つ目と重なって外せなかった)
  const q = text.split(/(?<=[。?？])/).map((x) => x.trim()).filter((x) => /([?？]|だろうか。?|でしょうか。?)$/.test(x)).pop();
  return q ? q.replace(/。$/, "").replace(/\uE000/g, "?") : "";
}

/** 予想の問いの文を、説明の中でコードにした語はコードの書体のまま出す(「予想: 2 回目の getBean は…」と地の文に落ちていた) */
function predictHtml(q, descriptionHtml) {
  const d = document.createElement("div");
  d.innerHTML = descriptionHtml || "";
  // 2 字以上のコードを、長いものから、語の途中でない所だけ置き換える(1 字の `s` が地の文の s まで拾わないように)
  const codes = [...new Set([...d.querySelectorAll("code")].map((c) => c.textContent.trim()).filter((t) => t.length >= 2))].sort((a, b) => b.length - a.length);
  let out = esc(q);
  codes.forEach((c, i) => {
    const pat = esc(c).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    out = out.replace(new RegExp(`(?<![A-Za-z0-9_])${pat}(?![A-Za-z0-9_])`, "g"), `\u0000${i}\u0000`);
  });
  return out.replace(/\u0000(\d+)\u0000/g, (_, i) => `<code>${esc(codes[Number(i)])}</code>`);
}

/**
 * 予想の問いが説明の最後の文なら、説明からはその文を外す(コードの下の「予想: 〜」と同じ文が 2 回続かないように)。
 * 最後の段落の終わりに問いがあるときだけ、その範囲を Range で消す(中に <code> があってもよい)
 */
function descriptionWithoutPredict(html) {
  const q = predictLine(html);
  if (!q) return html;
  const d = document.createElement("div");
  d.innerHTML = html;
  const p = [...d.querySelectorAll("p")].pop();
  if (!p) return html;
  // 空白をそろえた文字の位置と、もとの文字の位置を対応させる
  const nodes = [];
  const walker = document.createTreeWalker(p, NodeFilter.SHOW_TEXT);
  for (let n = walker.nextNode(); n; n = walker.nextNode()) nodes.push(n);
  const map = [];
  let norm = "";
  let prevSpace = true;
  nodes.forEach((n) => {
    for (let i = 0; i < n.data.length; i++) {
      const space = /\s/.test(n.data[i]);
      if (space && prevSpace) continue;
      norm += space ? " " : n.data[i];
      map.push([n, i]);
      prevSpace = space;
    }
  });
  const trimmed = norm.replace(/\s+$/, "");
  const at = trimmed.lastIndexOf(q);
  if (at < 0 || !/^[。?？]?$/.test(trimmed.slice(at + q.length))) return html;
  if (!trimmed.slice(0, at).trim()) return html; // 段落が問いだけなら残す(説明が消えてしまうので)
  const range = document.createRange();
  range.setStart(map[at][0], map[at][1]);
  range.setEndAfter(p.lastChild);
  range.deleteContents();
  return d.innerHTML;
}

/** 実行時例外の文。1 行目(例外の名前と理由)だけ朱にする(PC の fragments/run.html と同じ。::first-line は折り返しの途中で切れた) */
function exceptionPre(text) {
  const nl = text.indexOf("\n");
  const head = nl < 0 ? text : text.slice(0, nl);
  const rest = nl < 0 ? "" : text.slice(nl);
  return `<pre class="exception"><span class="ex-head">${esc(head)}</span>${esc(rest)}</pre>`;
}

/** サンプルの実行結果(PC で実行した控え)。予想してから開くように、最初は閉じておく */
function sampleResult(r, descriptionHtml) {
  if (!r) return "";
  const q = predictLine(descriptionHtml);
  const failed = r.status !== "OK";
  const body = `${r.output ? `<pre class="console">${phraseLines(r.output)}</pre>` : ""}
    ${r.values?.length ? `<div class="values">${r.values.map((v) => `<div class="value-line"><span class="arrow" aria-hidden="true">⇒</span> <code>${esc(v)}</code></div>`).join("")}</div>` : ""}
    ${r.exception ? exceptionPre(r.exception) : ""}
    ${!r.output && !r.values?.length && !r.exception ? `<p class="muted small">(出力はありません)</p>` : ""}
    ${r.varies ? `<p class="muted small">スレッドの順番や、@ の後ろの番号(ハッシュコード)などは、実行のたびに変わることがあります。</p>` : ""}`;
  return `<details class="sample-result"><summary><span>${q ? `<span class="predict">予想: ${predictHtml(q, descriptionHtml)}</span>` : ""}<span>予想したら、実行結果を見る</span></span></summary>
    <div class="run-result ${failed ? "status-exception" : "status-ok"}"><div class="run-meta"><span class="run-status">${failed ? "× 実行時例外で止まる" : "実行結果"}</span></div>${body}</div></details>`;
}

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
function readingCard(r, ref, from, order) {
  const done = S.reading[ref];
  const lv = Math.max(1, Math.min(3, r.level || 1));
  return `<li class="reading-q" id="rq-${esc(ref.replace("@", "-"))}" data-ref="${esc(ref)}">
    <div class="reading-head"><h3>${esc(r.title)}</h3>
      <span class="level" aria-label="難しさ ${lv} / 3">${[1, 2, 3].map((n) => `<i class="${n <= lv ? "on" : ""}"></i>`).join("")}<span>${["やさしい", "ふつう", "むずかしい"][lv - 1]}</span></span></div>
    ${from ? `<p class="reading-from muted">${from}</p>` : ""}
    <pre class="code-lines"><code class="language-java">${esc(r.code)}</code></pre>
    <form data-action="reading">
      <fieldset style="border:0;padding:0;margin:0"><legend class="reading-question">${r.questionHtml}</legend>
      ${(order || choiceOrder(r.choicesHtml.length, ref)).map((j) => `<label class="choice"><input type="radio" name="c" value="${j}" required><span>${r.choicesHtml[j]}</span></label>`).join("")}</fieldset>
      <button class="btn primary arm-submit" type="submit" disabled>選んでから答え合わせ</button>
      ${done ? `<span class="small muted reading-last">前回: ${done.ok ? "正解" : "不正解"}</span>` : ""}
    </form>
    <div class="reading-answer" hidden aria-live="polite"></div>
  </li>`;
}

function readingAnswerHtml(r, ok, choice) {
  return `<div class="verdict ${ok ? "correct" : "wrong"}">${icon(ok ? "check" : "cross")}${ok ? "正解" : "不正解"}</div>
    ${!ok && r.whyHtml?.[choice] ? `<p class="check-why">${r.whyHtml[choice]}</p>` : ""}<div class="md">${r.explanationHtml}</div>
    ${r.traceHtml?.length ? `<p class="trace-title">1 行ずつの追跡</p><ol class="trace">${r.traceHtml.map((t) => `<li>${t}</li>`).join("")}</ol>` : ""}`;
}

/** 読解フォームの答え合わせ。記録して復習キューに入れる */
// 答え合わせのあとは回答を固定し、正解・あなたの答えを文字でも付ける(クイズと同じ規則)。「もう一度解く」で解き直せる
/** 選択肢に正解とあなたの答えを付ける(色だけでなく文字でも)。並びを入れ替えた復習でも合うよう、位置ではなく値で見る */
function markChoices(form, answer, choice) {
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
}

/** 開いた畳みの記憶(レッスン id!問いの番号)。スマホは学習の記録と一緒に保存する */
const gatePage = () => (location.hash.match(/#\/lesson\/([^?/]+)/) || [])[1] || "";
const gateOpened = (key) => !!S.gates?.[key];
function rememberGate(key) { S.gates = S.gates || {}; S.gates[key] = 1; save(); }

/**
 * 手を止めて: 押したら正解とあなたの答えを付け、選んだ誤答に向けた 1 文と理由を出す。記録は onAnswer に任せる。
 * 正解なら枠の上の針目が糸になる(縫う)。フォーカスは理由へ移す(押したボタンは止めるので、読み上げが迷子にならないように)
 */
function bindChecks(root, onAnswer) {
  root.querySelectorAll("aside.check").forEach((box) => {
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
        ex.insertAdjacentHTML("afterbegin", `<span class="q-mark">${icon(ok ? "check" : "cross", "sm")} ${ok ? "正解" : "不正解"}</span>`);
        ex.hidden = false;
        box.classList.add(ok ? "sewn" : "missed");
        // 答えの前に置いた問いは、本文の続きが種明かしをするので、ここでは理由を繰り返さず続きへ送る
        if (gate) {
          ex.querySelector(".check-ex-body")?.setAttribute("hidden", "");
          ex.insertAdjacentHTML("beforeend", `<p class="check-gate-after">続きの本文で、答えを確かめよう</p>`);
        }
        openGate(false);
        ex.focus({ preventScroll: true });
        onAnswer(Number(box.dataset.check), ok);
      };
    });
  });
}

function lockAnswer(form, answer, choice, onReset) {
  markChoices(form, answer, choice);
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
    if (submit.classList.contains("arm-submit")) { submit.disabled = true; submit.textContent = "選んでから答え合わせ"; }
    again.hidden = true;
    onReset();
  };
}

function bindReading(root, findQuestion) {
  root.querySelectorAll("form[data-action=reading]").forEach((form) => {
    // 復習の帯と同じく、選ぶまでは押せない「選んでから答え合わせ」、選んだら藍の「答え合わせ」(枠だけの小さなボタンで、
    // 選んでも変わらず、押し忘れて次のカードへ送れた。25 回目の審査)
    form.addEventListener("change", () => {
      const b = form.querySelector(".arm-submit");
      if (b && b.disabled) { b.disabled = false; b.textContent = "答え合わせ"; }
    });
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
      out.innerHTML = readingAnswerHtml(r, ok, choice);
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
    ${withLesson && t.lesson ? `<a class="term-from" href="#/lesson/${esc(t.lesson)}" data-peek="terms">${esc(lessonTitle || t.lesson)}</a>` : ""}</li>`;
}

/** 用語集の 1 枚。同じ語がいくつかのレッスンで出るときは、レッスンごとの説明を 1 枚の中に並べる */
function termCard(g, titles) {
  const t = g[0];
  // 英語が見出しと同じ綴りなら繰り返さない(「final(クラス・メソッド)」の括弧の前とも比べる)
  const base = t.term.replace(/[((].*$/, "").trim().toLowerCase();
  const en = g.map((x) => x.en).find((e) => e && e.toLowerCase() !== t.term.toLowerCase() && e.toLowerCase() !== base);
  const tid = `term-${[...base].map((c) => c.codePointAt(0).toString(36)).join("")}`;
  const head = `<p class="term-name">${esc(t.term)}${t.read ? `<small>${esc(t.read)}</small>` : ""}${en ? `<small lang="en">${esc(en)}</small>` : ""}${g.length > 1 ? ` <span class="term-count">${g.length} レッスン</span>` : ""}</p>`;
  // どの語も「〇〇 で」(レッスン名)を説明の前に置く
  if (g.length === 1) return `<li class="term" id="${tid}">${head}${t.lesson ? `<p class="term-from-line"><a class="term-from term-from-head" href="#/lesson/${esc(t.lesson)}" data-peek="terms" data-resume="term:${esc(t.term)}">${esc(titles[t.lesson] || t.lesson)}</a><span class="term-de">で</span></p>` : ""}<p class="term-def">${t.defHtml}</p></li>`;
  return `<li class="term" id="${tid}">${head}<ul class="term-defs">${g.map((x) => `<li>${x.lesson ? `<p class="term-from-line"><a class="term-from term-from-head" href="#/lesson/${esc(x.lesson)}" data-peek="terms" data-resume="term:${esc(x.term)}">${esc(titles[x.lesson] || x.lesson)}</a><span class="term-de">で</span></p>` : ""}
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
  // 手を止めて: 答えた問いは復習(1・3・7・14 日)にも入る
  bindChecks(root, (i, ok) => { practice(`${L.id}!${i}`, "check", L.id, ok); recordDay(); });
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
      form.querySelectorAll(".quiz-choices").forEach((box, i) => {
        const byValue = new Map([...box.querySelectorAll("label.choice")].map((l) => [Number(l.querySelector("input").value), l]));
        choiceOrder(byValue.size, `${L.id}#${i}|${S.quiz[L.id]?.n || 0}`).forEach((j) => box.appendChild(byValue.get(j)));
      });
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
      li.querySelectorAll("label.choice").forEach((label) => {
        const j = Number(label.querySelector("input").value); // 並びを入れ替えているので、位置ではなく値で見る
        label.classList.toggle("is-answer", j === q.answer);
        label.classList.toggle("is-wrong", j === chosen && !ok);
        // 色だけに頼らない: 選んだ誤答には「あなたの答え」、正解には「正解」と文字で出す
        label.querySelector(".choice-tag")?.remove();
        if (j === chosen && !ok) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag">× あなたの答え</span>`);
        if (j === q.answer) label.querySelector("span").insertAdjacentHTML("beforeend", `<span class="choice-tag is-ok">✓ 正解</span>`);
      });
      li.insertAdjacentHTML("beforeend", `<div class="q-explain ${ok ? "correct" : "wrong"}"><span class="q-mark">${icon(ok ? "check" : "cross", "sm")} ${ok ? "正解" : chosen < 0 ? "未回答" : "不正解"}</span>
        ${!ok && q.whyHtml?.[chosen] ? `<p class="check-why">${q.whyHtml[chosen]}</p>` : ""}<div class="explanation md">${q.explanationHtml}</div></div>`);
    });
    decorate(form);
    form.querySelectorAll("input[type=radio]").forEach((x) => { x.disabled = true; });
    const submit = form.querySelector("button[type=submit]");
    submit.hidden = true;
    // 最高点(縹)に数えるのは、その日の最初の採点だけ。答えを見た直後の解き直しは練習(覚えているだけのことがある)
    const prev = S.quiz[L.id];
    const counted = prev?.day !== today();
    S.quiz[L.id] = { best: counted ? Math.max(score, prev?.best ?? 0) : (prev?.best ?? 0), last: score, day: today(), n: (prev?.n || 0) + 1 };
    recordDay();
    const dyed = refreshDye(meta);
    const pass = Math.ceil(L.quiz.length * 0.8);
    if (S.quiz[L.id].best >= pass) markStep("quiz");
    const wrong = L.quiz.map((q, i) => i).filter((i) => !form.querySelectorAll(".quiz-q")[i].querySelector(".q-explain.correct"));
    form.querySelectorAll(".quiz-q").forEach((li, i) => { li.id = `q-${i + 1}`; });
    // 次の一手: 合格なら段の並びの次(演習 → 読解)、届かなければ間違えた問の見直しと解き直し
    const nextKey = score >= pass ? (L.exercises.length ? "write" : L.reading?.length ? "reading" : null) : null;
    const level = meta ? dyeLevel(meta) : 0;
    $("#quiz-result").innerHTML = `<div class="quiz-result"><div class="quiz-score"><span class="big">${score}<span class="slash">/</span>${L.quiz.length}</span>${scoreStitches(L.quiz.map((_, i) => !wrong.includes(i)))}
      <span>${score === L.quiz.length ? "満点" : score >= pass ? `合格(${pass} 問以上)` : `あと ${pass - score} 問で合格(${pass} 問以上)`}</span></div>${dyedLine(dyed)}
      ${score >= pass && S.quiz[L.id].best < pass ? `<p class="muted small">同じ日の解き直しは練習として数えます。縹(クイズ合格)には、明日以降に解き直して ${pass} 問以上(答えを見た直後は、覚えているだけのことがあるため)。</p>` : ""}
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
    tmp.innerHTML = chunk.map((r) => readingCard(r, r.ref, `<a href="#/lesson/${esc(r.lesson)}" data-peek="reading" data-resume="rq-${esc(r.ref.replace("@", "-"))}">${esc(titles[r.lesson] || r.lesson)}</a>`)).join("");
    bindReading(tmp, (ref) => byRef[ref]);
    decorate(tmp);
    list.append(...tmp.children);
    count += chunk.length;
    more.hidden = count >= shown.length;
    more.textContent = `もっと見る(残り ${shown.length - count} 問)`;
  };
  if (list) { more.onclick = showMore; showMore(); }
  if (returning?.tab === "reading" && returning.at) {
    const ids = [returning.at, ...(returning.next || [])];
    returning = null;
    const find = () => ids.map((x) => document.getElementById(x)).find(Boolean);
    while (list && !find() && !more.hidden) showMore();
    const card = find();
    // 頭に合わせる(縦に長いカードを中央に合わせると、題と出どころが画面の上に切れた)
    if (card) requestAnimationFrame(() => {
      card.scrollIntoView({ block: "start" });
      card.classList.add("spot-flash");
      setTimeout(() => card.classList.remove("spot-flash"), 1200);
    });
  }
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
  if (returning?.tab === "terms") {
    const back = returning;
    returning = null;
    if (back.q) { $("#q").value = back.q; show(back.q); }
    const card = back.at && document.getElementById(back.at);
    requestAnimationFrame(() => {
      if (card) {
        card.scrollIntoView({ block: "center" });
        card.classList.add("spot-flash");
        setTimeout(() => card.classList.remove("spot-flash"), 1200);
      } else scrollTo({ top: back.y });
    });
  }
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

let reviewBreakAt = "";
// 解き直し: 今日の終わりに、間違えた問題をその日のうちにもう一度(答えは記録せず、予定は変えない)。
// 直後に正しい答えで思い出し直すと、翌日の復習まで間違えたまま寝かせるより定着する(演習は書き直しが重いので入れない)
// 状態は今日の記録(S.reviewLog[今日].retry)に保存する。通勤の途中で閉じて夜に開いても、仮縫いの跡と札が残るように
let retry = null; // { day, refs, i, ok, results: { ref: 正しく解けたか }, pass: 何回目の解き直しか }
const saveRetry = () => {
  const log = S.reviewLog = S.reviewLog || {};
  const t = today();
  log[t] = Object.assign({}, log[t], { retry: { refs: retry.refs, i: retry.i, ok: retry.ok, results: retry.results, pass: retry.pass || 1 } });
  save();
};
/**
 * 解き直しの終わりの 1 行。何回目の解き直しかによらず、今日の全部で言う(運針の仮縫いの目と札の数が合うように)。
 * 1 問も仮縫いできていなければ札を付けず「まだ」。括弧は使わない(「解け / た(本縫い…」と動詞の途中で折れた)
 */
const retryDoneLine = (r, missed) => {
  const basted = missed.filter((ref) => r.results?.[ref] === true).length;
  if (basted === 0) return `<p class="retry-done">解き直した ${r.refs.length} 問は、まだ。明日の復習で、もう一度</p>`;
  return `<p class="retry-done"><span class="retry-tag">仮縫い</span>今日間違えた ${missed.length} 問${basted === missed.length ? "を、全部" : `のうち ${basted} 問を、`}仮縫いできた。本縫いは明日の復習で</p>`;
};
async function renderReview() {
  const due = dueItems();
  const upcoming = Object.entries(S.review).filter(([, it]) => !it.graduated && it.due > today()).length;
  const tomorrow = Object.values(S.review).filter((it) => !it.graduated && it.due === addDays(today(), 1)).length;
  const done = todayReviewed();
  if (!retry || retry.day !== today()) {
    const saved = S.reviewLog?.[today()]?.retry;
    retry = saved ? { day: today(), ...saved, results: { ...saved.results } } : null;
    // 解き直す問が記録から消えていたら(教材の差し替えなど)、今日の解き直しは始めから(消えた問を開くと画面が空になった)
    if (retry && !retry.refs.every((r) => S.review[r])) retry = null;
  }
  const inRetry = !!retry && retry.i < retry.refs.length;
  // 今日の終わり: 区切りと同じ部品で、今日答えた全問を運針に並べ、間違えた問題と読み直す所を出す
  if (due.length === 0 && !inRetry) {
    const again = done.missed.filter((r) => S.review[r] && S.review[r].type !== "exercise");
    // 解き直してもまだの問題は、もう一度だけ回せる(仮縫いできた分の跡は残す)
    const still = retry ? retry.refs.filter((r) => retry.results[r] === false && S.review[r]) : [];
    view().innerHTML = `<h1 class="page-title">復習</h1>` + (done.n ? `
      <section class="review-break review-done" aria-labelledby="break-title">
      <p class="review-break-kicker">今日の復習はおしまい</p>
      ${stitchRows(done.results, done.entries.map(([r]) => retry?.results?.[r] === true))}
      <h2 class="review-break-title" id="break-title">今日 ${done.n} 問のうち ${done.ok} 問${done.ok === done.n ? "。全部縫えた" : ""}</h2>
      <p class="review-break-note">${done.n - done.ok ? `間違えた ${done.n - done.ok} 問は、明日もう一度出ます。` : ""}${tomorrow ? `明日の復習は ${tomorrow} 問。` : ""}${upcoming ? `この先 ${upcoming} 問が、1・3・7・14 日の間隔で出ます。` : ""}</p>
      ${await missedList(done.missed, 5)}
      ${retry ? retryDoneLine(retry, again) : ""}
      <div class="review-next">${!retry && again.length ? `<button class="btn primary" id="again">間違えた ${again.length} 問を、いま解き直す</button><a class="btn" href="#/">ホームへ</a>`
        : `<a class="btn primary" href="#/">ホームへ</a>${still.length ? `<button class="btn" id="again-still">まだの ${still.length} 問を、もう一度</button>` : ""}`}</div></section>` : `
      <section class="review-break review-done"><p class="review-break-note">今日の復習はありません。${upcoming ? `この先 ${upcoming} 問が、1・3・7・14 日の間隔で出ます。` : "レッスンでクイズや読解を解くと、ここに出てきます。"}</p>
      <div class="review-next"><a class="btn primary" href="#/">ホームへ</a></div></section>`);
    const go = $("#again");
    if (go) go.onclick = () => { retry = { day: today(), refs: again, i: 0, ok: 0, results: {} }; saveRetry(); scrollTo({ top: 0 }); renderReview(); };
    const goStill = $("#again-still");
    if (goStill) goStill.onclick = () => { retry = { day: today(), refs: still, i: 0, ok: 0, results: retry.results, pass: (retry.pass || 1) + 1 }; saveRetry(); scrollTo({ top: 0 }); renderReview(); };
    return;
  }
  // 10 問ごとに区切りを出す(残りが多い日でも、ここで止めてよいと分かるように)。いまの 10 問を運針で並べ、間違えた問題を出す
  if (!inRetry && done.n > 0 && done.n % 10 === 0 && reviewBreakAt !== `${today()}#${done.n}`) {
    const block = done.entries.slice(done.n - 10, done.n);
    const miss = block.filter(([, ok]) => !ok).map(([r]) => r);
    view().innerHTML = `<h1 class="page-title">復習</h1>
      <section class="review-break" aria-labelledby="break-title">
      <p class="review-break-kicker">今日の ${done.n - 9}〜${done.n} 問目</p>
      ${stitchRows(block.map(([, ok]) => ok))}
      <h2 class="review-break-title" id="break-title">10 問のうち ${10 - miss.length} 問${miss.length ? "" : "。全部縫えた"}</h2>
      <p class="review-break-note">${miss.length ? `間違えた ${miss.length} 問は、明日もう一度出ます。` : ""}ここで区切っても大丈夫。残り ${due.length} 問は、明日の分と一緒に出ます。</p>
      ${await missedList(miss)}
      <div class="review-next"><button class="btn primary" id="go-on">続ける(残り ${due.length} 問)</button><a class="btn" href="#/">今日はここまで</a></div></section>`;
    $("#go-on").onclick = () => { reviewBreakAt = `${today()}#${done.n}`; renderReview(); };
    return;
  }
  const [ref, item] = inRetry ? [retry.refs[retry.i], S.review[retry.refs[retry.i]]] : nextDue(due);
  const L = await lesson(item.lessonId);
  // 解き直しは、数分前に「✓ 正解」を見たのと別の並びにする(位置で当てないように。正解の位置も必ず変える)
  // 2 回目からの解き直しは、朝とそれまでの解き直しで見せたどの位置とも違う所に正解を置く(位置を覚えて当てないように)
  const order = (n, answer) => {
    const first = choiceOrder(n, ref + "|" + today());
    if (!inRetry) return first;
    const used = [first.indexOf(answer)];
    let pick = first;
    for (let pass = 1; pass <= (retry.pass || 1); pass++) {
      const tries = Array.from({ length: 12 }, (_, k) => choiceOrder(n, `${ref}|${today()}|retry${pass > 1 ? `${pass}-` : ""}${k + 1}`));
      // 12 の候補に使っていない位置が無いときは、並びを回して正解を使っていない位置へ(無ければ前の回と違う位置へ)
      const unused = Array.from({ length: n }, (_, i) => i).filter((i) => !used.includes(i));
      const target = unused.length ? unused[0] : (used.at(-1) + 1) % n;
      pick = tries.find((o) => !used.includes(o.indexOf(answer))) || (() => {
        const o = tries[0].slice();
        const shift = ((target - o.indexOf(answer)) % n + n) % n;
        return o.slice(n - shift).concat(o.slice(0, n - shift));
      })();
      used.push(pick.indexOf(answer));
    }
    return pick;
  };
  // 答える前は出どころのレッスン名を見せない(どの知識を使う問題かを、自分で見分けるところに混ぜる効き目がある)
  // 問いの画面の見出しは 1 行に詰める(毎問、上の 3 割を同じ前置きが占めていた)。説明はその日(解き直し)の最初の 1 問だけ
  const head = `<div class="review-top"><h1 class="page-title">復習</h1><p class="review-progress" id="review-progress">${inRetry ? `解き直し <b>${retry.i + 1}</b> / ${retry.refs.length}` : reviewProgress(due.length, done.n)}</p></div>
    ${inRetry ? (retry.i === 0 ? `<p class="muted small review-mix">間違えた問題を、並びを変えてもう一度。</p>` : "")
      : done.n === 0 ? `<p class="muted small review-mix">いろいろなレッスンの問題を混ぜて出します。選択肢の並びは日ごとに変わります。</p>` : ""}`;
  // 解き直しは段階の運針の代わりに、1 目だけの仮縫い(正しく解けたら破線の糸。本縫いは明日の復習)
  const stitchOf = (it) => inRetry ? `<div class="review-stitch retry-stitch"><div class="unshin baste" role="img" aria-label="仮縫い(記録はしない)" style="--steps: 1"><i></i></div>
    <p class="unshin-cap" aria-hidden="true"><span>仮縫い(記録はしない)</span><span>本縫いは明日の復習で</span></p></div>` : reviewStitch(it);
  // 答えたあと: レッスンのクイズと同じ判定の部品 → 出どころ → 次回と次へ
  // (解き直しは答えた時点で retry.i を進めてから描くので、残りがあるかは retry.i < 問題数。+1 すると最後から 2 問目で「おわりの画面へ」になった。27 回目の審査)
  // 判定 → 選んだ誤答への 1 文 → 刺し子の線 → 解説(レッスンのクイズ・手を止めて・読解と同じ形)
  const after = (it, ok, explanationHtml, whyHtml) => `<div class="q-explain ${ok ? "correct" : "wrong"}" tabindex="-1"><span class="q-mark">${icon(ok ? "check" : "cross", "sm")} ${ok ? "正解" : "不正解"}</span>
      ${whyHtml || ""}${explanationHtml ? `<div class="explanation md">${explanationHtml}</div>` : ""}</div>
    <a class="review-source" href="#/lesson/${esc(L.id)}" data-resume="${reviewSpot(it, ref)}" data-peek="review"><span>出どころ: <b>${esc(L.title)}</b></span><span class="review-source-go">読み直す</span></a>
    <div class="review-next"><p class="review-note">${inRetry ? "明日の復習で、もう一度" : nextLabel(it)}</p><button class="btn primary" id="next">${inRetry ? (retry.i < retry.refs.length ? "次の解き直しへ" : "おわりの画面へ") : "次の復習へ"}</button></div>`;
  // 「答え合わせ」は最初から下に出しておき、選ぶまでは押せない形に(選んだ瞬間に出すと、押した選択肢の上にかぶさった)
  const armSubmit = (form) => {
    const b = form.querySelector("button[type=submit]");
    const bar = document.createElement("div");
    bar.className = "submit-bar";
    b.before(bar);
    bar.append(b);
    b.disabled = true;
    b.textContent = "選んでから答え合わせ";
    form.addEventListener("change", (ev) => {
      b.disabled = false;
      b.textContent = "答え合わせ";
      // 選んだ選択肢が帯の下に入っていたら、帯の上まで送る(選んだ答えを目で確かめてから押せるように)
      const label = ev.target.closest("label");
      const gap = label ? label.getBoundingClientRect().bottom - (bar.getBoundingClientRect().top - 12) : 0;
      if (gap > 0) scrollBy({ top: gap, behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    });
  };
  const finish = (it, ok, out, explanationHtml, chosen, whyHtml) => {
    recordDay();
    // 解き直しも答えた時に記録する(「次の解き直しへ」を押したときだけだと、「読み直す」→「復習へ戻る」で答えが消え、
    // 同じ問いが同じ並びで出直して、見た位置を押すだけで仮縫いになった。26 回目の審査。PC・いつもの復習と同じ)
    if (inRetry) { retry.ok += ok ? 1 : 0; retry.results[ref] = ok; retry.i++; saveRetry(); }
    out.innerHTML = after(it, ok, explanationHtml, whyHtml);
    out.hidden = false;
    if (inRetry) {
      const b = view().querySelector(".retry-stitch");
      b?.querySelector(".unshin i")?.classList.toggle("sewn", ok);
      const cap = b?.querySelector(".unshin-cap");
      if (cap) cap.innerHTML = ok ? "<span><b>仮縫いした</b>。本縫いは明日の復習で</span>" : "<span>明日の復習で、もう一度</span>";
    } else {
      if (ok) sewStitch(it); else unsewStitch();
      // 上の数も答えた分だけ進める(間違えた問題は明日に回るので、今日の残りからは外れる)
      const now = todayReviewed();
      $("#review-progress").innerHTML = reviewProgress(dueItems().length, now.n, true);
    }
    decorate(out);
    $("#next").onclick = () => renderReview();
    // 判定へフォーカスを移し、選んだ答えが画面の上の方に来るまで送る(解説が長くても「× あなたの答え」を見失わない)
    const verdict = out.querySelector(".q-explain");
    verdict.focus({ preventScroll: true });
    const top = (chosen || verdict).getBoundingClientRect().top;
    if (top > 140) scrollTo({ top: scrollY + top - 120, behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };
  if (item.type === "quiz" || item.type === "check") {
    const q = item.type === "quiz" ? L.quiz[Number(ref.split("#")[1])] : (L.checks || [])[Number(ref.split("!")[1])];
    if (!q) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<div class="card review-quiz">${stitchOf(item)}${item.type === "check" ? `<p class="review-kind">手を止めて(本文の途中の問い)</p>` : ""}<div class="md">${q.questionHtml}</div>
      <form id="rq">${order(q.choicesHtml.length, q.answer).map((j) => `<label class="choice"><input type="radio" name="c" value="${j}" required><span>${q.choicesHtml[j]}</span></label>`).join("")}
      <button class="btn primary" type="submit">答え合わせ</button></form><div id="ra"></div></div>`;
    armSubmit($("#rq"));
    $("#rq").onsubmit = (ev) => {
      ev.preventDefault();
      const choice = Number(new FormData(ev.target).get("c"));
      const ok = choice === q.answer;
      const it = inRetry ? item : reviewAnswer(ref, ok, choice);
      markChoices(ev.target, q.answer, choice);
      ev.target.querySelector("button[type=submit]").hidden = true;
      const why = !ok && q.whyHtml?.[choice] ? `<p class="check-why">${q.whyHtml[choice]}</p>` : "";
      finish(it, ok, $("#ra"), q.explanationHtml, ev.target.querySelector("label.is-wrong") || ev.target.querySelector("label.is-answer"), why);
    };
  } else if (item.type === "reading") {
    const r = (L.reading || [])[Number(ref.split("@")[1])];
    if (!r) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<ol class="reading-list">${readingCard(r, ref, undefined, order(r.choicesHtml.length, r.answer))}</ol>`;
    // クイズ・手を止めてと同じ形に: 地の上に置き、運針と「読解」の札。題は答えるまで隠す(使う知識と罠を先に言ってしまうので)
    const card = view().querySelector(".reading-q");
    card.classList.add("review-reading");
    card.insertAdjacentHTML("afterbegin", stitchOf(item) + `<p class="review-kind">読解(コードを読む問い)</p>`);
    const form = view().querySelector("form[data-action=reading]");
    form.querySelector(".small.muted")?.remove(); // 「前回: 正解」は、いま答えた判定と食い違うので出さない
    form.querySelector("button[type=submit]").classList.add("primary");
    armSubmit(form);
    form.onsubmit = (ev) => {
      ev.preventDefault();
      const choice = Number(new FormData(form).get("c"));
      const ok = choice === r.answer;
      if (!inRetry) S.reading[ref] = { ok, at: today() };
      const it = inRetry ? item : reviewAnswer(ref, ok, choice);
      markChoices(form, r.answer, choice);
      form.querySelector("button[type=submit]").hidden = true;
      card.classList.add("answered");
      const out = view().querySelector(".reading-answer");
      // 判定 → 選んだ誤答への 1 文 → 解説 → 1 行ずつの追跡(読解のページと同じ順)
      const why = !ok && r.whyHtml?.[choice] ? `<p class="check-why">${r.whyHtml[choice]}</p>` : "";
      finish(it, ok, out, r.explanationHtml, form.querySelector("label.is-wrong") || form.querySelector("label.is-answer"), why);
      if (r.traceHtml?.length) out.querySelector(".q-explain").insertAdjacentHTML("afterend", `<p class="trace-title">1 行ずつの追跡</p><ol class="trace">${r.traceHtml.map((t) => `<li>${t}</li>`).join("")}</ol>`);
      decorate(out);
    };
  } else {
    const e = L.exercises.find((x) => x.key === ref);
    if (!e) { delete S.review[ref]; save(); return renderReview(); }
    view().innerHTML = head + `<p class="muted small">何も見ずに雛形から書いてみよう。書けたら模範解答と見比べて「できた/まだ」を選びます。</p>
      <article class="exercise card"><h3>${esc(e.title)}</h3><div class="problem md">${e.problemHtml}</div>
      <textarea class="code-input" spellcheck="false" autocapitalize="off" autocorrect="off" aria-label="コード">${esc(e.starter)}</textarea>
      <details class="solution"><summary>模範解答と見比べる</summary><pre><code class="language-java">${esc(e.solution)}</code></pre></details>
      <div class="self-check"><button class="btn" data-ok="1">${icon("check")}できた</button><button class="btn" data-ok="0">まだ</button></div>
      <div id="ra"></div></article>`;
    view().querySelector("article.exercise").insertAdjacentHTML("afterbegin", reviewStitch(item));
    addSymbolBar(view().querySelector("textarea"));
    view().querySelectorAll(".self-check .btn").forEach((b) => {
      b.onclick = () => {
        const ok = b.dataset.ok === "1";
        view().querySelectorAll(".self-check .btn").forEach((x) => { x.disabled = true; });
        finish(reviewAnswer(ref, ok), ok, $("#ra"), null);
      };
    });
  }
  decorate(view());
}

/**
 * 復習の段階を運針で: 1・3・7・14 日の 4 目。縫えた目は糸、今回の目に朱の玉結び、まだの目は針穴。
 * 「重ねるほど深くなる」をいちばん毎日開く画面でも見せる(18 回目の審査)
 */
function reviewStitch(item) {
  const s = Math.min(item.stage, INTERVALS.length - 1);
  return `<div class="review-stitch"><div class="unshin" role="img" aria-label="4 回のうち ${s + 1} 回目の復習" style="--steps: ${INTERVALS.length}">${INTERVALS.map((_, i) =>
    `<i class="${i < s ? "sewn" : i === s ? "now" : ""}"></i>`).join("")}</div>
    <p class="unshin-cap" aria-hidden="true"><span>${INTERVALS[s]} 日後の復習(4 回のうち <b>${s + 1} 回目</b>)</span><span>${s === INTERVALS.length - 1 ? "正解で卒業" : `卒業まで あと ${INTERVALS.length - s} 回`}</span></p></div>`;
}

/** 正解したら、今回の目を糸で縫う(卒業ならそう添える) */
function sewStitch(it) {
  const now = view().querySelector(".review-stitch .unshin i.now");
  if (!now) return;
  now.classList.remove("now");
  now.classList.add("sewn", "just");
  const cap = view().querySelector(".review-stitch .unshin-cap");
  // 卒業はホームの地図のいちばん深い色(褐)に染まる瞬間。同じ「染み込み」で褐のマスを添える
  if (cap) cap.innerHTML = it.graduated ? `<span class="grad-line"><span class="dye-burst grad-burst" aria-hidden="true"><span class="dye dye-0"></span><span class="dye dye-4 ink"></span></span><span><b>卒業</b>。褐に染まった(この問題はもう出ません)</span></span>`
    : `<span>縫えた。次は <b>${INTERVALS[it.stage]} 日後</b>(4 回のうち ${it.stage + 1} 回目)</span>`;
}

/** 間違えたら、縫えていた目をほどいて 1 目めに戻す(下の「次は 1 日後」と上の表示を食い違わせない) */
function unsewStitch() {
  const marks = view().querySelectorAll(".review-stitch .unshin i");
  if (!marks.length) return;
  // 縫えた目が無い(1 回目で間違えた)ときは、ほどくものが無いので「もう一度」だけ言う
  const had = view().querySelectorAll(".review-stitch .unshin i.sewn").length > 0;
  marks.forEach((m, i) => { m.classList.remove("sewn", "now", "just"); m.classList.toggle("now", i === 0); m.classList.add("undone"); });
  view().querySelector(".review-stitch .unshin").setAttribute("aria-label", had ? "ほどけて 1 回目に戻った" : "1 回目のまま");
  const cap = view().querySelector(".review-stitch .unshin-cap");
  if (cap) cap.innerHTML = had ? `<span><b>ほどけた</b>。明日、1 回目からもう一度</span>` : `<span>明日、もう一度</span>`;
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
  fitSvgFigures(root);
  // 読むためのコードは折り返す。書き写す・実行するもの(サンプル・演習・本文の例)はそのまま
  root.querySelectorAll(".quiz-q .md pre, .q-explain pre, .review-quiz pre, .quiz-answer pre, .reading-answer pre, .check pre").forEach((pre) => pre.classList.add("code-lines"));
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
  // 16 字までのチップには候補を入れない(nowrap でも <wbr> で「/api/tasks/3」が「/」で 2 行に割れた。27 回目の審査)
  root.querySelectorAll(".choice span, :not(pre) > code").forEach((el) => { if (!(el.matches("code") && el.textContent.length <= 16)) softBreaks(el); });
  mendSplitChips(root);
  if (window.hljs) {
    root.querySelectorAll("pre code:not([data-highlighted]):not([data-lines])").forEach((el) => {
      // 仕事のコードの行の説明(コードの中に差し込んだ文)のチップは色付けしない(塊になり、説明が全幅の切れ端に割れた。25 回目の審査)
      if (el.closest(".ln-note")) return;
      if (!el.className.includes("language-")) el.classList.add("language-java");
      if (!el.classList.contains("language-text")) window.hljs.highlightElement(el);
    });
  }
}

/** コードを 1 行ずつの要素に分け、行番号を付ける(色付けの span が行をまたいでも、行ごとに閉じて開き直す) */
function numberLines(code) {
  // 400px 以下は字下げを半分(深くても 6 文字まで)にして 1 行の字数を稼ぐ(文字は 14px のまま。型と名前が折り返しで離れないように)
  const narrow = matchMedia("(max-width: 400px)").matches;
  const text = code.textContent.replace(/\n$/, "").replace(/^ +/gm, (m) => (narrow ? " ".repeat(Math.min(6, Math.ceil(m.length / 2))) : m));
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

/**
 * 長い英字(例外名・修飾した名前)に折り返しの候補を入れる。. ( , の後ろと、28 字以上の語の中の小文字→大文字の境目。
 * 28 字より短い語(NullPointerException・@SpringBootConfiguration)は境目で割らず、行末で収まらなければ語ごと次の行へ送る
 * (「Null / PointerException」と割れていた)。以前は inline-block で包んでいたが、コードのチップの中で箱ごと割れて地が帯になり、
 * 前の「(」が行末に取り残された(23 回目の審査)ので、インラインのまま候補の数で決める。行より長い語は overflow-wrap が折る
 */
function softBreaks(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  // 長い連なり(式・パス・URL・定数)と、ドットを 2 つ以上持つ名前と、パス(/a/b/c)。
  // 「)」「:」「/」「?」「"」などで区切られた式やパスは、20 字に届かず候補が入らなかった(語の途中で割れた。25 回目の審査)
  const LONG = /[@A-Za-z0-9_.(,):\/?&="'<>\[\]-]{20,}|[A-Za-z_]\w*(?:\.\w+){2,}|\/[\w.{}-]+(?:\/[\w.{}-]+){1,}/g;
  const MARK = "\u0001";
  for (let n = walker.nextNode(); n; n = walker.nextNode()) if (new RegExp(LONG.source).test(n.data)) nodes.push(n);
  for (const n of nodes) {
    const span = document.createElement("span");
    // 候補は . ( , / ? & :: の後ろと、大文字の定数の _ の後ろ。28 字以上の区切りの無い所だけ、小文字 → 大文字の境目にも
    const marked = n.data.replace(LONG, (w) =>
      w.replace(/[^.(,\/?&:_]{28,}/g, (seg) => seg.replace(/([a-z])([A-Z])/g, `$1${MARK}$2`))
        .replace(/([.(,?&]|(?<=[^\/])\/(?!\/)|::|(?<=[A-Z0-9])_(?=[A-Z0-9]))(?=.)/g, `$1${MARK}`));
    span.innerHTML = esc(marked).replaceAll(MARK, "<wbr>");
    n.replaceWith(...span.childNodes);
  }
}

/** 組んだあとで、候補の無いまま語の途中で割れたチップ(行の幅に入らない 28 字未満の名前)にだけ、小文字 → 大文字の境目の候補を足す。
 *  初めから境目に候補を入れると、収まる名前まで境目で割れた(23 回目)ので、割れたものだけ */
function mendSplitChips(root) {
  requestAnimationFrame(() => root.querySelectorAll(":not(pre) > code:not(.code-msg)").forEach((c) => {
    if (/\s/.test(c.textContent) || !/[a-z][A-Z]/.test(c.textContent) || c.getClientRects().length < 2 || !splitInsideWord(c)) return;
    // 候補(<wbr>)は残したまま、文字の所だけ境目を足す(`IllegalArgumentException("…")` は「(」の候補があっても、前の 24 字が割れた)
    const walker = document.createTreeWalker(c, NodeFilter.SHOW_TEXT);
    const texts = [];
    for (let n = walker.nextNode(); n; n = walker.nextNode()) if (/[a-z][A-Z]/.test(n.data)) texts.push(n);
    for (const n of texts) {
      const span = document.createElement("span");
      span.innerHTML = esc(n.data).replace(/([a-z])([A-Z])/g, "$1<wbr>$2");
      n.replaceWith(...span.childNodes);
    }
  }));
}

/** チップが語の途中(英数字と英数字の間)で次の行へ割れているか */
function splitInsideWord(c) {
  const walker = document.createTreeWalker(c, NodeFilter.SHOW_TEXT);
  const r = document.createRange();
  let prevTop = null;
  let prevCh = "";
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    for (let i = 0; i < n.data.length; i++) {
      r.setStart(n, i);
      r.setEnd(n, i + 1);
      const rect = r.getClientRects()[0];
      if (!rect) continue;
      if (prevTop !== null && rect.top > prevTop + 2 && /\w/.test(prevCh) && /\w/.test(n.data[i])) return true;
      prevTop = rect.top;
      prevCh = n.data[i];
    }
  }
  return false;
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
  const from = currentRoute;
  currentRoute = location.hash || "#/";
  // 端末の「戻る」(Android の戻るボタン・iOS のスワイプ)でのぞき見の元の一覧へ戻ったときも、戻るリンクと同じく押した所へ
  // (戻るリンクのときだけ送っていたので、端末の「戻る」では読解・用語集の先頭に戻った。27 回目の審査)
  if (!linkNav && peekReturn && from.startsWith("#/lesson/") && currentRoute === PEEK_BACK[peekReturn.tab]?.[0]) returning = peekReturn;
  linkNav = false;
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

// コードを写すときは、折り返しのために挟んだ語をつなぐ印(U+2060)を取り除く(Java に貼るとコンパイルエラーになるので)
document.addEventListener("copy", (e) => {
  const text = getSelection()?.toString() || "";
  if (!text.includes("\u2060")) return;
  e.clipboardData.setData("text/plain", text.replace(/\u2060/g, ""));
  e.preventDefault();
});
