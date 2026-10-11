package com.sayana.dojo.ai;

import com.sayana.dojo.content.ExerciseDef;
import com.sayana.dojo.content.Lesson;

/**
 * AI に渡す「共通の前置き + レッスン教材」の文字列を作る。
 * プロンプトキャッシュが効くよう、同じレッスンなら毎回まったく同じ文字列になること。
 * サーバー版(AiService)とスマホ版(MobileSiteGenerator)で同じものを使う。
 */
public final class LessonContext {

    private LessonContext() {
    }

    public static String build(String commonPrompt, Lesson lesson) {
        StringBuilder sb = new StringBuilder(commonPrompt).append("\n\n");
        if (lesson == null) {
            sb.append("<lesson>\n(特定のレッスンではなく、自由に試すプレイグラウンドからの依頼です)\n</lesson>");
            return sb.toString();
        }
        sb.append("<lesson id=\"").append(lesson.id()).append("\">\n");
        sb.append("# ").append(lesson.title()).append("\n\n");
        if (lesson.summary() != null) {
            sb.append(lesson.summary()).append("\n\n");
        }
        sb.append("## 解説\n").append(lesson.bodyMarkdown().strip()).append("\n\n");
        if (!lesson.exercises().isEmpty()) {
            sb.append("## このレッスンの演習(問題文のみ)\n");
            for (ExerciseDef e : lesson.exercises()) {
                sb.append("### ").append(e.title()).append("(クラス名: ").append(e.className()).append(")\n")
                        .append(e.problemMarkdown().strip()).append("\n\n");
            }
        }
        sb.append("</lesson>");
        return sb.toString();
    }
}
