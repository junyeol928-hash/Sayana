import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniRouterTest {

    public static class UserController {
        private int calls = 0;

        @Get("/users")
        public String list() {
            calls++;
            return "佐藤, 鈴木";
        }

        @Get("/users/count")
        public String count() {
            return "2";
        }

        @Get("/about")
        public String about() {
            return "社内名簿 v1 (呼ばれた回数: " + calls + ")";
        }

        public String admin() {
            return "管理画面";
        }
    }

    public static class Duplicated {
        @Get("/same")
        public String a() { return "a"; }

        @Get("/same")
        public String b() { return "b"; }
    }

    public static class Broken {
        @Get("/boom")
        public String boom() {
            throw new IllegalArgumentException("内部エラー");
        }
    }

    @Test
    @DisplayName("routes:@Get の付いたメソッドだけ、パスの昇順で対応表にする")
    void routes() {
        assertEquals(Map.of("/about", "about", "/users", "list", "/users/count", "count"),
                MiniRouter.routes(UserController.class),
                "@Get の value(パス)をキー、メソッド名を値にしてください。@Get の無い admin は入れません");
        assertEquals("[/about, /users, /users/count]",
                MiniRouter.routes(UserController.class).keySet().toString(),
                "パスの昇順に並ぶよう TreeMap を使ってください");
    }

    @Test
    @DisplayName("routes:同じパスが2つあれば IllegalStateException")
    void duplicated() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MiniRouter.routes(Duplicated.class),
                "/same が2つのメソッドに付いています。どちらを呼ぶか決まらないので IllegalStateException を投げてください");
        assertTrue(e.getMessage() != null && e.getMessage().contains("/same"),
                "例外メッセージに重複したパス(/same)を含めてください。どこを直せばよいか分かるようにするためです");
    }

    @Test
    @DisplayName("dispatch:パスに対応するメソッドを呼んで、戻り値を返す")
    void dispatch() {
        UserController c = new UserController();
        assertEquals("佐藤, 鈴木", MiniRouter.dispatch(c, "/users"),
                "/users には list() が対応します。m.invoke(controller) で呼んだ戻り値を返してください");
        assertEquals("2", MiniRouter.dispatch(c, "/users/count"), "/users/count には count() が対応します");
        assertEquals("社内名簿 v1 (呼ばれた回数: 1)", MiniRouter.dispatch(c, "/about"),
                "渡された controller のインスタンスそのものを呼んでください(new し直すと呼ばれた回数が 0 に戻ります)");
    }

    @Test
    @DisplayName("dispatch:問題文の ShopController")
    void shop() {
        assertEquals("いらっしゃいませ", MiniRouter.dispatch(new ShopController(), "/hello"),
                "/hello には hello() が対応します");
        assertEquals("コーヒー, 紅茶", MiniRouter.dispatch(new ShopController(), "/items"),
                "/items には items() が対応します");
    }

    @Test
    @DisplayName("dispatch:目印の無いメソッドや、知らないパスは 404")
    void notFound() {
        assertEquals("404 Not Found", MiniRouter.dispatch(new UserController(), "/admin"),
                "admin() には @Get が付いていないので、URL からは呼べません。\"404 Not Found\" を返してください");
        assertEquals("404 Not Found", MiniRouter.dispatch(new ShopController(), "/secret"),
                "secret() には @Get が付いていないので 404 です");
        assertEquals("404 Not Found", MiniRouter.dispatch(new UserController(), "/nothing"),
                "どの @Get にも一致しないパスは \"404 Not Found\" です");
    }

    @Test
    @DisplayName("dispatch:呼んだメソッドが例外を投げたら IllegalStateException に包む")
    void wrapsException() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MiniRouter.dispatch(new Broken(), "/boom"),
                "invoke が投げる InvocationTargetException を IllegalStateException に包んで投げ直してください");
        assertTrue(e.getCause() != null,
                "IllegalStateException の原因(cause)に元の例外を渡してください。new IllegalStateException(メッセージ, e) の形です");
    }
}
