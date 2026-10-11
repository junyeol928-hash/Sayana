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
        return "売上日報";
    }

    @Override
    String body() {
        return "売上: " + sales + "円 / 客数: " + customers + "人";
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
        return "在庫報告";
    }

    @Override
    String body() {
        String line = item + ": " + count + "個";
        if (count < 10) {
            line += " 要発注";
        }
        return line;
    }
}
