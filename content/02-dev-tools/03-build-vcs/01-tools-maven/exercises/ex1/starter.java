import java.util.ArrayList;
import java.util.List;

public class MavenPlan {

    /** default ライフサイクルのフェーズ(簡略版)。この順に実行される */
    static final List<String> DEFAULT_PHASES = List.of(
            "validate", "compile", "test", "package",
            "verify", "install", "deploy");

    public static List<String> plan(String command) {
        List<String> result = new ArrayList<>();

        // TODO: command が null・空白だけなら IllegalArgumentException を投げる

        // TODO: command.trim().split("\\s+") で1つずつ取り出し、
        //   - "clean" なら "clean" を追加
        //   - DEFAULT_PHASES にあるなら validate からそのフェーズまでを順に追加
        //   - ":" を含むならそのまま追加
        //   - どれでもなければ IllegalArgumentException(メッセージに指定を含める)
        //   ※ すでに result にあるものは追加しない

        return result;
    }

    public static void main(String[] args) {
        System.out.println(plan("package"));
        // [validate, compile, test, package]
        System.out.println(plan("clean install"));
        // [clean, validate, compile, test, package, verify, install]
        System.out.println(plan("clean spring-boot:run"));
        // [clean, spring-boot:run]
    }
}
