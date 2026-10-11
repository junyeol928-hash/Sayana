import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchJobTest {

    @Test
    @DisplayName("close() で「close:名前;」を記録する")
    void closeLogs() {
        StringBuilder log = new StringBuilder();
        new TrackedResource("X", log).close();
        assertEquals("open:X;close:X;", log.toString(),
                "close() で \"close:\" + 名前 + \";\" を log に追記してください");
    }

    @Test
    @DisplayName("正常時:開いた順と逆の順に閉じ、最後に finally")
    void runSuccess() {
        assertEquals("open:DB;open:FILE;work;close:FILE;close:DB;finally;", BatchJob.run(false),
                "try-with-resources で DB → FILE の順に開くと、閉じる順は FILE → DB になります。try の ( ) の中で開いていますか?");
    }

    @Test
    @DisplayName("失敗時:close が終わってから catch、最後に finally")
    void runFailure() {
        assertEquals("open:DB;open:FILE;work;close:FILE;close:DB;catch:書き込み失敗;finally;",
                BatchJob.run(true),
                "fail が true なら work の後に IllegalStateException(\"書き込み失敗\") を投げます。リソースは catch より前に自動で閉じられます");
    }

    @Test
    @DisplayName("readLimit:数字ならそのまま返す")
    void readLimitOk() {
        assertEquals(500, BatchJob.readLimit("500"), "\"500\" は 500 を返してください");
        assertEquals(0, BatchJob.readLimit("0"), "\"0\" は 0 を返してください");
    }

    @Test
    @DisplayName("readLimit:変換失敗は原因つきで BatchConfigException にラップする")
    void readLimitWrap() {
        BatchConfigException e = assertThrows(BatchConfigException.class,
                () -> BatchJob.readLimit("12x"),
                "変換に失敗したら BatchConfigException を投げてください(NumberFormatException のままではなく)");
        assertEquals("件数の設定が不正: 12x", e.getMessage(),
                "メッセージは \"件数の設定が不正: \" + text です");
        assertTrue(e.getCause() instanceof NumberFormatException,
                "原因(cause)が失われています。new BatchConfigException(メッセージ, e) のように catch した例外を渡してください");
    }

    @Test
    @DisplayName("readLimit:null も BatchConfigException になる")
    void readLimitNull() {
        BatchConfigException e = assertThrows(BatchConfigException.class,
                () -> BatchJob.readLimit(null),
                "Integer.parseInt(null) も NumberFormatException なので、同じく BatchConfigException にラップされます");
        assertSame(NumberFormatException.class, e.getCause().getClass(),
                "cause には catch した NumberFormatException をそのまま渡してください");
    }
}
