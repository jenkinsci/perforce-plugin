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
 * Representation of a source control client. This class can be used to retrieve
 * and hold p4 client information. It's class methods can be used to list <a
 * href="#getClients()">all loaded clients</a> or list those <a
 * href="#lookupClient(java.lang.String)">beginning with a particular prefix</a>.
 * Before these class methods are called, the class method <a
 * href="#loadClients()">loadClients()</a> must be called to ensure the client
 * list is up to date.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #4 $
 */
public final class Client extends Mapping {
	private String root = "";

	private String options = "";

	private final static int DECAY_TIMEOUT = 86400000;

	private static HashDecay clients = null;

	private static long load_time = 0;

	/**
	 * Default no-argument constructor.
	 */
	public Client() {
		super();
		getCache();
	}

	/**
	 * Constructor that is passed the client name.
	 */
	public Client(String name) {
		this((Env) null, name);
	}

	/**
	 * Constructor that is passed the client name.
	 */
	public Client(Env env, String name) {
		this();
		setEnv(env);
		setName(name);
	}

	private synchronized static HashDecay setCache() {
		if(null == clients) {
			clients = new HashDecay(DECAY_TIMEOUT);
			clients.start();
		}
		if(clients.isEmpty())
			load_time = 0;

		return clients;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Sets the options for the Client.
	 * 
	 * @param options
	 *            Options of the client.
	 */
	public void setOptions(String options) {
		this.options = options;
	}

	/** Returns the Client options. */
	public String getOptions() {
		return options;
	}

	/**
	 * Sets the root for the Client.
	 * 
	 * @param root
	 *            Root of the client.
	 */
	public void setRoot(String root) {
		this.root = root;
	}

	/** Returns the Client root. */
	public String getRoot() {
		return root;
	}

	/**
	 * Returns a Client with the specified name, or null if not found. It is
	 * important to keep in mind that the Client returned may not hold valid
	 * information, other than its name. To ensure the Client has valid
	 * information, the sync() method must be called.
	 * 
	 * @param name
	 *            Name of the client to find.
	 */
	public static synchronized Client getClient(Env env, String name) {
		Client c;
		if(null == name || name.trim().equals(""))
			return null;
		if(null == (c = (Client) setCache().get(name)))
			c = new Client(name);
		if(null != env)
			c.setEnv(env);
		c.sync();
		synchronized(clients) {
			clients.put(name, c);
		}
		return c;
	}

	/**
	 * Returns a Client with the specified name, or null if not found.
	 * 
	 * @see #getClient(Env, String)
	 */
	public static synchronized Client getClient(String name) {
		return getClient(null, name);
	}

	/**
	 * Returns a list of clients that begin with the specified prefix.
	 * 
	 * @param env
	 *            P4 environment to use.
	 * @param prefix
	 *            Prefix for all clients to be returned.
	 * @return List of clients matching the prefix.
	 */
	public static Enumeration lookupClient(Env env, String prefix) {
		loadClients(env);
		return lookupMappings(clients, prefix);
	}

	/**
	 * Returns a list of clients that begin with the specified prefix.
	 * 
	 * @param prefix
	 *            Prefix for all clients to be returned
	 * @return List of clients matching the prefix.
	 */
	public static Enumeration lookupClient(String prefix) {
		loadClients(null);
		return lookupMappings(clients, prefix);
	}

	/**
	 * Returns a list of all the clients currently loaded.
	 * 
	 * @return List of all clients currently loaded.
	 * @deprecated
	 */
	public static Enumeration getClients() {
		loadClients(null);
		return clients.keys();
	}

	/**
	 * @param env
	 *            Environment to use when working with P4
	 * @return Enumeration of all clients currently loaded.
	 */
	public static Enumeration getClients(Env env) {
		return Utils.getEnumeration(getClientIterator(env));
	}

	/**
	 * @param env
	 *            Environment to use when working with P4
	 * @return Iterator for all clients currently loaded.
	 */
	public static Iterator getClientIterator(Env env) {
		loadClients(env);
		Enumeration en = clients.elements();
		TreeSet ts = new TreeSet();
		while(en.hasMoreElements()) {
			ts.add(en.nextElement());
		}
		return ts.iterator();
	}

	/**
	 * Loads a list of all the clients into an internal class Hashtable. This
	 * method will only be called by the class itself if the Hashtable is empty.
	 * Users should call this method if they believe the p4 client information
	 * needs to be brought up to date.
	 * 
	 * @see java.util.Hashtable
	 */
	public static void loadClients() {
		loadClients(null);
	}

	/**
	 * Loads a list of all the clients into an internal class Hashtable.
	 * 
	 * @see #loadClients()
	 * @param env
	 *            Source conrol environment to use.
	 */
	public static void loadClients(Env env) {
		String l, name;
		Client c;
		String[] cmd = { "p4", "clients" };
		StringTokenizer st;

		setCache();
		synchronized(clients) {
			if((clients.getDelay() * 0.5) > ((new Date()).getTime() - load_time))
				return;
			try {
				P4Process p = new P4Process(env);
				p.exec(cmd);
				while(null != (l = p.readLine())) {
					if(!l.startsWith("Client")) {
						continue;
					}
					try {
						st = new StringTokenizer(l.trim());
						if(6 > st.countTokens()) {
							continue;
						}
						st.nextToken();
						name = st.nextToken();
						if(null == (c = (Client) clients.get(name))) {
							c = new Client(name);
							c.setEnv(env);
						} else {
							c.refreshUpdateTime();
							continue;
						}
						st.nextToken(); // Skip modtime for now.
						st.nextToken(); // Skip 'root' text.
						c.setRoot(st.nextToken());
						if(null != (st.nextToken("'"))) {
							c.setDescription(st.nextToken());
						} else {
							c.setDescription("");
						}
						clients.put(c.getName(), c);
					} catch(NoSuchElementException ex) {
						// We'll just skip this one.
					}
				}
				load_time = (new Date()).getTime();
			} catch(Exception ex) {
				ex.printStackTrace(System.out);
				System.out.flush();
			}
		}
	}

	/**
	 * Stores the client information back into p4, creating the client if it
	 * didn't already exist.
	 * 
	 * @deprecated Use {@link #commit() commit()} instead.
	 */
	public void store() throws CommitException {
		this.commit();
	}

	public void commit() throws CommitException {
		String[] cmd = { "p4", "client", "-i" };
		String l;
		int cnt = 0;
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			p.println("Client: " + getName());
			p.println("Owner: " + getOwner());
			p.println("Root: " + getRoot());
			p.println("Description: " + getDescription());
			p.println("View:");
			p.println(getView());
			p.flush();
			p.outClose();
			while(null != (l = p.readLine())) {
				if(0 == cnt++)
					continue;
			}
			p.close();
		} catch(Exception ex) {
			throw new CommitException(ex.getMessage());
		}
	}

