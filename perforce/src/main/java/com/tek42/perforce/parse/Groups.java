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

import java.util.List;
import java.util.ArrayList;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Group;
import com.tek42.perforce.model.Workspace;
import com.tek42.perforce.model.Changelist;

/**
 * Object for working with perforce groups.
 * 
 * @author Mike
 *         Date: Jul 21, 2008 3:38:25 PM
 */
public class Groups extends AbstractPerforceTemplate {
	public Groups(Depot depot) {
		super(depot);
	}

	/**
	 * Retrieves a single group specified by name.
	 *
	 * @param name	The name of the group to retrieve.
	 * @return	A valid perforce group.
	 * @throws PerforceException	If there is a problem or the group was not found.
	 */
	public Group getGroup(String name) throws PerforceException {
		GroupBuilder builder = new GroupBuilder();
		Group group = builder.build(getPerforceResponse(builder.getBuildCmd(name)));
		if(group == null)
			throw new PerforceException("Failed to retrieve group: " + name);

		return group;
	}

	/**
	 * Saves changes to an existing group, or creates a new one.
	 *
	 * @param group	The group to save.
	 * @throws PerforceException	When there is a problem.
	 */
	public void saveGroup(Group group) throws PerforceException {
		GroupBuilder builder = new GroupBuilder();
		saveToPerforce(group, builder);
	}

	/**
	 * Retrieves all groups in the perforce server.
	 *
	 * @return	A List of groups
	 * @throws PerforceException when there is a problem.
	 */
	public List<Group> getGroups() throws PerforceException {
		String cmd[] = new String[] { "p4", "groups" };
		StringBuilder response = getPerforceResponse(cmd);
		List<String> names = parseList(response, 0);

		List<Group> groups = new ArrayList<Group>();
		for(String name : names) {
			groups.add(getGroup(name));
		}

		return groups;
	}
}
