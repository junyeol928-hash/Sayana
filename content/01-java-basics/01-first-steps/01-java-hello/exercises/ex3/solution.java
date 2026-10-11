public class NameTag {

    public static void printNameTag(String name) {
        String border = "+" + "-".repeat(name.length() + 2) + "+";
        System.out.println(border);
        System.out.println("| " + name + " |");
        System.out.println(border);
    }

    public static void main(String[] args) {
        printNameTag("Haru");
    }
}
