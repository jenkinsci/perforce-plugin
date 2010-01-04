package com.perforce.api;

import java.io.*;
import java.util.*;
import java.text.DateFormat;

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
 * Handles the execution of all perforce commands. This class can be used
 * directly, but the preferred use of this API is through the
 * {@link com.perforce.api.SourceControlObject SourceControlObject} subclasses.
 * <p>
 * <b>Example Usage:</b>
 * 
 * <pre>
 * String l;
 * Env env = new Env();
 * String[] cmd = { &quot;p4&quot;, &quot;branches&quot; };
 * try {
 * 	P4Process p = new P4Process(env);
 * 	p.exec(cmd);
 * 	while(null != (l = p.readLine())) {
 * 		// Parse the output.
 * 	}
 * 	p.close();
 * } catch(Exception ex) {
 * 	throw new PerforceException(ex.getMessage());
 * }
 * </pre>
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/01/15 $ $Revision: #3 $
 * @see Env
 * @see SourceControlObject
 * @see Thread
 */
public class P4Process {
	private static P4Process base = null;

	private P4JNI jni_proc = null;

	private boolean using_native = false;

	private Env environ = null;

	private Runtime rt = Runtime.getRuntime();

	private Process p;

	private BufferedReader in, err;

	private Writer out;

	private int exit_code = 0;

	private EventLog log;

	private String P4_ERROR = null;

	private String[] new_cmd;

	private long threshold = 10000; // The default is 10 seconds;

	private boolean raw = false;

	/**
	 * Default no-argument constructor. If the runtime has not been established,
	 * this constructor will set it up. No environment is specified, so the base
	 * environment will be used if it exists.
	 * 
	 * @see #getBase()
	 */
	public P4Process() {
		this(null);
	}

	/**
	 * Constructor that specifies the source control environment.
	 * 
	 * @param e
	 *            Source control environment to use.
	 */
	public P4Process(Env e) {
		super();
		if(null == rt) {
			rt = Runtime.getRuntime();
		}
		if(null == e) {
			if(null == base) {
				base = this;
				this.environ = new Env();
			} else {
				this.environ = new Env(base.getEnv());
			}
		} else {
			this.environ = e;
		}
		if(null != environ)
			this.threshold = environ.getServerTimeout();
	}

	/**
	 * Sets the environment to use.
	 * 
	 * @param e
	 *            Source control environment.
	 */
	public void setEnv(Env e) {
		this.environ = e;
		if(null != environ)
			this.threshold = environ.getServerTimeout();
	}

	/**
	 * Returns the environment in use by this process.
	 * 
	 * @return Source control environment.
	 */
	public Env getEnv() {
		return this.environ;
	}

	/**
	 * Returns the base process for this class. The base process is set when
	 * this class is first instantiated. The base process is used when other
	 * <code>P4Process</code> are instantiated to share settings, including
	 * the {@link com.perforce.api.Env source control environment}.
	 * 
	 * @see Env
	 * @return Source control environment.
	 */
	public static P4Process getBase() {
		if(null != base) {
			return base;
		} else {
			return new P4Process();
		}
	}

	/**
	 * Sets the base process to be used when new processes are instantiated.
	 * 
	 * @see #getBase()
	 */
	public static void setBase(P4Process b) {
		if(null != b) {
			base = b;
		}
	}
	
	public Writer getWriter() {
		return out;
	}

	/**
	 * Returns the exit code returned when the underlying process exits.
	 * 
	 * @return Typical UNIX style return code.
	 */
	public int getExitCode() {
		return exit_code;
	}

	/**
	 * In raw mode, the process will return the prefix added by the "-s" command
	 * line option. The default is false.
	 */
	public void setRawMode(boolean raw) {
		this.raw = raw;
	}

	/**
	 * Returns the status of raw mode for this process.
	 */
	public boolean getRawMode() {
		return this.raw;
	}

	/**
	 * Executes a p4 command. This uses the class environment information to
	 * execute the p4 command specified in the String array. This array contains
	 * all the command line arguments that will be specified for execution,
	 * including "p4" in the first position.
	 * 
	 * @param cmd
	 *            Array of command line arguments ("p4" must be first).
	 */
	public synchronized void exec(String[] cmd) throws IOException {
		String[] pre_cmds = new String[12];
		int i = 0;
		pre_cmds[i++] = cmd[0];
		pre_cmds[i++] = "-s";// Forces all commands to use stdout for message
								// reporting, no longer read stderr
		if(!getEnv().getPort().trim().equals("")) {
			pre_cmds[i++] = "-p";
			pre_cmds[i++] = getEnv().getPort();
		}
		if(!getEnv().getUser().trim().equals("")) {
			pre_cmds[i++] = "-u";
			pre_cmds[i++] = getEnv().getUser();
		}
		if(!getEnv().getClient().trim().equals("")) {
			pre_cmds[i++] = "-c";
			pre_cmds[i++] = getEnv().getClient();
		}
		if(!getEnv().getPassword().trim().equals("")) {
			pre_cmds[i++] = "-P";
			pre_cmds[i++] = getEnv().getPassword();
		}
		if(cmd[1].equals("-x")) {
			pre_cmds[i++] = "-x";
			pre_cmds[i++] = cmd[2];
		}
		new_cmd = new String[(i + cmd.length) - 1];
		for(int j = 0; j < (i + cmd.length) - 1; j++) {
			if(j < i) {
				new_cmd[j] = pre_cmds[j];
			} else {
				new_cmd[j] = cmd[(j - i) + 1];
			}
		}
		Debug.verbose("P4Process.exec: ", new_cmd);
		if(P4JNI.isValid()) {
			native_exec(new_cmd);
			using_native = true;
		} else {
			pure_exec(new_cmd);
			using_native = false;
		}
	}

