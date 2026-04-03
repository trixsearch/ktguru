package com.ktguru.chat.service;

import com.ktguru.chat.model.Issue;
import com.ktguru.chat.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelIngestionService {

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private final IssueRepository issueRepository;
    private final VectorIndexService vectorIndexService;

    public ExcelIngestResult ingest(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rows = sheet.rowIterator();
            if (!rows.hasNext()) {
                return new ExcelIngestResult(0, 0, 0, "No rows in sheet");
            }
            Row header = rows.next();
            ColumnMap col = ColumnMap.fromHeader(header);
            if (!col.isComplete()) {
                return new ExcelIngestResult(0, 0, 0,
                        "Missing required columns. Need headers for: Problem/Subject, Resolution, Raised By, Raised On, Resolved On (optional: Resolved By). Found: " + col.foundDescription());
            }

            int examined = 0;
            int skipped = 0;
            List<Issue> batch = new ArrayList<>();

            while (rows.hasNext()) {
                Row row = rows.next();
                if (isRowEmpty(row)) {
                    continue;
                }
                examined++;
                String subject = getString(row, col.subject);
                String resolution = getString(row, col.resolution);
                LocalDateTime raisedAt = parseDateTime(row.getCell(col.raisedOn));
                LocalDateTime resolvedAt = parseDateTime(row.getCell(col.resolvedOn));

                if (subject == null || subject.isBlank() || resolution == null || resolution.isBlank()
                        || raisedAt == null || resolvedAt == null) {
                    skipped++;
                    continue;
                }

                String raisedBy = null;
                if (col.raisedBy >= 0) {
                    String rb = getString(row, col.raisedBy);
                    if (rb != null && !rb.isBlank()) {
                        raisedBy = rb.trim();
                    }
                }
                String resolvedBy = null;
                if (col.resolvedBy >= 0) {
                    String rb = getString(row, col.resolvedBy);
                    if (rb != null && !rb.isBlank()) {
                        resolvedBy = rb.trim();
                    }
                }

                Issue issue = new Issue();
                issue.setSubject(subject.trim());
                issue.setResolution(resolution.trim());
                issue.setRaisedBy(raisedBy);
                issue.setResolvedBy(resolvedBy);
                issue.setRaisedAt(raisedAt);
                issue.setResolvedAt(resolvedAt);
                long minutes = java.time.Duration.between(raisedAt, resolvedAt).toMinutes();
                issue.setResolutionTimeMinutes((int) Math.max(0, Math.min(minutes, Integer.MAX_VALUE)));

                batch.add(issue);
            }

            if (batch.isEmpty()) {
                return new ExcelIngestResult(examined, skipped, 0, "No valid rows (need subject, resolution, and both dates).");
            }

            List<Issue> saved = issueRepository.saveAll(batch);
            vectorIndexService.indexIssuesBatch(saved);

            return new ExcelIngestResult(examined, skipped, saved.size(), null);
        }
    }

    private static boolean isRowEmpty(Row row) {
        if (row.getFirstCellNum() < 0 || row.getLastCellNum() < 0) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String t = getString(row, c);
                if (t != null && !t.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String getString(Row row, int colIndex) {
        if (colIndex < 0) {
            return null;
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        DataFormatter fmt = new DataFormatter();
        return fmt.formatCellValue(cell).trim();
    }

    private LocalDateTime parseDateTime(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                java.util.Date d = cell.getDateCellValue();
                return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
            }
        } catch (Exception ignored) {
            // fall through to string parse
        }
        String raw = new DataFormatter().formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                if (f.toString().contains("HH") || f.toString().contains("H:mm")) {
                    return LocalDateTime.parse(raw, f);
                }
                return java.time.LocalDate.parse(raw, f).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private record ColumnMap(int subject, int resolution, int raisedBy, int raisedOn, int resolvedOn, int resolvedBy) {

        boolean isComplete() {
            return subject >= 0 && resolution >= 0 && raisedOn >= 0 && resolvedOn >= 0;
        }

        String foundDescription() {
            List<String> f = new ArrayList<>();
            if (subject >= 0) {
                f.add("subject");
            }
            if (resolution >= 0) {
                f.add("resolution");
            }
            if (raisedBy >= 0) {
                f.add("raisedBy");
            }
            if (raisedOn >= 0) {
                f.add("raisedOn");
            }
            if (resolvedOn >= 0) {
                f.add("resolvedOn");
            }
            if (resolvedBy >= 0) {
                f.add("resolvedBy");
            }
            return String.join(", ", f);
        }

        static ColumnMap fromHeader(Row header) {
            int subject = -1;
            int resolution = -1;
            int raisedBy = -1;
            int raisedOn = -1;
            int resolvedOn = -1;
            int resolvedBy = -1;

            int first = header.getFirstCellNum();
            int last = header.getLastCellNum();
            if (first < 0 || last < 0) {
                return new ColumnMap(-1, -1, -1, -1, -1, -1);
            }

            for (int i = first; i < last; i++) {
                Cell c = header.getCell(i);
                if (c == null) {
                    continue;
                }
                String norm = normalize(new DataFormatter().formatCellValue(c));
                if (norm.isEmpty()) {
                    continue;
                }
                if (matches(norm, "problem", "subject", "issue", "title")) {
                    subject = i;
                } else if (matches(norm, "resolution", "solution", "fix")) {
                    resolution = i;
                } else if (matches(norm, "raised by", "reporter", "created by", "raisedby")) {
                    raisedBy = i;
                } else if (matches(norm, "raised on", "created on", "opened on", "raised date", "created")) {
                    raisedOn = i;
                } else if (matches(norm, "resolved on", "closed on", "completed on", "resolved date")) {
                    resolvedOn = i;
                } else if (matches(norm, "resolved by", "resolver", "closed by")) {
                    resolvedBy = i;
                }
            }
            return new ColumnMap(subject, resolution, raisedBy, raisedOn, resolvedOn, resolvedBy);
        }

        private static String normalize(String s) {
            return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        }

        private static boolean matches(String norm, String... tokens) {
            for (String t : tokens) {
                String nt = t.replace(" ", "");
                String collapsed = norm.replace(" ", "");
                if (norm.contains(t) || collapsed.contains(nt)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record ExcelIngestResult(int rowsExamined, int rowsSkippedNoDatesOrData, int issuesSaved, String errorMessage) {
        public boolean ok() {
            return errorMessage == null;
        }
    }
}
