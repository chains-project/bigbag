package com.example.core.report;

import com.example.core.model.BreakingUpdateRecord;

import java.nio.file.Path;

public record ReportFile(Path source, BreakingUpdateRecord record) {
}

