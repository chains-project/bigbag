package github.hamcrest;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fixes Hamcrest StringContains constructor breaking change from (boolean, String) to (String).
 * This transformation replaces calls to new StringContains(true, "message") with
 * new StringContains("message").
 */
public class StringContainsFix extends ModifierVisitor<Void> {
    
    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        // Check if this is a StringContains constructor call
        if (n.getType().getNameAsString().equals("StringContains")) {
            // Check if it has 2 arguments (boolean, String) - the breaking change case
            if (n.getArguments().size() == 2) {
                // Get the second argument (the string part)
                Expression secondArg = n.getArguments().get(1);
                // Replace with constructor that only takes the string
                return new ObjectCreationExpr(
                    n.getScope().orElse(null),
                    n.getType(),
                    List.of(secondArg)
                );
            }
        }
        return super.visit(n, arg);
    }
}