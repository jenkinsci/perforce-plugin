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

package com.tek42.perforce;

import hudson.plugins.perforce.PerforcePasswordEncryptor;

import java.util.HashMap;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.perforce.api.Env;
import com.tek42.perforce.parse.*;
import com.tek42.perforce.process.DefaultExecutorFactory;
import com.tek42.perforce.process.Executor;
import com.tek42.perforce.process.ExecutorFactory;

/**
 * Represents the root object from which to interact with a Perforce server.
 * <p>
 * As an example of usage:<br>
 * 
 * <pre>
 *  
 * // Setup
 * Depot depot = new Depot();
 * depot.setPort(&quot;perforce.com:1666&quot;);
 * depot.setUser(&quot;username&quot;);
 * depot.setPassword(&quot;password&quot;);
 * depot.setWorkspace(&quot;workspace&quot;);
 * 
 * // Test
 * depot.isValid()  // returns true if so 
 * 
 * // Look at the last change for a project...
 * List&lt;Changelist&gt; changes = depot.getChanges().getChangelists(&quot;//depot/ProjectName/...&quot;, -1, 1);
 * System.out.println(Last Change is: &quot; + changes.get(0));
 * </pre>
 * 
 * @author Mike Wille
 */
public class Depot {
	private transient final Logger logger = LoggerFactory.getLogger("perforce");
	// settings contains encryt password for not exposing it in build.xml.
	// In order not to break compatibility, the method returns password will
	// decrypt it first.
	private HashMap<String, String> settings;
	private String pathSep;
	private String fileSep;
	private boolean validEnvp;
	private String p4exe;
	private long threshold;
	private String p4Ticket;

	ExecutorFactory execFactory;

	private Changes changes;
	private Workspaces workspaces;
	private Users users;
	private Labels labels;
	private Status status;
	private Groups groups;
	private Counters counters;
	private String programName;
	private String programVersion;

	public Depot() {
		this(new DefaultExecutorFactory());
	}

	public Depot(ExecutorFactory factory) {
		settings = new HashMap<String, String>();

		setPassword("");
		setSystemDrive("C:");
		setSystemRoot("C:\\WINDOWS");
		setExecutable("p4");
		setServerTimeout(10000);

		String os = System.getProperty("os.name");

		if(os == null) {
			return;
		}

		if(os.startsWith("Windows")) {
			String windir = System.getProperty("com.ms.windir");
			if(windir != null) {
				setSystemDrive(windir.substring(0, 1));
				setSystemRoot(windir);
			}
		}
		execFactory = factory;
		validEnvp = false;
	}

	/**
	 * Ensures that the latest settings are reflected in the ExecutorFactory before it is used.
	 * 
	 * @return
	 */
	public ExecutorFactory getExecFactory() {
		if (!validEnvp) {
			HashMap<String, String> decryptCloneSettings = new HashMap<String, String>(
					settings);
			PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();

			if (decryptCloneSettings.containsKey("P4PASSWD")) {
				decryptCloneSettings.put("P4PASSWD",
						encryptor.decryptString(decryptCloneSettings.get("P4PASSWD")));
			}

			execFactory.setEnv(decryptCloneSettings);
		}
		return execFactory;
	}

	public Logger getLogger() {
		return logger;
	}

	/**
	 * Obtain a legacy perforce Env object for using legacy API. Useful if you need to leverage a feature not present in
	 * com.tek42.perforce but one that does exist in com.perforce.api.
	 * 
	 * @return {@link com.perforce.api.Env} object
	 */
	public Env getPerforceEnv() {
		Env env = new Env();
		env.setClient(getClient());
		env.setExecutable(getExecutable());
		env.setPassword(getPassword());
		env.setUser(getUser());
		env.setPort(getPort());
		env.setSystemDrive(getSystemDrive());
		env.setSystemRoot(getSystemRoot());

		return env;
	}

	/**
	 * Retrieves the Changes object for interacting with this depot's changelists
	 * 
	 * @return Changes object
	 */
	public Changes getChanges() {
		if(changes == null)
			changes = new Changes(this);
		return changes;
	}

	/**
	 * Retrieves the Workspaces object for interacting with this depot's workspaces
	 * 
	 * @return Workspaces object
	 */
	public Workspaces getWorkspaces() {
		if(workspaces == null)
			workspaces = new Workspaces(this);
		return workspaces;
	}

