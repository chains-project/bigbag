package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {
  private static final String OLD_FRAMED = "org.apache.thrift.transport.TFramedTransport";
  private static final String NEW_FRAMED = "org.apache.thrift.transport.layered.TFramedTransport";
  private static final String OLD_SERIALIZER = "ThreadLocal.withInitial(TSerializer::new)";

  private Main() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
    }

    Path sourceDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args.length > 1 ? args[1] : args[0]);

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(false);
    launcher.getEnvironment().setCommentEnabled(true);
    launcher.addInputResource(sourceDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toString());
    launcher.buildModel();

    Factory factory = launcher.getFactory();
    List<CtTypeReference<?>> references = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
    for (CtTypeReference<?> reference : references) {
      rewriteReference(factory, reference);
    }

    launcher.prettyprint();
    rewriteSourceTree(outputDir);
  }

  private static void rewriteReference(Factory factory, CtTypeReference<?> reference) {
    String qualifiedName = reference.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }

    if (qualifiedName.equals(OLD_FRAMED) || qualifiedName.startsWith(OLD_FRAMED + ".")) {
      String suffix = qualifiedName.substring(OLD_FRAMED.length());
      reference.replace(factory.Type().createReference(NEW_FRAMED + suffix));
    }
  }

  private static void rewriteSourceTree(Path outputDir) {
    try (Stream<Path> paths = Files.walk(outputDir)) {
      paths.filter(path -> path.toString().endsWith(".java"))
          .forEach(Main::rewriteJavaFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void rewriteJavaFile(Path path) {
    try {
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      String updated = content
          .replace("import " + OLD_FRAMED + ";", "import " + NEW_FRAMED + ";")
          .replace(OLD_FRAMED, NEW_FRAMED)
          .replace(OLD_SERIALIZER,
              "ThreadLocal.withInitial(() -> { try { return new TSerializer(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); } })");

      if (needsGetMinSerializedSize(updated)) {
        updated = insertGetMinSerializedSize(updated);
      }

      if (!updated.equals(content)) {
        Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean needsGetMinSerializedSize(String content) {
    return content.contains("extends TProtocol") || content.contains("extends org.apache.thrift.protocol.TProtocol")
        || content.contains("extends TProtocolDecorator");
  }

  private static String insertGetMinSerializedSize(String content) {
    if (content.contains("getMinSerializedSize(byte type)")) {
      return content;
    }

    int insertAt = content.lastIndexOf('}');
    if (insertAt < 0) {
      return content;
    }

    String method = "\n    @Override\n    public int getMinSerializedSize(byte type) throws org.apache.thrift.TException {\n        switch (type) {\n            case 0:\n            case 1:\n                return 0;\n            case 2:\n            case 3:\n                return 1;\n            case 4:\n                return 8;\n            case 6:\n                return 2;\n            case 8:\n                return 4;\n            case 10:\n                return 8;\n            case 11:\n                return 4;\n            case 12:\n                return 0;\n            case 13:\n            case 14:\n            case 15:\n                return 4;\n            default:\n                throw new org.apache.thrift.transport.TTransportException(0, \"unrecognized type code\");\n        }\n    }\n";
    return content.substring(0, insertAt) + method + content.substring(insertAt);
  }
}
