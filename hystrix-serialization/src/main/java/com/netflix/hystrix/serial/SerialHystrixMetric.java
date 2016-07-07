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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SerialHystrixMetric {
    protected final static JsonFactory jsonFactory = new JsonFactory();
    protected final static CBORFactory cborFactory = new CBORFactory();
    protected final static ObjectMapper mapper = new ObjectMapper();
    protected final static Logger logger = LoggerFactory.getLogger(SerialHystrixMetric.class);

    public static String fromByteBufferToString(ByteBuffer bb) {
        byte[] byteArray = new byte[bb.remaining()];
        bb.get(byteArray);

        try {
            CBORParser parser = cborFactory.createParser(byteArray);
            JsonNode rootNode = mapper.readTree(parser);

            return rootNode.toString();
        } catch (IOException ioe) {
            logger.error("IO Exception during deserialization of ByteBuffer of Hystrix Metric : " + ioe);
            return "";
        }
    }
}
