package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        Path srcRoot = Paths.get("/workspace/lithium/src");
        List<Path> javaFiles = Files.walk(srcRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path javaFile : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            boolean changed = false;

            for (ImportDeclaration imp : cu.findAll(ImportDeclaration.class)) {
                String name = imp.getNameAsString();
                String newName = name;

                newName = newName.replace("javax.ws.rs", "jakarta.ws.rs");
                newName = newName.replace("javax.servlet", "jakarta.servlet");
                newName = newName.replace("javax.validation", "jakarta.validation");
                
                // Do not replace javax.annotation as Dropwizard 4 uses JSR-305 (javax.annotation.Nullable)

                if (newName.equals("io.dropwizard.Application")) newName = "io.dropwizard.core.Application";
                if (newName.equals("io.dropwizard.Configuration")) newName = "io.dropwizard.core.Configuration";
                if (newName.equals("io.dropwizard.setup.Bootstrap")) newName = "io.dropwizard.core.setup.Bootstrap";
                if (newName.equals("io.dropwizard.setup.Environment")) newName = "io.dropwizard.core.setup.Environment";

                if (newName.startsWith("io.dropwizard.logging.")) {
                    newName = newName.replace("io.dropwizard.logging.", "io.dropwizard.logging.common.");
                }

                if (newName.equals("com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider")) {
                    newName = "com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider";
                }

                if (newName.startsWith("io.swagger.annotations")) {
                    imp.remove();
                    changed = true;
                    continue;
                }

                if (newName.equals("io.dropwizard.util.Strings")) {
                    imp.remove();
                    changed = true;
                    continue;
                }

                if (!newName.equals(name)) {
                    imp.setName(newName);
                    changed = true;
                }
            }

            for (MethodCallExpr mc : cu.findAll(MethodCallExpr.class)) {
                if (mc.getNameAsString().equals("isNullOrEmpty")) {
                    if (mc.getScope().isPresent() && mc.getScope().get().toString().equals("Strings")) {
                        Expression arg = mc.getArgument(0);
                        BinaryExpr replacement = new BinaryExpr(
                                new BinaryExpr(arg.clone(), new NullLiteralExpr(), BinaryExpr.Operator.EQUALS),
                                new MethodCallExpr(arg.clone(), "isEmpty"),
                                BinaryExpr.Operator.OR
                        );
                        mc.replace(new EnclosedExpr(replacement));
                        changed = true;
                    }
                }
            }

            for (AnnotationExpr ann : cu.findAll(AnnotationExpr.class)) {
                String name = ann.getNameAsString();
                if (name.equals("Api") || name.equals("ApiOperation") || name.equals("ApiResponses")
                        || name.equals("ApiResponse") || name.equals("ApiParam")) {
                    ann.remove();
                    changed = true;
                } else if (name.equals("Authorization")) {
                    NormalAnnotationExpr replacement = new NormalAnnotationExpr(new Name("io.swagger.v3.oas.annotations.security.SecurityRequirement"), new com.github.javaparser.ast.NodeList<>(
                            new MemberValuePair("name", new StringLiteralExpr("Bearer"))
                    ));
                    ann.replace(replacement);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(javaFile, cu.toString());
            }
        }
    }
}
