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

import java.util.Map;
import java.io.Writer;
import java.io.IOException;

import com.tek42.perforce.model.Group;
import com.tek42.perforce.PerforceException;

/**
 * @author Mike
 *         Date: Jul 21, 2008 2:44:07 PM
 */
public class GroupBuilder extends AbstractFormBuilder<Group> {
	public Group buildForm(Map<String, String> fields) throws PerforceException {
		Group group = new Group();
		group.setName(getField("Group", fields));
		group.setMaxLockTime(getField("MaxLockTime", fields));
		group.setMaxResults(getField("MaxResults", fields));
		group.setMaxScanRows(getField("MaxScanRows", fields));

		String value = getField("Timeout", fields);
		if(value.equals(""))
			value = "0";

		group.setTimeout(Long.parseLong(value));
		group.setUsers(getFieldAsList("Users", fields));
		group.setSubgroups(getFieldAsList("Subgroups", fields));
		group.setOwners(getFieldAsList("Owners", fields));

		return group;
	}

	public String[] getBuildCmd(String id) {
		return new String[] { "p4", "group", "-o", id };
	}

	public String[] getSaveCmd(Group obj) {
		return new String[] { "p4", "-s", "group", "-i" };
	}

	public void save(Group group, Writer out) throws PerforceException {
		// A bit of validation to make sure we are correct.  Perforce does NOT freaking tell us about this during the save...
		if(group.getOwnersAsString().equals("")) {
			throw new IllegalArgumentException("Group owner is a required field.");
		}
		/* form looks like:
			Group:  tekdev

			MaxResults: unlimited

			MaxScanRows:    unlimited

			MaxLockTime:    unlimited

			Timeout:    43200

			Subgroups:

			Owners:

			Users:
				anil
				mwille
		 */
		try {
			out.write("Group: " + group.getName() + "\n");
			out.write("MaxResults: " + group.getMaxResults() + "\n");
			out.write("MaxScanRows: " + group.getMaxScanRows() + "\n");
			out.write("MaxLockTime: " + group.getMaxLockTime() + "\n");
			out.write("Timeout: " + group.getTimeout() + "\n");
			out.write("Subgroups:\n");
			for(String s : group.getSubgroups()) {
				out.write(" " + s + "\n");
			}
			out.write("Owners:\n");
			for(String s : group.getOwners()) {
				out.write(" " + s + "\n");
			}
			out.write("Users:\n");
			for(String s : group.getUsers()) {
				out.write(" " + s + "\n");
			}

		} catch(IOException e) {
			throw new PerforceException("Failed to save group: " + group.getName(), e);
		}
	}
}
