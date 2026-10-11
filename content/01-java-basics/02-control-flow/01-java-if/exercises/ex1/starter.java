public class GradeJudge {

    public static String grade(int score) {
        // TODO: 0 未満 または 100 を超える点数なら "ERROR" を返す(ガード節)

        // TODO: 90 以上 "S" / 80 以上 "A" / 70 以上 "B" / 60 以上 "C" / それ以外 "D" を返す

        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(grade(95));  // S
        System.out.println(grade(80));  // A
        System.out.println(grade(59));  // D
        System.out.println(grade(101)); // ERROR
    }
}
