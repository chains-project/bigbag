package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String COUNTING_INPUT_STREAM = "org.apache.commons.io.input.CountingInputStream";
    private static final String BOUNDED_INPUT_STREAM = "org.apache.commons.io.input.BoundedInputStream";
    private static final String CLOSED_INPUT_STREAM = "org.apache.commons.io.input.ClosedInputStream";
    private static final String IOUTILS = "org.apache.commons.io.IOUtils";
    private static final String DEFERRED_FILE_OUTPUT_STREAM = "org.apache.commons.io.output.DeferredFileOutputStream";
    private static final String THRESHOLDING_OUTPUT_STREAM = "org.apache.commons.io.output.ThresholdingOutputStream";
    private static final String NULL_PRINT_STREAM = "org.apache.commons.io.output.NullPrintStream";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> sourceRoots = resolveSourceRoots(input);

        boolean needsBoundedInputStream = false;
        boolean needsThresholdingOutputStream = false;
        boolean needsNullPrintStream = false;

        for (Path root : sourceRoots) {
            List<Path> javaFiles = listJavaFiles(root);
            for (Path file : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    boolean changed = false;

                    if (renameCountingInputStreamMethod(cu)) {
                        changed = true;
                    }

                    if (referencesType(cu, BOUNDED_INPUT_STREAM)) {
                        needsBoundedInputStream = true;
                    }
                    if (referencesType(cu, CLOSED_INPUT_STREAM)) {
                        needsBoundedInputStream = true;
                    }
                    if (referencesType(cu, IOUTILS)) {
                        needsBoundedInputStream = true;
                    }
                    if (referencesType(cu, DEFERRED_FILE_OUTPUT_STREAM)) {
                        needsThresholdingOutputStream = true;
                    }
                    if (referencesType(cu, THRESHOLDING_OUTPUT_STREAM)) {
                        needsThresholdingOutputStream = true;
                    }
                    if (referencesType(cu, NULL_PRINT_STREAM)) {
                        needsNullPrintStream = true;
                    }

                    if (changed) {
                        Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException("Failed to process " + file, e);
                }
            }
        }

        if (!sourceRoots.isEmpty()) {
            Path shimRoot = sourceRoots.get(0);
            if (needsBoundedInputStream) {
                writeShim(shimRoot, "org.apache.commons.io.input", "BoundedInputStream", boundedInputStreamSource());
                writeShim(shimRoot, "org.apache.commons.io.input", "ClosedInputStream", closedInputStreamSource());
                writeShim(shimRoot, "org.apache.commons.io", "IOUtils", ioUtilsSource());
            }
            if (needsThresholdingOutputStream) {
                writeShim(shimRoot, "org.apache.commons.io.output", "ThresholdingOutputStream", thresholdingOutputStreamSource());
                writeShim(shimRoot, "org.apache.commons.io.output", "DeferredFileOutputStream", deferredFileOutputStreamSource());
            }
            if (needsNullPrintStream) {
                writeShim(shimRoot, "org.apache.commons.io.output", "NullPrintStream", nullPrintStreamSource());
            }
        }
    }

    private static List<Path> resolveSourceRoots(Path input) {
        List<Path> roots = new ArrayList<>();
        Path main = input.resolve("src/main/java");
        Path test = input.resolve("src/test/java");

        if (Files.isDirectory(main)) {
            roots.add(main);
        }
        if (Files.isDirectory(test)) {
            roots.add(test);
        }

        if (roots.isEmpty() && Files.isDirectory(input)) {
            roots.add(input);
        }

        return roots;
    }

    private static List<Path> listJavaFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to list Java files under " + root, e);
        }
    }

    private static boolean renameCountingInputStreamMethod(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("getByteCount") && call.getArguments().isEmpty()) {
                call.setName("getCount");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean referencesType(CompilationUnit cu, String fqcn) {
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        if (cu.getImports().stream().anyMatch(imp -> !imp.isAsterisk() && imp.getNameAsString().equals(fqcn))) {
            return true;
        }
        if (cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(t -> t.getNameAsString().equals(simpleName))) {
            return true;
        }
        return cu.findAll(ObjectCreationExpr.class).stream().anyMatch(t -> t.getType().getNameAsString().equals(simpleName));
    }

    private static void writeShim(Path sourceRoot, String packageName, String className, String content) {
        Path dir = sourceRoot.resolve(packageName.replace('.', '/'));
        Path file = dir.resolve(className + ".java");

        try {
            Files.createDirectories(dir);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to write shim " + file, e);
        }
    }

    private static String boundedInputStreamSource() {
        return "package org.apache.commons.io.input;\n"
            + "\n"
            + "import java.io.FilterInputStream;\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "\n"
            + "public class BoundedInputStream extends FilterInputStream {\n"
            + "    private long remaining;\n"
            + "    private long markRemaining = -1;\n"
            + "\n"
            + "    public BoundedInputStream(InputStream in) {\n"
            + "        this(in, Long.MAX_VALUE);\n"
            + "    }\n"
            + "\n"
            + "    public BoundedInputStream(InputStream in, long size) {\n"
            + "        super(in);\n"
            + "        this.remaining = size < 0 ? Long.MAX_VALUE : size;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int read() throws IOException {\n"
            + "        if (remaining <= 0) {\n"
            + "            return -1;\n"
            + "        }\n"
            + "        int value = super.read();\n"
            + "        if (value != -1) {\n"
            + "            remaining--;\n"
            + "        }\n"
            + "        return value;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int read(byte[] b, int off, int len) throws IOException {\n"
            + "        if (remaining <= 0) {\n"
            + "            return -1;\n"
            + "        }\n"
            + "        int requested = (int) Math.min(len, remaining);\n"
            + "        int count = super.read(b, off, requested);\n"
            + "        if (count != -1) {\n"
            + "            remaining -= count;\n"
            + "        }\n"
            + "        return count;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public long skip(long n) throws IOException {\n"
            + "        long requested = Math.min(n, remaining);\n"
            + "        long skipped = super.skip(requested);\n"
            + "        remaining -= skipped;\n"
            + "        return skipped;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int available() throws IOException {\n"
            + "        return (int) Math.min(super.available(), remaining);\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public synchronized void mark(int readlimit) {\n"
            + "        if (super.markSupported()) {\n"
            + "            super.mark(readlimit);\n"
            + "            markRemaining = remaining;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public synchronized void reset() throws IOException {\n"
            + "        super.reset();\n"
            + "        if (markRemaining >= 0) {\n"
            + "            remaining = markRemaining;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    private static String closedInputStreamSource() {
        return "package org.apache.commons.io.input;\n"
            + "\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "\n"
            + "public class ClosedInputStream extends InputStream {\n"
            + "    public static final ClosedInputStream CLOSED_INPUT_STREAM = new ClosedInputStream();\n"
            + "\n"
            + "    @Override\n"
            + "    public int read() throws IOException {\n"
            + "        return -1;\n"
            + "    }\n"
            + "}\n";
    }

    private static String thresholdingOutputStreamSource() {
        return "package org.apache.commons.io.output;\n"
            + "\n"
            + "import java.io.IOException;\n"
            + "import java.io.OutputStream;\n"
            + "\n"
            + "public abstract class ThresholdingOutputStream extends OutputStream {\n"
            + "    private final int threshold;\n"
            + "    private long byteCount;\n"
            + "    private boolean thresholdExceeded;\n"
            + "\n"
            + "    protected ThresholdingOutputStream(int threshold) {\n"
            + "        if (threshold < 0) {\n"
            + "            throw new IllegalArgumentException(\"threshold must be non-negative\");\n"
            + "        }\n"
            + "        this.threshold = threshold;\n"
            + "    }\n"
            + "\n"
            + "    protected abstract OutputStream getStream() throws IOException;\n"
            + "\n"
            + "    protected abstract void thresholdReached() throws IOException;\n"
            + "\n"
            + "    protected void checkThreshold(int count) throws IOException {\n"
            + "        if (!thresholdExceeded && byteCount + count > threshold) {\n"
            + "            thresholdExceeded = true;\n"
            + "            thresholdReached();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void write(int b) throws IOException {\n"
            + "        checkThreshold(1);\n"
            + "        getStream().write(b);\n"
            + "        byteCount++;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void write(byte[] b) throws IOException {\n"
            + "        write(b, 0, b.length);\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void write(byte[] b, int off, int len) throws IOException {\n"
            + "        checkThreshold(len);\n"
            + "        getStream().write(b, off, len);\n"
            + "        byteCount += len;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void flush() throws IOException {\n"
            + "        getStream().flush();\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void close() throws IOException {\n"
            + "        getStream().close();\n"
            + "    }\n"
            + "\n"
            + "    public long getByteCount() {\n"
            + "        return byteCount;\n"
            + "    }\n"
            + "\n"
            + "    public int getThreshold() {\n"
            + "        return threshold;\n"
            + "    }\n"
            + "\n"
            + "    public boolean isThresholdExceeded() {\n"
            + "        return thresholdExceeded;\n"
            + "    }\n"
            + "\n"
            + "    protected void resetByteCount() {\n"
            + "        byteCount = 0L;\n"
            + "        thresholdExceeded = false;\n"
            + "    }\n"
            + "\n"
            + "    protected void setByteCount(long count) {\n"
            + "        byteCount = count;\n"
            + "    }\n"
            + "}\n";
    }

    private static String nullPrintStreamSource() {
        return "package org.apache.commons.io.output;\n"
            + "\n"
            + "import java.io.OutputStream;\n"
            + "import java.io.PrintStream;\n"
            + "\n"
            + "public class NullPrintStream extends PrintStream {\n"
            + "    public static final NullPrintStream NULL_PRINT_STREAM = new NullPrintStream();\n"
            + "\n"
            + "    public NullPrintStream() {\n"
            + "        super(new OutputStream() {\n"
            + "            @Override\n"
            + "            public void write(int b) {\n"
            + "            }\n"
            + "        });\n"
            + "    }\n"
            + "}\n";
    }

    private static String deferredFileOutputStreamSource() {
        return "package org.apache.commons.io.output;\n"
            + "\n"
            + "import java.io.ByteArrayInputStream;\n"
            + "import java.io.ByteArrayOutputStream;\n"
            + "import java.io.File;\n"
            + "import java.io.FileInputStream;\n"
            + "import java.io.FileOutputStream;\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "import java.io.OutputStream;\n"
            + "\n"
            + "public class DeferredFileOutputStream extends ThresholdingOutputStream {\n"
            + "    private final int initialBufferSize;\n"
            + "    private final File outputFile;\n"
            + "    private ByteArrayOutputStream memoryOutputStream;\n"
            + "    private OutputStream currentOutputStream;\n"
            + "    private boolean closed;\n"
            + "\n"
            + "    public DeferredFileOutputStream(int threshold, File outputFile) {\n"
            + "        this(threshold, threshold, outputFile);\n"
            + "    }\n"
            + "\n"
            + "    public DeferredFileOutputStream(int threshold, int initialBufferSize, File outputFile) {\n"
            + "        super(threshold);\n"
            + "        this.initialBufferSize = initialBufferSize;\n"
            + "        this.outputFile = outputFile;\n"
            + "        this.memoryOutputStream = new ByteArrayOutputStream(initialBufferSize);\n"
            + "        this.currentOutputStream = memoryOutputStream;\n"
            + "    }\n"
            + "\n"
            + "    public DeferredFileOutputStream(int threshold, int initialBufferSize, String prefix, String suffix, File directory) {\n"
            + "        this(threshold, initialBufferSize, new File(directory, prefix + \".tmp\" + suffix));\n"
            + "    }\n"
            + "\n"
            + "    public DeferredFileOutputStream(int threshold, String prefix, String suffix, File directory) {\n"
            + "        this(threshold, threshold, prefix, suffix, directory);\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    protected OutputStream getStream() throws IOException {\n"
            + "        return currentOutputStream;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    protected void thresholdReached() throws IOException {\n"
            + "        if (memoryOutputStream == null) {\n"
            + "            return;\n"
            + "        }\n"
            + "        File parent = outputFile.getParentFile();\n"
            + "        if (parent != null) {\n"
            + "            parent.mkdirs();\n"
            + "        }\n"
            + "        FileOutputStream fileOut = new FileOutputStream(outputFile);\n"
            + "        memoryOutputStream.writeTo(fileOut);\n"
            + "        currentOutputStream = fileOut;\n"
            + "        memoryOutputStream = null;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public void close() throws IOException {\n"
            + "        super.close();\n"
            + "        closed = true;\n"
            + "        if (currentOutputStream != null) {\n"
            + "            currentOutputStream.close();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public byte[] getData() {\n"
            + "        return memoryOutputStream != null ? memoryOutputStream.toByteArray() : null;\n"
            + "    }\n"
            + "\n"
            + "    public File getFile() {\n"
            + "        return outputFile;\n"
            + "    }\n"
            + "\n"
            + "    public boolean isInMemory() {\n"
            + "        return memoryOutputStream != null;\n"
            + "    }\n"
            + "\n"
            + "    public InputStream toInputStream() throws IOException {\n"
            + "        if (memoryOutputStream != null) {\n"
            + "            return new ByteArrayInputStream(memoryOutputStream.toByteArray());\n"
            + "        }\n"
            + "        return new FileInputStream(outputFile);\n"
            + "    }\n"
            + "\n"
            + "    public void writeTo(OutputStream outputStream) throws IOException {\n"
            + "        try (InputStream in = toInputStream()) {\n"
            + "            byte[] buffer = new byte[8192];\n"
            + "            int n;\n"
            + "            while ((n = in.read(buffer)) != -1) {\n"
            + "                outputStream.write(buffer, 0, n);\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    private static String ioUtilsSource() {
        return "package org.apache.commons.io;\n"
            + "\n"
            + "import java.io.Closeable;\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStreamReader;\n"
            + "import java.io.InputStream;\n"
            + "import java.io.OutputStream;\n"
            + "import java.io.Reader;\n"
            + "import java.io.Writer;\n"
            + "import java.nio.charset.Charset;\n"
            + "import java.nio.charset.StandardCharsets;\n"
            + "\n"
            + "public final class IOUtils {\n"
            + "    private IOUtils() {\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, OutputStream output) throws IOException {\n"
            + "        byte[] buffer = new byte[8192];\n"
            + "        int n;\n"
            + "        int total = 0;\n"
            + "        while ((n = input.read(buffer)) != -1) {\n"
            + "            output.write(buffer, 0, n);\n"
            + "            total += n;\n"
            + "        }\n"
            + "        return total;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, OutputStream output, int bufferSize) throws IOException {\n"
            + "        byte[] buffer = new byte[bufferSize];\n"
            + "        int n;\n"
            + "        int total = 0;\n"
            + "        while ((n = input.read(buffer)) != -1) {\n"
            + "            output.write(buffer, 0, n);\n"
            + "            total += n;\n"
            + "        }\n"
            + "        return total;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, Writer output) throws IOException {\n"
            + "        return copy(input, output, StandardCharsets.UTF_8.name());\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, Writer output, int bufferSize) throws IOException {\n"
            + "        return copy(input, output, StandardCharsets.UTF_8.name(), bufferSize);\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, Writer output, String encoding) throws IOException {\n"
            + "        return copy(input, output, encoding, 8192);\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(InputStream input, Writer output, String encoding, int bufferSize) throws IOException {\n"
            + "        char[] buffer = new char[bufferSize];\n"
            + "        int total = 0;\n"
            + "        try (Reader reader = new InputStreamReader(input, Charset.forName(encoding))) {\n"
            + "            int n;\n"
            + "            while ((n = reader.read(buffer)) != -1) {\n"
            + "                output.write(buffer, 0, n);\n"
            + "                total += n;\n"
            + "            }\n"
            + "        }\n"
            + "        return total;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, OutputStream output) throws IOException {\n"
            + "        output.write(input);\n"
            + "        return input.length;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, OutputStream output, int offset) throws IOException {\n"
            + "        output.write(input, 0, offset);\n"
            + "        return offset;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, Writer output) throws IOException {\n"
            + "        output.write(new String(input, StandardCharsets.UTF_8));\n"
            + "        return input.length;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, Writer output, int bufferSize) throws IOException {\n"
            + "        output.write(new String(input, StandardCharsets.UTF_8));\n"
            + "        return input.length;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, Writer output, String encoding) throws IOException {\n"
            + "        output.write(new String(input, Charset.forName(encoding)));\n"
            + "        return input.length;\n"
            + "    }\n"
            + "\n"
            + "    public static int copy(byte[] input, Writer output, String encoding, int bufferSize) throws IOException {\n"
            + "        output.write(new String(input, Charset.forName(encoding)));\n"
            + "        return input.length;\n"
            + "    }\n"
            + "\n"
            + "    public static byte[] toByteArray(InputStream input) throws IOException {\n"
            + "        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();\n"
            + "        copy(input, output);\n"
            + "        return output.toByteArray();\n"
            + "    }\n"
            + "\n"
            + "    public static String toString(InputStream input, String encoding) throws IOException {\n"
            + "        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();\n"
            + "        copy(input, output);\n"
            + "        return new String(output.toByteArray(), Charset.forName(encoding));\n"
            + "    }\n"
            + "\n"
            + "    public static String toString(InputStream input) throws IOException {\n"
            + "        return toString(input, StandardCharsets.UTF_8.name());\n"
            + "    }\n"
            + "\n"
            + "    public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException {\n"
            + "        byte[] buffer1 = new byte[8192];\n"
            + "        byte[] buffer2 = new byte[8192];\n"
            + "        while (true) {\n"
            + "            int n1 = input1.read(buffer1);\n"
            + "            int n2 = input2.read(buffer2);\n"
            + "            if (n1 != n2) {\n"
            + "                return false;\n"
            + "            }\n"
            + "            if (n1 == -1) {\n"
            + "                return true;\n"
            + "            }\n"
            + "            for (int i = 0; i < n1; i++) {\n"
            + "                if (buffer1[i] != buffer2[i]) {\n"
            + "                    return false;\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static void closeQuietly(Closeable closeable) {\n"
            + "        if (closeable != null) {\n"
            + "            try {\n"
            + "                closeable.close();\n"
            + "            } catch (IOException ignored) {\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static void closeQuietly(InputStream input) {\n"
            + "        closeQuietly((Closeable) input);\n"
            + "    }\n"
            + "\n"
            + "    public static void closeQuietly(OutputStream output) {\n"
            + "        closeQuietly((Closeable) output);\n"
            + "    }\n"
            + "\n"
            + "    public static void closeQuietly(Reader reader) {\n"
            + "        closeQuietly((Closeable) reader);\n"
            + "    }\n"
            + "\n"
            + "    public static void closeQuietly(Writer writer) {\n"
            + "        closeQuietly((Closeable) writer);\n"
            + "    }\n"
            + "}\n";
    }
}
