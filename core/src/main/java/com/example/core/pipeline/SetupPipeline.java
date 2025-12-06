package com.example.core.pipeline;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import se.kth.DockerBuild;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralized pipeline setup information.
 * Manages all paths, Docker information, dependency details, and environment configuration
 * for a single breaking update.
 * Similar to Bacardi's SetupPipeline, this class keeps all pipeline state updated
 * and centralizes all environment variable access.
 */
public class SetupPipeline {

    // Pipeline paths and Docker information
    private String dockerImage;
    private Path clientFolder;
    private Path logFilePath;
    private String branch;
    private Path m2FolderPath;
    private DockerBuild dockerBuild;
    private BreakingUpdateRecord breakingUpdate;
    private Path outPutPatchFolder;
    
    // Dependency information
    private String libraryName;
    private String baseVersion;
    private String newVersion;
    
    // Configuration from environment variables
    private int maxRepairAttempts;
    private boolean verbose;
    private boolean extractProjects;
    private boolean classify;
    private boolean cleanExisting;
    private String llmModel;
    private String specificFile;
    private String category;
    private Path jsonOutput;
    private Path spoonJar;
    private String llmClientPy;
    private String promptClasses;
    private boolean skipPromptGeneration;

    public SetupPipeline() {
        // Default values
        this.maxRepairAttempts = 3;
        this.verbose = false;
        this.extractProjects = true;
        this.classify = true;
        this.cleanExisting = false;
        this.llmModel = "default_model";
        this.llmClientPy = "llm/llm_client.py";
        this.promptClasses = "default";
        this.skipPromptGeneration = false;
    }

    /**
     * Initializes configuration from EnvConfig.
     * Loads all environment variables into the pipeline configuration.
     *
     * @param envConfig the environment configuration
     */
    public void loadFromEnvConfig(EnvConfig envConfig) {
        if (envConfig == null) {
            return;
        }
        
        // Pipeline configuration
        this.maxRepairAttempts = envConfig.get("MAX_REPAIR_ATTEMPTS")
                .map(Integer::parseInt)
                .orElse(3);
        
        this.verbose = envConfig.getBoolean("VERBOSE").orElse(false);
        this.extractProjects = envConfig.getBoolean("EXTRACT").orElse(true);
        this.classify = envConfig.getBoolean("CLASSIFY").orElse(true);
        this.cleanExisting = envConfig.getBoolean("CLEAN").orElse(false);
        
        // LLM and model configuration
        this.llmModel = envConfig.get("LLM_MODEL").orElse("default_model");
        this.llmClientPy = envConfig.get("LLM_CLIENT_PY").orElse("llm/llm_client.py");
        this.promptClasses = envConfig.get("PROMPT_CLASSES").orElse("default");
        this.skipPromptGeneration = envConfig.getBoolean("SKIP_PROMPT_GENERATION").orElse(false);
        
        // File and output configuration
        this.specificFile = envConfig.get("SPECIFIC_FILE").filter(s -> !s.isBlank()).orElse(null);
        this.category = envConfig.get("CATEGORY").orElse(null);
        this.jsonOutput = envConfig.getPath("JSON_OUTPUT").orElse(null);
        
        // Spoon configuration
        this.spoonJar = envConfig.get("SPOON_JAR")
                .map(Paths::get)
                .orElse(null);
    }

    /**
     * Updates the patch folder and log file path for a new attempt.
     * This is called at the start of each attempt to update paths.
     *
     * @param attemptNumber the attempt number (1-based)
     * @param basePatchesDir the base directory where patches are stored
     */
    public void updateForPatch(int attemptNumber, Path basePatchesDir) {
        this.outPutPatchFolder = basePatchesDir.resolve("patches").resolve("patch_" + attemptNumber);
        this.logFilePath = this.outPutPatchFolder.resolve("maven-log.txt");
    }