	/**
	 * Executes the command utilizing the P4API. This method will be used only
	 * if the supporting Java Native Interface library could be loaded.
	 */
	private synchronized void native_exec(String[] cmd) throws IOException {
		jni_proc = new P4JNI();
		// P4JNI tmp = new P4JNI();
		jni_proc.runCommand(jni_proc, cmd, environ);
		in = jni_proc.getReader();
		err = in;
		out = jni_proc.getWriter();
	}

	/**
	 * Executes the command through a system 'exec'. This method will be used
	 * only if the supporting Java Native Interface library could not be loaded.
	 */
	private synchronized void pure_exec(String[] cmd) throws IOException {
		if(null != this.environ.getExecutable()) {
			cmd[0] = this.environ.getExecutable();
		}
		p = rt.exec(cmd, this.environ.getEnvp());
		InputStream is = p.getInputStream();
		Debug.verbose("P4Process.exec().is: " + is);
		InputStreamReader isr = new InputStreamReader(is);
		Debug.verbose("P4Process.exec().isr: " + isr);
		in = new BufferedReader(isr);
		InputStream es = p.getErrorStream();
		Debug.verbose("P4Process.exec().es: " + es);
		InputStreamReader esr = new InputStreamReader(es);
		Debug.verbose("P4Process.exec().esr: " + esr);
		err = new BufferedReader(esr);

		OutputStream os = p.getOutputStream();
		Debug.verbose("P4Process.exec().os: " + os);
		OutputStreamWriter osw = new OutputStreamWriter(os);
		Debug.verbose("P4Process.exec().osw: " + osw);
		out = new FilterWriter(new BufferedWriter(osw)) {
			public void write(String str) throws IOException {
				super.write(str);
				System.out.print("P4DebugOutput: " + str);
			}
			
		};
	}

	/**
	 * Sets the event log. Any events that should be logged will be logged
	 * through the EventLog specified here.
	 * 
	 * @param log
	 *            Log for all events.
	 */
	public synchronized void setEventLog(EventLog log) {
		this.log = log;
	}

	/**
	 * Logs the event message to the output stream.
	 * 
	 * @param out
	 *            Stream to which the message is logged.
	 * @param event
	 *            Message to be logged.
	 */
	private void log(PrintStream out, String event) {
		if(null == log) {
			out.println(event);
			out.flush();
		} else {
			log.log(event);
		}
	}

	/**
	 * Writes <code>line</code> to the standard input of the process.
	 * 
	 * @param line
	 *            Line to be written.
	 */
	public synchronized void print(String line) throws IOException {
		out.write(line);
	}

	/**
	 * Writes <code>line</code> to the standard input of the process. A
	 * newline is appended to the output.
	 * 
	 * @param line
	 *            Line to be written.
	 */
	public synchronized void println(String line) throws IOException {
		out.write(line + "\n");
	}

	/**
	 * Flushes the output stream to the process.
	 */
	public synchronized void flush() throws IOException {
		out.flush();
	}

	/**
	 * Flushes and closes the output stream to the process.
	 */
	public synchronized void outClose() throws IOException {
		out.flush();
		out.close();
	}

	/**
	 * Returns the next line from the process, or null if the command has
	 * completed its execution.
	 */
	public synchronized String readLine() {
		if(using_native && null != jni_proc && jni_proc.isPiped()) {
			return native_readLine();
		} else {
			return pure_readLine();
		}
	}

	/**
	 * Reads the next line from the process. This method will be used only if
	 * the supporting Java Native Interface library could be loaded.
	 */
	private synchronized String native_readLine() {
		try {
			return in.readLine();
		} catch(IOException ex) {
			return null;
		}
	}

