package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Path sourceDir = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();

        rewriteCountingInvocations(launcher);
        writeCompatibilityShims(outputDir);

        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.prettyprint();
    }

    private static void rewriteCountingInvocations(Launcher launcher) {
        List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                continue;
            }

            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType == null) {
                continue;
            }

            String declaringName = declaringType.getQualifiedName();
            String methodName = executable.getSimpleName();
            if (isCountingType(declaringName) && "getByteCount".equals(methodName)) {
                executable.setSimpleName("getCount");
            } else if (isCountingType(declaringName) && "resetByteCount".equals(methodName)) {
                executable.setSimpleName("resetCount");
            }
        }
    }

    private static boolean isCountingType(String typeName) {
        return "org.apache.commons.io.input.CountingInputStream".equals(typeName)
            || "org.apache.commons.io.output.CountingOutputStream".equals(typeName);
    }

    private static void writeCompatibilityShims(Path outputDir) throws IOException {
        writeJava(outputDir, "org/apache/commons/io/input/ClosedInputStream.java", closedInputStream());
        writeJava(outputDir, "org/apache/commons/io/input/BoundedInputStream.java", boundedInputStream());
        writeJava(outputDir, "org/apache/commons/io/IOUtils.java", ioUtils());
        writeJava(outputDir, "org/apache/commons/io/output/DeferredFileOutputStream.java", deferredFileOutputStream());
        writeJava(outputDir, "org/apache/commons/io/output/ThresholdingOutputStream.java", thresholdingOutputStream());
        writeJava(outputDir, "org/apache/commons/io/output/NullPrintStream.java", nullPrintStream());
    }

    private static void writeJava(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String closedInputStream() {
        return "package org.apache.commons.io.input;\n" +
            "\n" +
            "import java.io.IOException;\n" +
            "import java.io.InputStream;\n" +
            "\n" +
            "public class ClosedInputStream extends InputStream {\n" +
            "    public static final ClosedInputStream CLOSED_INPUT_STREAM = new ClosedInputStream();\n" +
            "\n" +
            "    @Override\n" +
            "    public int read() throws IOException {\n" +
            "        return -1;\n" +
            "    }\n" +
            "}\n";
    }

    private static String boundedInputStream() {
        return "package org.apache.commons.io.input;\n" +
            "\n" +
            "import java.io.FilterInputStream;\n" +
            "import java.io.IOException;\n" +
            "import java.io.InputStream;\n" +
            "\n" +
            "public class BoundedInputStream extends FilterInputStream {\n" +
            "    private final long size;\n" +
            "    private long count;\n" +
            "    private boolean propagateClose = true;\n" +
            "\n" +
            "    public BoundedInputStream(InputStream in) {\n" +
            "        this(in, Long.MAX_VALUE);\n" +
            "    }\n" +
            "\n" +
            "    public BoundedInputStream(InputStream in, long size) {\n" +
            "        super(in);\n" +
            "        this.size = size < 0 ? Long.MAX_VALUE : size;\n" +
            "    }\n" +
            "\n" +
            "    public boolean isPropagateClose() {\n" +
            "        return propagateClose;\n" +
            "    }\n" +
            "\n" +
            "    public void setPropagateClose(boolean propagateClose) {\n" +
            "        this.propagateClose = propagateClose;\n" +
            "    }\n" +
            "\n" +
            "    private long remaining() {\n" +
            "        return size == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, size - count);\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public int read() throws IOException {\n" +
            "        if (remaining() == 0) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        int value = super.read();\n" +
            "        if (value != -1) {\n" +
            "            count++;\n" +
            "        }\n" +
            "        return value;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public int read(byte[] b, int off, int len) throws IOException {\n" +
            "        long remaining = remaining();\n" +
            "        if (remaining == 0) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        int toRead = len;\n" +
            "        if (remaining != Long.MAX_VALUE && remaining < toRead) {\n" +
            "            toRead = (int) remaining;\n" +
            "        }\n" +
            "        int read = super.read(b, off, toRead);\n" +
            "        if (read > 0) {\n" +
            "            count += read;\n" +
            "        }\n" +
            "        return read;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public int available() throws IOException {\n" +
            "        int available = super.available();\n" +
            "        long remaining = remaining();\n" +
            "        return remaining == Long.MAX_VALUE ? available : (int) Math.min(available, remaining);\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void close() throws IOException {\n" +
            "        if (propagateClose) {\n" +
            "            super.close();\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
    }

    private static String thresholdingOutputStream() {
        return "package org.apache.commons.io.output;\n" +
            "\n" +
            "import java.io.IOException;\n" +
            "import java.io.OutputStream;\n" +
            "\n" +
            "public abstract class ThresholdingOutputStream extends OutputStream {\n" +
            "    private final int threshold;\n" +
            "    private long byteCount;\n" +
            "    private boolean thresholdExceeded;\n" +
            "\n" +
            "    protected ThresholdingOutputStream(int threshold) {\n" +
            "        if (threshold < 0) {\n" +
            "            throw new IllegalArgumentException(\"Threshold must be non-negative\");\n" +
            "        }\n" +
            "        this.threshold = threshold;\n" +
            "    }\n" +
            "\n" +
            "    protected abstract OutputStream getStream() throws IOException;\n" +
            "\n" +
            "    protected abstract void thresholdReached() throws IOException;\n" +
            "\n" +
            "    protected int getThreshold() {\n" +
            "        return threshold;\n" +
            "    }\n" +
            "\n" +
            "    protected long getByteCount() {\n" +
            "        return byteCount;\n" +
            "    }\n" +
            "\n" +
            "    protected void resetByteCount() {\n" +
            "        byteCount = 0L;\n" +
            "        thresholdExceeded = false;\n" +
            "    }\n" +
            "\n" +
            "    protected boolean isThresholdExceeded() {\n" +
            "        return thresholdExceeded;\n" +
            "    }\n" +
            "\n" +
            "    private void checkThreshold(int count) throws IOException {\n" +
            "        if (!thresholdExceeded && byteCount + count > threshold) {\n" +
            "            thresholdExceeded = true;\n" +
            "            thresholdReached();\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void write(int b) throws IOException {\n" +
            "        checkThreshold(1);\n" +
            "        getStream().write(b);\n" +
            "        byteCount++;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void write(byte[] b, int off, int len) throws IOException {\n" +
            "        if (b == null) {\n" +
            "            throw new NullPointerException(\"Buffer must not be null\");\n" +
            "        }\n" +
            "        if (off < 0 || len < 0 || off + len > b.length) {\n" +
            "            throw new IndexOutOfBoundsException();\n" +
            "        }\n" +
            "        if (len == 0) {\n" +
            "            return;\n" +
            "        }\n" +
            "        checkThreshold(len);\n" +
            "        getStream().write(b, off, len);\n" +
            "        byteCount += len;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void flush() throws IOException {\n" +
            "        getStream().flush();\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void close() throws IOException {\n" +
            "        getStream().close();\n" +
            "    }\n" +
            "}\n";
    }

    private static String deferredFileOutputStream() {
        return "package org.apache.commons.io.output;\n" +
            "\n" +
            "import java.io.ByteArrayInputStream;\n" +
            "import java.io.File;\n" +
            "import java.io.FileInputStream;\n" +
            "import java.io.IOException;\n" +
            "import java.io.InputStream;\n" +
            "import java.io.OutputStream;\n" +
            "import java.nio.file.Files;\n" +
            "\n" +
            "public class DeferredFileOutputStream extends ThresholdingOutputStream {\n" +
            "    private final int initialBufferSize;\n" +
            "    private final File outputFile;\n" +
            "    private final String prefix;\n" +
            "    private final String suffix;\n" +
            "    private final File directory;\n" +
            "    private ByteArrayOutputStream memoryOutputStream;\n" +
            "    private OutputStream currentOutputStream;\n" +
            "    private boolean closed;\n" +
            "\n" +
            "    public DeferredFileOutputStream(int threshold, File outputFile) {\n" +
            "        this(threshold, 32, outputFile);\n" +
            "    }\n" +
            "\n" +
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, File outputFile) {\n" +
            "        this(threshold, initialBufferSize, null, null, outputFile);\n" +
            "    }\n" +
            "\n" +
            "    public DeferredFileOutputStream(int threshold, String prefix, String suffix, File directory) {\n" +
            "        this(threshold, 32, prefix, suffix, directory);\n" +
            "    }\n" +
            "\n" +
            "    public DeferredFileOutputStream(int threshold, int initialBufferSize, String prefix, String suffix, File directory) {\n" +
            "        super(threshold);\n" +
            "        this.initialBufferSize = initialBufferSize;\n" +
            "        this.outputFile = prefix == null && suffix == null ? directory : null;\n" +
            "        this.prefix = prefix;\n" +
            "        this.suffix = suffix;\n" +
            "        this.directory = directory;\n" +
            "        this.memoryOutputStream = new ByteArrayOutputStream(initialBufferSize);\n" +
            "        this.currentOutputStream = memoryOutputStream;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected OutputStream getStream() throws IOException {\n" +
            "        return currentOutputStream;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void thresholdReached() throws IOException {\n" +
            "        File file = outputFile;\n" +
            "        if (file == null) {\n" +
            "            file = File.createTempFile(prefix, suffix, directory);\n" +
            "        }\n" +
            "        currentOutputStream = Files.newOutputStream(file.toPath());\n" +
            "    }\n" +
            "\n" +
            "    public boolean isInMemory() {\n" +
            "        return currentOutputStream == memoryOutputStream;\n" +
            "    }\n" +
            "\n" +
            "    public byte[] getData() {\n" +
            "        return memoryOutputStream == null ? null : memoryOutputStream.toByteArray();\n" +
            "    }\n" +
            "\n" +
            "    public File getFile() {\n" +
            "        return outputFile;\n" +
            "    }\n" +
            "\n" +
            "    public InputStream toInputStream() throws IOException {\n" +
            "        if (outputFile == null) {\n" +
            "            return new ByteArrayInputStream(getData());\n" +
            "        }\n" +
            "        return new FileInputStream(outputFile);\n" +
            "    }\n" +
            "\n" +
            "    public void writeTo(OutputStream out) throws IOException {\n" +
            "        try (InputStream in = toInputStream()) {\n" +
            "            byte[] buffer = new byte[Math.max(1, initialBufferSize)];\n" +
            "            int read;\n" +
            "            while ((read = in.read(buffer)) != -1) {\n" +
            "                out.write(buffer, 0, read);\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void close() throws IOException {\n" +
            "        super.close();\n" +
            "        closed = true;\n" +
            "    }\n" +
            "}\n";
    }

    private static String nullPrintStream() {
        return "package org.apache.commons.io.output;\n" +
            "\n" +
            "import java.io.OutputStream;\n" +
            "import java.io.PrintStream;\n" +
            "\n" +
            "public class NullPrintStream extends PrintStream {\n" +
            "    public static final NullPrintStream NULL_PRINT_STREAM = new NullPrintStream();\n" +
            "\n" +
            "    public NullPrintStream() {\n" +
            "        super(new OutputStream() {\n" +
            "            @Override\n" +
            "            public void write(int b) {\n" +
            "            }\n" +
            "        });\n" +
            "    }\n" +
            "}\n";
    }

    private static String ioUtils() {
        return "package org.apache.commons.io;\n" +
            "\n" +
            "import java.io.Closeable;\n" +
            "import java.io.IOException;\n" +
            "import java.io.InputStream;\n" +
            "import java.io.OutputStream;\n" +
            "import java.io.Reader;\n" +
            "import java.io.StringWriter;\n" +
            "import java.nio.charset.Charset;\n" +
            "\n" +
            "public final class IOUtils {\n" +
            "    private IOUtils() {\n" +
            "    }\n" +
            "\n" +
            "    public static String toString(InputStream input, String charsetName) throws IOException {\n" +
            "        return toString(input, Charset.forName(charsetName));\n" +
            "    }\n" +
            "\n" +
            "    public static String toString(InputStream input, Charset charset) throws IOException {\n" +
            "        byte[] buffer = new byte[8192];\n" +
            "        StringBuilder sb = new StringBuilder();\n" +
            "        int read;\n" +
            "        while ((read = input.read(buffer)) != -1) {\n" +
            "            sb.append(new String(buffer, 0, read, charset));\n" +
            "        }\n" +
            "        return sb.toString();\n" +
            "    }\n" +
            "\n" +
            "    public static String toString(InputStream input) throws IOException {\n" +
            "        return toString(input, Charset.defaultCharset());\n" +
            "    }\n" +
            "\n" +
            "    public static void copy(InputStream input, OutputStream output) throws IOException {\n" +
            "        byte[] buffer = new byte[8192];\n" +
            "        int read;\n" +
            "        while ((read = input.read(buffer)) != -1) {\n" +
            "            output.write(buffer, 0, read);\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static void closeQuietly(Closeable closeable) {\n" +
            "        if (closeable != null) {\n" +
            "            try {\n" +
            "                closeable.close();\n" +
            "            } catch (IOException ignored) {\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
    }
}
