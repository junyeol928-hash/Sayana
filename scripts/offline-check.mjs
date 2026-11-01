// スマホ版のオフライン動作を、ヘッドレスの Edge で実際に確かめる。
// 1) ローカルのサーバで開き、Service Worker の制御と「保存済み」表示を待って撮る
// 2) サーバを止め、Edge をネットワークに出られない設定(死んだプロキシ)で開き直し、ページもオフラインにして
//    図のあるレッスン・読解・用語集を開いて撮る
// 使い方: node scripts/offline-check.mjs <リポジトリ> <出力フォルダ>(Edge と Node 22 以上、図の部品を取る間のインターネットが要る)
import { spawn } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const [repo, outDir] = process.argv.slice(2);
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const PORT = 8822, DBG = 9333;
const profile = join(tmpdir(), "javadojo-offline-check"); // 毎回まっさらな状態から(リポジトリの中には作らない)
mkdirSync(outDir, { recursive: true });
rmSync(profile, { recursive: true, force: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

function startServer() {
  return spawn("python", ["-m", "http.server", String(PORT), "--bind", "127.0.0.1"], { cwd: repo, stdio: "ignore" });
}
function startEdge(extra = []) {
  return spawn(EDGE, ["--headless=new", `--remote-debugging-port=${DBG}`, `--user-data-dir=${profile}`,
    "--no-first-run", "--no-default-browser-check", "--window-size=390,844", ...extra, "about:blank"], { stdio: "ignore" });
}
async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${DBG}/json`)).json();
      const page = list.find((t) => t.type === "page");
      if (page) return cdp(page.webSocketDebuggerUrl);
    } catch { /* まだ起動中 */ }
    await sleep(300);
  }
  throw new Error("Edge に接続できませんでした");
}
function cdp(url) {
  const ws = new WebSocket(url);
  let id = 0;
  const pending = new Map();
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(m.error.message)) : res(m.result); }
    if (m.method === "Runtime.consoleAPICalled" && ["warning", "error"].includes(m.params.type)) console.log("  [console]", m.params.args.map((a) => a.value ?? a.description).join(" ").slice(0, 300));
    if (m.method === "Runtime.exceptionThrown") console.log("  [exception]", m.params.exceptionDetails.exception?.description?.slice(0, 300));
  };
  const ready = new Promise((r) => { ws.onopen = r; });
  const send = async (method, params = {}) => { await ready; return new Promise((res, rej) => { pending.set(++id, { res, rej }); ws.send(JSON.stringify({ id, method, params })); }); };
  const evaluate = async (expr) => (await send("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result.value;
  const shot = async (name) => {
    const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 85 });
    writeFileSync(join(outDir, name + ".jpg"), Buffer.from(data, "base64"));
    log("撮影", name);
  };
  return { send, evaluate, shot, close: () => ws.close() };
}
const DIAG = `(async () => ({ state: document.querySelector('#save-state')?.textContent, ctl: !!navigator.serviceWorker.controller,
  files: localStorage.getItem('javadojo-m-mermaid-files'), caches: await caches.keys(), online: navigator.onLine,
  imp: await import("https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs").then(() => "ok", (e) => String(e)), ric: typeof requestIdleCallback }))()`;
async function waitFor(c, expr, label, timeoutMs = 120000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    const v = await c.evaluate(expr).catch(() => null);
    if (v) return v;
    await sleep(1000);
  }
  log("診断:", JSON.stringify(await c.evaluate(DIAG).catch((e) => String(e))).slice(0, 1500));
  await c.shot("timeout-" + label.replace(/[^\w]/g, "_"));
  throw new Error("待ちきれませんでした: " + label);
}
async function setup(c, offline) {
  await c.send("Page.enable");
  await c.send("Runtime.enable");
  await c.send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await c.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: "light" }] });
  if (offline) { await c.send("Network.enable"); await c.send("Network.emulateNetworkConditions", { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 }); }
}
const go = async (c, url) => { await c.send("Page.navigate", { url }); await sleep(2500); };

// ---- 1) 電波のある所で開き、保存されるのを待つ
let server = startServer();
let edge = startEdge();
process.on("uncaughtException", (e) => { console.error(e); try { edge.kill(); server.kill(); } catch {} process.exit(1); });
process.on("unhandledRejection", (e) => { console.error(e); try { edge.kill(); server.kill(); } catch {} process.exit(1); });
await sleep(1500);
let c = await connect();
await setup(c, false);
const base = `http://127.0.0.1:${PORT}/m/`;
await go(c, base);
await waitFor(c, "!!navigator.serviceWorker.controller || (location.reload(), false)", "Service Worker の制御", 60000);
await sleep(2000);
await go(c, base);
const saved = await waitFor(c, `(() => { const t = document.querySelector('#save-state')?.textContent || ''; return t.includes('と図') ? t : (t.startsWith('保存済み') && !window.__r && (window.__r = setTimeout(() => location.reload(), 8000)), null); })()`, "保存済み(図まで)", 180000);
log("保存の状態:", saved);
await c.evaluate("window.scrollTo(0,0)");
await sleep(500);
await c.shot("online-home-saved");
const cached = await c.evaluate(`(async () => { const names = await caches.keys(); let n = 0; for (const k of names) n += (await (await caches.open(k)).keys()).length; return names.join(',') + ' / ' + n; })()`);
log("キャッシュ:", cached);
c.close();
edge.kill();
server.kill();
await sleep(2000);

// ---- 2) サーバを止め、ネットワークに出られない Edge で開き直す
edge = startEdge(["--proxy-server=http://127.0.0.1:9", "--proxy-bypass-list=<-loopback>"]);
await sleep(1500);
c = await connect();
await setup(c, true);
const report = { saved, cached };
await go(c, base);
await waitFor(c, "document.querySelector('.today-title') && document.querySelector('#save-state')?.textContent.includes('保存済み')", "オフラインのホーム", 30000);
report.homeNet = await c.evaluate("document.querySelector('#net')?.textContent");
await c.shot("offline-home");
await go(c, base + "#/lesson/java-loops");
await waitFor(c, "document.querySelector('.lesson-body svg') || document.querySelector('.mermaid[data-processed] svg')", "オフラインで図を描く", 30000);
report.figures = await c.evaluate("document.querySelectorAll('.mermaid svg').length");
await c.evaluate("const f=document.querySelector('.mermaid svg'); window.scrollTo(0, f.getBoundingClientRect().top + scrollY - 200)");
await sleep(800);
await c.shot("offline-lesson-figure");
await go(c, base + "#/reading");
await waitFor(c, "document.querySelectorAll('.reading-q').length > 0", "オフラインの読解", 20000);
report.reading = await c.evaluate("document.querySelectorAll('.reading-q').length");
await c.shot("offline-reading");
await go(c, base + "#/terms");
await waitFor(c, "document.querySelectorAll('.term').length > 100", "オフラインの用語集", 20000);
report.terms = await c.evaluate("document.querySelectorAll('.term').length");
await c.shot("offline-terms");
log("結果:", JSON.stringify(report));
writeFileSync(join(outDir, "offline-check.json"), JSON.stringify(report, null, 2));
c.close();
edge.kill();
process.exit(0);
