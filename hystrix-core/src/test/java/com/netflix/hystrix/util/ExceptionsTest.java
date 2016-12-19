package com.netflix.hystrix.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExceptionsTest {

    @Test
    public void testCastOfException(){
        Exception exception = new IOException("simulated checked exception message");
        try{
            Exceptions.sneakyThrow(exception);
            fail();
        } catch(Exception e){
            assertTrue(e instanceof  IOException);
        }
    }
}
