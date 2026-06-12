package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        String projectPath = "/workspace/PeyangSuperbAntiCheat/src/main/java";
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            boolean[] changed = {false};

            for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                String importName = id.getNameAsString();
                if (importName.startsWith("develop.p2p.lib")) {
                    String newName = importName.replace("develop.p2p.lib", "tokyo.peya.lib");
                    id.setName(newName);
                    changed[0] = true;
                }
            }

            for (Name name : cu.findAll(Name.class)) {
                if (name.asString().equals("develop.p2p.lib.LeetConverter")) {
                    name.setQualifier(new Name(new Name(new Name("tokyo"), "peya"), "lib"));
                    changed[0] = true;
                } else if (name.asString().equals("develop.p2p.lib")) {
                    if (name.getQualifier().isPresent()) {
                        name.setQualifier(new Name(new Name("tokyo"), "peya"));
                        name.setIdentifier("lib");
                    }
                    changed[0] = true;
                }
            }

            for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
                if (nameExpr.getNameAsString().equals("develop")) {
                    if (nameExpr.getParentNode().isPresent() && nameExpr.getParentNode().get() instanceof FieldAccessExpr) {
                        FieldAccessExpr p2p = (FieldAccessExpr) nameExpr.getParentNode().get();
                        if (p2p.getNameAsString().equals("p2p")) {
                            if (p2p.getParentNode().isPresent() && p2p.getParentNode().get() instanceof FieldAccessExpr) {
                                FieldAccessExpr lib = (FieldAccessExpr) p2p.getParentNode().get();
                                if (lib.getNameAsString().equals("lib")) {
                                    nameExpr.setName("tokyo");
                                    p2p.setName("peya");
                                    changed[0] = true;
                                }
                            }
                        }
                    }
                }
            }

            if (changed[0]) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
