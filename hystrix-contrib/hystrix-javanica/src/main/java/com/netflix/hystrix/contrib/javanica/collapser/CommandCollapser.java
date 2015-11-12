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
package com.netflix.hystrix.contrib.javanica.collapser;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.BatchHystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import java.util.Collection;
import java.util.List;

import static org.slf4j.helpers.MessageFormatter.arrayFormat;

/**
 * Collapses multiple requests into a single {@link HystrixCommand} execution based
 * on a time window and optionally a max batch size.
 */
public class CommandCollapser extends HystrixCollapser<List<Object>, Object, Object> {

    private MetaHolder metaHolder;

    private static final String ERROR_MSG = "Failed to map all collapsed requests to response. " +
            "The expected contract has not been respected. ";

    private static final String ERROR_MSF_TEMPLATE = "Collapser key: '{}', requests size: '{}', response size: '{}'";

    /**
     * Constructor with parameters.
     *
     * @param metaHolder the {@link MetaHolder}
     */
    public CommandCollapser(MetaHolder metaHolder) {
        super(HystrixCommandBuilderFactory.getInstance().create(metaHolder).getSetterBuilder().buildCollapserCommandSetter());
        this.metaHolder = metaHolder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getRequestArgument() {
        return metaHolder.getArgs();
    }

    /**
     * Creates batch command.
     */
    @Override
    protected HystrixCommand<List<Object>> createCommand(
            Collection<CollapsedRequest<Object, Object>> collapsedRequests) {
       return new BatchHystrixCommand(HystrixCommandBuilderFactory.getInstance().create(metaHolder, collapsedRequests));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void mapResponseToRequests(List<Object> batchResponse,
                                         Collection<CollapsedRequest<Object, Object>> collapsedRequests) {
        if (batchResponse.size() < collapsedRequests.size()) {
            throw new RuntimeException(createMessage(collapsedRequests, batchResponse));
        }
        int count = 0;
        for (CollapsedRequest<Object, Object> request : collapsedRequests) {
            request.setResponse(batchResponse.get(count++));
        }
    }

    private String createMessage(Collection<CollapsedRequest<Object, Object>> requests,
                                 List<Object> response) {
        return ERROR_MSG + arrayFormat(ERROR_MSF_TEMPLATE, new Object[]{getCollapserKey().name(),
                requests.size(), response.size()}).getMessage();
    }

}
