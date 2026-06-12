
public class TestTransformation {
    public String testOldPattern1(Object result) {
        return result != null ? result.toString() : null;
    }
    public String testOldPattern2(Object result) {
        return result.toString();
    }
    public String testOldPattern3(Object result) {
        Object jsResult = sr;
        return jsResult != null ? jsResult.toString() : null;
    }
}
