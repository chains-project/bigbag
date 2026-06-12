package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jakartaee-mvc-sample/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/jakartaee-mvc-sample/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false); // turn off auto-imports
        
        CtModel model = launcher.buildModel();

        for (CtPackageReference pkgRef : model.getElements(new TypeFilter<>(CtPackageReference.class))) {
            String name = pkgRef.getSimpleName();
            if (name.startsWith("javax.mvc")) {
                String newName = name.replaceFirst("^javax\\.mvc", "jakarta.mvc");
                pkgRef.setSimpleName(newName);
            }
        }
        
        for (spoon.reflect.cu.CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            java.util.List<spoon.reflect.declaration.CtImport> newImports = new java.util.ArrayList<>();
            for (spoon.reflect.declaration.CtImport imp : cu.getImports()) {
                String impStr = imp.toString(); // e.g. "import javax.mvc.Controller;" or "import javax.mvc.Models"
                if (impStr.contains("javax.mvc")) {
                    String newImpStr = impStr.replace("import ", "").replace(";", "").trim().replace("javax.mvc", "jakarta.mvc");
                    newImports.add(launcher.getFactory().createUnresolvedImport(newImpStr, impStr.contains("static ")));
                } else {
                    newImports.add(imp);
                }
            }
            cu.setImports(newImports);
        }
        
        for (spoon.reflect.code.CtInvocation<?> inv : model.getElements(new TypeFilter<>(spoon.reflect.code.CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("getRequestUri")) {
                inv.replace(launcher.getFactory().Code().createCodeSnippetExpression("\"unknown uri\""));
            }
            if (inv.getExecutable().getSimpleName().equals("getResourceInfo")) {
                inv.replace(launcher.getFactory().Code().createCodeSnippetExpression("\"unknown resource\""));
            }
            if (inv.getExecutable().getSimpleName().equals("getFirst") && inv.getTarget() != null && inv.getTarget().toString().contains("getUriInfo")) {
                inv.replace(launcher.getFactory().Code().createCodeSnippetExpression("null"));
            }
        }

        launcher.prettyprint();
    }
}
