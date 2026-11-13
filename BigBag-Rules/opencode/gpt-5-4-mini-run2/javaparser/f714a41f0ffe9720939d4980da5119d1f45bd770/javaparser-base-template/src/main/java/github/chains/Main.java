package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
  private static final String OLD_MEMBERSHIP = "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult";
  private static final String NEW_MEMBERSHIP = "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult";
  private static final String OLD_SPACE = "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult";
  private static final String NEW_CHANNEL = "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult";
  private static final String OLD_USER = "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult";
  private static final String NEW_UUID = "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult";
  private static final String OLD_FILE = "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult";
  private static final String NEW_FILE = "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult";

  public static void main(String[] args) {
    Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
    JavaParser parser = new JavaParser(new ParserConfiguration());

    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> transform(parser, path));
    } catch (IOException e) {
      throw new RuntimeException("Failed to scan " + root, e);
    }
  }

  private static void transform(JavaParser parser, Path file) {
    try {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      Optional<CompilationUnit> parsed = parser.parse(source).getResult();
      if (parsed.isEmpty()) {
        return;
      }
      CompilationUnit cu = parsed.get();
      LexicalPreservingPrinter.setup(cu);

      boolean changed = rewriteImports(cu);
      changed |= rewritePnConfiguration(cu);
      changed |= rewriteSubscribeCallbackMethods(cu);

      if (changed) {
        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to transform " + file, e);
    }
  }

  private static boolean rewriteImports(CompilationUnit cu) {
    boolean changed = false;
    changed |= replaceImport(cu, OLD_MEMBERSHIP, NEW_MEMBERSHIP);
    changed |= replaceImport(cu, OLD_SPACE, NEW_CHANNEL);
    changed |= replaceImport(cu, OLD_USER, NEW_UUID);
    changed |= replaceImport(cu, OLD_FILE, NEW_FILE);
    changed |= removeImport(cu, OLD_SPACE);
    changed |= removeImport(cu, OLD_USER);
    return changed;
  }

  private static boolean removeImport(CompilationUnit cu, String name) {
    return cu.getImports().removeIf(i -> i.getNameAsString().equals(name));
  }

  private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
    boolean changed = false;
    for (int i = 0; i < cu.getImports().size(); i++) {
      if (cu.getImports().get(i).getNameAsString().equals(oldName)) {
        cu.getImports().get(i).setName(newName);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rewritePnConfiguration(CompilationUnit cu) {
    boolean changed = false;
    for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
      if (expr.getType().getNameAsString().equals("PNConfiguration") && expr.getArguments().isEmpty()) {
        NodeList<com.github.javaparser.ast.expr.Expression> args = new NodeList<>();
        args.add(StaticJavaParser.parseExpression("new com.pubnub.api.UserId(PubNub.generateUUID())"));
        expr.setArguments(args);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rewriteSubscribeCallbackMethods(CompilationUnit cu) {
    boolean changed = false;
    for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
      String name = method.getNameAsString();
      if (method.getParameters().size() != 2) {
        continue;
      }
      if (!method.getParameter(0).getTypeAsString().endsWith("PubNub")) {
        continue;
      }
      if (name.equals("user") || method.getParameter(1).getTypeAsString().endsWith("PNUserResult")) {
        method.setName("uuid");
        setParamType(method, NEW_UUID);
        changed = true;
      } else if (name.equals("space") || method.getParameter(1).getTypeAsString().endsWith("PNSpaceResult")) {
        method.setName("channel");
        setParamType(method, NEW_CHANNEL);
        changed = true;
      } else if (name.equals("membership") || method.getParameter(1).getTypeAsString().endsWith("PNMembershipResult")) {
        setParamType(method, NEW_MEMBERSHIP);
        changed = true;
      } else if (name.equals("file") || method.getParameter(1).getTypeAsString().endsWith("PNFileEventResult")) {
        setParamType(method, NEW_FILE);
        changed = true;
      }
    }
    return changed;
  }

  private static void setParamType(MethodDeclaration method, String fqcn) {
    Type type = StaticJavaParser.parseType(fqcn);
    method.getParameter(1).setType(type);
  }
}
