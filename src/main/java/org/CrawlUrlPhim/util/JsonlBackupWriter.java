package org.CrawlUrlPhim.util;

import org.CrawlUrlPhim.model.Movie;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonlBackupWriter implements AutoCloseable {
    private final BufferedWriter writer;

    public JsonlBackupWriter(Path backupPath) throws IOException {
        Path parent = backupPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(backupPath, StandardCharsets.UTF_8);
    }

    public synchronized void append(Movie movie) throws IOException {
        writer.write(toJson(movie));
        writer.newLine();
        writer.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }

    private String toJson(Movie movie) {
        return "{"
                + "\"id\":" + JsonEscaper.quote(movie.getId()) + ','
                + "\"url\":" + JsonEscaper.quote(movie.getUrl()) + ','
                + "\"title\":" + JsonEscaper.quote(movie.getTitle()) + ','
                + "\"year\":" + JsonEscaper.quote(movie.getYear()) + ','
                + "\"country\":" + JsonEscaper.quote(movie.getCountry()) + ','
                + "\"contentType\":" + JsonEscaper.quote(movie.getContentType()) + ','
                + "\"runtimeMinutes\":" + (movie.getRuntimeMinutes() == null ? "null" : movie.getRuntimeMinutes()) + ','
                + "\"summary\":" + JsonEscaper.quote(movie.getSummary()) + ','
                + "\"genres\":" + JsonEscaper.toJsonArray(movie.getGenres()) + ','
                + "\"directors\":" + JsonEscaper.toJsonArray(movie.getDirectors()) + ','
                + "\"actors\":" + JsonEscaper.toJsonArray(movie.getActors()) + ','
                + "\"crawledAt\":" + JsonEscaper.quote(movie.getCrawledAt() == null ? null : movie.getCrawledAt().toString())
                + '}';
    }
}
