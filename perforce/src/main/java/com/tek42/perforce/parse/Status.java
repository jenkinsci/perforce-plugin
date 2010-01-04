/*
 *	P4Java - java integration with Perforce SCM
 *	Copyright (C) 2007-,  Mike Wille, Tek42
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *	You can contact the author at:
 *
 *	Web:	http://tek42.com
 *	Email:	mike@tek42.com
 *	Mail:	755 W Big Beaver Road
 *			Suite 1110
 *			Troy, MI 48084
 */

package com.tek42.perforce.parse;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;

/**
 * Allows checking the status of the depot.
 * 
 * @author Mike Wille
 */
public class Status extends AbstractPerforceTemplate {
	public Status(Depot depot) {
		super(depot);
	}

	/**
	 * Checks the environment to see if it is valid. To check the validity of the environment, the user information is
	 * accessed. This ensures that the server can be contacted and that the password is set properly.
	 * <p>
	 * If the environment is valid, this method will return true. Otherwise, it will throw a
	 * <code>PerforceException</code> with a message regarding the failure.
	 */
	public boolean isValid() throws PerforceException {
		getPerforceResponse(new String[] { "p4", "user", "-o" });
		return true;
	}

	/**
	 * Checks the specified path to see if it exists in the depot. This may take a bit of time the first time it is
	 * called. It seems perforce takes a bit to wake up.
	 * <p>
	 * The path must end with the perforce wildcard: /... Otherwise it will return no results.
	 * <p>
	 * Note: this method may move once the API is more complete.
	 * 
	 * @param path
	 *            Path to check, example: //depot/MyProject/...
	 * @return True if it exists, false if not.
	 * @throws PerforceException
	 */
	public boolean exists(String path) throws PerforceException {
		StringBuilder sb = getPerforceResponse(new String[] { "p4", "fstat", "-m", "1", path });
		if(sb.indexOf("no such file(s).") > 0)
			return false;
		return true;
	}
}
