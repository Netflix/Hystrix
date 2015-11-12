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


import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.HystrixCacheKeyGenerator;
import com.netflix.hystrix.contrib.javanica.cache.HystrixRequestCacheManager;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;
import rx.Observable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;

@ThreadSafe
public class GenericHystrixObservableCommand extends HystrixObservableCommand {

    private final CommandActions commandActions;
    private final CacheInvocationContext<CacheResult> cacheResultInvocationContext;
    private final CacheInvocationContext<CacheRemove> cacheRemoveInvocationContext;
    private final List<Class<? extends Throwable>> ignoreExceptions; // todo implement
    private final ExecutionType executionType;
    private final HystrixCacheKeyGenerator defaultCacheKeyGenerator = HystrixCacheKeyGenerator.getInstance(); // todo implement

    protected GenericHystrixObservableCommand(HystrixCommandBuilder builder) {
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
        result = (Observable) commandActions.getCommandAction().execute(executionType);
        flushCache();
        return result;
    }

    @Override
    protected Observable resumeWithFallback() {
        if (commandActions.hasFallbackAction()) {
            return (Observable) commandActions.getFallbackAction().execute(executionType);
        }
        return super.resumeWithFallback();
    }

    /**
     * Clears cache for the specified hystrix command.
     */
    protected void flushCache() {
        if (cacheRemoveInvocationContext != null) {
            HystrixRequestCacheManager.getInstance().clearCache(cacheRemoveInvocationContext);
        }
    }

}
