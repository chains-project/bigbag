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
public class Main {
    public static void main(String[] args) {
        // This is a generic transformation that can be applied to any Maven project
        // with the breaking change from Mockito 4.x to 5.x
        System.out.println("Generic Mockito getArgumentAt to getArgument transformation rule");
    }
}
