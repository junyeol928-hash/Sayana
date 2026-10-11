package com.sayana.dojo.content;

import java.util.List;

/** 部。 */
public record Part(String id, int order, String title, String description, List<Chapter> chapters) {

    public List<Lesson> lessons() {
        return chapters.stream().flatMap(c -> c.lessons().stream()).toList();
    }
}
