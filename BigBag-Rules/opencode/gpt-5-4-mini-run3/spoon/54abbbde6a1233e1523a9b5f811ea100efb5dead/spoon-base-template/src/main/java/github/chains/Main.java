package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String REMOVED_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = Map.of(
        "SEVEN", 7,
        "TEN", 10,
        "MILLION", 1000000
    );

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addInputResource(input.toString());
        launcher.buildModel();
        final List<CtImport> removedImports = new ArrayList<>();
        for (CtImport imp : launcher.getModel().getElements(
            new TypeFilter<>(CtImport.class)
        )) {
            if (imp.getReference() instanceof CtTypeReference<?> typeRef
                && REMOVED_TYPE.equals(typeRef.getQualifiedName())) {
                removedImports.add(imp);
            }
        }
        for (CtImport imp : removedImports) {
            final CtCompilationUnit unit = imp.getParent(CtCompilationUnit.class);
            if (unit != null) {
                unit.getImports().removeIf(item -> item == imp);
            }
            imp.delete();
        }
        for (CtFieldAccess<?> access : launcher.getModel().getElements(
            new TypeFilter<>(CtFieldAccess.class)
        )) {
            final CtFieldReference<?> field = access.getVariable();
            if (field == null) {
                continue;
            }
            final CtTypeReference<?> declaring = field.getDeclaringType();
            if (declaring == null) {
                continue;
            }
            if (!REMOVED_TYPE.equals(declaring.getQualifiedName())) {
                continue;
            }
            final Integer value = CONSTANTS.get(field.getSimpleName());
            if (value == null) {
                continue;
            }
            final CtLiteral<Integer> literal = launcher.getFactory().Code()
                .createLiteral(value);
            access.replace(literal);
        }
        launcher.prettyprint();
        rewriteSources(output);
    }

    private static void rewriteSources(final Path root) {
        try {
            Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String text = Files.readString(path, StandardCharsets.UTF_8);
                        String updated = text
                            .replace("import com.jcabi.aspects.Tv;\n", "")
                            .replace("import com.jcabi.aspects.Tv;\r\n", "")
                            .replace("Tv.SEVEN", "7")
                            .replace("Tv.TEN", "10")
                            .replace("Tv.MILLION", "1000000");
                        if (!text.equals(updated)) {
                            Files.writeString(path, updated, StandardCharsets.UTF_8);
                        }
                    } catch (final Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
