public class OrderCsv {

    // "A001,コーヒー豆,1200,3" → 1200 × 3 = 3600
    public static int lineTotal(String line) {
        // TODO: split(",") で列に分ける
        // TODO: 単価(2列目 = インデックス2)と数量(インデックス3)を strip してから parseInt
        // TODO: 単価 × 数量 を返す
        return 0; // ← ダミー。書き換えてください
    }

    // CSV 全体(1行目は見出し)の金額の合計
    public static int sumAll(String csv) {
        // TODO: null なら 0
        // TODO: split("\n") で行に分け、インデックス1から回す(0 は見出し)
        // TODO: 空行(isBlank)は飛ばし、lineTotal を足していく
        return 0; // ← ダミー。書き換えてください
    }

    // 金額が最大の明細の商品名(同額なら先のもの、明細なしなら "")
    public static String topItem(String csv) {
        // TODO: null なら ""
        // TODO: sumAll と同じように行を回し、最大の金額とその商品名を覚えておく
        return null; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        String csv = """
                code,name,price,qty
                A001,コーヒー豆,1200,3
                B002, マグカップ , 800 , 2

                C003,ドリッパー,1500,1
                """;
        System.out.println(lineTotal("A001,コーヒー豆,1200,3")); // 3600
        System.out.println(sumAll(csv));                        // 6700
        System.out.println(topItem(csv));                       // コーヒー豆
    }
}
