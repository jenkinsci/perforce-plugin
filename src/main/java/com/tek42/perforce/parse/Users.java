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
import com.tek42.perforce.model.User;

/**
 * Base API object for interacting with users.
 * 
 * @author Mike Wille
 */
public class Users extends AbstractPerforceTemplate {
	public Users(Depot depot) {
		super(depot);
	}

	/**
	 * Returns the user specified by username.
	 * 
	 * @param name
	 * @return
	 * @throws Exception
	 */
	public User getUser(String name) throws Exception {
		UserBuilder builder = new UserBuilder();
		User user = builder.build(getPerforceResponse(builder.getBuildCmd(name)));
		return user;
	}
}
