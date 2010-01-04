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
 * An interface that indicates the implementing class can be cached for some
 * period of time. Implementing classes will be stored in a
 * <code>HashDecay</code> instance. Each instantiating class must create its
 * own <code>HashDecay</code> instance and return a reference to it through
 * the <code>getCache</code> method.
 * 
 * The update time is what is used by the <code>HasDecay</code> to determine
 * when an object will decay and be discarded.
 * 
 * @see HashDecay
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/01/15 $ $Revision: #2 $
 */
public interface Cacheable {

	/** Returns the time, in milliseconds, for this object's last update. */
	public long getUpdateTime();

	/** Sets the update time for this object to the current time. */
	public void refreshUpdateTime();

	/** Returns the time, in milliseconds, that this object was synchronized. */
	public long getSyncTime();

	/**
	 * Tests this object to see if it is out of sync. Checks to see if the
	 * expiration time is within the specified number of milliseconds.
	 * 
	 * @param threshold
	 *            Number of milliseconds.
	 * @return True if the object will be out of sync within the threshold.
	 */
	public boolean outOfSync(long threshold);

	/** Invalidates this object. */
	public void invalidate();

	/** Marks this object as being in in sync or valid. */
	public void inSync();

	/** Removes any cached objects. */
	public void clearCache();

	/** Returns the HashDecay instance for this class */
	public HashDecay getCache();

	/**
	 * Stores this object back into Perforce, creating it if it didn't already
	 * exist.
	 */
	public void commit() throws CommitException;

	/**
	 * Brings this object back into sync with Perforce. This also sets the sets
	 * the update and sync time for this object.
	 */
	public void sync() throws PerforceException;
}
