package com.netflix.hystrix.dashboard.eureka.pojos;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(strict = false)
public class Application {

	@Element(required=false)
	private String name;

	public Application() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Application [name=" + name + "]";
	}

}
