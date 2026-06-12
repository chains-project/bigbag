public class TestScriptResult {

    public String testMethod(Object result) {
        ScriptResult scriptResult = result;
        return scriptResult.getJavaScriptResult().toString();
    }

    public Object testMethod2(Object result) {
        Object scriptResult = result;
        return scriptResult;
    }
}
