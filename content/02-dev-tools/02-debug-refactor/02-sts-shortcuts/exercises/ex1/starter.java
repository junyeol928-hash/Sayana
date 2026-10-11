public class Overtime {

    // TODO: リファクタリングで次の3つを行う(計算結果は変えないこと)
    //   1. メソッド名 calc を totalOvertimeMinutes に変える(Alt+Shift+R)
    //   2. 引数名 m を workedMinutes、t を total、x を minutes に変える(Alt+Shift+R)
    //   3. 480 を定数 STANDARD_MINUTES に抽出する(Alt+Shift+T > Extract Constant...)

    /** 1日ごとの勤務時間(分)の配列から、その月の残業時間(分)の合計を返す */
    public static int calc(int[] m) {
        int t = 0;
        for (int x : m) {
            if (x > 480) {
                t += x - 480;
            }
        }
        return t;
    }

    public static void main(String[] args) {
        int[] month = {480, 540, 600, 300};
        System.out.println(calc(month)); // 180
    }
}
