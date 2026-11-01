import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class OwnerReport {

    public static List<String> owners(Class<?> type) {
        List<String> result = new ArrayList<>();
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)); // 名前順に並べておく
        // TODO: 各メソッドの @Owner を getAnnotation で取り出す(付いていなければ null)
        // TODO: 付いていれば "メソッド名: 担当者(チーム)" を result に追加する
        return result;
    }

    public static List<String> unowned(Class<?> type) {
        List<String> result = new ArrayList<>();
        // TODO: @Owner が付いていないメソッドの名前を、名前の昇順で result に追加する
        return result;
    }

    public static void main(String[] args) {
        System.out.println(owners(PaymentService.class));   // [charge: 佐藤(未所属), refund: 鈴木(経理)]
        System.out.println(unowned(PaymentService.class));  // [ping]
    }
}

// TODO: SOURCE のままだとコンパイル後に消えて、実行時には読めない。RUNTIME に直す
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@interface Owner {
    String value();                    // 担当者名
    String team() default "未所属";    // チーム(省略可)
}

class PaymentService {
    @Owner("佐藤")
    public void charge() { }

    @Owner(value = "鈴木", team = "経理")
    public void refund() { }

    public void ping() { }
}
