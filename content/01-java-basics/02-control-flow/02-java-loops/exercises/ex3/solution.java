public class SavingsPlan {

    public static int yearsToReach(long principal, int ratePercent, long target) {
        long balance = principal;
        int years = 0;
        while (balance < target) {
            long interest = balance * ratePercent / 100;
            if (interest <= 0) {
                return -1; // 利息が増えない → 永遠に目標に届かない
            }
            balance += interest;
            years++;
        }
        return years;
    }
}
