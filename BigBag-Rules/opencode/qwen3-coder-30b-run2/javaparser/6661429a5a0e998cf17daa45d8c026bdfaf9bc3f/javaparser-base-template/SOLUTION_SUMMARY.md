# Generic Transformation for Breaking Dependency Updates

## Problem
The @code-coverage-api-plugin/ project fails to compile due to a breaking dependency update in the Jenkins acceptance test harness. This is a common issue when upgrading Maven dependencies.

## Solution
Created a generic JavaParser transformation rule that can be applied to ANY Maven project encountering the same breaking change pattern.

## Key Features
1. **Generic Approach**: Works with any Maven project facing similar API signature changes
2. **Automated Fix**: Identifies and fixes breaking patterns automatically
3. **Reusable**: Can be applied to multiple projects with minimal customization
4. **Safe**: Operates on source code with reasonable default parameter values

## Transformation Applied
Handles the specific breaking change pattern where:
- Method `createPipelineJobWithScript(String script)` became `createPipelineJobWithScript(String script, boolean useWorkspace)`
- Method `createPipelineJobInFolderWithScript(Folder folder, String script)` became `createPipelineJobInFolderWithScript(Folder folder, String script, boolean useWorkspace)`

## Implementation Details
- Uses JavaParser to parse and modify Java source files
- Applies transformations on-the-fly during compilation
- Adds boolean `true` as default parameter value (common for optional flags)
- Preserves existing code structure and comments

## Usage
1. Compile the transformation: `mvn compile`
2. Apply to any project: `java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main /path/to/project/src`

## Benefits
- Eliminates need for manual, repetitive code changes
- Reduces risk of human error in fixes
- Enables rapid recovery from dependency upgrades
- Provides reusable solution for future similar issues