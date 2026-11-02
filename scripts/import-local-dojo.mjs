// ローカル版 Java道場(Artifact 版、data/chNN_*.js)から、Sayana にない教材を移す一度きりの変換スクリプト。
//   node scripts/import-local-dojo.mjs <ローカル版のフォルダ> [content]
// 書き出すもの:
//   - 各レッスンの reading.yml(コード読解問題)と atwork.yml(仕事のコードではこう出てくる)
//   - content/glossary.yml(用語集。同じ用語でも章ごとに説明が違うので全部残す。画面では読み順に並べる)
// ローカル版の本文・確認テストは移さない(重なる章は Sayana 側を正とする)。
// 既にあるファイルは上書きする(手で直したあとに流し直さないこと)。
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const [localDir, contentDir = "content"] = process.argv.slice(2);
if (!localDir) {
  console.error("使い方: node scripts/import-local-dojo.mjs <ローカル版のフォルダ> [content]");
  process.exit(1);
}

// ローカル版の章 → Sayana のレッスン。1 つのレッスンに複数の章が入ることがある。
const MAP = {
  ch00: "java-hello", ch01: "java-variables", ch02: "java-operators", ch03: "java-arrays",
  ch04: "java-if", ch05: "java-loops", ch06: "java-methods", ch07: "java-classes",
  ch08: "java-classes", ch09: "java-inheritance", ch10: "java-abstract-interface",
  ch11: "java-encapsulation", ch12: "java-exceptions", ch13: "java-collections",
  ch14: "java-polymorphism", ch15: "sts-debugger", ch16: "java-strings", ch17: "java-generics",
  ch18: "java-lambda-stream", ch19: "java-lambda-stream", ch20: "java-optional",
  ch21: "java-record-enum", ch22: "java-datetime", ch23: "java-annotations",
  ch24: "spring-logging", ch25: "java-concurrency", ch26: "spring-di", ch27: "spring-boot-startup",
  ch28: "spring-controller", ch29: "spring-data-jpa", ch30: "java-encapsulation",
  ch31: "spring-reading-code", ch32: "java-jvm-memory", ch33: "java-nested-classes",
  ch34: "java-file-io", ch35: "spring-security",
};

// ---- ローカル版を読む ----
const chapters = [];
const sandbox = { JAVA_DOJO: { register: (c) => chapters.push(c) } };
vm.createContext(sandbox);
for (const f of fs.readdirSync(path.join(localDir, "data")).filter((f) => f.endsWith(".js")).sort()) {
  vm.runInContext(fs.readFileSync(path.join(localDir, "data", f), "utf8"), sandbox, { filename: f });
}
// 表示順(order)で並べる
chapters.sort((a, b) => a.order - b.order);

// ---- Sayana のレッスンの場所 ----
const lessonDirs = {};
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name === "lesson.yml") {
      const m = fs.readFileSync(p, "utf8").match(/^id:\s*(\S+)/m);
      if (m) lessonDirs[m[1]] = dir;
    }
  }
})(contentDir);

