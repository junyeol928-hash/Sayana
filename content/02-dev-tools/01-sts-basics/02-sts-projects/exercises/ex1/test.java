import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenLayoutTest {

    @Test
    @DisplayName("src/main/java と src/test/java のファイルから完全修飾クラス名を作る")
    void classNameFromSourceRoots() {
        assertEquals("com.example.demo.DemoApplication",
                MavenLayout.toClassName("src/main/java/com/example/demo/DemoApplication.java"),
                "src/main/java/ の後ろを . 区切りにし、末尾の .java を外してください");
        assertEquals("com.example.demo.DemoApplicationTests",
                MavenLayout.toClassName("src/test/java/com/example/demo/DemoApplicationTests.java"),
                "src/test/java/ の下のテストクラスも同じように変換してください");
    }

    @Test
    @DisplayName("Windows の区切り文字(\\)でも同じ結果になる")
    void windowsSeparator() {
        assertEquals("com.example.demo.DemoApplication",
                MavenLayout.toClassName("src\\main\\java\\com\\example\\demo\\DemoApplication.java"),
                "最初に path.replace('\\\\', '/') で区切りを / にそろえていますか");
        assertFalse(MavenLayout.shouldCommit("target\\classes\\application.properties"),
                "\\ 区切りの target 配下も、コミットしない(false)にしてください");
    }

    @Test
    @DisplayName("パッケージなしのクラスはクラス名だけになる(境界値)")
    void defaultPackage() {
        assertEquals("Hello", MavenLayout.toClassName("src/main/java/Hello.java"),
                "src/main/java/ の直下のファイルはパッケージなしなので、\"Hello\" だけを返してください");
    }

    @Test
    @DisplayName("Java のソースでないものは null")
    void notJavaSource() {
        assertNull(MavenLayout.toClassName("src/main/resources/application.properties"),
                "resources の下は Java のソースではないので null を返してください");
        assertNull(MavenLayout.toClassName("pom.xml"),
                "pom.xml はソースではないので null を返してください");
        assertNull(MavenLayout.toClassName("src/main/resources/Hello.java"),
                ".java でも src/main/java か src/test/java の下でなければ null です");
    }

    @Test
    @DisplayName("ソース・pom.xml・mvnw はコミットする")
    void committed() {
        assertTrue(MavenLayout.shouldCommit("pom.xml"), "pom.xml はプロジェクトの設計図なのでコミットします(true)");
        assertTrue(MavenLayout.shouldCommit("mvnw"), "mvnw はコミットします(true)");
        assertTrue(MavenLayout.shouldCommit("src/main/java/com/example/demo/DemoApplication.java"),
                "ソースコードはコミットします(true)");
        assertTrue(MavenLayout.shouldCommit("src/main/resources/static/bin/app.js"),
                "途中に bin があるだけのファイルはコミットします。先頭のフォルダだけを見ていますか");
    }

    @Test
    @DisplayName("ビルド結果と STS の設定ファイルはコミットしない")
    void ignored() {
        assertFalse(MavenLayout.shouldCommit("target/classes/com/example/demo/DemoApplication.class"),
                "target はビルド結果なのでコミットしません(false)");
        assertFalse(MavenLayout.shouldCommit("bin/Hello.class"), "bin はビルド結果なのでコミットしません(false)");
        assertFalse(MavenLayout.shouldCommit(".classpath"), ".classpath は m2e の生成物なのでコミットしません(false)");
        assertFalse(MavenLayout.shouldCommit(".project"), ".project は m2e の生成物なのでコミットしません(false)");
        assertFalse(MavenLayout.shouldCommit(".settings/org.eclipse.jdt.core.prefs"),
                ".settings フォルダの中はコミットしません(false)");
    }
}