	/**
	 * Retrieves the Users object for interacting with this depot's users.
	 * 
	 * @return Users object
	 */
	public Users getUsers() {
		if(users == null)
			users = new Users(this);
		return users;
	}

	/**
	 * Retrieves the labels object for interacting with this depot's labels.
	 * 
	 * @return Labels object
	 */
	public Labels getLabels() {
		if(labels == null)
			labels = new Labels(this);
		return labels;
	}

	/**
	 * Retrieves the Groups object for interacting with this depot's groups.
	 * 
	 * @return Groups object
	 */
	public Groups getGroups() {
		if(groups == null)
			groups = new Groups(this);
		return groups;
	}

	/**
	 * Retrieves the {@link Counters} object for interacting with this depot's counters.
	 * 
	 * @return Counters object
	 */
	public Counters getCounters() {
		if(counters == null)
			counters = new Counters(this);
		return counters;
	}

	/**
	 * Retrieves the status object for interacting with the depot's status.
	 * <p>
	 * E.g., depot.getStatus().isValid() for checking if the settings are correct.
	 * 
	 * @return Status object
	 */
	public Status getStatus() {
		if(status == null)
			status = new Status(this);
		return status;
	}

	/**
	 * Retrieves a file object to access a file in the depot.
	 * <p>
	 * E.g., depot.getFile().read() for reading a file from the depot.
	 * 
	 * @return Status object
	 */
	public File getFile(String file) {
        return new File(this,file);
    }
	
	/**
	 * Returns the output created by "p4 info"
	 * 
	 * @return The string output of p4 info
	 */
	public String info() throws Exception {
		Executor p4 = getExecFactory().newExecutor();
		String cmd[] = { getExecutable(), "info" };
		p4.exec(cmd);
		StringBuilder sb = new StringBuilder();
		String line;
		while((line = p4.getReader().readLine()) != null) {
			sb.append(line + "\n");
		}
		return sb.toString();
	}

	/**
	 * Gets a property specified by key
	 * 
	 * @param key
	 * @return
	 */
	public String getProperty(String key) {
		if (key.equals("P4PASSWD")) {
			String password = settings.get("P4PASSWD");
			PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
			return encryptor.decryptString(password);
		}
		return settings.get(key);
	}

	/**
	 * Gets a value specified by key. If the value is empty, it will return the specified default.
	 * 
	 * @param key
	 * @param def
	 * @return
	 */
	public String getProperty(String key, String def) {
		String value = getProperty(key);
		if(value == null || value.equals(""))
			return def;
		return value;
	}

	/**
	 * Sets the P4USER in the class information.
	 * 
	 * @param user
	 *            P4USER value.
	 */
	public void setUser(String user) {
		setenv("P4USER", user);
	}

	/**
	 * Returns the P4USER.
	 * 
	 * @return
	 */
	public String getUser() {
		return settings.get("P4USER");
	}

	/**
	 * Sets the P4CLIENT in the class information.
	 * 
	 * @param user
	 *            P4CLIENT value.
	 */
	public void setClient(String client) {
		setenv("P4CLIENT", client);
	}

	/**
	 * Returns the P4CLIENT.
	 * 
	 * @return
	 */
	public String getClient() {
		return settings.get("P4CLIENT");
	}

	/**
	 * Sets the P4PORT in the class information.
	 * 
	 * @param user
	 *            P4PORT value.
	 */
	public void setPort(String port) {
		setenv("P4PORT", port);
	}

	/**
	 * Returns the P4PORT.
	 * 
	 * @return
	 */
	public String getPort() {
		return settings.get("P4PORT");
	}

	/**
	 * Sets the P4PASSWD in the class information.
	 * 
	 * @param user
	 *            P4PASSWD value.
	 */
	public void setPassword(String password) {
		PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
		setenv("P4PASSWD", encryptor.encryptString(password));
	}

	/**
	 * Returns the P4PASSWORD.
	 * 
	 * @return
	 */
	public String getPassword() {
		String password = settings.get("P4PASSWD");
		PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
		return encryptor.decryptString(password);
	}

	/**
	 * Sets the PATH in the class information.
	 * 
	 * @param path
	 *            PATH value.
	 */
	public void setPath(String path) {
		setenv("PATH", path);
	}

        public String getCharset() {
            return settings.get("P4CHARSET");
        }

