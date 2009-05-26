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
 * Representation of a source control user. Each instance can store one p4
 * user's information. The class methods can be used to <a
 * href="#getUser(java.lang.String)">get a particular user</a>. If that user
 * has been gotten before, their user information will not be reloaded from P4.
 * It is instead loaded from an internal HashDecay.
 * <p>
 * If the user information must be up to date, then the <a href="#sync()">sync()</a>
 * method must be called.
 * <p>
 * TBD: The current implementation does NOT handle the "reviews" information for
 * each user. Should User extend Mapping?
 * 
 * @see HashDecay
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/05/16 $ $Revision: #2 $
 */
public final class User extends SourceControlObject {
	private String id = "";

	private String email = "";

	private String fullname = "";

	private static HashDecay users = null;

	/**
	 * Default no-argument constructor.
	 */
	public User() {
		super();
		getCache();
	}

	/**
	 * Constructor that accepts the id of the user. This simply creates an
	 * instance that has the id set. No other information in the class will be
	 * present until the <a href="#sync()">sync() method is called.
	 * 
	 * @param id
	 *            Id for the user.
	 */
	public User(String id) {
		this();
		this.id = id;
	}

	private static HashDecay setCache() {
		if(null == users) {
			users = new HashDecay(600000);
			users.start();
		}
		return users;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Sets the id for this user.
	 * 
	 * @param id
	 *            Id for the user.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns an <code>Enumeration</code> of all <code>User</code> objects.
	 */
	public static synchronized Enumeration getUsers() {
		return getUsers(null);
	}

	/**
	 * Returns an <code>Enumeration</code> of all <code>User</code> objects.
	 */
	public static synchronized Enumeration getUsers(Env env) {
		String l;
		User u;
		String[] cmd = { "p4", "users" };

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				u = getUser(l);
				u.setEnv(env);
			}
			p.close();
		} catch(IOException ex) {
		}

		return setCache().elements();
	}

	/**
	 * Gets the user information for the specified user. If that user has been
	 * gotten before, their user information will not be reloaded from P4. It is
	 * instead loaded from an internal HashDecay.
	 * <p>
	 * If the user information must be up to date, then the <a
	 * href="#sync()">sync() method must be called.
	 * 
	 * @deprecated Use method with Env parameter.
	 * @param uid
	 *            User id that is requested.
	 */
	public static synchronized User getUser(String uid) {
		return getUser(null, uid);
	}

	/**
	 * Gets the user information for the specified user. If that user has been
	 * gotten before, their user information will not be reloaded from P4. It is
	 * instead loaded from an internal HashDecay.
	 * <p>
	 * If the user information must be up to date, then the <a
	 * href="#sync()">sync() method must be called.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param uid
	 *            The user id of the user information to get from p4.
	 */
	public static synchronized User getUser(Env env, String uid) {
		if(null == uid) {
			return null;
		}
		if(uid.trim().equals("")) {
			return null;
		}

		String tid;
		int i, left = 0;
		uid = uid.trim();
		if(-1 == (left = uid.indexOf("<"))) {
			tid = uid;
		} else {
			tid = uid.substring(0, left - 1).trim();
		}
		User u = (User) setCache().get(uid);
		if(null != u) {
			return u;
		} else {
			u = new User(tid);
		}
		u.setEmail(tid);
		u.setFullName(tid);
		if(-1 != left) {
			uid = uid.substring(left + 1);
			char[] ch = uid.toCharArray();
			left = 0;
			for(i = 0; i < ch.length; i++) {
				switch(ch[i]) {
				case '>':
					u.setEmail(new String(ch, left, i - left));
					break;
				case '(':
					left = i + 1;
					break;
				case ')':
					u.setFullName(new String(ch, left, i - left));
					break;
				}
			}
		}
		u.setEnv(env);
		users.put(tid, u);
		return u;
	}

	/**
	 * Returns the id for this user.
	 * 
	 * @return Id for the user.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets the e-mail address for this user.
	 * 
	 * @param email
	 *            Email address for the user.
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	/**
	 * Returns the e-mail address for this user.
	 * 
	 * @return Email address for the user.
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * Sets the full name of this user.
	 * 
	 * @param fullname
	 *            The full name for the user.
	 */
	public void setFullName(String fullname) {
		this.fullname = fullname;
	}

	/**
	 * Returns the full name of this user.
	 * 
	 * @return The full name for the user.
	 */
	public String getFullName() {
		return fullname;
	}

	/**
	 * TBD: The <code>commit</code> method is not working yet.
	 */
	public void commit() {
	}

	/**
	 * Synchronizes the user information with P4. This method must be called to
	 * ensure that this contains the latest information from p4. This form of
	 * the method can be used to change the user Id in at the same time.
	 * 
	 * @param id
	 *            The user id for this to synchronize from p4.
	 */
	public void sync(String id) {
		this.id = id;
		sync();
	}

	/**
	 * Synchronizes the user information with P4. This method must be called to
	 * ensure that this contains the latest information from p4.
	 */
	public void sync() {
		if(!outOfSync(300000))
			return;
		String l;
		String[] cmd = { "p4", "user", "-o", "id" };
		cmd[3] = id;

		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				if(l.startsWith("User:")) {
					id = l.substring(6).trim();
				} else if(l.startsWith("Email:")) {
					email = l.substring(7).trim();
				} else if(l.startsWith("FullName:")) {
					fullname = l.substring(10).trim();
				}
			}
			p.close();
			inSync();
		} catch(IOException ex) {
		}
	}

	public String toString() {
		return id;
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<user id=\"");
		sb.append(getId());
		sb.append("\" fullname=\"");
		sb.append(getFullName());
		sb.append("\" email=\"");
		sb.append(getEmail());
		sb.append("\"/>");
		return sb.toString();
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] argv) {
		User u;
		Env env = new Env();
		Debug.setDebugLevel(Debug.NOTICE);
		env.setUser("robot");
		env.setPort("perforce.ma.lycos.com:1666");
		Enumeration en = User.getUsers(env);
		while(en.hasMoreElements()) {
			u = (User) en.nextElement();
			System.out.println("USER: " + u.getId());
		}
	}
}
