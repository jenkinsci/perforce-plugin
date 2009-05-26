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

import java.io.Writer;
import java.util.Map;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.User;

/**
 * Responsible for building and saving user objects.
 * 
 * @author Mike Wille
 */
public class UserBuilder extends AbstractFormBuilder<User> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.AbstractFormBuilder#buildForm(java.util.Map)
	 */
	@Override
	public User buildForm(Map<String, String> fields) throws PerforceException {
		User user = new User();
		user.setUsername(getField("User", fields));
		user.setEmail(getField("Email", fields));
		user.setFullName(getField("FullName", fields));
		user.setPassword(getField("Password", fields));
		user.setJobView(getField("JobView", fields));
		user.setReviews(getField("Review", fields));
		return user;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#getBuildCmd(java.lang.String)
	 */
	public String[] getBuildCmd(String id) {
		return new String[] { "p4", "user", "-o", id };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#getSaveCmd()
	 */
	public String[] getSaveCmd(User obj) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#save(java.lang.Object, java.io.Writer)
	 */
	public void save(User obj, Writer writer) throws PerforceException {
		// TODO Auto-generated method stub
	}

}
