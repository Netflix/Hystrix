package com.netflix.hystrix;

/**
 * Basic class for hystrix keys
 */
public interface HystrixKey {
    /**
     * The word 'name' is used instead of 'key' so that Enums can implement this interface and it work natively.
     *
     * @return String
     */
    String name();

    /**
     * Default implementation of the interface
     */
    abstract class HystrixKeyDefault implements HystrixKey {
        private final String name;

        public HystrixKeyDefault(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
