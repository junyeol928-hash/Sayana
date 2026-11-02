import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

public class MiniRouter {

    public static Map<String, String> routes(Class<?> type) {
        Map<String, String> table = new TreeMap<>();
        for (Method m : type.getDeclaredMethods()) {
            Get get = m.getAnnotation(Get.class);
            if (get == null) {
                continue;
            }
            if (table.containsKey(get.value())) {
                throw new IllegalStateException("パスが重複しています: " + get.value());
            }
            table.put(get.value(), m.getName());
        }
        return table;
    }

    public static String dispatch(Object controller, String path) {
        for (Method m : controller.getClass().getDeclaredMethods()) {
            Get get = m.getAnnotation(Get.class);
            if (get != null && get.value().equals(path)) {
                try {
                    return String.valueOf(m.invoke(controller));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(path + " の呼び出しに失敗しました", e);
                }
            }
        }
        return "404 Not Found";
    }

    public static void main(String[] args) {
        System.out.println(routes(ShopController.class));                 // {/hello=hello, /items=items}
        System.out.println(dispatch(new ShopController(), "/hello"));     // いらっしゃいませ
        System.out.println(dispatch(new ShopController(), "/secret"));    // 404 Not Found
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Get {
    String value();   // URL のパス("/hello" など)
}

class ShopController {
    @Get("/hello")
    public String hello() { return "いらっしゃいませ"; }

    @Get("/items")
    public String items() { return "コーヒー, 紅茶"; }

    public String secret() { return "裏メニュー"; }
}
