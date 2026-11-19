// 相互レビュー用の撮影を、ヘッドレスの Edge でまとめて撮る(画面内ブラウザが隠れていても撮れるように)。
// スマホ版は撮影用の進み具合を localStorage に入れてから撮る。PC 版は起動中のアプリ(既定 8093)の記録のまま。
// 使い方: node scripts/review-shots.mjs <出力フォルダ> [スマホ版の URL] [PC 版の URL]
//   例: node scripts/review-shots.mjs docs/screenshots/round7 http://127.0.0.1:8811/m/ http://127.0.0.1:8093/
import { spawn } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";

const [outArg, mobileArg = "http://127.0.0.1:8811/m/", pcArg = "http://127.0.0.1:8093/"] = process.argv.slice(2);
const outDir = resolve(outArg);
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const DBG = 9334;
const profile = join(tmpdir(), "javadojo-review-shots");
mkdirSync(outDir, { recursive: true });
rmSync(profile, { recursive: true, force: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- 撮影用の進み具合(13 レッスンを読み、深さがばらばら。今日の復習 20 問)
const IDS = ["java-hello", "java-variables", "java-operators", "java-if", "java-loops", "java-arrays", "java-methods", "java-strings",
  "java-classes", "java-encapsulation", "java-inheritance", "java-polymorphism"];
// アプリの today() と同じ地方時の日付(toISOString は UTC なので、日本時間の朝 9 時前は前の日になっていた)
const day = (d) => { const t = new Date(); t.setDate(t.getDate() + d); return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}-${String(t.getDate()).padStart(2, "0")}`; };
function seed() {
  const S = { read: {}, quiz: {}, reading: {}, review: {}, checklist: {}, exercise: {}, chat: {}, days: [], settings: { modelsVersion: 3 }, seen: { "spring-di": true }, sections: { "spring-di": ["atwork", "run"] } };
  [...IDS, "spring-di"].forEach((id) => { S.read[id] = true; });
  [...IDS.slice(0, 10), "spring-di"].forEach((id) => { S.quiz[id] = { best: 5, last: id === "spring-di" ? 4 : 5 }; });
  IDS.slice(0, 6).forEach((id) => [1, 2, 3].forEach((n) => { S.exercise[`${id}/ex${n}`] = { selfPass: true }; }));
  const q = (id, stage, due, graduated) => [0, 1, 2, 3, 4].forEach((i) => { S.review[`${id}#${i}`] = { type: "quiz", lessonId: id, stage, due, lapses: 0, graduated }; });
  IDS.slice(0, 3).forEach((id) => q(id, 4, day(5), true));
  ["java-if", "java-arrays", "java-strings", "java-encapsulation"].forEach((id) => q(id, 1, day(0), false));
  ["java-loops", "java-methods", "java-classes"].forEach((id) => q(id, 1, day(2), false));
  q("spring-di", 0, day(1), false);
  IDS.slice(0, 5).forEach((id) => [0, 1, 2, 3].forEach((i) => { S.reading[`${id}@${i}`] = { ok: i !== 2, at: day(-1) }; }));
  S.days = [-9, -8, -6, -4, -3, -2, -1, 0].map(day);
  S.last = { id: "spring-di", section: "quiz", off: 40 };
  return S;
}

const edge = spawn(EDGE, ["--headless=new", `--remote-debugging-port=${DBG}`, `--user-data-dir=${profile}`, "--no-first-run",
  "--no-default-browser-check", "--hide-scrollbars", "about:blank"], { stdio: "ignore" });
process.on("exit", () => { try { edge.kill(); } catch { /* 終わっている */ } });

async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const page = (await (await fetch(`http://127.0.0.1:${DBG}/json`)).json()).find((t) => t.type === "page");
      if (page) return page.webSocketDebuggerUrl;
    } catch { /* 起動中 */ }
    await sleep(300);
  }
  throw new Error("Edge に接続できませんでした");
}
const ws = new WebSocket(await connect());
await new Promise((r) => { ws.onopen = r; });
let seq = 0;
const pending = new Map();
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(m.error.message)) : res(m.result); }
};
const send = (method, params = {}) => new Promise((res, rej) => { pending.set(++seq, { res, rej }); ws.send(JSON.stringify({ id: seq, method, params })); });
const js = async (expr) => (await send("Runtime.evaluate", { expression: `(async () => { ${expr} })()`, awaitPromise: true, returnByValue: true })).result.value;
await send("Page.enable");
await send("Runtime.enable");

