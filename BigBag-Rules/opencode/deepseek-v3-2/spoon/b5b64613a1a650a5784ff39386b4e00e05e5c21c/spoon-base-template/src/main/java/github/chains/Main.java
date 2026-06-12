package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Add processor for StringUtils.isAllBlank transformation
        launcher.addProcessor(new StringUtilsIsAllBlankProcessor());
        
        // Add processor for ClientHelper constructor transformation
        launcher.addProcessor(new ClientHelperConstructorProcessor());
        
        launcher.run();
        
        System.out.println("Transformation complete. Output written to: " + sourceDir);
    }
    
    /**
     * Processor to replace StringUtils.isAllBlank() calls with equivalent logic
     * for Apache Commons Lang 3.5 compatibility
     */
    static class StringUtilsIsAllBlankProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) return;
            
            String methodName = execRef.getSimpleName();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            // Check if this is a StringUtils.isAllBlank call
            boolean isStringUtilsCall = declaringType != null && 
                "org.apache.commons.lang3.StringUtils".equals(declaringType.getQualifiedName()) &&
                "isAllBlank".equals(methodName);
            
            // Check if this is a static import of isAllBlank
            boolean isStaticImportCall = "isAllBlank".equals(methodName) && 
                invocation.getTarget() == null; // Static method call without target
            
            if (isStringUtilsCall || isStaticImportCall) {
                System.out.println("Found isAllBlank call at: " + invocation.getPosition());
                
                // Get the arguments
                List<?> arguments = invocation.getArguments();
                
                if (arguments.isEmpty()) {
                    // isAllBlank() with no arguments should return true
                    CtLiteral<Boolean> literal = getFactory().createLiteral(true);
                    invocation.replace(literal);
                    System.out.println("Replaced isAllBlank() with true");
                } else {
                    // Create replacement code snippet
                    StringBuilder snippet = new StringBuilder();
                    
                    // Build: (arg1 == null || StringUtils.isBlank(arg1)) && (arg2 == null || StringUtils.isBlank(arg2)) && ...
                    for (int i = 0; i < arguments.size(); i++) {
                        if (i > 0) {
                            snippet.append(" && ");
                        }
                        snippet.append("(");
                        snippet.append("$").append(i + 1); // arg reference
                        snippet.append(" == null || ");
                        snippet.append("org.apache.commons.lang3.StringUtils.isBlank(");
                        snippet.append("$").append(i + 1);
                        snippet.append("))");
                    }
                    
                    // Create code snippet expression
                    CtCodeSnippetExpression<Boolean> replacement = getFactory().createCodeSnippetExpression(snippet.toString());
                    
                    // Set the same type as original
                    replacement.setType(getFactory().Type().booleanPrimitiveType());
                    
                    invocation.replace(replacement);
                    System.out.println("Replaced isAllBlank with: " + snippet.toString());
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null) return false;
            
            String methodName = execRef.getSimpleName();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            boolean isStringUtilsCall = declaringType != null && 
                "org.apache.commons.lang3.StringUtils".equals(declaringType.getQualifiedName()) &&
                "isAllBlank".equals(methodName);
            
            boolean isStaticImportCall = "isAllBlank".equals(methodName) && 
                candidate.getTarget() == null;
                
            return isStringUtilsCall || isStaticImportCall;
        }
    }
    
    /**
     * Processor to handle ClientHelper constructor changes
     * Note: This transformation is more complex and may need project-specific adaptation
     */
    static class ClientHelperConstructorProcessor extends AbstractProcessor<CtNewClass<?>> {
        @Override
        public void process(CtNewClass<?> newClass) {
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef == null) return;
            
            if ("org.jenkinsci.plugins.p4.client.ClientHelper".equals(typeRef.getQualifiedName())) {
                System.out.println("Found ClientHelper constructor at: " + newClass.getPosition());
                List<?> arguments = newClass.getArguments();
                
                // Check if it matches old pattern: ClientHelper(String, TaskListener, String, String)
                if (arguments.size() == 4) {
                    System.out.println("WARNING: Found old ClientHelper constructor with 4 arguments");
                    System.out.println("This constructor signature is no longer available in p4 plugin 1.11.5");
                    System.out.println("Old signature: ClientHelper(String credential, TaskListener listener, String client, String charset)");
                    System.out.println("New signatures:");
                    System.out.println("  - ClientHelper(Item item, String credential, TaskListener listener, Workspace workspace)");
                    System.out.println("  - ClientHelper(ItemGroup itemGroup, String credential, TaskListener listener, Workspace workspace)");
                    System.out.println("  - ClientHelper(P4BaseCredentials credentials, TaskListener listener, Workspace workspace)");
                    System.out.println("This transformation requires manual intervention based on project context.");
                    
                    // We can't automatically transform this without understanding the project context
                    // The transformation would need to:
                    // 1. Determine what Item/ItemGroup to use from context
                    // 2. Create a Workspace from client and charset parameters
                    // 3. Possibly convert credential string to P4BaseCredentials
                    
                    // For now, we'll create a comment and placeholder
                    String comment = "/* TODO: ClientHelper constructor needs update for p4 plugin 1.11.5\n" +
                                    " * Old: new ClientHelper(credential, listener, client, charset)\n" +
                                    " * New constructors available:\n" +
                                    " * - ClientHelper(Item item, String credential, TaskListener listener, Workspace workspace)\n" +
                                    " * - ClientHelper(ItemGroup itemGroup, String credential, TaskListener listener, Workspace workspace)\n" +
                                    " * - ClientHelper(P4BaseCredentials credentials, TaskListener listener, Workspace workspace)\n" +
                                    " * You may need to:\n" +
                                    " * 1. Get Item/ItemGroup from context (e.g., build, job)\n" +
                                    " * 2. Create Workspace object from client and charset\n" +
                                    " * 3. Convert credential string to P4BaseCredentials if needed\n" +
                                    " */\n" +
                                    "// FIXME: Update ClientHelper constructor";
                    
                    CtCodeSnippetExpression<?> commentSnippet = getFactory().createCodeSnippetExpression(
                        "/* TODO: Fix ClientHelper constructor for p4 plugin 1.11.5 */ null"
                    );
                    
                    // Don't automatically replace - just add warning
                    // newClass.replace(commentSnippet);
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtNewClass<?> candidate) {
            CtTypeReference<?> typeRef = candidate.getType();
            return typeRef != null && 
                   "org.jenkinsci.plugins.p4.client.ClientHelper".equals(typeRef.getQualifiedName());
        }
    }
}