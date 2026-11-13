package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(root).filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(javaFiles::add);

        for (Path file : javaFiles) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(source);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewritePropertyUtilsCalls(cu);
            changed |= rewriteHibernateValidatorPattern(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean rewritePropertyUtilsCalls(CompilationUnit cu) {
        boolean changed = false;
        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            if (!call.getNameAsString().equals("getPropertyDescriptors") && !call.getNameAsString().equals("getPropertyDescriptor")) {
                continue;
            }
            if (call.getScope().isEmpty()) {
                continue;
            }

            String scope = call.getScope().get().toString();
            if (!scope.endsWith("PropertyUtils")) {
                continue;
            }

            Expression replacement;
            if (call.getNameAsString().equals("getPropertyDescriptors") && call.getArguments().size() == 1) {
                replacement = StaticJavaParser.parseExpression("resolvePropertyDescriptors(" + call.getArgument(0) + ")");
            } else if (call.getNameAsString().equals("getPropertyDescriptor") && call.getArguments().size() == 2) {
                replacement = StaticJavaParser.parseExpression("resolvePropertyDescriptor(" + call.getArgument(0) + ", " + call.getArgument(1) + ")");
            } else {
                continue;
            }

            call.replace(replacement);
            changed = true;
        }

        if (changed) {
            addPropertyDescriptorHelpers(cu);
        }
        return changed;
    }

    private static boolean rewriteHibernateValidatorPattern(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            List<Statement> statements = new ArrayList<>(block.getStatements());
            boolean blockChanged = false;

            for (int i = 0; i < statements.size(); i++) {
                Statement statement = statements.get(i);

                if (isValidatorFactoryStatement(statement) || isConstraintDescriptorsStatement(statement)) {
                    statements.remove(i);
                    i--;
                    blockChanged = true;
                    continue;
                }

                if (isRequiredFalseStatement(statement) && i + 1 < statements.size() && isHibernateConstraintBlock(statements.get(i + 1))) {
                    statements.set(i, StaticJavaParser.parseStatement("boolean required = resolveRequiredProperty(modelClass, descriptor);"));
                    statements.remove(i + 1);
                    blockChanged = true;
                }
            }

            if (blockChanged) {
                block.getStatements().clear();
                statements.forEach(block::addStatement);
                changed = true;
            }
        }

        if (changed) {
            addRequiredPropertyHelpers(cu);
        }
        return changed;
    }

    private static boolean isValidatorFactoryStatement(Statement statement) {
        Optional<VariableDeclarationExpr> variable = extractVariableDeclarationExpr(statement);
        if (!variable.isPresent() || variable.get().getVariables().size() != 1) {
            return false;
        }
        return variable.get().getVariable(0).getNameAsString().equals("validator")
                && variable.get().getVariable(0).getInitializer().isPresent()
                && variable.get().getVariable(0).getInitializer().get().toString().contains("HibernateValidatorProperty.validatorFactory.getValidator()");
    }

    private static boolean isConstraintDescriptorsStatement(Statement statement) {
        Optional<VariableDeclarationExpr> variable = extractVariableDeclarationExpr(statement);
        if (!variable.isPresent() || variable.get().getVariables().size() != 1) {
            return false;
        }
        return variable.get().getVariable(0).getNameAsString().equals("constraintDescriptors");
    }

    private static boolean isRequiredFalseStatement(Statement statement) {
        Optional<VariableDeclarationExpr> variable = extractVariableDeclarationExpr(statement);
        if (!variable.isPresent() || variable.get().getVariables().size() != 1) {
            return false;
        }
        return variable.get().getVariable(0).getNameAsString().equals("required")
                && variable.get().getVariable(0).getInitializer().isPresent()
                && variable.get().getVariable(0).getInitializer().get().isBooleanLiteralExpr()
                && !variable.get().getVariable(0).getInitializer().get().asBooleanLiteralExpr().getValue();
    }

    private static boolean isHibernateConstraintBlock(Statement statement) {
        if (!statement.isIfStmt()) {
            return false;
        }
        IfStmt ifStmt = statement.asIfStmt();
        return ifStmt.getCondition().toString().contains("constraintDescriptor")
                && ifStmt.getThenStmt().toString().contains("getConstraintDescriptors")
                && ifStmt.getThenStmt().toString().contains("NotNull")
                && ifStmt.getThenStmt().toString().contains("NotEmpty")
                && ifStmt.getThenStmt().toString().contains("NotBlank");
    }

    private static Optional<VariableDeclarationExpr> extractVariableDeclarationExpr(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }
        if (!statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
            return Optional.empty();
        }
        return Optional.of(statement.asExpressionStmt().getExpression().asVariableDeclarationExpr());
    }

    private static void addPropertyDescriptorHelpers(CompilationUnit cu) {
        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (clazz == null) {
            return;
        }
        if (clazz.getMethodsByName("resolvePropertyDescriptors").isEmpty()) {
            clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static java.beans.PropertyDescriptor[] resolvePropertyDescriptors(Class<?> modelClass) {" +
                    "    try {" +
                    "        return java.beans.Introspector.getBeanInfo(modelClass).getPropertyDescriptors();" +
                    "    } catch (java.beans.IntrospectionException e) {" +
                    "        throw new RuntimeException(e);" +
                    "    }" +
                    "}"));
        }
        if (clazz.getMethodsByName("resolvePropertyDescriptor").isEmpty()) {
            clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static java.beans.PropertyDescriptor resolvePropertyDescriptor(Object bean, String propertyName) {" +
                    "    try {" +
                    "        for (java.beans.PropertyDescriptor descriptor : java.beans.Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {" +
                    "            if (propertyName.equals(descriptor.getName())) {" +
                    "                return descriptor;" +
                    "            }" +
                    "        }" +
                    "        throw new RuntimeException(\"error getting property \" + propertyName);" +
                    "    } catch (java.beans.IntrospectionException e) {" +
                    "        throw new RuntimeException(e);" +
                    "    }" +
                    "}"));
        }
    }

    private static void addRequiredPropertyHelpers(CompilationUnit cu) {
        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (clazz == null) {
            return;
        }
        if (clazz.getMethodsByName("resolveRequiredProperty").isEmpty()) {
            clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static boolean resolveRequiredProperty(Class<?> modelClass, java.beans.PropertyDescriptor descriptor) {" +
                    "    for (java.lang.annotation.Annotation annotation : resolvePropertyAnnotations(modelClass, descriptor)) {" +
                    "        String name = annotation.annotationType().getSimpleName();" +
                    "        if (\"NotNull\".equals(name) || \"NotEmpty\".equals(name) || \"NotBlank\".equals(name)) {" +
                    "            return true;" +
                    "        }" +
                    "    }" +
                    "    return false;" +
                    "}"));
        }
        if (clazz.getMethodsByName("resolvePropertyAnnotations").isEmpty()) {
            clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static java.lang.annotation.Annotation[] resolvePropertyAnnotations(Class<?> modelClass, java.beans.PropertyDescriptor descriptor) {" +
                    "    if (descriptor.getReadMethod() != null) {" +
                    "        return descriptor.getReadMethod().getAnnotations();" +
                    "    }" +
                    "    try {" +
                    "        java.lang.reflect.Field field = modelClass.getDeclaredField(descriptor.getName());" +
                    "        return field.getAnnotations();" +
                    "    } catch (NoSuchFieldException e) {" +
                    "        return new java.lang.annotation.Annotation[0];" +
                    "    }" +
                    "}"));
        }
    }
}
