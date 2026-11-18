package com.sayana.dojo.progress;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

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

    /**
     * 次に出す復習。同じレッスンの問題を続けて出さず、いちばん長く復習していないレッスンの問題から出す(交互に混ぜる)。
     * 似た問題を続けて解くより、混ぜて解くほうが「どの知識を使う問題か」を見分ける力がつく。スマホ版の nextDue と同じ規則。
     *
     * @param due 期限の来た問題(期限の古い順)
     */
    @Transactional(readOnly = true)
    public Optional<ReviewItem> next(List<ReviewItem> due) {
        if (due.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Instant> lastByLesson = new HashMap<>();
        for (ReviewItem it : items.findAll()) {
            if (it.getLastReviewedAt() != null) {
                lastByLesson.merge(it.getLessonId(), it.getLastReviewedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        // 同じ古さなら期限の古い順(min は最初に見つかったものを残す)
        return due.stream().min(Comparator.comparing(it -> lastByLesson.getOrDefault(it.getLessonId(), Instant.EPOCH)));
    }

    /** 今日の復習の数と正解の数(同じ問題を 1 日に 2 回解いたら 1 問と数える)。 */
    @Transactional(readOnly = true)
    public int[] todayTally() {
        LocalDate today = LocalDate.now(clock);
        int n = 0;
        int ok = 0;
        for (ReviewItem it : items.findAll()) {
            if (it.getLastReviewedAt() != null && LocalDate.ofInstant(it.getLastReviewedAt(), clock.getZone()).equals(today)) {
                n++;
                if (Boolean.TRUE.equals(it.getLastCorrect())) {
                    ok++;
                }
            }
        }
        return new int[] {n, ok};
    }

    /** 今日答えた問題を、最後に答えた時刻の順に(区切りと終わりの画面で運針に並べ、間違えた問題を出す)。 */
    @Transactional(readOnly = true)
    public List<ReviewItem> todayItems() {
        LocalDate today = LocalDate.now(clock);
        return items.findAll().stream()
                .filter(it -> it.getLastReviewedAt() != null && LocalDate.ofInstant(it.getLastReviewedAt(), clock.getZone()).equals(today))
                .sorted(java.util.Comparator.comparing(ReviewItem::getLastReviewedAt))
                .toList();
    }

    /** 今日答えた問題の正誤を、最後に答えた時刻の順に。 */
    @Transactional(readOnly = true)
    public List<Boolean> todayResults() {
        return todayItems().stream().map(it -> Boolean.TRUE.equals(it.getLastCorrect())).toList();
    }

    /**
     * 選択肢を見せる並び。復習では位置で覚えてしまわないよう入れ替える。同じ問題は同じ日なら同じ並び(開き直しても変わらない)。
     */
    public List<Integer> choiceOrder(ReviewItem item, int size) {
        return shuffled(item.getItemRef() + "|" + LocalDate.now(clock), size);
    }

    /**
     * 解き直しの並び。数分前に「✓ 正解」を見たのと別の並びにし、正解の位置も必ず変える(位置で当てないように。スマホ版と同じ)。
     */
    public List<Integer> retryOrder(ReviewItem item, int size, int answer) {
        List<Integer> first = choiceOrder(item, size);
        for (int k = 1; k <= 6; k++) {
            List<Integer> o = shuffled(item.getItemRef() + "|" + LocalDate.now(clock) + "|retry" + k, size);
            if (o.indexOf(answer) != first.indexOf(answer)) {
                return o;
            }
        }
        List<Integer> reversed = new ArrayList<>(first);
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<Integer> shuffled(String key, int size) {
        List<Integer> order = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        Collections.shuffle(order, new Random(key.hashCode()));
        return order;
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> upcoming() {
        return items.findByGraduatedFalseAndDueDateGreaterThanOrderByDueDateAscIdAsc(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public long graduatedCount() {
        return items.countByGraduatedTrue();
    }

    /** 復習で答えたあとの「次は」。ボタンの横に置く短い文字(スマホ版の nextLabel と同じ)。 */
    public static String nextLabel(ReviewItem item) {
        if (item.isGraduated()) {
            return "卒業。もう出ません";
        }
        return "次は " + INTERVAL_DAYS[Math.min(item.getStage(), INTERVAL_DAYS.length - 1)] + " 日後";
    }

    /**
     * 今日の復習の目安(分)。区切りの単位(先頭の 10 問)で数える。クイズは 25 秒、演習の書き直しは 3 分
     * (スマホ版の reviewMinutes と同じ数え方。スマホには読解 45 秒もある)。
     */
    public static int firstBlockMinutes(List<ReviewItem> due) {
        int secs = due.stream().limit(10).mapToInt(it -> it.getItemType() == ReviewItem.Type.QUIZ ? 25 : 180).sum();
        return Math.max(1, Math.round(secs / 60f));
    }

    /** 次の復習までの日数の説明(演習の採点結果に出す)。 */
    public static String intervalLabel(ReviewItem item) {
        if (item.isGraduated()) {
            return "卒業(もう復習しなくてOK)";
        }
        return INTERVAL_DAYS[Math.min(item.getStage(), INTERVAL_DAYS.length - 1)] + "日後に復習";
    }
}