async function view({ w, h, dpr, mobile, dark }) {
  await send("Emulation.setDeviceMetricsOverride", { width: w, height: h, deviceScaleFactor: dpr, mobile });
  await send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: dark ? "dark" : "light" }, { name: "prefers-reduced-motion", value: "reduce" }] });
}
async function open(url) { await send("Page.navigate", { url }); await sleep(2500); }
async function shot(name) {
  await sleep(700);
  const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 85 });
  writeFileSync(join(outDir, name + ".jpg"), Buffer.from(data, "base64"));
  console.log("撮影", name);
}
const scrollTo = (sel, off) => js(`const el = document.querySelector(${JSON.stringify(sel)}); window.scrollTo(0, el.getBoundingClientRect().top + scrollY - ${off}); await new Promise((r) => setTimeout(r, 500));`);

const PHONE = { w: 390, h: 844, dpr: 2, mobile: true };
const DESK = { w: 1440, h: 900, dpr: 1, mobile: false };

// ---- スマホ版
await view(PHONE);
await open(mobileArg);
await js(`localStorage.setItem("javadojo-m-v1", ${JSON.stringify(JSON.stringify(seed()))}); location.reload();`);
await sleep(2500);
await shot("m-home-light");
await js(`document.querySelector(".dye-key").open = true;`);
await scrollTo(".dye-map", 120);
await shot("m-home-lower");
await view({ ...PHONE, dark: true });
await open(mobileArg + "#/");
await js(`window.scrollTo(0, 0);`);
await shot("m-home-dark");
await js(`document.querySelector(".dye-key").open = true;`);
await scrollTo(".dye-map", 120);
await shot("m-home-dark-lower");
await view(PHONE);
// 本文の表は 390px で横にはみ出さない(3 列までは行ごとの積み)。spring-boot-startup の合成アノテーションの表を撮り、はみ出す数を出す
await open(mobileArg + "#/lesson/spring-boot-startup");
console.log("390px ではみ出す本文の表:", await js(`return [...document.querySelectorAll(".md table")].filter((t) => t.scrollWidth > t.clientWidth + 1).length + " / " + document.querySelectorAll(".md table").length;`));
await scrollTo(".md table", 160);
await shot("m-table");
// 縦書きの軸が 2 列目へ折れていないか(26 回目: 本文の短いレッスンで 55 本中 33 本が折れていた)。全レッスンを数え、本文の短い java-hello を撮る
const ids = await js(`const c = await (await fetch("data/curriculum.json")).json(); return c.parts.flatMap((p) => p.chapters.flatMap((ch) => ch.lessons.map((l) => l.id)));`);
let folded = 0;
for (const id of ids) {
  await send("Page.navigate", { url: mobileArg + "#/lesson/" + id });
  await sleep(900);
  if (await js(`const cols = (el) => { if (!el || getComputedStyle(el).display === "none") return 1; const r = document.createRange(); r.selectNodeContents(el); return new Set([...r.getClientRects()].map((q) => Math.round(q.left))).size; };
    const ax = document.querySelector(".lesson-axis"); return !!ax && (cols(ax.querySelector("span")) > 1 || cols(ax.querySelector("small")) > 1);`)) folded++;
}
console.log("2 列目へ折れる軸:", folded, "/", ids.length);
await open(mobileArg + "#/lesson/java-hello");
await js(`window.scrollTo(0, 0);`);
await shot("m-lesson-axis-short");
// ホームの大きな題が札からはみ出さないか(27 回目: 「ポリモーフィズム」「トランザクション」が 56px のまま外へ出て、ホームの幅も広がった)。
// 55 本をそれぞれ前回の続きにして数え、いちばん長いカタカナ 1 語の java-polymorphism を撮る
let titleOver = 0;
for (const id of ids) {
  const S = seed(); S.last = { id, section: "read", off: 0 };
  await js(`localStorage.setItem("javadojo-m-v1", ${JSON.stringify(JSON.stringify(S))});`);
  // #/ の後ろだけを変えるとページが読み直されず、前の記録のホームのまま数えていた(28 回目)。? を変えて読み直す
  await send("Page.navigate", { url: mobileArg + "?t=" + id + "#/" });
  await sleep(1200);
  if (await js(`const h = document.querySelector(".today-title"); return !h || [...h.querySelectorAll(".phrase")].some((p) => p.scrollWidth > p.clientWidth + 1 && !p.classList.contains("phrase-break")) || document.documentElement.scrollWidth > innerWidth + 1;`)) titleOver++;
}
console.log("ホームの題が札からはみ出す:", titleOver, "/", ids.length);
{
  const S = seed(); S.last = { id: "java-polymorphism", section: "read", off: 0 };
  await js(`localStorage.setItem("javadojo-m-v1", ${JSON.stringify(JSON.stringify(S))});`);
  await open(mobileArg + "?t=long-title#/");
  await js(`window.scrollTo(0, 0);`);
  await shot("m-home-long-title");
  await js(`localStorage.setItem("javadojo-m-v1", ${JSON.stringify(JSON.stringify(seed()))});`);
  await open(mobileArg + "?t=seed#/");
}
// 手描きの SVG の図(27 回目: 34 のうち 33 が右の 3 割を手がかりなく切られていた)。はみ出す図のうち、フェードと「図の全体を見る」の無い数
let figBare = 0, figOver = 0;
for (const id of ids) {
  await send("Page.navigate", { url: mobileArg + "#/lesson/" + id });
  await sleep(700);
  const r = await js(`const of = [...document.querySelectorAll(".fig-scroll")].filter((f) => f.scrollWidth > f.clientWidth + 1);
    return [of.length, of.filter((f) => !(f.classList.contains("has-more") && f.nextElementSibling?.classList.contains("fig-fit") && getComputedStyle(f.nextElementSibling).display !== "none")).length];`);
  figOver += r[0]; figBare += r[1];
}
console.log("390px ではみ出す手描きの図:", figOver, "/ うち手がかりの無いもの:", figBare);
await open(mobileArg + "#/lesson/java-jvm-memory");
await scrollTo(".fig-scroll", 140);
await shot("m-figure-svg");
await js(`localStorage.setItem("javadojo-m-v1", ${JSON.stringify(JSON.stringify(seed()))});`);
await open(mobileArg + "#/lesson/spring-di");
await js(`window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 500)); document.querySelector(".step-menu").open = true;`);
await shot("m-lesson-top");
await js(`document.querySelector(".step-menu").open = false;`);
await scrollTo("#atwork .code-notes", 160);
await sleep(3500); // 3 秒読んだら「済み」になるのを確かめる
await shot("m-lesson-atwork");
// 仕事のコードの行の説明の中のチップ(25 回目: 色付けの塊になって、説明が全幅の切れ端に割れていた)
await open(mobileArg + "#/lesson/java-arrays");
console.log("行の説明の中の色付けの塊:", await js(`return document.querySelectorAll(".ln-note code.hljs").length + " / " + document.querySelectorAll(".ln-note code").length;`));
await scrollTo(".ln-note code", 260);
await shot("m-lnnote-chip");
await open(mobileArg + "#/lesson/spring-di");
await js(`const L = await (await fetch("data/lessons/spring-di.json")).json(); const f = document.querySelector("form[data-action=quiz]");
  L.quiz.forEach((q, i) => { f.querySelector('input[name=q' + i + '][value="' + (i === 1 ? (q.answer + 1) % q.choicesHtml.length : q.answer) + '"]').checked = true; });
  f.querySelector("button[type=submit]").click(); await new Promise((r) => setTimeout(r, 1500));`);
