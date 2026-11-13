import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class TestJacksonExceptions {
    
    private ObjectMapper mapper = new ObjectMapper();
    
    // Method with throws clause
    public MyData readData(File file) throws JsonParseException, JsonMappingException, IOException {
        return mapper.readValue(file, MyData.class);
    }
    
    // Method with try-catch
    public MyData readDataSafely(File file) {
        try {
            return mapper.readValue(file, MyData.class);
        } catch (JsonParseException e) {
            System.err.println("Parse error: " + e.getMessage());
            return null;
        } catch (JsonMappingException e) {
            System.err.println("Mapping error: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            return null;
        }
    }
    
    // Variable declaration with old exception type
    private JsonParseException lastParseException;
    private JsonMappingException lastMappingException;
    
    // Method that returns exception type
    public JsonParseException getLastParseException() {
        return lastParseException;
    }
    
    public void setLastParseException(JsonParseException exception) {
        this.lastParseException = exception;
    }
    
    // Catch with multiple exceptions
    public void processFile(File file) {
        try {
            MyData data = mapper.readValue(file, MyData.class);
            process(data);
        } catch (JsonParseException | JsonMappingException e) {
            handleJacksonException(e);
        } catch (IOException e) {
            handleIOException(e);
        }
    }
    
    private void process(MyData data) {
        // processing logic
    }
    
    private void handleJacksonException(Exception e) {
        System.err.println("Jackson error: " + e.getMessage());
    }
    
    private void handleIOException(IOException e) {
        System.err.println("IO error: " + e.getMessage());
    }
    
    static class MyData {
        private String name;
        private int value;
        
        // getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}