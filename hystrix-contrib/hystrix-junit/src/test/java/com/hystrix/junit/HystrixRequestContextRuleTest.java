package com.hystrix.junit;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;

public final class HystrixRequestContextRuleTest {
    @Rule
    public HystrixRequestContextRule request = new HystrixRequestContextRule();

    @Test
    public void initsContext() {
        MatcherAssert.assertThat(this.request.context(), CoreMatchers.notNullValue());
    }

    @Test
    public void manuallyShutdownContextDontBreak() {
        this.request.after();
        this.request.after();
        MatcherAssert.assertThat(this.request.context(), CoreMatchers.nullValue());
    }
}
