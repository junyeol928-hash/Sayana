public class Warikan {

    // 1人あたりの支払額(端数は切り捨て)を返す
    public static int perPerson(int total, int people) {
        // TODO: ここに書く(total と people を使って1人あたりの金額を計算する)
        return 0;
    }

    // 割り切れずに余った端数を返す
    public static int remainder(int total, int people) {
        // TODO: ここに書く(余りを求める演算子を使う)
        return 0;
    }

    public static void main(String[] args) {
        int total = 10000;
        int people = 3;
        System.out.println("1人あたり: " + perPerson(total, people) + "円");
        System.out.println("幹事の上乗せ: " + remainder(total, people) + "円");
    }
}
