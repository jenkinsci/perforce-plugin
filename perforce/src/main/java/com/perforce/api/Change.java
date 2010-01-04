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
 * Representation of a source control change. This class can be used to
 * determine information for a particular p4 change. It can be constructed using
 * the change number, but will not contain any additional change information
 * until the <a href="#sync()">sync()</a> method is called.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #10 $
 */
public final class Change extends SourceControlObject {
	private int number = -1;

	private User user = null;

	private String client_name = "";

	private String modtime_string = "";

	private String description = "";

	private int status = PENDING;

	private static HashDecay changes = null;

	/** Indicates that the Change is pending submission. */
	public final static int PENDING = 1;

	/** Indicates that the Change has been submitted. */
	public final static int SUBMITTED = 2;

	/**
	 * Default no-argument constructor.
	 */
	public Change() {
		super();
		getCache();
	}

	public Change(Env environ) {
		this();
		this.setEnv(environ);
	}

	/**
	 * Constructor that accepts the change number. This change is not populated
	 * with the correct information until the sync() method is called on it.
	 * 
	 * @param number
	 *            Change number
	 */
	public Change(int number) {
		this();
		this.number = number;
	}

	public Change(String number) {
		this();
		this.number = Integer.valueOf(number).intValue();
	}

	private static HashDecay setCache() {
		if(null == changes) {
			changes = new HashDecay(300000);
			changes.start();
		}
		return changes;
	}

	public HashDecay getCache() {
		return setCache();
	}

	public static Change getChange(String number) {
		return getChange(null, number, true);
	}

	public static Change getChange(String number, boolean force) {
		return getChange(null, number, force);
	}

	public static Change getChange(Env env, String number, boolean force) {
		return getChange(env, (Integer.valueOf(number)).intValue(), force);
	}

	public static Change getChange(int number) {
		return getChange(null, number, true);
	}

	public static Change getChange(int number, boolean force) {
		return getChange(null, number, force);
	}

	public static Change getChange(Env env, int number, boolean force) {
		Change c;
		if(null == (c = (Change) setCache().get(new Integer(number)))) {
			c = new Change(number);
			force = true;
		}
		if(null != env)
			c.setEnv(env);
		if(force)
			c.sync();
		changes.put(new Integer(number), c);
		return c;
	}

	public String getClientName() {
		return client_name;
	}

	public void setClientName(String name) {
		this.client_name = name;
	}

	public String getModtimeString() {
		return modtime_string;
	}

	public void setModtimeString(String modtime) {
		this.modtime_string = modtime;
	}

	/**
	 * Sets the change number for the Change. This invalidates all the other
	 * data for the Change.
	 * 
	 * @param number
	 *            Change number
	 */
	public void setNumber(int number) {
		this.number = number;
		user = null;
		description = "";
		status = PENDING;
	}

	/**
	 * Returns the number of this Change.
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Sets the User that owns this Change.
	 * 
	 * @param user
	 *            Owning user.
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * Returns the User that owns this Change.
	 */
	public User getUser() {
		return user;
	}

	/**
	 * Sets the description for the change.
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
	 * Returns the description for the Change. This description includes not
	 * only the textual description provided by the user, but also the list of
	 * affected files and how they were affected.
	 * 
	 * The String returned includes newline characters.
	 */
	public String getDescription() {
		return description;
	}

	public String getShortDescription() {
		return getShortDescription(false);
	}

	public String getShortDescription(boolean blurb) {
		StringBuffer sb = new StringBuffer();
		String l;
		try {
			BufferedReader b = new BufferedReader(new StringReader(getDescription()));
			while(null != (l = b.readLine())) {
				if(blurb && l.startsWith("Change"))
					continue;
				if(blurb && l.startsWith("Jobs fixed"))
					break;
				if(l.startsWith("Affected file")) {
					break;
				} else {
					sb.append(l);
					sb.append('\n');
				}
			}
		} catch(IOException ex) {
		}
		return sb.toString();
	}

	/**
	 * Returns a Vector filled with the files (including revision numbers) that
	 * were affected by this change. What was done to each file as a result of
	 * this Change is stripped off.
	 * 
	 * This method uses the value of the Change's description to determine the
	 * files that are affected.
	 * 
	 * @return <code>Vector</code> of <code>String</code>s of files
	 *         affected.
	 */
	public Vector getFiles() {
		Vector v = new Vector();
		try {
			BufferedReader b = new BufferedReader(new StringReader(getDescription()));
			String l, t;
			int pos;
			while(null != (l = b.readLine())) {
				t = l.trim();
// if (t.startsWith("... ")) {
				if(t.startsWith("//")) {
					if(-1 != (pos = t.lastIndexOf(" "))) {
						v.addElement(t.substring(0, pos).trim());
					}
				}
			}
		} catch(IOException e) {
		}
		return v;
	}

