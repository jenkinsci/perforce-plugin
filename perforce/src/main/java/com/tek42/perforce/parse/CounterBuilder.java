package com.tek42.perforce.parse;

import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Counter;

/**
 * Responsible for building and saving counters.
 * 
 * @author Kamlesh Sangani
 */
public class CounterBuilder implements Builder<Counter> {

	/*
	 * (non-Javadoc)
	 * @see com.tek42.perforce.parse.Builder#getBuildCmd(java.lang.String)
	 */
	public String[] getBuildCmd(String id) {
		return new String[] { "p4", "counter", id };
	}

	/*
	 * (non-Javadoc)
	 * @see com.tek42.perforce.parse.Builder#getSaveCmd()
	 */
	public String[] getSaveCmd(Counter obj) {
		return new String[] { "p4", "counter", obj.getName(), String.valueOf(obj.getValue()) };
	}

	/*
	 * (non-Javadoc)
	 * @see com.tek42.perforce.parse.Builder#build(java.lang.StringBuilder)
	 */
	public Counter build(StringBuilder sb) throws PerforceException {
		final Pattern p = Pattern.compile("^([0-9])*", Pattern.DOTALL | Pattern.MULTILINE);
		final Matcher m = p.matcher(sb.toString());
		final Counter counter = new Counter();
		counter.setName("");
		if(m.find()) {
			counter.setValue(Integer.parseInt(m.group(0).trim()));
		}
		return counter;
	}

	/*
	 * (non-Javadoc)
	 * @see com.tek42.perforce.parse.Builder#save(java.lang.Object, java.io.Writer)
	 */
	public void save(Counter counter, Writer writer) throws PerforceException {}

	/*
	 * (non-Javadoc)
	 * @see com.tek42.perforce.parse.Builder#getSaveCmd(T obj)
	 */
	public boolean requiresStandardInput() {
		return false;
	}
}
