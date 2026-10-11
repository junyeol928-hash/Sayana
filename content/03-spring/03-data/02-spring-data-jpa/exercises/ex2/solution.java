import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import org.springframework.data.jpa.repository.JpaRepository;

// 書籍エンティティ(完成済み。プロパティ名を確認するのに使ってください)
@Entity
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;          // 書名
    private String author;         // 著者名
    private String category;       // カテゴリ("技術書" "小説" など)
    private int price;             // 税込価格(円)
    private int publishedYear;     // 発行年
    private int stock;             // 在庫数

    protected Book() {
    }

    public Book(String title, String author, String category,
                int price, int publishedYear, int stock) {
        this.title = title;
        this.author = author;
        this.category = category;
        this.price = price;
        this.publishedYear = publishedYear;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public int getPrice() { return price; }
    public int getPublishedYear() { return publishedYear; }
    public int getStock() { return stock; }
}

// 書籍のリポジトリ。メソッド名だけで検索条件を表す
interface BookRepository extends JpaRepository<Book, Long> {

    // 1. 著者名が完全一致する本
    List<Book> findByAuthor(String author);

    // 2. 書名に keyword を含む本(部分一致)
    List<Book> findByTitleContaining(String keyword);

    // 3. 価格が min 以上 max 以下の本を、安い順に
    List<Book> findByPriceBetweenOrderByPriceAsc(int min, int max);

    // 4. 指定カテゴリの本のうち、発行年が新しいもの上位3冊
    List<Book> findTop3ByCategoryOrderByPublishedYearDesc(String category);

    // 5. 在庫数が threshold 未満の本の冊数(発注候補の件数)
    long countByStockLessThan(int threshold);

    // 6. 著者名が一致し、かつ発行年が year 以降の本
    List<Book> findByAuthorAndPublishedYearGreaterThanEqual(String author, int year);
}
