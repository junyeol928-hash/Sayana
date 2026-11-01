package com.sayana.dojo.content;

/**
 * 用語集の1語(content/glossary.yml)。lessonId はその用語を扱うレッスン(無ければ null)。
 */
public record Term(String term, String read, String en, String defHtml, String lessonId) {
}
