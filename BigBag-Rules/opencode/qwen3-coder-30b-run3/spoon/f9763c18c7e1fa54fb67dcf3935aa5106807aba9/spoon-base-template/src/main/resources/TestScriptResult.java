import com.gargoylesoftware.htmlunit.ScriptResult;

public class TestScriptResult {
    public void test() {
        Object result = executeScript("some script");
        ScriptResult scriptResult = new ScriptResult(result);
        String data = scriptResult.getJavaScriptResult().toString();
    }
    
    private Object executeScript(String script) {
        return "some result";
    }
}