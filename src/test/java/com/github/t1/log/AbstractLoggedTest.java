package com.github.t1.log;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Collections;

import javax.enterprise.inject.Instance;
import javax.interceptor.InvocationContext;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
@Deprecated
public abstract class AbstractLoggedTest {
    @Mock
    protected InvocationContext context;
    @Mock
    protected Logger logger;
    @Mock
    protected Instance<LogContextVariable> variables;
    @Mock
    protected Converters converters;
    @InjectMocks
    protected LoggingInterceptor interceptor = new LoggingInterceptor() {
        @Override
        protected Logger getLogger(Class<?> loggerType) {
            AbstractLoggedTest.this.loggerType = loggerType;
            return logger;
        }
    };

    protected Class<?> loggerType;

    @Before
    public void setupLogger() {
        when(logger.isDebugEnabled()).thenReturn(true);
        when(variables.iterator()).thenReturn(Collections.<LogContextVariable> emptyList().iterator());
    }

    @Before
    public void setupConverters() {
        when(converters.convert(any())).thenAnswer(new ReturnsArgumentAt(0));
    }

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    protected void whenMethod(Object target, String methodName, Object... args) throws Exception {
        Method method = target.getClass().getMethod(methodName, types(args));
        whenMethod(method, target, args);
    }

    private Class<?>[] types(Object[] objects) {
        Class<?>[] result = new Class[objects.length];
        for (int i = 0; i < objects.length; i++) {
            result[i] = objects[i].getClass();
        }
        return result;
    }

    protected void whenMethod(final Method method, final Object target, final Object... args) throws Exception {
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(target);
        when(context.getParameters()).thenReturn(args);
        when(context.proceed()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return method.invoke(target, args);
            }
        });
    }
}