await scrollTo(".quiz-q:nth-child(2) .choice", 150);
await shot("m-quiz-inline");
await scrollTo("#quiz-result", 130);
await shot("m-quiz-score");
// 手を止めて(本文の途中の 1 問)。答える前と、誤答を押したあと
await scrollTo("#read aside.check", 140);
await shot("m-check");
await js(`const b = [...document.querySelectorAll("#read aside.check")][0]; const wrong = [...b.querySelectorAll(".choice")].find((x) => Number(x.dataset.c) !== Number(b.dataset.answer)); wrong.click(); await new Promise((r) => setTimeout(r, 400));`);
await scrollTo("#read aside.check", 140);
await shot("m-check-answer");
await scrollTo("#read aside.check .check-explain", 420);
await shot("m-check-why");
// サンプルの実行結果(予想してから開く欄)。閉じた形と開いた形
await scrollTo("#run details.sample-result", 420);
await shot("m-sample-closed");
await js(`document.querySelector("#run .sample-result").open = true;`);
await scrollTo("#run .sample-result", 260);
await shot("m-sample-result");
// 復習(レッスンを混ぜ、選択肢の並びを入れ替える)。1 問答えたあとの形も
await open(mobileArg + "#/review");
await js(`window.scrollTo(0, 0);`);
await shot("m-review");
// 選んだら「答え合わせ」が下のメニューのすぐ上に留まる(選ぶだけ。答えるのは下で)
await js(`const i = document.querySelector("#rq input"); if (i) { i.checked = true; i.dispatchEvent(new Event("change", { bubbles: true })); }`);
await shot("m-review-chosen");
await js(`document.querySelectorAll("#rq input").forEach((i) => { i.checked = false; });`);
// わざと間違える(ほどける運針・上の数の更新・選んだ誤答への送りを見るため)。出ている問いの正解を教材のデータから探す
await js(`const f = document.querySelector("#rq"); if (f) {
    const head = document.querySelector(".review-quiz > .md").textContent.replace(/[\\s\\u2060]+/g, "").slice(0, 24);
    let answer = -1;
    for (const id of ["java-if", "java-arrays", "java-strings", "java-encapsulation", "spring-di"]) {
      const L = await (await fetch("data/lessons/" + id + ".json")).json();
      [...L.quiz, ...(L.checks || [])].forEach((q) => { const d = document.createElement("div"); d.innerHTML = q.questionHtml; if (d.textContent.replace(/[\\s\\u2060]+/g, "").startsWith(head)) answer = q.answer; });
    }
    [...f.querySelectorAll("input")].find((x) => Number(x.value) !== answer).click();
    f.querySelector("button[type=submit]").click(); }
  await new Promise((r) => setTimeout(r, 1200));`);
