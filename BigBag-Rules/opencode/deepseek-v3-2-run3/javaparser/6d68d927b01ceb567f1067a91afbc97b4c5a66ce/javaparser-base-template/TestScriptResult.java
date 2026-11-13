package test;

public class TestScriptResult {

    public String testMethod(Object result) {
        Object scriptResult = result;
        return scriptResult.toString();
    }

    public Object testMethod2(Object result) {
        return result;
    }
}