	/**
	 * Reads the next line from the process. This method will be used only if
	 * the supporting Java Native Interface library could not be loaded.
	 */
	private synchronized String pure_readLine() {
		String line;
		long current, timeout = ((new Date()).getTime()) + threshold;

		if(null == p || null == in || null == err)
			return null;
		// Debug.verbose("P4Process.readLine()");
		try {
			for(;;) {
				if(null == p || null == in || null == err) {
					Debug.error("P4Process.readLine(): Something went null");
					return null;
				}

				current = (new Date()).getTime();
				if(current >= timeout) {
					Debug.error("P4Process.readLine(): Timeout");
					// If this was generating a new object from stdin, return an
					// empty string. Otherwise, return null.
					for(int i = 0; i < new_cmd.length; i++) {
						if(new_cmd[i].equals("-i"))
							return "";
					}
					return null;
				}

				// Debug.verbose("P4Process.readLine().in: "+in);
				try {
					/**
					 * If there's something coming in from stdin, return it. We
					 * assume that the p4 command was called with -s which sends
					 * all messages to standard out pre-pended with a string
					 * that indicates what kind of messsage it is error warning
					 * text info exit
					 */
					// Some errors still come in on Standard error
					while(err.ready()) {
						line = err.readLine();
						if(null != line) {
							addP4Error(line + "\n");
						}
					}

					if(in.ready()) {
						line = in.readLine();
						Debug.verbose("From P4:" + line);
						if(line.startsWith("error")) {
							if(!line.trim().equals("") && (-1 == line.indexOf("up-to-date"))
									&& (-1 == line.indexOf("no file(s) to resolve"))) {
								addP4Error(line);
							}
						} else if(line.startsWith("warning")) {
						} else if(line.startsWith("text")) {
						} else if(line.startsWith("info")) {
						} else if(line.startsWith("exit")) {
							int exit_code = new Integer(line.substring(line.indexOf(" ") + 1, line.length()))
									.intValue();
							if(0 == exit_code) {
								Debug.verbose("P4 Exec Complete.");
							} else {
								Debug.error("P4 exited with an Error!");
							}
							return null;
						}
						if(!raw)
							line = line.substring(line.indexOf(":") + 1).trim();
						Debug.verbose("P4Process.readLine(): " + line);
						return line;
					}
				} catch(NullPointerException ne) {
				}
				// If there's nothing on stdin or stderr, check to see if the
				// process has exited. If it has, return null.
				try {
					exit_code = p.exitValue();
					return null;
				} catch(IllegalThreadStateException ie) {
					Debug.verbose("P4Process: Thread is not done yet.");
				}
				// Sleep for a second, so this thread can't become a CPU hog.
				try {
					Debug.verbose("P4Process: Sleeping...");
					Thread.sleep(100); // Sleep for 1/10th of a second.
				} catch(InterruptedException ie) {
				}
			}
		} catch(IOException ex) {
			return null;
		}
	}

	/**
	 * Waits for the process to exit and closes out the process. This method
	 * should be called after the {@link #exec(java.lang.String[]) exec} method
	 * in order to close things down properly.
	 * 
	 * @param out
	 *            The stream to which any errors should be sent.
	 * @return The exit value of the underlying process.
	 */
	public synchronized int close(PrintStream out) throws IOException {
		if(using_native && null != jni_proc && jni_proc.isPiped()) {
			native_close(out);
		} else {
			pure_close(out);
		}
		/*
		 * if (0 != exit_code) { throw new IOException("P4Process ERROR: p4 sync
		 * exited with error ("+ exit_code+")"); }
		 */
		if(null != P4_ERROR) {
			throw new IOException(P4_ERROR);
		}
		return exit_code;
	}

	/**
	 * Closes down connections to the underlying process. This method will be
	 * used only if the supporting Java Native Interface library could be
	 * loaded.
	 */
	private synchronized void native_close(PrintStream out) {
		try {
			in.close();
			out.flush();
			out.close();
		} catch(IOException ioe) {
		}
	}

	/**
	 * Closes down connections to the underlying process. This method will be
	 * used only if the supporting Java Native Interface library could not be
	 * loaded.
	 */
	private synchronized void pure_close(PrintStream out) {
		/*
		 * Try to close this process for at least 30 seconds.
		 */
		for(int i = 0; i < 30; i++) {
			try {
				in.close();
				err.close();
				out.flush();
				out.close();
			} catch(IOException ioe) {
			}
			try {
				exit_code = p.waitFor();
				p.destroy();
				break;
			} catch(InterruptedException ie) {
			}
			try {
				Thread.sleep(1000);
			} catch(InterruptedException ie) {
			}
		}
	}

	/**
	 * Waits for the underlying process to exit and closes it down. This method
	 * should be called after the {@link #exec(java.lang.String[]) exec} method
	 * in order to close things out properly. Errors are sent to System.err.
	 * 
	 * @see System
	 * @return The exit value of the underlying process.
	 */
	public int close() throws IOException {
		return close(System.err);
	}

	/** Set the server timeout threshold. */
	public void setServerTimeout(long threshold) {
		this.threshold = threshold;
	}

	/** Return the server timeout threshold. */
	public long getServerTimeout() {
		return threshold;
	}

	public String toString() {
		return this.environ.toString();
	}

	private void addP4Error(String message) {
		if(null == P4_ERROR) {
			P4_ERROR = message;
		} else {
			P4_ERROR += message;
		}
	}
}
