package se.kth;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import se.kth.models.FailureCategory;
import se.kth.models.Result;
import se.kth.models.Attempt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class DockerBuild {

    static Logger log = LoggerFactory.getLogger(DockerBuild.class);

    private static DockerClient dockerClient;

    private static final int EXIT_CODE_OK = 0;

    private static final List<String> containers = new ArrayList<>();

    public static final String BASE_IMAGE = "ghcr.io/chains-project/breaking-updates:base-image";

    private Boolean isBump = false;

    private int max_attempts = 1;

    private boolean verbose = false;

    public DockerBuild(Boolean isBump, int max_attempts) {
        this.isBump = isBump;
        createDockerClient();
        this.max_attempts = max_attempts;
    }

    public DockerBuild(Boolean isBump) {
        this.isBump = isBump;
        createDockerClient();
    }

    public DockerBuild(Boolean isBump, boolean verbose) {
        this.isBump = isBump;
        this.verbose = verbose;
        createDockerClient();
    }

    public DockerBuild(Boolean isBump, int max_attempts, boolean verbose) {
        this.isBump = isBump;
        this.verbose = verbose;
        createDockerClient();
        this.max_attempts = max_attempts;
    }

    /**
     * Method to remove Docker container
     *
     * @param containerId - container id
     * @return boolean - true if container is removed successfully, false otherwise
     */
    public boolean removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container with id: {}", containerId);
            return false;
        }
        log.info("Container with id: {} removed successfully", containerId);
        return true;
    }

    /**
     * Helper method to safely stop and remove a Docker container.
     * This method handles exceptions gracefully and logs warnings instead of
     * throwing exceptions.
     *
     * @param containerId the container ID to clean up
     */
    private void cleanupContainer(String containerId) {
        if (containerId == null || containerId.trim().isEmpty()) {
            return;
        }
        try {
            dockerClient.stopContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("Could not stop container {}", containerId, e);
        }
        try {
            dockerClient.removeContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("Could not remove container {}", containerId, e);
        }
    }

    /**
     * Helper method to execute an operation within a temporary Docker container.
     * The container is automatically cleaned up after the operation completes
     * (success or failure).
     *
     * @param dockerImage the Docker image to use for the container
     * @param operation   a function that receives the container ID and returns a
     *                    result
     * @param <T>         the type of result returned by the operation
     * @return the result of the operation, or null if the operation fails
     */
    private <T> T executeInContainer(String dockerImage, java.util.function.Function<String, T> operation) {
        String containerId = null;
        try {
            ensureBaseMavenImageExists(dockerImage);

            CreateContainerResponse container = dockerClient.createContainerCmd(dockerImage)
                    .withCmd("sh", "-c", "sleep 10")
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();

            return operation.apply(containerId);

        } catch (Exception e) {
            log.error("Error executing operation in container from image {}", dockerImage, e);
            return null;
        } finally {
            cleanupContainer(containerId);
        }
    }

    /**
     * Copies a project from a Docker container to a specified directory.
     *
     * @param containerId the ID of the Docker container
     * @param project     the name of the project to copy
     * @param dir         the directory to copy the project to
     * @return the path to the directory where the project was copied, or null if
     *         the copy failed
     */
    public Path copyProjectFromContainer(String containerId, String project, Path dir) {
        containers.add(containerId);
        try (InputStream dependencyStream = dockerClient.copyArchiveFromContainerCmd(containerId, "/" + project)
                .exec()) {
            copyFiles(dir, dependencyStream);
            log.info("Project {} copied successfully", project);
            return dir;
        } catch (Exception e) {
            log.error("Could not copy the project {}", project, e);
            return null;
        }
    }

    public Optional<String> createImageForRepositoryAtVersion(String baseImage, URL gitUrl, String versionTag,
            String imageName, Path outputPath) {
        String projectDirectoryName = "project";
        log.info("Creating container for {} with version {} in {}", gitUrl, versionTag, baseImage);

        CreateContainerResponse container = dockerClient.createContainerCmd(baseImage)
                .withCmd("/bin/sh", "-c",
                        ("git clone --branch %s %s %s && cd %s && mvn test -B -l output.log -DtestFailureIgnore=true " +
                                "-Dmaven.test.failure.ignore=true").formatted(versionTag,
                                        gitUrl, projectDirectoryName, projectDirectoryName))
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        WaitContainerResultCallback waitResult = dockerClient.waitContainerCmd(container.getId())
                .exec(new WaitContainerResultCallback());

        if (waitResult.awaitStatusCode() != EXIT_CODE_OK) {
            log.warn("Could not create docker image for project {} at version {} in {}", gitUrl, versionTag, baseImage);
            this.copyM2FolderToLocalPath(container.getId(), Paths.get("/project/output.log"), outputPath);
            dockerClient.removeContainerCmd(container.getId()).exec();
            return Optional.empty();
        } else {
            log.info("Successfully created docker image for project {} at version {} in {}", gitUrl, versionTag,
                    baseImage);
            this.copyM2FolderToLocalPath(container.getId(), Paths.get("/project/output.log"), outputPath);
            dockerClient.commitCmd(container.getId())
                    .withWorkingDir("/project")
                    .withRepository("ghcr.io/chains-project/breaking-updates")
                    .withTag(imageName)
                    .exec();
            dockerClient.removeContainerCmd(container.getId()).exec();
            return Optional.of(container.getId());
        }
    }

    public Path copyFromContainer(String containerId, String file, Path dir) {
        try (InputStream dependencyStream = dockerClient.copyArchiveFromContainerCmd(containerId, file)
                .exec()) {
            copyFile(dir, dependencyStream);
            log.info("File {} copied successfully", file);
            return dir;
        } catch (Exception e) {
            log.error("Could not copy the file {}", file, e);
            return null;
        }
    }

    public String copyFolderToDockerImage(String dockerImage, String folderPath) throws IOException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        try {
            // 1. Create a container from the existing image
            CreateContainerResponse container = dockerClient.createContainerCmd(dockerImage)
                    .withCmd("/bin/sh") // Ensure container has a shell to execute commands
                    .exec();

            String containerId = container.getId();
            log.info("Created container with ID: {}", containerId);

            // 2. Start the container
            dockerClient.startContainerCmd(containerId).exec();

            // 3. Copy the folder into the running container
            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                throw new IllegalArgumentException("Invalid folder path: " + folderPath);
            }

            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withHostResource(folderPath) // Path to folder on host
                    .withRemotePath("/") // Destination in container
                    .exec();

            log.info("Copied folder to container");

            // 4. Commit the container to create a new image
            String newImageId = dockerClient.commitCmd(containerId)
                    .withRepository(dockerImage)
                    .exec();

            log.info("Committed container to create new image: {}", newImageId);

            // 5. Stop and remove the temporary container
            try {
                dockerClient.stopContainerCmd(containerId).exec();
            } catch (NotModifiedException e) {
                log.error("Could not stop container {} ", e.getMessage());
            }
            try {
                dockerClient.removeContainerCmd(containerId).exec();
            } catch (Exception e) {
                log.error("Could not remove container {} ", e.getMessage());
            }

            return newImageId;
        } catch (DockerException e) {
            log.error("Could not copy folder to Docker image {} ", e.getMessage());
        }

        return dockerImage;
    }

    public String copyProjectToContainer(String dockerImage, Path client, FailureCategory failureCategory) {
        try {
            // start container
            String containerId = startSpinningContainer(dockerImage);

            Path localFolder = Paths.get("%s".formatted(client));

            // copy project to container
            // 1. Create a container from the existing image
            CreateContainerResponse container = dockerClient.createContainerCmd(dockerImage)
                    .withCmd("/bin/sh") // Ensure container has a shell to execute commands
                    .exec();

            // 2. Start the container
            dockerClient.startContainerCmd(containerId).exec();

            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withHostResource(localFolder.toAbsolutePath().toString()) // local path
                    .withRemotePath("/")// container path // Destination in container
                    .exec();

            System.out.println("Copied folder to container");

            // 4. Commit the container to create a new image
            String newImageId = dockerClient.commitCmd(containerId)
                    .withRepository(dockerImage)
                    .exec();

            System.out.println("Committed container to create new image: " + newImageId);

            // 5. Stop and remove the temporary container
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();

            return dockerImage;
        } catch (DockerException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String createNewBaseImageWithNewJavaVersion(Path client, String javaVersion, String dockerImage) {
        String baseImage;
        String imageName = "";

        // get client name
        Path clientName = client.toAbsolutePath().getFileName();

        if (javaVersion.equals("Java 17")) {
            baseImage = "%s-java-17".formatted(BASE_IMAGE);
            imageName = "%s:breaking-update-java-17".formatted(clientName);
        } else {
            baseImage = BASE_IMAGE;
            imageName = "%s:base".formatted(clientName);
        }

        try {
            ensureBaseMavenImageExists(baseImage);

            log.info("Creating docker image for breaking update {}", clientName);

            // create container with base image
            CreateContainerResponse container = dockerClient.createContainerCmd(baseImage)
                    .withWorkingDir("/%s".formatted(clientName))
                    .withCmd("sh")
                    .exec();

            // start container
            dockerClient.startContainerCmd(container.getId()).exec();

            Path localFolder = Paths.get("%s".formatted(client));

            String containerPath = "/%s".formatted(clientName);

            // copy project to container
            CopyArchiveToContainerCmd copyProjectToContainer = dockerClient.copyArchiveToContainerCmd(container.getId())
                    .withHostResource(localFolder.toAbsolutePath().toString()) // local path
                    .withRemotePath("/"); // container path

            copyProjectToContainer.exec();

            // copy M2 folder to container
            Path m2 = localFolder.resolveSibling("m2/.m2").normalize();

            log.info("Copying M2 folder to container");

            CopyArchiveToContainerCmd copyM2ToContainer = dockerClient.copyArchiveToContainerCmd(container.getId())
                    .withHostResource(m2.toAbsolutePath().toString()) // local path
                    .withRemotePath("/root/"); // container path

            copyM2ToContainer.exec();

            // execute command to create new image and wait for completion
            WaitContainerResultCallback waitResult = dockerClient.waitContainerCmd(container.getId())
                    .exec(new WaitContainerResultCallback());

            if (waitResult.awaitStatusCode() != EXIT_CODE_OK) {
                log.warn("Could not create docker image for {}", clientName);
                throw new RuntimeException(waitResult.toString());
            }

            dockerClient.commitCmd(container.getId())
                    .withRepository(clientName.toString().toLowerCase())
                    .withWorkingDir("/%s".formatted(clientName))
                    .withTag("breaking-update-java-17").exec();

            log.warn("Created docker image for  {}", clientName);

            dockerClient.removeContainerCmd(container.getId()).exec();

            return imageName;
        } catch (InterruptedException e) {
            log.error("Could not pull base image {} ", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createDockerClient() {
        DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryUrl("https://hub.docker.com")
                .build();

        DockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .sslConfig(clientConfig.getSSLConfig())
                .connectTimeout(30)
                .build();

        dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient);

        // Configure docker-java logger level based on verbose flag
        configureDockerJavaLogging();
    }

    private void configureDockerJavaLogging() {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger dockerJavaLogger = loggerContext.getLogger("com.github.dockerjava");
            if (verbose) {
                dockerJavaLogger.setLevel(Level.DEBUG);
            } else {
                dockerJavaLogger.setLevel(Level.INFO);
            }
        } catch (Exception e) {
            // If logback is not available or there's an error, just log a warning
            log.debug("Could not configure docker-java logging level: {}", e.getMessage());
        }
    }

    public void ensureBaseMavenImageExists(String image) throws InterruptedException {
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            if (verbose) {
                log.info("Base image not present, pulling {}", image);
                log.info("Pulling Maven image {} ...", image);
            }
            dockerClient.pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
            if (verbose) {
                log.info("Done pulling Maven image {}", image);
            }
        }
    }

    private String startContainer(String cmd, String image, Path client) {
        String clientName = client.getFileName().toString();

        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withWorkingDir("/" + clientName)
                .withCmd("sh", "-c", cmd)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container.getId();
    }

    /**
     * Starts a container with the project folder mounted as a volume.
     * This allows file modifications on the host to be immediately available in the
     * container.
     *
     * @param cmd    the command to execute
     * @param image  the Docker image to use
     * @param client the local project path to mount
     * @return the container ID
     */
    private String startContainerWithMount(String cmd, String image, Path client) {
        // Default to /project if no specific path is provided
        return startContainerWithMount(cmd, image, client, "/project");
    }

    /**
     * Starts a container with the project folder mounted at the original container
     * path.
     * This ensures that changes made to the project are reflected inside the
     * container.
     *
     * @param cmd                  the command to execute in the container
     * @param image                the Docker image to use
     * @param client               the local path to the project folder
     * @param containerProjectPath the original path where the project was located
     *                             in the container (e.g., "/project")
     * @return the container ID
     */
    private String startContainerWithMount(String cmd, String image, Path client, String containerProjectPath) {
        Path absoluteClientPath = client.toAbsolutePath().normalize();

        // Normalize container path (ensure it starts with /)
        String normalizedContainerPath = containerProjectPath.startsWith("/")
                ? containerProjectPath
                : "/" + containerProjectPath;

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(
                        absoluteClientPath.toString(),
                        new Volume(normalizedContainerPath),
                        AccessMode.rw));

        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withWorkingDir(normalizedContainerPath)
                .withCmd("sh", "-c", cmd)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container.getId();
    }

    /**
     * Extracts the Docker image name from a reproduction command.
     * The command might be in formats like:
     * - "docker run <image> ..."
     * - "<image>"
     * - "docker run -v ... <image> ..."
     *
     * @param reproductionCommand the reproduction command string
     * @return the Docker image name, or null if not found
     */
    public String extractDockerImageFromCommand(String reproductionCommand) {
        if (reproductionCommand == null || reproductionCommand.trim().isEmpty()) {
            return null;
        }

        String command = reproductionCommand.trim();
        String[] parts = command.split("\\s+");

        // Look for docker run and find the image after it
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("docker") && i + 1 < parts.length && parts[i + 1].equals("run")) {
                // Skip docker run and any flags/options
                for (int j = i + 2; j < parts.length; j++) {
                    String part = parts[j];
                    // Skip flags and options
                    if (part.startsWith("-") || part.startsWith("--")) {
                        continue;
                    }
                    // If it looks like an image (contains / or :), return it
                    if (part.contains("/") || part.contains(":")) {
                        return part;
                    }
                }
            }
        }

        // If no "docker run" found, look for image-like strings
        for (String part : parts) {
            if ((part.contains("/") || part.contains(":")) && !part.startsWith("-") && !part.startsWith("/")) {
                return part;
            }
        }

        // Fallback: return the last non-flag token
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].startsWith("-") && !parts[i].startsWith("--")) {
                return parts[i];
            }
        }

        return null;
    }

    /**
     * Reproduces a breaking update using a specified Docker image and failure
     * category.
     *
     * @param image           the Docker image to use for the reproduction
     * @param failureCategory the category of failure to reproduce
     * @param client          the client name
     * @return a Result object containing the outcome of the reproduction attempts
     */
    public Result reproduce(String image, FailureCategory failureCategory, Path client, Path logFile) {
        // Store result for each attempt
        Result breakingUpdateReproductionResult = new Result(failureCategory);
        Map<String, String> startedContainers = new HashMap<>();

        int attemptCount;
        for (attemptCount = 1; attemptCount <= max_attempts; attemptCount++) {
            startedContainers.put("postContainer%s".formatted(attemptCount),
                    startContainer(getPostCmd(), image, client));

            WaitContainerResultCallback result = dockerClient.waitContainerCmd(startedContainers.get("postContainer%s"
                    .formatted(attemptCount))).exec(new WaitContainerResultCallback());

            if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
                // Get list of files and errors and store
                // if fail store the log file
                storeLogFile(startedContainers.get("postContainer%s".formatted(attemptCount)), client, logFile);
                // stop the process and store the log file
                log.info("Breaking commit failed in the {} attempt.", attemptCount);
                // Use the requested failure category instead of UNKNOWN to reflect cause
                breakingUpdateReproductionResult.getAttempts()
                        .add(new Attempt(attemptCount, failureCategory, logFile.getParent().toString(),
                                false));
            } else {
                log.info("Breaking commit did not fail in the {} attempt.", attemptCount);
                // if (attemptCount == 3) {
                // TODO:why successful is set to false not true
                breakingUpdateReproductionResult.getAttempts()
                        .add(new Attempt(attemptCount, FailureCategory.BUILD_SUCCESS, logFile.getParent().toString(),
                                false));
                storeLogFile(startedContainers.get("postContainer%s".formatted(attemptCount)), client, logFile);
                // }
            }
        }

        // remove the containers
        startedContainers.forEach((key, value) -> removeContainer(value));

        return breakingUpdateReproductionResult;
    }

    /**
     * Reproduces a breaking update using volume mounts.
     * The project folder is mounted into the container, allowing file modifications
     * on the host to be immediately available for compilation.
     *
     * @param image           the Docker image to use for the reproduction
     * @param failureCategory the category of failure to reproduce
     * @param client          the local project path (will be mounted)
     * @param logFile         the path where the log file should be saved (on host)
     * @return a Result object containing the outcome of the reproduction attempts
     */
    public Result reproduceWithMount(String image, FailureCategory failureCategory, Path client, Path logFile) {
        // Default to /project if no specific path is provided
        return reproduceWithMount(image, failureCategory, client, logFile, "/project");
    }

    /**
     * Reproduces a breaking update by building the project in a container with
     * volume mount.
     * The project folder is mounted at the original container path to ensure
     * changes are reflected.
     *
     * @param image                the Docker image to use
     * @param failureCategory      the expected failure category
     * @param client               the local path to the project folder
     * @param logFile              the path where the build log will be saved
     * @param containerProjectPath the original path where the project was located
     *                             in the container (e.g., "/project")
     * @return the build result
     */
    public Result reproduceWithMount(String image, FailureCategory failureCategory, Path client, Path logFile,
            String containerProjectPath) {
        Result breakingUpdateReproductionResult = new Result(failureCategory);
        Map<String, String> startedContainers = new HashMap<>();

        int attemptCount;
        for (attemptCount = 1; attemptCount <= max_attempts; attemptCount++) {
            String containerId = startContainerWithMount(getPostCmd(), image, client, containerProjectPath);
            startedContainers.put("postContainer%s".formatted(attemptCount), containerId);

            WaitContainerResultCallback result = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback());

            Integer exitCode = result.awaitStatusCode();
            boolean success = exitCode != null && exitCode.intValue() == EXIT_CODE_OK;

            // Copy log file from mounted volume (it's already on the host)
            // The log file is created inside the container at
            // containerProjectPath/mavenLog.log
            // Since we mounted the project folder, it's directly accessible on the host
            Path containerLogPath = client.resolve("mavenLog.log");
            boolean logCopied = false;

            if (Files.exists(containerLogPath)) {
                try {
                    Files.copy(containerLogPath, logFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("Log file copied from mounted volume to {}", logFile);
                    logCopied = true;
                } catch (IOException e) {
                    log.error("Could not copy log file from mounted volume", e);
                }
            } else {
                log.warn("Log file not found at expected location: {}. Attempting fallback copy from container...",
                        containerLogPath);
            }

            // Fallback: If log file doesn't exist in mounted volume, try to copy from
            // container
            // This handles cases where the command failed before creating the log file,
            // or if there were permission issues writing to the mounted volume
            if (!logCopied) {
                try {
                    // Use the containerProjectPath to construct the correct log path in container
                    String normalizedContainerPath = containerProjectPath.startsWith("/")
                            ? containerProjectPath
                            : "/" + containerProjectPath;
                    String logLocationInContainer = normalizedContainerPath + "/mavenLog.log";

                    // Try to copy log directly from container using the correct path
                    try (InputStream logStream = dockerClient
                            .copyArchiveFromContainerCmd(containerId, logLocationInContainer).exec()) {
                        byte[] fileContent = logStream.readAllBytes();
                        Files.createDirectories(logFile.getParent());
                        Files.write(logFile, fileContent);
                        log.info("Log file copied from container (fallback) to {}", logFile);
                        logCopied = true;
                    }
                } catch (Exception e) {
                    log.error("Could not copy log file from container (fallback): {}", e.getMessage());
                    // Create a placeholder log file to indicate the attempt was made but log is
                    // unavailable
                    try {
                        Files.createDirectories(logFile.getParent());
                        Files.write(logFile,
                                ("[ERROR] Log file could not be retrieved from container or mounted volume.\n" +
                                        "Container ID: " + containerId + "\n" +
                                        "Container path: " + containerProjectPath + "\n" +
                                        "Exit code: " + exitCode + "\n" +
                                        "Error: " + e.getMessage() + "\n").getBytes());
                    } catch (IOException ioException) {
                        log.error("Could not create placeholder log file", ioException);
                    }
                }
            }

            if (!success) {
                log.info("Breaking commit failed in the {} attempt.", attemptCount);
                breakingUpdateReproductionResult.getAttempts()
                        .add(new Attempt(attemptCount, failureCategory,
                                logFile.getParent().toString(), false));
            } else {
                log.info("Breaking commit succeeded in the {} attempt.", attemptCount);
                breakingUpdateReproductionResult.getAttempts()
                        .add(new Attempt(attemptCount, FailureCategory.BUILD_SUCCESS,
                                logFile.getParent().toString(), true));
            }
        }

        // remove the containers
        startedContainers.forEach((key, value) -> removeContainer(value));

        return breakingUpdateReproductionResult;
    }

    public Result reproduceContainer(String containerId, FailureCategory failureCategory, Path client, Path logFile) {
        // Store result for each attempt
        Result breakingUpdateReproductionResult = new Result(failureCategory);
        Map<String, String> startedContainers = new HashMap<>();

        int attemptCount;
        for (attemptCount = 3; attemptCount < 4; attemptCount++) {
            startedContainers.put("postContainer%s".formatted(attemptCount), containerId);
            executeInContainer(containerId, getPostCmd());

            WaitContainerResultCallback result = dockerClient.waitContainerCmd(startedContainers.get("postContainer%s"
                    .formatted(attemptCount))).exec(new WaitContainerResultCallback());

            if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
                // if fail store the log file
                storeLogFile(startedContainers.get("postContainer%s".formatted(attemptCount)), client, logFile);
                // stop the process and store the log file
                log.info("Breaking commit failed in the {} attempt.", attemptCount);
                breakingUpdateReproductionResult.getAttempts()
                        .add(new Attempt(attemptCount, failureCategory, logFile.getParent().toString(),
                                false));
            } else {
                log.info("Breaking commit did not fail in the {} attempt.", attemptCount);
                if (attemptCount == 3) {
                    breakingUpdateReproductionResult.getAttempts()
                            .add(new Attempt(attemptCount, FailureCategory.BUILD_SUCCESS,
                                    logFile.getParent().toString(), false));
                    storeLogFile(startedContainers.get("postContainer%s".formatted(attemptCount)), client, logFile);
                }
            }
        }

        // remove the containers
        startedContainers.forEach((key, value) -> removeContainer(value));

        return breakingUpdateReproductionResult;
    }

    /**
     * Command to compile and test the breaking update
     */
    private static String getPostCmd() {
        return "set -o pipefail && mvn test -Dcheckstyle.skip=true -Dpmd.skip=true  -B | tee mavenLog.log";
    }

    public Path storeLogFile(String containerId, Path client, Path logFile) {
        String clientName = client.getFileName().toString();
        String logLocation = "/%s/mavenLog.log".formatted(clientName);

        try (InputStream logStream = dockerClient.copyArchiveFromContainerCmd(containerId, logLocation).exec()) {
            byte[] fileContent = logStream.readAllBytes();
            Files.write(logFile, fileContent);
            return logFile;
        } catch (IOException e) {
            log.error("Could not store the log file for breaking update ", e);
            throw new RuntimeException(e);
        }
    }

    public CreateContainerResponse startContainerEntryPoint(String imageId, String[] entrypoint) {
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageId)
                .withEntrypoint(entrypoint)
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container;
    }

    public void copyM2FolderToLocalPath(String containerId, Path fromContainer, Path localPath) {
        if (Files.notExists(localPath)) {
            try {
                log.info("Creating local path {}", localPath);
                Files.createDirectories(localPath);
            } catch (IOException e) {
                log.error("Could not create local path", e);
                throw new RuntimeException(e);
            }
        }

        log.info("");
        log.info("Copying folder {} from container to local path", localPath.getFileName());

        try (InputStream m2Stream = dockerClient.copyArchiveFromContainerCmd(containerId, fromContainer.toString())
                .exec()) {
            copyFiles(localPath, m2Stream);
            log.info("Folder {} copied successfully", localPath.getFileName());
        } catch (Exception e) {
            log.error("Could not copy the {} folder", localPath, e);
        }
    }

    private void copyFiles(Path localPath, InputStream m2Stream) throws IOException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(m2Stream)) {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path filePath = localPath.resolve(entry.getName());
                    if (!Files.exists(filePath)) {
                        Files.createDirectories(filePath.getParent());
                        Files.createFile(filePath);
                        byte[] fileContent = tarStream.readAllBytes();
                        Files.write(filePath, fileContent, StandardOpenOption.WRITE);
                    }
                }
            }
        }
    }

    private void copyFile(Path localPath, InputStream m2Stream) throws IOException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(m2Stream)) {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    if (!Files.exists(localPath)) {
                        Files.createFile(localPath);
                        byte[] fileContent = tarStream.readAllBytes();
                        Files.write(localPath, fileContent, StandardOpenOption.WRITE);
                    }
                }
            }
        }
    }

    /**
     * Copies only the JAR file from a TAR archive, ignoring directory structure.
     * This method extracts only the specific JAR file and writes it directly to the
     * output path.
     *
     * @param outputPath  the path where the JAR file should be written
     * @param jarFileName the expected JAR file name (e.g., "artifact-version.jar")
     * @param tarStream   the TAR archive input stream from Docker
     * @throws IOException if there's an error reading or writing the file
     */
    private void copyJarFile(Path outputPath, String jarFileName, InputStream tarStream) throws IOException {
        try (TarArchiveInputStream archiveStream = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            boolean jarFound = false;

            while ((entry = archiveStream.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    // Extract only the JAR file, ignoring directory structure
                    // The entry name might be like "root/.m2/repository/.../artifact-version.jar"
                    // or just "artifact-version.jar"
                    if (entryName.endsWith(jarFileName)) {
                        // Ensure parent directory exists
                        if (outputPath.getParent() != null) {
                            Files.createDirectories(outputPath.getParent());
                        }

                        // Read the JAR file content and write it to the output path
                        // Only copy this specific JAR file, not the directory structure
                        byte[] fileContent = archiveStream.readAllBytes();
                        Files.write(outputPath, fileContent, StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING);
                        jarFound = true;
                        log.debug("Extracted JAR file: {} -> {}", entryName, outputPath);
                        break; // Found the JAR, no need to continue
                    }
                }
            }

            if (!jarFound) {
                throw new IOException("JAR file " + jarFileName + " not found in TAR archive");
            }
        }
    }

    public static void deleteImage(String imageId) {
        try {
            try {
                dockerClient.inspectImageCmd(imageId).exec();
            } catch (NotFoundException e) {
                log.warn("Image {} not found, skipping deletion", imageId);
                return;
            }
            dockerClient.removeImageCmd(imageId).withForce(true).exec();
            log.info("Image {} deleted successfully", imageId);
        } catch (Exception e) {
            log.error("Could not delete image {}", imageId, e);
        }
    }

    /**
     * Starts a container which just spins infinitely long, meant to keep the
     * container alive and execute multiple
     * commands later on. The container must be killed manually!
     *
     * @param imageId    the docker image to use
     * @param hostConfig the HostConfig the container should be started with
     * @return the containerID of the started container
     */
    public String startSpinningContainer(String imageId, HostConfig hostConfig) {
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageId)
                .withHostConfig(hostConfig)
                .withEntrypoint("sh", "-c", "sleep 60")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container.getId();
    }

    /**
     * Starts a container which just spins infinitely long, meant to keep the
     * container alive and execute multiple
     * commands later on. The container must be killed manually!
     *
     * @param imageId the docker image to use
     * @return the containerID of the started container
     */
    public String startSpinningContainer(String imageId) {
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageId)
                .withEntrypoint("sh", "-c", "sleep infinity")
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        return container.getId();
    }

    /**
     * Executes the given command inside an already running container and returns
     * the output.
     *
     * @param containerId the ID of the container to execute the command in
     * @param command     the command to execute
     * @return the output of the command
     */
    public String executeInContainer(String containerId, String... command) {
        ExecCreateCmdResponse response = dockerClient.execCreateCmd(containerId)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            dockerClient.execStartCmd(response.getId()).exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                    if (item.getStreamType() == StreamType.STDOUT || item.getStreamType() == StreamType.STDERR) {
                        try {
                            outputStream.write(item.getPayload());
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }

        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Extracts project and m2 folder from a Docker image and saves them to a local
     * directory.
     * Creates a folder named after the breakingCommit.
     * If the directory already exists and contains content, skips extraction
     * (unless force is true).
     *
     * @param dockerImage    the Docker image to extract from
     * @param projectPath    the path to the project inside the container (e.g.,
     *                       "/project")
     * @param outputBaseDir  the base directory where the breakingCommit folder will
     *                       be created
     * @param breakingCommit the breaking commit hash (used as folder name)
     * @return the path to the created directory containing the project and m2
     */
    public Path extractProjectAndM2FromImage(String dockerImage, String projectPath, Path outputBaseDir,
            String breakingCommit) {
        return extractProjectAndM2FromImage(dockerImage, projectPath, outputBaseDir, breakingCommit, false);
    }

    /**
     * Extracts project and m2 folder from a Docker image and saves them to a local
     * directory.
     * Creates a folder named after the breakingCommit.
     *
     * @param dockerImage    the Docker image to extract from
     * @param projectPath    the path to the project inside the container (e.g.,
     *                       "/project")
     * @param outputBaseDir  the base directory where the breakingCommit folder will
     *                       be created
     * @param breakingCommit the breaking commit hash (used as folder name)
     * @param force          if true, overwrites existing directory; if false, skips
     *                       if directory exists and has content
     * @return the path to the created directory containing the project and m2
     */
    public Path extractProjectAndM2FromImage(String dockerImage, String projectPath, Path outputBaseDir,
            String breakingCommit, boolean force) {
        try {
            // Create output directory with breakingCommit name
            Path outputDir = outputBaseDir.resolve(breakingCommit);
            Path projectOutputDir = outputDir;

            // Check if project already exists and has content (other than m2)
            if (Files.exists(outputDir)) {
                try {
                    boolean hasProjectContent = Files.list(outputDir)
                            .anyMatch(path -> !"m2".equals(path.getFileName().toString()));
                    if (hasProjectContent && !force) {
                        log.info("Project already exists and is not empty at {}. Skipping extraction...", outputDir);
                        return outputDir;
                    } else if (hasProjectContent && force) {
                        log.warn("Output directory already exists with content: {}. Will overwrite.", outputDir);
                    }
                } catch (IOException e) {
                    log.warn("Could not check directory content, proceeding with extraction", e);
                }
            }

            // Ensure base image exists
            ensureBaseMavenImageExists(dockerImage);

            // Create directories if they don't exist
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
                log.info("Created output directory: {}", outputDir);
            }

            // Extract project and m2 using the helper method
            Path result = executeInContainer(dockerImage, containerId -> {
                try {
                    // Extract project
                    Files.createDirectories(projectOutputDir);
                    log.info("Extracting project from {} to {}", projectPath, projectOutputDir);

                    try (InputStream projectStream = dockerClient.copyArchiveFromContainerCmd(containerId, projectPath)
                            .exec()) {
                        copyFiles(projectOutputDir, projectStream);
                        log.info("Project extracted successfully");
                    } catch (Exception e) {
                        log.warn("Could not extract project from {}, trying common paths", projectPath, e);
                        // Try common project paths
                        String[] commonPaths = { "/project", "/app", "/workspace", "/code", "/src" };
                        boolean extracted = false;
                        for (String commonPath : commonPaths) {
                            try (InputStream projectStream = dockerClient
                                    .copyArchiveFromContainerCmd(containerId, commonPath).exec()) {
                                copyFiles(projectOutputDir, projectStream);
                                log.info("Project extracted from {}", commonPath);
                                extracted = true;
                                break;
                            } catch (Exception ex) {
                                // Try next path
                            }
                        }
                        if (!extracted) {
                            log.error("Could not extract project from any common path");
                            throw new RuntimeException("Failed to extract project from Docker image");
                        }
                    }

                    // Extract m2 folder
                    Path m2OutputDir = outputDir.resolve("m2");
                    Files.createDirectories(m2OutputDir);
                    log.info("Extracting m2 folder to {}", m2OutputDir);

                    try (InputStream m2Stream = dockerClient.copyArchiveFromContainerCmd(containerId, "/root/.m2")
                            .exec()) {
                        copyFiles(m2OutputDir, m2Stream);
                        log.info("M2 folder extracted successfully");
                    } catch (Exception e) {
                        log.warn("Could not extract m2 from /root/.m2, trying alternative locations", e);
                        // Try alternative m2 locations
                        String[] m2Paths = { "/home/user/.m2", "/.m2" };
                        boolean extracted = false;
                        for (String m2Path : m2Paths) {
                            try (InputStream m2Stream = dockerClient.copyArchiveFromContainerCmd(containerId, m2Path)
                                    .exec()) {
                                copyFiles(m2OutputDir, m2Stream);
                                log.info("M2 folder extracted from {}", m2Path);
                                extracted = true;
                                break;
                            } catch (Exception ex) {
                                // Try next path
                            }
                        }
                        if (!extracted) {
                            log.warn("Could not extract m2 folder from any location");
                        }
                    }

                    return outputDir;
                } catch (IOException e) {
                    log.error("Error creating directories during extraction", e);
                    throw new RuntimeException("Failed to create directories for extraction", e);
                }
            });

            if (result == null) {
                throw new RuntimeException("Failed to extract project and m2 from Docker image");
            }

            return result;

        } catch (Exception e) {
            log.error("Error extracting project and m2 from image {}", dockerImage, e);
            throw new RuntimeException("Failed to extract project and m2 from Docker image", e);
        }
    }

    /**
     * Extracts a specific JAR file from a Docker container's Maven repository.
     *
     * @param containerId the container ID
     * @param groupId     the Maven group ID
     * @param artifactId  the Maven artifact ID
     * @param version     the version of the artifact
     * @param outputPath  the local path where the JAR should be saved
     * @return true if extraction was successful, false otherwise
     */
    public boolean extractJarFromContainer(String containerId, String groupId, String artifactId, String version,
            Path outputPath) {
        try {
            // Check if JAR already exists before downloading
            if (Files.exists(outputPath)) {
                log.info("JAR already exists, skipping extraction: {}", outputPath);
                return true;
            }

            // Build Maven repository path:
            // /root/.m2/repository/group/artifact/version/artifact-version.jar
            String groupPath = groupId.replace(".", "/");
            String jarFileName = "%s-%s.jar".formatted(artifactId, version);
            String jarPathInContainer = "/root/.m2/repository/%s/%s/%s/%s".formatted(groupPath, artifactId, version,
                    jarFileName);

            log.info("Extracting JAR from container: {}", jarPathInContainer);

            // Create parent directories
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            try (InputStream jarStream = dockerClient.copyArchiveFromContainerCmd(containerId, jarPathInContainer)
                    .exec()) {
                copyJarFile(outputPath, jarFileName, jarStream);
                log.info("JAR extracted successfully to: {}", outputPath);
                return true;
            } catch (Exception e) {
                log.warn("Could not extract JAR from {}, trying alternative locations", jarPathInContainer, e);
                // Try alternative Maven repository locations
                String[] m2BasePaths = { "/root/.m2", "/home/user/.m2", "/.m2" };
                for (String m2Base : m2BasePaths) {
                    String altJarPath = "%s/repository/%s/%s/%s/%s".formatted(m2Base, groupPath, artifactId, version,
                            jarFileName);
                    try (InputStream jarStream = dockerClient.copyArchiveFromContainerCmd(containerId, altJarPath)
                            .exec()) {
                        copyJarFile(outputPath, jarFileName, jarStream);
                        log.info("JAR extracted from alternative location: {}", altJarPath);
                        return true;
                    } catch (Exception ex) {
                        // Try next location
                    }
                }
                log.error("Could not extract JAR {}:{}:{} from any location", groupId, artifactId, version);
                return false;
            }
        } catch (Exception e) {
            log.error("Error extracting JAR {}:{}:{}", groupId, artifactId, version, e);
            return false;
        }
    }

    /**
     * Extracts a single JAR file from a Docker image's m2 repository.
     * This is a convenience method for extracting a single JAR without needing to
     * handle both previous and new versions.
     *
     * @param dockerImage   the Docker image containing the JAR
     * @param groupId       the Maven group ID
     * @param artifactId    the Maven artifact ID
     * @param version       the version of the JAR to extract
     * @param outputBaseDir the base directory where JAR will be saved
     * @return the path to the extracted JAR, or null if extraction failed
     */
    public Path extractJarFromImage(String dockerImage, String groupId, String artifactId, String version,
            Path outputBaseDir) {
        if (version == null || version.trim().isEmpty()) {
            log.warn("Version is null or empty, cannot extract JAR");
            return null;
        }

        Path jarPath = outputBaseDir.resolve("%s-%s.jar".formatted(artifactId, version));

        Boolean extracted = executeInContainer(dockerImage, containerId -> {
            return extractJarFromContainer(containerId, groupId, artifactId, version, jarPath);
        });

        if (extracted != null && extracted && Files.exists(jarPath)) {
            return jarPath;
        }

        return null;
    }

    /**
     * Extracts specific dependency JARs (previous and new versions) from Docker
     * images.
     * Useful for API diff analysis.
     *
     * @param dockerImage     the Docker image containing the JARs
     * @param groupId         the Maven group ID
     * @param artifactId      the Maven artifact ID
     * @param previousVersion the previous version
     * @param newVersion      the new version
     * @param outputBaseDir   the base directory where JARs will be saved
     * @return a map with keys "previous" and "new" containing the paths to
     *         extracted JARs, or null if extraction failed
     */
    public Map<String, Path> extractDependencyJars(String dockerImage, String groupId, String artifactId,
            String previousVersion, String newVersion, Path outputBaseDir) {
        Map<String, Path> result = executeInContainer(dockerImage, containerId -> {
            Map<String, Path> jarPaths = new HashMap<>();

            // Extract previous version JAR
            if (previousVersion != null && !previousVersion.trim().isEmpty()) {
                Path previousJarPath = outputBaseDir.resolve("%s-%s.jar".formatted(artifactId, previousVersion));
                boolean extracted = extractJarFromContainer(containerId, groupId, artifactId, previousVersion,
                        previousJarPath);
                if (extracted) {
                    jarPaths.put("previous", previousJarPath);
                }
            }

            // Extract new version JAR
            if (newVersion != null && !newVersion.trim().isEmpty()) {
                Path newJarPath = outputBaseDir.resolve("%s-%s.jar".formatted(artifactId, newVersion));
                boolean extracted = extractJarFromContainer(containerId, groupId, artifactId, newVersion, newJarPath);
                if (extracted) {
                    jarPaths.put("new", newJarPath);
                }
            }

            return jarPaths;
        });

        return (result != null && !result.isEmpty()) ? result : null;
    }

    /**
     * Ensures that a Docker image exists, building it from Dockerfile if it doesn't
     * exist.
     * 
     * @param imageName      the name of the Docker image (e.g.,
     *                       "agent-base:latest")
     * @param dockerfilePath path to the Dockerfile
     * @throws InterruptedException if the build process is interrupted
     * @throws IOException          if there's an error reading the Dockerfile
     */
    public void ensureImageExistsOrBuildFromDockerfile(String imageName, Path dockerfilePath)
            throws InterruptedException, IOException {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
            if (verbose) {
                log.info("Docker image {} already exists, skipping build", imageName);
            }
        } catch (NotFoundException e) {
            // Image doesn't exist, build from Dockerfile
            if (verbose) {
                log.info("Docker image {} not found, building from Dockerfile: {}", imageName, dockerfilePath);
            }

            if (!Files.exists(dockerfilePath)) {
                throw new IOException("Dockerfile not found at: " + dockerfilePath);
            }

            Path dockerfileDir = dockerfilePath.getParent();

            // Parse image name to extract tag
            String[] imageParts = imageName.split(":");
            String repository = imageParts[0];
            String tag = imageParts.length > 1 ? imageParts[1] : "latest";

            // Build image from Dockerfile
            BuildImageResultCallback callback = new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    if (item.getStream() != null && verbose) {
                        log.debug("Build output: {}", item.getStream().trim());
                    }
                    super.onNext(item);
                }
            };

            try (InputStream dockerfileTar = buildTarInputStream(dockerfileDir)) {
                String imageId = dockerClient.buildImageCmd(dockerfileTar)
                        .withTag(imageName)
                        .exec(callback)
                        .awaitImageId();

                if (verbose) {
                    log.info("Successfully built Docker image {} with ID: {}", imageName, imageId);
                }
            }
        }
    }

    /**
     * Creates a TAR input stream containing the Dockerfile for building.
     */
    private InputStream buildTarInputStream(Path dockerfileDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(baos)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            // Add Dockerfile
            Path dockerfile = dockerfileDir.resolve("Dockerfile");
            if (Files.exists(dockerfile)) {
                TarArchiveEntry entry = new TarArchiveEntry(dockerfile.toFile(), "Dockerfile");
                tarOut.putArchiveEntry(entry);
                Files.copy(dockerfile, tarOut);
                tarOut.closeArchiveEntry();
            }

            tarOut.finish();
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Finds the m2 folder path relative to the project directory.
     * When extractProjectAndM2FromImage extracts, it saves m2 to extractedDir/m2.
     * This method looks for m2 in the parent directory of projectDir.
     * 
     * @param projectDir the project directory path
     * @return the path to the m2 folder if found, null otherwise
     */
    public Path findM2Folder(Path projectDir) {
        if (projectDir == null) {
            return null;
        }

        Path parentDir = projectDir.getParent();
        if (parentDir != null) {
            Path m2Direct = parentDir.resolve("m2");

            // Check if m2 directory exists (this contains the .m2 repository content)
            if (Files.exists(m2Direct) && Files.isDirectory(m2Direct)) {
                log.info("M2 folder found at: {}", m2Direct);
                return m2Direct;
            }
        }

        log.debug("M2 folder not found at {}", parentDir != null ? parentDir.resolve("m2") : "unknown");
        return null;
    }

    /**
     * Adds m2 folder mount to the list of binds if the m2 folder exists.
     * Handles multiple possible structures when copying from container:
     * 1. m2Folder/.m2/repository/... (when TAR contains .m2/repository/...)
     * 2. m2Folder/root/.m2/repository/... (when TAR contains
     * root/.m2/repository/...)
     * 3. m2Folder/repository/... (when TAR contains repository/... directly)
     * 
     * @param binds    the list of binds to add to
     * @param m2Folder the path to the m2 folder (can be null)
     */
    public void addM2MountIfExists(List<Bind> binds, Path m2Folder) {
        if (m2Folder != null) {
            // Normalize the m2Folder path first
            Path normalizedM2Folder = m2Folder.toAbsolutePath().normalize();
            if (!Files.exists(normalizedM2Folder) || !Files.isDirectory(normalizedM2Folder)) {
                log.info("M2 folder not provided or does not exist: {}. Maven will use default repository.", normalizedM2Folder);
                return;
            }
            
            Path mountPath = null;

            // Check for m2Folder/.m2/repository (most common case)
            Path m2DotM2 = normalizedM2Folder.resolve(".m2");
            if (Files.exists(m2DotM2) && Files.isDirectory(m2DotM2)) {
                mountPath = m2DotM2;
                log.info("Found .m2 subdirectory in m2 folder");
            } else {
                // Check for m2Folder/root/.m2/repository (when TAR preserves root/.m2 path)
                Path rootDotM2 = normalizedM2Folder.resolve("root/.m2");
                if (Files.exists(rootDotM2) && Files.isDirectory(rootDotM2)) {
                    mountPath = rootDotM2;
                    log.info("Found root/.m2 subdirectory in m2 folder");
                } else {
                    // Check if m2Folder contains repository directly
                    Path repository = normalizedM2Folder.resolve("repository");
                    if (Files.exists(repository) && Files.isDirectory(repository)) {
                        mountPath = normalizedM2Folder;
                        log.info("Found repository directory directly in m2 folder");
                    }
                }
            }

            if (mountPath != null) {
                // Normalize and verify the path exists before mounting
                Path normalizedMountPath = mountPath.toAbsolutePath().normalize();
                if (!Files.exists(normalizedMountPath) || !Files.isDirectory(normalizedMountPath)) {
                    log.warn("M2 mount path does not exist or is not a directory: {}. Skipping m2 mount.", normalizedMountPath);
                } else {
                    binds.add(new Bind(
                            normalizedMountPath.toString(),
                            new Volume("/root/.m2"),
                            AccessMode.rw));
                    log.info("M2 folder will be mounted: {} -> /root/.m2", normalizedMountPath);
                }
            } else {
                log.warn(
                        "M2 folder structure not recognized at {}. Expected one of: .m2/, root/.m2/, or repository/. Maven will use default repository.",
                        normalizedM2Folder);
            }
        }
    }

    /**
     * Executes a Maven command (e.g., 'mvn compile') in a Docker container by
     * mounting
     * the project as a volume and running the command with environment variables.
     * The console output (stdout and stderr) is exported to a log file.
     * 
     * This is similar to running: gemini --yolo "execute 'mvn compile'"
     * 
     * IMPORTANT: This method creates a NEW container for each execution and cleans
     * it up afterwards.
     * If you need to execute multiple commands in the same container, consider
     * using
     * startSpinningContainer() + executeInContainer() + manual cleanup, or use
     * reproduceWithMount()
     * which is designed for iterative build workflows.
     * 
     * @param dockerImage          the Docker image to use for the container
     * @param projectDir           the local path to the project directory to mount
     * @param containerWorkDir     the working directory path inside the container
     *                             (e.g., "/workspace")
     * @param mavenCommand         the Maven command to execute (e.g., "mvn
     *                             compile")
     * @param logFile              the local path where the log file should be saved
     * @param environmentVariables map of environment variables to set in the
     *                             container (can be null)
     * @param m2Folder             optional path to the m2 folder to mount (can be
     *                             null)
     * @return true if the command executed successfully (exit code 0), false
     *         otherwise
     */
    // public boolean executeMavenCommandInContainer(String dockerImage, Path
    // projectDir,
    // String containerWorkDir, String mavenCommand,
    // Path logFile, Map<String, String> environmentVariables,
    // Path m2Folder) {
    // String containerId = null;
    // try {
    // // Ensure base image exists
    // ensureBaseMavenImageExists(dockerImage);

    // // Normalize container work directory (ensure it starts with /)
    // String normalizedWorkDir = containerWorkDir.startsWith("/")
    // ? containerWorkDir
    // : "/" + containerWorkDir;

    // Path absoluteProjectPath = projectDir.toAbsolutePath().normalize();

    // // Prepare volume binds - always mount the project
    // List<Bind> binds = new ArrayList<>();
    // binds.add(new Bind(
    // absoluteProjectPath.toString(),
    // new Volume(normalizedWorkDir),
    // AccessMode.rw));

    // // Add m2 folder mount if provided
    // addM2MountIfExists(binds, m2Folder);

    // // Create host config with volume mounts
    // HostConfig hostConfig = HostConfig.newHostConfig()
    // .withBinds(binds);

    // // Build container creation command
    // // Use sleep infinity to keep container alive indefinitely until we
    // explicitly stop it
    // // This prevents the container from stopping before the exec command
    // completes
    // CreateContainerCmd createCmd = dockerClient.createContainerCmd(dockerImage)
    // .withHostConfig(hostConfig)
    // .withWorkingDir("/") // Set working dir to root initially
    // .withCmd("sh", "-c", "sleep infinity"); // Keep container alive indefinitely

    // // Add environment variables if provided
    // if (environmentVariables != null && !environmentVariables.isEmpty()) {
    // List<String> envList = new ArrayList<>();
    // for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
    // envList.add(entry.getKey() + "=" + entry.getValue());
    // }
    // createCmd.withEnv(envList);
    // log.info("Setting {} environment variables in container",
    // environmentVariables.size());
    // }

    // // Create container
    // CreateContainerResponse container = createCmd.exec();
    // containerId = container.getId();
    // log.info("Created container {} for Maven command execution (project mounted
    // at {})",
    // containerId, normalizedWorkDir);

    // // Start the container
    // dockerClient.startContainerCmd(containerId).exec();
    // log.info("Started container {}", containerId);

    // // Execute command in the work directory (project folder)
    // // For interactive commands like gemini, we need to ensure all output is
    // captured
    // // Redirect both stdout and stderr to tee, which writes to both file and
    // stdout
    // // This ensures we capture everything even if the command writes to stderr
    // String setupAndRunCommand = String.format(
    // "cd %s && (%s) 2>&1 | tee mavenCompile.log",
    // normalizedWorkDir,
    // mavenCommand
    // );

    // log.info("Will execute command in directory: {}", normalizedWorkDir);

    // log.info("Executing command: {} in container", mavenCommand);
    // // For interactive commands like gemini, we may need TTY, but it's disabled
    // for exec
    // // Instead, we ensure all output is captured via stdout/stderr redirection
    // ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
    // .withCmd("sh", "-c", setupAndRunCommand)
    // .withAttachStdout(true)
    // .withAttachStderr(true)
    // .withAttachStdin(false)
    // .exec();

    // // Capture output - use a callback that properly handles all stream types
    // ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    // try {
    // dockerClient.execStartCmd(execResponse.getId())
    // .exec(new ResultCallback.Adapter<Frame>() {
    // @Override
    // public void onNext(Frame item) {
    // // Capture both stdout and stderr
    // if (item.getStreamType() == StreamType.STDOUT ||
    // item.getStreamType() == StreamType.STDERR) {
    // try {
    // outputStream.write(item.getPayload());
    // // Also log to console for debugging
    // String line = new String(item.getPayload(), StandardCharsets.UTF_8);
    // if (verbose && !line.trim().isEmpty()) {
    // log.debug("Container output: {}", line.trim());
    // }
    // } catch (Exception e) {
    // log.error("Error capturing output", e);
    // }
    // }
    // }

    // @Override
    // public void onError(Throwable throwable) {
    // log.error("Error in exec stream", throwable);
    // super.onError(throwable);
    // }
    // })
    // .awaitCompletion();
    // } catch (InterruptedException e) {
    // log.error("Interrupted while waiting for command completion", e);
    // Thread.currentThread().interrupt();
    // }

    // // Wait for the exec command to complete and get exit code
    // InspectExecResponse execInspect =
    // dockerClient.inspectExecCmd(execResponse.getId()).exec();
    // Integer exitCode = execInspect.getExitCode();
    // boolean success = exitCode != null && exitCode == EXIT_CODE_OK;

    // log.info("Maven command execution completed with exit code: {}", exitCode);

    // // Copy the log file from container (or read from mounted volume)
    // String logPathInContainer = normalizedWorkDir + "/mavenCompile.log";
    // boolean logCopied = false;

    // // Try to read log from mounted volume first (faster and more reliable)
    // Path logFileOnHost = absoluteProjectPath.resolve("mavenCompile.log");
    // if (Files.exists(logFileOnHost)) {
    // try {
    // // Only create parent directories if logFile has a parent (i.e., is not a
    // relative path)
    // if (logFile.getParent() != null) {
    // Files.createDirectories(logFile.getParent());
    // }
    // Files.copy(logFileOnHost, logFile,
    // java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    // log.info("Log file copied from mounted volume to {}", logFile);
    // logCopied = true;
    // } catch (IOException e) {
    // log.warn("Could not copy log file from mounted volume: {}", e.getMessage());
    // }
    // }

    // // Fallback: try to copy log file from container
    // if (!logCopied) {
    // try {
    // try (InputStream logStream = dockerClient
    // .copyArchiveFromContainerCmd(containerId, logPathInContainer).exec()) {

    // // Only create parent directories if logFile has a parent (i.e., is not a
    // relative path)
    // if (logFile.getParent() != null) {
    // Files.createDirectories(logFile.getParent());
    // }

    // // Extract the log file from the TAR archive
    // try (TarArchiveInputStream tarStream = new TarArchiveInputStream(logStream))
    // {
    // TarArchiveEntry entry;
    // while ((entry = tarStream.getNextTarEntry()) != null) {
    // if (!entry.isDirectory()) {
    // String entryName = entry.getName();
    // // Check if this is the log file (could be at various paths in TAR)
    // if (entryName.endsWith("mavenCompile.log") ||
    // entryName.equals("mavenCompile.log") ||
    // entryName.endsWith(normalizedWorkDir + "/mavenCompile.log")) {
    // byte[] logContent = tarStream.readAllBytes();
    // Files.write(logFile, logContent, StandardOpenOption.CREATE,
    // StandardOpenOption.TRUNCATE_EXISTING);
    // log.info("Log file copied from container to {}", logFile);
    // logCopied = true;
    // break;
    // }
    // }
    // }
    // }
    // }
    // } catch (Exception e) {
    // log.warn("Could not copy log file from container: {}. Using captured output
    // instead.",
    // e.getMessage());
    // }
    // }

    // // Final fallback: if log file couldn't be copied, write the captured output
    // // Also always write the captured output to ensure we have it even if tee
    // worked
    // try {
    // // Only create parent directories if logFile has a parent (i.e., is not a
    // relative path)
    // if (logFile.getParent() != null) {
    // Files.createDirectories(logFile.getParent());
    // }
    // String output = outputStream.toString(StandardCharsets.UTF_8);

    // // If we already copied from file, append the captured output to ensure
    // nothing is missing
    // // Otherwise, write it as the main content
    // if (logCopied && !output.trim().isEmpty()) {
    // // Append captured output to existing log file (in case tee missed something)
    // Files.write(logFile,
    // ("\n\n=== Additional captured output ===\n" +
    // output).getBytes(StandardCharsets.UTF_8),
    // StandardOpenOption.APPEND);
    // log.info("Appended captured output to log file at {}", logFile);
    // } else if (!logCopied) {
    // // Write captured output as the main log content
    // Files.write(logFile, output.getBytes(StandardCharsets.UTF_8),
    // StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    // log.info("Log file created from captured output at {} (size: {} bytes)",
    // logFile, output.length());
    // }
    // } catch (IOException e) {
    // log.error("Could not write log file", e);
    // }

    // return success;

    // } catch (Exception e) {
    // log.error("Error executing Maven command in container", e);
    // // Try to save error to log file
    // try {
    // // Only create parent directories if logFile has a parent (i.e., is not a
    // relative path)
    // if (logFile != null && logFile.getParent() != null) {
    // Files.createDirectories(logFile.getParent());
    // }
    // if (logFile != null) {
    // String errorMsg = String.format("[ERROR] Failed to execute Maven command:
    // %s%nError: %s%n",
    // mavenCommand, e.getMessage());
    // Files.write(logFile, errorMsg.getBytes(StandardCharsets.UTF_8),
    // StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    // }
    // } catch (IOException ioException) {
    // log.error("Could not write error log file", ioException);
    // }
    // return false;
    // } finally {
    // // Clean up container
    // if (containerId != null) {
    // // cleanupContainer(containerId);
    // }
    // }
    // }

    /**
     * Executes a Maven command (or Agent command) in a Docker container.
     * Optimized for Agent execution with TTY support and proper cleanup.
     */
    public boolean executeMavenCommandInContainer(String dockerImage, Path projectDir,
            String containerWorkDir, String mavenCommand,
            Path logFile, Map<String, String> environmentVariables,
            Path m2Folder, boolean verbose) {
        return executeMavenCommandInContainer(dockerImage, projectDir, containerWorkDir, mavenCommand,
                logFile, environmentVariables, m2Folder, null, verbose);
    }

    public boolean executeMavenCommandInContainer(String dockerImage, Path projectDir,
            String containerWorkDir, String mavenCommand,
            Path logFile, Map<String, String> environmentVariables,
            Path m2Folder, Path spoonDocsFolder, boolean verbose) {
        String containerId = null;
        try {
            ensureBaseMavenImageExists(dockerImage);

            String normalizedWorkDir = containerWorkDir.startsWith("/") ? containerWorkDir : "/" + containerWorkDir;
            Path absoluteProjectPath = projectDir.toAbsolutePath().normalize();

            // 1. Configurar Montajes de Volumen (Proyecto + M2 + Spoon Docs)
            List<Bind> binds = new ArrayList<>();
            binds.add(new Bind(absoluteProjectPath.toString(), new Volume(normalizedWorkDir), AccessMode.rw));
            addM2MountIfExists(binds, m2Folder);
            
            // Add Spoon documentation mount if provided (mount it in the workspace/api-docs)
            // Note: This creates a bind mount inside the already-mounted workspace directory
            // Docker supports nested bind mounts, so this should work
            if (spoonDocsFolder != null && Files.exists(spoonDocsFolder) && Files.isDirectory(spoonDocsFolder)) {
                // Mount documentation in the workspace at /workspace/api-docs
                String spoonDocsMountPath = normalizedWorkDir + "/api-docs";
                binds.add(new Bind(
                        spoonDocsFolder.toAbsolutePath().toString(),
                        new Volume(spoonDocsMountPath),
                        AccessMode.ro)); // Read-only mount for documentation
                log.info("Spoon documentation will be mounted: {} -> {}", spoonDocsFolder, spoonDocsMountPath);
            }

            HostConfig hostConfig = HostConfig.newHostConfig().withBinds(binds);

            // 2. Crear Contenedor (Estrategia Sleep Infinity)
            // Mantenemos el contenedor vivo indefinidamente para poder ejecutar el comando
            // exec después.
            CreateContainerCmd createCmd = dockerClient.createContainerCmd(dockerImage)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/")
                    .withCmd("sh", "-c", "sleep infinity");

            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                List<String> envList = new ArrayList<>();
                for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                    envList.add(entry.getKey() + "=" + entry.getValue());
                }
                createCmd.withEnv(envList);
            }

            CreateContainerResponse container = createCmd.exec();
            containerId = container.getId();
            log.info("Created container {} for execution", containerId);

            dockerClient.startContainerCmd(containerId).exec();

            // 3. Preparar el Comando
            // Usamos "2>&1" para mezclar errores y salida estándar.
            // Usamos "tee" para guardar en archivo dentro del volumen Y mostrar en consola
            // al mismo tiempo.
            String setupAndRunCommand = String.format(
                    "cd %s && (%s) 2>&1 | tee mavenCompile.log",
                    normalizedWorkDir,
                    mavenCommand);

            log.info("Executing command in container: {}", mavenCommand);

            // 4. Ejecutar con TTY (CRÍTICO PARA AGENTES)
            // .withTty(true) es vital para que herramientas como Gemini no buffericen el
            // output
            // y para que se comporten como si estuvieran en una terminal real.
            ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", setupAndRunCommand)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .exec();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try {
                dockerClient.execStartCmd(execResponse.getId())
                        .exec(new ResultCallback.Adapter<Frame>() {
                            @Override
                            public void onNext(Frame item) {
                                try {
                                    outputStream.write(item.getPayload());
                                    // Opcional: Imprimir en consola de Java para depuración en tiempo real
                                    if (verbose) {
                                        System.out.print(new String(item.getPayload(), StandardCharsets.UTF_8));
                                    }
                                } catch (Exception e) {
                                    log.error("Error capturing output", e);
                                }
                            }
                        })
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for command", e);
                Thread.currentThread().interrupt();
            }

            // 5. Verificar Código de Salida
            InspectExecResponse execInspect = dockerClient.inspectExecCmd(execResponse.getId()).exec();
            Integer exitCode = execInspect.getExitCode();
            boolean success = exitCode != null && exitCode == EXIT_CODE_OK;

            log.info("Execution completed. Exit code: {}", exitCode);

            // 6. Recuperación de Logs (Estrategia Robusta)
            // Intento A: Copiar el archivo físico del volumen montado (Rápido y seguro)
            Path logFileOnHost = absoluteProjectPath.resolve("mavenCompile.log");
            boolean logSaved = false;

            if (Files.exists(logFileOnHost)) {
                try {
                    if (logFile.getParent() != null)
                        Files.createDirectories(logFile.getParent());
                    Files.copy(logFileOnHost, logFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logSaved = true;
                } catch (IOException e) {
                    log.warn("Failed to copy log from mounted volume: {}", e.getMessage());
                }
            }

            // Intento B (Fallback): Si no hay archivo, guardamos lo capturado en memoria
            if (!logSaved) {
                try {
                    if (logFile.getParent() != null)
                        Files.createDirectories(logFile.getParent());
                    Files.write(logFile, outputStream.toByteArray(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    log.info("Log saved from captured stream (fallback).");
                } catch (IOException e) {
                    log.error("Failed to write log file", e);
                }
            }

            return success;

        } catch (Exception e) {
            log.error("Error executing command in container", e);
            return false;
        } finally {
            // 7. LIMPIEZA (CRÍTICO: DESCOMENTADO)
            // Esto evita que se acumulen cientos de contenedores "zombies"
            if (containerId != null) {
                // cleanupContainer(containerId);
            }
        }
    }

    /**
     * Overload version without m2 folder for backward compatibility.
     */
    public boolean executeMavenCommandInContainer(String dockerImage, Path projectDir,
            String containerWorkDir, String mavenCommand,
            Path logFile, Map<String, String> environmentVariables) {
        return executeMavenCommandInContainer(dockerImage, projectDir, containerWorkDir,
                mavenCommand, logFile, environmentVariables, null, this.verbose);
    }

    /**
     * Overload version without environment variables and m2 folder for backward
     * compatibility.
     */
    public boolean executeMavenCommandInContainer(String dockerImage, Path projectDir,
            String containerWorkDir, String mavenCommand,
            Path logFile) {
        return executeMavenCommandInContainer(dockerImage, projectDir, containerWorkDir,
                mavenCommand, logFile, null, null, this.verbose);
    }

    /**
     * Executes a Maven command in a container with a workspace structure.
     * Mounts:
     * - Workspace directory at /workspace (contains spoon-base-template)
     * - Project directory at /workspace/{projectName}/ (mounted directly from branch)
     * - Spoon API docs at /workspace/api-docs/ (if provided)
     * - M2 folder at /root/.m2 (if provided)
     *
     * @param dockerImage the Docker image to use
     * @param workspaceDir the workspace directory (contains spoon-base-template)
     * @param projectDir the project directory (already in correct branch)
     * @param projectName the project name (used for mount path)
     * @param containerWorkDir the base working directory in container (e.g., /workspace)
     * @param mavenCommand the command to execute
     * @param logFile the log file path on host
     * @param environmentVariables environment variables to set
     * @param m2Folder the M2 folder to mount (optional)
     * @param spoonDocsFolder the Spoon API docs folder to mount (optional)
     * @param verbose whether to enable verbose logging
     * @return true if command succeeded, false otherwise
     */
    public boolean executeMavenCommandInContainerWithWorkspace(String dockerImage, Path workspaceDir,
            Path projectDir, String projectName, String containerWorkDir, String mavenCommand,
            Path logFile, Map<String, String> environmentVariables, Path m2Folder, Path spoonDocsFolder,
            boolean verbose) {
        String containerId = null;
        try {
            ensureBaseMavenImageExists(dockerImage);

            String normalizedWorkDir = containerWorkDir.startsWith("/") ? containerWorkDir : "/" + containerWorkDir;
            Path absoluteWorkspacePath = workspaceDir.toAbsolutePath().normalize();
            Path absoluteProjectPath = projectDir.toAbsolutePath().normalize();

            // 1. Configure volume mounts
            List<Bind> binds = new ArrayList<>();
            
            // Mount workspace at /workspace (contains spoon-base-template)
            binds.add(new Bind(absoluteWorkspacePath.toString(), new Volume(normalizedWorkDir), AccessMode.rw));
            
            // Mount project at /workspace/{projectName}/ (nested mount inside workspace)
            String projectMountPath = normalizedWorkDir + "/" + projectName;
            binds.add(new Bind(absoluteProjectPath.toString(), new Volume(projectMountPath), AccessMode.rw));
            
            // Add M2 mount if provided
            addM2MountIfExists(binds, m2Folder);
            
            // Add Spoon documentation mount if provided (mount it in the workspace/api-docs)
            if (spoonDocsFolder != null && Files.exists(spoonDocsFolder) && Files.isDirectory(spoonDocsFolder)) {
                String spoonDocsMountPath = normalizedWorkDir + "/api-docs";
                binds.add(new Bind(
                        spoonDocsFolder.toAbsolutePath().toString(),
                        new Volume(spoonDocsMountPath),
                        AccessMode.ro)); // Read-only mount for documentation
                log.info("Spoon documentation will be mounted: {} -> {}", spoonDocsFolder, spoonDocsMountPath);
            }

            HostConfig hostConfig = HostConfig.newHostConfig().withBinds(binds);

            // 2. Create container (sleep infinity strategy)
            CreateContainerCmd createCmd = dockerClient.createContainerCmd(dockerImage)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/")
                    .withCmd("sh", "-c", "sleep infinity");

            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                List<String> envList = new ArrayList<>();
                for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                    envList.add(entry.getKey() + "=" + entry.getValue());
                }
                createCmd.withEnv(envList);
            }

            CreateContainerResponse container = createCmd.exec();
            containerId = container.getId();
            log.info("Created container {} for execution with workspace structure", containerId);

            dockerClient.startContainerCmd(containerId).exec();

            // 3. Prepare command
            // Verify m2 mount before executing command (for debugging)
            String m2CheckCommand = m2Folder != null 
                    ? "ls -la /root/.m2/repository 2>/dev/null | head -5 || echo 'M2 repository not accessible'" 
                    : "echo 'M2 folder not mounted'";
            String setupAndRunCommand = String.format(
                    "cd %s && echo '=== M2 Repository Check ===' && %s && echo '=== Executing Maven Command ===' && (%s) 2>&1 | tee mavenCompile.log",
                    normalizedWorkDir,
                    m2CheckCommand,
                    mavenCommand);

            log.info("Executing command in container: {} (m2 mounted: {})", mavenCommand, m2Folder != null);

            // 4. Execute with TTY (critical for agents)
            ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", setupAndRunCommand)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .exec();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try {
                dockerClient.execStartCmd(execResponse.getId())
                        .exec(new ResultCallback.Adapter<Frame>() {
                            @Override
                            public void onNext(Frame item) {
                                try {
                                    outputStream.write(item.getPayload());
                                    if (verbose) {
                                        System.out.print(new String(item.getPayload(), StandardCharsets.UTF_8));
                                    }
                                } catch (Exception e) {
                                    log.error("Error capturing output", e);
                                }
                            }
                        })
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for command", e);
                Thread.currentThread().interrupt();
            }

            // 5. Check exit code
            InspectExecResponse execInspect = dockerClient.inspectExecCmd(execResponse.getId()).exec();
            Integer exitCode = execInspect.getExitCode();
            boolean success = exitCode != null && exitCode == EXIT_CODE_OK;

            log.info("Execution completed. Exit code: {}", exitCode);

            // 6. Retrieve logs
            // Try to copy log from workspace (project might have written it there)
            Path logFileInWorkspace = absoluteWorkspacePath.resolve("mavenCompile.log");
            Path logFileInProject = absoluteProjectPath.resolve("mavenCompile.log");
            boolean logSaved = false;

            // Try workspace first
            if (Files.exists(logFileInWorkspace)) {
                try {
                    if (logFile.getParent() != null)
                        Files.createDirectories(logFile.getParent());
                    Files.copy(logFileInWorkspace, logFile, StandardCopyOption.REPLACE_EXISTING);
                    logSaved = true;
                } catch (IOException e) {
                    log.warn("Failed to copy log from workspace: {}", e.getMessage());
                }
            }
            
            // Try project directory
            if (!logSaved && Files.exists(logFileInProject)) {
                try {
                    if (logFile.getParent() != null)
                        Files.createDirectories(logFile.getParent());
                    Files.copy(logFileInProject, logFile, StandardCopyOption.REPLACE_EXISTING);
                    logSaved = true;
                } catch (IOException e) {
                    log.warn("Failed to copy log from project: {}", e.getMessage());
                }
            }

            // Fallback: save from captured stream
            if (!logSaved) {
                try {
                    if (logFile.getParent() != null)
                        Files.createDirectories(logFile.getParent());
                    Files.write(logFile, outputStream.toByteArray(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    log.info("Log saved from captured stream (fallback).");
                } catch (IOException e) {
                    log.error("Failed to write log file", e);
                }
            }

            return success;

        } catch (Exception e) {
            log.error("Error executing command in container", e);
            return false;
        } finally {
            // Cleanup
            if (containerId != null) {
                // cleanupContainer(containerId);
            }
        }
    }

}
