// Test file for okio transformation
public class TestOkioTransformation {

    public void testTransformations() {
        // Should transform: Okio.buffer(source) -> source.buffer()
        String result1 = source.buffer();
        // Should transform: Okio.source(file) -> file.source()
        String result2 = file.source();
        // Should transform: Okio.sink(file) -> file.sink()
        String result3 = file.sink();
        // Should NOT transform (wrong method name)
        String result4 = Okio.otherMethod(arg);
        // Should NOT transform (different class)
        String result5 = OtherClass.buffer(arg);
        // Chain transformation: Okio.buffer(Okio.source(file)) -> file.source().buffer()
        String result6 = file.source().buffer();
    }
}