// ---- HTML(ローカル版の許可タグ)→ Markdown ----
const decode = (s) => s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"')
  .replace(/&#39;/g, "'").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&");
// 日本語の句読点・括弧で始まる/終わる強調は CommonMark では効かないので、そのときは強調を外す
const PUNCT = /^[\s「」『』()()、。,.:;:;!?!?・…—\-[\]【】]|[\s「」『』()()、。,.:;:;!?!?・…—\-[\]【】]$/u;
function md(html) {
  if (html == null) return "";
  let s = String(html);
  s = s.replace(/<code>([\s\S]*?)<\/code>/g, (_, c) => {
    const t = decode(c);
    return t.includes("`") ? "`` " + t + " ``" : "`" + t + "`";
  });
  s = s.replace(/<(strong|em)>([\s\S]*?)<\/\1>/g, (_, tag, inner) => {
    if (PUNCT.test(inner)) return inner;
    return tag === "strong" ? "**" + inner + "**" : "*" + inner + "*";
  });
  s = s.replace(/<br\s*\/?>/g, "\n");
  s = s.replace(/<li>([\s\S]*?)<\/li>/g, "\n- $1").replace(/<\/?(ul|ol)>/g, "\n");
  s = s.replace(/<[^>]+>/g, "");
  // コード片(`…`)の外だけ実体参照を戻す(コード片の中は上で戻し済み)
  s = s.split(/(`[^`]*`)/).map((part, i) => (i % 2 ? part : decode(part))).join("");
  return s.replace(/\n{3,}/g, "\n\n").trim();
}

// ---- YAML を書く(JSON 文字列は YAML の二重引用符スカラーとして読める) ----
const q = (s) => JSON.stringify(String(s));
function block(key, text, indent) {
  const pad = " ".repeat(indent);
  const lines = String(text).replace(/\r\n/g, "\n").replace(/\s+$/, "").split("\n");
  return pad + key + ": |\n" + lines.map((l) => (l ? pad + "  " + l : "")).join("\n") + "\n";
}
function scalar(key, text, indent) {
  return String(text).includes("\n") ? block(key, text, indent) : " ".repeat(indent) + key + ": " + q(text) + "\n";
}

const readingOut = {};
const atWorkOut = {};
const glossary = [];
const skipped = new Set();

for (const c of chapters) {
  const lessonId = MAP[c.id];
  if (!lessonId || !lessonDirs[lessonId]) {
    skipped.add(c.id + "→" + lessonId);
    continue;
  }
  for (const r of c.reading || []) {
    let y = "- title: " + q(r.title) + "\n";
    y += "  level: " + (r.level || 1) + "\n";
    y += block("code", r.code, 2);
    if (r.question) y += scalar("question", md(r.question), 2);
    y += "  choices:\n" + r.choices.map((ch) => "    - " + q(md(ch)) + "\n").join("");
    y += "  answer: " + r.answer + "\n";
    y += scalar("explanation", md(r.why), 2);
    if (r.trace && r.trace.length) y += "  trace:\n" + r.trace.map((t) => "    - " + q(md(t)) + "\n").join("");
    (readingOut[lessonId] ||= []).push(y);
  }
  if (c.atWork) {
    const a = c.atWork;
    let y = "- title: " + q(c.title) + "\n";
    y += scalar("intro", md(a.intro), 2);
    y += block("code", a.code, 2);
    const notes = Object.entries(a.notes || {});
    if (notes.length) y += "  notes:\n" + notes.map(([k, v]) => "    " + k + ": " + q(md(v)) + "\n").join("");
    if (a.points && a.points.length) y += "  points:\n" + a.points.map((p) => "    - " + q(md(p)) + "\n").join("");
    (atWorkOut[lessonId] ||= []).push(y);
  }
  for (const t of c.terms || []) {
    const key = t.term.trim();
    let y = "- term: " + q(key) + "\n";
    if (t.read) y += "  read: " + q(t.read) + "\n";
    if (t.en) y += "  en: " + q(t.en) + "\n";
    y += "  def: " + q(md(t.def)) + "\n";
    y += "  lesson: " + lessonId + "\n";
    glossary.push(y);
  }
}

const HEADER = "# ローカル版 Java道場から移した教材(scripts/import-local-dojo.mjs で生成。以後は手で直してよい)\n";
for (const [id, items] of Object.entries(readingOut)) {
  fs.writeFileSync(path.join(lessonDirs[id], "reading.yml"), HEADER + items.join(""), "utf8");
}
for (const [id, items] of Object.entries(atWorkOut)) {
  fs.writeFileSync(path.join(lessonDirs[id], "atwork.yml"), HEADER + items.join(""), "utf8");
}
fs.writeFileSync(path.join(contentDir, "glossary.yml"), HEADER + glossary.join(""), "utf8");

const n = (o) => Object.values(o).reduce((s, a) => s + a.length, 0);
console.log(`読解 ${n(readingOut)} 問 / 仕事のコード ${n(atWorkOut)} 件 / 用語 ${glossary.length} 語`);
if (skipped.size) console.log("移し先のレッスンが無いので飛ばした章: " + [...skipped].join(", "));
