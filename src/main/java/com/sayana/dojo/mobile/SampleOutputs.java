package com.sayana.dojo.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayana.dojo.content.ContentRepository;
import com.sayana.dojo.content.Lesson;
import com.sayana.dojo.exec.JShellRunner;
import com.sayana.dojo.exec.RunResult;

/**
 * サンプルの実行結果の控え。
 * <p>
 * スマホ版はコードを実行できないので、「出力を予想してから答えを見る」ために、PC で実行した結果を
 * {@code src/main/resources/mobile/sample-outputs.json} に控えておき、スマホ版のデータに入れる。
 * キーはコードと追加ライブラリのハッシュなので、サンプルを書き換えると控えが無くなり、
 * {@code ./mvnw -q compile exec:java} で実行し直すまで MobileSiteTest が落ちて知らせる。
 * 控えが本当の出力と同じことは CurriculumTest が全サンプルを実行するときに確かめる(同じ JDK のとき)。
 */
public final class SampleOutputs {

    public static final Path FILE = Path.of("src/main/resources/mobile/sample-outputs.json");

    /**
     * 1 つのサンプルの結果。
     *
     * @param varies 2 回実行して出力が違った(スレッドの順番など)か、オブジェクトのハッシュコードが出ている。
     *               画面で「実行のたびに変わることがある」と添え、テストでは比べない
     */
    public record Entry(String status, String output, List<String> values, String exception, boolean varies) {

        static Entry of(RunResult r, boolean varies) {
            return new Entry(r.status().name(), r.output(), r.values(), r.exception(), varies);
        }

        /** 実行のたびに同じになるはずの部分(経過時間は含めない)。 */
        boolean sameAs(RunResult r) {
            return status.equals(r.status().name()) && output.equals(r.output()) && values.equals(r.values())
                    && Objects.equals(exception, r.exception());
        }
    }

    /** 控えを作った JDK のメジャー番号と、キー → 結果。 */
    public record Data(int jdk, Map<String, Entry> entries) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** toString を上書きしていないオブジェクトの表示(クラス名@ハッシュコード)。 */
    private static final Pattern IDENTITY_HASH = Pattern.compile("[\\w$.;\\[]@[0-9a-f]{4,8}\\b");

    private SampleOutputs() {
    }

    public static String key(Lesson.Sample s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(String.join(",", s.libraries()).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(s.code().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Data load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new Data(Runtime.version().feature(), new TreeMap<>());
        }
        Map<String, Object> raw = MAPPER.readValue(file.toFile(), new TypeReference<>() { });
        int jdk = ((Number) raw.getOrDefault("jdk", 0)).intValue();
        Map<String, Entry> entries = MAPPER.convertValue(raw.get("samples"), new TypeReference<TreeMap<String, Entry>>() { });
        return new Data(jdk, entries == null ? new TreeMap<>() : entries);
    }

    /**
     * 控えの無いサンプルを実行して書き足し、教材から消えたサンプルの控えを捨てて保存する。
     * 新しく実行したものは 2 回動かし、出力が違えば {@code varies} にする。
     *
     * @return 新しく実行したサンプルの数
     */
    public static int refresh(ContentRepository content, JShellRunner runner, Path file) throws IOException {
        Data old = load(file);
        int jdk = Runtime.version().feature();
        // JDK が変わったら全部取り直す(例外のメッセージなどが変わりうる)
        Map<String, Entry> before = old.jdk() == jdk ? old.entries() : Map.of();
        Map<String, Entry> after = new TreeMap<>();
        int ran = 0;
        List<Lesson.Sample> samples = new ArrayList<>();
        content.readyLessons().forEach(l -> samples.addAll(l.samples()));
        for (Lesson.Sample s : samples) {
            String k = key(s);
            Entry e = before.get(k);
            if (e == null) {
                RunResult first = run(runner, s);
                RunResult second = run(runner, s);
                e = Entry.of(first, !Entry.of(first, false).sameAs(second));
                ran++;
                System.out.println("  実行: " + s.title() + (e.varies() ? "(実行のたびに変わる)" : ""));
            }
            if (!e.varies() && IDENTITY_HASH.matcher(e.output() + String.join("\n", e.values())).find()) {
                // 「C@31221be2」の @ の後ろ(ハッシュコード)は、同じ JDK でも版や環境で変わりうる
                e = new Entry(e.status(), e.output(), e.values(), e.exception(), true);
            }
            after.put(k, e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jdk", jdk);
        out.put("samples", after);
        String json = MAPPER.writer(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")))
                .writeValueAsString(out) + "\n";
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return ran;
    }

    private static RunResult run(JShellRunner runner, Lesson.Sample s) {
        RunResult r = runner.run(s.code(), Duration.ofSeconds(30), s.libraries());
        if (r.status() != RunResult.Status.OK && r.status() != RunResult.Status.EXCEPTION) {
            throw new IllegalStateException("サンプルが最後まで動きませんでした: " + s.title() + "\n" + r.errorText());
        }
        return r;
    }
}
