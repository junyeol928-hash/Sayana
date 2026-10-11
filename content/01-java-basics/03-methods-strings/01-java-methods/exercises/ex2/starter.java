public class StockUpdater {

    // 商品 index から qty 個出荷する。出荷できたら true(呼び出し元の配列が書き換わる)
    public static boolean ship(int[] stock, int index, int qty) {
        // TODO: stock が null なら false
        // TODO: index が範囲外(0 未満 / stock.length 以上)なら false
        // TODO: qty が 0 以下なら false
        // TODO: 在庫が足りなければ false
        // TODO: 在庫から qty を引いて true
        return false; // ← ダミー。書き換えてください
    }

    // 全商品の在庫に qty を足した「新しい配列」を返す(stock は変えない)
    public static int[] restocked(int[] stock, int qty) {
        // TODO: stock が null なら new int[0] を返す
        // TODO: 同じ長さの新しい配列を作り、各要素に stock[i] + qty を入れて返す
        return null; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        int[] stock = {10, 0, 5};
        System.out.println(ship(stock, 0, 3));                       // true
        System.out.println(java.util.Arrays.toString(stock));       // [7, 0, 5]
        System.out.println(ship(stock, 1, 1));                       // false
        int[] next = restocked(stock, 2);
        System.out.println(java.util.Arrays.toString(next));        // [9, 2, 7]
        System.out.println(java.util.Arrays.toString(stock));       // [7, 0, 5]
    }
}
