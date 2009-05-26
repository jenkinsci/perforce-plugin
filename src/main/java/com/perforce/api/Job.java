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
 * Representation of a source control job. This class can be used to determine
 * information for a particular p4 job. It can be constructed using the job
 * name, but will not contain any additional change information until the <a
 * href="#sync()">sync()</a> method is called.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #2 $
 */
public final class Job extends SourceControlObject {
	private String name = "new";

	private String user = null;

	private String modtime_string = "";

	private String description = "\tUseless description. This must be changed.";

	private int status = OPEN;

	private Hashtable fields = new Hashtable();

	private static HashDecay jobs = new HashDecay();

	private Change[] changes = null;

	/** Indicates that the Job is open. */
	public final static int OPEN = 1;

	/** Indicates that the Job is closed. */
	public final static int CLOSED = 2;

	/** Indicates that the Job is suspended. */
	public final static int SUSPENDED = 4;

	/**
	 * Default no-argument constructor.
	 */
	public Job(Env env) {
		super();
		if(null == fields) {
			fields = new Hashtable();
		}
		getCache();
		setEnv(env);
	}

	private static HashDecay setCache() {
		if(null == jobs) {
			jobs = new HashDecay(300000);
			jobs.start();
		}
		return jobs;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Constructor that accepts the job number. This job is not populated with
	 * the correct information until the sync() method is called on it.
	 * 
	 * @param name
	 *            Job name
	 */
	public Job(String name) {
		this((Env) null);
		this.name = name;
	}

	public Job() {
		this((Env) null);
	}

	public Job(Env env, String name) {
		this(env);
		this.name = name;
	}

	/** Returns the job with the specified name. */
	public static Job getJob(String name) {
		return getJob(null, name);
	}

	/** Returns the job with the specified name. */
	public static Job getJob(Env env, String name) {
		Job j = new Job(name);
		if(null != env)
			j.setEnv(env);
		j.sync();
		return j;
	}

	/** Returns the job's modification time */
	public String getModtimeString() {
		return modtime_string;
	}

	/** Sets the job's modification time */
	public void setModtimeString(String modtime) {
		this.modtime_string = modtime;
	}

	/**
	 * Sets the job name for the Job. This invalidates all the other data for
	 * the Job.
	 * 
	 * @param name
	 *            Job name
	 */
	public void setName(String name) {
		this.name = name;
		user = null;
		description = "";
		status = OPEN;
	}

	/**
	 * Returns the name of this Job.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the User that owns this Job.
	 * 
	 * @param user
	 *            Owning user.
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Returns the User that owns this Job.
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets the description for the job.
	 */
	public void setDescription(String description) {
		String l;
		try {
			StringBuffer sb = new StringBuffer();
			BufferedReader b = new BufferedReader(new StringReader(description));
			while(null != (l = b.readLine())) {
				sb.append('\t');
				sb.append(l.trim());
				sb.append('\n');
			}
			this.description = sb.toString();
		} catch(IOException ex) {
			this.description = description;
		}
	}

	/**
	 * Returns the description for the Job. This description includes not only
	 * the textual description provided by the user, but also the list of
	 * affected files and how they were affected.
	 * 
	 * The String returned includes newline characters.
	 */
	public String getDescription() {
		return description;
	}

	public Enumeration getFieldNames() {
		return fields.keys();
	}

	public void setField(String name, String value) {
		fields.put(name.toLowerCase(), value);
	}

	public String getField(String name) {
		return (String) fields.get(name.toLowerCase());
	}

	public Vector getFileEntries() {
		Vector v = new Vector();
		Integer pos;
		Hashtable h = new Hashtable();
		Enumeration en;
		FileEntry fent;
		changes = getChanges();
		for(int i = 0; i < changes.length; i++) {
			changes[i].sync();
			en = changes[i].getFileEntries().elements();
			while(en.hasMoreElements()) {
				fent = (FileEntry) en.nextElement();
				if(null != (pos = (Integer) h.get(fent.getDepotPath()))) {
					if(((FileEntry) v.elementAt(pos.intValue())).getHeadRev() < fent.getHeadRev()) {
						v.setElementAt(fent, pos.intValue());
					}
				} else {
					v.addElement(fent);
					h.put(fent.getDepotPath(), new Integer(v.size() - 1));
				}
			}
		}
		return v;
	}

	/**
	 * Sets status for the Job. This can be either OPEN, CLOSED, or SUSPENDED.
	 */
	public void setStatus(String status) {
		if(-1 != status.indexOf("closed")) {
			this.status = CLOSED;
		} else if(-1 != status.indexOf("suspended")) {
			this.status = SUSPENDED;
		} else {
			this.status = OPEN;
		}
	}

	/**
	 * Sets status for the Job. This can be either OPEN, CLOSED, or SUSPENDED.
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * Returns the status for the Job. This can be either OPEN, CLOSED, or
	 * SUSPENDED.
	 */
	public int getStatus() {
		return status;
	}

	public String getStatusName() {
		switch(status) {
		case SUSPENDED:
			return "suspended";
		case CLOSED:
			return "closed";
		default:
			return "open";
		}
	}

	/**
	 * Stores the job information back into perforce.
	 * 
	 * @deprecated Use {@link #commit() commit()} instead.
	 */
	public void store() throws CommitException {
		this.commit();
	}

	public void commit() throws CommitException {
		StringBuffer sb = new StringBuffer();
		String[] cmd = { "p4", "job", "-i" };
		String l, key;
		int pos;
		boolean store_failed = false;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			p.println("Job: " + getName());
			p.println("Status: " + getStatusName());
			if(null == getUser() && null != getEnv()) {
				p.println("User: " + getEnv().getUser());
			} else {
				p.println("User: " + user);
			}
			p.println("Description: ");
			p.println(getDescription());
			Enumeration en = fields.keys();
			while(en.hasMoreElements()) {
				key = (String) en.nextElement();
				p.println(key + ": " + (String) fields.get(key));
			}
			p.flush();
			p.outClose();
			while(null != (l = p.readLine())) {
				if(l.startsWith("Job ") && (-1 != (pos = l.indexOf("saved")))) {
					setName(l.substring(4, pos - 1).trim());
				}
				if(l.startsWith("Error"))
					store_failed = true;
				sb.append(l);
				sb.append('\n');
			}
			p.close();
		} catch(Exception ex) {
			throw new CommitException(ex.getMessage());
		}
		if(store_failed) {
			throw new CommitException(sb.toString());
		}
	}

