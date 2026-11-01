package com.sayana.dojo.web;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.content.Term;

/**
 * 全レッスン横断の「読解」と「用語集」。答え合わせはブラウザの中(static/js/app.js)で行う。
 */
@Controller
public class StudyController {

    private final ContentRepository content;

    public StudyController(ContentRepository content) {
        this.content = content;
    }

    /** レッスンごとの読解問題。 */
    public record LessonReading(Lesson lesson, List<Lesson.ReadingQuestion> questions) {
    }

    @GetMapping("/reading")
    public String reading(Model model) {
        List<LessonReading> groups = content.readyLessons().stream().filter(l -> !l.reading().isEmpty())
                .map(l -> new LessonReading(l, l.reading())).toList();
        model.addAttribute("groups", groups);
        model.addAttribute("total", groups.stream().mapToInt(g -> g.questions().size()).sum());
        return "reading";
    }

    private static final String[] KANA_ROWS = {"あいうえおぁぃぅぇぉゔ", "かきくけこがぎぐげご", "さしすせそざじずぜぞ",
            "たちつてとだぢづでどっ", "なにぬねの", "はひふへほばびぶべぼぱぴぷぺぽ", "まみむめも", "やゆよゃゅょ", "らりるれろ", "わをん"};

    /** 読みの最初の文字から「あ行」「か行」…の見出しを決める(スマホ版の app.js の kanaRow と同じ)。 */
    static String kanaRow(Term t) {
        // 英字で始まる語(equals・Iterator・@Bean など)は読みではなく「A-Z」に(索引の A-Z から探せるように)
        if (!t.term().isBlank() && (t.term().strip().charAt(0) < 0x80 && Character.isLetter(t.term().strip().charAt(0)) || t.term().strip().charAt(0) == '@')) {
            return "A-Z";
        }
        String s = (t.read() != null ? t.read() : t.term()).strip();
        char c = s.isEmpty() ? ' ' : s.charAt(0);
        char hira = c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c;
        for (int i = 0; i < KANA_ROWS.length; i++) {
            if (KANA_ROWS[i].indexOf(hira) >= 0) {
                return "あかさたなはまやらわ".charAt(i) + "行";
            }
        }
        return Character.isLetter(c) && c < 0x80 || c == '@' ? "A-Z" : "その他";
    }

    /** 用語集の 1 枚。いくつかのレッスンに出る語は、レッスンごとの説明をまとめて持つ。 */
    public record TermCard(String row, List<Term> terms) {
        public Term first() {
            return terms.getFirst();
        }

        public String en() {
            // 英語が見出しと同じ綴りなら繰り返さない(「final(クラス・メソッド)」の括弧の前とも比べる)
            String base = first().term().replaceFirst("[((].*$", "").strip();
            return terms.stream().map(Term::en)
                    .filter(e -> e != null && !e.isBlank() && !e.equalsIgnoreCase(first().term()) && !e.equalsIgnoreCase(base))
                    .findFirst().orElse(null);
        }
    }

    @GetMapping("/glossary")
    public String glossary(Model model) {
        // 読み順に並べ、同じ語(大文字小文字は区別しない)を 1 枚にまとめる(スマホ版の renderGlossary と同じ)
        Collator ja = Collator.getInstance(Locale.JAPANESE);
        Map<String, List<Term>> byTerm = new LinkedHashMap<>();
        content.glossary().stream()
                .sorted(Comparator.comparing((Term t) -> t.read() != null ? t.read() : t.term(), ja))
                .forEach(t -> byTerm.computeIfAbsent(t.term().strip().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(t));
        // 行の順に並べ、A-Z の中は綴りの順(スマホ版と同じ)
        List<String> rowOrder = new ArrayList<>(List.of("A-Z"));
        "あかさたなはまやらわ".chars().forEach(k -> rowOrder.add((char) k + "行"));
        rowOrder.add("その他");
        Comparator<TermCard> order = Comparator.comparingInt((TermCard c) -> rowOrder.indexOf(c.row()))
                .thenComparing(c -> c.row().equals("A-Z") ? c.first().term().replaceFirst("^@", "").toLowerCase(Locale.ROOT) : "");
        List<TermCard> cards = byTerm.values().stream().map(g -> new TermCard(kanaRow(g.getFirst()), g)).sorted(order).toList();
        model.addAttribute("cards", cards);
        model.addAttribute("rows", cards.stream().map(TermCard::row).distinct().toList());
        model.addAttribute("lessonTitles", LessonTitles.of(content));
        return "glossary";
    }
}