await shot("m-review-answer");
await js(`window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 300));`);
await shot("m-review-answer-top");
// 「読み直す」はのぞき見: レッスンの段の帯に「復習へ戻る」、ホームの前回の続きは変わらない(25 回目: 古いレッスンに替わっていた)
const heroBefore = await js(`return JSON.parse(localStorage.getItem("javadojo-m-v1")).last?.id || "";`);
await js(`document.querySelector(".review-source")?.click(); await new Promise((r) => setTimeout(r, 2500));`);
await shot("m-reread-landing");
await open(mobileArg + "#/");
console.log("読み直したあとの前回の続き:", heroBefore, "→", await js(`return JSON.parse(localStorage.getItem("javadojo-m-v1")).last?.id || "";`));
await shot("m-home-after-reread");
await open(mobileArg + "#/review");
// 10 問の区切り(今日 10 問答えた形にして開き直す)
await js(`const s = JSON.parse(localStorage.getItem("javadojo-m-v1")); const d = new Date(); const t = d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  const keys = Object.keys(s.review).filter((k) => !s.review[k].graduated); // 本物の問題で(間違えた問題の行に名前が出るように)
  // 間違えた問は選んだ答えも残す(区切りの「間違えた問題」の行に、どう読み違えたかが出るように。実際の流れと同じ)
  const refs = {}, choices = {};
  const oks = [true, true, false, true, true, true, false, true, true, true];
  for (let i = 0; i < oks.length; i++) {
    const k = keys[i]; refs[k] = oks[i];
    if (!oks[i]) {
      const L = await (await fetch("data/lessons/" + s.review[k].lessonId + ".json")).json();
      const q = k.includes("#") ? L.quiz[Number(k.split("#")[1])] : k.includes("!") ? L.checks[Number(k.split("!")[1])] : k.includes("@") ? L.reading[Number(k.split("@")[1])] : null;
      if (q) choices[k] = (q.answer + 1) % q.choicesHtml.length;
    }
  }
  s.reviewLog = { [t]: { refs, choices } };
  // 区切りに数えた 10 問は答えた扱いなので、今日の残りから外す(「残り n 問」を実際の流れとそろえる。21 回目の審査)
  const tomorrow = new Date(d.getTime() + 864e5); const t1 = tomorrow.getFullYear() + "-" + String(tomorrow.getMonth() + 1).padStart(2, "0") + "-" + String(tomorrow.getDate()).padStart(2, "0");
  keys.slice(0, 10).forEach((k) => { s.review[k].due = t1; });
  localStorage.setItem("javadojo-m-v1", JSON.stringify(s)); location.reload();`);
await sleep(2500);
await js(`window.scrollTo(0, 0);`);
await shot("m-review-break");
// 今日の終わり(12 問のうち 9 問)。間違えた問題の行は、選んだ誤答の読み違えを言う。そこから解き直しへ
await js(`const s = JSON.parse(localStorage.getItem("javadojo-m-v1")); const d = new Date(); const day = (x) => x.getFullYear() + "-" + String(x.getMonth() + 1).padStart(2, "0") + "-" + String(x.getDate()).padStart(2, "0");
  const t = day(d); const t1 = day(new Date(d.getTime() + 864e5));
  const keys = Object.keys(s.review).filter((k) => !s.review[k].graduated && s.review[k].type !== "exercise");
  const oks = [true, true, false, true, true, true, false, true, true, true, false, true];
  const refs = {}, choices = {};
  for (let i = 0; i < oks.length && i < keys.length; i++) {
    const k = keys[i]; refs[k] = oks[i]; s.review[k].due = t1;
    if (!oks[i]) {
      const L = await (await fetch("data/lessons/" + s.review[k].lessonId + ".json")).json();
      const q = k.includes("#") ? L.quiz[Number(k.split("#")[1])] : k.includes("!") ? L.checks[Number(k.split("!")[1])] : L.reading[Number(k.split("@")[1])];
      choices[k] = (q.answer + 1) % q.choicesHtml.length;
    }
  }
  Object.values(s.review).forEach((it) => { if (!it.graduated && it.due <= t) it.due = t1; });
  s.reviewLog = { [t]: { refs, choices } };
  localStorage.setItem("javadojo-m-v1", JSON.stringify(s)); location.reload();`);
