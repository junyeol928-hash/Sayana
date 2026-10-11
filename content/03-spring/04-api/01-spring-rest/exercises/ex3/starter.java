import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 保存用のクラス(本物のアプリなら @Entity)。外に見せたくない項目も持っている */
class TaskEntity {
    Long id;
    String title;
    boolean done;
    LocalDate dueDate;
    String internalMemo;

    TaskEntity(Long id, String title, LocalDate dueDate, String internalMemo) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.internalMemo = internalMemo;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public boolean isDone() { return done; }
    public LocalDate getDueDate() { return dueDate; }
    public String getInternalMemo() { return internalMemo; }
}

/** API が返す形(DTO)。見せてよい項目だけ */
record TaskResponse(long id, String title, boolean done, LocalDate dueDate) {
    static TaskResponse from(TaskEntity e) {
        // TODO: id / title / done / dueDate を詰め替えて返す(internalMemo は入れない)
        return null; // ← ダミー
    }
}

/** PATCH の本文。送られてこなかった項目は null になる */
record UpdateTaskRequest(String title, Boolean done, LocalDate dueDate) {
}

@RestController
@RequestMapping("/api/tasks")
public class TaskApi {

    private final Map<Long, TaskEntity> store = new ConcurrentHashMap<>();

    public TaskApi() {
        store.put(1L, new TaskEntity(1L, "見積書を作る", LocalDate.of(2026, 10, 31), "A社は値引き交渉が厳しい"));
        store.put(2L, new TaskEntity(2L, "請求書を送る", LocalDate.of(2026, 11, 5), "担当者が11月に異動予定"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(@PathVariable long id) {
        return ResponseEntity.of(Optional.ofNullable(store.get(id)).map(TaskResponse::from));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> patch(@PathVariable long id, @RequestBody UpdateTaskRequest req) {
        // TODO: store から取り出し、無ければ 404(ResponseEntity.notFound().build())

        // TODO: req の項目のうち null でないものだけを TaskEntity に反映する
        //       (title / done / dueDate。フィールドは同じファイル内なので e.title = ... のように書ける)

        // TODO: 200 + 更新後の TaskResponse を返す
        return ResponseEntity.ok().build(); // ← ダミー
    }
}
