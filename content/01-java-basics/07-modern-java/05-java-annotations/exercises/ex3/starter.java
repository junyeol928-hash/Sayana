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
        // TODO: getDeclaredMethods() で全メソッドを見て、@Get が付いていれば「パス → メソッド名」を table に入れる
        // TODO: 同じパスがすでに table にあれば IllegalStateException(メッセージにパスを含める)
        return table;
    }

    public static String dispatch(Object controller, String path) {
        // TODO: controller.getClass() のメソッドから、@Get の value が path と一致するものを探す
        // TODO: 見つかったら m.invoke(controller) で呼び、戻り値を String にして返す
        //       (IllegalAccessException / InvocationTargetException は IllegalStateException に包む)
        // TODO: 見つからなければ "404 Not Found"
        return null; // ← ダミー。書き換えてください
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
