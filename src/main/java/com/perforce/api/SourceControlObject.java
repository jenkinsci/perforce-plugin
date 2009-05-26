package com.perforce.api;

import java.io.*;
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
 * An abstract base class for all source control objects.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/01/15 $ $Revision: #2 $
 */
public abstract class SourceControlObject implements Cacheable {
	private long update_time = 0;

	private long sync_time = 0;

	private Env environ;

	/** Default, no-argument constructor. */
	public SourceControlObject() {
		update_time = 0;
		sync_time = 0;
	}

	/**
	 * Constructor that takes an environment for this object to use.
	 * 
	 * @param env
	 *            source control environement to use.
	 */
	public SourceControlObject(Env env) {
		this();
		setEnv(env);
	}

	/**
	 * Sets the P4 environment to be used when working with this object. This
	 * environment is required to store, sync, or otherwise work with the P4
	 * depot. It is passed to the P4Process used in each of these transactions.
	 * 
	 * @see Env
	 * @see P4Process
	 * @param env
	 *            user environment to use.
	 */
	public void setEnv(Env env) {
		this.environ = env;
	}

	/**
	 * Returns the P4 environment associated with this instance.
	 * 
	 * @return P4 environment.
	 */
	public Env getEnv() {
		return this.environ;
	}

	/** Returns the time, in milliseconds, for this object's last update. */
	public synchronized long getUpdateTime() {
		return update_time;
	}

	/** Sets the update time for this object to the current time. */
	public synchronized void refreshUpdateTime() {
		update_time = (new Date()).getTime();
	}

	/** Returns the time, in milliseconds, that this object was synchronized. */
	public synchronized long getSyncTime() {
		return sync_time;
	}

	/**
	 * Tests this object to see if it is out of sync. Checks to see if the
	 * expiration time is within the specified number of milliseconds.
	 * 
	 * @param threshold
	 *            Number of milliseconds.
	 * @return True if the object will be out of sync within the threshold.
	 */
	public synchronized boolean outOfSync(long threshold) {
		return (threshold < ((new Date()).getTime() - sync_time));
	}

	/** Invalidates this object. */
	public synchronized void invalidate() {
		sync_time = 0;
	}

	/** Marks this object as being in in sync or valid. */
	public synchronized void inSync() {
		sync_time = (new Date()).getTime();
	}

	/** Removes any cached objects. */
	public void clearCache() {
		getCache().clear();
	}

	/** Returns the HashDecay instance for this class */
	public abstract HashDecay getCache();

	/**
	 * Stores this object back into Perforce, creating it if it didn't already
	 * exist.
	 */
	public abstract void commit() throws CommitException;

	/**
	 * Brings this object back into sync with Perforce. This also sets the sets
	 * the update and sync time for this object.
	 */
	public abstract void sync() throws PerforceException;

	/**
	 * Returns a string containing the object in XML form.
	 */
	public abstract String toXML();
}
