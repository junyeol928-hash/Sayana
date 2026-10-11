import java.util.ArrayList;
import java.util.List;

public class MavenPlan {

    /** default ライフサイクルのフェーズ(簡略版)。この順に実行される */
    static final List<String> DEFAULT_PHASES = List.of(
            "validate", "compile", "test", "package",
            "verify", "install", "deploy");

    public static List<String> plan(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(
                    "ゴールまたはフェーズを指定してください");
        }
        List<String> result = new ArrayList<>();
        for (String arg : command.trim().split("\\s+")) {
            if (arg.equals("clean")) {
                addOnce(result, arg);
            } else if (DEFAULT_PHASES.contains(arg)) {
                int last = DEFAULT_PHASES.indexOf(arg);
                for (int i = 0; i <= last; i++) {
                    addOnce(result, DEFAULT_PHASES.get(i));
                }
            } else if (arg.contains(":")) {
                addOnce(result, arg);
            } else {
                throw new IllegalArgumentException(
                        "不明なライフサイクルフェーズです: " + arg);
            }
        }
        return result;
    }

    private static void addOnce(List<String> list, String s) {
        if (!list.contains(s)) {
            list.add(s);
        }
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
