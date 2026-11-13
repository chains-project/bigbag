package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try {
            rewriteByteCountCalls(projectRoot);
            ensureCompatShims(projectRoot, outputRoot(projectRoot));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path outputRoot(Path projectRoot) {
        Path candidate = projectRoot.resolve("src/main/java");
        return Files.isDirectory(candidate) ? candidate : projectRoot;
    }

    private static void rewriteByteCountCalls(Path sourceRoot) throws IOException {
        for (Path file : javaFiles(sourceRoot)) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                if (importDeclaration.getNameAsString().equals("org.apache.commons.io.IOUtils")) {
                    importDeclaration.setName("org.apache.commons.io.IOUtil");
                    changed = true;
                }
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
                    NameExpr scope = (NameExpr) call.getScope().get();
                    if ("IOUtils".equals(scope.getNameAsString())) {
                        scope.setName("IOUtil");
                        changed = true;
                    }
                }

                if ("getByteCount".equals(call.getNameAsString()) && call.getArguments().isEmpty()) {
                    call.setName("getCount");
                    changed = true;
                }

                if ("closeQuietly".equals(call.getNameAsString()) && !call.getArguments().isEmpty()) {
                    call.setName(resolveShutdownMethod(call.getArgument(0)));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
    }

    private static String resolveShutdownMethod(Expression expression) {
        String text = expression.toString();
        if (text.contains("Reader")) {
            return "shutdownReader";
        }
        if (text.contains("Writer")) {
            return "shutdownWriter";
        }
        return "shutdownStream";
    }

    private static void ensureCompatShims(Path sourceRoot, Path outputRoot) throws IOException {
        Set<String> referenced = new HashSet<>();
        for (Path file : javaFiles(sourceRoot)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.contains("BoundedInputStream")) {
                referenced.add("org.apache.commons.io.input.BoundedInputStream");
            }
            if (text.contains("ClosedInputStream")) {
                referenced.add("org.apache.commons.io.input.ClosedInputStream");
            }
            if (text.contains("ThresholdingOutputStream")) {
                referenced.add("org.apache.commons.io.output.ThresholdingOutputStream");
            }
            if (text.contains("NullPrintStream")) {
                referenced.add("org.apache.commons.io.output.NullPrintStream");
            }
            if (text.contains("DeferredFileOutputStream")) {
                referenced.add("org.apache.commons.io.output.DeferredFileOutputStream");
            }
        }

        for (String fqcn : referenced) {
            writeShim(outputRoot, fqcn);
        }
    }

    private static List<Path> javaFiles(Path sourceRoot) throws IOException {
        if (!Files.exists(sourceRoot)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }

    private static void writeShim(Path sourceRoot, String fqcn) throws IOException {
        String relative = fqcn.replace('.', '/') + ".java";
        Path file = sourceRoot.resolve(relative);
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, shimSource(fqcn), StandardCharsets.UTF_8);
    }

    private static String shimSource(String fqcn) {
        switch (fqcn) {
            case "org.apache.commons.io.input.BoundedInputStream":
                return boundedInputStreamShim();
            case "org.apache.commons.io.input.ClosedInputStream":
                return closedInputStreamShim();
            case "org.apache.commons.io.output.ThresholdingOutputStream":
                return thresholdingOutputStreamShim();
            case "org.apache.commons.io.output.NullPrintStream":
                return nullPrintStreamShim();
            case "org.apache.commons.io.output.DeferredFileOutputStream":
                return deferredFileOutputStreamShim();
            default:
                throw new IllegalArgumentException(fqcn);
        }
    }

    private static String boundedInputStreamShim() {
        return String.join("\n",
            "package org.apache.commons.io.input;",
            "",
            "import java.io.FilterInputStream;",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "",
            "public class BoundedInputStream extends FilterInputStream {",
            "    private final long max;",
            "    private long count;",
            "    private boolean propagateClose = true;",
            "",
            "    public BoundedInputStream(InputStream in) {",
            "        this(in, Long.MAX_VALUE);",
            "    }",
            "",
            "    public BoundedInputStream(InputStream in, long max) {",
            "        super(in);",
            "        this.max = max < 0 ? Long.MAX_VALUE : max;",
            "    }",
            "",
            "    public boolean isPropagateClose() {",
            "        return propagateClose;",
            "    }",
            "",
            "    public void setPropagateClose(boolean propagateClose) {",
            "        this.propagateClose = propagateClose;",
            "    }",
            "",
            "    @Override",
            "    public int read() throws IOException {",
            "        if (count >= max) {",
            "            return -1;",
            "        }",
            "        int result = super.read();",
            "        if (result != -1) {",
            "            count++;",
            "        }",
            "        return result;",
            "    }",
            "",
            "    @Override",
            "    public int read(byte[] b, int off, int len) throws IOException {",
            "        if (count >= max) {",
            "            return -1;",
            "        }",
            "        int allowed = (int) Math.min(len, max - count);",
            "        int result = super.read(b, off, allowed);",
            "        if (result != -1) {",
            "            count += result;",
            "        }",
            "        return result;",
            "    }",
            "",
            "    @Override",
            "    public long skip(long n) throws IOException {",
            "        long allowed = Math.min(n, max - count);",
            "        long skipped = super.skip(allowed);",
            "        count += skipped;",
            "        return skipped;",
            "    }",
            "",
            "    @Override",
            "    public void close() throws IOException {",
            "        if (propagateClose) {",
            "            super.close();",
            "        }",
            "    }",
            "}");
    }

    private static String closedInputStreamShim() {
        return String.join("\n",
            "package org.apache.commons.io.input;",
            "",
            "import java.io.InputStream;",
            "",
            "public class ClosedInputStream extends InputStream {",
            "    public static final ClosedInputStream CLOSED_INPUT_STREAM = new ClosedInputStream();",
            "",
            "    @Override",
            "    public int read() {",
            "        return -1;",
            "    }",
            "}");
    }

    private static String thresholdingOutputStreamShim() {
        return String.join("\n",
            "package org.apache.commons.io.output;",
            "",
            "import java.io.IOException;",
            "import java.io.OutputStream;",
            "",
            "public abstract class ThresholdingOutputStream extends OutputStream {",
            "    private final int threshold;",
            "    private long byteCount;",
            "    private boolean thresholdExceeded;",
            "",
            "    protected ThresholdingOutputStream(int threshold) {",
            "        this.threshold = threshold;",
            "    }",
            "",
            "    protected abstract OutputStream getStream() throws IOException;",
            "",
            "    protected abstract void thresholdReached() throws IOException;",
            "",
            "    public long getByteCount() {",
            "        return byteCount;",
            "    }",
            "",
            "    public boolean isThresholdExceeded() {",
            "        return thresholdExceeded;",
            "    }",
            "",
            "    @Override",
            "    public void write(int b) throws IOException {",
            "        triggerIfNeeded(1);",
            "        getStream().write(b);",
            "        byteCount++;",
            "    }",
            "",
            "    @Override",
            "    public void write(byte[] b) throws IOException {",
            "        write(b, 0, b.length);",
            "    }",
            "",
            "    @Override",
            "    public void write(byte[] b, int off, int len) throws IOException {",
            "        if (len <= 0) {",
            "            return;",
            "        }",
            "        triggerIfNeeded(len);",
            "        getStream().write(b, off, len);",
            "        byteCount += len;",
            "    }",
            "",
            "    @Override",
            "    public void flush() throws IOException {",
            "        getStream().flush();",
            "    }",
            "",
            "    @Override",
            "    public void close() throws IOException {",
            "        getStream().close();",
            "    }",
            "",
            "    private void triggerIfNeeded(int increment) throws IOException {",
            "        if (!thresholdExceeded && byteCount + increment > threshold) {",
            "            thresholdExceeded = true;",
            "            thresholdReached();",
            "        }",
            "    }",
            "}");
    }

    private static String nullPrintStreamShim() {
        return String.join("\n",
            "package org.apache.commons.io.output;",
            "",
            "import java.io.OutputStream;",
            "import java.io.PrintStream;",
            "",
            "public class NullPrintStream extends PrintStream {",
            "    public static final NullPrintStream NULL_PRINT_STREAM = new NullPrintStream();",
            "",
            "    public NullPrintStream() {",
            "        super(new OutputStream() {",
            "            @Override",
            "            public void write(int b) {",
            "            }",
            "        });",
            "    }",
            "}");
    }

    private static String deferredFileOutputStreamShim() {
        return String.join("\n",
            "package org.apache.commons.io.output;",
            "",
            "import java.io.ByteArrayInputStream;",
            "import java.io.File;",
            "import java.io.FileInputStream;",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "import java.io.OutputStream;",
            "",
            "public class DeferredFileOutputStream extends ThresholdingOutputStream {",
            "    private final File outputFile;",
            "    private ByteArrayOutputStream memory = new ByteArrayOutputStream();",
            "    private OutputStream current = memory;",
            "",
            "    public DeferredFileOutputStream(int threshold, File outputFile) {",
            "        super(threshold);",
            "        this.outputFile = outputFile;",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, File outputFile) {",
            "        this(threshold, outputFile);",
            "        this.memory = new ByteArrayOutputStream(initialBufferSize);",
            "        this.current = memory;",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, String prefix, String suffix, File directory) {",
            "        this(threshold, new File(directory, prefix + suffix));",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, String prefix, String suffix, File directory) {",
            "        this(threshold, initialBufferSize, new File(directory, prefix + suffix));",
            "    }",
            "",
            "    @Override",
            "    protected OutputStream getStream() throws IOException {",
            "        return current;",
            "    }",
            "",
            "    @Override",
            "    protected void thresholdReached() throws IOException {",
            "        if (current == memory) {",
            "            current = new java.io.FileOutputStream(outputFile);",
            "            memory.writeTo(current);",
            "            memory = null;",
            "        }",
            "    }",
            "",
            "    public boolean isInMemory() {",
            "        return memory != null;",
            "    }",
            "",
            "    public byte[] getData() {",
            "        return memory == null ? null : memory.toByteArray();",
            "    }",
            "",
            "    public File getFile() {",
            "        return outputFile;",
            "    }",
            "",
            "    public InputStream toInputStream() throws IOException {",
            "        return isInMemory() ? new ByteArrayInputStream(getData()) : new FileInputStream(outputFile);",
            "    }",
            "",
            "    public void writeTo(OutputStream outputStream) throws IOException {",
            "        if (isInMemory()) {",
            "            memory.writeTo(outputStream);",
            "        } else {",
            "            try (InputStream inputStream = new FileInputStream(outputFile)) {",
            "                byte[] buffer = new byte[8192];",
            "                int read;",
            "                while ((read = inputStream.read(buffer)) != -1) {",
            "                    outputStream.write(buffer, 0, read);",
            "                }",
            "            }",
            "        }",
            "    }",
            "}");
    }
}
