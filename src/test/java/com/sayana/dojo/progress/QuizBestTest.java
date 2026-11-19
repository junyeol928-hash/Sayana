package com.sayana.dojo.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizBestTest {

    private static QuizAttempt at(LocalDate day, int hour, int score) {
        return new QuizAttempt("java-if", score, 5, "", day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static final LocalDate DAY = ProgressService.FIRST_ATTEMPT_RULE_FROM.plusDays(3);

    @Test
    @DisplayName("最高点は各日の最初の採点だけで数える(答えを見た直後の解き直しは数えない)")
    void onlyFirstAttemptOfEachDayCounts() {
        assertThat(ProgressService.bestOfFirstAttempts(List.of(at(DAY, 9, 2), at(DAY, 10, 5)))).isEqualTo(2);
        assertThat(ProgressService.bestOfFirstAttempts(List.of(at(DAY, 9, 2), at(DAY, 10, 5), at(DAY.plusDays(1), 8, 4))))
                .as("翌日の最初の採点は数える").isEqualTo(4);
    }

    @Test
    @DisplayName("規則を入れる前の記録は全部数え、記録が無ければ null")
    void attemptsBeforeTheRuleAllCount() {
        LocalDate before = ProgressService.FIRST_ATTEMPT_RULE_FROM.minusDays(1);
        assertThat(ProgressService.bestOfFirstAttempts(List.of(at(before, 9, 2), at(before, 10, 5)))).isEqualTo(5);
        assertThat(ProgressService.bestOfFirstAttempts(List.of(at(before, 9, 5), at(DAY, 9, 3)))).isEqualTo(5);
        assertThat(ProgressService.bestOfFirstAttempts(List.of())).isNull();
    }
}
