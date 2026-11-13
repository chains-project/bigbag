package org.apache.bval.jsr;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import static org.mockito.Mockito.when;
public class SampleTest {
    public void testMethod() {
        org.mockito.invocation.InvocationOnMock invocation = org.mockito.Mockito.mock(org.mockito.invocation.InvocationOnMock.class);
        org.mockito.Mockito.when(invocation.getArgument(0, java.lang.String.class)).thenReturn("test");
    }
}
