import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

public class OrderService {

    private final StockStore stock;
    private final PaymentGateway payment;

    public OrderService(StockStore stock, PaymentGateway payment) {
        this.stock = stock;
        this.payment = payment;
    }

    // 決済の失敗(検査例外)でもロールバックする
    @Transactional(rollbackFor = PaymentException.class)
    public void placeOrder(String item, int qty) throws PaymentException {
        stock.reduce(item, qty);
        payment.charge(stock.priceOf(item) * qty);
    }

    // 同じクラスの placeOrder を呼んでもプロキシを通らないので、
    // こちらにも @Transactional を付けて「全体で1トランザクション」にする
    @Transactional(rollbackFor = PaymentException.class)
    public void placeAll(List<OrderLine> lines) throws PaymentException {
        for (OrderLine line : lines) {
            placeOrder(line.item(), line.qty());
        }
    }
}

// 注文の1行(商品と数量)
record OrderLine(String item, int qty) {
}

// 決済の失敗を表す検査例外(Exception を継承。RuntimeException ではない)
class PaymentException extends Exception {
    PaymentException(String message) {
        super(message);
    }
}

// 外部の決済サービスの代わり(完成済み)。1回の決済が 10000 円を超えると失敗する
class PaymentGateway {
    final List<Integer> charged = new ArrayList<>();   // 決済できた金額の記録

    void charge(int amount) throws PaymentException {
        if (amount > 10000) {
            throw new PaymentException("限度額オーバーです: " + amount + "円");
        }
        charged.add(amount);
    }
}

// 在庫と単価を持つ「DB の代わり」(完成済み。変更しないでください)
class StockStore {
    private final Map<String, Integer> stocks = new HashMap<>();
    private final Map<String, Integer> prices = new HashMap<>();

    void register(String item, int price, int stock) {
        prices.put(item, price);
        stocks.put(item, stock);
    }

    int stockOf(String item) {
        return stocks.getOrDefault(item, 0);
    }

    int priceOf(String item) {
        return prices.get(item);
    }

    void reduce(String item, int qty) {
        if (stockOf(item) < qty) {
            throw new IllegalStateException("在庫が足りません: " + item);
        }
        stocks.put(item, stockOf(item) - qty);
    }

    // ↓ ロールバックの再現用(採点用のトランザクションマネージャが使う)
    Map<String, Integer> snapshot() {
        return new HashMap<>(stocks);
    }

    void restore(Map<String, Integer> snapshot) {
        stocks.clear();
        stocks.putAll(snapshot);
    }
}

