import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesReportTest {

    @TempDir
    Path dir; // テストごとに作られ、終わると自動で消える一時フォルダ

    private Path input(String... lines) throws IOException {
        Path p = dir.resolve("sales.csv");
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
        return p;
    }

    private List<String> read(Path out) throws IOException {
        assertTrue(Files.exists(out), "出力ファイル " + out.getFileName() + " が作られていません。Files.write などで書き出していますか?");
        return Files.readAllLines(out, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("店舗ごとに合計し、店舗名の順で書き出す(問題文の例)")
    void basic() throws IOException {
        Path in = input("日付,店舗,金額",
                "2026-10-01,渋谷,1200",
                "2026-10-01,新宿,800",
                "2026-10-02,渋谷,500");
        Path out = dir.resolve("summary.csv");
        SalesReport.summarize(in, out);
        assertEquals(List.of("店舗,合計", "新宿,800", "渋谷,1700"), read(out),
                "1行目はヘッダー「店舗,合計」、2行目以降は店舗名の昇順(TreeMap の順)で「店舗,合計金額」です");
    }

    @Test
    @DisplayName("空行は飛ばして集計する")
    void blankLines() throws IOException {
        Path in = input("日付,店舗,金額",
                "2026-10-01,池袋,300",
                "",
                "   ",
                "2026-10-02,池袋,700");
        Path out = dir.resolve("summary.csv");
        SalesReport.summarize(in, out);
        assertEquals(List.of("店舗,合計", "池袋,1000"), read(out),
                "空行(isBlank() が true の行)は飛ばしてください。split すると列が足りず ArrayIndexOutOfBoundsException になります");
    }

    @Test
    @DisplayName("出力先のフォルダが無ければ作る")
    void createsParentDirectories() throws IOException {
        Path in = input("日付,店舗,金額", "2026-10-01,渋谷,100");
        Path out = dir.resolve("report").resolve("2026-10").resolve("summary.csv");
        SalesReport.summarize(in, out);
        assertEquals(List.of("店舗,合計", "渋谷,100"), read(out),
                "出力先の親フォルダ(report/2026-10)が無いときは Files.createDirectories で作ってから書き出してください");
    }

    @Test
    @DisplayName("ヘッダーだけの CSV なら、出力もヘッダー行だけ(境界値)")
    void headerOnly() throws IOException {
        Path in = input("日付,店舗,金額");
        Path out = dir.resolve("summary.csv");
        SalesReport.summarize(in, out);
        assertEquals(List.of("店舗,合計"), read(out),
                "データ行が0件なら、出力はヘッダー「店舗,合計」の1行だけです。ヘッダー行を集計しようとして NumberFormatException になっていませんか?");
    }

    @Test
    @DisplayName("出力ファイルが既にあれば上書きする(追記しない)")
    void overwrite() throws IOException {
        Path in = input("日付,店舗,金額", "2026-10-01,新宿,250");
        Path out = dir.resolve("summary.csv");
        Files.write(out, List.of("店舗,合計", "古いデータ,999"), StandardCharsets.UTF_8);
        SalesReport.summarize(in, out);
        assertEquals(List.of("店舗,合計", "新宿,250"), read(out),
                "前回の出力が残っています。StandardOpenOption.APPEND を付けず、上書きで書き出してください");
    }
}
