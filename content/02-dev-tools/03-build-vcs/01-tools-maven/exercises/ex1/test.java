import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenPlanTest {

    @Test
    @DisplayName("package を指定すると validate から package までが順に並ぶ")
    void packagePhase() {
        assertEquals(List.of("validate", "compile", "test", "package"),
                MavenPlan.plan("package"),
                "フェーズを指定したら、validate からそのフェーズまでを順に全部追加してください");
    }

    @Test
    @DisplayName("先頭の validate と最後の deploy(境界値)")
    void firstAndLastPhase() {
        assertEquals(List.of("validate"), MavenPlan.plan("validate"),
                "validate は先頭のフェーズなので、validate だけが並びます");
        assertEquals(MavenPlan.DEFAULT_PHASES, MavenPlan.plan("deploy"),
                "deploy は最後のフェーズなので、7つ全部が並びます。<= と < を取り違えていませんか?");
    }

    @Test
    @DisplayName("clean install は clean の後に install までのフェーズが続く")
    void cleanInstall() {
        assertEquals(List.of("clean", "validate", "compile", "test",
                        "package", "verify", "install"),
                MavenPlan.plan("clean install"),
                "clean は別ライフサイクルなので \"clean\" だけを追加し、続けて install までを追加してください");
    }

    @Test
    @DisplayName("プラグインのゴール(: を含む)はそのまま1つ追加する")
    void pluginGoal() {
        assertEquals(List.of("clean", "validate", "compile", "test",
                        "package", "spring-boot:run"),
                MavenPlan.plan("clean package spring-boot:run"),
                "spring-boot:run のように : を含む指定は、ゴールとしてそのまま追加してください");
    }

    @Test
    @DisplayName("重複は2回目以降を飛ばし、余分な空白も許す")
    void duplicatesAndSpaces() {
        assertEquals(List.of("validate", "compile", "test", "package"),
                MavenPlan.plan("compile package"),
                "すでにリストにあるフェーズは追加しないでください(validate, compile が2回入っていませんか?)");
        assertEquals(List.of("clean", "validate", "compile", "test"),
                MavenPlan.plan("  clean   test "),
                "前後や間の空白が複数あっても正しく分けてください。trim() と split(\"\\\\s+\") を使っていますか?");
    }

    @Test
    @DisplayName("打ち間違いや空のコマンドは IllegalArgumentException")
    void invalid() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MavenPlan.plan("clean biuld"),
                "知らないフェーズ(biuld)を指定したら IllegalArgumentException を投げてください");
        assertTrue(e.getMessage() != null && e.getMessage().contains("biuld"),
                "例外のメッセージに、間違えた指定(biuld)をそのまま含めてください");
        assertThrows(IllegalArgumentException.class, () -> MavenPlan.plan("   "),
                "空白だけのコマンドでも IllegalArgumentException を投げてください");
        assertThrows(IllegalArgumentException.class, () -> MavenPlan.plan(null),
                "null のコマンドでも IllegalArgumentException を投げてください");
    }
}
