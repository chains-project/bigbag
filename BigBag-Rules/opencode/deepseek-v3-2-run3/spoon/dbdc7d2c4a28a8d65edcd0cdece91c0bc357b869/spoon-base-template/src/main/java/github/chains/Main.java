package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Use SniperJavaPrettyPrinter to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Build the model
        launcher.buildModel();
        
        // Find all method invocations
        launcher.getModel().getRootPackage().getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))
            .forEach(invocation -> {
                // Check if this is a call to enableLogging on AbstractUnArchiver
                if (isEnableLoggingCall(invocation)) {
                    // Remove the statement containing this invocation
                    CtStatement statement = invocation.getParent(CtStatement.class);
                    if (statement != null) {
                        statement.delete();
                        System.out.println("Removed enableLogging call at: " + statement.getPosition());
                    }
                }
            });
        
        // Write transformed code back to a temporary directory first
        // to avoid file copying issues with git files
        String tempOutputDir = sourceDirectory + "-transformed";
        launcher.setSourceOutputDirectory(tempOutputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully! Output written to: " + tempOutputDir);
        System.out.println("Please manually copy files from " + tempOutputDir + " to " + sourceDirectory + " if needed.");
    }
    
    private static boolean isEnableLoggingCall(CtInvocation<?> invocation) {
        // Check if method name is "enableLogging"
        if (!"enableLogging".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        
        // Check if target type is AbstractUnArchiver (or subclass)
        if (invocation.getTarget() != null) {
            String targetType = invocation.getTarget().getType().getQualifiedName();
            if (targetType != null && 
                (targetType.equals("org.codehaus.plexus.archiver.AbstractUnArchiver") ||
                 targetType.startsWith("org.codehaus.plexus.archiver."))) {
                return true;
            }
        }
        
        // Also check by looking at the method's declaring type
        String declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
        if (declaringType != null && 
            (declaringType.equals("org.codehaus.plexus.archiver.AbstractUnArchiver") ||
             declaringType.startsWith("org.codehaus.plexus.archiver."))) {
            return true;
        }
        
        return false;
    }
}