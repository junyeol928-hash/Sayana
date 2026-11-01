import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

// ===== service(ここを直す) =====
public class KeyRotationService {
    private final KeyStore store;
    private final HsmClient hsm;

    public KeyRotationService(KeyStore store, HsmClient hsm) {
        this.store = store;
        this.hsm = hsm;
    }

    /** 鍵の更新。今の鍵を失効させ、HSM で作った新しい鍵を有効にする */
    @Transactional
    public String rotate(String cardId) {
        store.revokeActive(cardId);                          // 1. 今の ACTIVE な鍵を REVOKED にする
        try {
            String newKeyId = hsm.generateKey(cardId);       // 2. HSM に新しい鍵を作ってもらう
            store.saveActive(cardId, newKeyId);              // 3. 新しい鍵を ACTIVE で保存する
            return newKeyId;
        } catch (HsmException e) {
            // 検査例外のままではロールバックされない。原因を付けた RuntimeException にして投げ直す
            throw new KeyRotationFailedException(cardId, e);
        }
    }
}

// ===== 例外(用意済み) =====
/** HSM(鍵を作る専用装置)との通信に失敗した。検査例外 */
class HsmException extends Exception {
    HsmException(String message) {
        super(message);
    }
}

/** 鍵の更新に失敗した。RuntimeException なので @Transactional の既定でロールバックされる */
class KeyRotationFailedException extends RuntimeException {
    KeyRotationFailedException(String cardId, Throwable cause) {
        super("カード " + cardId + " の鍵の更新に失敗しました", cause);
    }
}

// ===== 外部装置のクライアント(用意済み) =====
interface HsmClient {
    String generateKey(String cardId) throws HsmException;
}

// ===== DB の代わり(用意済み。直さなくてよい) =====
record KeyRow(String keyId, String cardId, String status) {
}

class KeyStore {
    private List<KeyRow> rows = new ArrayList<>();

    void add(String keyId, String cardId, String status) {
        rows.add(new KeyRow(keyId, cardId, status));
    }

    void revokeActive(String cardId) {
        rows.replaceAll(r -> r.cardId().equals(cardId) && r.status().equals("ACTIVE")
                ? new KeyRow(r.keyId(), r.cardId(), "REVOKED") : r);
    }

    void saveActive(String cardId, String keyId) {
        rows.add(new KeyRow(keyId, cardId, "ACTIVE"));
    }

    Optional<String> activeKeyOf(String cardId) {
        return rows.stream().filter(r -> r.cardId().equals(cardId) && r.status().equals("ACTIVE"))
                .map(KeyRow::keyId).findFirst();
    }

    /** 採点用:トランザクション開始時の状態を覚えておく */
    List<KeyRow> snapshot() {
        return List.copyOf(rows);
    }

    /** 採点用:ロールバックのときに開始時の状態へ戻す */
    void restore(List<KeyRow> saved) {
        rows = new ArrayList<>(saved);
    }
}
