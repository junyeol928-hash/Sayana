public class Pyramid {

    public static String build(int height) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= height; i++) {
            if (i > 1) {
                sb.append('\n');
            }
            for (int s = 0; s < height - i; s++) {
                sb.append(' ');
            }
            for (int k = 0; k < 2 * i - 1; k++) {
                sb.append('*');
            }
        }
        return sb.toString();
    }
}
