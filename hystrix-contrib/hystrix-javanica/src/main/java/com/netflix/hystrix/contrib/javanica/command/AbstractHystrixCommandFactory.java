/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;


import com.google.common.collect.Maps;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContextFactory.createCacheRemoveInvocationContext;
import static com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContextFactory.createCacheResultInvocationContext;
import static com.netflix.hystrix.contrib.javanica.utils.EnvUtils.isCompileWeaving;
import static com.netflix.hystrix.contrib.javanica.utils.ajc.AjcUtils.getAjcMethodAroundAdvice;

/**
 * Base implementation of {@link HystrixCommandFactory} interface.
 *
 * @param <T> the type of Hystrix command
 */
public abstract class AbstractHystrixCommandFactory<T extends AbstractHystrixCommand>
        implements HystrixCommandFactory<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    public T create(MetaHolder metaHolder,
                    Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        Validate.notNull(metaHolder.getHystrixCommand(), "hystrixCommand cannot be null");
        String groupKey = StringUtils.isNotEmpty(metaHolder.getHystrixCommand().groupKey()) ?
                metaHolder.getHystrixCommand().groupKey()
                : metaHolder.getDefaultGroupKey();
        String commandKey = StringUtils.isNotEmpty(metaHolder.getHystrixCommand().commandKey()) ?
                metaHolder.getHystrixCommand().commandKey()
                : metaHolder.getDefaultCommandKey();

        CommandSetterBuilder setterBuilder = new CommandSetterBuilder();
        setterBuilder.commandKey(commandKey);
        setterBuilder.groupKey(groupKey);
        setterBuilder.threadPoolKey(metaHolder.getHystrixCommand().threadPoolKey());
        setterBuilder.threadPoolProperties(metaHolder.getHystrixCommand().threadPoolProperties());
        setterBuilder.commandProperties(metaHolder.getHystrixCommand().commandProperties());

        Map<String, Object> commandProperties = getCommandProperties(metaHolder.getHystrixCommand());
        CommandAction commandAction = new MethodExecutionAction(metaHolder.getObj(), metaHolder.getMethod(), metaHolder.getArgs(), metaHolder);
        CommandAction fallbackAction = createFallbackAction(metaHolder, collapsedRequests);
        CommandActions commandActions = CommandActions.builder().commandAction(commandAction)
                .fallbackAction(fallbackAction).build();

        HystrixCommandBuilder hystrixCommandBuilder = new HystrixCommandBuilder().setterBuilder(setterBuilder)
                .commandActions(commandActions)
                .commandProperties(commandProperties)
                .collapsedRequests(collapsedRequests)
                .cacheResultInvocationContext(createCacheResultInvocationContext(metaHolder))
                .cacheRemoveInvocationContext(createCacheRemoveInvocationContext(metaHolder))
                .ignoreExceptions(metaHolder.getHystrixCommand().ignoreExceptions())
                .executionType(metaHolder.getExecutionType());
        return create(hystrixCommandBuilder);
    }

    CommandAction createFallbackAction(MetaHolder metaHolder,
                                       Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {

        FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(metaHolder.getObj().getClass(), metaHolder.getMethod(), metaHolder.isExtendedFallback());
        fallbackMethod.validateReturnType(metaHolder.getMethod());
        CommandAction fallbackAction = null;
        if (fallbackMethod.isPresent()) {

            Method fMethod = fallbackMethod.getMethod();
            if (fallbackMethod.isCommand()) {
                fMethod.setAccessible(true);
                MetaHolder fmMetaHolder = MetaHolder.builder()
                        .obj(metaHolder.getObj())
                        .method(fMethod)
                        .ajcMethod(getAjcMethod(metaHolder.getObj(), fMethod))
                        .args(metaHolder.getArgs())
                        .fallback(true)
                        .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                        .fallbackMethod(fMethod)
                        .extendedFallback(fallbackMethod.isExtended())
                        .fallbackExecutionType(fallbackMethod.getExecutionType())
                        .extendedParentFallback(metaHolder.isExtendedFallback())
                        .defaultCommandKey(fMethod.getName())
                        .defaultGroupKey(metaHolder.getDefaultGroupKey())
                        .hystrixCollapser(metaHolder.getHystrixCollapser())
                        .hystrixCommand(fMethod.getAnnotation(HystrixCommand.class)).build();
                fallbackAction = new LazyCommandExecutionAction(GenericHystrixCommandFactory.getInstance(), fmMetaHolder, collapsedRequests);
            } else {
                MetaHolder fmMetaHolder = MetaHolder.builder()
                        .obj(metaHolder.getObj())
                        .method(fMethod)
                        .fallbackExecutionType(ExecutionType.SYNCHRONOUS)
                        .extendedFallback(fallbackMethod.isExtended())
                        .extendedParentFallback(metaHolder.isExtendedFallback())
                        .ajcMethod(null) // if fallback method isn't annotated with command annotation then we don't need to get ajc method for this
                        .args(metaHolder.getArgs()).build();

                fallbackAction = new MethodExecutionAction(fmMetaHolder.getObj(), fMethod, fmMetaHolder.getArgs(), fmMetaHolder);
            }

        }
        return fallbackAction;
    }

    abstract T create(HystrixCommandBuilder hystrixCommandBuilder);


    private Method getAjcMethod(Object target, Method fallback) {
        if (isCompileWeaving()) {
            return getAjcMethodAroundAdvice(target.getClass(), fallback);
        }
        return null;
    }

    private CommandAction createCacheKeyAction(MetaHolder metaHolder) {
        CommandAction cacheKeyAction = null;
        if (metaHolder.getCacheKeyMethod() != null) {
            cacheKeyAction = new MethodExecutionAction(metaHolder.getObj(), metaHolder.getCacheKeyMethod(), metaHolder.getArgs(), metaHolder);
        }
        return cacheKeyAction;
    }

    private Map<String, Object> getCommandProperties(HystrixCommand hystrixCommand) {
        if (hystrixCommand.commandProperties() == null || hystrixCommand.commandProperties().length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> commandProperties = Maps.newHashMap();
        for (HystrixProperty commandProperty : hystrixCommand.commandProperties()) {
            commandProperties.put(commandProperty.name(), commandProperty.value());
        }
        return commandProperties;
    }

}
