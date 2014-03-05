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

import org.apache.commons.lang3.Validate;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Specific implementation of {@link CommandAction} which calls proceed() on specified joint point.
 * This action can process only proceeding joint points {@link ProceedingJoinPoint}.
 */
public class ProceedingJoinPointAction extends CommandAction {

    private ProceedingJoinPoint jointPoint;

    public ProceedingJoinPointAction(ProceedingJoinPoint jointPoint) {
        Validate.notNull(jointPoint, "jointPoint is required parameter and cannot be null");
        this.jointPoint = jointPoint;
    }

    public static ProceedingJoinPointAction create(JoinPoint jp) {
        if (jp instanceof ProceedingJoinPoint) {
            return new ProceedingJoinPointAction((ProceedingJoinPoint) jp);
        } else {
            throw new IllegalArgumentException("specified JoinPoint isn't instance of ProceedingJoinPoint");
        }
    }

    @Override
    public Object execute() throws Throwable {
        return jointPoint.proceed();
    }
}
