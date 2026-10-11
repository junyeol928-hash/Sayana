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
        if (!Files.exists(file)) {
            return config;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException((i + 1) + "行目: = がありません");
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            config.put(key, value); // 同じキーなら後勝ちで上書きされる
        }
        return config;
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
