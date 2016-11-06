package com.netflix.hystrix.util;

import java.util.LinkedList;
import java.util.List;

public class Exceptions {
    private Exceptions() {
    }

    /**
     * Throws the argument, return-type is RuntimeException so the caller can use a throw statement break out of the method
     */
    public static RuntimeException sneakyThrow(Throwable t) {
        return Exceptions.<RuntimeException>doThrow(t);
    }

    private static <T extends Throwable> T doThrow(Throwable ex) throws T {
        throw (T) ex;
    }
}
