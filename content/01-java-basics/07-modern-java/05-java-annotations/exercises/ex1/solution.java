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
        for (Method m : methods) {
            Owner owner = m.getAnnotation(Owner.class);
            if (owner != null) {
                result.add(m.getName() + ": " + owner.value() + "(" + owner.team() + ")");
            }
        }
        return result;
    }

    public static List<String> unowned(Class<?> type) {
        List<String> result = new ArrayList<>();
        Method[] methods = type.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method m : methods) {
            if (!m.isAnnotationPresent(Owner.class)) {
                result.add(m.getName());
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println(owners(PaymentService.class));   // [charge: 佐藤(未所属), refund: 鈴木(経理)]
        System.out.println(unowned(PaymentService.class));  // [ping]
    }
}

@Retention(RetentionPolicy.RUNTIME)
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
