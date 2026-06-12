package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class JacksonDependencyFixer {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.JacksonDependencyFixer <project-root>");
            System.err.println("This tool identifies and helps fix Jackson dependency version mismatches.");
            System.exit(1);
        }
        
        String projectRoot = args[0];
        System.out.println("Analyzing project at: " + projectRoot);
        
        // Look for pom.xml files
        String pomPath = projectRoot + "/pom.xml";
        
        // In a real implementation, we would parse and modify the POM
        // For this example, we'll provide guidance
        
        System.out.println("\n=== Jackson Dependency Analysis ===");
        System.out.println("Problem: Jackson 2.13+ introduced StreamReadException which replaces JsonParseException.");
        System.out.println("This causes compilation errors when jackson-core and jackson-databind versions are mismatched.");
        System.out.println("\nRecommended fix:");
        System.out.println("1. Update all Jackson dependencies to compatible versions (e.g., 2.13.4)");
        System.out.println("2. Replace JsonParseException with StreamReadException in catch blocks");
        System.out.println("3. Ensure methods declare appropriate exceptions (IOException covers StreamReadException)");
        
        // Analyze Java source files for potential issues
        analyzeJavaSources(projectRoot);
    }
    
    private static void analyzeJavaSources(String projectRoot) {
        try {
            SpoonAPI spoon = new Launcher();
            spoon.addInputResource(projectRoot + "/src/main/java");
            spoon.getEnvironment().setNoClasspath(true);
            spoon.getEnvironment().setAutoImports(true);
            
            CtModel model = spoon.buildModel();
            
            // Find ObjectMapper.readValue calls
            List<CtInvocation<?>> readValueCalls = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    CtTypeReference<?> targetType = invocation.getExecutable().getDeclaringType();
                    return targetType != null && 
                           targetType.getQualifiedName().equals("com.fasterxml.jackson.databind.ObjectMapper") &&
                           invocation.getExecutable().getSimpleName().equals("readValue");
                }
            });
            
            if (!readValueCalls.isEmpty()) {
                System.out.println("\n=== Found " + readValueCalls.size() + " ObjectMapper.readValue() calls ===");
                
                for (CtInvocation<?> call : readValueCalls) {
                    CtMethod<?> method = call.getParent(CtMethod.class);
                    if (method != null) {
                        System.out.println("  - " + method.getSignature() + " (line " + call.getPosition().getLine() + ")");
                        
                        // Check if method declares IOException
                        boolean hasIOException = false;
                        for (CtTypeReference<?> ex : method.getThrownTypes()) {
                            if (ex.getQualifiedName().equals("java.io.IOException")) {
                                hasIOException = true;
                                break;
                            }
                        }
                        
                        if (!hasIOException) {
                            System.out.println("    WARNING: Method does not declare IOException");
                            System.out.println("    Suggestion: Add 'throws IOException' to method signature");
                        }
                    }
                }
            }
            
            // Look for JsonParseException references
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    String name = typeRef.getQualifiedName();
                    return name != null && name.contains("JsonParseException");
                }
            });
            
            if (!typeRefs.isEmpty()) {
                System.out.println("\n=== Found " + typeRefs.size() + " JsonParseException references ===");
                System.out.println("In Jackson 2.13+, JsonParseException was replaced by StreamReadException");
                System.out.println("Consider updating to StreamReadException");
            }
            
        } catch (Exception e) {
            System.out.println("Could not analyze Java sources: " + e.getMessage());
        }
    }
}