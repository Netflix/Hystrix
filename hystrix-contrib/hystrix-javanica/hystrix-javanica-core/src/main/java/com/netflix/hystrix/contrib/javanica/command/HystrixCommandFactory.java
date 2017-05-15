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

import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.collapser.CommandCollapser;

/**
 * Created by dmgcodevil.
 */
public class HystrixCommandFactory {

    private static final HystrixCommandFactory INSTANCE = new HystrixCommandFactory();

    private HystrixCommandFactory() {

    }

    public static HystrixCommandFactory getInstance() {
        return INSTANCE;
    }

    public <T extends MetaHolder, V extends MetaHolder.Builder<V>> HystrixInvokable create(AbstractHystrixCommandBuilderFactory<T, V> commandBuilderFactory, T metaHolder) {
        HystrixInvokable executable;
        if (metaHolder.isCollapserAnnotationPresent()) {
            executable = new CommandCollapser<T, V>(commandBuilderFactory, metaHolder);
        } else if (metaHolder.isObservable()) {
            executable = new GenericObservableCommand(commandBuilderFactory.create(metaHolder));
        } else {
            executable = new GenericCommand(commandBuilderFactory.create(metaHolder));
        }
        return executable;
    }

    public <T extends MetaHolder, V extends MetaHolder.Builder<V>> HystrixInvokable createDelayed(AbstractHystrixCommandBuilderFactory<T, V> commandBuilderFactory, T metaHolder) {
        HystrixInvokable executable;
        if (metaHolder.isObservable()) {
            executable = new GenericObservableCommand(commandBuilderFactory.create(metaHolder));
        } else {
            executable = new GenericCommand(commandBuilderFactory.create(metaHolder));
        }
        return executable;
    }
}