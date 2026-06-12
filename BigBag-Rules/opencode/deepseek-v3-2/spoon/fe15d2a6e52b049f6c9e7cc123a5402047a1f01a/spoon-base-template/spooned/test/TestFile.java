package test;
import org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy;
public class TestFile {
    public void testMethod() {
        // This should be transformed
        Object strategy1 = JAXBToStringStrategy.INSTANCE;
        // This should NOT be transformed (not a Strategy class)
        String str = "test".getInstance();
        // This should be transformed if DefaultToStringStrategy exists
        // Object strategy2 = org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy.getInstance();
    }
}
