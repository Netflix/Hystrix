package com.netflix.hystrix.contrib.javanica.test.common.error;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixException;
import com.netflix.hystrix.exception.HystrixRuntimeException;

import rx.Observable;
import rx.observers.TestSubscriber;

/**
 * Created by Mike Cowan
 */
public abstract class BasicDefaultRaiseHystrixExceptionsTest {

    private Service service;

    @Before
    public void setUp() throws Exception {
        service = createService();
    }

    protected abstract Service createService();

    @Test(expected = BadRequestException.class)
    public void testDefaultIgnoreException() {
        service.commandInheritsDefaultIgnoreExceptions();
    }

    @Test(expected = SpecificException.class)
    public void testCommandOverridesDefaultIgnoreExceptions() {
        service.commandOverridesDefaultIgnoreExceptions(SpecificException.class);
    }

    @Test(expected = HystrixRuntimeException.class)
    public void testCommandOverridesDefaultIgnoreExceptions_nonIgnoreExceptionShouldBePropagated() {
        // method throws BadRequestException that isn't ignored
        service.commandOverridesDefaultIgnoreExceptions(BadRequestException.class);
    }

    @Ignore // https://github.com/Netflix/Hystrix/issues/993#issuecomment-229542203
    @Test(expected = BadRequestException.class)
    public void testFallbackCommandInheritsDefaultIgnoreException() {
        service.commandWithFallbackInheritsDefaultIgnoreExceptions();
    }

    @Ignore // https://github.com/Netflix/Hystrix/issues/993#issuecomment-229542203
    @Test(expected = SpecificException.class)
    public void testFallbackCommandOverridesDefaultIgnoreExceptions() {
        service.commandWithFallbackOverridesDefaultIgnoreExceptions(SpecificException.class);
    }

    @Test(expected = HystrixRuntimeException.class)
    public void testFallbackCommandOverridesDefaultIgnoreExceptions_nonIgnoreExceptionShouldBePropagated() {
        service.commandWithFallbackOverridesDefaultIgnoreExceptions(BadRequestException.class);
    }

    @Test(expected = HystrixRuntimeException.class)
    public void testRaiseHystrixRuntimeException() {
        service.commandShouldRaiseHystrixRuntimeException();
    }

    @Test
    public void testObservableRaiseHystrixRuntimeException() {
        TestSubscriber<Void> testSubscriber = new TestSubscriber<Void>();
        service.observableCommandShouldRaiseHystrixRuntimeException().subscribe(testSubscriber);
        testSubscriber.assertError(HystrixRuntimeException.class);
    }

    @DefaultProperties(ignoreExceptions = BadRequestException.class, raiseHystrixExceptions = {HystrixException.RUNTIME_EXCEPTION})
    public static class Service {
        @HystrixCommand
        public Object commandShouldRaiseHystrixRuntimeException() throws SpecificException {
            throw new SpecificException("from 'commandShouldRaiseHystrixRuntimeException'");
        }

        @HystrixCommand
        public Observable<Void> observableCommandShouldRaiseHystrixRuntimeException() throws SpecificException {
            return Observable.error(new SpecificException("from 'observableCommandShouldRaiseHystrixRuntimeException'"));
        }

        @HystrixCommand
         public Object commandInheritsDefaultIgnoreExceptions() throws BadRequestException {
            // this exception will be ignored (wrapped in HystrixBadRequestException) because specified in default ignore exceptions
            throw new BadRequestException("from 'commandInheritsIgnoreExceptionsFromDefault'");
        }

        @HystrixCommand(ignoreExceptions = SpecificException.class)
        public Object commandOverridesDefaultIgnoreExceptions(Class<? extends Throwable> errorType) throws BadRequestException, SpecificException  {
            if(errorType.equals(BadRequestException.class)){
                // isn't ignored because command doesn't specify this exception type in 'ignoreExceptions'
                throw new BadRequestException("from 'commandOverridesDefaultIgnoreExceptions', cause: " + errorType.getSimpleName());
            }
            // something went wrong, this error is ignored because specified in the command's ignoreExceptions
            throw new SpecificException("from 'commandOverridesDefaultIgnoreExceptions', cause: " + errorType.getSimpleName());
        }

        @HystrixCommand(fallbackMethod = "fallbackInheritsDefaultIgnoreExceptions")
        public Object commandWithFallbackInheritsDefaultIgnoreExceptions() throws SpecificException {
            // isn't ignored, need to trigger fallback
            throw new SpecificException("from 'commandWithFallbackInheritsDefaultIgnoreExceptions'");
        }

        @HystrixCommand
        private Object fallbackInheritsDefaultIgnoreExceptions() throws BadRequestException {
            // should be ignored because specified in global ignore exception, fallback command inherits default ignore exceptions
            throw new BadRequestException("from 'fallbackInheritsDefaultIgnoreExceptions'");
        }

        @HystrixCommand(fallbackMethod = "fallbackOverridesDefaultIgnoreExceptions")
        public Object commandWithFallbackOverridesDefaultIgnoreExceptions(Class<? extends Throwable> errorType) {
            // isn't ignored, need to trigger fallback
            throw new SpecificException();
        }

        @HystrixCommand(ignoreExceptions = SpecificException.class)
        private Object fallbackOverridesDefaultIgnoreExceptions(Class<? extends Throwable> errorType) {
            if(errorType.equals(BadRequestException.class)){
                // isn't ignored because fallback doesn't specify this exception type in 'ignoreExceptions'
                throw new BadRequestException("from 'fallbackOverridesDefaultIgnoreExceptions', cause: " + errorType.getSimpleName());
            }
            // something went wrong, this error is ignored because specified in the fallback's ignoreExceptions
            throw new SpecificException("from 'commandOverridesDefaultIgnoreExceptions', cause: " + errorType.getSimpleName());
        }
    }

    public static final class BadRequestException extends RuntimeException {
        public BadRequestException() {
        }

        public BadRequestException(String message) {
            super(message);
        }
    }

    public static final class SpecificException extends RuntimeException {
        public SpecificException() {
        }

        public SpecificException(String message) {
            super(message);
        }
    }
}
