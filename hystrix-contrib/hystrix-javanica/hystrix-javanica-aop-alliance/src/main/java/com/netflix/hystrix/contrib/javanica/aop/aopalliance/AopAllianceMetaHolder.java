/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aopalliance;

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.aopalliance.intercept.MethodInvocation;

/**
 *
 * @author justinjose28
 *
 */
public class AopAllianceMetaHolder extends MetaHolder {
    private MethodInvocation methodInvocation;

    protected AopAllianceMetaHolder(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public MethodInvocation getMethodInvocation() {
        return methodInvocation;
    }

    @Override
    public Builder copy() {
        Builder builder = new Builder(this);
        return builder;
    }

    public static final class Builder extends MetaHolder.Builder<Builder> {

        private Builder(AopAllianceMetaHolder metaHolder) {
            super(metaHolder);
        }

        private Builder() {

        }

        @Override
        public AopAllianceMetaHolder build() {
            return new AopAllianceMetaHolder(this);
        }

        @Override
        protected Builder getThis() {
            return this;
        }
    }
}