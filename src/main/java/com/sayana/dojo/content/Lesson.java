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
        List<AtWork> atWork) {

    public boolean ready() {
        return "ready".equals(status);
    }

    public boolean hasSts() {
        return sts != null;
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
    public record QuizQuestion(String questionMarkdown, String questionHtml, List<String> choices,
                               List<String> choicesHtml, int answer, String explanationHtml) {
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
                                  List<String> traceHtml) {
    }

    /**
     * 「仕事のコードではこう出てくる」(atwork.yml)。業務の Spring Boot コードで、この知識がどこに現れるか。
     * notes は行番号(1始まり)→その行の説明。
     */
    public record AtWork(String title, String introHtml, String code, Map<Integer, String> notesHtml,
                         List<String> pointsHtml) {
    }
}
