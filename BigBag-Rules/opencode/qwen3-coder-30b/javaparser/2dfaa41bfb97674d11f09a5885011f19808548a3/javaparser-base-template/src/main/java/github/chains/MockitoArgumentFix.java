package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SuffixExpression;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation rule to fix Mockito getArgumentAt method calls
 * that were changed to getArgument in Mockito 5.x
 */
public class MockitoArgumentFix {
    
    /**
     * A JavaParser visitor that transforms getArgumentAt calls to getArgument calls
     */
    public static class MockitoArgumentFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to getArgumentAt on an InvocationOnMock
            if (n.getNameAsString().equals("getArgumentAt")) {
                // Replace getArgumentAt with getArgument
                // This handles the pattern: invocation.getArgumentAt(index, Class.class).cast(result)
                MethodCallExpr replacement = new MethodCallExpr(
                    n.getScope().orElse(null), 
                    "getArgument",
                    n.getArguments()
                );
                return replacement;
            }
            return super.visit(n, arg);
        }
    }
    
    /**
     * Apply the transformation to a compilation unit
     */
    public static void applyTransformation(CompilationUnit cu) {
        cu.accept(new MockitoArgumentFixVisitor(), null);
    }
}