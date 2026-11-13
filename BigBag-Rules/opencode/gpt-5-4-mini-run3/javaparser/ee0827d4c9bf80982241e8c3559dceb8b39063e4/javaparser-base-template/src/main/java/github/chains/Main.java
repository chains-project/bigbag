package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());

        ensureCompatibilityClasses(root);
        transformJavaFiles(parser, root);
    }

    private static void transformJavaFiles(JavaParser parser, Path root) throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            javaFiles = stream
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            ParseResult<CompilationUnit> result = parser.parse(file);
            Optional<CompilationUnit> maybeCu = result.getResult();
            if (!maybeCu.isPresent()) {
                continue;
            }

            CompilationUnit cu = maybeCu.get();
            LexicalPreservingPrinter.setup(cu);
            boolean changed = applyTransformations(cu);
            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static boolean applyTransformations(CompilationUnit cu) {
        boolean changed = false;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("getByteCount".equals(call.getNameAsString())) {
                call.setName("getCount");
                changed = true;
            }
        }

        return changed;
    }

    private static void ensureCompatibilityClasses(Path root) throws IOException {
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/input/ClosedInputStream.java"), closedInputStreamSource());
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/input/BoundedInputStream.java"), boundedInputStreamSource());
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/IOUtils.java"), ioUtilsSource());
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/output/NullPrintStream.java"), nullPrintStreamSource());
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/output/DeferredFileOutputStream.java"), deferredFileOutputStreamSource());
        writeIfMissing(root.resolve("src/main/java/org/apache/commons/io/output/ThresholdingOutputStream.java"), thresholdingOutputStreamSource());
    }

    private static void writeIfMissing(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String closedInputStreamSource() {
        return String.join("\n",
            "package org.apache.commons.io.input;",
            "",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "",
            "public class ClosedInputStream extends InputStream {",
            "    public static final ClosedInputStream CLOSED_INPUT_STREAM = new ClosedInputStream();",
            "",
            "    public ClosedInputStream() {",
            "    }",
            "",
            "    @Override",
            "    public int read() throws IOException {",
            "        return -1;",
            "    }",
            "}",
            "");
    }

    private static String boundedInputStreamSource() {
        return String.join("\n",
            "package org.apache.commons.io.input;",
            "",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "",
            "public class BoundedInputStream extends InputStream {",
            "    private final InputStream in;",
            "    private final long size;",
            "    private long pos;",
            "    private boolean propagateClose = true;",
            "",
            "    public BoundedInputStream(InputStream in) {",
            "        this(in, -1L);",
            "    }",
            "",
            "    public BoundedInputStream(InputStream in, long size) {",
            "        this.in = in;",
            "        this.size = size;",
            "    }",
            "",
            "    @Override",
            "    public int available() throws IOException {",
            "        if (size >= 0 && pos >= size) {",
            "            return 0;",
            "        }",
            "        return in.available();",
            "    }",
            "",
            "    @Override",
            "    public void close() throws IOException {",
            "        if (propagateClose) {",
            "            in.close();",
            "        }",
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
            "    public synchronized void mark(int readlimit) {",
            "        in.mark(readlimit);",
            "    }",
            "",
            "    @Override",
            "    public boolean markSupported() {",
            "        return in.markSupported();",
            "    }",
            "",
            "    @Override",
            "    public int read() throws IOException {",
            "        if (size >= 0 && pos >= size) {",
            "            return -1;",
            "        }",
            "        int value = in.read();",
            "        if (value != -1) {",
            "            pos++;",
            "        }",
            "        return value;",
            "    }",
            "",
            "    @Override",
            "    public int read(byte[] b) throws IOException {",
            "        return read(b, 0, b.length);",
            "    }",
            "",
            "    @Override",
            "    public int read(byte[] b, int off, int len) throws IOException {",
            "        if (size >= 0) {",
            "            long remaining = size - pos;",
            "            if (remaining <= 0) {",
            "                return -1;",
            "            }",
            "            len = (int) Math.min(len, remaining);",
            "        }",
            "        int read = in.read(b, off, len);",
            "        if (read != -1) {",
            "            pos += read;",
            "        }",
            "        return read;",
            "    }",
            "",
            "    @Override",
            "    public synchronized void reset() throws IOException {",
            "        in.reset();",
            "        pos = 0L;",
            "    }",
            "",
            "    @Override",
            "    public long skip(long n) throws IOException {",
            "        if (size >= 0) {",
            "            long remaining = size - pos;",
            "            if (remaining <= 0) {",
            "                return 0L;",
            "            }",
            "            n = Math.min(n, remaining);",
            "        }",
            "        long skipped = in.skip(n);",
            "        pos += skipped;",
            "        return skipped;",
            "    }",
            "",
            "    @Override",
            "    public String toString() {",
            "        return in.toString();",
            "    }",
            "}",
            "");
    }

    private static String ioUtilsSource() {
        return String.join("\n",
            "package org.apache.commons.io;",
            "",
            "import java.io.Closeable;",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "import java.io.OutputStream;",
            "import java.io.Reader;",
            "import java.io.Writer;",
            "",
            "public final class IOUtils {",
            "    private IOUtils() {",
            "    }",
            "",
            "    public static void closeQuietly(Closeable closeable) {",
            "        if (closeable != null) {",
            "            try {",
            "                closeable.close();",
            "            } catch (IOException ignored) {",
            "            }",
            "        }",
            "    }",
            "",
            "    public static void closeQuietly(InputStream input) {",
            "        closeQuietly((Closeable) input);",
            "    }",
            "",
            "    public static void closeQuietly(OutputStream output) {",
            "        closeQuietly((Closeable) output);",
            "    }",
            "",
            "    public static void closeQuietly(Reader reader) {",
            "        closeQuietly((Closeable) reader);",
            "    }",
            "",
            "    public static void closeQuietly(Writer writer) {",
            "        closeQuietly((Closeable) writer);",
            "    }",
            "",
            "    public static int copy(InputStream input, OutputStream output) throws IOException {",
            "        byte[] buffer = new byte[8192];",
            "        int count = 0;",
            "        int n;",
            "        while ((n = input.read(buffer)) != -1) {",
            "            output.write(buffer, 0, n);",
            "            count += n;",
            "        }",
            "        return count;",
            "    }",
            "",
            "    public static byte[] toByteArray(InputStream input) throws IOException {",
            "        return IOUtil.toByteArray(input);",
            "    }",
            "",
            "    public static String toString(InputStream input, String encoding) throws IOException {",
            "        return IOUtil.toString(input, encoding);",
            "    }",
            "",
            "    public static String toString(InputStream input) throws IOException {",
            "        return IOUtil.toString(input);",
            "    }",
            "",
            "    public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException {",
            "        return IOUtil.contentEquals(input1, input2);",
            "    }",
            "}",
            "");
    }

    private static String nullPrintStreamSource() {
        return String.join("\n",
            "package org.apache.commons.io.output;",
            "",
            "import java.io.ByteArrayOutputStream;",
            "import java.io.PrintStream;",
            "",
            "public class NullPrintStream extends PrintStream {",
            "    public static final NullPrintStream NULL_PRINT_STREAM = new NullPrintStream();",
            "",
            "    public NullPrintStream() {",
            "        super(new ByteArrayOutputStream());",
            "    }",
            "}",
            "");
    }

    private static String deferredFileOutputStreamSource() {
        return String.join("\n",
            "package org.apache.commons.io.output;",
            "",
            "import java.io.ByteArrayInputStream;",
            "import java.io.ByteArrayOutputStream;",
            "import java.io.File;",
            "import java.io.FileOutputStream;",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "import java.io.OutputStream;",
            "import java.nio.file.Files;",
            "",
            "public class DeferredFileOutputStream extends ThresholdingOutputStream {",
            "    private final int initialBufferSize;",
            "    private final String prefix;",
            "    private final String suffix;",
            "    private final File directory;",
            "    private final File outputFile;",
            "    private ByteArrayOutputStream memoryOutputStream;",
            "    private OutputStream currentOutputStream;",
            "    private boolean closed;",
            "",
            "    public DeferredFileOutputStream(int threshold, File outputFile) {",
            "        this(threshold, 32, null, null, outputFile == null ? null : outputFile.getParentFile());",
            "        this.currentOutputStream = memoryOutputStream;",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, File outputFile) {",
            "        this(threshold, initialBufferSize, null, null, outputFile == null ? null : outputFile.getParentFile());",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, String prefix, String suffix, File directory) {",
            "        this(threshold, 32, prefix, suffix, directory);",
            "    }",
            "",
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, String prefix, String suffix, File directory) {",
            "        super(threshold);",
            "        this.initialBufferSize = initialBufferSize;",
            "        this.prefix = prefix;",
            "        this.suffix = suffix;",
            "        this.directory = directory;",
            "        this.outputFile = prefix == null && suffix == null && directory != null ? new File(directory, \"deferred.tmp\") : null;",
            "        this.memoryOutputStream = new ByteArrayOutputStream(initialBufferSize);",
            "        this.currentOutputStream = memoryOutputStream;",
            "    }",
            "",
            "    @Override",
            "    protected OutputStream getStream() {",
            "        return currentOutputStream;",
            "    }",
            "",
            "    @Override",
            "    protected void thresholdReached() throws IOException {",
            "        File file = outputFile;",
            "        if (file == null) {",
            "            file = Files.createTempFile(prefix == null ? \"deferred\" : prefix, suffix == null ? \".tmp\" : suffix).toFile();",
            "        }",
            "        currentOutputStream = new FileOutputStream(file);",
            "        memoryOutputStream.writeTo(currentOutputStream);",
            "        memoryOutputStream = null;",
            "    }",
            "",
            "    @Override",
            "    public void close() throws IOException {",
            "        super.close();",
            "        closed = true;",
            "    }",
            "",
            "    public byte[] getData() {",
            "        return memoryOutputStream != null ? memoryOutputStream.toByteArray() : null;",
            "    }",
            "",
            "    public File getFile() {",
            "        return outputFile;",
            "    }",
            "",
            "    public boolean isInMemory() {",
            "        return memoryOutputStream != null;",
            "    }",
            "",
            "    public InputStream toInputStream() {",
            "        return new ByteArrayInputStream(getData() == null ? new byte[0] : getData());",
            "    }",
            "",
            "    public void writeTo(OutputStream outputStream) throws IOException {",
            "        if (memoryOutputStream != null) {",
            "            memoryOutputStream.writeTo(outputStream);",
            "        } else if (outputFile != null) {",
            "            Files.copy(outputFile.toPath(), outputStream);",
            "        }",
            "    }",
            "}",
            "");
    }

    private static String thresholdingOutputStreamSource() {
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
            "    protected void thresholdReached() throws IOException {",
            "    }",
            "",
            "    protected void checkThreshold(int count) throws IOException {",
            "        if (!thresholdExceeded && byteCount + count > threshold) {",
            "            thresholdExceeded = true;",
            "            thresholdReached();",
            "        }",
            "    }",
            "",
            "    public long getByteCount() {",
            "        return byteCount;",
            "    }",
            "",
            "    protected void setByteCount(long count) {",
            "        byteCount = count;",
            "    }",
            "",
            "    protected void resetByteCount() {",
            "        byteCount = 0L;",
            "        thresholdExceeded = false;",
            "    }",
            "",
            "    public int getThreshold() {",
            "        return threshold;",
            "    }",
            "",
            "    public boolean isThresholdExceeded() {",
            "        return thresholdExceeded;",
            "    }",
            "",
            "    @Override",
            "    public void write(int b) throws IOException {",
            "        checkThreshold(1);",
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
            "        checkThreshold(len);",
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
            "}",
            "");
    }
}
