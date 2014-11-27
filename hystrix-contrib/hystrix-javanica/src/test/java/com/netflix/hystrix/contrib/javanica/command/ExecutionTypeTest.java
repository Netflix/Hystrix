package com.netflix.hystrix.contrib.javanica.command;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import rx.Observable;
import rx.internal.operators.OperatorMulticast;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;

import static com.netflix.hystrix.contrib.javanica.command.ExecutionType.ASYNCHRONOUS;
import static com.netflix.hystrix.contrib.javanica.command.ExecutionType.OBSERVABLE;
import static com.netflix.hystrix.contrib.javanica.command.ExecutionType.SYNCHRONOUS;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ExecutionTypeTest {

    @Parameterized.Parameters
    public static List<Object[]> data() {
        return asList(new Object[][]{
                {returnType(Integer.class), shouldHaveExecutionType(SYNCHRONOUS)},
                {returnType(List.class), shouldHaveExecutionType(SYNCHRONOUS)},
                {returnType(Object.class), shouldHaveExecutionType(SYNCHRONOUS)},
                {returnType(Class.class), shouldHaveExecutionType(SYNCHRONOUS)},
                {returnType(Future.class), shouldHaveExecutionType(ASYNCHRONOUS)},
                {returnType(AsyncResult.class), shouldHaveExecutionType(ASYNCHRONOUS)},
                {returnType(RunnableFuture.class), shouldHaveExecutionType(ASYNCHRONOUS)},
                {returnType(CompletableFuture.class), shouldHaveExecutionType(ASYNCHRONOUS)},
                {returnType(Observable.class), shouldHaveExecutionType(OBSERVABLE)},
                {returnType(OperatorMulticast.class), shouldHaveExecutionType(OBSERVABLE)},
        });
    }

    @Test
    public void should_return_correct_execution_type() throws Exception {
        assertEquals("Unexpected execution type for method return type: " + methodReturnType, expectedType, ExecutionType.getExecutionType(methodReturnType));

    }

    private static ExecutionType shouldHaveExecutionType(final ExecutionType type) {
        return type;
    }

    private static Class<?> returnType(final Class<?> aClass) {
        return aClass;
    }

    private final Class<?> methodReturnType;
    private final ExecutionType expectedType;

    public ExecutionTypeTest(final Class<?> methodReturnType, final ExecutionType expectedType) {
        this.methodReturnType = methodReturnType;
        this.expectedType = expectedType;
    }
}
