package com.sayana.dojo.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * レッスン。status が planned のものは章立てだけで、本文・演習などは空。
 */
public record Lesson(
        String id,
        String partId,
        String chapterId,
        String title,
        String status,
        int minutes,
        String summary,
        List<String> goals,
        String bodyMarkdown,
        String bodyHtml,
        List<Sample> samples,
        List<QuizQuestion> quiz,
        List<Column> columns,
        StsGuide sts,
        List<ChecklistItem> checklist,
        List<ExerciseDef> exercises,
        List<ReadingQuestion> reading,
        List<AtWork> atWork,
        List<Check> checks) {

    public boolean ready() {
        return "ready".equals(status);
    }

    public boolean hasSts() {
        return sts != null;
    }

    /** ゴールの文(地の文はエスケープし、`…` だけコードの書体に。コードの語を本文と同じ書体で出す) */
    public List<String> goalsHtml() {
        return goals.stream().map(g -> org.springframework.web.util.HtmlUtils.htmlEscape(g).replaceAll("`([^`]+)`", "<code>$1</code>")).toList();
    }

    /** レッスンにある段(読む → 仕事のコード → サンプル → クイズ → 演習 → 読解 → STSで試す)。レッスン画面の段の並びと同じ。 */
    public List<String> stepKeys() {
        List<String> keys = new ArrayList<>(List.of("read"));
        if (!atWork.isEmpty()) {
            keys.add("atwork");
        }
        if (!samples.isEmpty()) {
            keys.add("run");
        }
        keys.add("quiz");
        if (!exercises.isEmpty()) {
            keys.add("write");
        }
        if (!reading.isEmpty()) {
            keys.add("reading");
        }
        if (hasSts() || !checklist.isEmpty()) {
            keys.add("sts");
        }
        return keys;
    }

    /** 1つのサンプル。libraries に "spring" などがあると、そのライブラリ入りの JShell で実行する。 */
    public record Sample(String title, String descriptionHtml, String code, List<String> libraries) {
    }

    /** 選択式クイズ1問。answer は 0 始まり。 */
    /** クイズの 1 問。whyHtml は選択肢と同じ並びで、選んだ誤答に向けた 1 文(正解の所は空。書いていなければ空のリスト)。 */
    public record QuizQuestion(String questionMarkdown, String questionHtml, List<String> choices,
                               List<String> choicesHtml, int answer, String explanationHtml, List<String> whyHtml) {
    }

    /** コラム。type は trivia / genba / mistake。 */
    public record Column(String type, String title, String bodyHtml) {

        public String label() {
            return switch (type) {
                case "genba" -> "現場あるある";
                case "mistake" -> "よくあるミス";
                default -> "豆知識";
            };
        }
    }

    /** STSでやってみよう。 */
    public record StsGuide(String introHtml, List<StsStep> steps) {
    }

    public record StsStep(String title, String detailHtml) {
    }

    /** 画面上で実行できない作業の「やった」チェック。 */
    public record ChecklistItem(String key, String text) {
    }

    /**
     * コード読解問題(reading.yml)。コードを読んで出力や挙動を当てる。
     * trace は「1行ずつ何が起きるか」の答え合わせ。level は 1=やさしい 2=ふつう 3=むずかしい。
     */
    public record ReadingQuestion(String title, int level, String code, String questionHtml,
                                  List<String> choicesHtml, int answer, String explanationHtml,
                                  List<String> traceHtml, List<String> whyHtml) {
    }

    /**
     * 「仕事のコードではこう出てくる」(atwork.yml)。業務の Spring Boot コードで、この知識がどこに現れるか。
     * notes は行番号(1始まり)→その行の説明。
     */
    public record AtWork(String title, String introHtml, String code, Map<Integer, String> notesHtml,
                         List<String> pointsHtml) {
    }

    /**
     * 「手を止めて」(checks.yml)。本文の途中に置く 1 問(読みながら思い出す練習)。
     * 置き場所は after(その ## 節の終わり)か before(書き出しがその文字の塊の直前。答えを見せる前に予想させる)。
     * hides は before のとき、答えるまで畳む後ろの塊の数(答えが同じ画面に見えないように)。
     * whyHtml は選択肢と同じ並びで、選んだ誤答に向けた 1 文(正解の所は空)。本文の HTML には描画済みのものが入っている。
     */
    public record Check(String after, String before, int hides, String questionHtml, List<String> choicesHtml, int answer,
                        String explanationHtml, List<String> whyHtml) {
    }
}
