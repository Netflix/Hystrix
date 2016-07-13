/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.examples.basic;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixObservableCollapser;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.examples.basic.ObservableCommandNumbersToWords.NumberWord;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * Example that uses {@link HystrixObservableCollapser} to batch multiple {@link ObservableCommandNumbersToWords} requests.
 *
 * @author Patrick Ruhkopf
 */
public class ObservableCollapserGetWordForNumber extends HystrixObservableCollapser<Integer, NumberWord, String, Integer>
{
	private final Integer number;

	private final static AtomicInteger counter = new AtomicInteger();

	public static void resetCmdCounter()
	{
		counter.set(0);
	}

	public static int getCmdCount()
	{
		return counter.get();
	}

	public ObservableCollapserGetWordForNumber(final Integer number)
	{
		this.number = number;
	}

	@Override
	public Integer getRequestArgument()
	{
		return number;
	}

	@SuppressWarnings("boxing")
	@Override
	protected HystrixObservableCommand<NumberWord> createCommand(final Collection<CollapsedRequest<String, Integer>> requests)
	{
		final int count = counter.incrementAndGet();
		System.out.println("Creating batch for " + requests.size() + " requests. Total invocations so far: " + count);

		final List<Integer> numbers = new ArrayList<Integer>();
		for (final CollapsedRequest<String, Integer> request : requests)
		{
			numbers.add(request.getArgument());
		}

		return new ObservableCommandNumbersToWords(numbers);
	}

	@Override
	protected Func1<NumberWord, Integer> getBatchReturnTypeKeySelector()
	{
		// Java 8: (final NumberWord nw) -> nw.getNumber();

		return new Func1<NumberWord, Integer>()
		{
			@Override
			public Integer call(final NumberWord nw)
			{
				return nw.getNumber();
			}
		};
	}

	@Override
	protected Func1<Integer, Integer> getRequestArgumentKeySelector()
	{
		// Java 8: return (final Integer no) -> no;

		return new Func1<Integer, Integer>()
		{
			@Override
			public Integer call(final Integer no)
			{
				return no;
			}

		};
	}

	@Override
	protected Func1<NumberWord, String> getBatchReturnTypeToResponseTypeMapper()
	{
		// Java 8: return (final NumberWord nw) -> nw.getWord();

		return new Func1<NumberWord, String>()
		{
			@Override
			public String call(final NumberWord nw)
			{
				return nw.getWord();
			}
		};
	}

	@Override
	protected void onMissingResponse(final CollapsedRequest<String, Integer> request)
	{
		request.setException(new Exception("No word"));
	}

	public static class ObservableCollapserGetWordForNumberTest
	{
		private HystrixRequestContext ctx;

		@Before
		public void before()
		{
			ctx = HystrixRequestContext.initializeContext();
			ObservableCollapserGetWordForNumber.resetCmdCounter();
		}

		@After
		public void after()
		{
			System.out.println(HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
			ctx.shutdown();
		}

		/**
		 * Example where we subscribe without using a specific scheduler. That means we run the actions on the same thread.
		 */
		@Test
		public void shouldCollapseRequestsSync()
		{
			final int noOfRequests = 10;
			final Map<Integer, TestSubscriber<String>> subscribersByNumber = new HashMap<Integer, TestSubscriber<String>>(
					noOfRequests);

			TestSubscriber<String> subscriber;
			for (int number = 0; number < noOfRequests; number++)
			{
				subscriber = new TestSubscriber<String>();
				new ObservableCollapserGetWordForNumber(number).toObservable().subscribe(subscriber);
				subscribersByNumber.put(number, subscriber);

				// wait a little bit after running half of the requests so that we don't collapse all of them into one batch
				// TODO this can probably be improved by using a test scheduler
				if (number == noOfRequests / 2)
					sleep(1000);

			}

			assertThat(subscribersByNumber.size(), is(noOfRequests));
			for (final Entry<Integer, TestSubscriber<String>> subscriberByNumber : subscribersByNumber.entrySet())
			{
				subscriber = subscriberByNumber.getValue();
				subscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);

				assertThat(subscriber.getOnErrorEvents().toString(), subscriber.getOnErrorEvents().size(), is(0));
				assertThat(subscriber.getOnNextEvents().size(), is(1));

				final String word = subscriber.getOnNextEvents().get(0);
				System.out.println("Translated " + subscriberByNumber.getKey() + " to " + word);
				assertThat(word, equalTo(numberToWord(subscriberByNumber.getKey())));
			}

			assertTrue(ObservableCollapserGetWordForNumber.getCmdCount() > 1);
			assertTrue(ObservableCollapserGetWordForNumber.getCmdCount() < noOfRequests);
		}