await sleep(2500);
await js(`window.scrollTo(0, 0);`);
await shot("m-review-done");
await js(`document.querySelector("#again")?.click(); await new Promise((r) => setTimeout(r, 1200)); window.scrollTo(0, 0);`);
await shot("m-review-retry");
// 解き直しを最後まで(1 問目は正しく、ほかは誤答で)。終わりの運針に仮縫いの跡と、行ごとの「仮縫いできた / まだ」
await js(`for (let k = 0; k < 6; k++) {
    const f = document.querySelector("#rq") || document.querySelector("form[data-action=reading]");
    if (!f) break;
    const head = (f.closest(".review-quiz, .reading-q")?.querySelector(".md, .reading-question")?.textContent || "").replace(/[\\s\\u2060]+/g, "").slice(0, 24);
    let answer = -1;
    const s = JSON.parse(localStorage.getItem("javadojo-m-v1") || "{}");
    for (const id of new Set(Object.values(s.review || {}).map((it) => it.lessonId))) {
      const L = await (await fetch("data/lessons/" + id + ".json")).json();
      [...L.quiz, ...(L.checks || []), ...(L.reading || [])].forEach((q) => { const d = document.createElement("div"); d.innerHTML = q.questionHtml; if (d.textContent.replace(/[\\s\\u2060]+/g, "").startsWith(head)) answer = q.answer; });
    }
    const inputs = [...f.querySelectorAll("input[type=radio]")];
    (inputs.find((x) => (k === 0) === (Number(x.value) === answer)) || inputs[0]).click();
    f.querySelector("button[type=submit]").click(); await new Promise((r) => setTimeout(r, 700));
    // 答えたあとの「次へ」の言葉(27 回目: 最後から 2 問目で「おわりの画面へ」と出ていた)
    (window.__labels = window.__labels || []).push((document.querySelector("#review-progress")?.textContent || "").replace(/\\s+/g, " ").trim() + " → " + (document.querySelector("#next")?.textContent || ""));
    if (k === 0) { window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 300)); window.__first = true; }
    document.querySelector("#next")?.click(); await new Promise((r) => setTimeout(r, 1000));
  }
  window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 300));`);
await shot("m-review-retry-end");
console.log("解き直しの「次へ」の言葉:", await js(`return (window.__labels || []).join(" / ");`));
await open(mobileArg + "#/reading");
await js(`window.scrollTo(0, 0);`);
await shot("m-reading");
await view({ ...PHONE, dark: true });
await open(mobileArg + "#/reading");
await scrollTo(".reading-q", 70);
await shot("m-reading-dark");
await view(PHONE);
await open(mobileArg + "#/terms");
await js(`const t = [...document.querySelectorAll(".term")].find((x) => x.querySelector(".term-count") && x.textContent.includes("equals")); window.scrollTo(0, t.getBoundingClientRect().top + scrollY - 200); await new Promise((r) => setTimeout(r, 600));`);
await shot("m-terms");

