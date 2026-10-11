import java.util.Optional;

class Address {
    private final String zip;
    private final String city;

    Address(String zip, String city) {
        this.zip = zip;
        this.city = city;
    }

    String getZip() { return zip; }
    String getCity() { return city; }
}

class Customer {
    private final String name;
    private final Address address;

    Customer(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    String getName() { return name; }
    Address getAddress() { return address; }
}

public class ShippingLabel {

    public static String zipOf(Customer c) {
        // TODO: Optional.ofNullable(c) から map で 住所 → 郵便番号 と辿り、
        //       strip で前後の空白を除き、空文字なら filter で落として、
        //       取れなければ "未登録" を返す
        return "";
    }

    public static Optional<String> cityOf(Customer c) {
        // TODO: zipOf と同じ要領で市区町村を取り出す(orElse は付けずに Optional のまま返す)
        return Optional.empty();
    }

    public static void main(String[] args) {
        Customer ok = new Customer("佐藤", new Address(" 100-0001 ", "千代田区"));
        Customer noAddr = new Customer("鈴木", null);
        System.out.println(zipOf(ok));                       // 100-0001
        System.out.println(zipOf(noAddr));                   // 未登録
        System.out.println(cityOf(ok).orElse("(なし)"));      // 千代田区
        System.out.println(cityOf(noAddr).orElse("(なし)"));  // (なし)
    }
}
