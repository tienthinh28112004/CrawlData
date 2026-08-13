# Bài 2.6

Công cụ crawl dữ liệu phim viết bằng Java, thu thập thông tin từ [toivote.com](https://toivote.com), lưu vào SQLite và cung cấp HTTP API để tra cứu dữ liệu phim.

## Tổng quan

Dự án gồm 2 chế độ chính:

- **Chế độ crawl**: lấy danh sách URL phim từ sitemap của toivote.com, crawl thông tin, lưu vào SQLite và ghi thêm file backup JSONL
- **Chế độ web service**: cung cấp API đăng nhập và tra dữ liệu phim theo URL, có cache TTL và rate limit

## Tính năng

- Thu thập thông tin phim: tiêu đề, năm sản xuất, quốc gia, thể loại, đạo diễn, diễn viên
- Lấy danh sách URL phim từ `https://toivote.com/sitemap.xml`
- Lưu dữ liệu vào SQLite
- Ghi backup dự phòng vào file JSONL trên disk
- Bỏ qua phim đã có sẵn trong database
- Web service trả về JSON đã format đẹp
- Đăng nhập trước khi sử dụng API
- Cache TTL cho API `/movie` để giảm số lần truy cập database
- Rate limit cho từng user
- Đóng gói JAR có đầy đủ thư viện kèm theo

## Công nghệ sử dụng

| Thành phần | Thư viện / Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Build | Maven |
| Crawl HTML | Jsoup 1.17.2 |
| JSON | Gson 2.10.1 |
| Cache | Guava 33.2.1-jre |
| Cơ sở dữ liệu | SQLite |
| JDBC | sqlite-jdbc 3.47.1.0 |
| Logging | SLF4J 2.0.12 + Logback 1.5.3 |
| Đóng gói | `maven-assembly-plugin` (`jar-with-dependencies`) |

## Cấu trúc cơ bản

```text
src/main/java/org/CrawlUrlPhim/
    Main.java                   Điểm vào, hỗ trợ 2 chế độ: crawl và server
    cache/CacheTTL.java         Cache tự cài theo Map<K, V> có TTL
    crawler/MovieCrawler.java   Trích xuất dữ liệu phim từ HTML
    crawler/UrlRepository.java  Tìm danh sách URL phim từ sitemap
    db/DatabaseManager.java     Xử lý SQLite
    model/Movie.java            Model dữ liệu phim
    util/JsonEscaper.java       Hỗ trợ ghi JSON an toàn
    util/JsonlBackupWriter.java Ghi backup JSONL
    web/AuthHandler.java        Xử lý POST /login
    web/AuthManager.java        Xác thực và quản lý token
    web/LoginRequest.java       Body đăng nhập
    web/MovieHandler.java       Xử lý GET /movie?url=...
    web/PrimeHandler.java       API demo /prime
    web/RateLimiter.java        Giới hạn request theo user
    web/WebServer.java          Khởi động HTTP server
```

## Cơ sở dữ liệu

Dự án lưu dữ liệu vào file SQLite:

```text
data/movies.db
```

Bảng chính:

```text
movies
```

Các cột chính trong bảng:

```text
id
url
title
year
country
content_type
runtime_minutes
summary
genres_json
directors_json
actors_json
crawled_at
```

## Build và chạy

### Yêu cầu

- Java 21
- Maven 3

### Build project

```bash
mvn clean package
```

Sau khi build xong, file JAR chạy được sẽ nằm trong `target/` với tên:

```text
crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Chạy chế độ crawl

Không truyền tham số:

```bash
java -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Chế độ này sẽ:

- Lấy danh sách URL phim từ sitemap
- Crawl từng phim
- Lưu dữ liệu vào SQLite
- Ghi backup JSONL vào `data/backup/movie-records.jsonl`
- Bỏ qua các phim đã tồn tại trong database

### Chạy chế độ web service

```bash
java -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar --server
```

Có thể thiết lập heap:

```bash
java -Xms125m -Xmx512m -jar target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar --server
```

## API

### Đăng nhập

`POST /login`

Body:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Phản hồi thành công:

```json
{
  "token": "....",
  "username": "admin"
}
```

### Lấy thông tin phim

`GET /movie?url=...`

Header:

```text
Authorization: Bearer <token>
```

API sử dụng URL phim để tìm thông tin tương ứng trong database.

### API demo prime

`GET /prime?n=10000`

Header:

```text
Authorization: Bearer <token>
```

API này yêu cầu xác thực trước khi sử dụng.

## Tài khoản mẫu

| Username | Password |
|---|---|
| `admin` | `admin123` |
| `user1` | `pass1` |

## Cache

Dự án có 2 lớp cache phục vụ hai mục đích:

- `src/main/java/org/CrawlUrlPhim/cache/CacheTTL.java`: cache tự cài theo `Map<K, V>`, có TTL và hit rate
- `src/main/java/org/CrawlUrlPhim/web/MovieHandler.java`: sử dụng Guava Cache cho API `/movie`

Thông số cache của API `/movie`:

- `expireAfterAccess`: `10s`
- `expireAfterWrite`: `20s`

Mục đích của cache là giảm số lần truy cập database khi cùng một URL phim được yêu cầu nhiều lần trong thời gian ngắn.

## Rate Limit

API web service có cơ chế giới hạn request theo từng user.

Mỗi user được theo dõi riêng dựa trên thông tin xác thực trong request. Khi vượt quá giới hạn, server sẽ từ chối request thay vì tiếp tục xử lý.

## Quy trình crawl

Quy trình crawl hoạt động theo các bước:

1. Đọc sitemap từ `https://toivote.com/sitemap.xml`
2. Lấy danh sách URL phim
3. Kiểm tra URL đã tồn tại trong SQLite hay chưa
4. Bỏ qua những phim đã có trong database
5. Gửi request lấy HTML của trang phim
6. Sử dụng Jsoup để phân tích HTML
7. Trích xuất thông tin phim
8. Lưu dữ liệu vào SQLite
9. Ghi thêm bản backup vào file JSONL

Thông tin phim được thu thập gồm:

- Tiêu đề
- Năm sản xuất
- Quốc gia
- Loại nội dung
- Thời lượng
- Tóm tắt
- Thể loại
- Đạo diễn
- Diễn viên
- Thời gian crawl

## Backup JSONL

Ngoài SQLite, dữ liệu phim được ghi thêm vào file:

```text
data/backup/movie-records.jsonl
```

Mỗi dòng trong file tương ứng với một bản ghi phim ở định dạng JSON.

Việc sử dụng JSONL giúp có thêm bản sao dữ liệu trên disk trong trường hợp database gặp vấn đề.

## Ghi chú

- Database mặc định: `data/movies.db`
- Backup mặc định: `data/backup/movie-records.jsonl`
- Sitemap mặc định: `https://toivote.com/sitemap.xml`
- Project có `Dockerfile` để phục vụ việc đóng gói và triển khai
- `run-server.sh` hỗ trợ chạy server
- `compile-check.ps1` hỗ trợ kiểm tra việc biên dịch trên Windows
- `verify-server.ps1` hỗ trợ kiểm tra server local
- Chế độ mặc định khi không truyền tham số là chế độ crawl
- Tham số `--server` dùng để khởi động web service

## Kết quả đầu ra của chế độ crawl

Ví dụ:

```text
URLs đã xử lý  : 100
Lưu mới        : 87
Đã có trong DB : 10
Thất bại       : 3
Tổng trong DB  : 87
```
