package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.List;

/**
 * Processor for detecting Maven Archiver API breaking changes.
 */
public class MavenArchiverProcessor extends AbstractProcessor<CtInvocation<?>> {
    
    private static final List<String> MAVEN_ARCHIVER_CLASSES = Arrays.asList(
        "org.apache.maven.archiver.MavenArchiver",
        "org.apache.maven.archiver.ManifestConfiguration",
        "org.apache.maven.archiver.MavenArchiveConfiguration"
    );
    
    private static final List<String> DEPRECATED_METHODS = Arrays.asList(
        "configureReproducible",
        "parseOutputTimestamp"
    );

    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        // Check if the invocation is on a Maven Archiver class
        CtTypeReference<?> targetType = candidate.getExecutable().getDeclaringType();
        if (targetType == null) {
            return false;
        }
        
        String targetTypeName = targetType.getQualifiedName();
        return MAVEN_ARCHIVER_CLASSES.stream().anyMatch(targetTypeName::equals);
    }

    @Override
    public void process(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        String methodName = executable.getSimpleName();
        
        // Check for deprecated methods
        if (DEPRECATED_METHODS.contains(methodName)) {
            System.out.println("Found deprecated Maven Archiver method call: " + methodName);
            System.out.println("  Location: " + invocation.getPosition().getFile().getPath() 
                             + ":" + invocation.getPosition().getLine());
            
            // Provide guidance based on the method
            switch (methodName) {
                case "configureReproducible":
                    System.out.println("  Suggestion: Use configureReproducibleBuild instead");
                    break;
                case "parseOutputTimestamp":
                    System.out.println("  Suggestion: Use parseBuildOutputTimestamp instead");
                    break;
            }
        }
    }
}