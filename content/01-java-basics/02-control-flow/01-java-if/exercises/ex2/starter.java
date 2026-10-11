public class ShippingFee {

    public static int calc(int amount, String rank) {
        // TODO: amount が 0 以下なら -1 を返す(ガード節)

        // TODO: rank が "GOLD" なら 0 を返す(文字列の比較は equals で!)

        // TODO: rank が "SILVER" なら 3000 円以上で 0、未満なら 300 を返す

        // TODO: それ以外(null を含む)は 5000 円以上で 0、未満なら 500 を返す

        return 999; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(calc(100, "GOLD"));    // 0
        System.out.println(calc(2999, "SILVER")); // 300
        System.out.println(calc(3000, "SILVER")); // 0
        System.out.println(calc(1000, null));     // 500
        System.out.println(calc(0, "GOLD"));      // -1
    }
}
