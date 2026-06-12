import org.apache.mina.core.IoUtil;
import org.apache.mina.core.buffer.IoBuffer;
public class TestMinaClass {
    public void testIoUtilAwait() {
        java.util.List<java.lang.Object> sessions = null;
        // Old API - should be transformed
        org.apache.mina.core.IoUtil.await(sessions, 5L, "java.util.concurrent.TimeUnit.SECONDS");
        org.apache.mina.core.IoUtil.await(sessions, 5000L);
        org.apache.mina.core.IoUtil.await(sessions, 5000L, java.util.concurrent.TimeUnit.SECONDS);
        org.apache.mina.core.IoUtil.awaitUninterruptibly(sessions, 5L, "java.util.concurrent.TimeUnit.SECONDS");
        org.apache.mina.core.IoUtil.awaitUninterruptibly(sessions, 5000L);
        org.apache.mina.core.IoUtil.awaitUninterruptibly(sessions, 5000L, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void testIoBufferAllocation() {
        // Old API - should be transformed
        org.apache.mina.core.buffer.IoBuffer buffer1 = org.apache.mina.core.buffer.IoBuffer.allocate(1024, true);
        org.apache.mina.core.buffer.IoBuffer buffer2 = org.apache.mina.core.buffer.IoBuffer.allocate(1024, true);
    }
}
