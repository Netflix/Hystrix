package com.netflix.hystrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netflix.hystrix.HystrixCommandTest.*;

public class HystrixRequestLogTest {

    private static final String DIGITS_REGEX = "\\[\\d+";

    @Before
    public void prepareForTest() {
        cleanup();
        /* we must call this to simulate a new request lifecycle running and clearing caches */
        HystrixRequestContext.initializeContext();
    }

    @After
    public void cleanup() {
        // instead of storing the reference from initialize we'll just get the current state and shutdown
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            // it could have been set NULL by the test
            HystrixRequestContext.getContextForCurrentThread().shutdown();
        }

        // force properties to be clean as well
        ConfigurationManager.getConfigInstance().clear();

        HystrixCommandKey key = Hystrix.getCurrentThreadExecutingCommand();
        if (key != null) {
            System.out.println("WARNING: Hystrix.getCurrentThreadExecutingCommand() should be null but got: " + key + ". Can occur when calling queue() and never retrieving.");
        }
    }


    @Test
    public void testSuccess() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            new TestCommand("A", false, true).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[SUCCESS][ms]", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testSuccessFromCache() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 success
            new TestCommand("A", false, true).execute();
            // 4 success from cache
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[SUCCESS][ms], TestCommand[SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testFailWithFallbackSuccess() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 failure
            new TestCommand("A", true, false).execute();
            // 4 failures from cache
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[FAILURE, FALLBACK_SUCCESS][ms], TestCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testFailWithFallbackFailure() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 failure
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            // 1 failure from cache
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testMultipleCommands() {

        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

            // 1 success
            new TestCommand("GetData", "A", false, false).execute();

            // 1 success
            new TestCommand("PutData", "B", false, false).execute();

            // 1 success
            new TestCommand("GetValues", "C", false, false).execute();

            // 1 success from cache
            new TestCommand("GetValues", "C", false, false).execute();

            // 1 failure
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            // 1 failure from cache
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("GetData[SUCCESS][ms], PutData[SUCCESS][ms], GetValues[SUCCESS][ms], GetValues[SUCCESS, RESPONSE_FROM_CACHE][ms], TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
        } finally {
            context.shutdown();
        }

    }

    @Test
    public void testMaxLimit() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            for (int i = 0; i < HystrixRequestLog.MAX_STORAGE; i++) {
                new TestCommand("A", false, true).execute();
            }
            // then execute again some more
            for (int i = 0; i < 10; i++) {
                new TestCommand("A", false, true).execute();
            }

            assertEquals(HystrixRequestLog.MAX_STORAGE, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        } finally {
            context.shutdown();
        }
    }

    /**
     * Invoke a single command that has fixed 50ms overhead and verify the wall clock times match
     * Do not try to reduce the command execution time to anything less than 10 ms, mainly due to variances in OS'es granularity of interrupts
     * On linux it is usually at 1ms (again not guarenteed) but on other systems it could be much larger
     * e.g., if you try to sleep for exactly 1ms then Thread.sleep(1) does not always sleep for 1ms it could be more it could be less
     * more often than not it would be more
     *
     * |-----Command1-50ms-----|
     * Total Wall clock Execution time for commands = ~50ms
     *
     */
    //@Test
    public void testSingleCommandForWallClockTime()
    {
        TestCommandFixedDelay command;
        command = new TestCommandFixedDelay();

        //System.out.println("testSingleCommandForWallClockTime = " + System.currentTimeMillis());
        boolean b = command.execute();

        long testMethodWallClockTime = 50;

        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime  - wallClocktime) +
                        " tolerance = " + (long)(testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime  - wallClocktime) <= (long)(testMethodWallClockTime * 0.3)) );

    }

    /**
     * |-----Command1-50ms-----|------------sleep-50ms-------------|-----Command2-50ms-----|
     * Total Wall clock Execution time for commands = ~100ms
     *
     *
     */

    @Test
    public void testTwoSequentialCommandForWallClockTime()
    {
        //System.out.println("testTwoSequentialCommandForWallClockTime = " + System.currentTimeMillis());
        TestCommandFixedDelay cmd1;
        cmd1 = new TestCommandFixedDelay();
        boolean b1 = cmd1.execute();


        long testMethodWallClockTime = 100;
        // sleep for 40ms before executing cmd2.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestCommandFixedDelay cmd2;
        cmd2 = new TestCommandFixedDelay();
        boolean b2 = cmd2.execute();

        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );

    }

    /**
     * |------Command1-50ms-------|
     *     |Command2-20ms|
     * Total Wall clock Execution time for commands = ~50ms
    */
    @Test
    public void testTwoCompletelyOverlappingCommandsForWallClockTime()
    {
        //System.out.println("testTwoCompletelyOverlappingCommandsForWallClockTime = " + System.currentTimeMillis());

        TestCommandFixedDelay cmd1;
        cmd1 = new TestCommandFixedDelay();
        Future<Boolean> f = cmd1.queue();



        long testMethodWallClockTime = 50;
        // sleep for 40ms before executing cmd2.
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestCommandFixedDelay cmd2;
        cmd2 = new TestCommandFixedDelay();
        cmd2.setExecutionTime(20);
        boolean b2 = cmd2.execute();

        boolean b1 = false;
        try {
            Thread.sleep(40);
            b1 = f.get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getMessage(), false);
        }



        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );

    }



    /**
     * |------Command1-100ms-------|
     * |-----50ms-----|------Command2-100ms-------|
     * Total Wall clock Execution time for commands = ~150ms
     */

    @Test
    public void testTwoPartiallyOverlappingCommandsForWallClockTime()
    {

        //System.out.println("testTwoPartiallyOverlappingCommandsForWallClockTime = " + System.currentTimeMillis());
        TestCommandFixedDelay cmd1 ;
        cmd1 = new TestCommandFixedDelay();
        cmd1.setExecutionTime(100);
        Future<Boolean> f = cmd1.queue();



        long testMethodWallClockTime = 150;
        // sleep for 40ms before executing cmd2.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestCommandFixedDelay cmd2;
        cmd2 = new TestCommandFixedDelay();
        cmd2.setExecutionTime(100);
        boolean b2 = cmd2.execute();

        boolean b1 = false;
        try {

            b1 = f.get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getMessage(), false);
        }

        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );
    }


    /**
     *  |-----Command1-50ms-----|---Sleep-50ms----|---IgnoreCommand2-50ms---|
     *  Total Wall clock Execution time for commands = ~50ms
    */
    @Test
    public void testTimeWithIgnoredCommandsForWallClockTime()
    {
        //System.out.println("testTimeWithIgnoredCommandsForWallClockTime = " + System.currentTimeMillis());
        TestCommandFixedDelay cmd1;
        cmd1 = new TestCommandFixedDelay();
        boolean b1 = cmd1.execute();


        long testMethodWallClockTime = 50;
        // sleep for 40ms before executing cmd2.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestIgnoredCommandFixedDelay cmd2;
        cmd2 = new TestIgnoredCommandFixedDelay();
        boolean b2 = cmd2.execute();

        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        List<String> list = new ArrayList<>();
        list.add(cmd2.getCommandKey().name());
        long wallClocktime = log.getWallClockExecutionTime(list);

        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3) +
                        " Ignored command = " + list.get(0),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );
    }


    /**
     * |-----Command1-50ms-----|-----Command2-50ms-----|
     *                                                 |-----Command3-100ms----|
     *                                                 |----50ms----| ------Command4-100ms----|
     *Total Wall clock Execution time for commands = ~250ms
     *
    */
    @Test
    public void testTwoSequentialAndTwoOverlappingCommandsForWallClockTime()
    {
        //System.out.println("testTwoSequentialAndTwoOverlappingCommandsForWallClockTime = " + System.currentTimeMillis());
        TestCommandFixedDelay cmd1;
        cmd1 = new TestCommandFixedDelay();
        boolean b1 = cmd1.execute();


        long testMethodWallClockTime = 250;
        // sleep for 40ms before executing cmd2.
        TestCommandFixedDelay cmd2;
        cmd2 = new TestCommandFixedDelay();
        boolean b2 = cmd2.execute();
        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        TestCommandFixedDelay cmd3;
        cmd3 = new TestCommandFixedDelay();
        cmd3.setExecutionTime(100);
        Future<Boolean> f3 = cmd3.queue();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestCommandFixedDelay cmd4;
        cmd4 = new TestCommandFixedDelay();
        cmd4.setExecutionTime(100);
        boolean b4 = cmd4.execute();

        boolean b3 = false;
        try {

            b3 = f3.get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getMessage(), false);
        }

        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b3);
        assertTrue("command returned false", b4);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );
    }

    /**
     * |-----Command1-50ms-----|-----Command2-50ms-----|
     *                                                 |------Command3-100ms-------|
     *                                                 |---50ms--|--Command4-50ms--|
     *                                                 |--------------Command5-200ms-------------|
     *Total Wall clock Execution time for commands = ~300ms
     *
     */
    @Test
    public void testTwoSequentialAndTwoOverlappingCommandsForWallClockTimeScenario2()
    {
        //System.out.println("testTwoSequentialAndTwoOverlappingCommandsForWallClockTime = " + System.currentTimeMillis());
        TestCommandFixedDelay cmd1;
        cmd1 = new TestCommandFixedDelay();
        boolean b1 = cmd1.execute();


        long testMethodWallClockTime = 300;
        // sleep for 40ms before executing cmd2.
        TestCommandFixedDelay cmd2;
        cmd2 = new TestCommandFixedDelay();
        boolean b2 = cmd2.execute();
        assertTrue("command returned false", b1);
        assertTrue("command returned false", b2);

        TestCommandFixedDelay cmd3;
        cmd3 = new TestCommandFixedDelay();
        cmd3.setExecutionTime(100);
        Future<Boolean> f3 = cmd3.queue();

        TestCommandFixedDelay cmd5;
        cmd5 = new TestCommandFixedDelay();
        cmd5.setExecutionTime(200);
        Future<Boolean> f5 = cmd5.queue();

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            assertTrue(e.getMessage(), false);
        }

        TestCommandFixedDelay cmd4;
        cmd4 = new TestCommandFixedDelay();
        cmd4.setExecutionTime(50);
        boolean b4 = cmd4.execute();

        boolean b3 = false;
        boolean b5 = false;
        try {

            b3 = f3.get();
            b5 = f5.get();
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getMessage(), false);
        }



        HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
        long wallClocktime = log.getWallClockExecutionTime();

        assertTrue("command returned false", b3);
        assertTrue("command returned false", b4);
        assertTrue("command returned false", b5);

        assertTrue("Error calculating wall clock time taken" +
                        " duration computed in test program = " + testMethodWallClockTime +
                        " wall clock reported by HystrixRequestLog = " + wallClocktime +
                        " actuall esecution vs expected diff = " + Math.abs(testMethodWallClockTime - wallClocktime) +
                        " tolerance = " + (long) (testMethodWallClockTime * 0.3),
                (Math.abs(testMethodWallClockTime - wallClocktime) <= (long) (testMethodWallClockTime * 0.3)) );
    }


    private static class TestCommandFixedDelay extends SuccessfulTestCommand {
        private long executionTime = 50;

        public void setExecutionTime(long executionTime)
        {
            this.executionTime = executionTime;
        }
        @Override
        protected Boolean run() {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }
    }

    private static class TestIgnoredCommandFixedDelay extends TestCommandFixedDelay {

        public TestIgnoredCommandFixedDelay()
        {
            super();
        }
    }



    private static class TestCommand extends HystrixCommand<String> {

        private final String value;
        private final boolean fail;
        private final boolean failOnFallback;

        public TestCommand(String commandName, String value, boolean fail, boolean failOnFallback) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand")).andCommandKey(HystrixCommandKey.Factory.asKey(commandName)));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        public TestCommand(String value, boolean fail, boolean failOnFallback) {
            super(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand"));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        @Override
        protected String run() {
            if (fail) {
                throw new RuntimeException("forced failure");
            } else {
                return value;
            }
        }

        @Override
        protected String getFallback() {
            if (failOnFallback) {
                throw new RuntimeException("forced fallback failure");
            } else {
                return value + "-fallback";
            }
        }

        @Override
        protected String getCacheKey() {
            return value;
        }

    }
}