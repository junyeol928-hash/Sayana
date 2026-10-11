public class ParkingFee {

    private static final int FREE_MINUTES = 30;
    private static final int UNIT_MINUTES = 30;
    private static final int UNIT_PRICE = 200;
    private static final int MAX_FEE = 1500;

    /** 駐車時間(分)から料金(円)を計算する */
    public static int fee(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("駐車時間が負です: " + minutes);
        }
        if (minutes <= FREE_MINUTES) {
            return 0;
        }
        int over = minutes - FREE_MINUTES;
        int units = (over + UNIT_MINUTES - 1) / UNIT_MINUTES; // 切り上げ
        return Math.min(units * UNIT_PRICE, MAX_FEE);
    }

    public static void main(String[] args) {
        System.out.println(fee(30));  // 0
        System.out.println(fee(31));  // 200
        System.out.println(fee(61));  // 400
        System.out.println(fee(241)); // 1500
    }
}
