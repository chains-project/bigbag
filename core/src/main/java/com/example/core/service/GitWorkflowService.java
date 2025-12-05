package com.example.core.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Service for managing local Git repositories for extracted projects.
 * Replicates the behavior of Bacardi's git-manager.
 */
public class GitWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(GitWorkflowService.class);

    /**
     * Initializes a git repository in the given project directory, adds all files,
     * and commits them.
     * If the repository already exists, it does nothing.
     *
     * @param projectDir the directory to initialize as a git repo
     * @param message    the commit message
     */
    public void initAndCommit(Path projectDir, String message) {
        File gitDir = projectDir.resolve(".git").toFile();
        if (gitDir.exists()) {
            log.info("Git repository already exists at {}", projectDir);
            return;
        }

        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            log.info("Initialized git repository at {}", projectDir);

            // Add all files
            git.add().addFilepattern(".").call();

            // Commit
            git.commit().setMessage(message).call();
            log.info("Committed all files with message: '{}'", message);

        } catch (GitAPIException e) {
            log.error("Failed to initialize and commit git repository at {}", projectDir, e);
            throw new RuntimeException("Git initialization failed", e);
        }
    }

    /**
     * Creates a new branch and checks it out.
     * If the branch already exists, it just checks it out.
     *
     * @param projectDir the project directory
     * @param branchName the name of the branch to create/checkout
     */
    public void createAndCheckoutBranch(Path projectDir, String branchName) {
        try (Git git = Git.open(projectDir.toFile())) {
            boolean branchExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/" + branchName));

            if (branchExists) {
                log.info("Branch {} already exists, checking out...", branchName);
                git.checkout().setName(branchName).call();
            } else {
                log.info("Creating and checking out branch {}", branchName);
                git.checkout().setCreateBranch(true).setName(branchName).call();
            }

        } catch (IOException | GitAPIException e) {
            log.error("Failed to create/checkout branch {} at {}", branchName, projectDir, e);
            throw new RuntimeException("Git branch operation failed", e);
        }
    }
}
