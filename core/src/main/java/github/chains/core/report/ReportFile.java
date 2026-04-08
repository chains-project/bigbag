package github.chains.core.report;

import github.chains.core.model.BreakingUpdateRecord;

import java.nio.file.Path;

public record ReportFile(Path source, BreakingUpdateRecord record) {
}

