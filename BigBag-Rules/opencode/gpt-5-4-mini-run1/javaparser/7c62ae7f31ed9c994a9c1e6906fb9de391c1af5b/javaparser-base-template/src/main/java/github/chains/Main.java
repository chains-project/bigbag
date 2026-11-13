package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument");
        }

        Path sourceRootPath = Paths.get(args[0]);
        ParserConfiguration configuration = new ParserConfiguration();
        SourceRoot sourceRoot = new SourceRoot(sourceRootPath, configuration);

        sourceRoot.tryToParseParallelized();

        UserTypeSessionImplementorFix fix = new UserTypeSessionImplementorFix();
        for (CompilationUnit cu : sourceRoot.getCompilationUnits()) {
            fix.apply(cu);
        }

        sourceRoot.saveAll();
    }

    private static final class UserTypeSessionImplementorFix {
        private static final String SESSION_IMPLEMENTOR = "org.hibernate.engine.spi.SessionImplementor";
        private static final String SHARED_SESSION_CONTRACT_IMPLEMENTOR =
                "org.hibernate.engine.spi.SharedSessionContractImplementor";

        private void apply(CompilationUnit cu) {
            boolean changed = false;
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (!isTargetMethod(method)) {
                    continue;
                }

                for (var parameter : method.getParameters()) {
                    if (parameter.getType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType type = parameter.getType().asClassOrInterfaceType();
                        if (type.getNameAsString().equals("SessionImplementor")) {
                            type.setName("SharedSessionContractImplementor");
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                replaceImport(cu, SESSION_IMPLEMENTOR, SHARED_SESSION_CONTRACT_IMPLEMENTOR);
            }
        }

        private boolean isTargetMethod(MethodDeclaration method) {
            String name = method.getNameAsString();
            if (!name.equals("nullSafeGet") && !name.equals("nullSafeSet")) {
                return false;
            }
            return method.getParameters().stream()
                    .anyMatch(parameter -> parameter.getType().isClassOrInterfaceType()
                            && parameter.getType().asClassOrInterfaceType().getNameAsString().equals("SessionImplementor"));
        }

        private void replaceImport(CompilationUnit cu, String oldImport, String newImport) {
            cu.getImports().stream()
                    .filter(importDeclaration -> importDeclaration.getNameAsString().equals(oldImport))
                    .findFirst()
                    .ifPresent(importDeclaration -> importDeclaration.setName(newImport));
        }
    }
}
