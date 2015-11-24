package com.netflix.hystrix.contrib.javanica.util.bridge;

/**
 * Created by dmgcodevil
 */
public class GenericInterfaceImpl implements GenericInterface<Child, Parent> {


    public Child foo(SubChild c) {
        return null;
    }

    @Override
    public Child foo(Child c) {
        return null;
    }

    public Child foo(Parent c) {
        return null;
    }

}
