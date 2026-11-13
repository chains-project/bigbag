package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("THREE", 3);
        CONSTANTS.put("FOUR", 4);
        CONSTANTS.put("FIVE", 5);
        CONSTANTS.put("SIX", 6);
        CONSTANTS.put("SEVEN", 7);
        CONSTANTS.put("EIGHT", 8);
        CONSTANTS.put("NINE", 9);
        CONSTANTS.put("TEN", 10);
        CONSTANTS.put("FIFTEEN", 15);
        CONSTANTS.put("TWENTY", 20);
        CONSTANTS.put("THIRTY", 30);
        CONSTANTS.put("FORTY", 40);
        CONSTANTS.put("FIFTY", 50);
        CONSTANTS.put("SIXTY", 60);
        CONSTANTS.put("SEVENTY", 70);
        CONSTANTS.put("EIGHTY", 80);
        CONSTANTS.put("NINETY", 90);
        CONSTANTS.put("HUNDRED", 100);
        CONSTANTS.put("THOUSAND", 1_000);
        CONSTANTS.put("MILLION", 1_000_000);
        CONSTANTS.put("BILLION", 1_000_000_000);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }
        final Path input = Path.of(args[0]);
        final Path output = args.length > 1 ? Path.of(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());

        final var model = launcher.buildModel();
        final java.util.Set<CtCompilationUnit> affected = new java.util.HashSet<>();

        for (Object item : new ArrayList<>(model.getElements(new TypeFilter<>(CtType.class)))) {
            final CtType<?> type = (CtType<?>) item;
            final SourcePosition pos = type.getPosition();
            if (pos == null) {
                continue;
            }
            final CtCompilationUnit cu = pos.getCompilationUnit();
            if (cu == null) {
                continue;
            }
            affected.add(cu);
        }

        for (Object item : new ArrayList<>(model.getElements(new TypeFilter<>(CtImport.class)))) {
            final CtImport imp = (CtImport) item;
            if (isTvImport(imp)) {
                imp.delete();
            }
        }

        for (Object item : new ArrayList<>(model.getElements(new TypeFilter<>(CtFieldAccess.class)))) {
            final CtFieldAccess<?> access = (CtFieldAccess<?>) item;
            final CtFieldReference<?> ref = access.getVariable();
            if (ref == null) {
                continue;
            }
            final CtTypeReference<?> declaring = ref.getDeclaringType();
            if (declaring == null || !OLD_TYPE.equals(declaring.getQualifiedName())) {
                continue;
            }
            final SourcePosition pos = access.getPosition();
            if (pos != null && pos.getCompilationUnit() != null) {
                affected.add(pos.getCompilationUnit());
            }
        }

        for (CtCompilationUnit cu : affected) {
            cu.getImports().clear();
        }

        for (Object item : new ArrayList<>(model.getElements(new TypeFilter<>(CtFieldAccess.class)))) {
            final CtFieldAccess<?> access = (CtFieldAccess<?>) item;
            final CtFieldReference<?> ref = access.getVariable();
            if (ref == null) {
                continue;
            }
            final CtTypeReference<?> declaring = ref.getDeclaringType();
            if (declaring == null || !OLD_TYPE.equals(declaring.getQualifiedName())) {
                continue;
            }
            final Integer value = CONSTANTS.get(ref.getSimpleName());
            if (value == null) {
                continue;
            }
            final CtLiteral<Integer> literal = access.getFactory().Code().createLiteral(value);
            access.replace(literal);
        }

        launcher.prettyprint();
        cleanup(output);
        if (!output.equals(input)) {
            mirror(output, input);
        }
    }

    private static void cleanup(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    if (file.toString().endsWith(".java")) {
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        text = text.replace("import com.jcabi.aspects.Tv;\n", "");
                        for (Map.Entry<String, Integer> entry : CONSTANTS.entrySet()) {
                            text = text.replace("Tv." + entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        Files.writeString(file, text, StandardCharsets.UTF_8);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void mirror(Path source, Path target) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static boolean isTvImport(CtImport imp) {
        if (imp == null) {
            return false;
        }
        final var reference = imp.getReference();
        return reference instanceof CtTypeReference
            && OLD_TYPE.equals(((CtTypeReference<?>) reference).getQualifiedName());
    }
}
