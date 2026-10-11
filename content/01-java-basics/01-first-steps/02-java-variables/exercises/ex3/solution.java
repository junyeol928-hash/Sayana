public class TrafficCalculator {

    /**
     * 総転送量(バイト)を返す。
     * @param avgBytesPerRequest 1リクエストあたりの平均サイズ(バイト)
     * @param requestsPerDay     1日あたりのリクエスト数
     * @param days               集計日数
     */
    public static long totalBytes(int avgBytesPerRequest, int requestsPerDay, int days) {
        // 最初の掛け算の前に long にキャストする。
        // long * int → long なので、以降の掛け算もすべて long で計算される。
        return (long) avgBytesPerRequest * requestsPerDay * days;
    }
}
