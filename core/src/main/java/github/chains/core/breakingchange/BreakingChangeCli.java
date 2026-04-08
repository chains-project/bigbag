package github.chains.core.breakingchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.kth.DockerBuild;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "breaking-change",
        mixinStandardHelpOptions = true,
        description = "Extracts a project from a BreakingChange JSON, runs breaking-classifier and stores the result."
)
public class BreakingChangeCli implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(BreakingChangeCli.class);

    @CommandLine.Option(
            names = {"-i", "--input"},
            required = true,
            description = "Path to the BreakingChange JSON file"
    )
    private Path inputJson;

    @CommandLine.Option(
            names = {"-o", "--output-dir"},
            description = "Base directory where the project will be extracted",
            defaultValue = "./extracted-projects"
    )
    private Path outputDir;

    @CommandLine.Option(
            names = {"-r", "--result-json"},
            description = "Optional path to store the classifier output as JSON"
    )
    private Path resultJson;

    @CommandLine.Option(
            names = {"-f", "--force"},
            description = "If present, forces re-extraction even if the project folder already exists"
    )
    private boolean forceExtraction;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose logging"
    )
    private boolean verbose;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BreakingChangeCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        configureLogging();

        DockerBuild dockerBuild = new DockerBuild(false);
        BreakingChangeProcessor processor = new BreakingChangeProcessor(dockerBuild);

        if (verbose) {
            log.info("Reading BreakingChange JSON from {}", inputJson);
            log.info("Extraction directory: {}", outputDir.toAbsolutePath());
        }

        BreakingChangeProcessingResult result = processor.process(inputJson, outputDir, forceExtraction);
        log.info("Detected failure category: {}", result.classifierCategory());
        log.info("Log file: {}", result.logFile());

        if (resultJson != null) {
            processor.writeResult(result, resultJson);
        }

        return 0;
    }

    private void configureLogging() {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
    }
}

