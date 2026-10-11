package com.sayana.dojo.content;

import java.util.List;

/** 章。 */
public record Chapter(String id, String partId, String title, List<Lesson> lessons) {
}
