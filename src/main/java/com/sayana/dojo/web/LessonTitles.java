package com.sayana.dojo.web;

import java.util.Map;
import java.util.stream.Collectors;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;

/** レッスンID → タイトル(一覧画面の表示用)。 */
final class LessonTitles {

    private LessonTitles() {
    }

    static Map<String, String> of(ContentRepository content) {
        return content.allLessons().stream().collect(Collectors.toMap(Lesson::id, Lesson::title));
    }
}
