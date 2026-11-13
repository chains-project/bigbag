package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_BOUNDED = "org.apache.commons.io.input.BoundedInputStream";
    private static final String OLD_CLOSED = "org.apache.commons.io.input.ClosedInputStream";
    private static final String OLD_COUNTING = "org.apache.commons.io.input.CountingInputStream";
    private static final String OLD_NULL_PRINT_STREAM = "org.apache.commons.io.output.NullPrintStream";
    private static final String OLD_THRESHOLDING = "org.apache.commons.io.output.ThresholdingOutputStream";

    private static final String INPUT_STREAM = "java.io.InputStream";
    private static final String PRINT_STREAM = "java.io.PrintStream";
    private static final String HELPER_THRESHOLDING = "spooncompat.commonsio.CompatThresholdingOutputStream";

    public static void main( String[] args ) throws Exception {
        if ( args.length < 1 || args.length > 2 ) {
            throw new IllegalArgumentException( "Usage: Main <sourceDir> [outputDir]" );
        }

        Path inputDir = Paths.get( args[0] ).toAbsolutePath().normalize();
        Path outputDir = args.length == 2 ? Paths.get( args[1] ).toAbsolutePath().normalize() : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath( true );
        launcher.getEnvironment().setAutoImports( true );
        launcher.getEnvironment().setCommentEnabled( false );
        launcher.getEnvironment().setIgnoreDuplicateDeclarations( true );
        launcher.addInputResource( inputDir.toString() );
        launcher.setSourceOutputDirectory( outputDir.toFile() );

        CtModel model = launcher.buildModel();
        transform( model, launcher, outputDir );
        launcher.prettyprint();
    }

    private static void transform( CtModel model, Launcher launcher, Path outputDir ) throws IOException {
        ensureHelperClass( outputDir );

        for ( CtConstructorCall<?> call : model.getElements( new TypeFilter<>( CtConstructorCall.class ) ) ) {
            rewriteConstructorCall( call, launcher );
        }

        for ( CtInvocation<?> invocation : model.getElements( new TypeFilter<>( CtInvocation.class ) ) ) {
            rewriteInvocation( invocation );
        }

        for ( CtFieldAccess<?> access : model.getElements( new TypeFilter<>( CtFieldAccess.class ) ) ) {
            rewriteFieldAccess( access, launcher );
        }

        for ( CtClass<?> type : model.getElements( new TypeFilter<>( CtClass.class ) ) ) {
            rewriteSuperclass( type, launcher );
        }

        for ( CtTypeReference<?> ref : model.getElements( new TypeFilter<>( CtTypeReference.class ) ) ) {
            rewriteTypeReference( ref, launcher );
        }

        for ( CtCompilationUnit unit : model.getElements( new TypeFilter<>( CtCompilationUnit.class ) ) ) {
            unit.getImports().removeIf( importRef -> importRef != null
                && importRef.getReference() != null
                && isOldCommonsIoType( importRef.getReference() ) );
        }
    }

    private static void rewriteConstructorCall( CtConstructorCall<?> call, Launcher launcher ) {
        String typeName = qualifiedName( call.getType() );
        if ( OLD_CLOSED.equals( typeName ) ) {
            call.replace( launcher.getFactory().Code().createCodeSnippetExpression(
                "new java.io.ByteArrayInputStream(new byte[0])" ) );
            return;
        }

        if ( OLD_BOUNDED.equals( typeName ) ) {
            List<?> args = call.getArguments();
            if ( args.size() == 1 ) {
                call.replace( (spoon.reflect.code.CtExpression<?>) args.get( 0 ) );
            } else if ( args.size() >= 2 ) {
                String input = args.get( 0 ).toString();
                String size = args.get( 1 ).toString();
                String snippet = "new java.io.FilterInputStream(" + input + ") {"
                    + "{ final long maxBytes = " + size + "; }"
                    + "private long remaining = maxBytes;"
                    + "@Override public int read() throws java.io.IOException {"
                    + "if (remaining <= 0) return -1;"
                    + "int result = super.read();"
                    + "if (result != -1) remaining--;"
                    + "return result;"
                    + "}"
                    + "@Override public int read(byte[] b, int off, int len) throws java.io.IOException {"
                    + "if (remaining <= 0) return -1;"
                    + "int toRead = (int) java.lang.Math.min(len, remaining);"
                    + "int count = super.read(b, off, toRead);"
                    + "if (count != -1) remaining -= count;"
                    + "return count;"
                    + "}"
                    + "@Override public long skip(long n) throws java.io.IOException {"
                    + "long skipped = super.skip(java.lang.Math.min(n, remaining));"
                    + "remaining -= skipped;"
                    + "return skipped;"
                    + "}"
                    + "@Override public int available() throws java.io.IOException {"
                    + "return (int) java.lang.Math.min(super.available(), remaining);"
                    + "}"
                    + "}";
                call.replace( launcher.getFactory().Code().createCodeSnippetExpression( snippet ) );
            }
        }
    }

    private static void rewriteFieldAccess( CtFieldAccess<?> access, Launcher launcher ) {
        if ( access.getVariable() != null
            && OLD_NULL_PRINT_STREAM.equals( qualifiedName( access.getVariable().getDeclaringType() ) )
            && "NULL_PRINT_STREAM".equals( access.getVariable().getSimpleName() ) ) {
            access.replace( launcher.getFactory().Code().createCodeSnippetExpression(
                "new java.io.PrintStream(java.io.OutputStream.nullOutputStream())" ) );
        }
    }

    private static void rewriteInvocation( CtInvocation<?> invocation ) {
        if ( "getByteCount".equals( invocation.getExecutable().getSimpleName() )
            && isCountingInputStreamTarget( invocation ) ) {
            invocation.getExecutable().setSimpleName( "getCount" );
        }
    }

    private static void rewriteSuperclass( CtClass<?> type, Launcher launcher ) {
        if ( isOldThresholding( type.getSuperclass() ) ) {
            type.setSuperclass( launcher.getFactory().Type().createReference( HELPER_THRESHOLDING ) );
        }
    }

    private static void rewriteTypeReference( CtTypeReference<?> ref, Launcher launcher ) {
        String qualifiedName = qualifiedName( ref );
        if ( OLD_BOUNDED.equals( qualifiedName ) || OLD_CLOSED.equals( qualifiedName ) ) {
            ref.replace( launcher.getFactory().Type().createReference( INPUT_STREAM ) );
        } else if ( OLD_NULL_PRINT_STREAM.equals( qualifiedName ) ) {
            ref.replace( launcher.getFactory().Type().createReference( PRINT_STREAM ) );
        } else if ( OLD_THRESHOLDING.equals( qualifiedName ) ) {
            ref.replace( launcher.getFactory().Type().createReference( HELPER_THRESHOLDING ) );
        }
    }

    private static boolean isOldThresholding( CtTypeReference<?> ref ) {
        return ref != null && OLD_THRESHOLDING.equals( qualifiedName( ref ) );
    }

    private static boolean isCountingInputStreamTarget( CtInvocation<?> invocation ) {
        CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        return targetType != null && OLD_COUNTING.equals( qualifiedName( targetType ) );
    }

    private static boolean isOldCommonsIoType( CtReference reference ) {
        String qn = reference == null ? null : reference.toString();
        return OLD_BOUNDED.equals( qn ) || OLD_CLOSED.equals( qn ) || OLD_COUNTING.equals( qn )
            || OLD_NULL_PRINT_STREAM.equals( qn ) || OLD_THRESHOLDING.equals( qn );
    }

    private static String qualifiedName( CtTypeReference<?> reference ) {
        return reference == null ? null : reference.getQualifiedName();
    }

    private static void ensureHelperClass( Path outputDir ) throws IOException {
        Path helper = outputDir.resolve( Paths.get( "spooncompat", "commonsio", "CompatThresholdingOutputStream.java" ) );
        Files.createDirectories( helper.getParent() );
        String source = String.join( "\n",
            "package spooncompat.commonsio;",
            "",
            "import java.io.IOException;",
            "import java.io.OutputStream;",
            "",
            "public abstract class CompatThresholdingOutputStream extends OutputStream {",
            "    private final int threshold;",
            "    private long byteCount;",
            "    private boolean thresholdExceeded;",
            "",
            "    protected CompatThresholdingOutputStream( int threshold ) {",
            "        if ( threshold < 0 ) {",
            "            throw new IllegalArgumentException( \"Threshold must be non-negative\" );",
            "        }",
            "        this.threshold = threshold;",
            "    }",
            "",
            "    protected abstract OutputStream getStream() throws IOException;",
            "",
            "    protected abstract void thresholdReached() throws IOException;",
            "",
            "    protected void checkThreshold( int count ) throws IOException {",
            "        if ( !thresholdExceeded && byteCount + count > threshold ) {",
            "            thresholdExceeded = true;",
            "            thresholdReached();",
            "        }",
            "    }",
            "",
            "    protected long getByteCount() {",
            "        return byteCount;",
            "    }",
            "",
            "    protected int getThreshold() {",
            "        return threshold;",
            "    }",
            "",
            "    protected boolean isThresholdExceeded() {",
            "        return thresholdExceeded;",
            "    }",
            "",
            "    protected void resetByteCount() {",
            "        byteCount = 0;",
            "        thresholdExceeded = false;",
            "    }",
            "",
            "    protected void setByteCount( long count ) {",
            "        byteCount = count;",
            "        thresholdExceeded = count > threshold;",
            "    }",
            "",
            "    @Override",
            "    public void write( int b ) throws IOException {",
            "        byte[] single = new byte[] { (byte) b };",
            "        write( single, 0, 1 );",
            "    }",
            "",
            "    @Override",
            "    public void write( byte[] b ) throws IOException {",
            "        write( b, 0, b.length );",
            "    }",
            "",
            "    @Override",
            "    public void write( byte[] b, int off, int len ) throws IOException {",
            "        if ( b == null ) {",
            "            throw new NullPointerException();",
            "        }",
            "        if ( ( off < 0 ) || ( len < 0 ) || ( off > b.length ) || ( off + len > b.length ) || ( off + len < 0 ) ) {",
            "            throw new IndexOutOfBoundsException();",
            "        }",
            "        if ( len == 0 ) {",
            "            return;",
            "        }",
            "        checkThreshold( len );",
            "        getStream().write( b, off, len );",
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
            "}" );
        Files.writeString( helper, source, StandardCharsets.UTF_8 );
    }
}
