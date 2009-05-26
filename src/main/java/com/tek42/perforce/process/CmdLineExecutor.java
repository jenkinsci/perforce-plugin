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

package com.tek42.perforce.process;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tek42.perforce.PerforceException;

/**
 * Executes on the command line. This is not thread safe.
 * 
 * @author Mike Wille
 */
public class CmdLineExecutor implements Executor {
	ProcessBuilder builder;
	Process currentProcess;
	List<String> args;
	BufferedWriter writer;
	BufferedReader reader;
	private final Logger logger = LoggerFactory.getLogger("perforce");

	/**
	 * Requires a map of environment variables (P4USER, P4CLIENT, P4PORT, etc)
	 * 
	 * @param environment
	 */
	public CmdLineExecutor(Map<String, String> environment) {
		args = new ArrayList<String>();
		builder = new ProcessBuilder(args);
		Map<String, String> env = builder.environment();
		for(Map.Entry<String, String> entry : environment.entrySet()) {
			// if(key.equals("P4PASSWD"))
			// continue;
			// logger.warn("Settin env: " + key + " = " + environment.get(key));
			env.put(entry.getKey(), entry.getValue());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.process.P4Executor#exec(java.lang.String[])
	 */
	public void exec(String[] args) throws PerforceException {
		this.args.clear();
		StringBuilder debug = new StringBuilder();
		for(String arg : args) {
			debug.append(arg + " ");
			this.args.add(arg);
		}
		logger.info("Executing: " + debug);
		builder.redirectErrorStream(true);
		try {
			currentProcess = builder.start();
			reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
			writer = new BufferedWriter(new OutputStreamWriter(currentProcess.getOutputStream()));

		} catch(IOException e) {
			throw new PerforceException("Failed to open connection to: " + args[0], e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.process.P4Executor#getReader()
	 */
	public BufferedReader getReader() {
		return reader;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.process.P4Executor#getWriter()
	 */
	public BufferedWriter getWriter() {
		return writer;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.process.P4Executor#close()
	 */
	public void close() {
		try {
			if(reader != null) {
				reader.close();
			}
			reader = null;
		} catch(IOException e) {
		}

		try {
			if(writer != null) {
				writer.close();
			}
			writer = null;
		} catch(IOException e) {
		}
	}

	/**
	 * Useful for things like process.waitFor().
	 * 
	 * @return
	 */
	public Process getProcess() {
		return currentProcess;
	}

}
