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

/**
 * Represents a Perforce clientspec or workspace.
 * <p>
 * This is necessary because the Client class that Perforce provides in their API is not complete. It is missing several
 * fields and we cannot extend that class because its final.
 * 
 * @author Mike Wille
 */
public class Workspace extends AbstractViewsSupport implements java.io.Serializable {
	String name;
	String owner;
	String host;
	String description;
	String root;
	String altRoots;
	String options;
	String lineEnd;
	String submitOptions;
	String update;
	String access;

	public Workspace() {
		super();
		this.name = "";
		this.owner = "";
		this.host = "";
		this.description = "";
		this.root = "";
		this.altRoots = "";
		this.options = "";
		this.lineEnd = "";
		this.submitOptions = "";
		this.update = "";
		this.access = "";
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Client]\n");
		sb.append("Name: " + getName() + "\n");
		sb.append("Update: " + getUpdate() + "\n");
		sb.append("Access: " + getAccess() + "\n");
		sb.append("Owner: " + getOwner() + "\n");
		sb.append("Host: " + getHost() + "\n");
		sb.append("Description: " + getDescription() + "\n");
		sb.append("Root: " + getRoot() + "\n");
		sb.append("AltRoot: " + getAltRoots() + "\n");
		sb.append("Options: " + getOptions() + "\n");
		sb.append("SubmitOptions: " + getSubmitOptions() + "\n");
		sb.append("LineEnd: " + getLineEnd() + "\n");
		sb.append("Views: \n");
		for(String view : views) {
			sb.append("\t" + view + "\n");
		}

		return sb.toString();
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the owner
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * @param owner
	 *            the owner to set
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @param host
	 *            the host to set
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description
	 *            the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the root
	 */
	public String getRoot() {
		return root;
	}

	/**
	 * @param root
	 *            the root to set
	 */
	public void setRoot(String root) {
		this.root = root;
	}

	/**
	 * @return the altRoots
	 */
	public String getAltRoots() {
		return altRoots;
	}

	/**
	 * @param altRoots
	 *            the altRoots to set
	 */
	public void setAltRoots(String altRoots) {
		this.altRoots = altRoots;
	}

	/**
	 * @return the options
	 */
	public String getOptions() {
		return options;
	}

	/**
	 * @param options
	 *            the options to set
	 */
	public void setOptions(String options) {
		this.options = options;
	}

	/**
	 * @return the lineEnd
	 */
	public String getLineEnd() {
		return lineEnd;
	}

	/**
	 * @param lineEnd
	 *            the lineEnd to set
	 */
	public void setLineEnd(String lineEnd) {
		this.lineEnd = lineEnd;
	}

	/**
	 * @return the submitOptions
	 */
	public String getSubmitOptions() {
		return submitOptions;
	}

	/**
	 * @param submitOptions
	 *            the submitOptions to set
	 */
	public void setSubmitOptions(String submitOptions) {
		this.submitOptions = submitOptions;
	}

	/**
	 * @return the update
	 */
	public String getUpdate() {
		return update;
	}

	/**
	 * @param update
	 *            the update to set
	 */
	public void setUpdate(String update) {
		this.update = update;
	}

	/**
	 * @return the access
	 */
	public String getAccess() {
		return access;
	}

	/**
	 * @param access
	 *            the access to set
	 */
	public void setAccess(String access) {
		this.access = access;
	}

}
