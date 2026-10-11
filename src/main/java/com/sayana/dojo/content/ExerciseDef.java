package com.sayana.dojo.content;

/**
 * 演習1問の定義。教材由来(key = "lessonId/ex1")と AI生成の類題(key = "gen/123")の両方をこの形で扱う。
 */
public record ExerciseDef(
        String key,
        String lessonId,
        String title,
        String className,
        int difficulty,
        String problemMarkdown,
        String problemHtml,
        String starter,
        String solution,
        String test) {

    public static final String GENERATED_PREFIX = "gen/";

    public String testClassName() {
        return className + "Test";
    }

    public boolean generated() {
        return key.startsWith(GENERATED_PREFIX);
    }

    /** HTML の id 属性に使える形。 */
    public String domId() {
        return key.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    /** "lessonId/ex1" の "ex1" 部分。 */
    public String localId() {
        return key.substring(key.indexOf('/') + 1);
    }
}
