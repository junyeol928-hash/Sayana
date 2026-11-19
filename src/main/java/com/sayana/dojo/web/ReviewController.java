package com.sayana.dojo.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.progress.ExerciseCatalog;
import com.sayana.dojo.progress.LearningService;
import com.sayana.dojo.progress.ReviewItem;
import com.sayana.dojo.progress.ReviewItemRepository;
import com.sayana.dojo.progress.ReviewService;

/** 復習キュー。期限が来たものを1問ずつ出す。 */
@Controller
public class ReviewController {

    private final ReviewService review;
    private final ReviewItemRepository items;
    private final ContentRepository content;
    private final ExerciseCatalog catalog;
    private final ExerciseViews views;
    private final LearningService learning;
    private final java.time.Clock clock;

    public ReviewController(ReviewService review, ReviewItemRepository items, ContentRepository content,
                            ExerciseCatalog catalog, ExerciseViews views, LearningService learning, java.time.Clock clock) {
        this.clock = clock;
        this.review = review;
        this.items = items;
        this.content = content;
        this.catalog = catalog;
        this.views = views;
        this.learning = learning;
    }

    /**
     * 解き直しの状態(今日の分だけ。セッションに持つ)。refs はこの回に解き直す問、done はこの回に答えた数、
     * results はどの回も通した問ごとの正誤(終わりの運針の仮縫いと、行の「仮縫いできた / まだ」)、pass は何回目の解き直しか。
     * URL にだけ持つと、終わりの画面の「読み直す」で本文へ行って戻ると跡が消えた(24 回目の審査)
     */
    static final class RetryState implements java.io.Serializable {
        final LocalDate day;
        final List<Long> refs;
        final Map<Long, Boolean> results;
        final int pass;
        int done;

        RetryState(LocalDate day, List<Long> refs, Map<Long, Boolean> results, int pass) {
            this.day = day;
            this.refs = List.copyOf(refs);
            this.results = new java.util.HashMap<>(results);
            this.pass = pass;
        }

        boolean finished() {
            return done >= refs.size();
        }
    }

    static final String RETRY_KEY = "dojo.review.retry";

