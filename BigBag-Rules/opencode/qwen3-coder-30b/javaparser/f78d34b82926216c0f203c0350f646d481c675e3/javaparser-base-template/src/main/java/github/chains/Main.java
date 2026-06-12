package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java Main <oldPackage> <newPackage> <sourceDirectory>");
            System.err.println("Example: java Main develop.p2p.lib tokyo.peya.lib /path/to/project");
            System.exit(1);
        }

        String oldPackage = args[0];
        String newPackage = args[1];
        String sourceDirectory = args[2];

        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path filePath : javaFiles) {
                fixPackageReferences(filePath, oldPackage, newPackage);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void fixPackageReferences(Path filePath, String oldPackage, String newPackage) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            boolean modified = false;

            // Fix package declaration
            Optional<PackageDeclaration> packageDeclaration = cu.getPackageDeclaration();
            if (packageDeclaration.isPresent()) {
                String packageName = packageDeclaration.get().getNameAsString();
                if (packageName.startsWith(oldPackage)) {
                    String newPackageName = packageName.replaceFirst(
                        "^" + Pattern.quote(oldPackage) + "\\.",
                        newPackage + "."
                    );
                    packageDeclaration.get().setName(newPackageName);
                    modified = true;
                }
            }

            // Fix imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getName().asString();
                if (importName.startsWith(oldPackage + ".")) {
                    String newImport = importName.replaceFirst(
                        "^" + Pattern.quote(oldPackage) + "\\.",
                        newPackage + "."
                    );
                    importDecl.setName(newImport);
                    modified = true;
                }
            }

            // Fix class references in code (NameExpr)
            for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
                String nameStr = nameExpr.getName().asString();
                if (nameStr.startsWith(oldPackage + ".")) {
                    String newName = nameStr.replaceFirst(
                        "^" + Pattern.quote(oldPackage) + "\\.",
                        newPackage + "."
                    );
                    nameExpr.setName(newName);
                    modified = true;
                }
            }

            // Fix class references in code (Name)
            for (Name name : cu.findAll(Name.class)) {
                String nameStr = name.asString();
                if (nameStr.startsWith(oldPackage + ".")) {
                    String newName = nameStr.replaceFirst(
                        "^" + Pattern.quote(oldPackage) + "\\.",
                        newPackage + "."
                    );
                    name.setIdentifier(newName);
                    modified = true;
                }
            }

            if (modified) {
                // Write back to file
                String content = cu.toString();
                Files.write(filePath, content.getBytes());
                System.out.println("Fixed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}