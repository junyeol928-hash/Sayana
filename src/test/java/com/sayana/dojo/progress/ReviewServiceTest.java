package com.sayana.dojo.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.sayana.dojo.support.MutableClock;
import com.sayana.dojo.support.TestClockConfig;

@DataJpaTest
@Import({ReviewService.class, ActivityService.class, TestClockConfig.class})
class ReviewServiceTest {

    @Autowired
    ReviewService review;
    @Autowired
    ActivityService activity;
    @Autowired
    MutableClock clock;

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    @BeforeEach
    void sanity() {
        assertThat(review.due()).isEmpty();
    }

    @Test
    @DisplayName("初めて解いた問題は1日後→3日後→7日後→14日後と間隔が伸び、最後に卒業する")
    void intervalsGrowThenGraduate() {
        ReviewItem item = review.recordPractice(ReviewItem.Type.QUIZ, "java-hello#0", "java-hello", true);
        assertThat(item.getDueDate()).isEqualTo(today().plusDays(1));

        int[] expectedNext = {3, 7, 14};
        for (int next : expectedNext) {
            clock.advanceDays(LocalDate.now(clock).until(item.getDueDate()).getDays());
            assertThat(review.due()).extracting(ReviewItem::getId).contains(item.getId());
            item = review.recordReview(item.getId(), true);
            assertThat(item.getDueDate()).isEqualTo(today().plusDays(next));
            assertThat(review.due()).isEmpty();
        }
        clock.advanceDays(14);
        item = review.recordReview(item.getId(), true);
        assertThat(item.isGraduated()).isTrue();
        assertThat(review.due()).isEmpty();
        assertThat(review.graduatedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("復習で間違えると1日後からやり直し、つまずき回数が増える")
    void wrongAnswerResets() {
        ReviewItem item = review.recordPractice(ReviewItem.Type.EXERCISE, "java-if/ex1", "java-if", true);
        clock.advanceDays(1);
        item = review.recordReview(item.getId(), true);
        assertThat(item.getStage()).isEqualTo(1);
        clock.advanceDays(3);
        item = review.recordReview(item.getId(), false);
        assertThat(item.getStage()).isZero();
        assertThat(item.getLapses()).isEqualTo(1);
        assertThat(item.getDueDate()).isEqualTo(today().plusDays(1));
    }

    @Test
    @DisplayName("レッスン画面で間違えたら、キューにある問題もやり直しになる")
    void practiceMistakeResets() {
        ReviewItem item = review.recordPractice(ReviewItem.Type.QUIZ, "java-if#2", "java-if", true);
        clock.advanceDays(1);
        review.recordReview(item.getId(), true);
        item = review.recordPractice(ReviewItem.Type.QUIZ, "java-if#2", "java-if", false);
        assertThat(item.getStage()).isZero();
        assertThat(item.getLapses()).isEqualTo(1);
    }

    @Test
    @DisplayName("復習は同じレッスンを続けて出さず、長く復習していないレッスンから交互に出す。今日の数と正解も数える")
    void interleavesLessons() {
        ReviewItem a1 = review.recordPractice(ReviewItem.Type.QUIZ, "java-if#0", "java-if", true);
        ReviewItem a2 = review.recordPractice(ReviewItem.Type.QUIZ, "java-if#1", "java-if", true);
        ReviewItem b1 = review.recordPractice(ReviewItem.Type.QUIZ, "java-loops#0", "java-loops", true);
        clock.advanceDays(1);
        assertThat(review.next(review.due())).get().extracting(ReviewItem::getId).isEqualTo(a1.getId());
        review.recordReview(a1.getId(), true);
        assertThat(review.next(review.due())).as("java-if を解いた直後は java-loops").get()
                .extracting(ReviewItem::getId).isEqualTo(b1.getId());
        review.recordReview(b1.getId(), false);
        assertThat(review.next(review.due())).get().extracting(ReviewItem::getId).isEqualTo(a2.getId());
        assertThat(review.todayTally()).containsExactly(2, 1);
        assertThat(review.next(java.util.List.of())).isEmpty();
    }

    @Test
    @DisplayName("復習の選択肢の並びは入れ替わり、同じ日なら同じ並びのまま")
    void choiceOrderIsStableForTheDay() {
        ReviewItem item = review.recordPractice(ReviewItem.Type.QUIZ, "java-if#3", "java-if", true);
        java.util.List<Integer> order = review.choiceOrder(item, 4);
        assertThat(order).containsExactlyInAnyOrder(0, 1, 2, 3);
        assertThat(review.choiceOrder(item, 4)).isEqualTo(order);
        // 日が変われば並びも変わる(何日か進めれば、どこかで別の並びになる)
        java.util.Set<java.util.List<Integer>> seen = new java.util.HashSet<>();
        for (int d = 0; d < 10; d++) {
            seen.add(review.choiceOrder(item, 4));
            clock.advanceDays(1);
        }
        assertThat(seen).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("連続学習日数は今日(まだなら昨日)から遡って数える")
    void streak() {
        assertThat(activity.currentStreak()).isZero();
        activity.record();
        clock.advanceDays(1);
        activity.record();
        assertThat(activity.currentStreak()).isEqualTo(2);
        clock.advanceDays(1);
        assertThat(activity.currentStreak()).as("今日はまだでも昨日までの連続は数える").isEqualTo(2);
        clock.advanceDays(1);
        assertThat(activity.currentStreak()).isZero();
    }
}
