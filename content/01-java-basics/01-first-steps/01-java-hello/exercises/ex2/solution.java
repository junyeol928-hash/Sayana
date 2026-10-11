public class SelfIntro {

    public static String buildIntro(String name, String team, int years) {
        return "はじめまして。" + team + "の" + name + "です。Java歴は" + years + "年です。";
    }

    public static void main(String[] args) {
        System.out.println(buildIntro("ハル", "開発部", 1));
    }
}
