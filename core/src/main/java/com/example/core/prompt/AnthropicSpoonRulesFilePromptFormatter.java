package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Formatter that implements the Anthropic-style prompt defined in
 * {@code prompts/prompt_anthropic.txt}.
 *
 * It expects the global placeholder {@code DEPENDENCY_CHANGE_DIFF} to contain
 * a line-by-line description of the dependency's API changes, and embeds it
 * inside a &lt;dependency_change_diff&gt; block.
 */
public class AnthropicSpoonRulesFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Java developer and a specialist in the Spoon code transformation library. ")
          .append("Your task is to generate **Spoon transformation rules** to automatically migrate client code after a dependency upgrade.")
          .append(nl).append(nl);

        sb.append("You will be provided a **diff of the dependency changes**:")
          .append(nl).append(nl);

        sb.append("<dependency_change_diff>").append(nl);
        sb.append(globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "")).append(nl);
        sb.append("</dependency_change_diff>").append(nl).append(nl);

        sb.append("**Important:**").append(nl).append(nl);
        sb.append("* Only generate **Spoon transformation rules**.").append(nl);
        sb.append("* Do **not** include explanations, analysis, or instructions.").append(nl);
        sb.append("* The output must be **ready to copy and execute**.").append(nl).append(nl);

        sb.append("---").append(nl).append(nl);

        sb.append("### **In-Context Examples**").append(nl).append(nl);

        sb.append("<example_1>").append(nl);
        sb.append("Change: Method signature change (with parameter inference)").append(nl);
        sb.append("Old version: `public void processData(String data)`").append(nl);
        sb.append("New version: `public void processData(String data, ProcessingContext context)`").append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("CtInvocation<?> invocation = /* find processData invocation */;").append(nl);
        sb.append("CtExpression<?> contextArgument = null;").append(nl);
        sb.append("String requiredType = \"com.example.ProcessingContext\";").append(nl).append(nl);
        sb.append("CtMethod<?> parentMethod = invocation.getParent(CtMethod.class);").append(nl);
        sb.append("if (parentMethod != null) {").append(nl);
        sb.append("    for (CtLocalVariable<?> var : parentMethod.getElements(new TypeFilter<>(CtLocalVariable.class))) {").append(nl);
        sb.append("        if (var.getType().getQualifiedName().equals(requiredType)) {").append(nl);
        sb.append("            contextArgument = getFactory().Code().createVariableRead(var.getReference(), false);").append(nl);
        sb.append("            break;").append(nl);
        sb.append("        }").append(nl);
        sb.append("    }").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("if (contextArgument == null) {").append(nl);
        sb.append("    CtClass<?> parentClass = invocation.getParent(CtClass.class);").append(nl);
        sb.append("    if (parentClass != null) {").append(nl);
        sb.append("        for (CtField<?> field : parentClass.getFields()) {").append(nl);
        sb.append("            if (field.getType().getQualifiedName().equals(requiredType)) {").append(nl);
        sb.append("                CtThisAccess<?> thisAccess = getFactory().Code().createThisAccess(parentClass.getReference());").append(nl);
        sb.append("                contextArgument = getFactory().Code().createFieldRead().setTarget(thisAccess).setVariable(field.getReference());").append(nl);
        sb.append("                break;").append(nl);
        sb.append("            }").append(nl);
        sb.append("        }").append(nl);
        sb.append("    }").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("if (contextArgument == null) {").append(nl);
        sb.append("    contextArgument = getFactory().Code().createConstructorCall(").append(nl);
        sb.append("        getFactory().Core().createTypeReference().setQualifiedName(requiredType)").append(nl);
        sb.append("    );").append(nl);
        sb.append("}").append(nl).append(nl);
        sb.append("invocation.addArgument(contextArgument);").append(nl);
        sb.append("```").append(nl).append(nl);
        sb.append("</example_1>").append(nl).append(nl);

        sb.append("<example_2>").append(nl);
        sb.append("Change: Removed method").append(nl);
        sb.append("Old version: `public String getData()`").append(nl);
        sb.append("New version: Replaced by `public String retrieveInformation()`").append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("CtInvocation<?> oldInvocation = /* find getData() invocations */;").append(nl);
        sb.append("CtInvocation<?> newInvocation = getFactory().Code().createInvocation(").append(nl);
        sb.append("    oldInvocation.getTarget(),").append(nl);
        sb.append("    getFactory().Core().createExecutableReference().setSimpleName(\"retrieveInformation\")").append(nl);
        sb.append(");").append(nl);
        sb.append("oldInvocation.replace(newInvocation);").append(nl);
        sb.append("```").append(nl).append(nl);
        sb.append("</example_2>").append(nl).append(nl);

        sb.append("<example_3>").append(nl);
        sb.append("Change: Class moved to a new package").append(nl);
        sb.append("Old version: `com.example.old.DataProcessor`").append(nl);
        sb.append("New version: `com.example.new.DataProcessor`").append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("CtImport oldImport = /* find old import */;").append(nl);
        sb.append("CtImport newImport = getFactory().Core().createImport();").append(nl);
        sb.append("newImport.setReference(getFactory().Core().createTypeReference().setQualifiedName(\"com.example.new.DataProcessor\"));").append(nl);
        sb.append("oldImport.replace(newImport);").append(nl);
        sb.append("```").append(nl).append(nl);
        sb.append("</example_3>").append(nl).append(nl);

        sb.append("<example_4>").append(nl);
        sb.append("Change: Method return type changed").append(nl);
        sb.append("Old version: `public List<String> getItems()`").append(nl);
        sb.append("New version: `public Set<String> getItems()`").append(nl);
        sb.append("Spoon Rule:").append(nl).append(nl);

        sb.append("```java").append(nl);
        sb.append("CtInvocation<?> invocation = /* find getItems() invocations */;").append(nl);
        sb.append("if (invocation.getParent(CtLocalVariable.class) != null &&").append(nl);
        sb.append("    invocation.getParent(CtLocalVariable.class).getType().getQualifiedName().equals(\"java.util.List\")) {").append(nl).append(nl);
        sb.append("    CtConstructorCall<?> conversionCall = getFactory().Code().createConstructorCall(").append(nl);
        sb.append("        getFactory().Core().createTypeReference().setQualifiedName(\"java.util.ArrayList\"),").append(nl);
        sb.append("        invocation").append(nl);
        sb.append("    );").append(nl);
        sb.append("    invocation.replace(conversionCall);").append(nl);
        sb.append("}").append(nl);
        sb.append("```").append(nl).append(nl);
        sb.append("</example_4>").append(nl).append(nl);

        sb.append("---").append(nl).append(nl);

        sb.append("### **Instructions to Follow**").append(nl).append(nl);
        sb.append("Analyze the dependency change diff and generate **only Spoon transformation rules** for each change identified. For each transformation:")
          .append(nl).append(nl);
        sb.append("* Generate **ready-to-execute Java Spoon code blocks**").append(nl);
        sb.append("* Apply **contextual inference** for parameters and method replacements when needed").append(nl);
        sb.append("* Update imports and fully-qualified class names when classes are moved or renamed").append(nl);
        sb.append("* Handle return type changes by adapting consuming code appropriately").append(nl);
        sb.append("* Use appropriate Spoon API methods for creating, modifying, and replacing code elements").append(nl).append(nl);

        sb.append("**Output Requirements:**").append(nl);
        sb.append("* **Do not include explanations, commentary, or step-by-step reasoning**").append(nl);
        sb.append("* **Do not include change descriptions or analysis**").append(nl);
        sb.append("* Output must be **clean Java Spoon code blocks only**, one per transformation").append(nl);
        sb.append("* Each code block should be properly formatted and ready to copy and execute").append(nl).append(nl);

        sb.append("Generate the Spoon transformation rules now:").append(nl);

        return sb.toString();
    }
}


