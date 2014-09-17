package com.netflix.hystrix;

import static org.junit.Assert.*;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCommandTest.TestHystrixCommand;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapser;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

public class HystrixCollapserTest {
    static AtomicInteger counter = new AtomicInteger();

    @Before
    public void init() {
        counter.set(0);
        // since we're going to modify properties of the same class between tests, wipe the cache each time
        HystrixCollapser.reset();
        /* we must call this to simulate a new request lifecycle running and clearing caches */
        HystrixRequestContext.initializeContext();
    }

    @After
    public void cleanup() {
        // instead of storing the reference from initialize we'll just get the current state and shutdown
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            // it may be null if a test shuts the context down manually
            HystrixRequestContext.getContextForCurrentThread().shutdown();
        }
    }

    @Test
    public void testTwoRequests() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testMultipleBatches() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());

        assertEquals(1, counter.get());

        // now request more
        Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("3", response3.get());

        // we should have had it execute twice now
        assertEquals(2, counter.get());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testMaxRequestsInBatch() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1, 2, 10).queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2, 2, 10).queue();
        Future<String> response3 = new TestRequestCollapser(timer, counter, 3, 2, 10).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());

        // we should have had it execute twice because the batch size was 2
        assertEquals(2, counter.get());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testRequestsOverTime() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        timer.incrementTime(5);
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        assertEquals("1", response1.get());
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());
        assertEquals("4", response4.get());
        assertEquals("5", response5.get());

        System.out.println("number of executions: " + counter.get());
        assertEquals(3, counter.get());
        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testUnsubscribeOnOneDoesntKillBatch() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();

        // kill the first
        response1.cancel(true);

        timer.incrementTime(10); // let time pass that equals the default delay/period

        // the first is cancelled so should return null
        try {
            response1.get();
            fail("expect CancellationException after cancelling");
        } catch (CancellationException e) {
            // expected
        }
        // we should still get a response on the second
        assertEquals("2", response2.get());

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testShardedRequests() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestShardedRequestCollapser(timer, counter, "1a").queue();
        Future<String> response2 = new TestShardedRequestCollapser(timer, counter, "2b").queue();
        Future<String> response3 = new TestShardedRequestCollapser(timer, counter, "3b").queue();
        Future<String> response4 = new TestShardedRequestCollapser(timer, counter, "4a").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1a", response1.get());
        assertEquals("2b", response2.get());
        assertEquals("3b", response3.get());
        assertEquals("4a", response4.get());

        /* we should get 2 batches since it gets sharded */
        assertEquals(2, counter.get());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testRequestScope() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, "1").queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, "2").queue();

        // simulate a new request
        RequestCollapserFactory.resetRequest();

        Future<String> response3 = new TestRequestCollapser(timer, counter, "3").queue();
        Future<String> response4 = new TestRequestCollapser(timer, counter, "4").queue();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());
        assertEquals("4", response4.get());

        // 2 different batches should execute, 1 per request
        assertEquals(2, counter.get());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testGlobalScope() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestGloballyScopedRequestCollapser(timer, counter, "1").queue();
        Future<String> response2 = new TestGloballyScopedRequestCollapser(timer, counter, "2").queue();

        // simulate a new request
        RequestCollapserFactory.resetRequest();

        Future<String> response3 = new TestGloballyScopedRequestCollapser(timer, counter, "3").queue();
        Future<String> response4 = new TestGloballyScopedRequestCollapser(timer, counter, "4").queue();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());
        assertEquals("4", response4.get());

        // despite having cleared the cache in between we should have a single execution because this is on the global not request cache
        assertEquals(1, counter.get());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testErrorHandlingViaFutureException() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapserWithFaultyCreateCommand(timer, counter, "1").queue();
        Future<String> response2 = new TestRequestCollapserWithFaultyCreateCommand(timer, counter, "2").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }
        try {
            response2.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }

        assertEquals(0, counter.get());
        assertEquals(0, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testErrorHandlingWhenMapToResponseFails() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapserWithFaultyMapToResponse(timer, counter, "1").queue();
        Future<String> response2 = new TestRequestCollapserWithFaultyMapToResponse(timer, counter, "2").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }
        try {
            response2.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }

        // the batch failed so no executions
        assertEquals(0, counter.get());
        // but it still executed the command once
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testRequestVariableLifecycle1() throws Exception {
        // simulate request lifecycle
        HystrixRequestContext requestContext = HystrixRequestContext.initializeContext();

        // do actual work
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        timer.incrementTime(5);
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        assertEquals("1", response1.get());
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());
        assertEquals("4", response4.get());
        assertEquals("5", response5.get());

        // each task should have been executed 3 times
        for (TestCollapserTimer.ATask t : timer.tasks) {
            assertEquals(3, t.task.count.get());
        }

        System.out.println("timer.tasks.size() A: " + timer.tasks.size());
        System.out.println("tasks in test: " + timer.tasks);

        // simulate request lifecycle
        requestContext.shutdown();

        System.out.println("timer.tasks.size() B: " + timer.tasks.size());

        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

        assertNotNull(rv);
        // they should have all been removed as part of ThreadContext.remove()
        assertEquals(0, timer.tasks.size());
    }

    @Test
    public void testRequestVariableLifecycle2() throws Exception {
        // simulate request lifecycle
        HystrixRequestContext requestContext = HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        final ConcurrentLinkedQueue<Future<String>> responses = new ConcurrentLinkedQueue<Future<String>>();
        ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<Thread>();

        // kick off work (simulating a single request with multiple threads)
        for (int t = 0; t < 5; t++) {
            Thread th = new Thread(new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

                @Override
                public void run() {
                    for (int i = 0; i < 100; i++) {
                        responses.add(new TestRequestCollapser(timer, counter, 1).queue());
                    }
                }
            }));

            threads.add(th);
            th.start();
        }

        for (Thread th : threads) {
            // wait for each thread to finish
            th.join();
        }

        // we expect 5 threads * 100 responses each
        assertEquals(500, responses.size());

        for (Future<String> f : responses) {
            // they should not be done yet because the counter hasn't incremented
            assertFalse(f.isDone());
        }

        timer.incrementTime(5);
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        for (Future<String> f : responses) {
            assertEquals("1", f.get());
        }
        assertEquals("2", response2.get());
        assertEquals("3", response3.get());
        assertEquals("4", response4.get());
        assertEquals("5", response5.get());

        // each task should have been executed 3 times
        for (TestCollapserTimer.ATask t : timer.tasks) {
            assertEquals(3, t.task.count.get());
        }

        // simulate request lifecycle
        requestContext.shutdown();

        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

        assertNotNull(rv);
        // they should have all been removed as part of ThreadContext.remove()
        assertEquals(0, timer.tasks.size());
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCache1() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("A", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should have executed a command once
        assertEquals(1, counter.get());

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, counter.get());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[1])[0];
        System.out.println("command.getExecutionEvents(): " + command.getExecutionEvents());
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should have executed a command once
        assertEquals(1, counter.get());

        Future<String> f3 = command1.queue();
        Future<String> f4 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get());
            assertEquals("B", f4.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, counter.get());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[1])[0];
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);
        SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("B", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should have executed a command once
        assertEquals(1, counter.get());

        Future<String> f4 = command1.queue();
        Future<String> f5 = command2.queue();
        Future<String> f6 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f4.get());
            assertEquals("B", f5.get());
            assertEquals("B", f6.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, counter.get());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[1])[0];
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", false);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", false);
        SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", false);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("B", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should have executed a command once
        assertEquals(1, counter.get());

        Future<String> f4 = command1.queue();
        Future<String> f5 = command2.queue();
        Future<String> f6 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f4.get());
            assertEquals("B", f5.get());
            assertEquals("B", f6.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // request caching is turned off on this so we expect 2 command executions
        assertEquals(2, counter.get());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        // we expect to see it with SUCCESS and COLLAPSED and both
        HystrixExecutableInfo<?> commandA = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[2])[0];
        assertEquals(2, commandA.getExecutionEvents().size());
        assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        // we expect to see it with SUCCESS and COLLAPSED and both
        HystrixExecutableInfo<?> commandB = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[2])[1];
        assertEquals(2, commandB.getExecutionEvents().size());
        assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
    }

    /**
     * Test that a command that throws an Exception when cached will re-throw the exception.
     */
    @Test
    public void testRequestCacheWithException() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

        final TestCollapserTimer timer = new TestCollapserTimer();
        // pass in 'null' which will cause an NPE to be thrown
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("A", f2.get());
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // this should be 0 because we never complete execution
        assertEquals(0, counter.get());

        // it should have executed 1 command
        assertEquals(1, commands.size());
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);
        Future<String> f3 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get());
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // this should be 0 because we never complete execution
        assertEquals(0, counter.get());

        // it should still be 1 ... no new executions
        assertEquals(1, commands.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixExecutableInfo<?>[1])[0];
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
    }

    /**
     * Test that a command that times out will still be cached and when retrieved will re-throw the exception.
     */
    @Test
    public void testRequestCacheWithTimeout() {
        // simulate request lifecycle
        HystrixRequestContext.initializeContext();

        ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

        final TestCollapserTimer timer = new TestCollapserTimer();
        // pass in 'null' which will cause an NPE to be thrown
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "TIMEOUT", true, commands);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "TIMEOUT", true, commands);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get());
            assertEquals("A", f2.get());
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // this should be 0 because we never complete execution
        assertEquals(0, counter.get());

        // it should have executed 1 command
        assertEquals(1, commands.size());
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.TIMEOUT));
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get());
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // this should be 0 because we never complete execution
        assertEquals(0, counter.get());

        // it should still be 1 ... no new executions
        assertEquals(1, commands.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test how the collapser behaves when the circuit is short-circuited
     */
    @Test
    public void testRequestWithCommandShortCircuited() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<String> response1 = new TestRequestCollapserWithShortCircuitedCommand(timer, counter, "1").queue();
        Future<String> response2 = new TestRequestCollapserWithShortCircuitedCommand(timer, counter, "2").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            //                e.printStackTrace();
            // what we expect
        }
        try {
            response2.get();
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            //                e.printStackTrace();
            // what we expect
        }

        assertEquals(0, counter.get());
        // it will execute once (short-circuited)
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a Void response type - null being set as response.
     * 
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing1() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<Void> response1 = new TestCollapserWithVoidResponseType(timer, counter, 1).queue();
        Future<Void> response2 = new TestCollapserWithVoidResponseType(timer, counter, 2).queue();
        timer.incrementTime(100); // let time pass that equals the default delay/period

        // normally someone wouldn't wait on these, but we need to make sure they do in fact return
        // and not block indefinitely in case someone does call get()
        assertEquals(null, response1.get());
        assertEquals(null, response2.get());

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a Void response type - response never being set in mapResponseToRequest
     * 
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing2() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        Future<Void> response1 = new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, counter, 1).queue();
        Future<Void> response2 = new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, counter, 2).queue();
        timer.incrementTime(100); // let time pass that equals the default delay/period

        // we will fetch one of these just so we wait for completion ... but expect an error
        try {
            assertEquals(null, response1.get());
            fail("expected an error as mapResponseToRequests did not set responses");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().startsWith("No response set by"));
        }

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a Void response type with execute - response being set in mapResponseToRequest to null
     * 
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing3() throws Exception {
        CollapserTimer timer = new RealCollapserTimer();
        assertNull(new TestCollapserWithVoidResponseType(timer, counter, 1).execute());

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    private static class TestRequestCollapser extends HystrixCollapser<List<String>, String, String> {

        private final AtomicInteger count;
        private final String value;
        private ConcurrentLinkedQueue<HystrixCommand<List<String>>> commandsExecuted;

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value) {
            this(timer, counter, String.valueOf(value));
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
            this(timer, counter, value, 10000, 10);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value) {
            this(scope, timer, counter, value, 10000, 10);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            this(timer, counter, value, 10000, 10, executionLog);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, counter, String.valueOf(value), defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(scope, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            this(Scope.REQUEST, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, executionLog);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            // use a CollapserKey based on the CollapserTimer object reference so it's unique for each timer as we don't want caching
            // of properties to occur and we're using the default HystrixProperty which typically does caching
            super(collapserKeyFromString(timer), scope, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(defaultMaxRequestsInBatch).withTimerDelayInMilliseconds(defaultTimerDelayInMilliseconds));
            this.count = counter;
            this.value = value;
            this.commandsExecuted = executionLog;
        }

        @Override
        public String getRequestArgument() {
            return value;
        }

        @Override
        public HystrixCommand<List<String>> createCommand(final Collection<CollapsedRequest<String, String>> requests) {
            /* return a mocked command */
            HystrixCommand<List<String>> command = new TestCollapserCommand(requests);
            if (commandsExecuted != null) {
                commandsExecuted.add(command);
            }
            return command;
        }

        @Override
        public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
            // count how many times a batch is executed (this method is executed once per batch)
            System.out.println("increment count: " + count.incrementAndGet());

            // for simplicity I'll assume it's a 1:1 mapping between lists ... in real implementations they often need to index to maps
            // to allow random access as the response size does not match the request size
            if (batchResponse.size() != requests.size()) {
                throw new RuntimeException("lists don't match in size => " + batchResponse.size() + " : " + requests.size());
            }
            int i = 0;
            for (CollapsedRequest<String, String> request : requests) {
                request.setResponse(batchResponse.get(i++));
            }

        }

    }

    /**
     * Shard on the artificially provided 'type' variable.
     */
    private static class TestShardedRequestCollapser extends TestRequestCollapser {

        public TestShardedRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
            super(timer, counter, value);
        }

        @Override
        protected Collection<Collection<CollapsedRequest<String, String>>> shardRequests(Collection<CollapsedRequest<String, String>> requests) {
            Collection<CollapsedRequest<String, String>> typeA = new ArrayList<CollapsedRequest<String, String>>();
            Collection<CollapsedRequest<String, String>> typeB = new ArrayList<CollapsedRequest<String, String>>();

            for (CollapsedRequest<String, String> request : requests) {
                if (request.getArgument().endsWith("a")) {
                    typeA.add(request);
                } else if (request.getArgument().endsWith("b")) {
                    typeB.add(request);
                }
            }

            ArrayList<Collection<CollapsedRequest<String, String>>> shards = new ArrayList<Collection<CollapsedRequest<String, String>>>();
            shards.add(typeA);
            shards.add(typeB);
            return shards;
        }

    }

    /**
     * Test the global scope
     */
    private static class TestGloballyScopedRequestCollapser extends TestRequestCollapser {

        public TestGloballyScopedRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
            super(Scope.GLOBAL, timer, counter, value);
        }

    }

    /**
     * Throw an exception when creating a command.
     */
    private static class TestRequestCollapserWithFaultyCreateCommand extends TestRequestCollapser {

        public TestRequestCollapserWithFaultyCreateCommand(TestCollapserTimer timer, AtomicInteger counter, String value) {
            super(timer, counter, value);
        }

        @Override
        public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
            throw new RuntimeException("some failure");
        }

    }

    /**
     * Throw an exception when creating a command.
     */
    private static class TestRequestCollapserWithShortCircuitedCommand extends TestRequestCollapser {

        public TestRequestCollapserWithShortCircuitedCommand(TestCollapserTimer timer, AtomicInteger counter, String value) {
            super(timer, counter, value);
        }

        @Override
        public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
            // args don't matter as it's short-circuited
            return new ShortCircuitedCommand();
        }

    }

    /**
     * Throw an exception when mapToResponse is invoked
     */
    private static class TestRequestCollapserWithFaultyMapToResponse extends TestRequestCollapser {

        public TestRequestCollapserWithFaultyMapToResponse(TestCollapserTimer timer, AtomicInteger counter, String value) {
            super(timer, counter, value);
        }

        @Override
        public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
            // pretend we blow up with an NPE
            throw new NullPointerException("batchResponse was null and we blew up");
        }

    }

    private static class TestCollapserCommand extends TestHystrixCommand<List<String>> {

        private final Collection<CollapsedRequest<String, String>> requests;

        TestCollapserCommand(Collection<CollapsedRequest<String, String>> requests) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(50)));
            this.requests = requests;
        }

        @Override
        protected List<String> run() {
            System.out.println(">>> TestCollapserCommand run() ... batch size: " + requests.size());
            // simulate a batch request
            ArrayList<String> response = new ArrayList<String>();
            for (CollapsedRequest<String, String> request : requests) {
                if (request.getArgument() == null) {
                    throw new NullPointerException("Simulated Error");
                }
                if (request.getArgument() == "TIMEOUT") {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                response.add(request.getArgument());
            }
            return response;
        }

    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCollapsedCommand extends TestRequestCollapser {

        private final boolean cacheEnabled;

        public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, AtomicInteger counter, String value, boolean cacheEnabled) {
            super(timer, counter, value);
            this.cacheEnabled = cacheEnabled;
        }

        public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, AtomicInteger counter, String value, boolean cacheEnabled, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            super(timer, counter, value, executionLog);
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        public String getCacheKey() {
            if (cacheEnabled)
                return "aCacheKey_" + super.value;
            else
                return null;
        }
    }

    private static class ShortCircuitedCommand extends HystrixCommand<List<String>> {

        protected ShortCircuitedCommand() {
            super(HystrixCommand.Setter.withGroupKey(
                    HystrixCommandGroupKey.Factory.asKey("shortCircuitedCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandPropertiesTest
                            .getUnitTestPropertiesSetter()
                            .withCircuitBreakerForceOpen(true)));
        }

        @Override
        protected List<String> run() throws Exception {
            System.out.println("*** execution (this shouldn't happen)");
            // this won't ever get called as we're forcing short-circuiting
            ArrayList<String> values = new ArrayList<String>();
            values.add("hello");
            return values;
        }

    }

    private static class FireAndForgetCommand extends HystrixCommand<Void> {

        protected FireAndForgetCommand(List<Integer> values) {
            super(HystrixCommand.Setter.withGroupKey(
                    HystrixCommandGroupKey.Factory.asKey("fireAndForgetCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()));
        }

        @Override
        protected Void run() throws Exception {
            System.out.println("*** FireAndForgetCommand execution: " + Thread.currentThread());
            return null;
        }

    }

    /* package */ static class TestCollapserTimer implements CollapserTimer {

        private final ConcurrentLinkedQueue<ATask> tasks = new ConcurrentLinkedQueue<ATask>();

        @Override
        public Reference<TimerListener> addListener(final TimerListener collapseTask) {
            System.out.println("add listener: " + collapseTask);
            tasks.add(new ATask(new TestTimerListener(collapseTask)));

            /**
             * This is a hack that overrides 'clear' of a WeakReference to match the required API
             * but then removes the strong-reference we have inside 'tasks'.
             * <p>
             * We do this so our unit tests know if the WeakReference is cleared correctly, and if so then the ATack is removed from 'tasks'
             */
            return new SoftReference<TimerListener>(collapseTask) {
                @Override
                public void clear() {
                    System.out.println("tasks: " + tasks);
                    System.out.println("**** clear TimerListener: tasks.size => " + tasks.size());
                    // super.clear();
                    for (ATask t : tasks) {
                        if (t.task.actualListener.equals(collapseTask)) {
                            tasks.remove(t);
                        }
                    }
                }

            };
        }

        /**
         * Increment time by X. Note that incrementing by multiples of delay or period time will NOT execute multiple times.
         * <p>
         * You must call incrementTime multiple times each increment being larger than 'period' on subsequent calls to cause multiple executions.
         * <p>
         * This is because executing multiple times in a tight-loop would not achieve the correct behavior, such as batching, since it will all execute "now" not after intervals of time.
         * 
         * @param timeInMilliseconds
         */
        public synchronized void incrementTime(int timeInMilliseconds) {
            for (ATask t : tasks) {
                t.incrementTime(timeInMilliseconds);
            }
        }

        private static class ATask {
            final TestTimerListener task;
            final int delay = 10;

            // our relative time that we'll use
            volatile int time = 0;
            volatile int executionCount = 0;

            private ATask(TestTimerListener task) {
                this.task = task;
            }

            public synchronized void incrementTime(int timeInMilliseconds) {
                time += timeInMilliseconds;
                if (task != null) {
                    if (executionCount == 0) {
                        System.out.println("ExecutionCount 0 => Time: " + time + " Delay: " + delay);
                        if (time >= delay) {
                            // first execution, we're past the delay time
                            executeTask();
                        }
                    } else {
                        System.out.println("ExecutionCount 1+ => Time: " + time + " Delay: " + delay);
                        if (time >= delay) {
                            // subsequent executions, we're past the interval time
                            executeTask();
                        }
                    }
                }
            }

            private synchronized void executeTask() {
                System.out.println("Executing task ...");
                task.tick();
                this.time = 0; // we reset time after each execution
                this.executionCount++;
                System.out.println("executionCount: " + executionCount);
            }
        }

    }

    private static class TestTimerListener implements TimerListener {

        private final TimerListener actualListener;
        private final AtomicInteger count = new AtomicInteger();

        public TestTimerListener(TimerListener actual) {
            this.actualListener = actual;
        }

        @Override
        public void tick() {
            count.incrementAndGet();
            actualListener.tick();
        }

        @Override
        public int getIntervalTimeInMilliseconds() {
            return 10;
        }

    }

    private static HystrixCollapserKey collapserKeyFromString(final Object o) {
        return new HystrixCollapserKey() {

            @Override
            public String name() {
                return String.valueOf(o);
            }

        };
    }

    private static class TestCollapserWithVoidResponseType extends HystrixCollapser<Void, Void, Integer> {

        private final AtomicInteger count;
        private final Integer value;

        public TestCollapserWithVoidResponseType(CollapserTimer timer, AtomicInteger counter, int value) {
            super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
            this.count = counter;
            this.value = value;
        }

        @Override
        public Integer getRequestArgument() {
            return value;
        }

        @Override
        protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

            ArrayList<Integer> args = new ArrayList<Integer>();
            for (CollapsedRequest<Void, Integer> request : requests) {
                args.add(request.getArgument());
            }
            return new FireAndForgetCommand(args);
        }

        @Override
        protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
            count.incrementAndGet();
            for (CollapsedRequest<Void, Integer> r : requests) {
                r.setResponse(null);
            }
        }

    }

    private static class TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests extends HystrixCollapser<Void, Void, Integer> {

        private final AtomicInteger count;
        private final Integer value;

        public TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(CollapserTimer timer, AtomicInteger counter, int value) {
            super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
            this.count = counter;
            this.value = value;
        }

        @Override
        public Integer getRequestArgument() {
            return value;
        }

        @Override
        protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

            ArrayList<Integer> args = new ArrayList<Integer>();
            for (CollapsedRequest<Void, Integer> request : requests) {
                args.add(request.getArgument());
            }
            return new FireAndForgetCommand(args);
        }

        @Override
        protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
            count.incrementAndGet();
        }

    }

}
