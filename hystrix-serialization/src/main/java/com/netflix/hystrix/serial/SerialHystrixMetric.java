/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.serial;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class SerialHystrixMetric {
    protected final static JsonFactory jsonFactory = new JsonFactory();
    protected final static ObjectMapper mapper = new ObjectMapper();
    protected final static Logger logger = LoggerFactory.getLogger(SerialHystrixMetric.class);

    @Deprecated
    public static String fromByteBufferToString(ByteBuffer bb) {
        throw new UnsupportedOperationException("Not implemented anymore.  Will be implemented in a new class shortly");
    }
}
