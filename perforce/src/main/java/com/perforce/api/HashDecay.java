package com.perforce.api;

import java.util.*;

/*
 * Copyright (c) 2001, Perforce Software, All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * This hashtable build to contain objects that will decay over time. The
 * objects stored must implement the Cacheable interface.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2001/12/04 $ $Revision: #2 $
 * @see java.util.Hashtable
 * @see Cacheable
 */
public final class HashDecay extends Hashtable implements Runnable {
	/** The minimum delay allowed */
	public final static long MIN_DELAY = 0;

	private long delay = MIN_DELAY;

	private Thread decay_thread;

	private boolean runForever = true;

	private static Vector started = new Vector();

	static {
		started = new Vector();
	}

	public HashDecay() {
		super();
	}

	public HashDecay(long delay) {
		this();
		setDelay(delay);
	}

	/**
	 * Sets the delay, in milliseconds, before items in the hashtable are
	 * discarded.
	 */
	public void setDelay(long delay) {
		this.delay = (MIN_DELAY > delay) ? MIN_DELAY : delay;
	}

	/**
	 * Returns the delay.
	 */
	public long getDelay() {
		return delay;
	}

	public void run() {
		while(runForever) {
			try {
				Thread.sleep(delay);
			} catch(InterruptedException iex) {
			}
			decay();
		}
	}

	private synchronized void decay() {
		long now = (new Date()).getTime();
		Enumeration en = keys();
		Object key;
		while(en.hasMoreElements()) {
			key = en.nextElement();
			if(delay >= (now - ((Cacheable) get(key)).getUpdateTime())) {
				super.remove(key);
			}
		}
	}

	public Object put(Object key, Object value) {
		if(!(value instanceof Cacheable))
			return null;
		return put(key, (Cacheable) value);
	}

	public Object put(Object key, Cacheable value) {
		value.refreshUpdateTime();
		super.remove(key);
		Object obj = super.put(key, value);
		return obj;
	}

	public void start() {
		if(null != decay_thread)
			return;
		runForever = true;
		decay_thread = new Thread(this);
		decay_thread.start();
		started.addElement(this);
	}

	public void stop() {
		runForever = false;
		if(null == decay_thread)
			return;
		try {
			decay_thread.interrupt();
			decay_thread.join(100);
		} catch(InterruptedException ex) {
			decay_thread.destroy();
		} catch(SecurityException ex) {
			decay_thread.destroy();
		}
	}

	/**
	 * Stops running threads for all HashDecay instances.
	 */
	public static void stopAll() {
		HashDecay hd;
		Enumeration en = started.elements();
		while(en.hasMoreElements()) {
			hd = (HashDecay) en.nextElement();
			hd.stop();
		}
	}
}