// ---- PC 版(前回の続きは「クイズ」)
await view(DESK);
await open(pcArg);
await js(`localStorage.setItem("javadojo-pc-last", JSON.stringify({ id: "spring-di", title: "DIとIoCコンテナ", section: "quiz", off: 40, end: false })); location.reload();`);
await sleep(1500);
// 読み込みの途中(前回の続きに差し替える前はヒーローを隠す)で撮らないよう、整うまで待つ(19 回目の撮影で途中が写った)
await js(`for (let i = 0; i < 50 && !(document.readyState === "complete" && !document.body.classList.contains("resume-pending")); i++) await new Promise((r) => setTimeout(r, 100)); await new Promise((r) => setTimeout(r, 400));`);
await shot("pc-dashboard");
await open(pcArg + "lessons/spring-di");
await js(`window.scrollTo(0, 0);`);
await shot("pc-lesson-top");
await scrollTo("#atwork .notes-toggle", 190);
await shot("pc-lesson-atwork");
// 本文の軸(28 回目): 1280px 以上・AI の欄が無い 1 列では、冒頭の藍の軸が本文の終わりまで続き、段ごとの縦の運針が縫い進む。
// 読み進めた途中(読むの 45%)を 1440・1280px の明るい・暗い表示で撮る
const PC1280 = { w: 1280, h: 800, dpr: 1, mobile: false };
for (const [name, size] of [["pc-rail-1440", DESK], ["pc-rail-1440-dark", { ...DESK, dark: true }], ["pc-rail-1280", PC1280], ["pc-rail-1280-dark", { ...PC1280, dark: true }]]) {
  await view(size);
  await open(pcArg + "lessons/spring-di");
  await js(`const s = document.getElementById("read"); window.scrollTo(0, s.offsetTop + s.offsetHeight * 0.45); await new Promise((r) => setTimeout(r, 600));`);
  await shot(name);
}
await view(DESK);
await open(pcArg + "lessons/spring-di");
// 本文の 1 行の字数(27 回目: 860px で約 51 字。目安は 17px で約 42 字)。本文と同じ組みの段落に全角の字を並べて数える
console.log("本文の 1 行の字数(1440px):", await js(`const p = document.createElement("p"); p.textContent = "あ".repeat(200); document.querySelector(".lesson-body").prepend(p);
  const t = p.firstChild, r = document.createRange(); let lo = 1, hi = 200;
  while (lo < hi) { const m = Math.ceil((lo + hi) / 2); r.setStart(t, 0); r.setEnd(t, m); if (new Set([...r.getClientRects()].map((q) => Math.round(q.top))).size > 1) hi = m - 1; else lo = m; }
  p.remove(); return lo + " 字(行の幅 " + Math.round(document.querySelector(".lesson-body").getBoundingClientRect().width) + "px)";`));
// サンプルの編集欄(27 回目: ここだけ白地に紫・緑の既定の配色)。ほかのコードの面と同じ色付けか
await scrollTo("#run .cm-editor", 160);
console.log("編集欄の色付け:", await js(`const e = document.querySelector("#run .cm-editor"); return e ? getComputedStyle(e).backgroundColor + " / 注釈 " + e.querySelectorAll(".tok-meta").length + "・予約語 " + e.querySelectorAll(".tok-keyword").length + "・既定の配色の印 " + e.querySelectorAll(".cm-line span[class^='ͼ']").length : "CodeMirror なし";`));
await shot("pc-sample-editor");
// 編集欄のキーボード(28 回目: Tab が字下げになって抜けられず、案内も読み上げの名前も無かった)。Tab で残り、Esc のあと Tab で外へ出るか
{
  const key = async (k, code, vk) => { for (const type of ["keyDown", "keyUp"]) await send("Input.dispatchKeyEvent", { type, key: k, code, windowsVirtualKeyCode: vk }); await sleep(150); };
  await js(`document.querySelector("#run .cm-content").focus();`);
  await key("Tab", "Tab", 9);
  const inTab = await js(`return !!document.activeElement.closest(".cm-editor");`);
  await key("Escape", "Escape", 27);
  await key("Tab", "Tab", 9);
  const out = await js(`const a = document.activeElement; return a.closest(".cm-editor") ? "編集欄の中" : (a.textContent.trim() || a.tagName);`);
  const name = await js(`const c = document.querySelector("#run .cm-content"); return (c.getAttribute("aria-label") || "名前なし") + " / 案内 " + (document.getElementById(c.getAttribute("aria-describedby") || "")?.textContent.trim() || "なし");`);
  console.log("編集欄のキーボード: Tab のあと", inTab ? "編集欄に残る(字下げ)" : "外へ出た", "/ Esc→Tab のあと", out, "/", name);
  await js(`location.reload();`); // 字下げを入れた編集欄を元に戻す
  await sleep(2500);
}
// サンプルを実行した結果(28 回目: 緑・朱の全面の塗りをやめ、左の線に)
await js(`document.querySelector("#run .run-form button[type=submit]").click(); for (let i = 0; i < 60 && !document.querySelector("#run .run-result"); i++) await new Promise((r) => setTimeout(r, 250));`);
await scrollTo("#run .sample-out", 300);
await shot("pc-run-result");
// 演習に模範解答を出して合格したとき(28 回目: 段の帯・軸の「演習」と冒頭の色見本が、開き直すまで古いままだった)。
// この作業ツリーの data/ の記録が進む
await open(pcArg + "lessons/java-hello");
console.log("演習の合格:", await js(`const cards = [...document.querySelectorAll("#write .exercise")];
  for (const c of cards) {
    c.querySelector("textarea[name=code]").value = c.querySelector(".solution code").textContent;
    c.querySelector(".code-form button[type=submit]").click();
    for (let i = 0; i < 120 && !c.querySelector(".grade-result"); i++) await new Promise((r) => setTimeout(r, 250));
  }
  const w = document.querySelector('.stepper .step[href="#write"]'), rw = document.querySelector('.rail-step[href="#write"]');
  return cards.length + " 問 / 合格 " + document.querySelectorAll("#write .grade-result.status-passed").length + " / 帯 " + (w?.className || "-") + " / 軸 " + (rw?.className || "-")
    + " / 冒頭 " + (document.querySelector(".lesson-swatch")?.getAttribute("aria-label") || "-");`));