    @GetMapping("/review")
    public String queue(@RequestParam(name = "go", defaultValue = "-1") int goOnAt,
                        @RequestParam(name = "retry", defaultValue = "-1") int retryAt,
                        @RequestParam(name = "still", defaultValue = "0") int stillFrom,
                        @RequestParam(name = "r", required = false) String retryResults,
                        jakarta.servlet.http.HttpSession session, Model model) {
        List<ReviewItem> due = review.due().stream().filter(this::stillExists).toList();
        model.addAttribute("due", due);
        List<ReviewItem> upcoming = review.upcoming().stream().filter(this::stillExists).limit(30).toList();
        model.addAttribute("upcomingItems", upcoming);
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        model.addAttribute("tomorrowDue", review.upcoming().stream().filter(it -> tomorrow.equals(it.getDueDate())).filter(this::stillExists).count());
        model.addAttribute("graduated", review.graduatedCount());
        Map<String, String> titles = LessonTitles.of(content);
        model.addAttribute("lessonTitles", titles);
        model.addAttribute("upcomingRows", upcomingRows(upcoming, titles));
        int[] tally = review.todayTally();
        model.addAttribute("todayCount", tally[0]);
        model.addAttribute("todayCorrect", tally[1]);
        // 10 問ごとに区切りを出す(残りが多い日でも、ここで止めてよいと分かるように)。「続ける」で ?go=今日の数
        List<ReviewItem> today = review.todayItems();
        if (tally[0] > 0 && tally[0] % 10 == 0 && goOnAt != tally[0] && !due.isEmpty()) {
            List<ReviewItem> block = today.subList(Math.max(0, today.size() - 10), today.size());
            List<Boolean> results = block.stream().map(it -> Boolean.TRUE.equals(it.getLastCorrect())).toList();
            model.addAttribute("breakHere", true);
            model.addAttribute("breakBlock", results);
            model.addAttribute("breakMiss", results.stream().filter(ok -> !ok).count());
            model.addAttribute("missed", missed(block, Map.of()));
            return "review";
        }
        if (due.isEmpty()) {
            // 解き直し: 今日まちがえたクイズを、その日のうちにもう一度(記録せず、明日の予定も変えない。スマホ版と同じ)。
            // ?retry=n で n 問目。並びは今日答えた順で、解き直しでは記録が変わらないので番号はずれない。
            // ?r=101 は解き直した問ごとの正誤(終わりの運針で、正しく解けた × に仮縫いの破線を重ね、行に「仮縫いできた / まだ」)
            List<ReviewItem> again = today.stream().filter(it -> !Boolean.TRUE.equals(it.getLastCorrect()))
                    .filter(it -> it.getItemType() == ReviewItem.Type.QUIZ).filter(this::stillExists).toList();
            LocalDate day = LocalDate.now(clock);
            RetryState state = session.getAttribute(RETRY_KEY) instanceof RetryState s && s.day.equals(day) ? s : null;
            if (state != null && !again.stream().map(ReviewItem::getId).collect(java.util.stream.Collectors.toSet()).containsAll(state.refs)) {
                // 解き直す問が消えた(教材の差し替えなど)。消えた問を開くと落ちるので、今日の解き直しは始めから
                session.removeAttribute(RETRY_KEY);
                state = null;
            }
            if (retryResults != null && !again.isEmpty()) {
                // 問ごとの正誤を URL で渡したとき(撮影とテスト用): 1 回目の解き直しを終えた状態にする。
                // 解き直す問が無い日は無視する(「解き直した 0 問は、まだ」と「ホームへ」が 2 つ並んだ。30 回目の撮影)
                String r = retryResults.replaceAll("[^01]", "");
                r = r.substring(0, Math.min(r.length(), again.size()));
                Map<Long, Boolean> given = new java.util.HashMap<>();
                for (int i = 0; i < r.length(); i++) {
                    given.put(again.get(i).getId(), r.charAt(i) == '1');
                }
                state = new RetryState(day, again.stream().map(ReviewItem::getId).toList(), given, 1);
                state.done = state.refs.size();
            } else if (stillFrom > 0 && state != null && state.finished() && state.pass == stillFrom) {
                // まだの問題を、もう一度(何回目かを 1 つ進める)。押した画面の回(?still=何回目)と今の回が同じときだけ進め、
                // 戻る・再読み込みで同じ URL を開いても、次の回へ勝手に進まない
                RetryState prev = state;
                List<Long> stillIds = prev.refs.stream().filter(id -> Boolean.FALSE.equals(prev.results.get(id))).toList();
                if (!stillIds.isEmpty()) {
                    state = new RetryState(day, stillIds, prev.results, prev.pass + 1);
                }
            } else if (retryAt >= 0 && state == null && !again.isEmpty()) {
                state = new RetryState(day, again.stream().map(ReviewItem::getId).toList(), Map.of(), 1);
            }
            if (state != null) {
                session.setAttribute(RETRY_KEY, state);
            }
            Map<Long, Boolean> retried = state == null ? Map.of() : state.results;
            // 今日の終わり: 区切りと同じ部品で、今日答えた全問を運針に(10 目で 1 行)、間違えた問題と読み直す所
            List<String> marks = today.stream().map(it -> Boolean.TRUE.equals(it.getLastCorrect()) ? "sewn"
                    : Boolean.TRUE.equals(retried.get(it.getId())) ? "miss basted" : "miss").toList();
            java.util.List<List<String>> rows = new java.util.ArrayList<>();
            for (int i = 0; i < marks.size(); i += 10) {
                rows.add(marks.subList(i, Math.min(marks.size(), i + 10)));
            }
            model.addAttribute("doneRows", rows);
            model.addAttribute("missed", missed(today, retried));
            model.addAttribute("againCount", again.size());
            model.addAttribute("retryStarted", state != null);
            if (state != null) {
                RetryState st = state;
                model.addAttribute("retryCount", st.refs.size());
                model.addAttribute("retryPass", st.pass);
                model.addAttribute("retryDone", st.finished());
                model.addAttribute("retryResume", Math.min(st.done, st.refs.size() - 1)); // 「解き直しの続きへ」は答えた次の問から
                model.addAttribute("retryOk", st.refs.stream().filter(id -> Boolean.TRUE.equals(st.results.get(id))).count());
                model.addAttribute("bastedToday", again.stream().filter(it -> Boolean.TRUE.equals(st.results.get(it.getId()))).count());
                model.addAttribute("retryStill", st.refs.stream().filter(id -> Boolean.FALSE.equals(st.results.get(id))).count());
            }
            if (state != null && retryAt >= 0 && retryAt < state.refs.size()) {
                ReviewItem item = items.findById(state.refs.get(retryAt)).orElseThrow();
                Lesson.QuizQuestion q = content.lesson(item.getLessonId()).orElseThrow().quiz().get(item.quizIndex());
                model.addAttribute("item", item);
                model.addAttribute("retryAt", retryAt);
                model.addAttribute("question", q);
                // 数分前と別の並び(正解の位置も変える)。位置の記憶で当てないように
                model.addAttribute("choiceOrder", review.retryOrder(item, q.choicesHtml().size(), q.answer(), state.pass));
                return "review";
            }
        }
        review.next(due).ifPresent(item -> {
            model.addAttribute("item", item);
            Lesson lesson = content.lesson(item.getLessonId()).orElse(null);
            model.addAttribute("itemLesson", lesson);
            if (item.getItemType() == ReviewItem.Type.QUIZ) {
                Lesson.QuizQuestion q = lesson.quiz().get(item.quizIndex());
                model.addAttribute("question", q);
                model.addAttribute("choiceOrder", review.choiceOrder(item, q.choicesHtml().size()));
            } else {
                model.addAttribute("view", views.forReview(catalog.find(item.getItemRef()).orElseThrow(), item.getId()));
            }
        });
        return "review";
    }

