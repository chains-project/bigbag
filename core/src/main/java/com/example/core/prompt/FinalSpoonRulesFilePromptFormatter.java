package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Formatter that implements the "final" Spoon rules prompt defined in
 * {@code prompts/prompt_final.txt}.
 *
 * It expects the global placeholder {@code DEPENDENCY_CHANGE_DIFF} to contain
 * a diff of the relevant dependency changes, already filtered to the API
 * changes connected to constructs on the failing lines of this file.
 */
public class FinalSpoonRulesFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "final";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("You are to act as an expert Java developer and a specialist in the Spoon code transformation library. ")
          .append("Your objective is to generate a comprehensive set of Spoon transformation rules to automate the migration of client code following an upgrade of one of its dependencies.")
          .append(nl).append(nl);

        sb.append("You will analyze the diff provided below, which shows the changes between two versions of a dependency, and generate all the necessary Spoon rules to adapt the existing code.")
          .append(nl).append(nl);

        sb.append("The rules must be context-aware, attempting to infer required values from existing code before falling back to default values.")
          .append(nl).append(nl);

        sb.append("Here is the diff showing the changes in the dependency:").append(nl).append(nl);

        sb.append("<dependency_change_diff>").append(nl);
        sb.append(globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "")).append(nl);
        sb.append("</dependency_change_diff>").append(nl).append(nl);

        sb.append("Here are examples of different types of changes and their corresponding enhanced Spoon rules:")
          .append(nl).append(nl);

        // Example 1
        sb.append("<example_1>").append(nl);
        sb.append("**Change**: Method signature changed (with parameter inference).").append(nl).append(nl);
        sb.append("**Old version**: `public void processData(String data)`").append(nl).append(nl);
        sb.append("**New version**: `public void processData(String data, ProcessingContext context)`").append(nl).append(nl);
        sb.append("**Spoon Rule (with full inference logic)**:").append(nl);
        sb.append("This rule demonstrates a hierarchical search for the new `context` parameter.").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("// Rule to add a missing parameter by inferring it from the context.").append(nl);
        sb.append("CtInvocation<?> invocation = /* find the processData invocation */;").append(nl);
        sb.append("CtExpression<?> contextArgument = null;").append(nl);
        sb.append("String requiredType = \"com.example.ProcessingContext\";").append(nl).append(nl);
        sb.append("// 1. Attempt to find a local variable of the required type in the current method's scope.").append(nl);
        sb.append("CtMethod<?> parentMethod = invocation.getParent(CtMethod.class);").append(nl);
        sb.append("if (parentMethod != null) {").append(nl);
        sb.append("    for (CtLocalVariable<?> var : parentMethod.getElements(new TypeFilter<>(CtLocalVariable.class))) {").append(nl);
        sb.append("        if (var.getType().getQualifiedName().equals(requiredType)) {").append(nl);
        sb.append("            contextArgument = getFactory().Code().createVariableRead(var.getReference(), false);").append(nl);
        sb.append("            break; // Use the first local variable found.").append(nl);
        sb.append("        }").append(nl);
        sb.append("    }").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("// 2. If not found, look for a field in the enclosing class.").append(nl);
        sb.append("if (contextArgument == null) {").append(nl);
        sb.append("    CtClass<?> parentClass = invocation.getParent(CtClass.class);").append(nl);
        sb.append("    if (parentClass != null) {").append(nl);
        sb.append("        for (CtField<?> field : parentClass.getFields()) {").append(nl);
        sb.append("            if (field.getType().getQualifiedName().equals(requiredType)) {").append(nl);
        sb.append("                // Creates a 'this.fieldName' expression.").append(nl);
        sb.append("                CtThisAccess<?> thisAccess = getFactory().Code().createThisAccess(parentClass.getReference());").append(nl);
        sb.append("                contextArgument = getFactory().Code().createFieldRead().setTarget(thisAccess).setVariable(field.getReference());").append(nl);
        sb.append("                break; // Use the first class field found.").append(nl);
        sb.append("            }").append(nl);
        sb.append("        }").append(nl);
        sb.append("    }").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("// 3. As a last resort, create a default value.").append(nl);
        sb.append("if (contextArgument == null) {").append(nl);
        sb.append("    contextArgument = getFactory().Code().createConstructorCall(").append(nl);
        sb.append("        getFactory().Core().createTypeReference().setQualifiedName(requiredType)").append(nl);
        sb.append("    );").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("// Add the inferred or default argument to the method invocation.").append(nl);
        sb.append("invocation.addArgument(contextArgument);").append(nl);
        sb.append("```").append(nl).append(nl);
        sb.append("</example_1>").append(nl).append(nl);

        // Example 2
        sb.append("<example_2>").append(nl);
        sb.append("Change: Method removed.").append(nl).append(nl);
        sb.append("Old version: public String getData()").append(nl).append(nl);
        sb.append("New version: Method removed, replaced by public String retrieveInformation().").append(nl).append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("// Rule to replace a removed method with its new equivalent.").append(nl);
        sb.append("CtInvocation<?> oldInvocation = /* find getData() invocations */;").append(nl);
        sb.append("CtInvocation<?> newInvocation = getFactory().Code().createInvocation(").append(nl);
        sb.append("    oldInvocation.getTarget(),").append(nl);
        sb.append("    getFactory().Core().createExecutableReference().setSimpleName(\"retrieveInformation\")").append(nl);
        sb.append(");").append(nl);
        sb.append("oldInvocation.replace(newInvocation);").append(nl);
        sb.append("```").append(nl);
        sb.append("</example_2>").append(nl).append(nl);

        // Example 3
        sb.append("<example_3>").append(nl);
        sb.append("Change: Class moved to a different package.").append(nl).append(nl);
        sb.append("Old version: com.example.old.DataProcessor").append(nl).append(nl);
        sb.append("New version: com.example.new.DataProcessor").append(nl).append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("// Rule to update import statements and fully qualified names.").append(nl);
        sb.append("// Update imports.").append(nl);
        sb.append("CtImport oldImport = /* find the old import */;").append(nl);
        sb.append("CtImport newImport = getFactory().Core().createImport();").append(nl);
        sb.append("newImport.setReference(getFactory().Core().createTypeReference().setQualifiedName(\"com.example.new.DataProcessor\"));").append(nl);
        sb.append("oldImport.replace(newImport);").append(nl).append(nl);
        sb.append("// It's also necessary to update fully qualified usages in the code body.").append(nl);
        sb.append("```java").append(nl);
        sb.append("</example_3>").append(nl).append(nl);

        // Example 4
        sb.append("<example_4>").append(nl);
        sb.append("Change: Method return type changed.").append(nl).append(nl);
        sb.append("Old version: public List<String> getItems()").append(nl).append(nl);
        sb.append("New version: public Set<String> getItems()").append(nl).append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("// Rule to handle the return type change by wrapping the call to convert the result.").append(nl);
        sb.append("CtInvocation<?> invocation = /* find getItems() invocations */;").append(nl).append(nl);
        sb.append("// Check if the invocation is assigned to a variable of type List.").append(nl);
        sb.append("if (invocation.getParent(CtLocalVariable.class) != null &&").append(nl);
        sb.append("    invocation.getParent(CtLocalVariable.class).getType().getQualifiedName().equals(\"java.util.List\")) {").append(nl).append(nl);
        sb.append("    // Wrap the invocation with a conversion to a new ArrayList.").append(nl);
        sb.append("    CtConstructorCall<?> conversionCall = getFactory().Code().createConstructorCall(").append(nl);
        sb.append("        getFactory().Core().createTypeReference().setQualifiedName(\"java.util.ArrayList\"),").append(nl);
        sb.append("        invocation").append(nl);
        sb.append("    );").append(nl);
        sb.append("    invocation.replace(conversionCall);").append(nl);
        sb.append("}").append(nl);
        sb.append("```java").append(nl);
        sb.append("</example_4>").append(nl).append(nl);

        // Instructions
        sb.append("Instructions for Generating Spoon Rules:").append(nl).append(nl);
        sb.append("Analyze each change from the provided diff to determine its type (signature change, method removal, class movement, etc.).").append(nl).append(nl);
        sb.append("For signature changes (methods/constructors): Prioritize parameter inference. Generate rules that actively search the code's scope (local variables, class fields, enclosing method parameters) for an existing variable of the required type, as shown in Example 1. Only if inference fails should you fall back to creating a default value.").append(nl).append(nl);
        sb.append("For removed methods: If a replacement method exists, create a rule to replace the calls. If no direct replacement exists, attempt to construct equivalent functionality using other available methods.").append(nl).append(nl);
        sb.append("For moved/renamed classes: Generate rules to update both import statements and any fully-qualified class name usages in the code body.").append(nl).append(nl);
        sb.append("For return type changes: Generate rules that adapt the code consuming the value. This may involve wrapping the call in a conversion method (e.g., new ArrayList<>(originalCall)) or updating the type of the variable to which the result is assigned.").append(nl).append(nl);
        sb.append("Handling Ambiguity: If, during inference, you find multiple possible candidates for a parameter (e.g., two variables of the same type in scope), mention this in your analysis. The rule can choose to use the first candidate found and optionally add a //TODO: Review inferred parameter comment for a developer to verify.").append(nl).append(nl);
        sb.append("First, provide your detailed analysis of each change and explain your transformation strategy, with an emphasis on contextual inference logic. Then, provide the complete Spoon transformation rules.").append(nl).append(nl);

        sb.append("<analysis>").append(nl);
        sb.append("[Analyze each change from the diff here and explain your transformation strategy, with an emphasis on contextual inference logic.]").append(nl);
        sb.append("</analysis>").append(nl).append(nl);

        sb.append("<spoon_rules>").append(nl);
        sb.append("[Provide the complete Spoon transformation rules here.]").append(nl);
        sb.append("</spoon_rules>").append(nl);

        return sb.toString();
    }
}


