package github.chains.core.pipeline;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.ClassificationSummary;
import se.kth.models.Attempt;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for repair pipelines that handle the repair loop for breaking update records.
 * Different implementations can provide different repair strategies (e.g., model-based, agent-based).
 */
public interface RepairPipeline {

    /**
     * Runs the repair loop for a specific project.
     * 
     * @param extractedPath the root path where the project was extracted (contains project dir and m2)
     * @param projectName the name of the project
     * @param dockerImage the docker image used for reproduction
     * @param record the breaking update record
     * @param commitReportDir the report directory for this commit (reports/{model}/{commit}/)
     * @param summary the classification summary
     * @param outputBaseDir the output base directory
     * @param initialLogFile the initial log file from the project (for attempt 1)
     * @return list of attempts with their results
     */
    List<Attempt> runRepairLoop(
            Path extractedPath,
            String projectName,
            String dockerImage,
            BreakingUpdateRecord record,
            Path commitReportDir,
            ClassificationSummary summary,
            Path outputBaseDir,
            Path initialLogFile
    );
}

