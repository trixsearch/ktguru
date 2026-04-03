package com.ktguru.chat.service;

import com.ktguru.chat.model.Document;
import com.ktguru.chat.model.DocumentChunk;
import com.ktguru.chat.repository.DocumentChunkRepository;
import com.ktguru.chat.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorIndexService vectorIndexService;

    public DocumentIngestResult ingest(MultipartFile file, String uploadedBy) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        Document.SourceType type = detectType(name);

        DocumentParser parser = new ApacheTikaDocumentParser();
        dev.langchain4j.data.document.Document parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = parser.parse(in);
        } catch (Exception ex) {
            parsed = dev.langchain4j.data.document.Document.from(
                    new String(file.getBytes(), StandardCharsets.UTF_8));
        }

        String text = parsed.text();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No extractable text from document.");
        }

        Document doc = new Document();
        doc.setTitle(name);
        doc.setSourceType(type);
        doc.setUploadedBy(uploadedBy != null && !uploadedBy.isBlank() ? uploadedBy : null);
        doc = documentRepository.save(doc);

        DocumentSplitter splitter = DocumentSplitters.recursive(900, 120);
        List<TextSegment> segments = splitter.split(parsed);
        List<String> contents = new ArrayList<>(segments.size());
        List<DocumentChunk> chunks = new ArrayList<>();
        int idx = 0;
        for (TextSegment seg : segments) {
            String c = seg.text();
            if (c == null || c.isBlank()) {
                continue;
            }
            contents.add(c);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(doc);
            chunk.setContent(c);
            chunk.setChunkIndex(idx++);
            chunks.add(chunk);
        }

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document produced no chunks after splitting.");
        }

        documentChunkRepository.saveAll(chunks);
        vectorIndexService.indexDocumentChunks(doc.getId(), doc.getTitle(), contents);

        return new DocumentIngestResult(doc.getId(), doc.getTitle(), chunks.size());
    }

    private static Document.SourceType detectType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return Document.SourceType.DOCX;
        }
        if (lower.endsWith(".odt")) {
            return Document.SourceType.ODT;
        }
        return Document.SourceType.TXT;
    }

    public record DocumentIngestResult(long documentId, String title, int chunkCount) {
    }
}