	public void sync() {
		sync(getName());
	}

	/**
	 * Synchronizes the Client with the latest information from P4. This method
	 * forces the Client to contain the latest, correct information if it didn't
	 * already.
	 * 
	 * @param name
	 *            Name of the Client to synchronize.
	 */
	public void sync(String name) {
		if(!outOfSync(60000))
			return;
		String description = "";
		String l;
		String[] cmd = { "p4", "client", "-o", "name" };
		cmd[3] = name;
		setName(name);

		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				if(l.startsWith("Client:")) {
					setName(l.substring(8).trim());
				} else if(l.startsWith("Owner:")) {
					setOwner(l.substring(7).trim());
				} else if(l.startsWith("Root:")) {
					setRoot(l.substring(6).trim());
				} else if(l.startsWith("Options:")) {
					setOptions(l.substring(9).trim());
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
						addView(l);
					}
				}
			}
			p.close();
			inSync();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		refreshUpdateTime();
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<client name=\"");
		sb.append(getName());
		sb.append("\" owner=\"");
		sb.append(getOwner());
		sb.append("\" root=\"");
		sb.append(getRoot());
		sb.append("\" options=\"");
		sb.append(getOptions());
		sb.append("\">");
		sb.append(super.toXML());
		sb.append("</client>");
		return sb.toString();
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] argv) {
		if(argv.length < 2) {
			System.err.println("Usage: p4.Client <port> <user> [<password>]");
			System.exit(-1);
		}
		System.out.println("Clients on " + argv[0] + ":");
		Env environ = new Env();
		environ.setPort(argv[0]);
		environ.setUser(argv[1]);
		if(3 == argv.length) {
			environ.setPassword(argv[2]);
		}
		Enumeration en = getClients(environ);
		Client c;
		while(en.hasMoreElements()) {
			c = (Client) en.nextElement();
			System.out.println(c);
		}
		System.exit(0);
	}
}
