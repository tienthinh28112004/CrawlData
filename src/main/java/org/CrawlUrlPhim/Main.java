package org.CrawlUrlPhim;

import org.CrawlUrlPhim.crawler.MovieCrawler;
import org.CrawlUrlPhim.crawler.UrlRepository;
import org.CrawlUrlPhim.db.DatabaseManager;
import org.CrawlUrlPhim.model.Movie;
import org.CrawlUrlPhim.util.JsonlBackupWriter;
import org.CrawlUrlPhim.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long REQUEST_DELAY_MS = 1000;

    private Main() {
    }

    public static void main(String[] args) {
        boolean serverMode = Arrays.asList(args).contains("--server");

        DatabaseManager db = new DatabaseManager();
        try {
            db.init();
        } catch (SQLException exception) {
            logger.error("Failed to initialize database: {}", exception.getMessage(), exception);
            System.exit(1);
        }

        if (serverMode) {
            runServer(db);
        } else {
            runCrawler(db);
        }
    }

    private static void runServer(DatabaseManager db) {
        logger.info("=== Starting Web Server ===");
        try {
            WebServer server = new WebServer(db);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                db.close();
            }));
            server.start();
        } catch (Exception exception) {
            logger.error("Failed to start web server: {}", exception.getMessage(), exception);
            System.exit(1);
        }
    }

    private static void runCrawler(DatabaseManager db) {
        logger.info("=== CrawlUrl crawler starting ===");
        List<String> urls = UrlRepository.getUrls();
        logger.info("Loaded {} URLs to crawl.", urls.size());

        Path backupPath = Paths.get("data", "backup", "movie-records.jsonl");
        try {
            if (backupPath.getParent() != null) {
                Files.createDirectories(backupPath.getParent());
            }
        } catch (Exception exception) {
            logger.warn("Could not create backup directory: {}", exception.getMessage());
        }

        MovieCrawler crawler = new MovieCrawler();
        int success = 0;
        int failed = 0;
        int skipped = 0;

        try (JsonlBackupWriter backupWriter = new JsonlBackupWriter(backupPath)) {
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                logger.info("[{}/{}] Processing: {}", i + 1, urls.size(), url);

                Movie movie = crawler.crawl(url);
                if (movie != null) {
                    boolean saved = db.saveMovie(movie);
                    if (saved) {
                        backupWriter.append(movie);
                        success++;
                        logger.info("  -> Saved: {} | Year: {}{}",
                                movie.getTitle(),
                                movie.getYear(),
                                formatCountrySuffix(movie.getCountry()));
                    } else {
                        skipped++;
                        logger.info("  -> Skipped (already in DB): {}", movie.getTitle());
                    }
                } else {
                    failed++;
                    logger.warn("  -> Failed to crawl: {}", url);
                }

                if (i < urls.size() - 1) {
                    try {
                        Thread.sleep(REQUEST_DELAY_MS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception exception) {
            logger.error("Error while writing backup: {}", exception.getMessage(), exception);
        }

        int totalInDb = db.getMovieCount();
        logger.info("=== Crawl complete ===");
        logger.info("  URLs processed : {}", urls.size());
        logger.info("  Saved new      : {}", success);
        logger.info("  Already in DB  : {}", skipped);
        logger.info("  Failed/Empty   : {}", failed);
        logger.info("  Total in DB    : {}", totalInDb);
        logger.info("=== Done ===");
        db.close();
    }

    private static String formatCountrySuffix(String country) {
        if (country == null || country.isBlank()) {
            return "";
        }
        return " | Country: " + country;
    }
}
