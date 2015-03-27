/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.examples.demo;

import java.math.BigDecimal;
import java.net.HttpCookie;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;

/**
 * This class was originally taken from a functional example using the Authorize.net API
 * but was modified for this example to use mock classes so that the real API does not need
 * to be depended upon and so that a backend account with Authorize.net is not needed.
 */
// import net.authorize.Environment;
// import net.authorize.TransactionType;
// import net.authorize.aim.Result;
// import net.authorize.aim.Transaction;

/**
 * HystrixCommand for submitting credit card payments.
 * <p>
 * No fallback implemented as a credit card failure must result in an error as no logical fallback exists.
 * <p>
 * This implementation originated from a functional HystrixCommand wrapper around an Authorize.net API.
 * <p>
 * The original used the Authorize.net 'duplicate window' setting to ensure an Order could be submitted multiple times
 * and it would behave idempotently so that it would not result in duplicate transactions and each would return a successful
 * response as if it was the first-and-only execution.
 * <p>
 * This idempotence (within the duplicate window time frame set to multiple hours) allows for clients that
 * experience timeouts and failures to confidently retry the credit card transaction without fear of duplicate
 * credit card charges.
 * <p>
 * This in turn allows the HystrixCommand to be configured for reasonable timeouts and isolation rather than
 * letting it go 10+ seconds hoping for success when latency occurs.
 * <p>
 * In this example, the timeout is set to 3,000ms as normal behavior typically saw a credit card transaction taking around 1300ms
 * and in this case it's better to wait longer and try to succeed as the result is a user error.
 * <p>
 * We do not want to wait the 10,000-20,000ms that Authorize.net can default to as that would allow severe resource
 * saturation under high volume traffic when latency spikes.
 */
public class CreditCardCommand extends HystrixCommand<CreditCardAuthorizationResult> {
    private final static AuthorizeNetGateway DEFAULT_GATEWAY = new AuthorizeNetGateway();

    private final AuthorizeNetGateway gateway;
    private final Order order;
    private final PaymentInformation payment;
    private final BigDecimal amount;

    /**
     * A HystrixCommand implementation accepts arguments into the constructor which are then accessible
     * to the <code>run()</code> method when it executes.
     * 
     * @param order
     * @param payment
     * @param amount
     */
    public CreditCardCommand(Order order, PaymentInformation payment, BigDecimal amount) {
        this(DEFAULT_GATEWAY, order, payment, amount);
    }

    private CreditCardCommand(AuthorizeNetGateway gateway, Order order, PaymentInformation payment, BigDecimal amount) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("CreditCard"))
                // defaulting to a fairly long timeout value because failing a credit card transaction is a bad user experience and 'costly' to re-attempt
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(3000)));
        this.gateway = gateway;
        this.order = order;
        this.payment = payment;
        this.amount = amount;
    }

    /**
     * Actual work of submitting the credit card authorization occurs within this <code>HystrixCommand.run()</code> method.
     */
    @Override
    protected CreditCardAuthorizationResult run() {
        // Simulate transitive dependency from CreditCardCommand to GetUserAccountCommand.
        // UserAccount could be injected into this command as an argument (and that would be more accurate)
        // but often in large codebase that ends up not happening and each library fetches common data
        // such as user information directly such as this example.
        UserAccount user = new GetUserAccountCommand(new HttpCookie("mockKey", "mockValueFromHttpRequest")).execute();
        if (user.getAccountType() == 1) {
            // do something
        } else {
            // do something else
        }

        // perform credit card transaction
        Result<Transaction> result = gateway.submit(payment.getCreditCardNumber(),
                String.valueOf(payment.getExpirationMonth()),
                String.valueOf(payment.getExpirationYear()),
                TransactionType.AUTH_CAPTURE, amount, order);

        if (result.isApproved()) {
            return CreditCardAuthorizationResult.createSuccessResponse(result.getTarget().getTransactionId(), result.getTarget().getAuthorizationCode());
        } else if (result.isDeclined()) {
            return CreditCardAuthorizationResult.createFailedResponse(result.getReasonResponseCode() + " : " + result.getResponseText());
        } else {
            // check for duplicate transaction
            if (result.getReasonResponseCode().getResponseReasonCode() == 11) {
                if (result.getTarget().getAuthorizationCode() != null) {
                    // We will treat this as a success as this is telling us we have a successful authorization code
                    // just that we attempted to re-post it again during the 'duplicateWindow' time period.
                    // This is part of the idempotent behavior we require so that we can safely timeout and/or fail and allow
                    // client applications to re-attempt submitting a credit card transaction for the same order again.
                    // In those cases if the client saw a failure but the transaction actually succeeded, this will capture the
                    // duplicate response and behave to the client as a success.
                    return CreditCardAuthorizationResult.createDuplicateSuccessResponse(result.getTarget().getTransactionId(), result.getTarget().getAuthorizationCode());
                }
            }
            // handle all other errors
            return CreditCardAuthorizationResult.createFailedResponse(result.getReasonResponseCode() + " : " + result.getResponseText());
            /**
             * NOTE that in this use case we do not throw an exception for an "error" as this type of error from the service is not a system error,
             * but a legitimate usage problem successfully delivered back from the service.
             * 
             * Unexpected errors will be allowed to throw RuntimeExceptions.
             * 
             * The HystrixBadRequestException could potentially be used here, but with such a complex set of errors and reason codes
             * it was chosen to stick with the response object approach rather than using an exception.
             */
        }
    }

    /*
     * The following inner classes are all mocks based on the Authorize.net API that this class originally used.
     * 
     * They are statically mocked in this example to demonstrate how Hystrix might behave when wrapping this type of call.
     */

    public static class AuthorizeNetGateway {
        public AuthorizeNetGateway() {

        }

        public Result<Transaction> submit(String creditCardNumber, String expirationMonth, String expirationYear, TransactionType authCapture, BigDecimal amount, Order order) {
            /* simulate varying length of time 800-1500ms which is typical for a credit card transaction */
            try {
                Thread.sleep((int) (Math.random() * 700) + 800);
            } catch (InterruptedException e) {
                // do nothing
            }

            /* and every once in a while we'll cause it to go longer than 3000ms which will cause the command to timeout */
            if (Math.random() > 0.99) {
                try {
                    Thread.sleep(8000);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            if (Math.random() < 0.8) {
                return new Result<Transaction>(true);
            } else {
                return new Result<Transaction>(false);
            }

        }
    }

    public static class Result<T> {

        private final boolean approved;

        public Result(boolean approved) {
            this.approved = approved;
        }

        public boolean isApproved() {
            return approved;
        }

        public ResponseCode getResponseText() {
            return null;
        }

        public Target getTarget() {
            return new Target();
        }

        public ResponseCode getReasonResponseCode() {
            return new ResponseCode();
        }

        public boolean isDeclined() {
            return !approved;
        }

    }

    public static class ResponseCode {

        public int getResponseReasonCode() {
            return 0;
        }

    }

    public static class Target {

        public String getTransactionId() {
            return "transactionId";
        }

        public String getAuthorizationCode() {
            return "authorizedCode";
        }

    }

    public static class Transaction {

    }

    public static enum TransactionType {
        AUTH_CAPTURE
    }

}
