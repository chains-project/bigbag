package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class Main {

    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String NEW_CONCRETE_TYPE = "org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path sourceRoot = Path.of(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        }

    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                if (importDeclaration.getNameAsString().equals(OLD_TYPE)) {
                    importDeclaration.setName(NEW_TYPE);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (isOldType(type)) {
                    replaceType(type, NEW_TYPE);
                    changed = true;
                }
            }

            for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldType(creationExpr.getType()) || isType(creationExpr.getType(), NEW_TYPE)) {
                    replaceType(creationExpr.getType(), NEW_CONCRETE_TYPE);
                    changed = true;
                }
            }

            if (changed) {
                ensureImport(cu, NEW_TYPE);
                ensureImport(cu, NEW_CONCRETE_TYPE);
                Files.writeString(file, cu.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static void replaceType(ClassOrInterfaceType type, String qualifiedName) {
        type.setName(simpleName(qualifiedName));
        type.setScope(null);
    }

    private static boolean isOldType(ClassOrInterfaceType type) {
        return isType(type, OLD_TYPE);
    }

    private static boolean isType(ClassOrInterfaceType type, String qualifiedName) {
        String name = type.getNameWithScope();
        return qualifiedName.equals(name) || simpleName(qualifiedName).equals(type.getNameAsString());
    }

    private static String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
    }

    private static void ensureImport(CompilationUnit cu, String qualifiedName) {
        boolean alreadyPresent = cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch(qualifiedName::equals);
        if (!alreadyPresent) {
            cu.addImport(qualifiedName);
        }
    }

}
