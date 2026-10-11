public class EmployeeId {

    public static boolean isValid(String id) {
        if (id == null) {
            return false;
        }
        String s = id.strip();
        if (s.length() != 9) {
            return false;
        }
        if (!s.substring(0, 4).equalsIgnoreCase("EMP-")) {
            return false;
        }
        for (int i = 4; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static String normalize(String id) {
        if (!isValid(id)) {
            return null;
        }
        return "EMP-" + id.strip().substring(4);
    }

    public static void main(String[] args) {
        System.out.println(isValid("EMP-00123"));     // true
        System.out.println(isValid("EMP-0012A"));     // false
        System.out.println(normalize(" emp-00042 ")); // EMP-00042
    }
}
