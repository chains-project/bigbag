package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        int importCount = 0;
        int typeRefCount = 0;
        int methodCallCount = 0;
        
        // Transform imports: JsonBranch -> DataElement
        for (CtImport importDecl : model.getElements(new TypeFilter<>(CtImport.class))) {
            try {
                String importStr = importDecl.toString();
                if (importStr.contains("import de.gwdg.metadataqa.api.json.JsonBranch")) {
                    CtTypeReference<?> dataElementRef = launcher.getFactory().createReference("de.gwdg.metadataqa.api.json.DataElement");
                    importDecl.replace(launcher.getFactory().createImport(dataElementRef));
                    importCount++;
                }
            } catch (Exception e) {
                System.err.println("Error transforming import: " + e.getMessage());
            }
        }
        
        // Transform type references: JsonBranch -> DataElement
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            try {
                String typeName = typeRef.getQualifiedName();
                if (typeName != null && typeName.equals("de.gwdg.metadataqa.api.json.JsonBranch")) {
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference("de.gwdg.metadataqa.api.json.DataElement");
                    typeRef.replace(newTypeRef);
                    typeRefCount++;
                }
            } catch (Exception e) {
                System.err.println("Error transforming type reference: " + e.getMessage());
            }
        }
        
        // Transform method calls: getJsonPath() -> getPath()
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            try {
                if (invocation.getExecutable() != null && 
                    invocation.getExecutable().getSimpleName().equals("getJsonPath")) {
                    invocation.getExecutable().setSimpleName("getPath");
                    methodCallCount++;
                }
            } catch (Exception e) {
                System.err.println("Error transforming method call: " + e.getMessage());
            }
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory("./transformed-output-v2");
        launcher.prettyprint();
        
        System.out.println("Transformation summary:");
        System.out.println("  Imports transformed: " + importCount);
        System.out.println("  Type references transformed: " + typeRefCount);
        System.out.println("  Method calls transformed: " + methodCallCount);
        System.out.println("Transformation completed. Output written to ./transformed-output-v2");
    }
}