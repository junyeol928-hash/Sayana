package com.sayana.dojo.progress;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 復習キュー(間隔反復)。
 * <p>
 * クイズや演習に初めて取り組むと、その問題が「1日後」にキューへ入る。
 * 復習で正解するたびに 1日 → 3日 → 7日 → 14日 と間隔が伸び、14日後の復習にも正解したら卒業。
 * 間違えたら(レッスン画面で間違えた場合も)1日後からやり直し。
 */
@Service
@Transactional
public class ReviewService {

    /** stage ごとの「次の復習までの日数」。 */
    public static final int[] INTERVAL_DAYS = {1, 3, 7, 14};

    private final ReviewItemRepository items;
    private final Clock clock;

    public ReviewService(ReviewItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    /** レッスン画面での結果を記録する(復習キューに入れる/間違えたらやり直し)。 */
    public ReviewItem recordPractice(ReviewItem.Type type, String ref, String lessonId, boolean correct) {
        LocalDate today = LocalDate.now(clock);
        ReviewItem item = items.findByItemTypeAndItemRef(type, ref).orElse(null);
        if (item == null) {
            item = new ReviewItem(type, ref, lessonId, today.plusDays(INTERVAL_DAYS[0]), Instant.now(clock));
            item.setLastCorrect(correct);
            if (!correct) {
                item.setLapses(1);
            }
            return items.save(item);
        }
        if (!correct) {
            reset(item, today);
        }
        item.setLastCorrect(correct);
        return item;
    }

    /** 復習キューでの解答結果を反映する。 */
    public ReviewItem recordReview(long itemId, boolean correct) {
        ReviewItem item = items.findById(itemId).orElseThrow();
        LocalDate today = LocalDate.now(clock);
        item.setReviewCount(item.getReviewCount() + 1);
        item.setLastReviewedAt(Instant.now(clock));
        item.setLastCorrect(correct);
        if (!correct) {
            reset(item, today);
            return item;
        }
        int next = item.getStage() + 1;
        if (next >= INTERVAL_DAYS.length) {
            item.setGraduated(true);
            item.setStage(INTERVAL_DAYS.length);
        } else {
            item.setStage(next);
            item.setDueDate(today.plusDays(INTERVAL_DAYS[next]));
        }
        return item;
    }

    private static void reset(ReviewItem item, LocalDate today) {
        item.setStage(0);
        item.setGraduated(false);
        item.setDueDate(today.plusDays(INTERVAL_DAYS[0]));
        item.setLapses(item.getLapses() + 1);
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> due() {
        return items.findByGraduatedFalseAndDueDateLessThanEqualOrderByDueDateAscIdAsc(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> upcoming() {
        return items.findByGraduatedFalseAndDueDateGreaterThanOrderByDueDateAscIdAsc(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public long graduatedCount() {
        return items.countByGraduatedTrue();
    }

    /** 次の復習までの日数の説明(画面表示用)。 */
    public static String intervalLabel(ReviewItem item) {
        if (item.isGraduated()) {
            return "卒業(もう復習しなくてOK)";
        }
        return INTERVAL_DAYS[Math.min(item.getStage(), INTERVAL_DAYS.length - 1)] + "日後に復習";
    }
}
