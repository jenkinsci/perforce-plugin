package com.tek42.perforce.parse;

import java.util.ArrayList;
import java.util.List;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Counter;

/**
 * Base API object for interacting with counters.
 * 
 * @author Kamlesh Sangani
 */
public class Counters extends AbstractPerforceTemplate {

	public Counters(Depot depot) {
		super(depot);
	}

	/**
	 * Saves the given counter. Creates new if one does not exist.
	 * 
	 * @param counter
	 */
	public void saveCounter(Counter counter) throws PerforceException {
		saveToPerforce(counter, new CounterBuilder());
	}

	/**
	 * Returns a list of counters in the system.
	 * 
	 * @return a list of counters in the system
	 * @throws PerforceException
	 */
	public List<Counter> getCounters() throws PerforceException {
		final String cmd[] = new String[] { "p4", "counters" };
		final List<Counter> counters = new ArrayList<Counter>();

		final StringBuilder response = getPerforceResponse(cmd);
		final List<String> names = parseList(response, 0);

		for(final String name : names) {
			counters.add(getCounter(name));
		}

		return counters;
	}

	/**
	 * Returns a counter specified by name.
	 * 
	 * @param name
	 *            counter name
	 * @return a counter specified by name
	 * @throws PerforceException
	 */
	public Counter getCounter(String name) throws PerforceException {
		final CounterBuilder builder = new CounterBuilder();
		final Counter counter = builder.build(getPerforceResponse(builder.getBuildCmd(name)));
		counter.setName(name);
		return counter;
	}
}
