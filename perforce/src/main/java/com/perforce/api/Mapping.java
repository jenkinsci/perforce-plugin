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
 * Representation of a source control mapping. This handles a named set of file
 * mappings.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #2 $
 */
public abstract class Mapping extends SourceControlObject implements Comparable {
	private String name = "";

	private String owner = "";

	private String description = "";

	private Hashtable views;

	private Vector view_list;

	/**
	 * Default no-argument constructor.
	 */
	public Mapping() {
		super();
		views = new Hashtable();
		view_list = new Vector();
	}

	/**
	 * Constructor that is passed the mapping name.
	 */
	public Mapping(String name) {
		this();
		setName(name);
	}

	public int compareTo(Object o) {
		return getName().compareTo(((Mapping) o).getName());
	}

	/**
	 * Sets the name of the Mapping.
	 * 
	 * @param name
	 *            Name of the mapping.
	 */
	public void setName(String name) {
		HashDecay mappings = getCache();
		synchronized(mappings) {
			mappings.remove(this.name);
			this.name = name;
			if(null != name && !name.trim().equals("")) {
				mappings.put(this.name, this);
			}
		}
	}

	/** Returns the name of the Mapping. */
	public String getName() {
		return name;
	}

	/**
	 * Sets the owner of the Mapping.
	 * 
	 * @param owner
	 *            Owner of the mapping.
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/** Returns the owner of the Mapping. */
	public String getOwner() {
		return owner;
	}

	/**
	 * Sets the description for the Mapping.
	 * 
	 * @param description
	 *            Description of the mapping.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/** Returns the Mapping description. */
	public String getDescription() {
		return description;
	}

	/**
	 * Add a view to the Mapping.
	 * 
	 * @param view
	 *            View for the mapping.
	 */
	public synchronized void addView(String from, String to) {
		views.put(from, to);
		if(null == to) {
			view_list.addElement(from);
		} else {
			view_list.addElement(from + " " + to);
		}
	}

	public void addView(String line) {
		int from, to;
		line = line.trim();
		if(-1 == (from = line.indexOf("//")))
			return;
		if(-1 == (to = line.lastIndexOf("//")))
			return;
		if(from == to) {
			addView(line.substring(0, to).trim(), null);
		} else {
			addView(line.substring(0, to).trim(), line.substring(to - 1).trim());
		}
	}

	/**
	 * @return String version of all the views in this mapping.
	 */
	public synchronized String getView() {
		StringBuffer sb = new StringBuffer();
		Enumeration en = views.keys();
		String key, val;
		while(en.hasMoreElements()) {
			key = (String) en.nextElement();
			val = (String) views.get(key);
			sb.append('\t');
			sb.append(key);
			if(null != val) {
				sb.append(' ');
				sb.append(val);
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/**
	 * @return Enumerated list of the views in this mapping.
	 */
	public Enumeration getViews() {
		return view_list.elements();
	}

	/**
	 * Returns a list of mappings that begin with the specified prefix.
	 * 
	 * @param prefix
	 *            Prefix for all mappings to be returned
	 * @return List of mappings matching the prefix.
	 */
	public static Enumeration lookupMappings(HashDecay mappings, String prefix) {
		Vector v = new Vector();
		synchronized(mappings) {
			Enumeration en = mappings.keys();
			while(en.hasMoreElements()) {
				String key = (String) en.nextElement();
				if(key.startsWith(prefix)) {
					v.addElement(key);
				}
			}
		}
		return v.elements();
	}

	/**
	 * @return Name of the mapping.
	 */
	public String toString() {
		return name;
	}

	public abstract void commit() throws CommitException;

	/**
	 * Synchronizes the Mapping with the latest information from P4. This method
	 * forces the Mapping to contain the latest, correct information if it
	 * didn't already.
	 * 
	 * @param name
	 *            Name of the Mapping to synchronize.
	 */
	public abstract void sync(String name);

	public String toXML() {
		StringBuffer sb = new StringBuffer("<mappings>");
		Enumeration en = views.keys();
		String key, val;
		while(en.hasMoreElements()) {
			key = (String) en.nextElement();
			val = (String) views.get(key);
			sb.append("<map><from>");
			sb.append(key);
			sb.append("</from>");
			if(null != val) {
				sb.append("<to>");
				sb.append(val);
				sb.append("</to>");
			}
			sb.append("</map>");
		}
		sb.append("</mappings>");
		return sb.toString();
	}
}
