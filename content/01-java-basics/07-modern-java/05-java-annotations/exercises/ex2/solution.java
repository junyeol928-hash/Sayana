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
        if (value == null) {
            return "null";
        }
        if (keep == 0) {
            return "***";
        }
        if (value.length() <= keep) {
            return "*".repeat(value.length());
        }
        int hidden = value.length() - keep;
        return "*".repeat(hidden) + value.substring(hidden);
    }

    public static String toLog(Object obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName)); // 名前順に並べておく
        List<String> parts = new ArrayList<>();
        for (Field f : fields) {
            f.setAccessible(true);
            Object value;
            try {
                value = f.get(obj);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(f.getName() + " を読めません", e);
            }
            Mask m = f.getAnnotation(Mask.class);
            String text = (m == null) ? String.valueOf(value) : mask(value == null ? null : value.toString(), m.keep());
            parts.add(f.getName() + "=" + text);
        }
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
