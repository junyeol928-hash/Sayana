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
    if (k === 0) { window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 300)); window.__first = true; }
    document.querySelector("#next")?.click(); await new Promise((r) => setTimeout(r, 1000));
  }
  window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 300));`);
await shot("m-review-retry-end");
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
await js(`const f = document.querySelector(".review-quiz form"); if (f) { f.querySelector("input").click(); f.querySelector("button[type=submit]").click(); }
  await new Promise((r) => setTimeout(r, 1500));`);
await shot("pc-review-answer");
// 今日の残りを答えて終わりの画面へ(1 つ目の選択肢を選ぶ)。解き直しの結果(1 問目は正しく、2 問目はまだ)を渡した終わりを撮る
for (let k = 0; k < 12; k++) {
  await open(pcArg + "review");
  const more = await js(`const f = document.querySelector(".review-quiz form"); if (!f) return false;
    f.querySelector("input").click(); f.querySelector("button[type=submit]").click(); await new Promise((r) => setTimeout(r, 1200)); return true;`);
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
