package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();
    private static final List<String[]> CONSTRUCTOR_REWRITES = new ArrayList<>();

    static {
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFastFramedTransport",
                "org.apache.thrift.transport.layered.TFastFramedTransport");
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFramedTransport",
                "org.apache.thrift.transport.layered.TFramedTransport");

        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TFastFramedTransport(",
                "thriftcompat.ThriftCompat.newTFastFramedTransport(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TFramedTransport(",
                "thriftcompat.ThriftCompat.newTFramedTransport(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TIOStreamTransport(",
                "thriftcompat.ThriftCompat.newTIOStreamTransport(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TMemoryInputTransport(",
                "thriftcompat.ThriftCompat.newTMemoryInputTransport(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TBinaryProtocol.Factory()",
                "thriftcompat.ThriftCompat.newTBinaryProtocolFactory()" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TCompactProtocol.Factory()",
                "thriftcompat.ThriftCompat.newTCompactProtocolFactory()" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TSerializer(",
                "thriftcompat.ThriftCompat.newTSerializer(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TDeserializer(",
                "thriftcompat.ThriftCompat.newTDeserializer(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TBinaryProtocol(",
                "thriftcompat.ThriftCompat.newTBinaryProtocol(" });
        CONSTRUCTOR_REWRITES.add(new String[] {
                "new TCompactProtocol(",
                "thriftcompat.ThriftCompat.newTCompactProtocol(" });
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least one argument: <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputDir.toFile());

        for (Path inputRoot : resolveInputRoots(sourceDir)) {
            launcher.addInputResource(inputRoot.toString());
        }

        launcher.buildModel();

        CtModel model = launcher.getModel();
        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteTypeReference(typeReference, launcher);
        }

        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            rewriteImport(ctImport, launcher);
        }

        launcher.prettyprint();
        normalizeWrittenSources(outputDir);
        writeSupportClass(outputDir);
    }

    private static Set<Path> resolveInputRoots(Path sourceDir) {
        Set<Path> roots = new LinkedHashSet<>();
        if (Files.isDirectory(sourceDir) && sourceDir.getFileName() != null
                && "java".equals(sourceDir.getFileName().toString())) {
            roots.add(sourceDir);
            return roots;
        }

        try {
            Files.walk(sourceDir)
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith(Paths.get("src", "main", "java"))
                            || path.endsWith(Paths.get("src", "test", "java")))
                    .forEach(roots::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover source roots under " + sourceDir, e);
        }

        if (roots.isEmpty()) {
            roots.add(sourceDir);
        }
        return roots;
    }

    private static void rewriteTypeReference(CtTypeReference<?> typeReference, Launcher launcher) {
        String qualifiedName = typeReference.getQualifiedName();
        String replacement = TYPE_RENAMES.get(qualifiedName);
        if (replacement != null) {
            typeReference.replace(launcher.getFactory().Type().createReference(replacement));
        }
    }

    private static void rewriteImport(CtImport ctImport, Launcher launcher) {
        if (ctImport.getReference() instanceof CtTypeReference<?> typeReference) {
            String replacement = TYPE_RENAMES.get(typeReference.getQualifiedName());
            if (replacement != null) {
                ctImport.delete();
            }
        }
    }

    private static void normalizeWrittenSources(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to normalize rewritten sources under " + outputDir, e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String rewritten = content;
            for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
                rewritten = rewritten.replace(entry.getKey(), entry.getValue());
            }
            for (String[] rewrite : CONSTRUCTOR_REWRITES) {
                rewritten = rewritten.replace(rewrite[0], rewrite[1]);
            }
            if (!rewritten.equals(content)) {
                Files.writeString(file, rewritten, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static void writeSupportClass(Path outputDir) {
        Path supportDir = outputDir.resolve("thriftcompat");
        Path supportFile = supportDir.resolve("ThriftCompat.java");
        String source = "package thriftcompat;\n"
                + "\n"
                + "import org.apache.thrift.TDeserializer;\n"
                + "import org.apache.thrift.TSerializer;\n"
                + "import org.apache.thrift.protocol.TBinaryProtocol;\n"
                + "import org.apache.thrift.protocol.TCompactProtocol;\n"
                + "import org.apache.thrift.transport.TIOStreamTransport;\n"
                + "import org.apache.thrift.transport.TMemoryInputTransport;\n"
                + "import org.apache.thrift.transport.TTransport;\n"
                + "import org.apache.thrift.transport.layered.TFastFramedTransport;\n"
                + "import org.apache.thrift.transport.layered.TFramedTransport;\n"
                + "\n"
                + "public final class ThriftCompat {\n"
                + "  private ThriftCompat() {}\n"
                + "\n"
                + "  public static TSerializer newTSerializer() { return new TSerializer(); }\n"
                + "\n"
                + "  public static TFastFramedTransport newTFastFramedTransport(TTransport transport) {\n"
                + "    try { return new TFastFramedTransport(transport); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TFastFramedTransport newTFastFramedTransport(TTransport transport, int bufferCapacity) {\n"
                + "    try { return new TFastFramedTransport(transport, bufferCapacity); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TFastFramedTransport newTFastFramedTransport(TTransport transport, int bufferCapacity, int maxFrameSize) {\n"
                + "    try { return new TFastFramedTransport(transport, bufferCapacity, maxFrameSize); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TFramedTransport newTFramedTransport(TTransport transport) {\n"
                + "    try { return new TFramedTransport(transport); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TFramedTransport newTFramedTransport(TTransport transport, int maxFrameSize) {\n"
                + "    try { return new TFramedTransport(transport, maxFrameSize); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TIOStreamTransport newTIOStreamTransport(java.io.InputStream inputStream) {\n"
                + "    try { return new TIOStreamTransport(inputStream); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TIOStreamTransport newTIOStreamTransport(java.io.OutputStream outputStream) {\n"
                + "    try { return new TIOStreamTransport(outputStream); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TIOStreamTransport newTIOStreamTransport(java.io.InputStream inputStream, java.io.OutputStream outputStream) {\n"
                + "    try { return new TIOStreamTransport(inputStream, outputStream); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TMemoryInputTransport newTMemoryInputTransport() {\n"
                + "    try { return new TMemoryInputTransport(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TBinaryProtocol newTBinaryProtocol(TTransport transport) {\n"
                + "    return new TBinaryProtocol(transport);\n"
                + "  }\n"
                + "\n"
                + "  public static TCompactProtocol newTCompactProtocol(TTransport transport) {\n"
                + "    return new TCompactProtocol(transport);\n"
                + "  }\n"
                + "\n"
                + "  public static TBinaryProtocol.Factory newTBinaryProtocolFactory() {\n"
                + "    try { return new TBinaryProtocol.Factory(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TCompactProtocol.Factory newTCompactProtocolFactory() {\n"
                + "    try { return new TCompactProtocol.Factory(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TSerializer newTSerializer(TBinaryProtocol.Factory factory) {\n"
                + "    try { return new TSerializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TSerializer newTSerializer(TCompactProtocol.Factory factory) {\n"
                + "    try { return new TSerializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TDeserializer newTDeserializer(TBinaryProtocol.Factory factory) {\n"
                + "    try { return new TDeserializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TDeserializer newTDeserializer(TCompactProtocol.Factory factory) {\n"
                + "    try { return new TDeserializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TDeserializer newTDeserializer() { return new TDeserializer(); }\n"
                + "\n"
                + "  public static TSerializer newTSerializer(TBinaryProtocol.Factory factory) {\n"
                + "    try { return new TSerializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TSerializer newTSerializer(TCompactProtocol.Factory factory) {\n"
                + "    try { return new TSerializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "\n"
                + "  public static TDeserializer newTDeserializer(TBinaryProtocol.Factory factory) {\n"
                + "    try { return new TDeserializer(factory); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }\n"
                + "  }\n"
                + "}\n";
        try {
            Files.createDirectories(supportDir);
            Files.writeString(supportFile, source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
