package com.ktguru.chat.service;

import com.ktguru.chat.model.Issue;
import com.ktguru.chat.repository.IssueRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final IssueRepository issueRepository;

    @Value("${kt-guru.ai.rag.max-segments}")
    private int maxSegments;

    @Value("${kt-guru.ai.rag.min-score}")
    private double minScore;

    public List<EmbeddingMatch<TextSegment>> vectorSearch(String query) {
        Embedding q = embeddingModel.embed(query).content();
        return embeddingStore.findRelevant(q, maxSegments, minScore);
    }

    public RetrievalBundle retrieve(String query) {
        List<EmbeddingMatch<TextSegment>> matches = vectorSearch(query);
        double bestVec = matches.stream().map(EmbeddingMatch::score).max(Double::compareTo).orElse(0.0);
        List<Issue> issues = keywordIssues(query);
        String block = assembleContext(matches, issues);
        return new RetrievalBundle(block, bestVec);
    }

    public List<Issue> keywordIssues(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return issueRepository.searchByText(q, PageRequest.of(0, 8));
    }

    private static String assembleContext(List<EmbeddingMatch<TextSegment>> matches, List<Issue> issues) {
        List<String> parts = new ArrayList<>();

        if (!issues.isEmpty()) {
            parts.add("### Matched issue records (structured)");
            for (Issue i : issues) {
                parts.add(formatIssue(i));
            }
        }

        if (!matches.isEmpty()) {
            parts.add("### Retrieved knowledge snippets (documents / code / embedded issues)");
            List<EmbeddingMatch<TextSegment>> sorted = matches.stream()
                    .sorted(Comparator.comparing(EmbeddingMatch::score).reversed())
                    .collect(Collectors.toList());
            int n = 1;
            for (EmbeddingMatch<TextSegment> m : sorted) {
                TextSegment seg = m.embedded();
                String src = seg.metadata().get(VectorIndexService.META_SOURCE);
                parts.add("--- Snippet " + n++ + " (relevance " + String.format("%.2f", m.score()) + ", source=" + src + ") ---");
                parts.add(seg.text());
            }
        }

        if (parts.isEmpty()) {
            return "";
        }
        return String.join("\n\n", parts);
    }

    public record RetrievalBundle(String contextBlock, double bestVectorScore) {
    }

    private static String formatIssue(Issue i) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Subject: ").append(i.getSubject()).append("\n  Resolution: ").append(i.getResolution());
        if (i.getRaisedBy() != null) {
            sb.append("\n  Raised by: ").append(i.getRaisedBy());
        }
        if (i.getResolvedBy() != null) {
            sb.append("\n  Resolved by: ").append(i.getResolvedBy());
        }
        if (i.getResolutionTimeMinutes() != null) {
            sb.append("\n  Recorded resolution time (minutes): ").append(i.getResolutionTimeMinutes());
        }
        return sb.toString();
    }
}
