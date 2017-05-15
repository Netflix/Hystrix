package com.netflix.hystrix.contrib.javanica.test.common.fallback;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import org.junit.Before;
import org.junit.Test;


import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil.
 */
public abstract class BasicDefaultFallbackTest extends BasicHystrixTest {

    private ServiceWithDefaultFallback serviceWithDefaultFallback;
    private ServiceWithDefaultCommandFallback serviceWithDefaultCommandFallback;

    protected abstract ServiceWithDefaultFallback createServiceWithDefaultFallback();

    protected abstract ServiceWithDefaultCommandFallback serviceWithDefaultCommandFallback();

    @Before
    public void setUp() throws Exception {
        serviceWithDefaultFallback = createServiceWithDefaultFallback();
        serviceWithDefaultCommandFallback = serviceWithDefaultCommandFallback();
    }

    @Test
    public void testClassScopeDefaultFallback() {
        String res = serviceWithDefaultFallback.requestString("");
        assertEquals(ServiceWithDefaultFallback.DEFAULT_RESPONSE, res);
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("requestString", command.getCommandKey().name());

        // confirm that 'requestString' command has failed
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that default fallback waw successful
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testSpecificCommandFallbackOverridesDefault() {
        Integer res = serviceWithDefaultFallback.commandWithSpecificFallback("");
        assertEquals(Integer.valueOf(res), res);
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("commandWithSpecificFallback", command.getCommandKey().name());

        // confirm that 'commandWithSpecificFallback' command has failed
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that default fallback waw successful
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }


    @Test
    public void testCommandScopeDefaultFallback() {
        Long res = serviceWithDefaultFallback.commandWithDefaultFallback(1L);
        assertEquals(Long.valueOf(0L), res);
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("commandWithDefaultFallback", command.getCommandKey().name());

        // confirm that 'requestString' command has failed
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that default fallback waw successful
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testClassScopeCommandDefaultFallback() {
        String res = serviceWithDefaultCommandFallback.requestString("");
        assertEquals(ServiceWithDefaultFallback.DEFAULT_RESPONSE, res);


        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> requestStringCommand = getHystrixCommandByKey("requestString");
        com.netflix.hystrix.HystrixInvokableInfo fallback = getHystrixCommandByKey("classDefaultFallback");

        assertEquals("requestString", requestStringCommand.getCommandKey().name());
        // confirm that command has failed
        assertTrue(requestStringCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(requestStringCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        // confirm that fallback was successful
        assertTrue(fallback.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testCommandScopeCommandDefaultFallback() {
        Long res = serviceWithDefaultCommandFallback.commandWithDefaultFallback(1L);
        assertEquals(Long.valueOf(0L), res);


        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> requestStringCommand = getHystrixCommandByKey("commandWithDefaultFallback");
        com.netflix.hystrix.HystrixInvokableInfo fallback = getHystrixCommandByKey("defaultCommandFallback");

        assertEquals("commandWithDefaultFallback", requestStringCommand.getCommandKey().name());
        // confirm that command has failed
        assertTrue(requestStringCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(requestStringCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        // confirm that fallback was successful
        assertTrue(fallback.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    // case when class level default fallback
    @DefaultProperties(defaultFallback = "classDefaultFallback")
    public static class ServiceWithDefaultFallback {
        static final String DEFAULT_RESPONSE = "class_def";

        @HystrixCommand
        public String requestString(String str) {
            throw new RuntimeException();
        }

        public String classDefaultFallback() {
            return DEFAULT_RESPONSE;
        }

        @HystrixCommand(defaultFallback = "defaultCommandFallback")
        Long commandWithDefaultFallback(long l){
            throw new RuntimeException();
        }

        Long defaultCommandFallback(){
            return 0L;
        }

        @HystrixCommand(fallbackMethod = "specificFallback")
        Integer commandWithSpecificFallback(String str) {
            throw new RuntimeException();
        }

        Integer specificFallback(String str) {
            return 0;
        }
    }

    // class level default fallback is annotated with @HystrixCommand and should be executed as Hystrix command
    @DefaultProperties(defaultFallback = "classDefaultFallback")
    public static class ServiceWithDefaultCommandFallback {
        static final String DEFAULT_RESPONSE = "class_def";

        @HystrixCommand
        public String requestString(String str) {
            throw new RuntimeException();
        }

        @HystrixCommand
        public String classDefaultFallback() {
            return DEFAULT_RESPONSE;
        }

        @HystrixCommand(defaultFallback = "defaultCommandFallback")
        Long commandWithDefaultFallback(long l){
            throw new RuntimeException();
        }

        @HystrixCommand(fallbackMethod = "defaultCommandFallback")
        Long defaultCommandFallback(){
            return 0L;
        }
    }

}
