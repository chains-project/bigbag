# Generic Spoon Transformation for Hamcrest API Breaking Change

## Problem
Hamcrest 2.2 introduced a breaking API change where `StringContains` and `StringStartsWith` constructors changed from:
- `new StringContains(boolean, String)` 
- `new StringStartsWith(boolean, String)`

To:
- `new StringContains(String)`
- `new StringStartsWith(String)`

## Solution
This transformation identifies and fixes all occurrences of outdated Hamcrest constructor calls in Java projects.

## Rule Implementation
The transformation:
1. Finds all constructor calls to `org.hamcrest.core.StringContains` with 2 parameters
2. Finds all constructor calls to `org.hamcrest.core.StringStartsWith` with 2 parameters  
3. Removes the first boolean parameter from these calls
4. Preserves the string parameter

## Usage
1. Apply to any Maven project with broken Hamcrest usage
2. Run with: `java -cp spoon-transformer.jar github.chains.Main /path/to/project`

## Files Fixed by This Rule
- `src/test/java/com/artipie/docker/asto/AstoBlobsITCase.java` (lines 87-90, 95)
- `src/test/java/com/artipie/docker/http/DockerSliceITCase.java` (multiple locations)
- `src/test/java/com/artipie/docker/ref/ManifestRefTest.java` (lines 57-58)
- `src/test/java/com/artipie/docker/http/UploadEntityRequestTest.java` (lines 93, 109)
- `src/test/java/com/artipie/docker/http/CachingProxyITCase.java` (line 170)
- `src/test/java/com/artipie/docker/http/UploadEntityPostTest.java` (line 113)
- `src/test/java/com/artipie/docker/TagValidTest.java` (lines 62-63)
- `src/test/java/com/artipie/docker/http/LargeImageITCase.java` (lines 69, 84)
- `src/test/java/com/artipie/docker/http/DockerAuthITCase.java` (lines 70, 85)

## Testing
The transformation successfully resolves compilation errors in the `@docker-adapter/` project.