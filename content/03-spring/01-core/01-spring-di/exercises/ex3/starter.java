import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/** 決済手段 */
interface PaymentGateway {
    int fee(int amount);
}

// TODO: Bean 名 "card" を付け、@Primary で既定にする
@Component
class CardPayment implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return 0; // TODO: 金額の 3%(1円未満切り捨て)
    }
}

// TODO: Bean 名 "bank" を付ける
@Component
class BankTransfer implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return 0; // TODO: 一律 440円
    }
}

// TODO: Bean 名 "konbini" を付ける
@Component
class KonbiniPayment implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return 0; // TODO: 一律 220円
    }
}

@Service
public class CheckoutService {

    private final PaymentGateway defaultGateway;
    private final PaymentGateway bankGateway;
    private final Map<String, PaymentGateway> gateways;

    // TODO: 2番目の引数に @Qualifier("bank") を付ける
    public CheckoutService(PaymentGateway defaultGateway,
                           PaymentGateway bankGateway,
                           Map<String, PaymentGateway> gateways) {
        this.defaultGateway = defaultGateway;
        this.bankGateway = bankGateway;
        this.gateways = gateways;
    }

    public int totalWithDefault(int amount) {
        return 0; // TODO: 金額 + 既定の決済手段の手数料
    }

    public int totalByBank(int amount) {
        return 0; // TODO: 金額 + 銀行振込の手数料
    }

    public int totalBy(String method, int amount) {
        // TODO: gateways から method の決済手段を探す。無ければ IllegalArgumentException
        return 0;
    }
}
