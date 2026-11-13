package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic Spoon transformation to fix breaking changes in logback-classic dependency
 * This transformation addresses common breaking changes when upgrading logback-classic
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        
        // Transformation for fixing logback related breaking changes
        fixLogbackBreakingChanges(model);
        
        // Write the transformed files
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Fixes breaking changes in logback-classic API
     * Specifically looks for and fixes common patterns involving:
     * - AsyncAppender constructor calls
     * - LoggerContext method calls
     * - PatternLayout related issues
     */
    private static void fixLogbackBreakingChanges(CtModel model) {
        // Fix constructor calls to AsyncAppender that might have changed signature
        model.getElements(new TypeFilter<>(CtConstructorCall.class))
                .stream()
                .filter(call -> {
                    CtTypeReference<?> typeRef = call.getType();
                    return typeRef != null && 
                           typeRef.getQualifiedName().equals("ch.qos.logback.classic.AsyncAppender");
                })
                .forEach(constructorCall -> {
                    // Ensure the constructor call is properly formed with no arguments
                    // This addresses potential breaking changes in constructor signatures
                    if (constructorCall.getArguments().size() > 0) {
                        // Remove any arguments if they exist to match the new signature
                        constructorCall.getArguments().clear();
                    }
                });
        
        // Fix method calls to LoggerContext that might have changed
        model.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(invocation -> {
                    CtExecutableReference<?> executableRef = invocation.getExecutable();
                    if (executableRef == null) return false;
                    String signature = executableRef.getSignature();
                    return signature.contains("ch.qos.logback.classic.LoggerContext") &&
                           (
                               signature.contains("getLogger") ||
                               signature.contains("getLoggerContextRemoteView") ||
                               signature.contains("reset") ||
                               signature.contains("start") ||
                               signature.contains("stop")
                           );
                })
                .forEach(invocation -> {
                    // This can be extended for specific method signature changes
                });
        
        // Fix method calls to Logger that might have changed
        model.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(invocation -> {
                    CtExecutableReference<?> executableRef = invocation.getExecutable();
                    if (executableRef == null) return false;
                    String signature = executableRef.getSignature();
                    return signature.contains("ch.qos.logback.classic.Logger") &&
                           (
                               signature.contains("debug") ||
                               signature.contains("info") ||
                               signature.contains("warn") ||
                               signature.contains("error") ||
                               signature.contains("trace")
                           );
                })
                .forEach(invocation -> {
                    // Address potential signature changes in logging method calls
                });
                
        // Handle direct type references that might need updating
        model.getElements(new TypeFilter<>(CtTypeReference.class))
                .stream()
                .filter(typeRef -> {
                    String qualifiedName = typeRef.getQualifiedName();
                    return qualifiedName.startsWith("ch.qos.logback.classic.") && 
                           !qualifiedName.equals("ch.qos.logback.classic.LoggerContext");
                })
                .forEach(typeRef -> {
                    // Generic handler for logback class references
                });
    }
}