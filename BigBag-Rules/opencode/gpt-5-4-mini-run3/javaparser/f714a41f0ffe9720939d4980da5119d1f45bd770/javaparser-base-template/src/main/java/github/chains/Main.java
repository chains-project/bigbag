package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String OLD_USER_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult";
    private static final String OLD_SPACE_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult";
    private static final String OLD_MEMBERSHIP_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult";

    private static final String NEW_UUID_RESULT =
            "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult";
    private static final String NEW_CHANNEL_RESULT =
            "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult";
    private static final String NEW_MEMBERSHIP_RESULT =
            "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult";
    private static final String NEW_FILE_RESULT =
            "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/target/"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewrite);
    }

    private static void rewrite(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            changed |= removeBrokenImports(cu);
            PubnubVisitor visitor = new PubnubVisitor();
            visitor.visit(cu, null);
            changed |= visitor.changed;

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean removeBrokenImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= cu.getImports().removeIf(i -> OLD_USER_RESULT.equals(i.getNameAsString()));
        changed |= cu.getImports().removeIf(i -> OLD_SPACE_RESULT.equals(i.getNameAsString()));
        changed |= cu.getImports().removeIf(i -> OLD_MEMBERSHIP_RESULT.equals(i.getNameAsString()));
        return changed;
    }

    private static final class PubnubVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (isSimpleType(n.getType(), "PNConfiguration")) {
                ensurePubNubExceptionDeclared(n);
                changed = true;
            }

            if (isSimpleType(n.getType(), "SubscribeCallback")) {
                ensureFileCallback(n);
            }

            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            if (n.getExpression().isVariableDeclarationExpr()) {
                var declaration = n.getExpression().asVariableDeclarationExpr();
                if (declaration.getVariables().size() == 1) {
                    var variable = declaration.getVariable(0);
                    if (variable.getType().isClassOrInterfaceType()
                            && "PNConfiguration".equals(variable.getType().asClassOrInterfaceType().getNameAsString())
                            && variable.getInitializer().isPresent()
                            && variable.getInitializer().get().isObjectCreationExpr()
                            && variable.getInitializer().get().asObjectCreationExpr().getArguments().isEmpty()) {
                        String replacement =
                                "{ "
                                        + "com.pubnub.api.PNConfiguration "
                                        + variable.getNameAsString()
                                        + "; "
                                        + "try { "
                                        + variable.getNameAsString()
                                        + " = new com.pubnub.api.PNConfiguration(new com.pubnub.api.UserId(\"default\")); "
                                        + "} catch (com.pubnub.api.PubNubException e) { "
                                        + "throw new RuntimeException(e); "
                                        + "} }";
                        changed = true;
                        return StaticJavaParser.parseBlock(replacement);
                    }
                }
            }

            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            if (n.getParameters().size() == 2 && isPubNubType(n.getParameter(0).getType())) {
                Type secondType = n.getParameter(1).getType();
                String secondName = secondType.asString();

                if ("user".equals(n.getNameAsString()) && isSimpleType(secondType, "PNUserResult")) {
                    n.setName("uuid");
                    n.getParameter(1).setType(NEW_UUID_RESULT);
                    changed = true;
                } else if ("space".equals(n.getNameAsString()) && isSimpleType(secondType, "PNSpaceResult")) {
                    n.setName("channel");
                    n.getParameter(1).setType(NEW_CHANNEL_RESULT);
                    changed = true;
                } else if ("membership".equals(n.getNameAsString()) && isSimpleType(secondType, "PNMembershipResult")) {
                    if (!NEW_MEMBERSHIP_RESULT.equals(secondName)) {
                        n.getParameter(1).setType(NEW_MEMBERSHIP_RESULT);
                        changed = true;
                    }
                }
            }

            return super.visit(n, arg);
        }

        private void ensureFileCallback(ObjectCreationExpr n) {
            Optional<NodeList<BodyDeclaration<?>>> maybeBody = n.getAnonymousClassBody();
            if (maybeBody.isEmpty()) {
                return;
            }

            NodeList<BodyDeclaration<?>> body = maybeBody.get();
            boolean hasFile = body.stream()
                    .filter(BodyDeclaration::isMethodDeclaration)
                    .map(BodyDeclaration::asMethodDeclaration)
                    .anyMatch(method -> "file".equals(method.getNameAsString()) && method.getParameters().size() == 2);

            if (hasFile) {
                return;
            }

            MethodDeclaration method = new MethodDeclaration();
            method.addModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
            method.setType(new VoidType());
            method.setName("file");
            method.addParameter("com.pubnub.api.PubNub", "pubnub");
            method.addParameter(NEW_FILE_RESULT, "pnFileEventResult");
            method.setBody(new BlockStmt());
            body.add(method);
            changed = true;
        }

        private void ensurePubNubExceptionDeclared(ObjectCreationExpr n) {
            n.findAncestor(CallableDeclaration.class)
                    .ifPresent(
                            callable -> {
                                String exceptionType = "com.pubnub.api.PubNubException";
                                boolean alreadyDeclared = callable.getThrownExceptions().stream()
                                        .anyMatch(type -> exceptionType.equals(type.toString()));
                                if (!alreadyDeclared) {
                                    callable.addThrownException(
                                            new ClassOrInterfaceType(null, "com.pubnub.api.PubNubException"));
                                }
                            });
        }

        private boolean isPubNubType(Type type) {
            return isSimpleType(type, "PubNub");
        }

        private boolean isSimpleType(Type type, String expectedSimpleName) {
            return type.isClassOrInterfaceType()
                    && expectedSimpleName.equals(type.asClassOrInterfaceType().getNameAsString());
        }
    }
}
