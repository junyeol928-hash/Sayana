// 申請の状態(STS では com.example.approval.domain.RequestStatus)
enum RequestStatus {
    DRAFT("下書き"),
    SUBMITTED("申請中"),
    APPROVED("承認済"),
    RETURNED("差戻し"),
    WITHDRAWN("取下げ");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    /** この状態から next へ移れるか(状態遷移図に矢印があるか) */
    boolean canTransitionTo(RequestStatus next) {
        // TODO: switch (this) の switch 式で、状態ごとに行ける先を書く
        return false;
    }

    /** これ以上変更できない状態か(業務ルール5) */
    boolean isFinal() {
        // TODO: APPROVED と WITHDRAWN のときだけ true
        return false;
    }
}

// 遷移表を表示するだけのクラス(完成済み)
public class StatusTable {

    public static void main(String[] args) {
        System.out.print("from\\to   ");
        for (RequestStatus to : RequestStatus.values()) {
            System.out.printf("%-10s", to);
        }
        System.out.println();
        for (RequestStatus from : RequestStatus.values()) {
            System.out.printf("%-10s", from);
            for (RequestStatus to : RequestStatus.values()) {
                System.out.printf("%-10s", from.canTransitionTo(to) ? "○" : "-");
            }
            System.out.println(from.isFinal() ? " (確定)" : "");
        }
    }
}
