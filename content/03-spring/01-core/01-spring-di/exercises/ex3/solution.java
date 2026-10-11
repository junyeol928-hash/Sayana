import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/** 決済手段 */
interface PaymentGateway {
    int fee(int amount);
}

@Component("card")
@Primary
class CardPayment implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return amount * 3 / 100; // int の割り算なので1円未満は切り捨て
    }
}

@Component("bank")
class BankTransfer implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return 440;
    }
}

@Component("konbini")
class KonbiniPayment implements PaymentGateway {
    @Override
    public int fee(int amount) {
        return 220;
    }
}

@Service
public class CheckoutService {

    private final PaymentGateway defaultGateway;
    private final PaymentGateway bankGateway;
    private final Map<String, PaymentGateway> gateways;

    public CheckoutService(PaymentGateway defaultGateway,
                           @Qualifier("bank") PaymentGateway bankGateway,
                           Map<String, PaymentGateway> gateways) {
        this.defaultGateway = defaultGateway;
        this.bankGateway = bankGateway;
        this.gateways = gateways;
    }

    public int totalWithDefault(int amount) {
        return amount + defaultGateway.fee(amount);
    }

    public int totalByBank(int amount) {
        return amount + bankGateway.fee(amount);
    }

    public int totalBy(String method, int amount) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("未対応の決済手段です: " + method);
        }
        return amount + gateway.fee(amount);
    }
}
