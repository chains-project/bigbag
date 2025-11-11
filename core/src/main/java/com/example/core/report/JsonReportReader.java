package com.example.core.report;

import com.example.core.model.BreakingUpdateRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JsonReportReader {

    private final ObjectMapper objectMapper;

    public JsonReportReader() {
        this(createDefaultObjectMapper());
    }

    public JsonReportReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<BreakingUpdateRecord> readFromDirectory(Path directory) throws IOException {
        return readReportFiles(directory).stream()
                .map(ReportFile::record)
                .toList();
    }

    public List<ReportFile> readReportFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Provided path is not a directory: " + directory);
        }

        List<ReportFile> records = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> readEntry(records, path));
        }
        return records;
    }

    private void readEntry(List<ReportFile> records, Path path) {
        try {
            BreakingUpdateRecord record = objectMapper.readValue(path.toFile(), BreakingUpdateRecord.class);
            records.add(new ReportFile(path, record));
        } catch (IOException e) {
            throw new JsonReportReaderException("Failed to read JSON file: " + path, e);
        }
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

