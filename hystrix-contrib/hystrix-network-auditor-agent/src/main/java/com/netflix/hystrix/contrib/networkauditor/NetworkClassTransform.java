/**
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.hystrix.contrib.networkauditor;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Bytecode ClassFileTransformer used by the Java Agent to instrument network code in the java.* libraries and use Hystrix state to determine if calls are Hystrix-isolated or not.
 */
public class NetworkClassTransform implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String name = className.replace('/', '.');
        try {
            /*
             * These hook points were found through trial-and-error after wrapping all java.net/java.io/java.nio classes and methods and finding reliable
             * notifications that matched statistics and events in an application.
             * 
             * There may very well be problems here or code paths that don't correctly trigger an event.
             * 
             * If someone can provide a more reliable and cleaner hook point or if there are examples of code paths that don't trigger either of these
             * then please file a bug or submit a pull request at https://github.com/Netflix/Hystrix
             */
            if (name.equals("java.net.Socket$2")) {
                // this one seems to be fairly reliable in counting each time a request/response occurs on blocking IO
                return wrapConstructorsOfClass(name);
            } else if (name.equals("java.nio.channels.SocketChannel")) {
                // handle NIO 
                return wrapConstructorsOfClass(name);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed trying to wrap class: " + className, e);
        }

        // we didn't transform anything so return null to leave untouched
        return null;
    }

    /**
     * Wrap all constructors of a given class
     * 
     * @param className
     * @throws NotFoundException
     * @throws CannotCompileException
     * @throws IOException
     */
    private byte[] wrapConstructorsOfClass(String className) throws NotFoundException, IOException, CannotCompileException {
        return wrapClass(className, true);
    }

    /**
     * Wrap all signatures of a given method name.
     * 
     * @param className
     * @param methodName
     * @throws NotFoundException
     * @throws CannotCompileException
     * @throws IOException
     */
    private byte[] wrapClass(String className, boolean wrapConstructors, String... methodNames) throws NotFoundException, IOException, CannotCompileException {
        ClassPool cp = ClassPool.getDefault();
        CtClass ctClazz = cp.get(className);
        // constructors
        if (wrapConstructors) {
            CtConstructor[] constructors = ctClazz.getConstructors();
            for (CtConstructor constructor : constructors) {
                try {
                    constructor.insertBefore("{ com.netflix.hystrix.contrib.networkauditor.HystrixNetworkAuditorAgent.notifyOfNetworkEvent(); }");
                } catch (Exception e) {
                    throw new RuntimeException("Failed trying to wrap constructor of class: " + className, e);
                }

            }
        }
        // methods
        CtMethod[] methods = ctClazz.getDeclaredMethods();
        for (CtMethod method : methods) {
            try {
                for (String methodName : methodNames) {
                    if (method.getName().equals(methodName)) {
                        method.insertBefore("{ com.netflix.hystrix.contrib.networkauditor.HystrixNetworkAuditorAgent.handleNetworkEvent(); }");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed trying to wrap method [" + method.getName() + "] of class: " + className, e);
            }
        }
        return ctClazz.toBytecode();
    }

}