    @PostMapping("/review/{id}/quiz")
    public String answerQuiz(@PathVariable long id, @RequestParam(defaultValue = "-1") int choice,
                             @RequestParam(defaultValue = "-1") int retry, @RequestParam(defaultValue = "0") int total,
                             jakarta.servlet.http.HttpSession session, Model model) {
        ReviewItem item = items.findById(id).orElseThrow();
        Lesson lesson = content.lesson(item.getLessonId()).orElseThrow();
        if (retry >= 0) {
            // 解き直しは採点だけ(記録・予定・今日の数は変えない)。正しく解けた数は次の画面へ渡して、終わりに言う
            Lesson.QuizQuestion q = lesson.quiz().get(item.quizIndex());
            boolean ok = q.answer() == choice;
            model.addAttribute("correct", ok);
            model.addAttribute("choice", choice);
            model.addAttribute("question", q);
            model.addAttribute("quizIndex", item.quizIndex());
            model.addAttribute("lesson", lesson);
            model.addAttribute("retryNext", retry + 1);
            if (session.getAttribute(RETRY_KEY) instanceof RetryState st && st.day.equals(LocalDate.now(clock))
                    && st.refs.contains(item.getId())) {
                st.results.put(item.getId(), ok);
                st.done = Math.max(st.done, retry + 1);
                session.setAttribute(RETRY_KEY, st);
            }
            model.addAttribute("retryLast", retry + 1 >= total);
            model.addAttribute("reviewLabel", "明日の復習で、もう一度");
            return "fragments/review-parts :: quizAnswer";
        }
        boolean correct = learning.answerReviewQuiz(item, choice);
        ReviewItem updated = items.findById(id).orElseThrow();
        model.addAttribute("correct", correct);
        model.addAttribute("choice", choice);
        model.addAttribute("question", lesson.quiz().get(item.quizIndex()));
        model.addAttribute("quizIndex", item.quizIndex());
        model.addAttribute("reviewLabel", ReviewService.nextLabel(updated));
        model.addAttribute("graduatedNow", updated.isGraduated());
        model.addAttribute("nextDays", ReviewService.INTERVAL_DAYS[Math.min(updated.getStage(), ReviewService.INTERVAL_DAYS.length - 1)]);
        model.addAttribute("nextStep", Math.min(updated.getStage(), ReviewService.INTERVAL_DAYS.length - 1) + 1);
        model.addAttribute("lesson", lesson);
        // 上の「残り n 問・今日 n / 10」も答えた分だけ進める(hx-swap-oob で差し替える)
        model.addAttribute("dueLeft", review.due().stream().filter(this::stillExists).count());
        model.addAttribute("todayCount", review.todayTally()[0]);
        return "fragments/review-parts :: quizAnswer";
    }

