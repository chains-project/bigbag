# Docker Build Module

This module provides Docker container and image management functionality for building and testing Maven projects in isolated Docker environments. The module supports both file copying and volume mounting approaches, with volume mounting being recommended for iterative development workflows.

## Overview

The `docker-build` module contains the `DockerBuild` class which provides comprehensive Docker operations including:

- Container lifecycle management (create, start, stop, remove)
- Image creation and management
- **Volume mounting** - Mount project folders directly into containers for real-time file access
- File copying between host and containers (legacy approach)
- Command execution inside containers
- Maven build reproduction and testing
- Log file extraction and storage

## Key Features

### Volume Mounting

The module now supports volume mounting, which allows you to:
- Modify files on the host and immediately see changes in the container
- Avoid copying files for each build iteration
- Faster iteration cycles when replacing files and recompiling
- Direct access to build logs on the host filesystem

### Programmatic API

The module provides a Java API for programmatic usage. See [USAGE.md](USAGE.md) for detailed usage examples.

## Prerequisites

- Docker must be installed and running
- User must have permissions to interact with Docker daemon
- Base Docker images referenced in the code must be available or pullable
- JDK 21+ (for building the module)
- Maven 3.9+ (for building the module)

## Build

```bash
mvn clean package
```

This produces the module JAR at `target/docker-build-1.0.0-SNAPSHOT.jar`.

## Usage

See [USAGE.md](USAGE.md) for comprehensive usage examples and API documentation.

### Quick Start

```java
import se.kth.DockerBuild;
import se.kth.models.FailureCategory;
import java.nio.file.Paths;

// Create instance
DockerBuild dockerBuild = new DockerBuild(false, 1);

// Ensure Docker image exists
String dockerImage = "ghcr.io/chains-project/breaking-updates:base-image";
dockerBuild.ensureBaseMavenImageExists(dockerImage);

// Build with volume mount
Result result = dockerBuild.reproduceWithMount(
    dockerImage,
    FailureCategory.COMPILATION_FAILURE,
    Paths.get("/path/to/project"),
    Paths.get("/path/to/log.log")
);
```

For detailed usage examples, see [USAGE.md](USAGE.md).

## Main Classes

### DockerBuild

The main class providing Docker operations. Key methods include:

- `reproduceWithMount()` - Reproduces a breaking update using volume mounts (recommended)
- `reproduce()` - Reproduces a breaking update using file copying (legacy)
- `createImageForRepositoryAtVersion()` - Creates a Docker image from a Git repository at a specific version
- `copyProjectFromContainer()` - Copies a project from a container to the local filesystem
- `executeInContainer()` - Executes commands inside a running container
- `startSpinningContainer()` - Starts a container that stays alive for multiple operations

### Model Classes

- **Result** - Stores the outcome of reproduction attempts
- **Attempt** - Represents a single reproduction attempt with its result
- **FailureCategory** - Enumeration of different failure types (compilation, test, dependency resolution, etc.)

## Dependencies

- `docker-java-core` - Core Docker Java API
- `docker-java-okhttp` - OkHttp-based HTTP client for Docker API
- `commons-compress` - For handling TAR archives when copying files
- `slf4j-api` and `logback-classic` - Logging
- `picocli` - Command-line interface framework

## Volume Mount vs File Copy

### Volume Mount (Recommended)
- ✅ Faster for iterative builds
- ✅ Real-time file access
- ✅ No need to copy files
- ✅ Direct access to logs on host
- ⚠️ Requires proper file permissions

### File Copy (Legacy)
- ✅ Works in all environments
- ✅ Isolated file system
- ❌ Slower for multiple builds
- ❌ Requires copying files each time

## Troubleshooting

### Permission Issues

If you encounter permission issues with volume mounts, ensure:
- Docker has access to the project directory
- File permissions allow Docker to read/write
- On Linux/Mac, check SELinux/AppArmor settings

### Image Not Found

If the base image is not found:
- The CLI will automatically attempt to pull it
- Ensure you have network access to the Docker registry
- Check that the image name is correct

### Build Failures

- Check the log file output for detailed error messages
- Verify the project is a valid Maven project
- Ensure the Docker image contains Maven and required tools
