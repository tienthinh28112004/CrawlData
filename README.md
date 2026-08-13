# Bai2_6

Cong cu crawl du lieu phim viet bang Java, thu thap thong tin tu [toivote.com](https://toivote.com), luu vao SQLite va cung cap HTTP API de tra cuu du lieu phim.

## Tong quan

Du an gom 2 che do chinh:

- **Che do crawl**: lay danh sach URL phim tu sitemap cua toivote.com, crawl thong tin, luu vao SQLite va ghi them file backup JSONL
- **Che do web service**: cung cap API dang nhap va tra du lieu phim theo URL, co cache TTL va rate limit

## Tinh nang

- Thu thap thong tin phim: tieu de, nam san xuat, quoc gia, the loai, dao dien, dien vien
- Lay danh sach URL phim tu `https://toivote.com/sitemap.xml`
- Luu du lieu vao SQLite
- Ghi backup de phong vao file JSONL tren disk
- Bo qua phim da co san trong database
- Web service tra ve JSON da format dep
- Dang nhap truoc khi su dung API
- Cache TTL cho API `/movie` de giam so lan truy cap database
- Rate limit cho tung user
- Dong goi jar co day du thu vien kem theo

## Cong nghe su dung

| Thanh phan | Thu vien / Cong nghe |
|---|---|
| Ngon ngu | Java 21 |
| Build | Maven |
| Crawl HTML | Jsoup 1.17.2 |
| JSON | Gson 2.10.1 |
| Cache | Guava 33.2.1-jre |
| Co so du lieu | SQLite |
| JDBC | sqlite-jdbc 3.47.1.0 |
| Logging | SLF4J 2.0.12 + Logback 1.5.3 |
| Dong goi | `maven-assembly-plugin` (`jar-with-dependencies`) |

## Cau truc co ban

```text
src/main/java/org/CrawlUrlPhim/
    Main.java                   Diem vao, ho tro 2 che do: crawl va server
    cache/CacheTTL.java         Cache tu cai theo Map<K, V> co TTL
    crawler/MovieCrawler.java   Trich xuat du lieu phim tu HTML
    crawler/UrlRepository.java  Tim danh sach URL phim tu sitemap
    db/DatabaseManager.java     Xu ly SQLite
    model/Movie.java            Model du lieu phim
    util/JsonEscaper.java       Ho tro ghi JSON an toan
    util/JsonlBackupWriter.java Ghi backup JSONL
    web/AuthHandler.java        Xu ly POST /login
    web/AuthManager.java        Xac thuc va quan ly token
    web/LoginRequest.java       Body dang nhap
    web/MovieHandler.java       Xu ly GET /movie?url=...
    web/PrimeHandler.java       API demo /prime
    web/RateLimiter.java        Gioi han request theo user
    web/WebServer.java          Khoi dong HTTP server
```

## Co so du lieu

Du an luu du lieu vao file SQLite `data/movies.db`.

Bang chinh:

```text
movies
```

Cot chinh trong bang:

```text
id, url, title, year, country, content_type, runtime_minutes,
summary, genres_json, directors_json, actors_json, crawled_at
```

## Build va chay

### Yeu cau

- Java 21
- Maven 3

### Build project

```bash
mvn clean package
```

Sau khi build xong, file jar chay duoc se nam trong `target/` voi ten:

```text
crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Chay che do crawl

Khong truyen tham so nao:

```bash
java -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Che do nay se:

- Lay danh sach URL phim tu sitemap
- Crawl tung phim
- Luu vao SQLite
- Ghi backup JSONL vao `data/backup/movie-records.jsonl`

### Chay che do web service

```bash
java -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar --server
```

Co the set heap nhu sau:

```bash
java -Xms125m -Xmx512m -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar --server
```

## API

### Dang nhap

`POST /login`

Body:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Phan hoi thanh cong:

```json
{
  "token": "....",
  "username": "admin"
}
```

### Lay thong tin phim

`GET /movie?url=...`

Header:

```text
Authorization: Bearer <token>
```

### API demo prime

`GET /prime?n=10000`

Header:

```text
Authorization: Bearer <token>
```

## Tai khoan mau

- `admin / admin123`
- `user1 / pass1`

## Cache

Du an co 2 lop cache phuc vu hai muc dich:

- `src/main/java/org/CrawlUrlPhim/cache/CacheTTL.java`: cache tu cai theo `Map<K, V>`, co TTL va hit rate
- `src/main/java/org/CrawlUrlPhim/web/MovieHandler.java`: cache Guava cho API `/movie`

Thong so cache cua API `/movie`:

- `expireAfterAccess`: `10s`
- `expireAfterWrite`: `20s`

## Ghi chu

- Database mac dinh: `data/movies.db`
- Backup mac dinh: `data/backup/movie-records.jsonl`
- Project co `Dockerfile` va `run-server.sh` de phuc vu viec deploy sau nay
- `compile-check.ps1` va `verify-server.ps1` la script ho tro kiem tra local

## Ket qua dau ra cua che do crawl

```text
URLs da xu ly  : 100
Luu moi        : 87
Da co trong DB : 10
That bai       : 3
Tong trong DB  : 87
```

