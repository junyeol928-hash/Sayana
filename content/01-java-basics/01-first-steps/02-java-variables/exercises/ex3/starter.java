public class TrafficCalculator {

    /**
     * 総転送量(バイト)を返す。
     * @param avgBytesPerRequest 1リクエストあたりの平均サイズ(バイト)
     * @param requestsPerDay     1日あたりのリクエスト数
     * @param days               集計日数
     */
    public static long totalBytes(int avgBytesPerRequest, int requestsPerDay, int days) {
        // TODO: 平均サイズ × 1日のリクエスト数 × 日数 を、int で溢れないように計算して返す
        //       (avgBytesPerRequest * requestsPerDay * days だと溢れる)
        return 0;
    }
}
