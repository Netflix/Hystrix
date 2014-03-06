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


import com.google.common.collect.Lists;
import com.netflix.hystrix.HystrixCollapser;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This command is used in collapser.
 */
@ThreadSafe
public class BatchHystrixCommand extends AbstractHystrixCommand<List<Object>> {

    private List<Object> response = Lists.newCopyOnWriteArrayList();

    /**
     * {@inheritDoc}
     */
    protected BatchHystrixCommand(CommandSetterBuilder setterBuilder, CommandAction commandAction,
                                  CommandAction fallbackAction, Map<String, Object> commandProperties,
                                  Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        super(setterBuilder, commandAction, fallbackAction, commandProperties, collapsedRequests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Object> run() throws Exception {
        for (HystrixCollapser.CollapsedRequest<Object, Object> request : getCollapsedRequests()) {
            Object[] args = (Object[]) request.getArgument();
            response.add(getCommandAction().execute(args));
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Object> getFallback() {
        if (getFallbackAction() != null) {
            response.add(getFallbackAction().execute());
            return response;
        } else {
            return super.getFallback();
        }
    }

}
