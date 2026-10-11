import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SalesReport {

    /** input の売上CSVを店舗別に集計し、output に書き出す */
    public static void summarize(Path input, Path output) throws IOException {
        Map<String, Integer> total = new TreeMap<>(); // 店舗名の順に並ぶ

        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {      // 0 行目はヘッダー
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] c = line.split(",");
            total.merge(c[1].trim(), Integer.parseInt(c[2].trim()), Integer::sum);
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> out = new ArrayList<>();
        out.add("店舗,合計");
        for (Map.Entry<String, Integer> e : total.entrySet()) {
            out.add(e.getKey() + "," + e.getValue());
        }
        Files.write(output, out, StandardCharsets.UTF_8); // 既定で上書き
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("sales");
        Path in = dir.resolve("sales.csv");
        Files.write(in, List.of("日付,店舗,金額",
                "2026-10-01,渋谷,1200", "2026-10-01,新宿,800",
                "", "2026-10-02,渋谷,500"));
        Path out = dir.resolve("report").resolve("summary.csv");
        summarize(in, out);
        if (Files.exists(out)) {
            System.out.print(Files.readString(out)); // 店舗,合計 / 新宿,800 / 渋谷,1700
        } else {
            System.out.println("出力ファイルがありません");
        }
    }
}
