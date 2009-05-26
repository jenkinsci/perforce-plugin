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
 * Representation of a source control environment. This information is typically
 * passed to a <code>P4Process</code> instance by the
 * <code>SourceControlObject</code> instances. It can also be set in the
 * {@link P4Process#getBase() base} P4Process instance. This will cause it to be
 * used as the default environment for all command execution.
 * <P>
 * Values for the environment can be easily loaded from a
 * {@link java.util.Properties Properties} file. This makes configuration of the
 * environment much simpler.
 * 
 * @see java.util.Properties
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/05/16 $ $Revision: #5 $
 */
public class Env {
	private boolean envp_valid = false;

	private String[] envp;

	private Hashtable environ;

	private Properties props;

	private String p4_exe; // Full path to the P4 executable.

	private String sep_path = null;

	private String sep_file = null;

	private long threshold = 10000;

	/** Default, no-argument constructor. */
	public Env() {
		super();
		environ = new Hashtable();
		environ.put("P4USER", "robot");
		environ.put("P4CLIENT", "robot-client");
		environ.put("P4PORT", "localhost:1666");
		environ.put("P4PASSWD", "");
		environ.put("PATH", "C:\\Program Files\\Perforce");
		environ.put("CLASSPATH", "/usr/share/java/p4.jar");
		environ.put("SystemDrive", "C:");
		environ.put("SystemRoot", "C:\\WINNT");
		environ.put("PATHEXT", ".COM;.EXE;.BAT;.CMD");
		setFromProperties(new Properties(System.getProperties()));
	}

	/**
	 * Constructs an environment from a properties file.
	 * 
	 * @param propfile
	 *            full path to a properties file.
	 */
	public Env(String propfile) throws PerforceException {
		this();

		setFromProperties(propfile);
	}

	/**
	 * Constructor that uses another environment as its basis. This is useful
	 * for cloning environments and then changing a few attributes.
	 * 
	 * @param base
	 *            Environment to be copied into the new environment.
	 */
	public Env(Env base) {
		this();
		this.environ = (Hashtable) base.environ.clone();
		this.props = (Properties) base.props.clone();
		this.p4_exe = base.getExecutable();
	}

	/**
	 * Constructor that uses a set of <code>Properties</code> to set up the
	 * environment.
	 * 
	 * @see #setFromProperties(Properties)
	 * @param props
	 *            Used to construct the environment.
	 */
	public Env(Properties props) {
		this();
		setFromProperties(props);
	}

	/**
	 * Allows the user to set any environment variable. If the variable name
	 * starts with 'P4', the value can not be set to null. It will instead be
	 * set to the empty string. For all other variable, supplying a null value
	 * will remove that variable form the environment.
	 * 
	 * @param name
	 *            environment variable name
	 * @param value
	 *            environment variable value
	 */
	public void setenv(String name, String value) {
		if(null == name)
			return;
		synchronized(environ) {
			if(null == value) {
				if(name.startsWith("P4")) {
					environ.put(name, "");
				} else {
					environ.remove(name);
				}
			} else {
				environ.put(name, value);
			}
		}
		envp_valid = false;
	}

	/**
	 * Returns the value for the named environment variable.
	 * 
	 * @param name
	 *            environment variable name
	 */
	public String getenv(String name) {
		synchronized(environ) {
			return (String) environ.get(name);
		}
	}

	/**
	 * Returns the environment in a <code>String</code> array.
	 */
	public String[] getEnvp() {
		String var;
		if(!envp_valid) {
			synchronized(environ) {
				envp = new String[environ.size()];
				Enumeration en = environ.keys();
				int i = 0;
				while(en.hasMoreElements()) {
					var = (String) en.nextElement();
					envp[i++] = var + "=" + environ.get(var);
				}
			}
			envp_valid = true;
		}
		return envp;
	}

	/**
	 * Checks the environment to see if it is valid. To check the validity of
	 * the environment, the user information is accessed. This ensures that the
	 * server can be contacted and that the password is set properly.
	 * <p>
	 * If the environment is valid, this method will return quietly. Otherwise,
	 * it will throw a <code>PerforceException</code> with a message regarding
	 * the failure.
	 */
	public void checkValidity() throws PerforceException {
		String[] msg = { "Connect to server failed; check $P4PORT", "Perforce password (P4PASSWD) invalid or unset.",
				"Can't create a new user - over license quota." };
		int msgndx = -1, i, cnt = 0;

		P4Process p = null;
		String l;
		String[] cmd = { "p4", "user", "-o" };

		try {
			p = new P4Process(this);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				cnt++;
				for(i = 0; i < msg.length; i++) {
					if(-1 != l.indexOf(msg[i]))
						msgndx = i;
				}
			}
			p.close();
		} catch(IOException ex) {
			if(null != p) {
				try {
					p.close();
				} catch(Exception ignex) { /* Ignored Exception */
				}
			}
		}
		if(-1 != msgndx)
			throw new PerforceException(msg[msgndx]);
		if(0 == cnt)
			throw new PerforceException("No output from p4 user -o");
	}

	/**
	 * Returns a <code>Vector</code> containing the property value list, as
	 * split up by the commas. This is used to get the values for a property in
	 * the form of:
	 * <p>
	 * some.property.key=val1,val2,val3
	 * <p>
	 * Will always return a <code>Vector</code>, even if it is empty.
	 * 
	 * @param key
	 *            the property key
	 * @param defaultValue
	 *            a default value
	 */
	public Vector getPropertyList(String key, String defaultValue) {
		return getPropertyList(key, defaultValue, ",");
	}

	/**
	 * Returns a <code>Vector</code> containing the property value list, as
	 * split up by the specified delimeter. This is used to get the values for a
	 * property in the form of:
	 * <p>
	 * some.property.key=val1,val2,val3
	 * <p>
	 * Will always return a <code>Vector</code>, even if it is empty.
	 * 
	 * @param key
	 *            the property key
	 * @param defaultValue
	 *            a default value
	 * @param delimeter
	 *            string that seperates the values
	 */
	public Vector getPropertyList(String key, String defaultValue, String delimeter) {
		Vector v = new Vector();
		String val, tok;
		StringTokenizer st;

		val = getProperty(key, defaultValue);
		st = new StringTokenizer(val, delimeter);
		while(st.hasMoreTokens()) {
			v.addElement(st.nextToken());
		}
		return v;
	}

	/**
	 * Returns a new <code>Properties</code> instance that is set using the
	 * environments properties as its default.
	 */
	public Properties getProperties() {
		return new Properties(props);
	}

	/**
	 * Searches for the property with the specified key in this property list.
	 * If the key is not found in this property list, the default property list,
	 * and its defaults, recursively, are then checked. The method returns the
	 * default value argument if the property is not found.
	 * 
	 * @param key
	 *            the property key
	 * @param defaultValue
	 *            a default value
	 * @return the value in this property list with the specified key value
	 * @see java.util.Properties
	 */
	public String getProperty(String key, String defaultValue) {
		if(null == props)
			return defaultValue;
		return props.getProperty(key, defaultValue);
	}

	/**
	 * Searches for the property with the specified key in this property list.
	 * If the key is not found in this property list, the default property list,
	 * and its defaults, recursively, are then checked. The method returns null
	 * if the property is not found.
	 * 
	 * @param key
	 *            the property key
	 * @return the value in this property list with the specified key value
	 * @see java.util.Properties
	 */
	public String getProperty(String key) {
		return getProperty(key, null);
	}

	/**
	 * Calls the hashtable method put. Provided for parallelism with the
	 * getProperty method. Enforces use of strings for property keys and values.
	 * 
	 * @param key
	 *            the key to be placed into this property list.
	 * @param value
	 *            the value corresponding to key.
	 * @see java.util.Properties#setProperty(String,String)
	 */
	public String setProperty(String key, String value) {
		String val = (String) props.setProperty(key, value);
		if(!key.startsWith("p4.")) {
			return val;
		}
		if(key.equals("p4.user")) {
			setUser(value);
		} else if(key.equals("p4.client")) {
			setClient(value);
		} else if(key.equals("p4.port")) {
			setPort(value);
		} else if(key.equals("p4.password")) {
			setPassword(value);
		} else if(key.equals("p4.executable")) {
			setExecutable(value);
		} else if(key.equals("p4.sysdrive")) {
			setSystemDrive(value);
		} else if(key.equals("p4.sysroot")) {
			setSystemRoot(value);
		} else if(key.equals("p4.threshold")) {
			try {
				setServerTimeout(Integer.valueOf(value).intValue());
			} catch(Exception ex) { /* Ignored Exception */
			}
		}
		return val;
	}

	/**
	 * Sets the environment using the specified properties file.
	 * 
	 * @see #setFromProperties(Properties)
	 * @param propfile
	 *            Path to a properties file.
	 */
	public void setFromProperties(String propfile) throws PerforceException {
		Properties props = new Properties(System.getProperties());
		if(null != propfile) {
			try {
				props.load(new BufferedInputStream(new FileInputStream(propfile)));
				System.setProperties(props);
			} catch(Exception e) {
				System.err.println("Unable to load properties.");
				e.printStackTrace(System.err);
				throw new PerforceException("Unable to load properties from " + propfile);
			}
		}
		setFromProperties(props);
	}

	/**
	 * Uses a set of <code>Properties</code> to set up the environment. The
	 * properties that are used used by this method are:
	 * 
	 * <table border="1"> <thead>
	 * <tr>
	 * <th>Property</th>
	 * <th>Value Set</th>
	 * </tr>
	 * </thead><tbody>
	 * <tr>
	 * <td>p4.user</td>
	 * <td>P4USER</td>
	 * </tr>
	 * <tr>
	 * <td>p4.client</td>
	 * <td>P4CLIENT</td>
	 * </tr>
	 * <tr>
	 * <td>p4.port</td>
	 * <td>P4PORT</td>
	 * </tr>
	 * <tr>
	 * <td>p4.password</td>
	 * <td>P4PASSWORD</td>
	 * </tr>
	 * <tr>
	 * <td>p4.executable</td>
	 * <td>Executable</td>
	 * </tr>
	 * <tr>
	 * <td>p4.sysdrive</td>
	 * <td>SystemDrive</td>
	 * </tr>
	 * <tr>
	 * <td>p4.sysroot</td>
	 * <td>SystemRoot</td>
	 * </tr>
	 * <tr>
	 * <td>p4.threshold</td>
	 * <td>Server Timeout Threshold</td>
	 * </tr>
	 * </tbody></table>
	 * 
	 * @param props
	 *            Used to construct the environment.
	 */
	public void setFromProperties(Properties props) {
		this.props = props;
		sep_path = getProperty("path.separator", ";");
		sep_file = getProperty("file.separator", "/");
		setUser(getProperty("p4.user", "robot"));
		setClient(getProperty("p4.client", "robot-client"));
		setPort(getProperty("p4.port", "localhost:1666"));
		setPassword(getProperty("p4.password", ""));
		setExecutable(getProperty("p4.executable", "P4"));
		setSystemDrive(getProperty("p4.sysdrive", "C:"));
		setSystemRoot(getProperty("p4.sysroot", "C:\\WINNT"));
		try {
			setServerTimeout(Integer.valueOf(getProperty("p4.threshold", "10000")).intValue());
		} catch(Exception ex) { /* Ignored Exception */
		}

		String os = props.getProperty("os.name");
		if(null == os) {
			return;
		}
		if(os.startsWith("Windows")) {
			String windir = props.getProperty("com.ms.windir");
			if(null != windir) {
				appendPath(windir.substring(0, 1) + "\\Program Files\\Perforce");
				setSystemDrive(windir.substring(0, 1));
				setSystemRoot(windir);
			}
		}
	}

	/**
	 * Sets the P4USER in the class information.
	 * 
	 * @param user
	 *            P4USER value.
	 */
	public void setUser(String user) {
		if(null == user)
			return;
		environ.put("P4USER", user);
		props.setProperty("p4.user", user);
		envp_valid = false;
	}

	/** Returns the P4USER. */
	public String getUser() {
		return (String) environ.get("P4USER");
	}

	/**
	 * Sets the P4CLIENT in the class information.
	 * 
	 * @param user
	 *            P4CLIENT value.
	 */
	public void setClient(String client) {
		if(null == client)
			return;
		environ.put("P4CLIENT", client);
		props.setProperty("p4.client", client);
		envp_valid = false;
	}

	/** Returns the P4CLIENT. */
	public String getClient() {
		return (String) environ.get("P4CLIENT");
	}

	/**
	 * Sets the P4PORT in the class information.
	 * 
	 * @param user
	 *            P4PORT value.
	 */
	public void setPort(String port) {
		if(null == port)
			return;
		environ.put("P4PORT", port);
		props.setProperty("p4.port", port);
		envp_valid = false;
	}

	/** Returns the P4PORT. */
	public String getPort() {
		return (String) environ.get("P4PORT");
	}

	/**
	 * Sets the P4PASSWD in the class information.
	 * 
	 * @param user
	 *            P4PASSWD value.
	 */
	public void setPassword(String password) {
		if(null == password)
			return;
		environ.put("P4PASSWD", password);
		props.setProperty("p4.password", password);
		envp_valid = false;
	}

	/** Returns the P4PASSWORD. */
	public String getPassword() {
		return (String) environ.get("P4PASSWD");
	}

	/**
	 * Sets the PATH in the class information.
	 * 
	 * @param path
	 *            PATH value.
	 */
	public void setPath(String path) {
		if(null == path)
			return;
		environ.put("PATH", path);
		props.setProperty("p4.path", path);
		envp_valid = false;
	}

	/**
	 * Append the path element to the existing path. If the path element given
	 * is already in the path, no change is made.
	 * 
	 * @param path
	 *            the path element to be appended.
	 */
	public void appendPath(String path) {
		String tok;
		if(null == path)
			return;
		String orig_path = (String) environ.get("PATH");
		if(null == sep_path || null == orig_path) {
			setPath(path);
			return;
		}
		StringTokenizer st = new StringTokenizer(orig_path, sep_path);
		StringBuffer sb = new StringBuffer();
		while(st.hasMoreTokens()) {
			tok = (String) st.nextToken();
			if(tok.equals(path))
				return;
			sb.append(tok);
			sb.append(sep_path);
		}
		sb.append(path);
		setPath(path);
	}

	/** Returns the PATH. */
	public String getPath() {
		return (String) environ.get("PATH");
	}

	/**
	 * Sets the SystemDrive in the class information. This is only meaningful
	 * under Windows.
	 * 
	 * @param user
	 *            SystemDrive value.
	 */
	public void setSystemDrive(String drive) {
		if(null == drive)
			return;
		environ.put("SystemDrive", drive);
		props.setProperty("p4.sysdrive", drive);
		envp_valid = false;
	}

	/**
	 * Sets the SystemRoot in the class information. This is only meaningful
	 * under Windows.
	 * 
	 * @param user
	 *            SystemRoot value.
	 */
	public void setSystemRoot(String root) {
		if(null == root)
			return;
		environ.put("SystemRoot", root);
		props.setProperty("p4.sysroot", root);
		envp_valid = false;
	}

	/**
	 * Sets up the path to reach the p4 executable. The full path passed in must
	 * contain the executable or at least end in the system's file separator
	 * character. This gotten from the file.separator property. For example:
	 * 
	 * <pre>
	 * p4.executable=/usr/bin/p4   # This will work
	 * p4.executable=/usr/bin/     # This will work
	 * &lt;font color=Red&gt;p4.executable=/usr/bin      # This won't work&lt;/font&gt;
	 * </pre>
	 * 
	 * @param exe
	 *            Full path to the p4 executable.
	 */
	public void setExecutable(String exe) {
		int pos;
		if(null == exe)
			return;
		p4_exe = exe;
		if(null == sep_file) {
			sep_file = System.getProperties().getProperty("file.separator", "\\");
		}
		if(-1 == (pos = exe.lastIndexOf(sep_file)))
			return;
		if(null == sep_path) {
			sep_path = System.getProperties().getProperty("path.separator", ";");
		}
		appendPath(exe.substring(0, pos));
		props.setProperty("p4.executable", p4_exe);
		envp_valid = false;
	}

	/** Returns the path to the executable. */
	public String getExecutable() {
		return p4_exe;
	}

	/** Set the server timeout threshold. */
	public void setServerTimeout(long threshold) {
		this.threshold = threshold;
		props.setProperty("p4.threshold", String.valueOf(threshold));
	}

	/** Return the server timeout threshold. */
	public long getServerTimeout() {
		return threshold;
	}

	public String toString() {
		String[] envp = getEnvp();
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < envp.length; i++) {
			sb.append(envp[i]);
			sb.append("\n");
		}
		return sb.toString();
	}

	/**
	 * Returns an XML representation of the environment.
	 */
	public String toXML() {
		StringBuffer sb = new StringBuffer("<env");
		sb.append(" user=\"");
		sb.append(getUser());
		sb.append("\" client=\"");
		sb.append(getClient());
		sb.append("\" port=\"");
		sb.append(getPort());
		sb.append("\" password=\"");
		sb.append(getPassword());
		sb.append("\" sysdrive=\"");
		sb.append(environ.get("SystemDrive"));
		sb.append("\" sysroot=\"");
		sb.append(environ.get("SystemRoot"));
		sb.append("\" threshold=\"");
		sb.append(threshold);
		sb.append("\"><executable>");
		sb.append(getExecutable());
		sb.append("</executable><path>");
		sb.append(getPath());
		sb.append("</path></env>");
		return sb.toString();
	}
}
