// 帳票の「流れ」を決める抽象クラス(完成済み。変更しないこと)
abstract class Report {
    final String print() {
        return "【" + title() + "】\n" + body() + "\n-- 以上 --";
    }

    abstract String title();

    abstract String body();
}

public class DailyReport extends Report {
    private final int sales;
    private final int customers;

    DailyReport(int sales, int customers) {
        this.sales = sales;
        this.customers = customers;
    }

    @Override
    String title() {
        // TODO: "売上日報" を返す
        return "";
    }

    @Override
    String body() {
        // TODO: "売上: 12000円 / 客数: 30人" の形式で返す
        return "";
    }

    public static void main(String[] args) {
        System.out.println(new DailyReport(12000, 30).print());
        System.out.println(new StockReport("トナー", 9).print());
    }
}

class StockReport extends Report {
    private final String item;
    private final int count;

    StockReport(String item, int count) {
        this.item = item;
        this.count = count;
    }

    @Override
    String title() {
        // TODO: "在庫報告" を返す
        return "";
    }

    @Override
    String body() {
        // TODO: "コピー用紙: 25個" の形式で返す
        // TODO: 在庫数が 10 未満なら末尾に " 要発注" を付ける
        return "";
    }
}
