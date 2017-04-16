package com.netflix.hystrix.contrib.javanica.test.common.fallback;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.exception.FallbackDefinitionException;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import rx.Completable;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil.
 */
@RunWith(JUnitParamsRunner.class)
public abstract class BasicGenericFallbackTest extends BasicHystrixTest {

    @ClassRule
    public static final SpringClassRule SCR = new SpringClassRule();
    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    protected abstract <T> T createProxy(Class<T> t);


    public Object[] methodGenericDefinitionSuccess() {
        return new Object[]{
                new Object[]{MethodGenericDefinitionSuccessCase0.class},
                new Object[]{MethodGenericDefinitionSuccessCase1.class},
                new Object[]{MethodGenericDefinitionSuccessCase2.class},
                new Object[]{MethodGenericDefinitionSuccessCase3.class},
                new Object[]{MethodGenericDefinitionSuccessCase4.class},
                new Object[]{MethodGenericDefinitionSuccessCase5.class},
                new Object[]{MethodGenericDefinitionSuccessCase6.class},
                new Object[]{MethodGenericDefinitionSuccessCase7.class},
        };
    }

    public Object[] methodGenericDefinitionFailure() {
        return new Object[]{
                new Object[]{MethodGenericDefinitionFailureCase0.class},
                new Object[]{MethodGenericDefinitionFailureCase1.class},
                new Object[]{MethodGenericDefinitionFailureCase2.class},
                new Object[]{MethodGenericDefinitionFailureCase3.class},
                new Object[]{MethodGenericDefinitionFailureCase4.class},
                new Object[]{MethodGenericDefinitionFailureCase5.class},
                new Object[]{MethodGenericDefinitionFailureCase6.class},
                new Object[]{MethodGenericDefinitionFailureCase7.class},
                new Object[]{MethodGenericDefinitionFailureCase8.class},
                new Object[]{MethodGenericDefinitionFailureCase9.class},
                new Object[]{MethodGenericDefinitionFailureCase10.class},
                new Object[]{MethodGenericDefinitionFailureCase11.class},

        };
    }

    public Object[] classGenericDefinitionSuccess() {
        return new Object[]{
                new Object[]{ClassGenericDefinitionSuccessCase0.class},
                new Object[]{ClassGenericDefinitionSuccessCase1.class},
        };
    }


    public Object[] classGenericDefinitionFailure() {
        return new Object[]{
                new Object[]{ClassGenericDefinitionFailureCase0.class},
                new Object[]{ClassGenericDefinitionFailureCase1.class},
        };
    }

    @Test
    @Parameters(method = "methodGenericDefinitionSuccess")
    public void testMethodGenericDefinitionSuccess(Class<?> type) {
        testXKindGenericDefinitionSuccess(type);
    }

    @Test(expected = FallbackDefinitionException.class)
    @Parameters(method = "methodGenericDefinitionFailure")
    public void testMethodGenericDefinitionFailure(Class<?> type) {
        Object p = createProxy(type);
        executeCommand(p);
    }

    @Test
    @Parameters(method = "classGenericDefinitionSuccess")
    public void testClassGenericDefinitionSuccess(Class<?> type) {
        testXKindGenericDefinitionSuccess(type);
    }

    @Test(expected = FallbackDefinitionException.class)
    @Parameters(method = "classGenericDefinitionFailure")
    public void testClassGenericDefinitionFailure(Class<?> type) {
        Object p = createProxy(type);
        executeCommand(p);
    }

