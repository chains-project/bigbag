package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

public class JsonBranchTransformer {
    public static void main(String[] args) {
        // Process main sources
        process("/workspace/qa-catalogue/src/main/java");
        // Process test sources
        process("/workspace/qa-catalogue/src/test/java");
    }

    private static void process(String inputDir) {
        System.out.println("Processing: " + inputDir);
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        
        // Environment settings
        launcher.getEnvironment().setAutoImports(false); // Disable auto imports to avoid messing up existing ones
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Output directory - overwrite input
        launcher.setSourceOutputDirectory(inputDir);

        CtModel model = launcher.buildModel();

        for (CtType<?> type : model.getAllTypes()) {
            type.accept(new CtScanner() {
                @Override
                public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                    // Rename usages in code
                    if ("JsonBranch".equals(reference.getSimpleName())) {
                        reference.setSimpleName("DataElement");
                    }
                    super.visitCtTypeReference(reference);
                }

                @Override
                public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                    // Rename method calls
                    if ("getJsonPath".equals(invocation.getExecutable().getSimpleName())) {
                        invocation.getExecutable().setSimpleName("getPath");
                    }
                    super.visitCtInvocation(invocation);
                }
            });
        }
        
        // Update imports
        model.getUnnamedModule().getFactory().CompilationUnit().getMap().values().forEach(cu -> {
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> ref = (CtTypeReference<?>) imp.getReference();
                    if ("de.gwdg.metadataqa.api.json.JsonBranch".equals(ref.getQualifiedName())) {
                        ref.setSimpleName("DataElement");
                    }
                }
            }
        });

        launcher.prettyprint();
        System.out.println("Finished processing: " + inputDir);
    }
}