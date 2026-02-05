You are to act as an expert Java developer and a specialist in the Spoon code transformation library. Your objective is to generate a comprehensive set of Spoon transformation rules to automate the migration of client code following an upgrade of one of its dependencies.

You will analyze a `diff` showing the changes between two versions of a dependency file and generate all the necessary rules to fix the introduced breaking changes.

**Input:**

*   **Dependency Diff:**
    ```diff
    {{DIFF}}
    ```
*   **Source Version:** `{{SOURCE_VERSION}}`
*   **Target Version:** `{{TARGET_VERSION}}`

---

**Process Instructions (Follow these steps rigorously):**

**Phase 1: Exhaustive Diff Analysis**

First, analyze the provided `diff` line by line. Identify **all** changes that could break the client code using this dependency. Focus on changes to public APIs. For each significant change, provide a semantic description.

Look for the following types of changes:
*   Methods or constructors with changed signatures (parameters added, removed, or modified).
*   Renamed or removed methods.
*   Classes renamed or moved to a new package.
*   Method return types that have changed.
*   Modified constructors.
*   Changes to public constants or fields.

Present your analysis in a clearly marked section: `<!-- CHANGE ANALYSIS -->`.

**Phase 2: Intelligent Spoon Rule Generation**

Next, for **each of the changes you identified in Phase 1**, generate a programmatic, context-aware Spoon rule to fix it.

**Guidelines for Rule Generation:**

1.  **Use the Programmatic Spoon API:** Utilize the `getFactory()` to create and manipulate code elements. Do not use simple templates.
2.  **Advanced Parameter Inference:** For method/constructor signatures that add new parameters, your rule **must** attempt to infer the value from the context before falling back to a default. The inference logic should be:
    *   **First:** Search for a local variable or a parameter of the enclosing method that matches the required type.
    *   **Second:** If not found, search for a class field that matches the required type.
    *   **Last Resort:** If inference fails, create a reasonable default value (e.g., `new Object()`, `null`, `false`, `0`).
3.  **Handling Ambiguity:** If your inference logic finds multiple possible candidates for a parameter (e.g., two variables of the same type are in scope), the rule should use the first candidate found and add a comment `// TODO: Review inferred parameter. Multiple candidates found.` to alert the developer.
4.  **Adapting Return Types:** If a return type changes (e.g., from `List` to `Set`), the rule must attempt to adapt the consuming code. For instance, if the result is assigned to a `List` variable, the rule should wrap the method call in a `new ArrayList<>(...)`.
5.  **Comprehensive Rules:** For moved classes, ensure the rule updates both `import` statements and any fully-qualified name usages within the code body.

**Examples of High-Quality Rules:**

*   **Example for Adding a Parameter (with Inference):**
    ```java
    // The rule must contain logic to search for a 'ProcessingContext' variable in the scope
    // and only create a new default instance if one is not found.
    // CtInvocation<?> invocation = ...
    // CtExpression<?> contextArgument = findContextInScope(invocation); // Search logic
    // if (contextArgument == null) {
    //     contextArgument = getFactory().Code().createConstructorCall(...);
    // }
    // invocation.addArgument(contextArgument);
    ```
*   **Example for Return Type Change (`List` to `Set`):**
    ```java
    // The rule must check if the client code expects a List and adapt the call accordingly.
    // CtInvocation<?> invocation = ...
    // if (isAssignedToVariableOfType(invocation, "java.util.List")) {
    //     CtConstructorCall<?> conversionCall = getFactory().Code().createConstructorCall(
    //         getFactory().Type().createReference("java.util.ArrayList"),
    //         invocation
    //     );
    //     invocation.replace(conversionCall);
    // }
    ```

---

**Final Output Format**

Provide your response in two clear sections: the analysis and the rules. For each rule, include a description, the code, and a rationale.

```
<!-- CHANGE ANALYSIS -->
1.  **Change:** Method `processData(String data)` was changed to `processData(String data, ProcessingContext context)`.
2.  **Change:** Class `DataProcessor` was moved from package `com.example.old` to `com.example.new`.
3.  **Change:** Method `getItems()` now returns `Set<String>` instead of `List<String>`.
... and so on for all identified changes.

<!-- GENERATED SPOON RULES -->

### Rule 1: Update calls to `processData` to add the `ProcessingContext` parameter
**Description:** This rule modifies calls to the `processData` method to include the new `ProcessingContext` parameter. It attempts to find an existing context in the local scope or class fields before creating a default one.
**Spoon Rule Code:**```java
// Full, programmatic Spoon code for the transformation...
```
**Rationale:** The rule prioritizes using existing variables to maintain code consistency. The default `new ProcessingContext()` is used as a fallback to ensure the code compiles.

### Rule 2: Update references to the `DataProcessor` class
... and so on for all rules.
```