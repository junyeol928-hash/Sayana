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
        return Optional.ofNullable(c)
                .map(Customer::getAddress)
                .map(Address::getZip)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .orElse("未登録");
    }

    public static Optional<String> cityOf(Customer c) {
        return Optional.ofNullable(c)
                .map(Customer::getAddress)
                .map(Address::getCity)
                .map(String::strip)
                .filter(s -> !s.isEmpty());
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
