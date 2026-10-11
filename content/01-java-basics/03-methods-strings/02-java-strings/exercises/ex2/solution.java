public class OrderCsv {

    public static int lineTotal(String line) {
        String[] cols = line.split(",");
        int price = Integer.parseInt(cols[2].strip());
        int qty = Integer.parseInt(cols[3].strip());
        return price * qty;
    }

    public static int sumAll(String csv) {
        if (csv == null) {
            return 0;
        }
        String[] lines = csv.split("\n");
        int total = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            total += lineTotal(lines[i]);
        }
        return total;
    }

    public static String topItem(String csv) {
        if (csv == null) {
            return "";
        }
        String[] lines = csv.split("\n");
        String best = "";
        int max = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int amount = lineTotal(lines[i]);
            if (amount > max) {
                max = amount;
                best = lines[i].split(",")[1].strip();
            }
        }
        return best;
    }

    public static void main(String[] args) {
        String csv = """
                code,name,price,qty
                A001,コーヒー豆,1200,3
                B002, マグカップ , 800 , 2

                C003,ドリッパー,1500,1
                """;
        System.out.println(lineTotal("A001,コーヒー豆,1200,3")); // 3600
        System.out.println(sumAll(csv));                        // 6700
        System.out.println(topItem(csv));                       // コーヒー豆
    }
}
