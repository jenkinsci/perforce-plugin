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
 * Representation of a source control branch. There are static class methods
 * that can be used to list <a href="#getBranches()">all P4 branches</a> or to
 * get <a href="#getBranch(java.lang.String)">a particular branch</a>.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #3 $
 */
public class Branch extends Mapping {
	private static HashDecay branches = null;

	/**
	 * Default no-argument constructor.
	 */
	public Branch() {
		super();
		getCache();
	}

	/**
	 * Constructor that is passed the branch name.
	 */
	public Branch(String name) {
		this();
		setName(name);
	}

	private static HashDecay setCache() {
		if(null == branches) {
			branches = new HashDecay(1200000);
			branches.start();
		}
		return branches;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Returns a list of branches that begin with the specified prefix.
	 * 
	 * @param prefix
	 *            Prefix for all branches to be returned
	 * @return List of branches matching the prefix.
	 */
	public static Enumeration lookupBranches(String prefix) {
		return lookupMappings(branches, prefix);
	}

	/**
	 * Loads the list of branches using the default environment.
	 * 
	 * @see Env
	 */
	public static void loadBranches() {
		loadBranches(null);
	}

	/**
	 * Loads a list of all the branches into an internal class HashDecay. This
	 * method will only be called by the class itself if the HashDecay is empty.
	 * Users should call this method if they believe the p4 branch information
	 * needs to be brought up to date.
	 * 
	 * @param env
	 *            Environment to use when working with P4
	 * @see HashDecay
	 */
	public static void loadBranches(Env env) {
		String l, name;
		String[] cmd = { "p4", "branches" };
		StringTokenizer st;
		Branch b;

		setCache();
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(!l.startsWith("Branch")) {
					continue;
				}
				st = new StringTokenizer(l);
				if(6 > st.countTokens()) {
					continue;
				}
				st.nextToken();
				name = st.nextToken();
				synchronized(branches) {
					if(null == (b = (Branch) branches.get(name))) {
						b = new Branch(name);
						b.setEnv(env);
					} else {
						b.refreshUpdateTime();
						continue;
					}
				}
				st.nextToken();
				st.nextToken("'");
				b.setDescription(st.nextToken());
				branches.put(b.getName(), b);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Returns list of all branch names.
	 * 
	 * @return <code>Enumeration</code> of <code>String</code>s containing
	 *         branch names.
	 */
	public static Enumeration getBranchNames(Env env) {
		loadBranches(env);
		return branches.keys();
	}

	/**
	 * Returns list of all branches.
	 * 
	 * @return <code>Enumeration</code> of <code>Branch</code>es.
	 * @deprecated
	 */
	public static Enumeration getBranches() {
		return getBranches(null);
	}

	/**
	 * @param env
	 *            Source control environment.
	 * @return <code>Enumeration</code> of <code>Branch</code>es.
	 */
	public static Enumeration getBranches(Env env) {
		return Utils.getEnumeration(getBranchIterator(env));
	}

	/**
	 * @param env
	 *            Source control environment.
	 * @return <code>Iterator</code> of <code>Branch</code>es.
	 */
	public static Iterator getBranchIterator(Env env) {
		loadBranches(env);
		Enumeration en = branches.elements();
		TreeSet ts = new TreeSet();
		while(en.hasMoreElements()) {
			ts.add(en.nextElement());
		}
		return ts.iterator();
	}

	/**
	 * Returns a Branch with the specified name, or null if not found.
	 * 
	 * @param name
	 *            Name of the branch to find.
	 */
	public static synchronized Branch getBranch(String name) {
		return getBranch(null, name, true);
	}

	/**
	 * Returns a Branch with the specified name, or null if not found.
	 * 
	 * @param env
	 *            Environment to use when working with P4.
	 * @param name
	 *            Name of the branch to find.
	 * @param force
	 *            Indicates that the Branch should be sync'd.
	 */
	public static synchronized Branch getBranch(Env env, String name, boolean force) {
		Branch b;
		if(null == name || name.trim().equals(""))
			return null;
		if(null == (b = (Branch) setCache().get(name)))
			b = new Branch(name);
		if(null != env)
			b.setEnv(env);
		b.sync();
		branches.put(name, b);
		return b;
	}

	/**
	 * Integrate a set of files using the named branch. Creates a Change that
	 * contains the integraed files. The change will be *PENDING* after this
	 * completes.
	 * 
	 * @param env
	 *            environment to use when working with P4.
	 * @param fents
	 *            list of FileEntries to be integrated.
	 * @param branch
	 *            name of the branch to integrate with.
	 * @param sb
	 *            buffer that will contain a log of the integration.
	 * @param description
	 *            description to be used for the Change created.
	 * @return Change containing the files integrated.
	 * @see Change
	 */
	public static Change integrate(Env env, Vector fents, String branch, StringBuffer sb, String description)
			throws CommitException, PerforceException {
		Change c = new Change();
		c.setEnv(env);
		c.setDescription(description);
		c.setUser(User.getUser(env.getUser()));
		c.setClientName(env.getClient());
		c.commit();
		return integrate(env, fents, branch, sb, c);
	}

	/**
	 * Integrate a set of files using the named branch. Uses the Change passed
	 * in to contain the integraed files. The change will be *PENDING* after
	 * this completes.
	 * 
	 * @param env
	 *            environment to use when working with P4.
	 * @param fents
	 *            list of FileEntries to be integrated.
	 * @param branch
	 *            name of the branch to integrate with.
	 * @param sb
	 *            buffer that will contain a log of the integration.
	 * @param c
	 *            Change to be used to contain the integrated files.
	 * @return Change containing the files integrated.
	 * @see Change
	 */
	public static Change integrate(Env env, Vector fents, String branch, StringBuffer sb, Change c)
			throws PerforceException {
		FileEntry fent;

		Enumeration en = fents.elements();
		while(en.hasMoreElements()) {
			fent = (FileEntry) en.nextElement();
			integrate(env, fent.getDepotPath() + "#" + fent.getHeadRev(), branch, sb, c);
		}
		return c;
	}

	/**
	 * Class method for integrating using the instantiated Branch.
	 * 
	 * @param source
	 *            source files to integrate from.
	 * @param sb
	 *            buffer that will contain a log of the integration.
	 * @param c
	 *            Change to be used to contain the integrated files.
	 * @see Branch#integrate(Env,String,String,StringBuffer,Change)
	 */
	public Change integrate(String source, StringBuffer sb, Change c) throws PerforceException {
		if(null == c) {
			c = new Change();
			c.setDescription("Automated Integration");
			c.commit();
		}
		return Branch.integrate(this.getEnv(), source, this.getName(), sb, c);
	}

	/**
	 * Integrate a set of files using the named branch. Uses the Change passed
	 * in to contain the integraed files. The change will be *PENDING* after
	 * this completes.
	 * 
	 * @param env
	 *            environment to use when working with P4.
	 * @param source
	 *            source files to integrate from.
	 * @param branch
	 *            name of the branch to integrate with.
	 * @param sb
	 *            buffer that will contain a log of the integration.
	 * @param c
	 *            Change to be used to contain the integrated files.
	 * @return Change containing the files integrated.
	 * @see Change
	 */
	public static Change integrate(Env env, String source, String branch, StringBuffer sb, Change c)
			throws PerforceException {
		String[] intcmd = { "p4", "integrate", "-v", "-d", "-c", String.valueOf(c.getNumber()), "-b", branch, "-s",
				source };
		P4Process p;
		String l;

		intcmd[5] = String.valueOf(c.getNumber());
		for(int i = 0; i < 8; i++) {
			sb.append(intcmd[i]);
			sb.append(' ');
		}
		sb.append('\n');
		try {
			p = new P4Process(env);
			p.exec(intcmd);
			while(null != (l = p.readLine())) {
				if(null != sb) {
					sb.append(l);
					sb.append('\n');
				}
			}
			p.close();
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage());
		}
		return c;
	}

	/**
	 * Stores the branch information back into p4, creating the branch if it
	 * didn't already exist.
	 * 
	 * @deprecated Use {@link #commit() commit()} instead.
	 */
	public void store() throws CommitException {
		this.commit();
	}

	public void commit() throws CommitException {
		String[] cmd = { "p4", "branch", "-i" };
		String l;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				p.println("Branch: " + getName());
				p.println("Owner: " + getOwner());
				p.println("View:");
				p.println(getView());
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
	 * Synchronizes the Branch with the latest information from P4. This method
	 * forces the Branch to contain the latest, correct information if it didn't
	 * already.
	 * 
	 * @param name
	 *            Name of the Branch to synchronize.
	 */
	public void sync(String name) {
		if(!outOfSync(300000))
			return;
		setName(name);
		String description = "";
		String l;
		String[] cmd = { "p4", "branch", "-o", "name" };
		cmd[3] = name;

		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				if(l.startsWith("Branch:")) {
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
			Debug.out(Debug.ERROR, ex);
		}
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<branch name=\"");
		sb.append(getName());
		sb.append("\" owner=\"");
		sb.append(getOwner());
		sb.append("\">");
		sb.append(super.toXML());
		sb.append("</branch>");
		return sb.toString();
	}
}