await js(`const g = [...document.querySelectorAll("#write .grade-result")].pop(); window.scrollTo(0, g.getBoundingClientRect().top + scrollY - 260); await new Promise((r) => setTimeout(r, 500));`);
await shot("pc-exercise-pass");
// 全レッスンを 1280px で開き、軸が出ない・段の数が帯と違う・段の名前が画面の左へ出る・横にはみ出す数(目標はどれも 0)と、
// 横に送られる編集欄の数(28 回目: 736px の幅で 149 本のうち 96 本。コードだけ右へ張り出して、目標は 30 本以下)
// サンプルと演習の編集欄を分けて [サンプルの横送り, サンプル, 演習の横送り, 演習] を返す
const cmCount = `const ov = (s) => s.scrollWidth > s.clientWidth + 1; const sm = [...document.querySelectorAll(".sample .cm-scroller")], ex = [...document.querySelectorAll(".exercise .cm-scroller")];
  return [sm.filter(ov).length, sm.length, ex.filter(ov).length, ex.length];`;
const addCount = (acc, c) => c.forEach((n, i) => { acc[i] += n; });
await view(PC1280);
const railBad = [];
const cm1280 = [0, 0, 0, 0];
const summaryOrphan = [];
for (const id of ids) {
  await send("Page.navigate", { url: pcArg + "lessons/" + id });
  await sleep(1200);
  const bad = await js(`const rail = document.querySelector(".lesson-rail"); if (!rail || getComputedStyle(rail).display === "none") return "軸なし";
    if (rail.querySelectorAll(".rail-step").length !== document.querySelectorAll(".stepper .step").length) return "段の数";
    if (Math.min(...[...rail.querySelectorAll(".rail-label, .rail-back, .rail-cap")].map((l) => l.getBoundingClientRect().left)) < 0) return "名前が画面の外";
    if (document.documentElement.scrollWidth > innerWidth + 1) return "横にはみ出す"; return "";`);
  if (bad) railBad.push(id + ":" + bad);
  addCount(cm1280, await js(cmCount));
  // 冒頭の要約の最後の行が 2 字以下(40px 以下)か(28 回目: 「…落とし穴ま / で。」が 3 / 55)
  if (await js(`const s = document.querySelector(".lesson-hero .summary"); if (!s) return false; const r = document.createRange(); r.selectNodeContents(s);
    // コードのチップの矩形は上端が数 px ずれるので、上端の差が 8px 以内なら同じ行にまとめる
    const rows = []; for (const q of [...r.getClientRects()].sort((a, b) => a.top - b.top)) { const row = rows.find((w) => Math.abs(w.top - q.top) < 8); if (row) row.w += q.width; else rows.push({ top: q.top, w: q.width }); }
    return rows.length > 1 && rows[rows.length - 1].w <= 40;`)) summaryOrphan.push(id);
}
console.log("1280px で冒頭の要約の最後の行が 2 字以下:", summaryOrphan.length, "/", ids.length, summaryOrphan.join(" "));
console.log("1280px の本文の軸の不具合:", railBad.length, "/", ids.length, railBad.join(" "));
console.log("1280px で横に送られる編集欄: サンプル", cm1280[0], "/", cm1280[1], "・演習", cm1280[2], "/", cm1280[3]);
// 1440px で全レッスンを復習の「読み直す」(#quiz-q1)で開き、3 秒後に問いの頭が画面の上半分にあるか(28 回目: 図が描けて上の段が
// 伸び、55 本のうち 10 本で問いが画面の外へ流された。目標 0)と、横に送られる編集欄の数
await view(DESK);
const driftBad = [];
const cm1440 = [0, 0, 0, 0];
for (const id of ids) {
  await send("Page.navigate", { url: pcArg + "lessons/" + id + "?from=review#quiz-q1" });
  await sleep(3000);
  const top = await js(`const q = document.getElementById("quiz-q1"); return q ? Math.round(q.getBoundingClientRect().top) : null;`);
  if (top != null && (top < 0 || top > 450)) driftBad.push(id + ":" + top);
  addCount(cm1440, await js(cmCount));
}
console.log("1440px の読み直すで問いが画面の上半分にない:", driftBad.length, "/", ids.length, driftBad.join(" "));
console.log("1440px で横に送られる編集欄: サンプル", cm1440[0], "/", cm1440[1], "・演習", cm1440[2], "/", cm1440[3]);
// ホームの「続きから」で開いたとき(28 回目: 前回の続きのクイズではなく、1 画面上のサンプルに着いた)。3 秒後の前回の段の頭の位置
await open(pcArg);
await js(`localStorage.setItem("javadojo-pc-last", JSON.stringify({ id: "spring-di", title: "DIとIoCコンテナ", section: "quiz", off: 40, end: false })); location.reload();`);
await sleep(2000);
await js(`document.querySelector(".today-title a").click();`); // 題のリンクは、いつも前回の位置へ戻す
await sleep(3500);
console.log("続きからの着地(クイズの頭):", await js(`return Math.round(document.getElementById("quiz").getBoundingClientRect().top) + "px";`));
await shot("pc-resume");
await view(DESK);
await open(pcArg + "lessons/spring-di"); // 続くクイズの撮影は spring-di で
await js(`const ans = [2, 1, 3, 1, 2]; document.querySelectorAll("form.quiz .quiz-q").forEach((q, i) => { q.querySelectorAll("input")[ans[i]].checked = true; });
  document.querySelector("form.quiz button[type=submit]").click(); await new Promise((r) => setTimeout(r, 2000));`);
