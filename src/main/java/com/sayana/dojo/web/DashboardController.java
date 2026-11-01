package com.sayana.dojo.web;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.sayana.dojo.ai.AiService;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.progress.ActivityService;
import com.sayana.dojo.progress.ProgressService;
import com.sayana.dojo.progress.ReviewService;
import com.sayana.dojo.progress.SubmissionRepository;

@Controller
public class DashboardController {

    private final ProgressService progress;
    private final ActivityService activity;
    private final ReviewService review;
    private final SubmissionRepository submissions;
    private final ContentRepository content;
    private final AiService ai;

    public DashboardController(ProgressService progress, ActivityService activity, ReviewService review,
                               SubmissionRepository submissions, ContentRepository content, AiService ai) {
        this.progress = progress;
        this.activity = activity;
        this.review = review;
        this.submissions = submissions;
        this.content = content;
        this.ai = ai;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var parts = progress.overview();
        model.addAttribute("parts", parts);
        model.addAttribute("nextLesson", progress.nextLesson().orElse(null));
        model.addAttribute("weakLessons", progress.weakLessons(5));
        model.addAttribute("days", activity.recentDays(56));
        model.addAttribute("studiedToday", activity.studiedToday());
        model.addAttribute("totalDays", activity.totalDays());
        model.addAttribute("upcoming", review.upcoming().size());
        model.addAttribute("graduated", review.graduatedCount());
        model.addAttribute("recentSubmissions", submissions.findTop50ByOrderByCreatedAtDesc().stream().limit(5).toList());
        model.addAttribute("completedLessons", parts.stream().mapToLong(ProgressService.PartProgress::completed).sum());
        model.addAttribute("readyLessons", content.readyLessons().size());
        model.addAttribute("totalLessons", content.allLessons().size());
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        // 染まり具合(公開レッスンを順に並べた帯と、段ごとの数)
        List<ProgressService.LessonStatus> flat = parts.stream().flatMap(p -> p.chapters().stream())
                .flatMap(c -> c.lessons().stream()).filter(s -> s.lesson().ready()).toList();
        int[] dyeCounts = new int[5];
        flat.forEach(s -> dyeCounts[s.dyeLevel()]++);
        model.addAttribute("flatLessons", flat);
        // 染まりの地図の行ごとの読み上げ(部の名前・数・段ごとの数)
        model.addAttribute("partMapLabels", parts.stream().map(p -> {
            List<ProgressService.LessonStatus> ls = p.chapters().stream().flatMap(c -> c.lessons().stream())
                    .filter(s -> s.lesson().ready()).toList();
            int[] n = new int[5];
            ls.forEach(s -> n[s.dyeLevel()]++);
            String counts = IntStream.range(0, 5).filter(i -> n[i] > 0)
                    .mapToObj(i -> DYE_MEANING[i] + " " + n[i]).collect(Collectors.joining("、"));
            return p.part().title() + ": " + ls.size() + " レッスン。" + counts + "。一覧へ";
        }).toList());
        // 前回の続きに差し替えるとき(static/js/app.js)に使う、レッスンごとの「次の段へ進む条件」
        model.addAttribute("dyeHints", flat.stream().collect(Collectors.toMap(st -> st.lesson().id(),
                st -> Objects.requireNonNullElse(nextDyeHint(st), ""))));
        model.addAttribute("dyeCounts", dyeCounts);
        model.addAttribute("deepCount", dyeCounts[2] + dyeCounts[3] + dyeCounts[4]);
        // 数字の欄はスマホ版とそろえる(次の復習・読解の正解はブラウザ側で数える・この 7 日の稽古)
        var upcomingItems = review.upcoming();
        model.addAttribute("nextReview", !review.due().isEmpty() ? "今日"
                : upcomingItems.isEmpty() ? "なし" : upcomingItems.getFirst().getDueDate().format(DateTimeFormatter.ofPattern("M/d")));
        model.addAttribute("weekDays", activity.recentDays(7).stream().filter(d -> d.actions() > 0).count());
        model.addAttribute("readingTotal", content.readyLessons().stream().mapToInt(l -> l.reading().size()).sum());
        progress.nextLesson().ifPresent(next -> {
            flat.stream().filter(s -> s.lesson().id().equals(next.id())).findFirst().ifPresent(s -> {
                model.addAttribute("nextDye", s.dyeLevel());
                model.addAttribute("nextDyeName", s.dyeLabel().split("・")[0]);
                model.addAttribute("nextDyeRead", DYE_READ[s.dyeLevel()]);
                model.addAttribute("nextDyeMeaning", s.dyeLabel().split("・")[1]);
                model.addAttribute("nextHint", nextDyeHint(s));
            });
            model.addAttribute("nextIndex", content.readyLessons().indexOf(next) + 1);
            model.addAttribute("nextTitleSize", titleSize(next.title()));
            content.chapter(next.chapterId()).ifPresent(c -> {
                model.addAttribute("nextAxisChapter", kanjiOrdinal(c.title()));
                content.part(c.partId()).ifPresent(p -> model.addAttribute("nextAxisPart", kanjiOrdinal(p.title())));
            });
            // 所属はスマホ版と同じ「部・章」(部の「第n部」は省く)
            model.addAttribute("nextChapter", content.chapter(next.chapterId()).map(c -> content.part(c.partId())
                    .map(p -> p.title().replaceFirst("^第\\d+部\\s*", "") + "・").orElse("") + c.title()).orElse(""));
        });
        // 前回の続きに差し替えるとき(app.js)に使う、縦書きの軸の文字
        model.addAttribute("axisLabels", content.readyLessons().stream().collect(Collectors.toMap(Lesson::id,
                l -> content.chapter(l.chapterId()).map(c -> content.part(c.partId()).map(p -> kanjiOrdinal(p.title()))
                        .orElse("") + "|" + kanjiOrdinal(c.title())).orElse("|"))));
        return "dashboard";
    }

