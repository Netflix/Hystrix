package com.netflix.hystrix.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ExceptionThreadingUtilityTest {
    private final Throwable ex1 = new Throwable("Ex1");
    private final Throwable ex2 = new Throwable("Ex2", ex1);

    public ExceptionThreadingUtilityTest() {
        ex1.initCause(ex2);
    }

    @Test
    public void testAttachCallingThreadStackParentThenChild() {
        ExceptionThreadingUtility.attachCallingThreadStack(ex1, ex2.getStackTrace());
        assertEquals("Ex1", ex1.getMessage());
        assertEquals("Ex2", ex1.getCause().getMessage());
        assertEquals("Ex2", ex2.getMessage());
        assertEquals("Ex1", ex2.getCause().getMessage());
    }

    @Test
    public void testAttachCallingThreadStackChildThenParent() {
        ExceptionThreadingUtility.attachCallingThreadStack(ex2, ex1.getStackTrace());
        assertEquals("Ex1", ex1.getMessage());
        assertEquals("Ex2", ex1.getCause().getMessage());
        assertEquals("Ex2", ex2.getMessage());
        assertEquals("Ex1", ex2.getCause().getMessage());
    }

    @Test
    public void testAttachCallingThreadStackAddExceptionsToEachOther() {
        ExceptionThreadingUtility.attachCallingThreadStack(ex1, ex2.getStackTrace());
        ExceptionThreadingUtility.attachCallingThreadStack(ex2, ex1.getStackTrace());
        assertEquals("Ex1", ex1.getMessage());
        assertEquals("Ex2", ex2.getMessage());
        assertEquals("Ex2", ex1.getCause().getMessage());
        assertEquals("Ex1", ex2.getCause().getMessage());
    }

    @Test
    public void testAttachCallingThreadStackAddExceptionToItself() {
        ExceptionThreadingUtility.attachCallingThreadStack(ex2, ex2.getStackTrace());
        assertEquals("Ex1", ex1.getMessage());
        assertEquals("Ex2", ex1.getCause().getMessage());
        assertEquals("Ex2", ex2.getMessage());
        assertEquals("Ex1", ex2.getCause().getMessage());
    }

}
