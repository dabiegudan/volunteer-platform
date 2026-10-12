package com.volunteer.ai.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private final List<Map<String, Object>> documents = new CopyOnWriteArrayList<>();

    public void addDocument(String content, Map<String, String> metadata) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("content", content.trim());
        document.put("category", metadata != null ? metadata.getOrDefault("category", "GENERAL") : "GENERAL");
        document.put("source", metadata != null ? metadata.getOrDefault("source", "MANUAL") : "MANUAL");
        document.put("metadata", metadata != null ? new LinkedHashMap<>(metadata) : Map.of());
        document.put("createTime", LocalDateTime.now());
        documents.add(document);
    }

    public void addText(String text, String category, String source) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("category", category != null && !category.isBlank() ? category : "GENERAL");
        metadata.put("source", source != null && !source.isBlank() ? source : "MANUAL");
        addDocument(text, metadata);
    }

    public List<Map<String, Object>> search(String query, int maxResults, double minScore) {
        if (query == null || query.trim().isEmpty() || documents.isEmpty()) {
            return new ArrayList<>();
        }

        int limit = Math.max(1, Math.min(maxResults, 50));
        double threshold = Math.max(0.0, Math.min(minScore, 1.0));
        Set<String> queryTokens = tokenize(query);

        return documents.stream()
                .map(document -> withScore(document, scoreDocument(document, query.trim(), queryTokens)))
                .filter(document -> (double) document.get("score") >= threshold)
                .sorted(Comparator.comparingDouble((Map<String, Object> doc) -> (double) doc.get("score")).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> searchByCategory(String query, String category, int maxResults) {
        if (category == null || category.isBlank()) {
            return search(query, maxResults, 0.0);
        }

        return search(query, Math.max(maxResults, 50), 0.0).stream()
                .filter(document -> category.equalsIgnoreCase(String.valueOf(document.get("category"))))
                .limit(Math.max(1, maxResults))
                .collect(Collectors.toList());
    }

    public String getContextForQuery(String query, int maxSegments) {
        List<Map<String, Object>> results = search(query, maxSegments, 0.2);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\u4ee5\u4e0b\u662f\u76f8\u5173\u7684\u77e5\u8bc6\u5e93\u5185\u5bb9\uff1a\n\n");

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            context.append("[").append(i + 1).append("] ").append(result.get("content")).append("\n\n");
        }

        return context.toString();
    }

    private Map<String, Object> withScore(Map<String, Object> document, double score) {
        Map<String, Object> result = new LinkedHashMap<>(document);
        result.put("score", Math.round(score * 1000.0) / 1000.0);
        return result;
    }

    private double scoreDocument(Map<String, Object> document, String query, Set<String> queryTokens) {
        String content = String.valueOf(document.getOrDefault("content", "")).toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (content.contains(normalizedQuery)) {
            return 1.0;
        }
        if (queryTokens.isEmpty()) {
            return 0.0;
        }

        long hits = queryTokens.stream().filter(content::contains).count();
        return hits / (double) queryTokens.size();
    }

    private Set<String> tokenize(String text) {
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,;，；、。.!?！？]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toSet());
    }
}
