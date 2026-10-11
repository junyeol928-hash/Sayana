package com.sayana.dojo.content;

import java.util.List;

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
        List<ExerciseDef> exercises) {

    public boolean ready() {
        return "ready".equals(status);
    }

    public boolean hasSts() {
        return sts != null;
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
}
