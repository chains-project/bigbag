package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_WRAPPER_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GETTER_NAME = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-spoon-out");

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);

        launcher.buildModel();
        int removedImports = removeOldWrapperImports(launcher);
        int rewrittenGetters = rewriteGetterUsages(launcher);
        int rewrittenDeclarations = rewriteDeclarations(launcher);
        System.out.println("imports=" + removedImports + ", getters=" + rewrittenGetters + ", decls=" + rewrittenDeclarations);
        launcher.prettyprint();
        cleanupGeneratedImports(outputDir);
    }

    private static void cleanupGeneratedImports(Path outputDir) {
        try {
            Files.walk(outputDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                            String updated = content.replace("import com.gargoylesoftware.htmlunit.ScriptResult;\n", "")
                                    .replace("import com.gargoylesoftware.htmlunit.ScriptResult;\r\n", "");
                            if (!content.equals(updated)) {
                                Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static int removeOldWrapperImports(Launcher launcher) {
        int changed = 0;
        List<CtImport> imports = launcher.getFactory().getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() != null && "ScriptResult".equals(ctImport.getReference().getSimpleName())) {
                ctImport.delete();
                changed++;
            }
        }
        for (CtType<?> type : launcher.getFactory().getModel().getAllTypes()) {
            SourcePosition position = type.getPosition();
            if (position == null || position.getCompilationUnit() == null) {
                continue;
            }
            CtCompilationUnit compilationUnit = position.getCompilationUnit();
            List<CtImport> filteredImports = new java.util.ArrayList<>();
            for (CtImport ctImport : compilationUnit.getImports()) {
                if (ctImport.getReference() == null || !"ScriptResult".equals(ctImport.getReference().getSimpleName())) {
                    filteredImports.add(ctImport);
                } else {
                    changed++;
                }
            }
            compilationUnit.setImports(filteredImports);
        }
        return changed;
    }

    private static int rewriteGetterUsages(Launcher launcher) {
        int changed = 0;
        List<CtInvocation<?>> invocations = launcher.getFactory().getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!GETTER_NAME.equals(invocation.getExecutable().getSimpleName()) || invocation.getTarget() == null) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (isOldWrapperConstructor(target)) {
                List<CtExpression<?>> arguments = ((CtConstructorCall<?>) target).getArguments();
                if (!arguments.isEmpty()) {
                    invocation.replace((CtExpression) arguments.get(0).clone());
                    changed++;
                }
                continue;
            }

            if (isOldWrapperReference(target) || isOldWrapperType(target.getType())) {
                invocation.replace((CtExpression) target.clone());
                changed++;
            }
        }
        return changed;
    }

    private static int rewriteDeclarations(Launcher launcher) {
        int changed = 0;
        List<CtLocalVariable<?>> locals = launcher.getFactory().getModel().getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            if (rewriteLocalVariable(local)) {
                changed++;
            }
        }

        List<CtField<?>> fields = launcher.getFactory().getModel().getElements(new TypeFilter<>(CtField.class));
        for (CtField<?> field : fields) {
            if (rewriteField(field)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean rewriteLocalVariable(CtLocalVariable<?> local) {
        CtExpression<?> defaultExpression = local.getDefaultExpression();
        if (!isOldWrapperType(local.getType()) || !(defaultExpression instanceof CtConstructorCall)) {
            return false;
        }

        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
        if (!isOldWrapperType(constructorCall.getType()) || constructorCall.getArguments().isEmpty()) {
            return false;
        }

        local.setDefaultExpression((CtExpression) constructorCall.getArguments().get(0).clone());
        local.setType(local.getFactory().Type().objectType());
        return true;
    }

    private static boolean rewriteField(CtField<?> field) {
        CtExpression<?> defaultExpression = field.getDefaultExpression();
        if (!isOldWrapperType(field.getType()) || !(defaultExpression instanceof CtConstructorCall)) {
            return false;
        }

        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
        if (!isOldWrapperType(constructorCall.getType()) || constructorCall.getArguments().isEmpty()) {
            return false;
        }

        field.setDefaultExpression((CtExpression) constructorCall.getArguments().get(0).clone());
        field.setType(field.getFactory().Type().objectType());
        return true;
    }

    private static boolean isOldWrapperReference(CtExpression<?> expression) {
        return expression instanceof CtVariableRead || expression instanceof CtFieldRead;
    }

    private static boolean isOldWrapperConstructor(CtExpression<?> expression) {
        return expression instanceof CtConstructorCall && isOldWrapperType(expression.getType());
    }

    private static boolean isOldWrapperType(CtTypeReference<?> type) {
        if (type == null) {
            return false;
        }
        String qualifiedName = type.getQualifiedName();
        return OLD_WRAPPER_TYPE.equals(qualifiedName) || "ScriptResult".equals(type.getSimpleName()) || qualifiedName.endsWith(".ScriptResult");
    }
}
