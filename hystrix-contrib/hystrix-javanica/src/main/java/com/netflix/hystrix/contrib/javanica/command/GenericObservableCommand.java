/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;


import com.netflix.hystrix.HystrixExecutable;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.HystrixCacheKeyGenerator;
import com.netflix.hystrix.contrib.javanica.cache.HystrixGeneratedCacheKey;
import com.netflix.hystrix.contrib.javanica.cache.HystrixRequestCacheManager;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import rx.Observable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.utils.CommonUtils.createArgsForFallback;

@ThreadSafe
public class GenericObservableCommand extends HystrixObservableCommand implements HystrixExecutable {

    private final CommandActions commandActions;
    private final CacheInvocationContext<CacheResult> cacheResultInvocationContext;
    private final CacheInvocationContext<CacheRemove> cacheRemoveInvocationContext;
    private final List<Class<? extends Throwable>> ignoreExceptions;
    private final ExecutionType executionType;
    private final HystrixCacheKeyGenerator defaultCacheKeyGenerator = HystrixCacheKeyGenerator.getInstance();

    public GenericObservableCommand(HystrixCommandBuilder builder) {
        super(builder.getSetterBuilder().buildObservableCommandSetter());
        this.commandActions = builder.getCommandActions();
        this.cacheResultInvocationContext = builder.getCacheResultInvocationContext();
        this.cacheRemoveInvocationContext = builder.getCacheRemoveInvocationContext();
        this.ignoreExceptions = builder.getIgnoreExceptions();
        this.executionType = builder.getExecutionType();
    }

    @Override
    protected Observable construct() {
        Observable result;
        try {
            result = (Observable) commandActions.getCommandAction().execute(executionType);
            flushCache();
        } catch (CommandActionExecutionException throwable) {
            Throwable cause = throwable.getCause();
            if (isIgnorable(cause)) {
                throw new HystrixBadRequestException(cause.getMessage(), cause);
            }
            throw throwable;
        }
        return result;
    }

    @Override
    protected Observable resumeWithFallback() {
        if (commandActions.hasFallbackAction()) {
           MetaHolder metaHolder = commandActions.getFallbackAction().getMetaHolder();
            Throwable cause  = getFailedExecutionException();
            if(cause instanceof CommandActionExecutionException){
                cause = cause.getCause();
            }

            Object[] args = createArgsForFallback(metaHolder, cause);
            Object res =  commandActions.getFallbackAction().executeWithArgs(executionType, args);
            if(res instanceof Observable){
                return (Observable) res;
            }else {
                return Observable.just(res);
            }
        }
        return super.resumeWithFallback();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    protected String getCacheKey() {
        String key = null;
        if (cacheResultInvocationContext != null) {
            HystrixGeneratedCacheKey hystrixGeneratedCacheKey =
                    defaultCacheKeyGenerator.generateCacheKey(cacheResultInvocationContext);
            key = hystrixGeneratedCacheKey.getCacheKey();
        }
        return key;
    }

    /**
     * Clears cache for the specified hystrix command.
     */
    protected void flushCache() {
        if (cacheRemoveInvocationContext != null) {
            HystrixRequestCacheManager.getInstance().clearCache(cacheRemoveInvocationContext);
        }
    }

    /**
     * Check whether triggered exception is ignorable.
     *
     * @param throwable the exception occurred during a command execution
     * @return true if exception is ignorable, otherwise - false
     */
    boolean isIgnorable(Throwable throwable) {
        if (ignoreExceptions == null || ignoreExceptions.isEmpty()) {
            return false;
        }
        for (Class<? extends Throwable> ignoreException : ignoreExceptions) {
            if (ignoreException.isAssignableFrom(throwable.getClass())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object execute() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future queue() {
        throw new UnsupportedOperationException();
    }
}
