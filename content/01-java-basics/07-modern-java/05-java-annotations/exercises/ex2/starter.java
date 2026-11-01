import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class LogMasker {

    public static String mask(String value, int keep) {
        // TODO: value が null なら "null"
        // TODO: keep が 0 なら "***"
        // TODO: 長さが keep 以下なら全部を *、それ以外は末尾 keep 文字を残して前を * にする("*".repeat(n) が便利)
        return value; // ← ダミー。書き換えてください
    }

    public static String toLog(Object obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName)); // 名前順に並べておく
        List<String> parts = new ArrayList<>();
        // TODO: 各フィールドを setAccessible(true) して f.get(obj) で値を読む
        //       (IllegalAccessException は IllegalStateException に包んで投げ直す)
        // TODO: @Mask が付いていれば mask(値, keep) を、付いていなければ値をそのまま "名前=値" にして parts に追加
        return obj.getClass().getSimpleName() + "{" + String.join(", ", parts) + "}";
    }

    public static void main(String[] args) {
        System.out.println(mask("4111222233334444", 4)); // ************4444
        System.out.println(toLog(new LoginForm()));
        // LoginForm{cardNo=************4444, password=***, retry=2, userId=haru}
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Mask {
    int keep() default 0;   // 末尾を何文字残すか(0 なら *** だけ)
}

class LoginForm {
    private String userId = "haru";
    @Mask private String password = "s3cret!";
    @Mask(keep = 4) private String cardNo = "4111222233334444";
    private int retry = 2;
}
