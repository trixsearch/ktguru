package com.ktguru.chat.controller;

import com.ktguru.chat.service.CodeIngestionService;
import com.ktguru.chat.service.DocumentIngestionService;
import com.ktguru.chat.service.ExcelIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final ExcelIngestionService excelIngestionService;
    private final DocumentIngestionService documentIngestionService;
    private final CodeIngestionService codeIngestionService;

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ingestExcel(@RequestPart("file") MultipartFile file) throws Exception {
        ExcelIngestionService.ExcelIngestResult r = excelIngestionService.ingest(file);
        if (!r.ok()) {
            return Map.of("ok", false, "error", r.errorMessage());
        }
        return Map.of(
                "ok", true,
                "rowsExamined", r.rowsExamined(),
                "rowsSkipped", r.rowsSkippedNoDatesOrData(),
                "issuesSaved", r.issuesSaved());
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentIngestionService.DocumentIngestResult ingestDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy) throws Exception {
        return documentIngestionService.ingest(file, uploadedBy);
    }

    @PostMapping(value = "/code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CodeIngestionService.CodeIngestResult ingestCode(@RequestPart("file") MultipartFile file) throws Exception {
        return codeIngestionService.ingest(file);
    }
}