        public void setCharset(String charset){
            setenv("P4CHARSET", charset);
        }

        public String getCommandCharset() {
            return settings.get("P4COMMANDCHARSET");
        }

        public void setCommandCharset(String charset){
            setenv("P4COMMANDCHARSET", charset);
        }

	/**
	 * Append the path element to the existing path. If the path element given is already in the path, no change is
	 * made.
	 * 
	 * @param path
	 *            the path element to be appended.
	 */
	public void appendPath(String path) {
		String tok;
		if(null == path)
			return;
		String origPath = getProperty("PATH");
		if(null == pathSep || null == origPath) {
			setPath(path);
			return;
		}
		StringTokenizer st = new StringTokenizer(origPath, pathSep);
		StringBuffer sb = new StringBuffer();
		while(st.hasMoreTokens()) {
			tok = st.nextToken();
			if(tok.equals(path))
				return;
			sb.append(tok);
			sb.append(pathSep);
		}
		sb.append(path);
		setPath(path);
	}

	/**
	 * Returns the path
	 * 
	 * @return
	 */
	public String getPath() {
		return settings.get("PATH");
	}

	/**
	 * Sets the SystemDrive in the class information. This is only meaningful under Windows.
	 * 
	 * @param user
	 *            SystemDrive value.
	 */
	public void setSystemDrive(String drive) {
		setenv("SystemDrive", drive);
	}

	/**
	 * Returns the system drive
	 * 
	 * @return
	 */
	public String getSystemDrive() {
		return settings.get("SystemDrive");
	}

	/**
	 * Sets the SystemRoot in the class information. This is only meaningful under Windows.
	 * 
	 * @param user
	 *            SystemRoot value.
	 */
	public void setSystemRoot(String root) {
		setenv("SystemRoot", root);
	}

	/**
	 * Returns the system root.
	 * 
	 * @return
	 */
	public String getSystemRoot() {
		return settings.get("SystemRoot");
	}

	/**
	 * Sets up the path to reach the p4 executable. The full path passed in must contain the executable or at least end
	 * in the system's file separator character. This gotten from the file.separator property. For example:
	 * 
	 * <pre>
	 * p4.executable=/usr/bin/p4   # This will work
	 * p4.executable=/usr/bin/     # This will work
	 * <font color=Red>p4.executable=/usr/bin      # This won't work</font>
	 * </pre>
	 * 
	 * @param exe
	 *            Full path to the p4 executable.
	 */
	public void setExecutable(String exe) {
		int pos;
		if(null == exe)
			return;
		p4exe = exe;
	}

	/**
	 * Returns the path to the executable.
	 * 
	 * @return
	 */
	public String getExecutable() {
		return p4exe;
	}

	/**
	 * Set the server timeout threshold.
	 * 
	 * @param threshold
	 */
	public void setServerTimeout(long threshold) {
		this.threshold = threshold;
	}

	/**
	 * Return the server timeout threshold.
	 * 
	 * @return
	 */
	public long getServerTimeout() {
		return threshold;
	}

	/**
	 * Returns the ticket value for this depot's user.
	 * 
	 * @return the p4Ticket
	 */
	public String getP4Ticket() {
		return p4Ticket;
	}

	/**
	 * If using tickets, set the value of the ticket for this depot's user. Example value would be:
	 * 875477B92937E4AF7B20C5234C8905E2
	 * 
	 * @param ticket
	 *            the p4Ticket to set
	 */
	public void setP4Ticket(String ticket) {
		p4Ticket = ticket;
	}

	private void setenv(String key, String newValue) {
	    if (newValue != null) {
	        newValue = newValue.trim();
	        if (newValue.length() == 0)
	            newValue = null;
	    }

	    String currentValue = settings.get(key);
	    if (safeEquals(newValue, currentValue))
	        return;

	    if (newValue == null)
	        settings.remove(key);
	    else
	        settings.put(key, newValue);
	    validEnvp = false;
	    return;
	}

    public static boolean safeEquals(String newValue, String currentValue) {
        if (newValue == null)
            return currentValue == null;
        else
            return newValue.equals(currentValue);
    }

	public String getProgramName() {
		return programName;
	}

	public void setProgramName(String prog) {
		this.programName = prog;
	}
	
	public String getProgramVersion() {
		return programVersion;
	}

	public void setProgramVersion(String version) {
		this.programVersion = version;
	}
	
}