    /**
     * 間違えた問題の行。レッスン名と、どう読み違えたか(選んだ誤答の why の 1 文目。why が無い演習や、選んだ答えの記録が無い分は
     * 問いの書き出し)、読み直す先(その問いそのもの)。retry は解き直しの結果(正しく解けた / まだ。解き直していなければ null)。
     * 3 件まで(ほかは数だけ画面に出す)。
     */
    public record Missed(String lessonId, String lessonTitle, String text, boolean why, String anchor, Boolean retry) {
    }

    private List<Missed> missed(List<ReviewItem> answered, Map<Long, Boolean> retried) {
        return answered.stream().filter(it -> !Boolean.TRUE.equals(it.getLastCorrect())).filter(this::stillExists).map(it -> {
            Lesson lesson = content.lesson(it.getLessonId()).orElseThrow();
            if (it.getItemType() != ReviewItem.Type.QUIZ) {
                String title = catalog.find(it.getItemRef()).map(e -> e.title()).orElse("");
                return new Missed(lesson.id(), lesson.title(), clip(title, 30), false, "#write", null);
            }
            Lesson.QuizQuestion q = lesson.quiz().get(it.quizIndex());
            String anchor = "#quiz-q" + it.quizIndex();
            Integer c = it.getLastChoice();
            if (c != null && c >= 0 && c < q.whyHtml().size() && !q.whyHtml().get(c).isBlank()) {
                // why の 1 文目を HTML のまま(<code> だけ残す)。`i += 3` のようなコードの書体を落とさない
                String why = q.whyHtml().get(c).split("。")[0].replaceAll("<(?!/?code\\b)[^>]*>", "");
                return new Missed(lesson.id(), lesson.title(), why, true, anchor, retried.get(it.getId()));
            }
            String text = q.questionMarkdown().replaceAll("(?s)```.*?```", " ").replace("`", "").replace("**", "");
            return new Missed(lesson.id(), lesson.title(), clip(text, 30), false, anchor, retried.get(it.getId()));
        }).toList();
    }

    /**
     * この先の予定の 1 行。同じ日の同じレッスンは 1 行にまとめ(同じ行が 4 つ並んでいた)、段階は段階ごとに小さな運針 1 つと数
     * (stages は段階 0〜3 ごとの問題の数)。
     */
    public record UpcomingRow(LocalDate dueDate, String lessonTitle, long count, long exercises, List<long[]> stages) {
    }

    /** 予定の 1 日分(見出しの行「11/20 · 8 問」と、その日のレッスンの行)。 */
    public record UpcomingDay(LocalDate date, long count, List<UpcomingRow> rows) {
    }

    static List<UpcomingDay> upcomingRows(List<ReviewItem> upcoming, Map<String, String> titles) {
        Map<String, List<ReviewItem>> groups = new LinkedHashMap<>();
        for (ReviewItem it : upcoming) {
            groups.computeIfAbsent(it.getDueDate() + "|" + it.getLessonId(), k -> new ArrayList<>()).add(it);
        }
        List<UpcomingRow> rows = groups.values().stream().map(g -> new UpcomingRow(g.getFirst().getDueDate(),
                titles.getOrDefault(g.getFirst().getLessonId(), g.getFirst().getLessonId()), g.size(),
                g.stream().filter(it -> it.getItemType() != ReviewItem.Type.QUIZ).count(),
                java.util.stream.IntStream.rangeClosed(0, 3).mapToObj(s -> new long[] {s,
                        g.stream().filter(it -> Math.min(it.getStage(), 3) == s).count()}).filter(sc -> sc[1] > 0).toList())).toList();
        Map<LocalDate, List<UpcomingRow>> days = new LinkedHashMap<>();
        rows.forEach(r -> days.computeIfAbsent(r.dueDate(), k -> new ArrayList<>()).add(r));
        return days.entrySet().stream().map(e -> new UpcomingDay(e.getKey(),
                e.getValue().stream().mapToLong(UpcomingRow::count).sum(), e.getValue())).toList();
    }

    private static String clip(String text, int max) {
        String t = text.replaceAll("\\s+", " ").strip();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    /** 教材から消えた問題は出さない。 */
    private boolean stillExists(ReviewItem item) {
        if (item.getItemType() == ReviewItem.Type.QUIZ) {
            return content.lesson(item.getLessonId()).map(l -> item.quizIndex() < l.quiz().size()).orElse(false);
        }
        return catalog.find(item.getItemRef()).isPresent();
    }
}
