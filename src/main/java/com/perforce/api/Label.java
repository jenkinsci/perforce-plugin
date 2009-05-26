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
 * Representation of a source control lablel. There are static class methods
 * that can be used to list <a href="#getLabels()">all P4 labels</a> or to get
 * <a href="#getLabel(java.lang.String)">a particular label</a>.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #2 $
 */
public class Label extends Mapping {
	private static HashDecay labels = null;

	/**
	 * Default no-argument constructor.
	 */
	public Label() {
		super();
		getCache();
	}

	/**
	 * Constructor that is passed the label name.
	 */
	public Label(String name) {
		this();
		setName(name);
	}

	private synchronized static HashDecay setCache() {
		if(null == labels) {
			labels = new HashDecay(1200000);
			labels.start();
		}
		return labels;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Returns a list of labels that begin with the specified prefix.
	 * 
	 * @param prefix
	 *            Prefix for all labels to be returned
	 * @return List of labels matching the prefix.
	 * @deprecated Use
	 *             {@link #lookupLabels(Env, String) lookupLabels(Env, String)}
	 *             instead.
	 */
	public static Enumeration lookupLabels(String prefix) {
		return lookupMappings(labels, prefix);
	}

	/**
	 * Returns a list of labels that begin with the specified prefix.
	 * 
	 * @param prefix
	 *            Prefix for all labels to be returned
	 * @return List of labels matching the prefix.
	 */
	public static Enumeration lookupLabels(Env env, String prefix) {
		loadLabels(env);
		return lookupMappings(labels, prefix);
	}

	/**
	 * Loads the list of labels using the default environment.
	 * 
	 * @deprecated Use {@link #loadLabels(Env) loadLabels(Env, String)} instead.
	 */
	public static void loadLabels() {
		loadLabels(null);
	}

	/**
	 * Loads a list of all the labels into an internal class HashDecay. This
	 * method will only be called by the class itself if the HashDecay is empty.
	 * Users should call this method if they believe the p4 label information
	 * needs to be brought up to date.
	 * 
	 * @param env
	 *            Environment to use when working with P4
	 * @see HashDecay
	 */
	public static void loadLabels(Env env) {
		String l, name;
		String[] cmd = { "p4", "labels" };
		StringTokenizer st;
		Label b;

		setCache();
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(!l.startsWith("Label")) {
					continue;
				}
				st = new StringTokenizer(l);
				if(6 > st.countTokens()) {
					continue;
				}
				st.nextToken();
				name = st.nextToken();
				synchronized(labels) {
					if(null == (b = (Label) labels.get(name))) {
						b = new Label(name);
						b.setEnv(env);
					} else {
						b.refreshUpdateTime();
						continue;
					}
				}
				st.nextToken();
				st.nextToken("'");
				b.setDescription(st.nextToken());
				labels.put(b.getName(), b);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * @param env
	 *            Environment to use when working with P4
	 * @return List of all label names (Enumeration of String instances).
	 */
	public static Enumeration getLabelNames(Env env) {
		loadLabels(env);
		return labels.keys();
	}

	/**
	 * @return List of all labels (Enumeration of Label instances).
	 * @deprecated Use {@link #getLabels(Env) getLabels(Env)} instead.
	 */
	public static Enumeration getLabels() {
		return getLabels(null);
	}

	/**
	 * @param env
	 *            Environment to use when working with P4
	 * @return Enumeration of all labels (set of Label instances).
	 */
	public static Enumeration getLabels(Env env) {
		return Utils.getEnumeration(getLabelIterator(env));
	}

	/**
	 * @param env
	 *            Environment to use when working with P4
	 * @return Iterator for all labels (set of Label instances).
	 */
	public static Iterator getLabelIterator(Env env) {
		loadLabels(env);
		Enumeration en = labels.elements();
		TreeSet ts = new TreeSet();
		while(en.hasMoreElements()) {
			ts.add(en.nextElement());
		}
		return ts.iterator();
	}

	/**
	 * Returns a Label with the specified name, or null if not found.
	 * 
	 * @param name
	 *            Name of the label to find.
	 */
	public static synchronized Label getLabel(String name) {
		return getLabel(null, name, true);
	}

	/**
	 * Returns a Label with the specified name, or null if not found.
	 * 
	 * @param env
	 *            Environment to use when working with P4.
	 * @param name
	 *            Name of the label to find.
	 * @param force
	 *            Indicates that the Label should be sync'd.
	 */
	public static synchronized Label getLabel(Env env, String name, boolean force) {
		Label b;
		if(null == name || name.trim().equals(""))
			return null;
		if(null == (b = (Label) setCache().get(name)))
			b = new Label(name);
		if(null != env)
			b.setEnv(env);
		b.sync();
		labels.put(name, b);
		return b;
	}

	/**
	 * Stores the label information back into p4, creating the label if it
	 * didn't already exist.
	 * 
	 * @deprecated Use {@link #commit() commit()} instea.d
	 */
	public void store() throws CommitException {
		this.commit();
	}

	public void commit() throws CommitException {
		String[] cmd = { "p4", "label", "-i" };
		String l;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				p.println("Label: " + getName());
				p.println("Owner: " + getOwner());
				if(null != getDescription())
					p.println("Description:\n" + getDescription());
				p.println("View:");
				Enumeration en = getViews();
				while(en.hasMoreElements())
					p.println((String) en.nextElement());
				p.println("");
				p.flush();
				p.outClose();
				while(null != (l = p.readLine())) {
				}
				p.close();
			}
		} catch(Exception ex) {
			throw new CommitException(ex.getMessage());
		}
	}

	public void sync() {
		sync(getName());
	}

	/**
	 * Synchronizes the Label with the latest information from P4. This method
	 * forces the Label to contain the latest, correct information if it didn't
	 * already.
	 * 
	 * @param name
	 *            Name of the Label to synchronize.
	 */
	public void sync(String name) {
		if(!outOfSync(300000))
			return;
		setName(name);
		String description = "";
		String l;
		String[] cmd = { "p4", "label", "-o", "name" };
		cmd[3] = name;

		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				if(l.startsWith("Label:")) {
					setName(l.substring(8).trim());
				} else if(l.startsWith("Owner:")) {
					setOwner(l.substring(7).trim());
				} else if(l.startsWith("Description:")) {
					while(null != (l = p.readLine())) {
						if(!l.startsWith("\t"))
							break;
						description += l + "\n";
					}
					setDescription(description);
				} else if(l.startsWith("View:")) {
					while(null != (l = p.readLine())) {
						if(!(l.startsWith("\t") || l.startsWith(" ") || l.startsWith("//")))
							break;
						this.addView(l);
					}
				}
			}
			p.close();
			inSync();
		} catch(IOException ex) {
		}
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<label name=\"");
		sb.append(getName());
		sb.append("\" owner=\"");
		sb.append(getOwner());
		sb.append("\">");
		sb.append(super.toXML());
		sb.append("</label>");
		return sb.toString();
	}

	public static void main(String[] args) {
		String propfile = "/etc/p4.conf";
		Env environ = null;
		Enumeration labels = null;
		Label lbl = null;

		Debug.setDebugLevel(Debug.VERBOSE);
		if(0 < args.length)
			propfile = args[0];
		try {
			environ = new Env(propfile);
		} catch(PerforceException ex) {
			System.out.println("Could not load properties from " + propfile + ": " + ex);
			System.exit(-1);
		}
		System.out.println(environ);

		labels = getLabels(environ);
		while(labels.hasMoreElements()) {
			lbl = (Label) labels.nextElement();
			System.out.println(lbl.getName());
		}
		Utils.cleanUp();
	}
}