	/**
	 * Returns a Vector filled with the files (including revision numbers) that
	 * were affected by this change. What was done to each file as a result of
	 * this Change is stripped off.
	 * 
	 * This method uses the value of the Change's description to determine the
	 * files that are affected.
	 * 
	 * @return <code>Vector</code> of <code>FileEntry</code> objects of
	 *         files affected.
	 */
	public Vector getFileEntries() {
		if(PENDING == getStatus() && 0 < getNumber()) {
			return FileEntry.getOpened(getEnv(), false, false, getNumber(), null);
		}
		Vector v = new Vector();
		FileEntry fent;
		try {
			BufferedReader b = new BufferedReader(new StringReader(getDescription()));
			String l, t;
			int beg, end;
			while(null != (l = b.readLine())) {
				t = l.trim();
				if(t.startsWith("//")) {
					fent = new FileEntry();
					fent.setEnv(getEnv());
					beg = 0;
					end = 4;
					if(-1 != (end = t.indexOf('#', beg))) {
						fent.setDepotPath(t.substring(beg, end));
						beg = end + 1;
						if(-1 != (end = t.indexOf(' ', beg))) {
							fent.setHeadRev(Integer.valueOf(t.substring(beg, end).trim()).intValue());
							fent.setHeadAction(t.substring(end + 1));
						}
					} else {
						fent.setDepotPath(t);
					}
					v.addElement(fent);
				}
			}
		} catch(IOException e) {
		}
		return v;
	}

	/**
	 * Adds the given <code>FileEntry</code> to the changelist. If the
	 * changelist has not been committed to the server, that is done first.
	 * 
	 * @param fent
	 *            file entry to be added.
	 */
	public void addFile(FileEntry fent) throws PerforceException {
		if(-1 == number)
			commit();
		fent.reopen(null, this);
	}

