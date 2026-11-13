package org.apache.bval.jsr;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import static org.mockito.Mockito.when;

public class SampleTest {
    public void testMethod() {
        InvocationOnMock invocation = Mockito.mock(InvocationOnMock.class);
        when(invocation.getArgumentAt(0, String.class)).thenReturn("test");
    }
}