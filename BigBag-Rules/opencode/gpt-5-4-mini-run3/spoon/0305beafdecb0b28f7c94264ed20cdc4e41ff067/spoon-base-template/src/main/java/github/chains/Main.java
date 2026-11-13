package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String OLD_PREFIX = "com.mysql.jdbc";
    private static final String NEW_PREFIX = "com.mysql.cj.jdbc";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root> [output-root]");
        }

        Path sourceRoot = Paths.get(args[0]);
        Path outputRoot = args.length > 1 ? Paths.get(args[1]) : sourceRoot;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputRoot.toFile());
        launcher.addInputResource(sourceRoot.toString());
        launcher.buildModel();

        fixImports(launcher);
        fixTypeReferences(launcher);
        fixStringLiterals(launcher);

        launcher.prettyprint();
    }

    private static void fixImports(Launcher launcher) {
        List<CtImport> imports = launcher.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            CtTypeReference<?> reference = ctImport.getReference() instanceof CtTypeReference<?> typeReference ? typeReference : null;
            String qualifiedName = reference != null ? reference.getQualifiedName() : null;
            if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
                ctImport.setReference(launcher.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
            }
        }
    }

    private static void fixTypeReferences(Launcher launcher) {
        for (CtType<?> type : launcher.getModel().getElements(new TypeFilter<>(CtType.class))) {
            for (Object element : type.getElements(new TypeFilter<>(CtTypeReference.class))) {
                CtTypeReference<?> reference = (CtTypeReference<?>) element;
                String qualifiedName = reference.getQualifiedName();
                if (qualifiedName.startsWith(OLD_PREFIX)) {
                    reference.replace(launcher.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
                }
            }

            for (Object element : type.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                CtConstructorCall<?> call = (CtConstructorCall<?>) element;
                CtTypeReference<?> typeRef = call.getType();
                if (typeRef != null) {
                    String qualifiedName = typeRef.getQualifiedName();
                    if (qualifiedName.startsWith(OLD_PREFIX)) {
                        typeRef.replace(launcher.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
                    }
                }
            }
        }
    }

    private static void fixStringLiterals(Launcher launcher) {
        for (CtLiteral<?> literal : launcher.getModel().getElements(new TypeFilter<>(CtLiteral.class))) {
            Object value = literal.getValue();
            if (value instanceof String stringValue && stringValue.startsWith(OLD_PREFIX)) {
                ((CtLiteral) literal).setValue(rewriteQualifiedName(stringValue));
            }
        }
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        return qualifiedName.startsWith(OLD_PREFIX) ? NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length()) : qualifiedName;
    }
}
