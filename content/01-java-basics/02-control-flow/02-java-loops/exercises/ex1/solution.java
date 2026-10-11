public class FizzBuzz {

    public static String fizzBuzz(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) {
                sb.append("\n");
            }
            if (i % 15 == 0) {
                sb.append("FizzBuzz");
            } else if (i % 3 == 0) {
                sb.append("Fizz");
            } else if (i % 5 == 0) {
                sb.append("Buzz");
            } else {
                sb.append(i);
            }
        }
        return sb.toString();
    }
}
