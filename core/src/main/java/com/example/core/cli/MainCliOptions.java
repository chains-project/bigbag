package com.example.core.cli;

import picocli.CommandLine;

import java.nio.file.Path;

/**
 * Command line options for the breaking-update-processor CLI.
 * This class contains all the @CommandLine.Option annotations to keep them separate from the main CLI logic.
 */
public class MainCliOptions {

    @CommandLine.Option(names = { "-i",
            "--input" }, description = "Input directory containing BreakingUpdateRecord JSON files")
    private String inputDirStr;

    @CommandLine.Option(names = { "-o",
            "--output" }, description = "Output directory where extracted projects will be saved")
    private String outputDirStr;

    @CommandLine.Option(names = { "-c",
            "--category" }, description = "Filter by failure category (COMPILATION_FAILURE, TEST_FAILURE, etc.)")
    private String category;

    @CommandLine.Option(names = { "-f",
            "--file" }, description = "Process only the specified JSON file (name without .json extension)")
    private String singleJsonFile;

    @CommandLine.Option(names = { "-e", "--extract" }, description = "Extract projects from Docker images")
    private Boolean extractProjects;

    @CommandLine.Option(names = { "--no-extract" }, description = "Do not extract projects from Docker images")
    private boolean noExtract;

    @CommandLine.Option(names = { "-k", "--classify" }, description = "Extract JARs and run breaking-classifier")
    private Boolean extractJarsAndClassify;

    @CommandLine.Option(names = { "--no-classify" }, description = "Do not extract JARs or run breaking-classifier")
    private boolean noClassify;

    @CommandLine.Option(names = {
            "--clean" }, description = "Remove existing {breakingCommit} folders before extraction")
    private Boolean cleanExisting;

    @CommandLine.Option(names = {
            "--no-clean" }, description = "Keep existing {breakingCommit} folders (skip if exists)")
    private boolean noClean;

    @CommandLine.Option(names = { "-v", "--verbose" }, description = "Show detailed information for each record")
    private boolean verbose;

    @CommandLine.Option(names = { "-j",
            "--json-output" }, description = "Path to store a JSON summary with dataset and inferred categories")
    private Path jsonOutput;

    @CommandLine.Option(names = { "-p",
            "--pipeline" }, description = "Repair pipeline to use: 'model' (default) or 'agent'")
    private String pipelineType;

    // Getters
    public String getInputDirStr() {
        return inputDirStr;
    }

    public String getOutputDirStr() {
        return outputDirStr;
    }

    public String getCategory() {
        return category;
    }

    public String getSingleJsonFile() {
        return singleJsonFile;
    }

    public Boolean getExtractProjects() {
        return extractProjects;
    }

    public boolean isNoExtract() {
        return noExtract;
    }

    public Boolean getExtractJarsAndClassify() {
        return extractJarsAndClassify;
    }

    public boolean isNoClassify() {
        return noClassify;
    }

    public Boolean getCleanExisting() {
        return cleanExisting;
    }

    public boolean isNoClean() {
        return noClean;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public Path getJsonOutput() {
        return jsonOutput;
    }

    public String getPipelineType() {
        return pipelineType;
    }
}