    private static final Pattern ORDINAL = Pattern.compile("第(\\d+)");
    private static final String[] KANJI_DIGIT = {"〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    /** 縦書きの軸では数字を漢数字に(「第3部」→「第三部」)。スマホ版 app.js の kanjiOrdinal と同じ。 */
    public static String kanjiOrdinal(String text) {
        Matcher m = ORDINAL.matcher(text);
        if (!m.find()) {
            return text;
        }
        int n = Integer.parseInt(m.group(1));
        String k = n < 10 ? KANJI_DIGIT[n] : n < 20 ? "十" + (n % 10 > 0 ? KANJI_DIGIT[n % 10] : "") : String.valueOf(n);
        return text.substring(0, m.start()) + "第" + k + text.substring(m.end());
    }

    /** 題の長さで見出しの大きさを 3 段に分ける。英数字は和字の約半分の幅で数える。スマホ版 app.js の titleSize と同じ。 */
    public static String titleSize(String title) {
        double width = title.codePoints().mapToDouble(c -> c < 0x2000 ? 0.55 : 1).sum();
        return width <= 8 ? "size-xl" : width <= 14 ? "size-l" : "size-m";
    }

    /** 染めの名前の読み(スマホ版 app.js の DYE の read と同じ)。ヒーローでふりがなに使う。 */
    static final String[] DYE_READ = {"きなり", "かめのぞき", "はなだ", "あい", "かち"};

    /** 段の意味(地図の読み上げ用。スマホ版 app.js の DYE の meaning と同じ)。 */
    static final String[] DYE_MEANING = {"まだ", "読んだ", "クイズ合格", "演習もできた", "復習をすべて卒業"};

    /** 次の段へ進む条件(スマホ版 app.js の nextDyeHint と同じ文言)。 */
    public static String nextDyeHint(ProgressService.LessonStatus s) {
        return switch (s.dyeLevel()) {
            case 0 -> "読み終えると甕覗きに";
            case 1 -> "クイズで " + s.quizPassScore() + " 問以上取ると縹に";
            case 2 -> "演習ができたら藍に";
            case 3 -> "復習をすべて卒業すると褐に";
            default -> null;
        };
    }

    /** 教材を読み直す(教材を書きながら確認する用)。 */
    @PostMapping("/content/reload")
    public String reload() {
        content.reload();
        ai.clearContextCache();
        return "redirect:/";
    }
}
