package com.example.japicmp;

import com.example.japicmp.model.ComparisonReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ComparisonReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private ComparisonReportWriter() {
    }

    static void write(ComparisonReport report, Path outputJson) throws IOException {
        Path parent = outputJson.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        OBJECT_MAPPER.writeValue(outputJson.toFile(), report);
    }
}

