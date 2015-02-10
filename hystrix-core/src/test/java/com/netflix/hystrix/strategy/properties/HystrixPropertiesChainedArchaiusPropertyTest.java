/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.strategy.properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.IntegerProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.StringProperty;

public class HystrixPropertiesChainedArchaiusPropertyTest {
    @After
    public void cleanUp() {
        // Tests which use ConfigurationManager.getConfigInstance() will leave the singleton in an initialize state,
        // this will leave the singleton in a reasonable state between tests.
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testString() throws Exception {

        DynamicStringProperty pString = new DynamicStringProperty("defaultString", "default-default");
        HystrixPropertiesChainedArchaiusProperty.StringProperty fString = new HystrixPropertiesChainedArchaiusProperty.StringProperty("overrideString", pString);

        assertTrue("default-default".equals(fString.get()));

        ConfigurationManager.getConfigInstance().setProperty("defaultString", "default");
        assertTrue("default".equals(fString.get()));

        ConfigurationManager.getConfigInstance().setProperty("overrideString", "override");
        assertTrue("override".equals(fString.get()));

        ConfigurationManager.getConfigInstance().clearProperty("overrideString");
        assertTrue("default".equals(fString.get()));

        ConfigurationManager.getConfigInstance().clearProperty("defaultString");
        assertTrue("default-default".equals(fString.get()));
    }

    @Test
    public void testInteger() throws Exception {

        DynamicIntegerProperty pInt = new DynamicIntegerProperty("defaultInt", -1);
        HystrixPropertiesChainedArchaiusProperty.IntegerProperty fInt = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("overrideInt", pInt);

        assertTrue(-1 == fInt.get());

        ConfigurationManager.getConfigInstance().setProperty("defaultInt", 10);
        assertTrue(10 == fInt.get());

        ConfigurationManager.getConfigInstance().setProperty("overrideInt", 11);
        assertTrue(11 == fInt.get());

        ConfigurationManager.getConfigInstance().clearProperty("overrideInt");
        assertTrue(10 == fInt.get());

        ConfigurationManager.getConfigInstance().clearProperty("defaultInt");
        assertTrue(-1 == fInt.get());
    }

    @Test
    public void testBoolean() throws Exception {

        DynamicBooleanProperty pBoolean = new DynamicBooleanProperty("defaultBoolean", true);
        HystrixPropertiesChainedArchaiusProperty.BooleanProperty fBoolean = new HystrixPropertiesChainedArchaiusProperty.BooleanProperty("overrideBoolean", pBoolean);

        System.out.println("pBoolean: " + pBoolean.get());
        System.out.println("fBoolean: " + fBoolean.get());

        assertTrue(fBoolean.get());

        ConfigurationManager.getConfigInstance().setProperty("defaultBoolean", Boolean.FALSE);

        System.out.println("pBoolean: " + pBoolean.get());
        System.out.println("fBoolean: " + fBoolean.get());

        assertFalse(fBoolean.get());

        ConfigurationManager.getConfigInstance().setProperty("overrideBoolean", Boolean.TRUE);
        assertTrue(fBoolean.get());

        ConfigurationManager.getConfigInstance().clearProperty("overrideBoolean");
        assertFalse(fBoolean.get());

        ConfigurationManager.getConfigInstance().clearProperty("defaultBoolean");
        assertTrue(fBoolean.get());
    }

    @Test
    public void testChainingString() throws Exception {

        DynamicStringProperty node1 = new DynamicStringProperty("node1", "v1");
        StringProperty node2 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("node2", node1);

        HystrixPropertiesChainedArchaiusProperty.StringProperty node3 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("node3", node2);

        assertTrue("" + node3.get(), "v1".equals(node3.get()));

        ConfigurationManager.getConfigInstance().setProperty("node1", "v11");
        assertTrue("v11".equals(node3.get()));

        ConfigurationManager.getConfigInstance().setProperty("node2", "v22");
        assertTrue("v22".equals(node3.get()));

        ConfigurationManager.getConfigInstance().clearProperty("node1");
        assertTrue("v22".equals(node3.get()));

        ConfigurationManager.getConfigInstance().setProperty("node3", "v33");
        assertTrue("v33".equals(node3.get()));

        ConfigurationManager.getConfigInstance().clearProperty("node2");
        assertTrue("v33".equals(node3.get()));

        ConfigurationManager.getConfigInstance().setProperty("node2", "v222");
        assertTrue("v33".equals(node3.get()));

        ConfigurationManager.getConfigInstance().clearProperty("node3");
        assertTrue("v222".equals(node3.get()));

        ConfigurationManager.getConfigInstance().clearProperty("node2");
        assertTrue("v1".equals(node3.get()));

        ConfigurationManager.getConfigInstance().setProperty("node2", "v2222");
        assertTrue("v2222".equals(node3.get()));
    }

    @Test
    public void testChainingInteger() throws Exception {

        DynamicIntegerProperty node1 = new DynamicIntegerProperty("node1", 1);
        IntegerProperty node2 = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("node2", node1);

        HystrixPropertiesChainedArchaiusProperty.IntegerProperty node3 = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("node3", node2);

        assertTrue("" + node3.get(), 1 == node3.get());

        ConfigurationManager.getConfigInstance().setProperty("node1", 11);
        assertTrue(11 == node3.get());

        ConfigurationManager.getConfigInstance().setProperty("node2", 22);
        assertTrue(22 == node3.get());

        ConfigurationManager.getConfigInstance().clearProperty("node1");
        assertTrue(22 == node3.get());

        ConfigurationManager.getConfigInstance().setProperty("node3", 33);
        assertTrue(33 == node3.get());

        ConfigurationManager.getConfigInstance().clearProperty("node2");
        assertTrue(33 == node3.get());

        ConfigurationManager.getConfigInstance().setProperty("node2", 222);
        assertTrue(33 == node3.get());

        ConfigurationManager.getConfigInstance().clearProperty("node3");
        assertTrue(222 == node3.get());

        ConfigurationManager.getConfigInstance().clearProperty("node2");
        assertTrue(1 == node3.get());

        ConfigurationManager.getConfigInstance().setProperty("node2", 2222);
        assertTrue(2222 == node3.get());
    }

    @Test
    public void testAddCallback() throws Exception {

        final DynamicStringProperty node1 = new DynamicStringProperty("n1", "n1");
        final HystrixPropertiesChainedArchaiusProperty.StringProperty node2 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("n2", node1);

        final AtomicInteger callbackCount = new AtomicInteger(0);

        node2.addCallback(new Runnable() {
            @Override
            public void run() {
                callbackCount.incrementAndGet();
            }
        });

        assertTrue(0 == callbackCount.get());

        assertTrue("n1".equals(node2.get()));
        assertTrue(0 == callbackCount.get());

        ConfigurationManager.getConfigInstance().setProperty("n1", "n11");
        assertTrue("n11".equals(node2.get()));
        assertTrue(0 == callbackCount.get());

        ConfigurationManager.getConfigInstance().setProperty("n2", "n22");
        assertTrue("n22".equals(node2.get()));
        assertTrue(1 == callbackCount.get());

        ConfigurationManager.getConfigInstance().clearProperty("n1");
        assertTrue("n22".equals(node2.get()));
        assertTrue(1 == callbackCount.get());

        ConfigurationManager.getConfigInstance().setProperty("n2", "n222");
        assertTrue("n222".equals(node2.get()));
        assertTrue(2 == callbackCount.get());

        ConfigurationManager.getConfigInstance().clearProperty("n2");
        assertTrue("n1".equals(node2.get()));
        assertTrue(3 == callbackCount.get());
    }

}
