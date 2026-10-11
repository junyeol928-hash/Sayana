public class ParkingFee {

    /** 駐車時間(分)から料金(円)を計算する */
    public static int fee(int minutes) {
        // TODO: minutes が負なら IllegalArgumentException を投げる

        // TODO: 30 分以下なら 0 円

        // TODO: 30 分を超えた分を 30 分単位(切り上げ)で数えて 200 円ずつ。上限は 1500 円

        return -1; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        System.out.println(fee(30));  // 0
        System.out.println(fee(31));  // 200
        System.out.println(fee(61));  // 400
        System.out.println(fee(241)); // 1500
    }
}
