public class MavenLayout {

    private static final String[] SOURCE_ROOTS = {
            "src/main/java/", "src/test/java/"};

    public static String toClassName(String path) {
        String p = path.replace('\\', '/');
        if (!p.endsWith(".java")) {
            return null;
        }
        for (String root : SOURCE_ROOTS) {
            if (p.startsWith(root)) {
                String rest = p.substring(root.length(),
                        p.length() - ".java".length());
                return rest.replace('/', '.');
            }
        }
        return null;
    }

    public static boolean shouldCommit(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        String first = parts[0];
        String fileName = parts[parts.length - 1];
        if (first.equals("target") || first.equals("bin")
                || first.equals(".settings")) {
            return false;
        }
        return !(fileName.equals(".project")
                || fileName.equals(".classpath"));
    }

    public static void main(String[] args) {
        System.out.println(toClassName(
                "src/main/java/com/example/demo/DemoApplication.java"));
        System.out.println(shouldCommit("pom.xml"));    // true
        System.out.println(shouldCommit(".classpath")); // false
    }
}
