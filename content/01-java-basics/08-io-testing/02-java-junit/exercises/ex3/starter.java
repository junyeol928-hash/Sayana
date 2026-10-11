public class TicketCases {

    /**
     * チケット料金 price(int age) のテストケースを返す。
     * 各要素は {年齢, 期待する料金}。
     */
    public static int[][] boundaryCases() {
        return new int[][] {
            {30, 1900},   // 例:30歳は1900円(13〜64歳)
            // TODO: 同値クラスの代表値と、境界の両側(5と6、12と13、64と65)、
            //       最小の 0 歳、エラーになる負の年齢を追加する
        };
    }

    public static void main(String[] args) {
        for (int[] c : boundaryCases()) {
            System.out.println(c[0] + "歳 → " + c[1] + "円");
        }
    }
}
