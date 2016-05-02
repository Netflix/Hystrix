package com.netflix.hystrix.dashboard.eureka.pojos;

import java.util.ArrayList;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(strict = false)
public class Applications {

	@Element
	private String versions__delta;
	
	@Element
	private String apps__hashcode;
	
	@ElementList(inline=true)
	private ArrayList<Application> application;

	public Applications() {
	}

	public String getVersions__delta() {
		return versions__delta;
	}

	public void setVersions__delta(String versions__delta) {
		this.versions__delta = versions__delta;
	}

	public String getApps__hashcode() {
		return apps__hashcode;
	}

	public void setApps__hashcode(String apps__hashcode) {
		this.apps__hashcode = apps__hashcode;
	}

	public ArrayList<Application> getApplication() {
		return application;
	}

	public void setApplication(ArrayList<Application> application) {
		this.application = application;
	}

	@Override
	public String toString() {
		return "Applications [versions__delta=" + versions__delta + ", apps__hashcode=" + apps__hashcode
				+ ", application=" + application + "]";
	}

}
