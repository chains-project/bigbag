package com.example.core.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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

        try (Git git = Git.init().setDirectory(projectDir.toFile()).setInitialBranch("master").call()) {
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

    /**
     * Creates a new branch from a specific base branch and checks it out.
     *
     * @param projectDir the project directory
     * @param branchName the name of the new branch
     * @param baseBranch the name of the base branch
     */
    public void createBranchFromBase(Path projectDir, String branchName, String baseBranch) {
        try (Git git = Git.open(projectDir.toFile())) {
            log.info("Creating branch {} from {}", branchName, baseBranch);
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(baseBranch)
                    .call();
        } catch (IOException | GitAPIException e) {
            log.error("Failed to create branch {} from {} at {}", branchName, baseBranch, projectDir, e);
            throw new RuntimeException("Git branch creation failed", e);
        }
    }

    /**
     * Checks out an existing branch.
     *
     * @param projectDir the project directory
     * @param branchName the name of the branch to checkout
     */
    public void checkout(Path projectDir, String branchName) {
        try (Git git = Git.open(projectDir.toFile())) {
            log.info("Checking out branch {}", branchName);
            git.checkout().setName(branchName).call();
        } catch (IOException | GitAPIException e) {
            log.error("Failed to checkout branch {} at {}", branchName, projectDir, e);
            throw new RuntimeException("Git checkout failed", e);
        }
    }

    /**
     * Deletes a branch.
     *
     * @param projectDir the project directory
     * @param branchName the name of the branch to delete
     */
    public void deleteBranch(Path projectDir, String branchName) {
        try (Git git = Git.open(projectDir.toFile())) {
            log.info("Deleting branch {}", branchName);
            git.branchDelete().setBranchNames(branchName).setForce(true).call();
        } catch (IOException | GitAPIException e) {
            log.warn("Failed to delete branch {} at {} (might not exist)", branchName, projectDir);
        }
    }

    /**
     * Commits all changes with the given message.
     *
     * @param projectDir the project directory
     * @param message    the commit message
     */
    public void commitAll(Path projectDir, String message) {
        try (Git git = Git.open(projectDir.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
            log.info("Committed changes: '{}'", message);
        } catch (IOException | GitAPIException e) {
            log.error("Failed to commit changes at {}", projectDir, e);
            throw new RuntimeException("Git commit failed", e);
        }
    }

    /**
     * Prepares project for new repair process:
     * 1. Commits any uncommitted changes
     * 2. Ensures main branch exists (creates from master if needed)
     * 3. Checks out main branch (shared, no processId)
     * 
     * @param projectDir the project directory
     * @return the main branch name ("main")
     */
    public String prepareForNewProcess(Path projectDir) {
        try (Git git = Git.open(projectDir.toFile())) {
            // 1. Check for uncommitted changes
            if (hasUncommittedChanges(projectDir)) {
                log.info("Uncommitted changes detected, committing before new repair process");
                commitAll(projectDir, "Auto-commit: Uncommitted changes before new repair process");
            }
            
            // 2. Ensure main branch exists (create from master if needed)
            ensureMainBranchExists(projectDir);
            
            // 3. Checkout main (shared branch, no processId)
            checkout(projectDir, "main");
            
            return "main";
        } catch (IOException e) {
            log.error("Failed to prepare project for new process at {}", projectDir, e);
            throw new RuntimeException("Failed to prepare project for new process", e);
        }
    }

    /**
     * Checks if there are uncommitted changes in the repository.
     *
     * @param projectDir the project directory
     * @return true if there are uncommitted changes, false otherwise
     */
    public boolean hasUncommittedChanges(Path projectDir) {
        try (Git git = Git.open(projectDir.toFile())) {
            org.eclipse.jgit.api.Status status = git.status().call();
            return !status.isClean();
        } catch (IOException | GitAPIException e) {
            log.warn("Failed to check git status at {}, assuming no uncommitted changes", projectDir);
            return false;
        }
    }

    /**
     * Ensures main branch exists. Creates it from master if needed.
     * If master doesn't exist either, creates main as initial branch.
     *
     * @param projectDir the project directory
     */
    public void ensureMainBranchExists(Path projectDir) {
        try (Git git = Git.open(projectDir.toFile())) {
            // Check if main branch exists
            boolean mainExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/main"));
            
            if (mainExists) {
                log.debug("Main branch already exists");
                return;
            }
            
            // Check if master branch exists
            boolean masterExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/master"));
            
            if (masterExists) {
                log.info("Creating main branch from master");
                git.checkout()
                        .setCreateBranch(true)
                        .setName("main")
                        .setStartPoint("master")
                        .call();
            } else {
                // No master either, create main as new branch from current HEAD
                log.info("Creating main branch from current HEAD");
                git.checkout()
                        .setCreateBranch(true)
                        .setName("main")
                        .call();
            }
        } catch (IOException | GitAPIException e) {
            log.error("Failed to ensure main branch exists at {}", projectDir, e);
            throw new RuntimeException("Failed to ensure main branch exists", e);
        }
    }

    /**
     * Creates an agent branch with process ID from main.
     *
     * @param projectDir the project directory
     * @param ruleGenerator the rule generator name (e.g., "spoon")
     * @param processId the process identifier
     */
    public void createAgentBranch(Path projectDir, String ruleGenerator, String processId) {
        String agentBranchName = String.format("agent-%s-%s", ruleGenerator, processId);
        createBranchFromBase(projectDir, agentBranchName, "main");
        checkout(projectDir, agentBranchName);
    }

    /**
     * Creates a repair branch with process ID from main.
     *
     * @param projectDir the project directory
     * @param category the failure category (e.g., "compilation_failure")
     * @param processId the process identifier
     */
    public void createRepairBranch(Path projectDir, String category, String processId) {
        String repairBranchName = String.format("repair/%s-%s", category.toLowerCase(), processId);
        createBranchFromBase(projectDir, repairBranchName, "main");
        checkout(projectDir, repairBranchName);
    }

    /**
     * Creates an attempt branch with process ID from a base branch.
     *
     * @param projectDir the project directory
     * @param attemptNumber the attempt number
     * @param processId the process identifier
     * @param baseBranch the base branch name
     */
    public void createAttemptBranch(Path projectDir, int attemptNumber, String processId, String baseBranch) {
        String attemptBranchName = String.format("attempt_%d-%s", attemptNumber, processId);
        createBranchFromBase(projectDir, attemptBranchName, baseBranch);
        checkout(projectDir, attemptBranchName);
    }
}
