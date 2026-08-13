package org.CrawlUrlPhim.crawler;

import org.CrawlUrlPhim.model.Movie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MovieCrawler {
    private static final Logger logger = LoggerFactory.getLogger(MovieCrawler.class);
    private static final int TIMEOUT_MS = 20000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern RUNTIME_PATTERN = Pattern.compile("(\\d+)\\s*phút", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String COUNTRY_LABEL = "\u0110\u1EA5t n\u01B0\u1EDBc";
    private static final String DIRECTOR_LABEL = "\u0110\u1EA1o di\u1EC5n";
    private static final String ACTOR_LABEL = "\u0110i\u1EC5n vi\u00EAn";
    private static final String SUMMARY_LABEL = "T\u00D3M T\u1EAET";
    private static final String GENRE_LABEL = "Th\u1EC3 lo\u1EA1i";
    private static final String RELATED_END_LABEL = "\u0110\u1EC1 xu\u1EA5t";
    private static final String FAQ_LABEL = "C\u00E2u h\u1ECFi th\u01B0\u1EDDng g\u1EB7p";
    private static final String COMMENT_LABEL = "B\u00ECnh lu\u1EADn";
    private static final String EDIT_LABEL = "\u0110\u1EC1 xu\u1EA5t s\u1EEDa";
    private static final String BACK_LABEL = "Quay l\u1EA1i";

    public Movie crawl(String url) {
        logger.info("Crawling: {}", url);
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            String bodyText = bodyText(document);

            Movie movie = new Movie();
            movie.setId(extractIdFromUrl(url));
            movie.setUrl(url);
            movie.setTitle(firstNonBlank(selectTitle(document), extractHeadingFromBody(bodyText), fallbackTitle(url)));
            movie.setYear(extractYear(bodyText));
            movie.setCountry(extractLabeledValue(bodyText, COUNTRY_LABEL));
            movie.setContentType(extractType(bodyText));
            movie.setRuntimeMinutes(extractRuntimeMinutes(bodyText));
            movie.setSummary(extractSummary(bodyText));
            movie.setGenres(extractGenres(document));
            movie.setDirectors(extractPeople(bodyText, DIRECTOR_LABEL, ACTOR_LABEL));
            movie.setActors(extractPeople(bodyText, ACTOR_LABEL, "Reviews", RELATED_END_LABEL, FAQ_LABEL));
            movie.setCrawledAt(java.time.Instant.now());
            return movie;
        } catch (IOException exception) {
            logger.error("Failed to fetch URL {}: {}", url, exception.getMessage());
            return null;
        }
    }

    private String bodyText(Document document) {
        return document.body() != null ? document.body().text() : document.text();
    }

    private String selectTitle(Document document) {
        Element heading = document.select("h1").first();
        return heading != null ? normalize(heading.text()) : "";
    }

    private String extractHeadingFromBody(String bodyText) {
        Matcher matcher = Pattern.compile(
                        "#\\s*(.+?)\\s*(?:Phim l\u1EBB|Phim b\u1ED9|0upvote|" + EDIT_LABEL + "|" + "Th\u00F4ng tin|Reviews|" + FAQ_LABEL + "|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL)
                .matcher(bodyText.replace('\u00a0', ' '));
        if (matcher.find()) {
            return normalize(matcher.group(1));
        }
        return "";
    }

    private String extractYear(String bodyText) {
        Matcher matcher = YEAR_PATTERN.matcher(bodyText);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group());
            if (value >= 1900 && value <= 2100) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Integer extractRuntimeMinutes(String bodyText) {
        Matcher matcher = RUNTIME_PATTERN.matcher(bodyText);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String extractType(String bodyText) {
        String normalized = normalize(bodyText);
        if (normalized.contains("Phim b\u1ED9")) {
            return "Phim b\u1ED9";
        }
        if (normalized.contains("Phim l\u1EBB")) {
            return "Phim l\u1EBB";
        }
        return null;
    }

    private String extractSummary(String bodyText) {
        int start = indexOfIgnoreCase(bodyText, SUMMARY_LABEL);
        if (start < 0) {
            return null;
        }
        int end = indexOfAnyIgnoreCase(bodyText, start + 1, COMMENT_LABEL, FAQ_LABEL, BACK_LABEL);
        String chunk = end > start ? bodyText.substring(start, end) : bodyText.substring(start);
        chunk = chunk.replaceFirst("(?is).*?" + SUMMARY_LABEL, "");
        return normalize(chunk);
    }

    private String extractLabeledValue(String bodyText, String label) {
        String pattern = Pattern.quote(label) + "\\s+(.+?)(?=\\s+(?:"
                + GENRE_LABEL + "|" + DIRECTOR_LABEL + "|" + ACTOR_LABEL + "|" + SUMMARY_LABEL + "|" + FAQ_LABEL + "|" + COMMENT_LABEL + "|$))";
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL)
                .matcher(bodyText.replace('\u00a0', ' '));
        if (matcher.find()) {
            return normalize(matcher.group(1));
        }
        return null;
    }

    private List<String> extractGenres(Document document) {
        List<String> genres = new ArrayList<>();
        for (Element element : document.select("a[href^=/genre/]")) {
            String text = normalize(element.text());
            if (!text.isBlank() && !genres.contains(text)) {
                genres.add(text);
            }
        }
        return genres;
    }

    private List<String> extractPeople(String bodyText, String sectionLabel, String... stopLabels) {
        int start = indexOfIgnoreCase(bodyText, sectionLabel);
        if (start < 0) {
            return List.of();
        }
        int end = indexOfAnyIgnoreCase(bodyText, start + sectionLabel.length(), stopLabels);
        String chunk = end > start ? bodyText.substring(start, end) : bodyText.substring(start);
        chunk = chunk.replaceFirst("(?is).*?" + Pattern.quote(sectionLabel), "");

        List<String> people = new ArrayList<>();
        for (String token : chunk.split("[\\n,;•|]+")) {
            String cleaned = normalize(token);
            if (cleaned.isBlank() || cleaned.equalsIgnoreCase(sectionLabel)) {
                continue;
            }
            if (!people.contains(cleaned) && cleaned.length() > 1) {
                people.add(cleaned);
            }
        }
        return people;
    }

    private int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private int indexOfAnyIgnoreCase(String text, int fromIndex, String... needles) {
        int best = -1;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            int idx = lower.indexOf(needle.toLowerCase(Locale.ROOT), fromIndex);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return normalize(value);
            }
        }
        return null;
    }

    private String fallbackTitle(String url) {
        int index = url.lastIndexOf('/');
        return index >= 0 ? url.substring(index + 1) : url;
    }

    private String extractIdFromUrl(String url) {
        Pattern pattern = Pattern.compile("/movie/([a-f0-9\\-]{36})");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : url;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
