public class Order {
    private final String customer;
    private final int itemCount;
    private final int total;

    // 外からは new させず、Builder だけが Order を作る(完成済み)
    private Order(Builder b) {
        this.customer = b.customer;
        this.itemCount = b.itemCount;
        this.total = b.total;
    }

    String customer() {
        return customer;
    }

    int itemCount() {
        return itemCount;
    }

    int total() {
        return total;
    }

    String summary() {
        return customer + "様: " + itemCount + "点 " + total + "円";
    }

    static class Builder {
        // TODO: 顧客名を指定しなかったときは "ゲスト" になるようにする
        private String customer;
        private int itemCount = 0;
        private int total = 0;

        Builder customer(String name) {
            // TODO: 顧客名を覚える
            return this;
        }

        Builder item(int price) {
            // TODO: 点数を1増やし、合計に price を足す
            return this;
        }

        Order build() {
            // TODO: この Builder の内容で Order を作って返す
            return null;
        }
    }

    public static void main(String[] args) {
        Order o = new Order.Builder()
                .customer("佐藤")
                .item(1200)
                .item(800)
                .build();
        System.out.println(o.summary());
        System.out.println(new Order.Builder().build().summary());
    }
}
