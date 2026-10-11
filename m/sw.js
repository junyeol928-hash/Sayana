// Java道場 スマホ版の Service Worker(MobileSiteGenerator が生成。直接編集しない)
// 最初に開いたときに全レッスンを保存して、電波が無くても開けるようにする。
const VERSION = "92a6e8754d07";
const CACHE = "javadojo-m-" + VERSION;
const RUNTIME = "javadojo-m-runtime";
const FILES = [
  "./",
  "app.css",
  "app.js",
  "data/curriculum.json",
  "data/lessons/capstone-api-test.json",
  "data/lessons/capstone-apply.json",
  "data/lessons/capstone-approve.json",
  "data/lessons/capstone-release.json",
  "data/lessons/capstone-requirements.json",
  "data/lessons/capstone-search.json",
  "data/lessons/capstone-security.json",
  "data/lessons/capstone-setup.json",
  "data/lessons/java-abstract-interface.json",
  "data/lessons/java-arrays.json",
  "data/lessons/java-classes.json",
  "data/lessons/java-collections.json",
  "data/lessons/java-datetime.json",
  "data/lessons/java-encapsulation.json",
  "data/lessons/java-exceptions.json",
  "data/lessons/java-file-io.json",
  "data/lessons/java-generics.json",
  "data/lessons/java-hello.json",
  "data/lessons/java-if.json",
  "data/lessons/java-inheritance.json",
  "data/lessons/java-junit.json",
  "data/lessons/java-lambda-stream.json",
  "data/lessons/java-loops.json",
  "data/lessons/java-methods.json",
  "data/lessons/java-operators.json",
  "data/lessons/java-optional.json",
  "data/lessons/java-polymorphism.json",
  "data/lessons/java-record-enum.json",
  "data/lessons/java-strings.json",
  "data/lessons/java-variables.json",
  "data/lessons/spring-boot-startup.json",
  "data/lessons/spring-config.json",
  "data/lessons/spring-controller.json",
  "data/lessons/spring-data-jpa.json",
  "data/lessons/spring-di.json",
  "data/lessons/spring-exception-handling.json",
  "data/lessons/spring-forms.json",
  "data/lessons/spring-layers.json",
  "data/lessons/spring-logging.json",
  "data/lessons/spring-mockmvc.json",
  "data/lessons/spring-rest.json",
  "data/lessons/spring-security.json",
  "data/lessons/spring-thymeleaf.json",
  "data/lessons/spring-transaction.json",
  "data/lessons/sts-debugger.json",
  "data/lessons/sts-projects.json",
  "data/lessons/sts-setup.json",
  "data/lessons/sts-shortcuts.json",
  "data/lessons/tools-git.json",
  "data/lessons/tools-maven.json",
  "data/prompts.json",
  "icon.svg",
  "index.html",
  "manifest.webmanifest",
  "mobile.css"
];
// 画面の飾り(図・コードの色付け・AIの回答の整形)に使う CDN。取れたら保存しておく
const CDN = [
  "https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs",
  "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/highlight.min.js",
  "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/styles/github.min.css",
  "https://cdn.jsdelivr.net/npm/marked@12.0.2/lib/marked.esm.js",
];

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(FILES);
    const runtime = await caches.open(RUNTIME);
    await Promise.allSettled(CDN.map(async (url) => {
      if (await runtime.match(url)) return;
      const res = await fetch(url, { mode: "cors" });
      if (res.ok) await runtime.put(url, res);
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k.startsWith("javadojo-m-") && k !== CACHE && k !== RUNTIME)
      .map((k) => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.hostname === "api.anthropic.com") return; // AI の通信は保存しない
  if (url.origin === self.location.origin) {
    // 自分のファイルは保存したものを使う(新しい版は新しい Service Worker が入れ替える)
    event.respondWith((async () => {
      const cache = await caches.open(CACHE);
      const hit = await cache.match(req, { ignoreSearch: true });
      if (hit) return hit;
      try {
        return await fetch(req);
      } catch (e) {
        return (await cache.match("index.html")) || Response.error();
      }
    })());
    return;
  }
  if (url.hostname === "cdn.jsdelivr.net" || url.hostname === "esm.sh") {
    event.respondWith((async () => {
      const cache = await caches.open(RUNTIME);
      const hit = await cache.match(req);
      if (hit) return hit;
      const res = await fetch(req);
      if (res.ok) cache.put(req, res.clone());
      return res;
    })());
  }
});
