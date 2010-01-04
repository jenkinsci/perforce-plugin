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
 * Container class for messages to and from the P4Robot. This message is sent to
 * the robot by the P4ReviewerService and any other application that wants to
 * instruct the P4Robot to synchronize with the P4 depot in a particular way.
 * <p>
 * The key to this is the views that are a part of this. Each view will be used
 * in executing "p4 sync" on the P4Robot. If the SYNC_FORCE flag is specified,
 * then the views are all executed with "p4 sync -f". If the SYNC_ALL flag is
 * specified, the views are ignored and one "p4 sync //..." is executed (with
 * the -f flag, if that is also specified).
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2001/11/05 $ $Revision: #1 $
 * @deprecated This shoud be a part of the P4WebPublisher package.
 */
public class RobotMessage implements Serializable {
	/** Indicates that the "p4 sync" should be run with the -f option */
	public final static int SYNC_FORCE = 1;

	/** Indicates that the views should be ignored and "p4 sync //..." is run */
	public final static int SYNC_ALL = 2;

	/** @serial */
	private Vector views = new Vector();

	/** @serial */
	private String label = "";

	/** @serial */
	private int change = -1;

	/** @serial */
	private int flags = 0;

	/**
	 * Default no-argument constructor.
	 */
	public RobotMessage() {
	}

	/**
	 * Constructs a RobotMessage using the specified Change. What this does is
	 * load the views for this with the files that were affected by the Change.
	 * This allows for a more focused sync on the robot side.
	 * 
	 * @param change
	 *            Change to be used in constructing this.
	 */
	public RobotMessage(Change change) {
		this();

		views = change.getFiles();
	}

	/**
	 * Add a view. The view will be appended to a "p4 sync" executed by the
	 * robot. Thus, any valid file specification that is valid with "p4 sync"
	 * can be used: file[revRange]
	 * 
	 * @param view
	 *            Single view to be added.
	 */
	public void addView(String view) {
		views.addElement(view);
	}

	/**
	 * Clear all view information. This is useful, if the RobotMessage instance
	 * is to be reused in another send to a robot.
	 */
	public void clearViews() {
		views.removeAllElements();
	}

	/**
	 * Returns the number of views in this.
	 */
	public int getViewCount() {
		return views.size();
	}

	/**
	 * Returns an enumeration of the views. This is most useful on the robot end
	 * of things.
	 */
	public Enumeration getViews() {
		return views.elements();
	}

	/**
	 * Sets the flags.
	 * 
	 * @see #SYNC_FORCE
	 * @see #SYNC_ALL
	 * @param flags
	 *            New value for the flags
	 */
	public void setFlags(int flags) {
		this.flags = flags;
	}

	/**
	 * Returns the flags that are set.
	 * 
	 * @see #SYNC_FORCE
	 * @see #SYNC_ALL
	 */
	public int getFlags() {
		return flags;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		if(null == views) {
			views = new Vector();
		}
		out.writeObject(views);
		out.writeObject(label);
		out.writeInt(change);
		out.writeInt(flags);
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		views = (Vector) in.readObject();
		label = (String) in.readObject();
		change = in.readInt();
		flags = in.readInt();
	}

}
