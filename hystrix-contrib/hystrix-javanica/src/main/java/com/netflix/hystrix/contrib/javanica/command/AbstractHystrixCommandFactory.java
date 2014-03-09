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


import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

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
        CommandAction action;
        if (metaHolder.isAsync() || metaHolder.isObservable()) {
            action = new CommandAction(metaHolder.getClosure().getClosureObj(),
                    metaHolder.getClosure().getClosureMethod());
        } else {
            action = new CommandAction(metaHolder.getObj(), metaHolder.getMethod(), metaHolder.getArgs());
        }
        Map<String, Object> commandProperties = getCommandProperties(metaHolder.getHystrixCommand());
        CommandAction fallbackAction = createFallbackAction(metaHolder, collapsedRequests);
        return create(setterBuilder, commandProperties, action, fallbackAction, collapsedRequests);
    }

    CommandAction createFallbackAction(MetaHolder metaHolder,
                                       Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        String fallbackMethodName = metaHolder.getHystrixCommand().fallbackMethod();
        CommandAction fallbackAction = null;
        if (StringUtils.isNotEmpty(fallbackMethodName)) {
            try {
                Method fallbackMethod = metaHolder.getObj().getClass()
                        .getDeclaredMethod(fallbackMethodName, metaHolder.getParameterTypes());
                if (fallbackMethod.isAnnotationPresent(HystrixCommand.class)) {
                    fallbackMethod.setAccessible(true);
                    MetaHolder fmMetaHolder = MetaHolder.builder()
                            .obj(metaHolder.getObj())
                            .method(fallbackMethod)
                            .args(metaHolder.getArgs())
                            .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                            .defaultCommandKey(fallbackMethod.getName())
                            .defaultGroupKey(metaHolder.getDefaultGroupKey())
                            .hystrixCollapser(metaHolder.getHystrixCollapser())
                            .hystrixCommand(fallbackMethod.getAnnotation(HystrixCommand.class)).build();
                    fallbackAction = new CommandExecutionAction(create(fmMetaHolder, collapsedRequests));
                } else {
                    fallbackAction = new CommandAction(metaHolder.getObj(), fallbackMethod, metaHolder.getArgs());
                }
            } catch (NoSuchMethodException e) {
                Throwables.propagate(e);
            }
        }
        return fallbackAction;
    }

    abstract T create(CommandSetterBuilder setterBuilder, Map<String, Object> commandProperties, CommandAction action,
                      CommandAction fallbackAction,
                      Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests);

    private Map<String, Object> getCommandProperties(HystrixCommand hystrixCommand) {
        Map<String, Object> commandProperties = Maps.newHashMap();
        for (HystrixProperty commandProperty : hystrixCommand.commandProperties()) {
            commandProperties.put(commandProperty.name(), commandProperty.value());
        }
        return commandProperties;
    }

}
