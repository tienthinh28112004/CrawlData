package org.CrawlUrlPhim.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class UrlRepository {
    private static final Logger logger = LoggerFactory.getLogger(UrlRepository.class);
    private static final URI DEFAULT_SITEMAP = URI.create("https://toivote.com/sitemap.xml");
    private static final int DEFAULT_LIMIT = 100;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private UrlRepository() {
    }

    public static List<String> getUrls() {
        try {
            return discoverMovieUrls(DEFAULT_SITEMAP, DEFAULT_LIMIT);
        } catch (Exception exception) {
            logger.warn("Failed to discover URLs from sitemap: {}", exception.getMessage());
            return List.of();
        }
    }

    private static List<String> discoverMovieUrls(URI sitemapUri, int limit) throws IOException, InterruptedException {
        List<String> movieUrls = new ArrayList<>(limit);
        Queue<URI> queue = new ArrayDeque<>();
        Set<URI> visited = new HashSet<>();
        queue.add(sitemapUri);

        while (!queue.isEmpty() && movieUrls.size() < limit) {
            URI current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }

            Document document = fetchXml(current);
            boolean sitemapIndex = document.select("sitemap loc").first() != null;
            if (sitemapIndex) {
                for (Element element : document.select("sitemap loc")) {
                    URI nested = current.resolve(element.text().trim());
                    if (!visited.contains(nested)) {
                        queue.add(nested);
                    }
                }
                continue;
            }

            for (Element element : document.select("url loc")) {
                URI movieUrl = current.resolve(element.text().trim());
                if (isMovieUrl(movieUrl)) {
                    movieUrls.add(movieUrl.toString());
                    if (movieUrls.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return movieUrls;
    }

    private static Document fetchXml(URI uri) throws IOException, InterruptedException {
        return Jsoup.connect(uri.toString())
                .userAgent(USER_AGENT)
                .timeout(20000)
                .ignoreContentType(true)
                .get();
    }

    private static boolean isMovieUrl(URI uri) {
        return uri.getPath() != null && uri.getPath().startsWith("/movie/");
    }
}
