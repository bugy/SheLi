package net.buggy.shoplist.util;


import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockUtils {

    public static <T> T mockStrict(Class<T> clazz) {
        return Mockito.mock(clazz, new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String clazz = invocationOnMock.getMethod().getDeclaringClass().getSimpleName();
                final String method = invocationOnMock.getMethod().getName();

                final String text = "Method is not mocked: " + clazz + "." + method;
                throw new UnsupportedOperationException(text);
            }
        });
    }
}
