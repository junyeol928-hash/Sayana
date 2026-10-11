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
        // TODO: 小計 + 税額(subtotal * 税率 / 100)を返す
        return 0;
    }
}

/** ライブラリ側の「自動設定」のつもり */
// TODO: @Configuration を付ける
public class InvoiceAutoConfig {

    // TODO: @Bean を付ける
    InvoiceService invoiceService(ObjectProvider<TaxRate> taxRates) {
        // TODO: 利用者の TaxRate Bean があればそれを、無ければ StandardTaxRate を使う
        //       (taxRates.getIfAvailable(...) を使う)
        return new InvoiceService(new StandardTaxRate());
    }
}
