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
const day = (d) => { const t = new Date(); t.setDate(t.getDate() + d); return t.toISOString().slice(0, 10); };
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
await scrollTo(".dye-strip", 120);
await shot("m-home-lower");
await view({ ...PHONE, dark: true });
await open(mobileArg + "#/");
await js(`window.scrollTo(0, 0);`);
await shot("m-home-dark");
await js(`document.querySelector(".dye-key").open = true;`);
await scrollTo(".dye-strip", 120);
await shot("m-home-dark-lower");
await view(PHONE);
await open(mobileArg + "#/lesson/spring-di");
await js(`window.scrollTo(0, 0); await new Promise((r) => setTimeout(r, 500)); document.querySelector(".step-menu").open = true;`);
await shot("m-lesson-top");
await js(`document.querySelector(".step-menu").open = false;`);
await scrollTo("#atwork .code-notes", 160);
await sleep(3500); // 3 秒読んだら「済み」になるのを確かめる
await shot("m-lesson-atwork");
await js(`const L = await (await fetch("data/lessons/spring-di.json")).json(); const f = document.querySelector("form[data-action=quiz]");
  L.quiz.forEach((q, i) => { f.querySelector('input[name=q' + i + '][value="' + (i === 1 ? (q.answer + 1) % q.choicesHtml.length : q.answer) + '"]').checked = true; });
  f.querySelector("button[type=submit]").click(); await new Promise((r) => setTimeout(r, 1500));`);
await scrollTo(".quiz-q:nth-child(2) .choice", 150);
await shot("m-quiz-inline");
await scrollTo("#quiz-result", 130);
await shot("m-quiz-score");
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

// ---- PC 版(前回の続きは「確かめる」)
await view(DESK);
await open(pcArg);
await js(`localStorage.setItem("javadojo-pc-last", JSON.stringify({ id: "spring-di", title: "DIとIoCコンテナ", section: "quiz", label: "確かめる", off: 40, end: false })); location.reload();`);
await sleep(2500);
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
await open(pcArg + "glossary");
await js(`const t = [...document.querySelectorAll(".term")].find((x) => x.querySelector(".term-count") && x.textContent.includes("equals")); window.scrollTo(0, t.getBoundingClientRect().top + scrollY - 220); await new Promise((r) => setTimeout(r, 600));`);
await shot("pc-glossary");
ws.close();
process.exit(0);