await scrollTo("form.quiz .quiz-q:nth-child(2) .choice", 160);
await shot("pc-quiz");
await scrollTo("aside.check", 160);
await shot("pc-check");
await open(pcArg + "review");
await js(`window.scrollTo(0, 0);`);
await shot("pc-review");
// 答えたあと(この作業ツリーの data/ の記録が 1 問進む)
// 今日の分をこの作業ツリーで答え終えていると、答える前と同じ「済み」の画面が 2 枚になる(28 回目)。そのときは知らせて撮らない
const reviewLeft = await js(`const f = document.querySelector(".review-quiz form"); if (!f) return false; f.querySelector("input[type=radio]").click(); await new Promise((r) => setTimeout(r, 200)); f.querySelector("button[type=submit]").click();
  await new Promise((r) => setTimeout(r, 1500)); return true;`);
if (reviewLeft) await shot("pc-review-answer");
else console.log("PC の復習: 今日の分は答え終えているので、答えたあとの画面は撮りません(data/ を消すと撮れる)");
// 今日の残りを答えて終わりの画面へ(1 つ目の選択肢を選ぶ)。解き直しの結果(1 問目は正しく、2 問目はまだ)を渡した終わりを撮る
for (let k = 0; k < 12; k++) {
  await open(pcArg + "review");
  const more = await js(`const f = document.querySelector(".review-quiz form"); if (!f) return false;
    f.querySelector("input[type=radio]").click(); await new Promise((r) => setTimeout(r, 200)); f.querySelector("button[type=submit]").click(); await new Promise((r) => setTimeout(r, 1200)); return true;`);
  if (!more) break;
}
await open(pcArg + "review?retry=99&r=100");
await scrollTo(".review-done, .review-break", 90);
await shot("pc-review-retry-end");
// 「読み直す」で問いそのものへ着いたとき(1.2 秒の藍の枠とフォーカス)。枠が消える前に撮る
await send("Page.navigate", { url: pcArg + "lessons/java-if?from=review#quiz-q1" });
let landed = "";
for (let k = 0; k < 40 && !landed.includes("枠 true"); k++) {
  await sleep(100);
  landed = await js(`return (document.activeElement?.id || document.activeElement?.tagName) + " / 枠 " + !!document.querySelector(".spot-flash");`).catch(() => "");
}
console.log("読み直すの着地:", landed);
{
  const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 85 });
  writeFileSync(join(outDir, "pc-reread.jpg"), Buffer.from(data, "base64"));
  console.log("撮影", "pc-reread");
}
await open(pcArg + "glossary");
await js(`const t = [...document.querySelectorAll(".term")].find((x) => x.querySelector(".term-count") && x.textContent.includes("equals")); window.scrollTo(0, t.getBoundingClientRect().top + scrollY - 220); await new Promise((r) => setTimeout(r, 600));`);
await shot("pc-glossary");
// 本文の書体で欧文の下(g・j・p・q)が欠けないか(21 回目: Windows の BIZ UDPGothic で欠けていた)。毎回 56px で並べて確かめる
await js(`document.body.insertAdjacentHTML("afterbegin", '<div id="font-check" style="position:fixed;inset:0;z-index:9999;background:var(--bg);color:var(--ink);padding:40px;font:400 56px/1.6 var(--font)">本文: equals Spring javac getBean()<br><b>太字: Repository jpg qy</b><br><span style="font-family:var(--head);font-weight:700">見出し: Spring Boot Java</span></div>'); await new Promise((r) => setTimeout(r, 400));`);
await shot("pc-font-check");
ws.close();
process.exit(0);
