import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    /** key=value 形式の設定ファイルを読み込む。ファイルが無ければ空の Map */
    public static Map<String, String> load(Path file) throws IOException {
        Map<String, String> config = new HashMap<>();

        // TODO: ファイルが存在しなければ、空の config をそのまま返す

        // TODO: UTF-8 で全行を読み、1行ずつ処理する(行番号は1から)
        //   - trim して空の行、# で始まる行は飛ばす
        //   - 最初の = の位置でキーと値に分け、それぞれ trim して config に入れる
        //   - = が無い行は IllegalArgumentException("<行番号>行目: = がありません")

        return config; // ← 今は空のまま。中身を入れてください
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("batch", ".properties");
        Files.write(file, List.of(
                "# 売上集計バッチの設定",
                "input.dir = C:/data/in",
                "db.url=jdbc:postgresql://localhost/shop?ssl=true",
                "app.title = Java道場"));
        System.out.println(load(file)); // 3件(順不同)
        Files.delete(file);
        System.out.println(load(file)); // {}(ファイルが無いので空)
    }
}
