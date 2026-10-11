import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** レスポンスの形 */
record Task(long id, String title, boolean done) {
}

/** POST で受け取る JSON の形。id や done はクライアントに決めさせない */
record CreateTaskRequest(String title) {
}

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final Map<Long, Task> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable long id) {
        return ResponseEntity.of(Optional.ofNullable(store.get(id)));
    }

    @PostMapping
    public ResponseEntity<Task> create(CreateTaskRequest req) { // TODO: 引数に @RequestBody を付ける
        // TODO: title が null または空白だけなら 400(ResponseEntity.badRequest().build())

        // TODO: seq.incrementAndGet() で ID を採番し、done = false の Task を store に保存する

        // TODO: 201 Created + Location: /api/tasks/{id} + 本文に Task
        //       ヒント: ResponseEntity.created(URI.create("/api/tasks/" + id)).body(task)
        return ResponseEntity.ok().build(); // ← ダミー
    }
}
