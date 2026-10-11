import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 税率(%) */
interface TaxRate {
    int percent();
}

/** 既定の税率 10% */
class StandardTaxRate implements TaxRate {
    @Override
    public int percent() {
        return 10;
    }
}

/** 利用者(アプリ側)の設定:軽減税率 8% を使いたい */
@Configuration
class ReducedTaxConfig {
    @Bean
    TaxRate reducedTaxRate() {
        return () -> 8;
    }
}

class InvoiceService {
    private final TaxRate taxRate;

    InvoiceService(TaxRate taxRate) {
        this.taxRate = taxRate;
    }

    TaxRate taxRate() {
        return taxRate;
    }

    int total(int subtotal) {
        return subtotal + subtotal * taxRate.percent() / 100;
    }
}

/** ライブラリ側の「自動設定」のつもり */
@Configuration
public class InvoiceAutoConfig {

    @Bean
    InvoiceService invoiceService(ObjectProvider<TaxRate> taxRates) {
        // 利用者の TaxRate Bean があればそれを、無ければ既定の 10% を使う
        TaxRate rate = taxRates.getIfAvailable(StandardTaxRate::new);
        return new InvoiceService(rate);
    }
}