		/**
		 * Example where we subscribe on the computation scheduler. For this we need the {@link HystrixContextScheduler}, that
		 * passes the {@link HystrixRequestContext} to the thread that runs the action.
		 */
		@Test
		public void shouldCollapseRequestsAsync()
		{
			final HystrixContextScheduler contextAwareScheduler = new HystrixContextScheduler(Schedulers.computation());

			final int noOfRequests = 10;
			final Map<Integer, TestSubscriber<String>> subscribersByNumber = new HashMap<Integer, TestSubscriber<String>>(
					noOfRequests);

			TestSubscriber<String> subscriber;
			for (int number = 0; number < noOfRequests; number++)
			{
				subscriber = new TestSubscriber<String>();
				final int finalNumber = number;

				// defer and subscribe on specific scheduler
				Observable.defer(new Func0<Observable<String>>()
				{
					@Override
					public Observable<String> call()
					{
						return new ObservableCollapserGetWordForNumber(finalNumber).toObservable();
					}
				}).subscribeOn(contextAwareScheduler).subscribe(subscriber);

				subscribersByNumber.put(number, subscriber);

				// wait a little bit after running half of the requests so that we don't collapse all of them into one batch
				// TODO this can probably be improved by using a test scheduler
				if (number == noOfRequests / 2)
					sleep(1000);
			}

			assertThat(subscribersByNumber.size(), is(noOfRequests));
			for (final Entry<Integer, TestSubscriber<String>> subscriberByNumber : subscribersByNumber.entrySet())
			{
				subscriber = subscriberByNumber.getValue();
				subscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);

				assertThat(subscriber.getOnErrorEvents().toString(), subscriber.getOnErrorEvents().size(), is(0));
				assertThat(subscriber.getOnNextEvents().size(), is(1));

				final String word = subscriber.getOnNextEvents().get(0);
				System.out.println("Translated " + subscriberByNumber.getKey() + " to " + word);
				assertThat(word, equalTo(numberToWord(subscriberByNumber.getKey())));
			}

			assertTrue(ObservableCollapserGetWordForNumber.getCmdCount() > 1);
			assertTrue(ObservableCollapserGetWordForNumber.getCmdCount() < noOfRequests);
		}

		@Test
		public void shouldCollapseSameRequests()
		{
			//given
			final HystrixContextScheduler contextAwareScheduler = new HystrixContextScheduler(Schedulers.computation());

			//when
			TestSubscriber<String> subscriber1 = getWordForNumber(contextAwareScheduler, 0);
			TestSubscriber<String> subscriber2 = getWordForNumber(contextAwareScheduler, 0);

			//then
			subscriberReceived(subscriber1, 0);
			subscriberReceived(subscriber2, 0);
		}

		private TestSubscriber<String> getWordForNumber(HystrixContextScheduler contextAwareScheduler, final int number) {
			final TestSubscriber<String> subscriber = new TestSubscriber<String>();
			Observable.defer(new Func0<Observable<String>>()
			{
				@Override
				public Observable<String> call()
				{
					return new ObservableCollapserGetWordForNumber(number).toObservable();
				}
			}).subscribeOn(contextAwareScheduler).subscribe(subscriber);
			return subscriber;
		}

		private void subscriberReceived(TestSubscriber<String> subscriber, int number) {
			subscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);
			assertThat(subscriber.getOnErrorEvents().toString(), subscriber.getOnErrorEvents().size(), is(0));
			assertThat(subscriber.getOnNextEvents().size(), is(1));
			assertThat(subscriber.getOnNextEvents().get(0), equalTo(numberToWord(number)));
		}

		private String numberToWord(final int number)
		{
			return ObservableCommandNumbersToWords.dict.get(number);
		}

		private void sleep(final long ms)
		{
			try
			{
				Thread.sleep(1000);
			}
			catch (final InterruptedException e)
			{
				throw new IllegalStateException(e);
			}
		}

	}
}