	/**
	 * Synchronizes the Job with the correct information from P4, using whatever
	 * job number has already been set in the Job. After this method is called,
	 * all the information in the Job is valid.
	 */
	public void sync() {
		sync(name);
	}

	/**
	 * Sycnhronizes the Job with the correct information from P4. After this
	 * method is called, all the information in the Job is valid.
	 * 
	 * @param number
	 *            Job number
	 */
	public void sync(String name) {
		this.name = name;
		int pos;
		String l;
		String[] cmd = { "p4", "job", "-o", "jobname" };
		cmd[3] = name;
		String tmpdesc = "";

		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			p.setRawMode(true);
			while(null != (l = p.readLine())) {
				if(l.startsWith("info: ")) {
					l = l.substring(6);
				} else {
					continue;
				}
				if(l.startsWith("#"))
					continue;
				if(l.startsWith("Job:")) {
					name = l.substring(4).trim();
				} else if(l.startsWith("Status:")) {
					setStatus(l.substring(8).trim());
				} else if(l.startsWith("ReportedBy:")) {
					user = l.substring(11).trim();
				} else if(l.startsWith("ReportedDate:")) {
					modtime_string = l.substring(14).trim();
				} else if(l.startsWith("Description:")) {
					while(null != (l = p.readLine()) && l.startsWith("info: \t")) {
						tmpdesc += l.substring(6).trim() + "\n";
					}
					setDescription(tmpdesc);
				} else if(-1 != (pos = l.indexOf(':'))) {
					setField(l.substring(0, pos).trim(), l.substring(pos + 1).trim());
				}
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
	}

	public static void fix(Env env, String changelist, boolean del, String job) {
		Vector jobs = new Vector();
		jobs.addElement(job);
		fix(env, changelist, del, jobs);
	}

	public static void fix(Env env, String changelist, boolean del, Vector jobs) {
		if(null == jobs)
			return;
		int i = 0, len = (del) ? 5 : 4;
		len += jobs.size();
		String cmd[] = new String[len];
		String l;
		cmd[i++] = "p4";
		cmd[i++] = "fix";
		cmd[i++] = "-c";
		cmd[i++] = changelist;
		if(del)
			cmd[i++] = "-d";
		Enumeration en = jobs.elements();
		while(en.hasMoreElements()) {
			cmd[i++] = (String) en.nextElement();
		}
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
	}

	public void removeFix(int changelist) {
	}

	/**
	 * Overrides the default toString() method.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer("Job: ");
		sb.append(name);
		sb.append("\nUser: ");
		sb.append(user);
		sb.append("\nDescription:\n");
		sb.append(description);
		return sb.toString();
	}

	public static Job[] getJobs(Env env) {
		return getJobs(env, (String) null, 0, false, (String[]) null);
	}

	public static Job[] getJobs(Env env, String jobview, int max, boolean use_integs, String[] files) {
		int args = 3, pos = 0;
		if(null != jobview)
			args += 2;
		if(use_integs)
			args++;
		if(0 < max)
			args += 2;
		if(null != files)
			args += files.length;
		String[] cmd = new String[args];
		cmd[pos++] = "p4";
		cmd[pos++] = "jobs";
		cmd[pos++] = "-l";
		if(null != jobview) {
			cmd[pos++] = "-e";
			cmd[pos++] = jobview;
		}
		if(use_integs) {
			cmd[pos++] = "-i";
		}
		if(0 < max) {
			cmd[pos++] = "-m";
			cmd[pos++] = String.valueOf(max);
		}
		if(null != files) {
			for(int i = 0; i < files.length; i++) {
				cmd[pos++] = files[i];
			}
		}

		Vector v = new Vector();
		Job[] jobs;
		Job j = null;
		StringTokenizer st;
		String l, name, user, tmpdesc = "", modtime, state;

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			p.setRawMode(true);
			while(null != (l = p.readLine())) {
				if(l.startsWith("info: \t")) {
					tmpdesc += l.substring(7) + "\n";
					continue;
				} else if(l.trim().equals("info:")) {
					continue;
				}
				if(null != j) {
					j.setDescription(tmpdesc);
					v.addElement(j);
				}
				tmpdesc = "";
				j = null;
				st = new StringTokenizer(l);
				if(2 > st.countTokens())
					continue;
				st.nextToken(); /* Skip 'info:' */
				name = st.nextToken();
				if(!st.nextToken().equals("on"))
					continue;
				modtime = st.nextToken();
				if(!st.nextToken().equals("by"))
					continue;
				user = st.nextToken();
				state = st.nextToken();
				j = new Job(env, name);
				j.setModtimeString(modtime);
				j.setStatus(state);
				j.setUser(user);
			}
			p.close();
			if(null != j) {
				j.setDescription(tmpdesc);
				v.addElement(j);
			}
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		jobs = new Job[v.size()];
		for(int i = 0; i < v.size(); i++) {
			jobs[i] = (Job) v.elementAt(i);
		}
		return jobs;
	}

	/**
	 * @return Array of changes that are fixed by this job.
	 */
	public Change[] getChanges() {
		if(null == changes) {
			changes = getChangeFixes(getEnv(), getName(), null);
		}
		return changes;
	}

	/**
	 * Returns an array of changes that are fixed by the named job, limited to
	 * the list of files if specified.
	 * 
	 * @param env
	 *            Perforce environment to use.
	 * @param jobname
	 *            Named job to get fixes for.
	 * @param files
	 *            array of files (including wildcards) used to limit to lookup.
	 * @return array of changes fixed by the named job.
	 */
	public static Change[] getChangeFixes(Env env, String jobname, String[] files) {
		Vector[] fixes = getFixes(env, jobname, null, files);
		Vector vc = fixes[0];
		Change[] changes = new Change[vc.size()];
		for(int i = 0; i < vc.size(); i++) {
			changes[i] = (Change) vc.elementAt(i);
		}
		return changes;
	}

	/**
	 * Returns an array of jobs that fix the specified change, limited to the
	 * list of files if specified.
	 * 
	 * @param env
	 *            Perforce environment to use.
	 * @param change
	 *            Change number (as a <code>String</code>) to lookup jobs
	 *            for.
	 * @param files
	 *            array of files (including wildcards) used to limit to lookup.
	 * @return array of jobs that fix the specified change.
	 */
	public static Job[] getJobFixes(Env env, String change, String[] files) {
		Vector[] fixes = getFixes(env, null, change, files);
		Vector vj = fixes[1];
		Job[] jobs = new Job[vj.size()];
		for(int i = 0; i < vj.size(); i++) {
			jobs[i] = (Job) vj.elementAt(i);
		}
		return jobs;
	}

	/**
	 * Returns an array of two <code>Vector</code>s. The first
	 * <code>Vector</code> in the array is filled with the changes fixed. The
	 * second <code>Vector</code> contains the jobs that fix those changes.
	 * 
	 * @param env
	 *            Perforce environment to use.
	 * @param jobname
	 *            Named job to get fixes for.
	 * @param change
	 *            Change number (as a <code>String</code>) to lookup jobs
	 *            for.
	 * @param files
	 *            array of files (including wildcards) used to limit to lookup.
	 * @return an array of two <code>Vector</code>s that contains changes and
	 *         jobs fixed.
	 */
	private static Vector[] getFixes(Env env, String jobname, String change, String[] files) {
		int args = 2, pos = 0;
		if(null != jobname) {
			args += 2;
			jobname = jobname.trim();
		}
		if(null != change) {
			args += 2;
			change = change.trim();
		}
		if(null != files)
			args += files.length;
		String[] cmd = new String[args];
		cmd[pos++] = "p4";
		cmd[pos++] = "fixes";
		if(null != jobname) {
			cmd[pos++] = "-j";
			cmd[pos++] = jobname;
		}
		if(null != change) {
			cmd[pos++] = "-c";
			cmd[pos++] = change;
		}
		if(null != files) {
			for(int i = 0; i < files.length; i++) {
				cmd[pos++] = files[i];
			}
		}
		Vector vc = new Vector();
		Vector vj = new Vector();
		Change c = null;
		Job jb = null;
		StringTokenizer st;
		String l, jbname, number, user, tmpdesc = "", modtime, state;

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				st = new StringTokenizer(l);
				jbname = st.nextToken();
				jb = new Job(env, jbname);
				vj.addElement(jb);
				if(!st.nextToken().equals("fixed"))
					continue;
				if(!st.nextToken().equals("by"))
					continue;
				if(!st.nextToken().equals("change"))
					continue;
				c = new Change(st.nextToken());
				c.setEnv(env);
				if(!st.nextToken().equals("on"))
					continue;
				c.setModtimeString(st.nextToken());
				if(!st.nextToken().equals("by"))
					continue;
				c.setClientName(st.nextToken());
				if(null != c) {
					vc.addElement(c);
					c = null;
				}
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		Vector[] fixes = new Vector[2];
		fixes[0] = vc;
		fixes[1] = vj;
		return fixes;
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<job name=\"");
		sb.append(getName());
		sb.append("\" user=\"");
		sb.append(getUser());
		sb.append("\" status=\"");
		sb.append(getStatusName());
		sb.append("\" modtime=\"");
		sb.append(getModtimeString());
		sb.append("\"><description>");
		sb.append(getDescription());
		sb.append("<description>");

		Enumeration en = fields.keys();
		String key;
		while(en.hasMoreElements()) {
			key = (String) en.nextElement();
			sb.append("<field name=\"");
			sb.append(key);
			sb.append("\" value=\"");
			sb.append((String) fields.get(key));
			sb.append("\"/>");
		}
		sb.append("</job>");
		return sb.toString();
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] args) {
		String propfile = "/etc/p4.conf";
		Env environ = null;
		/*
		 * Debug.setDebugLevel(Debug.VERBOSE);
		 * Debug.setLogLevel(Debug.LOG_SPLIT);
		 */
		if(0 < args.length)
			propfile = args[0];
		try {
			environ = new Env(propfile);
		} catch(PerforceException ex) {
			System.out.println("Could not load properties from " + propfile + ": " + ex);
			System.exit(-1);
		}
		System.out.println(environ);
		Job[] jobs = getJobs(environ);
		for(int i = 0; i < jobs.length; i++) {
			System.out.println(jobs[i].getName() + " [" + jobs[i].getUser() + "]:\n\n" + jobs[i].getDescription());
		}

		System.out.println("\n---------\n");
		Job j = getJob(environ, "job000002");
		System.out.println(j.getName() + " [" + j.getUser() + "]:\n\n" + j.getDescription());

		System.out.println("\n---------\n");
		System.out.println("Job " + j.getName() + " fixes:");
		Change[] chngs = j.getChanges();
		for(int x = 0; x < chngs.length; x++) {
			System.out.println("\t change #" + chngs[x].getNumber());
		}

		if(0 < chngs.length) {
			System.out.println("\n---------\n");
			System.out.println("Change " + chngs[0].getNumber() + " is fixed by:");
			Job[] jfixes = getJobFixes(environ, String.valueOf(chngs[0].getNumber()), null);
			for(int x = 0; x < jfixes.length; x++) {
				System.out.println("\t job " + jfixes[x].getName());
			}
		}

		Utils.cleanUp();
	}

}