    /**
     * Updates the current Git branch.
     *
     * @param branch the branch name
     */
    public void updateBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Updates the log file path.
     * This is called after each build to update the log location.
     *
     * @param logFilePath the new log file path
     */
    public void updateLogFilePath(Path logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * Updates the Docker image.
     * This is called when a new Docker image is created.
     *
     * @param dockerImage the new Docker image name
     */
    public void updateDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    // Getters and Setters

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public Path getClientFolder() {
        return clientFolder;
    }

    public void setClientFolder(Path clientFolder) {
        this.clientFolder = clientFolder;
    }

    public Path getLogFilePath() {
        return logFilePath;
    }

    public void setLogFilePath(Path logFilePath) {
        this.logFilePath = logFilePath;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Path getM2FolderPath() {
        return m2FolderPath;
    }

    public void setM2FolderPath(Path m2FolderPath) {
        this.m2FolderPath = m2FolderPath;
    }

    public DockerBuild getDockerBuild() {
        return dockerBuild;
    }

    public void setDockerBuild(DockerBuild dockerBuild) {
        this.dockerBuild = dockerBuild;
    }

    public BreakingUpdateRecord getBreakingUpdate() {
        return breakingUpdate;
    }

    public void setBreakingUpdate(BreakingUpdateRecord breakingUpdate) {
        this.breakingUpdate = breakingUpdate;
        // Extract library information from breaking update
        if (breakingUpdate != null && breakingUpdate.updatedDependency() != null) {
            var dependency = breakingUpdate.updatedDependency();
            this.libraryName = dependency.classifier(); // Uses dependencyGroupId:dependencyArtifactId
            this.baseVersion = dependency.previousVersion();
            this.newVersion = dependency.newVersion();
        }
    }

    public Path getOutPutPatchFolder() {
        return outPutPatchFolder;
    }

    public void setOutPutPatchFolder(Path outPutPatchFolder) {
        this.outPutPatchFolder = outPutPatchFolder;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public void setLibraryName(String libraryName) {
        this.libraryName = libraryName;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(String baseVersion) {
        this.baseVersion = baseVersion;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    // Configuration getters

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public void setMaxRepairAttempts(int maxRepairAttempts) {
        this.maxRepairAttempts = maxRepairAttempts;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isExtractProjects() {
        return extractProjects;
    }

    public void setExtractProjects(boolean extractProjects) {
        this.extractProjects = extractProjects;
    }

    public boolean isClassify() {
        return classify;
    }

    public void setClassify(boolean classify) {
        this.classify = classify;
    }

    public boolean isCleanExisting() {
        return cleanExisting;
    }

    public void setCleanExisting(boolean cleanExisting) {
        this.cleanExisting = cleanExisting;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getSpecificFile() {
        return specificFile;
    }

    public void setSpecificFile(String specificFile) {
        this.specificFile = specificFile;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Path getJsonOutput() {
        return jsonOutput;
    }

    public void setJsonOutput(Path jsonOutput) {
        this.jsonOutput = jsonOutput;
    }

    public Path getSpoonJar() {
        return spoonJar;
    }

    public void setSpoonJar(Path spoonJar) {
        this.spoonJar = spoonJar;
    }

    public String getLlmClientPy() {
        return llmClientPy;
    }

    public void setLlmClientPy(String llmClientPy) {
        this.llmClientPy = llmClientPy;
    }

    public String getPromptClasses() {
        return promptClasses;
    }

    public void setPromptClasses(String promptClasses) {
        this.promptClasses = promptClasses;
    }

    public boolean isSkipPromptGeneration() {
        return skipPromptGeneration;
    }

    public void setSkipPromptGeneration(boolean skipPromptGeneration) {
        this.skipPromptGeneration = skipPromptGeneration;
    }

    @Override
    public String toString() {
        return "SetupPipeline{" +
                "dockerImage='" + dockerImage + '\'' +
                ", clientFolder=" + clientFolder +
                ", logFilePath=" + logFilePath +
                ", branch='" + branch + '\'' +
                ", outPutPatchFolder=" + outPutPatchFolder +
                ", libraryName='" + libraryName + '\'' +
                ", baseVersion='" + baseVersion + '\'' +
                ", newVersion='" + newVersion + '\'' +
                ", maxRepairAttempts=" + maxRepairAttempts +
                ", verbose=" + verbose +
                ", llmModel='" + llmModel + '\'' +
                '}';
    }
}

