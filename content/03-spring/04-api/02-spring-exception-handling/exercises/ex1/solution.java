import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// ===== 業務例外(Service が投げる) =====
class OrderNotFoundException extends RuntimeException {
    OrderNotFoundException(long id) {
        super("注文 " + id + " は見つかりません");
    }
}

class OrderStateException extends RuntimeException {
    OrderStateException(String message) {
        super(message);
    }
}

// ===== ドメインと Service(用意済み) =====
record Order(long id, String item, String status) {
}

class OrderService {
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();

    OrderService() {
        orders.put(1L, new Order(1, "ボールペン×10", "ORDERED"));
        orders.put(2L, new Order(2, "コピー用紙×5", "SHIPPED"));
    }

    Order find(long id) {
        Order o = orders.get(id);
        if (o == null) {
            throw new OrderNotFoundException(id);
        }
        return o;
    }

    Order cancel(long id) {
        Order o = find(id);
        if ("SHIPPED".equals(o.status())) {
            throw new OrderStateException("注文 " + id + " は発送済みのためキャンセルできません");
        }
        Order canceled = new Order(o.id(), o.item(), "CANCELED");
        orders.put(id, canceled);
        return canceled;
    }

    String issueReceipt(long id) {
        find(id);
        // 外部の領収書サービスにつながらなかった、という想定
        throw new IllegalStateException("receipt-service timeout: http://10.0.3.7:8080/receipts");
    }
}

@RestController
@RequestMapping("/api/orders")
class OrderController {
    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    Order get(@PathVariable long id) {
        return service.find(id);
    }

    @PostMapping("/{id}/cancel")
    Order cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/receipt")
    String receipt(@PathVariable long id) {
        return service.issueReceipt(id);
    }
}

/** エラー時に返す JSON の形 */
record ErrorResponse(String code, String message) {
}

// ===== ここを完成させる =====
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OrderStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ORDER_STATE_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // 本物のアプリでは log.error("想定外のエラー", ex); で詳細(スタックトレース)をログに残す
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "システムエラーが発生しました"));
    }
}
