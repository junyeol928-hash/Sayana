public class LateCounter {

    /**
     * 1か月分の出社時刻から、遅刻した日数を数える。
     * 時刻は「0時からの分数」で表す(9:00 → 540、9:05 → 545)。
     * 始業時刻 start ちょうどに着いたら遅刻ではない。1分でも過ぎたら遅刻。
     *
     * このコードにはバグが2つある。デバッガで値を見て探そう。
     */
    public static int countLate(int[] arrivals, int start) {
        int count = 0;
        for (int i = 1; i < arrivals.length; i++) {
            if (arrivals[i] >= start) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        // 9:10, 8:50, 9:00 に出社 → 遅刻は 9:10 の1日だけのはず
        int[] arrivals = {550, 530, 540};
        System.out.println(countLate(arrivals, 540)); // 期待: 1
    }
}
