package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Main {
    private static final String PN_CONFIGURATION_FQN = "com.pubnub.api.PNConfiguration";
    private static final String PUBNUB_FQN = "com.pubnub.api.PubNub";
    private static final String USER_ID_FQN = "com.pubnub.api.UserId";
    private static final String OLD_USER_RESULT_FQN = "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult";
    private static final String OLD_SPACE_RESULT_FQN = "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult";
    private static final String OLD_MEMBERSHIP_RESULT_FQN = "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult";
    private static final String NEW_UUID_RESULT_FQN = "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult";
    private static final String NEW_CHANNEL_RESULT_FQN = "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult";
    private static final String NEW_MEMBERSHIP_RESULT_FQN = "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult";
    private static final String FILE_RESULT_FQN = "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult";
    private static final String SUBSCRIBE_CALLBACK_FQN = "com.pubnub.api.callbacks.SubscribeCallback";
    private static final String SUBSCRIBE_CALLBACK_SIMPLE = "SubscribeCallback";
    private static final String UUID_FACTORY_EXPRESSION = "com.pubnub.api.PubNub.generateUUID()";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Input path is not a directory: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            AtomicBoolean changed = new AtomicBoolean(false);

            rewriteImports(cu, changed);

            cu.findAll(ObjectCreationExpr.class)
                .forEach(
                    expr -> {
                        if (isTargetPnConfigurationCreation(expr) && expr.getArguments().isEmpty()) {
                            Expression uuidExpr = StaticJavaParser.parseExpression("new " + USER_ID_FQN + "(" + UUID_FACTORY_EXPRESSION + ")");
                            expr.setArguments(NodeList.nodeList(uuidExpr));
                            changed.set(true);
                        }
                    });

            cu.findAll(VariableDeclarationExpr.class)
                .forEach(
                    declaration -> {
                        if (rewriteCheckedPnConfigurationDeclaration(declaration)) {
                            changed.set(true);
                        }
                    });

            cu.findAll(MethodDeclaration.class)
                .forEach(
                    method -> {
                        if (renameSubscribeCallbackMethod(method)) {
                            changed.set(true);
                        }
                    });

            cu.findAll(ObjectCreationExpr.class)
                .forEach(
                    expr -> {
                        if (ensureFileCallbackExists(expr)) {
                            changed.set(true);
                        }
                    });

            cu.findAll(ClassOrInterfaceDeclaration.class)
                .forEach(
                    declaration -> {
                        if (ensureFileCallbackExists(declaration)) {
                            changed.set(true);
                        }
                    });

            if (changed.get()) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static void rewriteImports(CompilationUnit cu, AtomicBoolean changed) {
        cu.getImports()
            .forEach(
                imp -> {
                    String name = imp.getNameAsString();
                    if (OLD_USER_RESULT_FQN.equals(name)) {
                        imp.setName(NEW_UUID_RESULT_FQN);
                        changed.set(true);
                    } else if (OLD_SPACE_RESULT_FQN.equals(name)) {
                        imp.setName(NEW_CHANNEL_RESULT_FQN);
                        changed.set(true);
                    } else if (OLD_MEMBERSHIP_RESULT_FQN.equals(name)) {
                        imp.setName(NEW_MEMBERSHIP_RESULT_FQN);
                        changed.set(true);
                    }
                });
    }

    private static boolean isTargetPnConfigurationCreation(ObjectCreationExpr expr) {
        String typeName = expr.getType().getNameAsString();
        String typeString = expr.getType().asString();
        return PN_CONFIGURATION_FQN.equals(typeString) || "PNConfiguration".equals(typeName);
    }

    private static boolean rewriteCheckedPnConfigurationDeclaration(VariableDeclarationExpr declaration) {
        if (declaration.getVariables().size() != 1) {
            return false;
        }

        VariableDeclarator variable = declaration.getVariable(0);
        if (!variable.getInitializer().isPresent() || !variable.getInitializer().get().isObjectCreationExpr()) {
            return false;
        }

        ObjectCreationExpr init = variable.getInitializer().get().asObjectCreationExpr();
        if (!isTargetPnConfigurationCreation(init) || !init.getArguments().isEmpty()) {
            return false;
        }

        if (!(declaration.getParentNode().orElse(null) instanceof ExpressionStmt)) {
            return false;
        }
        ExpressionStmt statement = (ExpressionStmt) declaration.getParentNode().orElse(null);
        if (!(statement.getParentNode().orElse(null) instanceof BlockStmt)) {
            return false;
        }
        BlockStmt block = (BlockStmt) statement.getParentNode().orElse(null);

        Statement declarationStmt = StaticJavaParser.parseStatement(variable.getType() + " " + variable.getNameAsString() + ";");
        Statement tryStmt = StaticJavaParser.parseStatement(
            "try { "
                + variable.getNameAsString()
                + " = new "
                + USER_ID_FQN
                + "(" 
                + UUID_FACTORY_EXPRESSION
                + "); } catch (com.pubnub.api.PubNubException e) { throw new RuntimeException(e); }");

        int index = block.getStatements().indexOf(statement);
        block.getStatements().remove(index);
        block.addStatement(index, declarationStmt);
        block.addStatement(index + 1, tryStmt);
        return true;
    }

    private static boolean renameSubscribeCallbackMethod(MethodDeclaration method) {
        if (method.getParameters().size() != 2) {
            return false;
        }

        String methodName = method.getNameAsString();
        String secondType = method.getParameter(1).getType().asString();
        if ("user".equals(methodName) && matchesType(secondType, "PNUserResult", OLD_USER_RESULT_FQN)) {
            method.setName("uuid");
            method.getParameter(1).setType(NEW_UUID_RESULT_FQN);
            return true;
        }
        if ("space".equals(methodName) && matchesType(secondType, "PNSpaceResult", OLD_SPACE_RESULT_FQN)) {
            method.setName("channel");
            method.getParameter(1).setType(NEW_CHANNEL_RESULT_FQN);
            return true;
        }
        if ("membership".equals(methodName) && matchesType(secondType, "PNMembershipResult", OLD_MEMBERSHIP_RESULT_FQN)) {
            method.getParameter(1).setType(NEW_MEMBERSHIP_RESULT_FQN);
            return true;
        }
        return false;
    }

    private static boolean ensureFileCallbackExists(ObjectCreationExpr expr) {
        if (expr.getAnonymousClassBody().isEmpty()) {
            return false;
        }

        boolean isSubscribeCallback =
            SUBSCRIBE_CALLBACK_FQN.equals(expr.getType().asString())
                || SUBSCRIBE_CALLBACK_SIMPLE.equals(expr.getType().getNameAsString());
        if (!isSubscribeCallback) {
            return false;
        }

        NodeList<BodyDeclaration<?>> body = expr.getAnonymousClassBody().get();
        boolean alreadyHasFile =
            body.stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .anyMatch(m -> m.getNameAsString().equals("file") && m.getParameters().size() == 2 && matchesType(m.getParameter(1).getType().asString(), "PNFileEventResult", FILE_RESULT_FQN));
        if (alreadyHasFile) {
            return false;
        }

        NodeList<BodyDeclaration<?>> updatedBody = new NodeList<>();
        updatedBody.addAll(body);
        updatedBody.add(
            StaticJavaParser.parseBodyDeclaration(
                "@Override public void file(" + PUBNUB_FQN + " pubnub, " + FILE_RESULT_FQN + " pnFileEventResult) {}"));
        expr.setAnonymousClassBody(updatedBody);
        return true;
    }

    private static boolean ensureFileCallbackExists(ClassOrInterfaceDeclaration declaration) {
        boolean extendsSubscribeCallback =
            declaration.getExtendedTypes().stream()
                .anyMatch(type -> SUBSCRIBE_CALLBACK_FQN.equals(type.getNameAsString()) || SUBSCRIBE_CALLBACK_FQN.equals(type.asString()));
        if (!extendsSubscribeCallback && !declaration.getImplementedTypes().stream()
            .anyMatch(type -> SUBSCRIBE_CALLBACK_FQN.equals(type.getNameAsString()) || SUBSCRIBE_CALLBACK_FQN.equals(type.asString()))) {
            return false;
        }

        boolean alreadyHasFile =
            declaration.getMethodsByName("file").stream()
                .anyMatch(m -> m.getParameters().size() == 2 && matchesType(m.getParameter(1).getType().asString(), "PNFileEventResult", FILE_RESULT_FQN));
        if (alreadyHasFile) {
            return false;
        }

        String fileMethod =
            "@Override public void file(" + PUBNUB_FQN + " pubnub, " + FILE_RESULT_FQN + " pnFileEventResult) {}";
        MethodDeclaration method = StaticJavaParser.parseBodyDeclaration(fileMethod).asMethodDeclaration();
        declaration.addMember(method);
        return true;
    }

    private static boolean matchesType(String actual, String simpleName, String fqn) {
        return simpleName.equals(actual) || fqn.equals(actual) || actual.endsWith("." + simpleName);
    }
}
