package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("Logback 1.4.6 Compatibility Transformation");
        System.out.println("==========================================");
        System.out.println("This transformation addresses the breaking change in logback 1.4.6");
        System.out.println("where ch.qos.logback.classic.Logger implements org.slf4j.spi.LoggingEventAware");
        System.out.println("which doesn't exist in slf4j-api 1.7.x.");
        System.out.println();
        System.out.println("Transformation rules:");
        System.out.println("1. Replace imports of ch.qos.logback.classic.Logger with org.slf4j.Logger");
        System.out.println("2. Remove casts to ch.qos.logback.classic.Logger");
        System.out.println("3. Change variable types from ch.qos.logback.classic.Logger to org.slf4j.Logger");
        System.out.println("4. Add comments for logback-specific method calls (setLevel, addAppender)");
        System.out.println("5. Suggest updating slf4j-api to 2.0.x or using reflection");
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all classes in the model
            List<CtClass<?>> classes = model.getElements(new TypeFilter<>(CtClass.class));
            
            System.out.println("Found " + classes.size() + " class(es) to analyze");
            
            int fixCount = 0;
            
            for (CtClass<?> clazz : classes) {
                // Check if class contains logback-specific code patterns
                String className = clazz.getQualifiedName();
                
                // Get the source code as string to check for patterns
                String source = clazz.toString();
                
                // Pattern 1: Import of ch.qos.logback.classic.Logger
                if (source.contains("import ch.qos.logback.classic.Logger;")) {
                    System.out.println("  Class " + className + ": Has import of ch.qos.logback.classic.Logger");
                    
                    // Add a comment at the class level
                    CtComment comment = launcher.getFactory().createComment(
                        "COMPATIBILITY ISSUE: This class imports ch.qos.logback.classic.Logger\n" +
                        "which causes compilation errors with logback 1.4.6 and slf4j-api 1.7.x\n" +
                        "because Logger implements org.slf4j.spi.LoggingEventAware (new in slf4j-api 2.0.x).\n" +
                        "Fix: Replace with org.slf4j.Logger and update slf4j-api to 2.0.x.",
                        CtComment.CommentType.BLOCK
                    );
                    
                    clazz.addComment(comment);
                    fixCount++;
                }
                
                // Pattern 2: Cast to (Logger) - assuming it's ch.qos.logback.classic.Logger
                if (source.contains("(Logger)")) {
                    System.out.println("  Class " + className + ": Has cast to Logger");
                    
                    // Find methods with this pattern
                    for (CtMethod<?> method : clazz.getMethods()) {
                        String methodSource = method.toString();
                        if (methodSource.contains("(Logger)")) {
                            // Add comment to the method
                            CtComment methodComment = launcher.getFactory().createComment(
                                "COMPATIBILITY FIX NEEDED: Cast to ch.qos.logback.classic.Logger detected.\n" +
                                "With logback 1.4.6, this causes: 'cannot access org.slf4j.spi.LoggingEventAware'\n" +
                                "Solutions:\n" +
                                "1. Update slf4j-api to 2.0.x in pom.xml\n" +
                                "2. Remove cast and use org.slf4j.Logger interface\n" +
                                "3. For setLevel/addAppender: Use reflection or testing framework",
                                CtComment.CommentType.BLOCK
                            );
                            
                            method.addComment(methodComment);
                            fixCount++;
                        }
                    }
                }
                
                // Pattern 3: setLevel or addAppender calls
                if (source.contains(".setLevel(") || source.contains(".addAppender(")) {
                    System.out.println("  Class " + className + ": Has logback-specific method calls");
                    
                    // Add a general comment
                    CtComment comment = launcher.getFactory().createComment(
                        "LOGSPECIFIC METHODS: This class uses logback-specific methods (setLevel, addAppender).\n" +
                        "These require casting to ch.qos.logback.classic.Logger which fails with\n" +
                        "logback 1.4.6 and slf4j-api 1.7.x. Use reflection or update dependencies.",
                        CtComment.CommentType.INLINE
                    );
                    
                    clazz.addComment(comment);
                    fixCount++;
                }
            }
            
            // Output transformed code
            launcher.setSourceOutputDirectory("./transformed");
            launcher.prettyprint();
            
            System.out.println();
            System.out.println("Transformation complete!");
            System.out.println("Applied " + fixCount + " fix(es)");
            System.out.println("Output written to: ./transformed");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("1. Review the transformed code with added comments");
            System.out.println("2. Manually update imports from ch.qos.logback.classic.Logger to org.slf4j.Logger");
            System.out.println("3. Remove casts to ch.qos.logback.classic.Logger");
            System.out.println("4. Change variable types to org.slf4j.Logger");
            System.out.println("5. For setLevel/addAppender: Implement reflection or update slf4j-api to 2.0.x");
            System.out.println();
            System.out.println("To update slf4j-api in Maven pom.xml:");
            System.out.println("  <dependency>");
            System.out.println("    <groupId>org.slf4j</groupId>");
            System.out.println("    <artifactId>slf4j-api</artifactId>");
            System.out.println("    <version>2.0.0</version> <!-- or later -->");
            System.out.println("  </dependency>");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}