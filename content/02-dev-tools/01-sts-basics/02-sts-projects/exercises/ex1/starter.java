public class MavenLayout {

    public static String toClassName(String path) {
        // TODO: 区切りを / にそろえる
        // TODO: src/main/java/ か src/test/java/ で始まり、.java で終わるなら
        //       その間の部分の / を . に変えて返す
        // TODO: それ以外は null を返す
        return ""; // ← ダミー。書き換えてください
    }

    public static boolean shouldCommit(String path) {
        // TODO: 区切りを / にそろえ、先頭のフォルダとファイル名を取り出す
        // TODO: target / bin / .settings で始まる、または
        //       ファイル名が .project / .classpath なら false
        return false; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(toClassName(
                "src/main/java/com/example/demo/DemoApplication.java"));
        System.out.println(shouldCommit("pom.xml"));    // true
        System.out.println(shouldCommit(".classpath")); // false
    }
}
