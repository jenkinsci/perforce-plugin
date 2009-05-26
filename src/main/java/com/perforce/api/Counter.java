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
 * Representation of perforce counters. This class can be used to set and get
 * the value of a counter. It can also be used to determine who reviews the
 * changes represented by a particular value.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/01/23 $ $Revision: #2 $
 */
public final class Counter extends SourceControlObject {
	private String name = "";

	private int value = -1;

	private static HashDecay counters = null;

	/**
	 * Default no-argument constructor.
	 */
	public Counter() {
		super();
		getCache();
	}

	public Counter(String name) {
		this(null, name);
	}

	public Counter(Env environ, String name) {
		this();
		this.setEnv(environ);
		this.name = name;
	}

	private static HashDecay setCache() {
		if(null == counters) {
			counters = new HashDecay(300000);
			counters.start();
		}
		return counters;
	}

	public HashDecay getCache() {
		return setCache();
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Sets the value for the Counter.
	 * 
	 * @param value
	 *            New counter value
	 */
	public void setValue(int value) {
		this.value = value;
	}

	/**
	 * Returns the value of this Counter.
	 */
	public int getValue() {
		return this.value;
	}

	public void commit() throws CommitException {
		boolean valid = false;
		String l;
		this.value = value;
		String[] cmd = { "p4", "counter", this.name, String.valueOf(this.value) };
		P4Process p = null;
		try {
			p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("Counter") && l.endsWith("set."))
					valid = true;
			}
			p.close();
		} catch(Exception ex) {
			try {
				if(null != p)
					p.close();
			} catch(Exception ign) { /* Ignoring */
			}
			throw new CommitException(ex.getMessage());
		}
		if(!valid)
			throw new CommitException("Counter " + name + " not set.");
	}

	public void sync() throws PerforceException {
		String l;
		String[] cmd = { "p4", "counter", this.name };
		P4Process p = null;
		try {
			p = new P4Process(getEnv());
			p.exec(cmd);
			l = p.readLine();
			value = Integer.valueOf(l).intValue();
			while(null != (l = p.readLine())) {
			}
			p.close();
		} catch(Exception ex) {
			try {
				if(null != p)
					p.close();
			} catch(Exception ign) { /* Ignoring */
			}
			throw new PerforceException(ex.getMessage());
		}
	}

	/**
	 * Returns an array of all the Counters established on the server.
	 */
	public static Counter[] getCounters(Env env) throws PerforceException {
		Vector v = new Vector();
		Counter cntr;
		int num;
		StringTokenizer st;
		String l, name;
		String[] cmd = { "p4", "counters" };
		P4Process p = null;
		try {
			p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				st = new StringTokenizer(l);
				name = st.nextToken();
				if(!st.nextToken().equals("="))
					continue;
				num = Integer.parseInt(st.nextToken());
				cntr = new Counter(env, name);
				cntr.setValue(num);
				v.addElement(cntr);
			}
			p.close();
		} catch(Exception ex) {
			try {
				if(null != p)
					p.close();
			} catch(Exception ign) { /* Ignoring */
			}
			throw new PerforceException(ex.getMessage());
		}
		if(0 == v.size())
			return null;
		return (Counter[]) v.toArray(new Counter[0]);
	}

	/**
	 * Determine the changelists that need to be reviewed. This method returns
	 * an array of Changes that need to be reviewed.
	 */
	public Change[] review() throws PerforceException {
		Vector v = new Vector();
		Change chng;
		User usr;
		int num;
		String uid;
		String email;
		String name, t;
		StringTokenizer st;
		String l;
		String[] cmd = { "p4", "review", "-t", this.name };
		P4Process p = null;
		try {
			p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("Change")) {
					st = new StringTokenizer(l);
					if(!st.nextToken().equals("Change"))
						continue;
					num = Integer.parseInt(st.nextToken());
					uid = st.nextToken();
					email = st.nextToken("<> \t");
					name = st.nextToken("<> ()\t");
					try {
						while(null != (t = st.nextToken("<> ()\t"))) {
							name += (" " + t);
						}
					} catch(NoSuchElementException ex) {
					}
					// System.out.print("U: "+uid+", E: "+email+", N:
					// "+name+"\n");
					chng = new Change(getEnv());
					chng.setNumber(num);
					usr = new User(uid);
					usr.setEnv(getEnv());
					usr.setEmail(email);
					usr.setFullName(name);
					chng.setUser(usr);
					v.addElement(chng);
				}

			}
			p.close();
		} catch(Exception ex) {
			try {
				if(null != p)
					p.close();
			} catch(Exception ign) { /* Ignoring */
			}
			throw new PerforceException(ex.getMessage());
		}
		if(0 == v.size())
			return null;
		return (Change[]) v.toArray(new Change[0]);
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<counter name=\"");
		sb.append(this.name);
		sb.append("\" value=\"");
		sb.append(this.value);
		sb.append("\"/>");
		return sb.toString();
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] argv) {
		Debug.setDebugLevel(Debug.VERBOSE);
		Properties props = new Properties(System.getProperties());
		try {
			props.load(new BufferedInputStream(new FileInputStream(argv[0])));
			System.setProperties(props);
		} catch(Exception e) {
			System.err.println("Unable to load properties.");
			e.printStackTrace(System.err);
			System.exit(-1);
		}
		Env environ = new Env(props);
		System.out.println(environ);
		Counter cnt = new Counter(environ, "testval");
		try {
			cnt.setValue(42);
			cnt.commit();
			System.out.println("VAL(1): " + cnt.getValue());
			cnt.setValue(66);
			cnt.commit();
			System.out.println("VAL(2): " + cnt.getValue());
			cnt = new Counter(environ, "testval");
			cnt.sync();
			System.out.println("VAL(3): " + cnt.getValue());
			cnt.setValue(6850);
			cnt.commit();
			System.out.println("VAL(4): " + cnt.getValue());
			Change[] chngs = cnt.review();
			User[] usrs;
			int i, max = 0;
			for(i = 0; i < chngs.length; i++) {
				System.out.println("Must review change(1): " + chngs[i].getNumber());
				if(max < chngs[i].getNumber())
					max = chngs[i].getNumber();
				usrs = chngs[i].reviews();
				if(null != usrs) {
					for(int j = 0; j < usrs.length; j++) {
						System.out.println("\t" + usrs[j].getFullName());
					}
				}
			}
			cnt.setValue(max);
			cnt.commit();
			chngs = cnt.review();
			if(null != chngs && chngs.length > 0) {
				for(i = 0; i < chngs.length; i++) {
					System.out.println("Must review change(2): " + chngs[i].getNumber());
					if(max < chngs[i].getNumber())
						max = chngs[i].getNumber();
				}
			}
			Counter[] cntrs = Counter.getCounters(environ);
			for(i = 0; i < cntrs.length; i++) {
				System.out.println("Counter " + cntrs[i].getName() + ": " + cntrs[i].getValue());
			}
		} catch(PerforceException e) {
			System.err.println("Unable to set counter value.");
			e.printStackTrace();
			System.exit(-1);
		}
		System.err.println("Cleaning up.");
		Utils.cleanUp();
	}

}
