package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <project-source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("================================================");
        System.out.println("Logback Classic 1.4.3 Compatibility Analyzer");
        System.out.println("================================================");
        System.out.println("Scanning source directory: " + sourceDir);
        System.out.println();
        System.out.println("PROBLEM: logback-classic 1.4.3 implements org.slf4j.spi.LoggingEventAware");
        System.out.println("         which only exists in SLF4J 2.x, causing compilation errors");
        System.out.println("         when using SLF4J 1.x.");
        System.out.println();
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            analyzeLogbackUsage(model);
            
            System.out.println("================================================");
            System.out.println("RECOMMENDED FIXES:");
            System.out.println("================================================");
            System.out.println("1. PRIMARY FIX: Update SLF4J dependency to 2.x");
            System.out.println("   - Change slf4j-api version from 1.x to 2.0.x");
            System.out.println("   - This is the proper fix for logback-classic 1.4.3+");
            System.out.println();
            System.out.println("2. ALTERNATIVE: Downgrade logback-classic");
            System.out.println("   - Use logback-classic 1.3.x or earlier");
            System.out.println("   - These versions are compatible with SLF4J 1.x");
            System.out.println();
            System.out.println("3. WORKAROUND: Avoid direct logback Logger usage");
            System.out.println("   - Use org.slf4j.Logger instead of ch.qos.logback.classic.Logger");
            System.out.println("   - Configure logging via logback.xml instead of programmatically");
            System.out.println("   - For tests, use logging configuration files");
            System.out.println("================================================");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void analyzeLogbackUsage(CtModel model) {
        System.out.println("ANALYSIS RESULTS:");
        System.out.println("-----------------");
        
        // Find imports of logback classes
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        int logbackImports = 0;
        
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains("ch.qos.logback.")) {
                logbackImports++;
                System.out.println("Found logback import: " + importStr);
            }
        }
        
        if (logbackImports > 0) {
            System.out.println("Total logback imports found: " + logbackImports);
        } else {
            System.out.println("No direct logback imports found.");
        }
        
        // Find usage of ch.qos.logback.classic.Logger
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        int logbackTypeRefs = 0;
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            if (typeName.equals("ch.qos.logback.classic.Logger")) {
                logbackTypeRefs++;
                CtElement parent = typeRef.getParent();
                if (parent != null) {
                    System.out.println("Found reference to ch.qos.logback.classic.Logger at: " + 
                                     parent.getPosition().toString());
                }
            }
        }
        
        if (logbackTypeRefs > 0) {
            System.out.println("Total references to ch.qos.logback.classic.Logger: " + logbackTypeRefs);
        }
        
        // Find method calls on logback Logger
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        int logbackMethodCalls = 0;
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef != null) {
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (declaringType != null && declaringType.getQualifiedName().equals("ch.qos.logback.classic.Logger")) {
                    logbackMethodCalls++;
                    System.out.println("Found call to logback Logger." + execRef.getSimpleName() + "() at: " +
                                     invocation.getPosition().toString());
                    
                    // Check for problematic methods
                    String methodName = execRef.getSimpleName();
                    if (methodName.equals("setLevel") || methodName.equals("addAppender") || 
                        methodName.equals("detachAppender") || methodName.equals("iteratorForAppenders")) {
                        System.out.println("  WARNING: This is a logback-specific method that may fail!");
                        System.out.println("  Suggestion: Configure logging via logback.xml instead");
                    }
                }
            }
        }
        
        if (logbackMethodCalls > 0) {
            System.out.println("Total logback Logger method calls: " + logbackMethodCalls);
        }
        
        System.out.println("-----------------");
        System.out.println("SUMMARY:");
        System.out.println("  Logback imports: " + logbackImports);
        System.out.println("  Logback Logger references: " + logbackTypeRefs);
        System.out.println("  Logback method calls: " + logbackMethodCalls);
        
        if (logbackImports > 0 || logbackTypeRefs > 0 || logbackMethodCalls > 0) {
            System.out.println("\nWARNING: This project uses logback classes directly.");
            System.out.println("This will cause compilation errors with logback-classic 1.4.3+");
            System.out.println("and SLF4J 1.x due to missing org.slf4j.spi.LoggingEventAware interface.");
        } else {
            System.out.println("\nNo direct logback usage found.");
            System.out.println("Compilation issues may still occur if dependencies");
            System.out.println("transitively use logback-classic 1.4.3+ with SLF4J 1.x.");
        }
    }
}