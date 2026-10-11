public class GradeJudge {

    public static String grade(int score) {
        if (score < 0 || score > 100) {
            return "ERROR";
        }
        if (score >= 90) {
            return "S";
        } else if (score >= 80) {
            return "A";
        } else if (score >= 70) {
            return "B";
        } else if (score >= 60) {
            return "C";
        } else {
            return "D";
        }
    }

    public static void main(String[] args) {
        System.out.println(grade(95));  // S
        System.out.println(grade(80));  // A
        System.out.println(grade(59));  // D
        System.out.println(grade(101)); // ERROR
    }
}
