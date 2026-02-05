# Core Module

The core module provides the main CLI application for processing breaking update records.

## Breaking Update Processor CLI

The `breaking-update-processor` CLI processes a `BreakingUpdateRecord` JSON file by:
1. Extracting the Docker image from `breakingUpdateReproductionCommand`
2. Extracting the project and m2 folder from the Docker image to a local directory
3. Optionally building the project using Docker with volume mount
4. Optionally running breaking-classifier on the build log

## Build

```bash
mvn clean package
```

This produces a runnable fat JAR at `target/core-1.0.0-SNAPSHOT-jar-with-dependencies.jar`.

## Usage

### Basic: Extract Project Only

```bash
java -jar target/core-1.0.0-SNAPSHOT-jar-with-dependencies.jar breaking-update.json
```

This will:
- Read the `BreakingUpdateRecord` from the JSON file
- Extract the Docker image from `breakingUpdateReproductionCommand`
- Extract the project and m2 folder to a directory named after `breakingCommit`

### Extract and Build

```bash
java -jar target/core-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --build \
  breaking-update.json
```

### Extract, Build, and Run Classifier

```bash
java -jar target/core-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --build \
  --classify \
  --json-output classifier-report.json \
  breaking-update.json
```

### Full Example with Custom Output Directory

```bash
java -jar target/core-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --output ./extracted-projects \
  --build \
  --classify \
  --json-output ./reports/classifier-report.json \
  --attempts 3 \
  --verbose \
  breaking-update.json
```

## CLI Options

- `JSON_FILE` (required): Path to the JSON file containing `BreakingUpdateRecord`
- `-o, --output OUTPUT_DIR`: Base directory where extracted projects will be saved (default: current directory)
- `-b, --build`: Build the extracted project using Docker with volume mount
- `-c, --classify`: Run breaking-classifier on the build log
- `-j, --json-output CLASSIFIER_JSON`: Path where breaking-classifier JSON output will be saved
- `-a, --attempts ATTEMPTS`: Number of build attempts (default: 1)
- `-v, --verbose`: Enable verbose output
- `-h, --help`: Show help message

## Workflow

1. **Extract Project**: The CLI reads the JSON file and extracts the Docker image from `breakingUpdateReproductionCommand`
2. **Extract from Docker**: Creates a container from the image and extracts:
   - Project folder (from path specified in `project` field or `/project` by default)
   - M2 folder (from `/root/.m2` or alternative locations)
3. **Save to Directory**: Saves everything to `OUTPUT_DIR/breakingCommit/`:
   - `project/` - The extracted Maven project
   - `m2/` - The extracted Maven dependencies
4. **Build (optional)**: If `--build` is specified:
   - Builds the project using Docker with volume mount
   - Saves build log to `OUTPUT_DIR/breakingCommit/build.log`
5. **Classify (optional)**: If `--classify` is specified:
   - Runs breaking-classifier on the build log
   - Prints error classification results
   - Optionally saves JSON report if `--json-output` is specified

## Example JSON File Format

```json
{
  "url": "https://github.com/example/project",
  "project": "project-name",
  "projectOrganisation": "example",
  "breakingCommit": "abc123def456",
  "breakingUpdateReproductionCommand": "docker run ghcr.io/chains-project/breaking-updates:image:tag",
  "failureCategory": "COMPILATION_FAILURE"
}
```

## Output Structure

After running the CLI, you'll have:

```
OUTPUT_DIR/
└── breakingCommit/
    ├── project/
    │   ├── pom.xml
    │   ├── src/
    │   └── ...
    ├── m2/
    │   └── .m2/
    │       └── repository/
    │           └── ...
    └── build.log (if --build was used)
```

## Dependencies

- `docker-build` - For Docker operations and project extraction
- `breaking-classifier` - For error classification from build logs
- `jackson` - For JSON parsing
- `picocli` - For CLI interface

## Breaking Change CLI

Use `BreakingChangeCli` when you want to process a single breaking-change JSON descriptor end-to-end (extract Docker image, locate the build log, run `breaking-classifier`, and persist the outcome to JSON).

```bash
java -cp target/core-1.0.0-SNAPSHOT.jar com.example.core.breakingchange.BreakingChangeCli \
  --input ./breaking-change.json \
  --output-dir ./extracted-projects \
  --result-json ./reports/breaking-change-report.json \
  --force \
  --verbose
```

### Options

- `-i, --input` (required): Path to the BreakingChange JSON descriptor.
- `-o, --output-dir`: Folder where the project (and `.m2`) will be extracted. Defaults to `./extracted-projects`.
- `-r, --result-json`: Optional file where the classifier result will be written.
- `-f, --force`: Forces re-extraction even if the `{breakingCommit}` folder already exists.
- `-v, --verbose`: Enables additional logging.

The result JSON includes the detected failure category, total errors, and a breakdown of errors per file together with metadata such as the Docker image used and the log file location.

