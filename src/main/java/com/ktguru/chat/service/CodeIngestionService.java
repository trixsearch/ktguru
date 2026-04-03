package com.ktguru.chat.service;

import com.ktguru.chat.model.CodeChunk;
import com.ktguru.chat.model.CodeFile;
import com.ktguru.chat.repository.CodeChunkRepository;
import com.ktguru.chat.repository.CodeFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CodeIngestionService {

    private static final int CHUNK_SIZE = 2000;
    private static final int OVERLAP = 200;

    private final CodeFileRepository codeFileRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final VectorIndexService vectorIndexService;

    public CodeIngestResult ingest(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "snippet.java";
        String language = detectLanguage(name);

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new IllegalArgumentException("Empty code file.");
        }

        CodeFile cf = new CodeFile();
        cf.setFileName(name);
        cf.setLanguage(language);
        cf = codeFileRepository.save(cf);

        List<String> chunkTexts = chunkByChars(content);
        List<CodeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            CodeChunk ch = new CodeChunk();
            ch.setCodeFile(cf);
            ch.setContent(chunkTexts.get(i));
            ch.setChunkIndex(i);
            chunks.add(ch);
        }
        codeChunkRepository.saveAll(chunks);
        vectorIndexService.indexCodeChunks(cf.getId(), cf.getFileName(), cf.getLanguage(), chunkTexts);

        return new CodeIngestResult(cf.getId(), name, chunkTexts.size());
    }

    private static String detectLanguage(String filename) {
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".java")) {
            return "JAVA";
        }
        if (f.endsWith(".xml")) {
            return "XML";
        }
        if (f.endsWith(".yml") || f.endsWith(".yaml")) {
            return "YAML";
        }
        if (f.endsWith(".properties")) {
            return "PROPERTIES";
        }
        if (f.endsWith(".json")) {
            return "JSON";
        }
        if (f.endsWith(".sql")) {
            return "SQL";
        }
        return "TEXT";
    }

    private static List<String> chunkByChars(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            out.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - OVERLAP;
            if (start < 0) {
                start = 0;
            }
        }
        return out;
    }

    public record CodeIngestResult(long codeFileId, String fileName, int chunkCount) {
    }
}
