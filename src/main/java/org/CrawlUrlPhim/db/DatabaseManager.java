package org.CrawlUrlPhim.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.CrawlUrlPhim.model.Movie;
import org.CrawlUrlPhim.util.JsonEscaper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson GSON = new Gson();

    private final Path databasePath;
    private Connection connection;

    public DatabaseManager() {
        this(resolveDefaultDatabasePath());
    }

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
    }

    public void init() throws java.sql.SQLException {
        if (databasePath.getParent() != null) {
            try {
                Files.createDirectories(databasePath.getParent());
            } catch (Exception exception) {
                throw new java.sql.SQLException("Cannot create database directory", exception);
            }
        }

        String jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        connection = DriverManager.getConnection(jdbcUrl);
        connection.setAutoCommit(false);
        createTables();
        logger.info("SQLite database initialised at {}", databasePath.toAbsolutePath());
    }

    public boolean saveMovie(Movie movie) {
        if (movie == null || movie.getId() == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO movies (
                    id, url, title, year, country, content_type, runtime_minutes,
                    summary, genres_json, directors_json, actors_json, crawled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(url) DO UPDATE SET
                    title = excluded.title,
                    year = excluded.year,
                    country = excluded.country,
                    content_type = excluded.content_type,
                    runtime_minutes = excluded.runtime_minutes,
                    summary = excluded.summary,
                    genres_json = excluded.genres_json,
                    directors_json = excluded.directors_json,
                    actors_json = excluded.actors_json,
                    crawled_at = excluded.crawled_at
                """)) {
            statement.setString(1, movie.getId());
            statement.setString(2, movie.getUrl());
            statement.setString(3, movie.getTitle());
            statement.setString(4, movie.getYear());
            statement.setString(5, emptyToNull(movie.getCountry()));
            statement.setString(6, emptyToNull(movie.getContentType()));
            if (movie.getRuntimeMinutes() == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, movie.getRuntimeMinutes());
            }
            statement.setString(8, emptyToNull(movie.getSummary()));
            statement.setString(9, GSON.toJson(movie.getGenres() == null ? List.of() : movie.getGenres()));
            statement.setString(10, GSON.toJson(movie.getDirectors() == null ? List.of() : movie.getDirectors()));
            statement.setString(11, GSON.toJson(movie.getActors() == null ? List.of() : movie.getActors()));
            statement.setString(12, movie.getCrawledAt() == null ? Instant.now().toString() : movie.getCrawledAt().toString());
            statement.executeUpdate();
            connection.commit();
            return true;
        } catch (Exception exception) {
            logger.error("Failed to save movie {}: {}", movie.getTitle(), exception.getMessage());
            try {
                connection.rollback();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    public Movie getMovieByUrl(String url) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, url, title, year, country, content_type, runtime_minutes,
                       summary, genres_json, directors_json, actors_json, crawled_at
                FROM movies
                WHERE url = ?
                """)) {
            statement.setString(1, url);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapRow(resultSet);
            }
        } catch (Exception exception) {
            logger.error("Failed to fetch movie by url {}: {}", url, exception.getMessage());
            return null;
        }
    }

    public int getMovieCount() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM movies")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (Exception exception) {
            logger.error("Failed to count movies: {}", exception.getMessage());
            return 0;
        }
    }

    public List<Movie> getAllMovies(int offset, int limit) {
        List<Movie> movies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, url, title, year, country, content_type, runtime_minutes,
                       summary, genres_json, directors_json, actors_json, crawled_at
                FROM movies
                ORDER BY title
                LIMIT ? OFFSET ?
                """)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    movies.add(mapRow(resultSet));
                }
            }
        } catch (Exception exception) {
            logger.error("Failed to fetch movie list: {}", exception.getMessage());
        }
        return movies;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void createTables() throws java.sql.SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS movies (
                        id TEXT PRIMARY KEY,
                        url TEXT NOT NULL UNIQUE,
                        title TEXT,
                        year TEXT,
                        country TEXT,
                        content_type TEXT,
                        runtime_minutes INTEGER,
                        summary TEXT,
                        genres_json TEXT,
                        directors_json TEXT,
                        actors_json TEXT,
                        crawled_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_movies_title ON movies(title)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_movies_year ON movies(year)");
            connection.commit();
        }
    }

    private Movie mapRow(ResultSet resultSet) throws Exception {
        Movie movie = new Movie();
        movie.setId(resultSet.getString("id"));
        movie.setUrl(resultSet.getString("url"));
        movie.setTitle(resultSet.getString("title"));
        movie.setYear(resultSet.getString("year"));
        movie.setCountry(resultSet.getString("country"));
        movie.setContentType(resultSet.getString("content_type"));
        int runtime = resultSet.getInt("runtime_minutes");
        movie.setRuntimeMinutes(resultSet.wasNull() ? null : runtime);
        movie.setSummary(resultSet.getString("summary"));
        movie.setGenres(parseList(resultSet.getString("genres_json")));
        movie.setDirectors(parseList(resultSet.getString("directors_json")));
        movie.setActors(parseList(resultSet.getString("actors_json")));
        movie.setCrawledAt(Instant.parse(resultSet.getString("crawled_at")));
        return movie;
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return GSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Path resolveDefaultDatabasePath() {
        Path[] candidates = new Path[] {
                Paths.get("data", "movies.db"),
                Paths.get("..", "data", "movies.db"),
                Paths.get("..", "..", "data", "movies.db")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
