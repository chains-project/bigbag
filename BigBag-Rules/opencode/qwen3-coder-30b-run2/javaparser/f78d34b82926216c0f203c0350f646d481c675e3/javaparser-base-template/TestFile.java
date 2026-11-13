package test;

import tokyo.peya.lib.FieldModifier;

public class TestFile {
    public void testMethod() {
        // This represents a call that might have a breaking change
        FieldModifier.modify(this, "fieldName", "value");
    }
}