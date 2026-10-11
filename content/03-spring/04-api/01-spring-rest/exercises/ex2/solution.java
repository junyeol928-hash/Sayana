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
    public ResponseEntity<Task> create(@RequestBody CreateTaskRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        long id = seq.incrementAndGet();
        Task task = new Task(id, req.title(), false);
        store.put(id, task);
        URI location = URI.create("/api/tasks/" + id);
        return ResponseEntity.created(location).body(task);
    }
}