	/**
	 * Resolves this file. If the force flag is false, and auto-resolve is
	 * attempted (p4 resolve -am). If the force flag is true, an "accept theirs"
	 * resolve is completed (p4 resolve -at).
	 * 
	 * @see FileEntry#resolve(boolean)
	 * @param force
	 *            Indicates whether the resolve should be forced.
	 */
	public String resolve(boolean force) throws PerforceException {
		StringBuffer sb = new StringBuffer();
		Enumeration en = getFileEntries().elements();

		try {
			while(en.hasMoreElements()) {
				sb.append(((FileEntry) en.nextElement()).resolve(force));
			}
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage());
		}
		return sb.toString();
	}

	/**
	 * Sets status for the Change. This can be either PENDING or SUBMITTED.
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * Returns the status for the Change. This can be either PENDING or
	 * SUBMITTED.
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Submits the change, if it is pending.
	 * 
	 * @throws SubmitException
	 *             If the submit fails.
	 */
	public String submit() throws SubmitException {
		String l;
		StringBuffer sb = new StringBuffer();
		if(PENDING != status) {
			throw new SubmitException("Change already submitted.");
		}
		String[] cmd = { "p4", "submit", "-c", String.valueOf(getNumber()) };
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				sb.append(l);
				sb.append('\n');
			}
			p.close();
		} catch(Exception ex) {
			throw new SubmitException(ex.getMessage() + "\n\n" + sb.toString());
		}
		return sb.toString();
	}

	/**
	 * Updates the change or creates a pending change.
	 * 
	 * @deprecated Use {@link #commit() commit()} instead.
	 */
	public void store() throws CommitException {
		this.commit();
	}

	public void commit() throws CommitException {
		Enumeration en;
		Vector fents = getFileEntries();
		StringBuffer sb = new StringBuffer();
		String[] cmd = { "p4", "change", "-i" };
		String l;
		int pos;
		boolean store_failed = false;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			try {
				Thread.sleep(1000);
			} catch(InterruptedException intex) { /* Ignoring Exception */
			}
			if(0 > number) {
				p.println("Change: new");
			} else {
				p.println("Change: " + getNumber());
			}
			p.println("Client: " + getClientName());
			if(null == getUser() && null != getEnv()) {
				p.println("User: " + getEnv().getUser());
			} else {
				p.println("User: " + user.getId());
			}
			p.println("Description: ");
			p.println(getDescription());
			if(null != fents && 0 < fents.size()) {
				FileEntry fent;
				p.println("Files: ");
				en = fents.elements();
				while(en.hasMoreElements()) {
					fent = (FileEntry) en.nextElement();
					p.println("\t" + fent.getDepotPath());
				}
			}
			if(Utils.isWindows()) {
				p.println("\032\n\032");
			}
			p.flush();
			p.outClose();
			Debug.notify("Change.store(): Wrote change info.");
			while(null != (l = p.readLine())) {
				Debug.notify("READ: " + l);
				if(l.startsWith("Change ") && (-1 != (pos = l.indexOf("created")))) {
					setNumber(Integer.valueOf(l.substring(7, pos - 1).trim()).intValue());
				}
				if(l.startsWith("Error"))
					store_failed = true;
				sb.append(l);
				sb.append('\n');
			}
			p.close();
			Debug.notify("Change.store(): All done reading.");
		} catch(Exception ex) {
			throw new CommitException(ex.getMessage());
		}
		if(store_failed || 0 > getNumber()) {
			throw new CommitException(sb.toString());
		}
	}

	/**
	 * Synchronizes the Change with the correct information from P4, using
	 * whatever change number has already been set in the Change. After this
	 * method is called, all the information in the Change is valid.
	 */
	public void sync() {
		sync(number);
	}

	/**
	 * Sycnhronizes the Change with the correct information from P4. After this
	 * method is called, all the information in the Change is valid.
	 * 
	 * @param number
	 *            Change number
	 */
	public void sync(int number) {
		if(SUBMITTED == status && !outOfSync(60000))
			return;
		this.number = number;
		String l, tstr;
		String[] cmd = { "p4", "describe", "-s", "number" };
		cmd[3] = String.valueOf(number);
		boolean wasFound = false;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(!wasFound && l.startsWith("Change")) {
					tstr = l.substring(l.indexOf("by") + 3).trim();
					tstr = tstr.substring(0, tstr.indexOf("@"));
					user = User.getUser(getEnv(), tstr);
					modtime_string = l.substring(l.indexOf(" on ") + 4).trim();
					if(-1 == l.indexOf("pending")) {
						status = SUBMITTED;
					}
					description = l;
					wasFound = true;
				} else {
					description += l.trim() + "\n";
				}
			}
			p.close();
			inSync();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
	}

	/**
	 * Reverts all the files associated with a pending changelist.
	 */
	public void revert() throws PerforceException {
		if(PENDING != status) {
			throw new PerforceException("Change already submitted.");
		}
		Enumeration en = getFileEntries().elements();
		try {
			while(en.hasMoreElements()) {
				((FileEntry) en.nextElement()).revert();
			}
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage());
		}
	}

	/**
	 * Delete the pending changelist. This method will revert any open files
	 * associated with the changelist and then delete it.
	 * 
	 * @return log of delete command.
	 */
	public String delete() throws PerforceException {
		this.revert();
		return this.deleteEmptyChange();
	}

	/**
	 * Deletes the Changelist if it is empty.
	 * 
	 * @deprecated Use <code>delete</code> method instead.
	 * @return String Contents of the information returned by P4 as a result of
	 *         the delete call.
	 */
	public String deleteEmptyChange() throws PerforceException {
		String l;
		StringBuffer sb = new StringBuffer();
		if(PENDING != status) {
			throw new PerforceException("Change already submitted.");
		}

		String[] cmd = { "p4", "change", "-d", String.valueOf(getNumber()) };
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				sb.append(l);
				sb.append('\n');
			}
			p.close();
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage() + "\n\n" + sb.toString());
		}
		return sb.toString();
	}

	/**
	 * Overrides the default toString() method.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer("Change: ");
		sb.append(number);
		sb.append("\nUser: ");
		sb.append(user);
		sb.append("\nDescription:\n");
		sb.append(description);
		return sb.toString();
	}

	public static Change[] getChanges(Env env, String path) throws PerforceException {
		return getChanges(env, path, 100, null, null, false, null);
	}

	public static Change[] getChanges(String path) throws PerforceException {
		return getChanges(null, path, 100, null, null, false, null);
	}

	public static Change[] getChanges(Env env, String path, int max, String start, String end, boolean use_integs,
			String ufilter) throws PerforceException {
		int cmdlen = 8;
		String[] cmd;
		String tpath = path;

		if(use_integs)
			cmdlen++;
		if(null == tpath)
			tpath = "";

		if(null != start && !start.trim().equals("")) {
			tpath += "@" + start;
			if(null != end && !end.trim().equals("")) {
				tpath += "," + end;
			}
		}

		if(tpath.trim().equals(""))
			cmdlen--;
		cmd = new String[cmdlen];
		if(!tpath.trim().equals(""))
			cmd[cmdlen - 1] = tpath;

		cmd[0] = "p4";
		cmd[1] = "changes";
		cmd[2] = "-m";
		cmd[3] = String.valueOf(max);
		cmd[4] = "-l";
		cmd[5] = "-s";
		cmd[6] = "submitted";
		if(use_integs)
			cmd[7] = "-i";
		Vector v = new Vector();
		Change[] chngs;
		StringTokenizer st;
		int num;
		String l, id, description = "";
		User user;
		Change c = null;
		String modtime, client_name;

		try {
			P4Process p = new P4Process(env);
			p.setRawMode(true);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("info: Change")) {
					l = l.substring(6).trim();
					st = new StringTokenizer(l);
					if(!st.nextToken().equals("Change"))
						continue;
					try {
						num = Integer.parseInt(st.nextToken());
					} catch(Exception ex) {
						throw new PerforceException("Could not parse change number from line: " + l);
					}
					if(!st.nextToken().equals("on"))
						continue;
					modtime = st.nextToken();
					if(!st.nextToken().equals("by"))
						continue;
					id = st.nextToken();
					int pos = id.indexOf("@");
					client_name = id.substring(pos + 1);
					id = id.substring(0, pos);
					user = User.getUser(env, id);
					if(null != c) {
						c.setDescription(description);
					}
					description = "";
					c = new Change(num);
					c.setEnv(env);
					c.setUser(user);
					c.setClientName(client_name);
					c.setModtimeString(modtime);
					if(null == ufilter || id.equals(ufilter)) {
						v.addElement(c);
					}
				} else {
					l = l.substring(5).trim();
					description += l + "\n";
				}
			}
			if(null != c) {
				c.setDescription(description);
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		chngs = new Change[v.size()];
		for(int i = 0; i < v.size(); i++) {
			chngs[i] = (Change) v.elementAt(i);
			changes.put(new Integer(chngs[i].getNumber()), chngs[i]);
		}
		return chngs;
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<change number=\"");
		sb.append(getNumber());
		sb.append("\" user=\"");
		sb.append(getUser());
		sb.append("\" client=\"");
		sb.append(getClientName());
		sb.append("\" status=\"");
		sb.append(getStatus());
		sb.append("\" modtime=\"");
		sb.append(getModtimeString());
		sb.append("\">");
		sb.append("<description>");
		sb.append(getDescription());
		sb.append("</description>");

		Vector v = getFileEntries();
		FileEntry fent = null;
		if(null != v && 0 != v.size()) {
			sb.append("<files>");
			for(int i = 0; i < v.size(); i++) {
				fent = (FileEntry) v.elementAt(i);
				sb.append("<file path=\"");
				sb.append(fent.getDepotPath());
				sb.append("\" rev=\"");
				sb.append(fent.getHeadRev());
				sb.append("\"/>");
			}
			sb.append("</files>");
		}

		sb.append("</change>");
		return sb.toString();
	}

	/**
	 * Determine the users that review this changelist. This method returns an
	 * array of Users that need to be informed.
	 */
	public User[] reviews() throws PerforceException {
		Vector users = new Vector();
		User[] usrs = new User[0];
		User chng;
		User usr;
		String uid;
		String email;
		String name, t;
		StringTokenizer st;
		String l;
		String[] cmd = { "p4", "reviews", "-c", String.valueOf(this.number) };
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.length() < 4)
					continue;
				st = new StringTokenizer(l);
				uid = st.nextToken();
				email = st.nextToken("<> \t");
				name = st.nextToken("<> ()\t");
				try {
					while(null != (t = st.nextToken("<> ()\t"))) {
						name += (" " + t);
					}
				} catch(NoSuchElementException ex) {
				}
				// System.out.print("U: "+uid+", E: "+email+", N: "+name+"\n");
				usr = new User(uid);
				usr.setEnv(getEnv());
				usr.setEmail(email);
				usr.setFullName(name);
				users.addElement(usr);
			}
			p.close();
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage());
		}
		if(0 == users.size())
			return null;
		return (User[]) users.toArray(usrs);
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] args) {
		String propfile = "/etc/p4.conf";
		Env environ = null;
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
		Change chng = new Change(environ);
		chng.setDescription("This is a test changelist.");
		try {
			chng.commit();
		} catch(CommitException e) {
			System.err.println("Unable to store new change.");
			e.printStackTrace(System.err);
			System.exit(-1);
		}
		System.out.println("New Changelist Generated: " + chng.getNumber());
		Utils.cleanUp();
	}
}
