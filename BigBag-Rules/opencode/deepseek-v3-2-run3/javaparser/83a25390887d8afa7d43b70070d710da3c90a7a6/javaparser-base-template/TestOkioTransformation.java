import okio.Okio;
import okio.Source;
import okio.Sink;
import okio.BufferedSource;
import okio.BufferedSink;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class TestOkioTransformation {
    
    public void testOldApiUsage() {
        // These should be transformed
        Source source = Okio.source(new File("test.txt"));
        Sink sink = Okio.sink(new File("output.txt"));
        
        BufferedSource bufferedSource = Okio.buffer(source);
        BufferedSink bufferedSink = Okio.buffer(sink);
        
        // Multiple arguments should not be transformed
        SomeClass result = Okio.someOtherMethod(arg1, arg2);
        
        // Different class should not be transformed
        OtherClass.buffer(something);
        
        // Chain calls
        Okio.buffer(Okio.source(new File("chain.txt")));
    }
    
    public void testAlreadyNewApi() {
        // These should remain unchanged (already using new API)
        File file = new File("test.txt");
        Source source = file.source();
        Sink sink = file.sink();
        
        BufferedSource buffered = source.buffer();
        BufferedSink bufferedSink = sink.buffer();
    }
}