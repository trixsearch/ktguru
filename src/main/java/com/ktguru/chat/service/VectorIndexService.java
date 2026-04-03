package com.ktguru.chat.service;

import com.ktguru.chat.model.Issue;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorIndexService {

    public static final String META_SOURCE = "source";
    public static final String SOURCE_ISSUE = "ISSUE";
    public static final String SOURCE_DOCUMENT = "DOCUMENT";
    public static final String SOURCE_CODE = "CODE";
    public static final String SOURCE_SME = "SME_NOTE";

    public static final String META_ISSUE_ID = "issueId";
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_CODE_FILE_ID = "codeFileId";
    public static final String META_TITLE = "title";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @Value("${kt-guru.ai.vector-store.file-path}")
    private String vectorStorePath;

    public synchronized void indexIssueText(long issueId, String subject, String resolution, String raisedBy,
                                            String resolvedBy, Integer resolutionMinutes) {
        addSegments(List.of(issueSegment(issueId, subject, resolution, raisedBy, resolvedBy, resolutionMinutes)));
    }

    public synchronized void indexIssuesBatch(List<Issue> issues) {
        List<TextSegment> segments = new ArrayList<>(issues.size());
        for (Issue issue : issues) {
            segments.add(issueSegment(
                    issue.getId(),
                    issue.getSubject(),
                    issue.getResolution(),
                    issue.getRaisedBy(),
                    issue.getResolvedBy(),
                    issue.getResolutionTimeMinutes()));
        }
        addSegments(segments);
    }

    private static TextSegment issueSegment(long issueId, String subject, String resolution, String raisedBy,
                                            String resolvedBy, Integer resolutionMinutes) {
        String text = buildIssueEmbeddingText(subject, resolution, raisedBy, resolvedBy, resolutionMinutes);
        Metadata md = new Metadata()
                .put(META_SOURCE, SOURCE_ISSUE)
                .put(META_ISSUE_ID, String.valueOf(issueId));
        return TextSegment.from(text, md);
    }

    public synchronized void indexDocumentChunks(long documentId, String title, List<String> chunkContents) {
        List<TextSegment> segments = new ArrayList<>(chunkContents.size());
        for (int i = 0; i < chunkContents.size(); i++) {
            Metadata md = new Metadata()
                    .put(META_SOURCE, SOURCE_DOCUMENT)
                    .put(META_DOCUMENT_ID, String.valueOf(documentId))
                    .put(META_TITLE, title != null ? title : "")
                    .put(META_CHUNK_INDEX, String.valueOf(i));
            segments.add(TextSegment.from(chunkContents.get(i), md));
        }
        addSegments(segments);
    }

    public synchronized void indexCodeChunks(long codeFileId, String fileName, String language, List<String> chunkContents) {
        List<TextSegment> segments = new ArrayList<>(chunkContents.size());
        for (int i = 0; i < chunkContents.size(); i++) {
            String text = "File: " + fileName + " (" + language + ")\n" + chunkContents.get(i);
            Metadata md = new Metadata()
                    .put(META_SOURCE, SOURCE_CODE)
                    .put(META_CODE_FILE_ID, String.valueOf(codeFileId))
                    .put(META_TITLE, fileName)
                    .put(META_CHUNK_INDEX, String.valueOf(i));
            segments.add(TextSegment.from(text, md));
        }
        addSegments(segments);
    }

    private static String buildIssueEmbeddingText(String subject, String resolution, String raisedBy,
                                                  String resolvedBy, Integer resolutionMinutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Historical support issue.\nSubject: ").append(subject).append("\nResolution: ").append(resolution);
        if (raisedBy != null && !raisedBy.isBlank()) {
            sb.append("\nRaised by: ").append(raisedBy);
        }
        if (resolvedBy != null && !resolvedBy.isBlank()) {
            sb.append("\nResolved by: ").append(resolvedBy);
        }
        if (resolutionMinutes != null) {
            sb.append("\nTime to resolve (minutes): ").append(resolutionMinutes);
        }
        return sb.toString();
    }

    public synchronized void indexSmeQa(String question, String answer) {
        String text = "SME recorded Q&A.\nQuestion: " + question + "\nAnswer: " + answer;
        Metadata md = new Metadata().put(META_SOURCE, SOURCE_SME);
        addSegments(List.of(TextSegment.from(text, md)));
    }

    private void addSegments(List<TextSegment> segments) {
        if (segments.isEmpty()) {
            return;
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        persistToDisk();
    }

    public synchronized void persistToDisk() {
        Path path = Paths.get(vectorStorePath);
        embeddingStore.serializeToFile(path);
    }
}
