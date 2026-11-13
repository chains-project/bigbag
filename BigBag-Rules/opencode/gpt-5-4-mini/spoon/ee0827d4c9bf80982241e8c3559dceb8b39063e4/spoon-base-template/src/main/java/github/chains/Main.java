package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws Exception {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceRoot.toString());
        launcher.buildModel();

        writeFile(sourceRoot, "org/apache/commons/io/IOUtils.java", ioUtils());
        writeFile(sourceRoot, "org/apache/commons/io/input/ClosedInputStream.java", closedInputStream());
        writeFile(sourceRoot, "org/apache/commons/io/input/CountingInputStream.java", countingInputStream());
        writeFile(sourceRoot, "org/apache/commons/io/input/BoundedInputStream.java", boundedInputStream());
        writeFile(sourceRoot, "org/apache/commons/io/output/NullPrintStream.java", nullPrintStream());
        writeFile(sourceRoot, "org/apache/commons/io/output/ThresholdingOutputStream.java", thresholdingOutputStream());
    }

    private static void writeFile(Path sourceRoot, String relativePath, String content) throws IOException {
        Path file = sourceRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String ioUtils() {
        return "package org.apache.commons.io;\n"
            + "\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "import java.io.OutputStream;\n"
            + "import java.io.Reader;\n"
            + "import java.io.Writer;\n"
            + "\n"
            + "public final class IOUtils {\n"
            + "    private IOUtils() {}\n"
            + "\n"
            + "    public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException {\n"
            + "        int b1;\n"
            + "        int b2;\n"
            + "        do {\n"
            + "            b1 = input1.read();\n"
            + "            b2 = input2.read();\n"
            + "            if (b1 != b2) {\n"
            + "                return false;\n"
            + "            }\n"
            + "        } while (b1 != -1);\n"
            + "        return true;\n"
            + "    }\n"
            + "\n"
            + "    public static void copy(InputStream input, OutputStream output) throws IOException {\n"
            + "        byte[] buffer = new byte[8192];\n"
            + "        int n;\n"
            + "        while ((n = input.read(buffer)) != -1) {\n"
            + "            output.write(buffer, 0, n);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static void copy(Reader input, Writer output) throws IOException {\n"
            + "        char[] buffer = new char[8192];\n"
            + "        int n;\n"
            + "        while ((n = input.read(buffer)) != -1) {\n"
            + "            output.write(buffer, 0, n);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static String toString(InputStream input, String encoding) throws IOException {\n"
            + "        byte[] data = toByteArray(input);\n"
            + "        return new String(data, encoding);\n"
            + "    }\n"
            + "\n"
            + "    public static byte[] toByteArray(InputStream input) throws IOException {\n"
            + "        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();\n"
            + "        copy(input, out);\n"
            + "        return out.toByteArray();\n"
            + "    }\n"
            + "}\n";
    }

    private static String closedInputStream() {
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

    private static String countingInputStream() {
        return "package org.apache.commons.io.input;\n"
            + "\n"
            + "import java.io.FilterInputStream;\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "\n"
            + "public class CountingInputStream extends FilterInputStream {\n"
            + "    private long count;\n"
            + "\n"
            + "    public CountingInputStream(InputStream in) {\n"
            + "        super(in);\n"
            + "    }\n"
            + "\n"
            + "    public int getCount() {\n"
            + "        return (int) count;\n"
            + "    }\n"
            + "\n"
            + "    public long getByteCount() {\n"
            + "        return count;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int read() throws IOException {\n"
            + "        int result = super.read();\n"
            + "        if (result != -1) count++;\n"
            + "        return result;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int read(byte[] b, int off, int len) throws IOException {\n"
            + "        int result = super.read(b, off, len);\n"
            + "        if (result > 0) count += result;\n"
            + "        return result;\n"
            + "    }\n"
            + "}\n";
    }

    private static String boundedInputStream() {
        return "package org.apache.commons.io.input;\n"
            + "\n"
            + "import java.io.FilterInputStream;\n"
            + "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "\n"
            + "public class BoundedInputStream extends FilterInputStream {\n"
            + "    private final long max;\n"
            + "    private long count;\n"
            + "    private boolean propagateClose = true;\n"
            + "\n"
            + "    public BoundedInputStream(InputStream in) { this(in, -1); }\n"
            + "    public BoundedInputStream(InputStream in, long size) { super(in); this.max = size; }\n"
            + "    public boolean isPropagateClose() { return propagateClose; }\n"
            + "    public void setPropagateClose(boolean propagateClose) { this.propagateClose = propagateClose; }\n"
            + "\n"
            + "    @Override public int available() throws IOException {\n"
            + "        if (max >= 0) { long remaining = max - count; return remaining <= 0 ? 0 : (int) Math.min(super.available(), remaining); }\n"
            + "        return super.available();\n"
            + "    }\n"
            + "\n"
            + "    @Override public void close() throws IOException { if (propagateClose) super.close(); }\n"
            + "    @Override public int read() throws IOException { if (max >= 0 && count >= max) return -1; int result = super.read(); if (result != -1) count++; return result; }\n"
            + "    @Override public int read(byte[] b) throws IOException { return read(b, 0, b.length); }\n"
            + "    @Override public int read(byte[] b, int off, int len) throws IOException { if (max >= 0) { long remaining = max - count; if (remaining <= 0) return -1; len = (int) Math.min(len, remaining); } int result = super.read(b, off, len); if (result > 0) count += result; return result; }\n"
            + "    @Override public long skip(long n) throws IOException { if (max >= 0) { long remaining = max - count; if (remaining <= 0) return 0; n = Math.min(n, remaining); } long skipped = super.skip(n); count += skipped; return skipped; }\n"
            + "    @Override public synchronized void mark(int readlimit) { super.mark(readlimit); }\n"
            + "    @Override public synchronized void reset() throws IOException { super.reset(); }\n"
            + "}\n";
    }

    private static String nullPrintStream() {
        return "package org.apache.commons.io.output;\n"
            + "\n"
            + "import java.io.OutputStream;\n"
            + "import java.io.PrintStream;\n"
            + "\n"
            + "public class NullPrintStream extends PrintStream {\n"
            + "    public static final NullPrintStream NULL_PRINT_STREAM = new NullPrintStream();\n"
            + "    public NullPrintStream() { super(new OutputStream() { public void write(int b) {} }); }\n"
            + "}\n";
    }

    private static String thresholdingOutputStream() {
        return "package org.apache.commons.io.output;\n"
            + "\n"
            + "import java.io.IOException;\n"
            + "import java.io.OutputStream;\n"
            + "\n"
            + "public abstract class ThresholdingOutputStream extends OutputStream {\n"
            + "    private final int threshold;\n"
            + "    private long byteCount;\n"
            + "    private boolean thresholdExceeded;\n"
            + "    protected ThresholdingOutputStream(int threshold) { this.threshold = threshold; }\n"
            + "    protected abstract OutputStream getStream() throws IOException;\n"
            + "    protected abstract void thresholdReached() throws IOException;\n"
            + "    protected void checkThreshold(int count) throws IOException { if (!thresholdExceeded && byteCount + count > threshold) { thresholdExceeded = true; thresholdReached(); } }\n"
            + "    public long getByteCount() { return byteCount; }\n"
            + "    public int getThreshold() { return threshold; }\n"
            + "    public boolean isThresholdExceeded() { return thresholdExceeded; }\n"
            + "    protected void resetByteCount() { byteCount = 0; thresholdExceeded = false; }\n"
            + "    protected void setByteCount(long count) { byteCount = count; }\n"
            + "    @Override public void write(int b) throws IOException { checkThreshold(1); getStream().write(b); byteCount++; }\n"
            + "    @Override public void write(byte[] b) throws IOException { write(b, 0, b.length); }\n"
            + "    @Override public void write(byte[] b, int off, int len) throws IOException { checkThreshold(len); getStream().write(b, off, len); byteCount += len; }\n"
            + "    @Override public void flush() throws IOException { getStream().flush(); }\n"
            + "    @Override public void close() throws IOException { getStream().close(); }\n"
            + "}\n";
    }
}