    private void testXKindGenericDefinitionSuccess(Class<?> type) {
        Object p = createProxy(type);
        try {
            executeCommand(p);

            HystrixInvokableInfo<?> command = getHystrixCommandByKey("command");

            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));

        } catch (FallbackDefinitionException e) {
            throw new AssertionError(MessageFormatter.format("Case class '{}' has failed. Reason:\n{}", type.getCanonicalName(), e).getMessage());
        }
    }

    /* ====================================================================== */
    /* ===================== GENERIC METHOD DEFINITIONS ===================== */
    /* =========================== SUCCESS CASES ============================ */

    public static class MethodGenericDefinitionSuccessCase0 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> T command() { throw new IllegalStateException(); }
        private <T> T fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase1 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable> T command() { throw new IllegalStateException(); }
        private <T extends Serializable> T fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase2 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable, T1 extends T> T1 command() { throw new IllegalStateException(); }
        private <T extends Serializable, T1 extends T> T1 fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase3 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable, T1 extends T> GenericEntity<? extends T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable, T1 extends T> GenericEntity<? extends T1> fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase4 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable & Comparable, T1 extends T> GenericEntity<? extends T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable & Comparable, T1 extends T> GenericEntity<? extends T1> fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase5 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable & Comparable, T1 extends GenericEntity<T>> GenericEntity<T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable & Comparable, T1 extends GenericEntity<T>> GenericEntity<T1> fallback() { return null; }
    }

    public static class MethodGenericDefinitionSuccessCase6 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> GenericEntity<T> command() { throw new IllegalStateException(); }
        private <T> GenericEntity<T> fallback() { return new GenericEntity<T>(); }
    }

    public static class MethodGenericDefinitionSuccessCase7 {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<? super Serializable> command() { throw new IllegalStateException(); }
        private GenericEntity<? super Serializable> fallback() { return null; }
    }


    /* ====================================================================== */
    /* ===================== GENERIC METHOD DEFINITIONS ===================== */
    /* =========================== FAILURE CASES ============================ */

    public static class MethodGenericDefinitionFailureCase0 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> T command() { throw new IllegalStateException(); }
        private String fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase1 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable> T command() { throw new IllegalStateException(); }
        private <T> T fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase2 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable> T command() { throw new IllegalStateException(); }
        private <T extends Comparable> T fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase3 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable, T1 extends T> GenericEntity<? extends T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable, T1 extends T> GenericEntity<T1> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase4 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable & Comparable, T1 extends T> GenericEntity<? extends T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable, T1 extends T> GenericEntity<? extends T1> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase5 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T extends Serializable & Comparable, T1 extends GenericEntity<T>> GenericEntity<T1> command() { throw new IllegalStateException(); }
        private <T extends Serializable & Comparable, T1 extends GenericEntity<String>> GenericEntity<T1> fallback() { return null;}
    }

    public static class MethodGenericDefinitionFailureCase6 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> GenericEntity<T> command() { throw new IllegalStateException(); }
        private GenericEntity<String> fallback() { return new GenericEntity<String>(); }
    }

    public static class MethodGenericDefinitionFailureCase7 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> Set<T> command() { throw new IllegalStateException(); }
        private <T> List<T> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase8 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> GenericEntity<Set<T>> command() { throw new IllegalStateException(); }
        private <T> GenericEntity<List<T>> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase9 {
        @HystrixCommand(fallbackMethod = "fallback")
        public <T> GenericEntity<List<Set<T>>> command() { throw new IllegalStateException(); }
        private <T> GenericEntity<List<List<T>>> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase10 {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<? super Serializable> command() { throw new IllegalStateException(); }
        private GenericEntity<? super Comparable> fallback() { return null; }
    }

    public static class MethodGenericDefinitionFailureCase11 {
        @HystrixCommand(fallbackMethod = "fallback")
        public Completable command() { throw new IllegalStateException(); }
        private void fallback() { return; }
    }

    /* ====================================================================== */
    /* ===================== GENERIC CLASS DEFINITIONS =====+================ */
    /* =========================== SUCCESS CASES ============================ */

    public static class ClassGenericDefinitionSuccessCase0<T> {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<T> command() { throw new IllegalStateException(); }
        private GenericEntity<T> fallback() { return new GenericEntity<T>(); }
    }

    public static class ClassGenericDefinitionSuccessCase1<T extends Serializable> {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<T> command() { throw new IllegalStateException(); }
        private GenericEntity<T> fallback() { return new GenericEntity<T>(); }
    }

    /* ====================================================================== */
    /* ===================== GENERIC CLASS DEFINITIONS =====+================ */
    /* =========================== FAILURE CASES ============================ */

    public static class ClassGenericDefinitionFailureCase0<T> {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<T> command() { throw new IllegalStateException(); }
        private <T> GenericEntity<T> fallback() { return new GenericEntity<T>(); }
    }

    public static class ClassGenericDefinitionFailureCase1<T extends Serializable> {
        @HystrixCommand(fallbackMethod = "fallback")
        public GenericEntity<T> command() { throw new IllegalStateException(); }
        private <T extends Serializable> GenericEntity<T> fallback() { return new GenericEntity<T>(); }
    }

    public static class GenericEntity<T> {
    }

    private static Object executeCommand(Object proxy) {
        try {
            Method method = proxy.getClass().getDeclaredMethod("command");
            return method.invoke(proxy);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof FallbackDefinitionException) {
                throw (FallbackDefinitionException) t;
            } else throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
