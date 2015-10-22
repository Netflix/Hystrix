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
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.command.BatchHystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.initializeCollapserProperties;
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
        super(new CollapserSetterBuilder()
                .collapserKey(metaHolder.getHystrixCollapser().collapserKey(), metaHolder.getDefaultCollapserKey())
                .scope(metaHolder.getHystrixCollapser().scope())
                .properties(metaHolder.getHystrixCollapser().collapserProperties())
                .build());
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
        return BatchHystrixCommandFactory.getInstance().create(metaHolder, collapsedRequests);
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

    /**
     * Builder for {@link Setter}.
     */
    private static final class CollapserSetterBuilder {

        private String collapserKey;

        private Scope scope;

        private HystrixProperty[] properties;

        private CollapserSetterBuilder collapserKey(String pCollapserKey, String def) {
            this.collapserKey = StringUtils.isNotEmpty(pCollapserKey) ? pCollapserKey : def;
            return this;
        }

        private CollapserSetterBuilder scope(Scope pScope) {
            this.scope = pScope;
            return this;
        }

        private CollapserSetterBuilder properties(HystrixProperty[] properties) {
            this.properties = properties;
            return this;
        }

        public Setter build() {
            HystrixCollapserProperties.Setter propSetter = initializeCollapserProperties(properties);
            return Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey(collapserKey)).andScope(scope)
                    .andCollapserPropertiesDefaults(propSetter);
        }
    }

    private String createMessage(Collection<CollapsedRequest<Object, Object>> requests,
                                 List<Object> response) {
        return ERROR_MSG + arrayFormat(ERROR_MSF_TEMPLATE, new Object[]{getCollapserKey().name(),
                requests.size(), response.size()}).getMessage();
    }

}
