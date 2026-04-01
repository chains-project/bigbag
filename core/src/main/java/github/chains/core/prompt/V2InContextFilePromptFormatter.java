package github.chains.core.prompt;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.ClassificationSummary;
import github.chains.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Formatter that implements the V2 in-context prompt defined in
 * {@code prompts/prompt_v2_in_context.txt}.
 *
 * This prompt generates a complete, executable Java class named MigrationTransformation
 * that performs the refactoring using Spoon transformations.
 */
public class V2InContextFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "v2_in_context";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String apiDiff = globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "");
        
        return """
            # System Prompt
            
            You are an expert Java Code Transformation Engine specializing in the **Spoon library** (http://spoon.gforge.inria.fr/). Your goal is to translate API dependency diffs into a standalone, executable Java class that performs the refactoring.
            
            You will be provided with a text description or diff of an API change.
            
            ### Generation Rules
            
            1.  **Output Structure**: The output must be a **single, valid Java class** named `MigrationTransformation`.
            2.  **Main Method**: The class must contain a `public static void main(String[] args)` method.
            3.  **Spoon Boilerplate**: Inside `main`, you must:
                *   Initialize `spoon.Launcher`.
                *   Add input resources (assume `"src/main/java"`).
                *   Build the model (`launcher.buildModel()`).
                *   Obtain the `Factory` and `CtModel`.
            4.  **Transformation Logic**: Implement the rules using standard Spoon patterns (e.g., `TypeFilter`, `getFactory().Code()`, `.replace()`).
            5.  **Formatting**:
                *   **Raw Code Only**: Do NOT use Markdown backticks. Start directly with `public class...`.
                *   **Indentation**: Strictly use **4 spaces** for indentation.
                *   **Final Step**: Always end the main method with `launcher.prettyprint();` to output the modified code.
            
            ---
            
            ### In-Context Examples (Few-Shot Learning)
            
            <example_1>
            **Diff**: Method Renamed
            *Old*: `server.start()`
            *New*: `server.boot()`
            **Spoon Rule**:
            public class MigrationTransformation {
                public static void main(String[] args) {
                    spoon.Launcher launcher = new spoon.Launcher();
                    launcher.addInputResource("src/main/java");
                    launcher.buildModel();
            
                    spoon.reflect.CtModel model = launcher.getModel();
            
                    // Transformation: Rename 'start' to 'boot' on Server objects
                    for (spoon.reflect.code.CtInvocation<?> invocation : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(spoon.reflect.code.CtInvocation.class))) {
                        spoon.reflect.reference.CtExecutableReference<?> execRef = invocation.getExecutable();
                        if (execRef.getSimpleName().equals("start") && 
                            execRef.getDeclaringType() != null && 
                            execRef.getDeclaringType().getSimpleName().equals("Server")) {
                            
                            execRef.setSimpleName("boot");
                        }
                    }
                    launcher.prettyprint();
                }
            }
            </example_1>
            
            <example_2>
            **Diff**: Argument Added
            *Old*: `logger.log("message")`
            *New*: `logger.log("message", LogLevel.INFO)`
            **Spoon Rule**:
            public class MigrationTransformation {
                public static void main(String[] args) {
                    spoon.Launcher launcher = new spoon.Launcher();
                    launcher.addInputResource("src/main/java");
                    launcher.buildModel();
                    
                    spoon.reflect.factory.Factory factory = launcher.getFactory();
                    spoon.reflect.CtModel model = launcher.getModel();
            
                    // Transformation: Add default LogLevel.INFO to log() calls
                    for (spoon.reflect.code.CtInvocation<?> invocation : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(spoon.reflect.code.CtInvocation.class))) {
                        if (invocation.getExecutable().getSimpleName().equals("log") && invocation.getArguments().size() == 1) {
                            
                            spoon.reflect.reference.CtTypeReference<?> enumType = factory.Type().createReference("com.logging.LogLevel");
                            spoon.reflect.code.CtFieldRead<?> infoEnum = factory.Code().createFieldRead();
                            infoEnum.setTarget(factory.Code().createTypeAccess(enumType));
                            infoEnum.setVariable(factory.Field().createReference(enumType, enumType, "INFO"));
                            
                            invocation.addArgument(infoEnum);
                        }
                    }
                    launcher.prettyprint();
                }
            }
            </example_2>
            
            <example_3>
            **Diff**: Package Moved
            *Old*: `org.legacy.util.StringHelper`
            *New*: `com.modern.text.StringHelper`
            **Spoon Rule**:
            public class MigrationTransformation {
                public static void main(String[] args) {
                    spoon.Launcher launcher = new spoon.Launcher();
                    launcher.addInputResource("src/main/java");
                    launcher.buildModel();
                    
                    spoon.reflect.factory.Factory factory = launcher.getFactory();
                    spoon.reflect.CtModel model = launcher.getModel();
            
                    // Transformation: Update package reference for StringHelper
                    for (spoon.reflect.reference.CtTypeReference<?> ref : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(spoon.reflect.reference.CtTypeReference.class))) {
                        if (ref.getQualifiedName().equals("org.legacy.util.StringHelper")) {
                            ref.setPackage(factory.Package().getOrCreate("com.modern.text"));
                        }
                    }
                    launcher.prettyprint();
                }
            }
            </example_3>
            
            <example_4>
            **Diff**: Static Utility to Instance Method
            *Old*: `StringUtils.isEmpty(str)`
            *New*: `str.isEmpty()`
            **Spoon Rule**:
            public class MigrationTransformation {
                public static void main(String[] args) {
                    spoon.Launcher launcher = new spoon.Launcher();
                    launcher.addInputResource("src/main/java");
                    launcher.buildModel();
                    
                    spoon.reflect.factory.Factory factory = launcher.getFactory();
                    spoon.reflect.CtModel model = launcher.getModel();
            
                    // Transformation: Convert static StringUtils.isEmpty(x) to x.isEmpty()
                    for (spoon.reflect.code.CtInvocation<?> invocation : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(spoon.reflect.code.CtInvocation.class))) {
                        spoon.reflect.reference.CtExecutableReference<?> exec = invocation.getExecutable();
                        
                        if (exec.getDeclaringType().getSimpleName().equals("StringUtils") && exec.getSimpleName().equals("isEmpty")) {
                            // Get the argument 'str'
                            spoon.reflect.code.CtExpression<?> targetArgument = invocation.getArguments().get(0);
                            
                            // Create new invocation: targetArgument.isEmpty()
                            spoon.reflect.code.CtInvocation<?> newInvocation = factory.Code().createInvocation(
                                targetArgument, 
                                factory.Executable().createReference("isEmpty")
                            );
                            
                            invocation.replace(newInvocation);
                        }
                    }
                    launcher.prettyprint();
                }
            }
            </example_4>
            
            <example_5>
            **Diff**: Exception Type Change
            *Old*: Throws `com.auth.AuthException`
            *New*: Throws `java.security.SecurityException`
            **Spoon Rule**:
            public class MigrationTransformation {
                public static void main(String[] args) {
                    spoon.Launcher launcher = new spoon.Launcher();
                    launcher.addInputResource("src/main/java");
                    launcher.buildModel();
                    
                    spoon.reflect.factory.Factory factory = launcher.getFactory();
                    spoon.reflect.CtModel model = launcher.getModel();
            
                    // Transformation: Replace AuthException with SecurityException in Catch blocks
                    for (spoon.reflect.code.CtCatch catchBlock : model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(spoon.reflect.code.CtCatch.class))) {
                        spoon.reflect.code.CtCatchVariable<?> variable = catchBlock.getParameter();
                        if (variable.getType().getQualifiedName().equals("com.auth.AuthException")) {
                            variable.setType(factory.Type().createReference("java.security.SecurityException"));
                        }
                    }
                    launcher.prettyprint();
                }
            }
            </example_5>
            
            ---
            
            Generate the `MigrationTransformation` class for the following API Diff:
            
            <dependency_change_diff>
            %s
            </dependency_change_diff>
            """.formatted(apiDiff);
    }
}
