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

package com.tek42.perforce.model;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a group in perforce.
 *
 * @author Mike
 *         Date: Jul 21, 2008 2:42:09 PM
 */
public class Group {
	String name;
	String maxResults;
	String maxScanRows;
	String maxLockTime;
	Long timeout;
	List<String> subgroups;
	List<String> owners;
	List<String> users;

	public Group() {
		maxResults = "unlimited";
		maxScanRows = "unlimited";
		maxLockTime = "unlimited";
		Long timeout = 43200L;
		subgroups = new ArrayList<String>(0);
		owners = new ArrayList<String>(0);
		users = new ArrayList<String>(0);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("Group: " + name + "\n");
		sb.append("MaxResults: " + maxResults + "\n");
		sb.append("MaxScanRows: " + maxScanRows + "\n");
		sb.append("MaxLockTime: " + maxLockTime + "\n");
		sb.append("Timeout: " + timeout + "\n");
		sb.append("SubGroups:\n" + getSubgroupsAsString() + "\n");
		sb.append("Owners:\n" + getOwnersAsString() + "\n");
		sb.append("Users:\n" + getUsersAsString() + "\n");

		return sb.toString();
	}

	public String getMaxLockTime() {
		return maxLockTime;
	}

	public void setMaxLockTime(String maxLockTime) {
		this.maxLockTime = maxLockTime;
	}

	public String getMaxResults() {
		return maxResults;
	}

	public void setMaxResults(String maxResults) {
		this.maxResults = maxResults;
	}

	public String getMaxScanRows() {
		return maxScanRows;
	}

	public void setMaxScanRows(String maxScanRows) {
		this.maxScanRows = maxScanRows;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getOwners() {
		return owners;
	}

	public String getOwnersAsString() {
		StringBuilder sb = new StringBuilder();
		for(String owner : owners) {
			sb.append(owner);
			sb.append("\n");
		}
		return sb.toString();

	}

	public void setOwners(List<String> owners) {
		this.owners = owners;
	}

	public List<String> getSubgroups() {
		return subgroups;
	}

	public String getSubgroupsAsString() {
		StringBuilder sb = new StringBuilder();
		for(String s : subgroups) {
			sb.append(s);
			sb.append("\n");
		}
		return sb.toString();
	}

	public void setSubgroups(List<String> subgroups) {
		this.subgroups = subgroups;
	}

	public Long getTimeout() {
		return timeout;
	}

	public void setTimeout(Long timeout) {
		this.timeout = timeout;
	}

	public List<String> getUsers() {
		return users;
	}

	public String getUsersAsString() {
		StringBuilder sb = new StringBuilder();
		for(String s : users) {
			sb.append(s);
			sb.append("\n");
		}
		return sb.toString();
	}

	public void setUsers(List<String> users) {
		this.users = users;
	}
}
