package chains.changeimpact.io;

import chains.changeimpact.model.ChangeImpactReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsible for serializing the change impact report to disk.
 */
public final class JsonReportWriter {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(ChangeImpactReport report, Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(outputPath.toFile(), report);
    }
}

