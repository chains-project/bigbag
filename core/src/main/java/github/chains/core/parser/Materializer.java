package github.chains.core.parser;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for materializing LLM output into executable transformation rules.
 * <p>
 * Different prompts may produce different output formats that require different
 * materialization strategies. Each materializer knows how to:
 * <ul>
 *   <li>Parse the LLM output for its specific prompt type</li>
 *   <li>Extract the transformation rules/code</li>
 *   <li>Generate executable files (e.g., Java classes, scripts, etc.)</li>
 * </ul>
 */
public interface Materializer {

    /**
     * Returns the identifier for this materializer, which should match the prompt kind ID.
     * 
     * @return the materializer identifier (e.g., "baseline_spoon", "final", etc.)
     */
    String id();

    /**
     * Materializes the LLM output into executable transformation files.
     * <p>
     * The materializer should:
     * <ul>
     *   <li>Read and parse the LLM output file</li>
     *   <li>Extract transformation rules/code</li>
     *   <li>Generate executable files in the target directory</li>
     *   <li>Return the path to the main executable file</li>
     * </ul>
     * 
     * @param promptOutputFile the file containing the LLM output
     * @param originalSourceFile the original source file to be transformed
     * @param commitReportDir the commit report directory (reports/{commit})
     * @param baseName the base name for output files
     * @return the path to the generated executable file (e.g., Java class, script, etc.)
     * @throws IOException if file operations fail
     */
    Path materialize(Path promptOutputFile,
                     Path originalSourceFile,
                     Path commitReportDir,
                     String baseName) throws IOException;
}

