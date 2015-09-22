package com.netflix.hystrix.examples.basic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.examples.basic.ObservableCommandNumbersToWords.NumberWord;

/**
 * A simple Hystrix Observable command that translates a number (<code>Integer</code>) into an English text.
 */
class ObservableCommandNumbersToWords extends HystrixObservableCommand<NumberWord>
{
	private final List<Integer> numbers;

	// in the real world you'd probably want to replace this very simple code by using ICU or similar
	static Map<Integer, String> dict = new HashMap<Integer, String>(11);
	static
	{
		dict.put(0, "zero");
		dict.put(1, "one");
		dict.put(2, "two");
		dict.put(3, "three");
		dict.put(4, "four");
		dict.put(5, "five");
		dict.put(6, "six");
		dict.put(7, "seven");
		dict.put(8, "eight");
		dict.put(9, "nine");
		dict.put(10, "ten");
	}

	public ObservableCommandNumbersToWords(final List<Integer> numbers)
	{
		super(HystrixCommandGroupKey.Factory.asKey(ObservableCommandNumbersToWords.class.getName()));
		this.numbers = numbers;
	}

	@Override
	protected Observable<NumberWord> construct()
	{
		return Observable.from(numbers).map(new Func1<Integer, NumberWord>()
		{
			@Override
			public NumberWord call(final Integer number)
			{
				return new NumberWord(number, dict.get(number));
			}

		});
	}

	static class NumberWord
	{
		private final Integer number;
		private final String word;

		public NumberWord(final Integer number, final String word)
		{
			super();
			this.number = number;
			this.word = word;
		}

		public Integer getNumber()
		{
			return number;
		}

		public String getWord()
		{
			return word;
		}
	}

}