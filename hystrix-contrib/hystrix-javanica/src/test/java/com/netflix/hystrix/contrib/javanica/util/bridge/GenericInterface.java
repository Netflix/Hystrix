package com.netflix.hystrix.contrib.javanica.util.bridge;

/**
 * Created by dmgcodevil
 */
public interface GenericInterface<P1, R extends Parent> {


    R foo(P1 p1);
}
