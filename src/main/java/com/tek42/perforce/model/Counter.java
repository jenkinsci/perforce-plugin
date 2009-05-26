package com.tek42.perforce.model;

/**
 * Represents a Perforce counter.
 * 
 * @author Kamlesh Sangani
 */
public class Counter {

	private String name;
	private int value = 0;

	/**
	 * Returns counter name
	 * 
	 * @return counter name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets counter name
	 * 
	 * @param name
	 *            counter name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns counter value
	 * 
	 * @return counter value
	 */
	public int getValue() {
		return value;
	}

	/**
	 * Sets counter value
	 * 
	 * @param value
	 *            counter value
	 */
	public void setValue(int value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.format("[Name=%s, Value=%d]", name, value);
	}
}
