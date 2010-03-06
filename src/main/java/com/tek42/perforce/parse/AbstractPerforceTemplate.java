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

package com.tek42.perforce.parse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FilterWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.process.Executor;
import org.slf4j.LoggerFactory;

/**
 * Provides default functionality for interacting with Perforce using the template design pattern.
 * 
 * @author Mike Wille
 */
public abstract class AbstractPerforceTemplate {
    private static final String p4errors[] = new String[] {
            "Connect to server failed; check $P4PORT",
            "Perforce password (P4PASSWD) invalid or unset.",
            "Password not allowed at this server security level, use 'p4 login'",
            "Can't create a new user - over license quota.",
            "Client '*' can only be used from host '*'",
            "Access for user '",
            "Your session has expired, please login again.",
            "You don't have permission for this operation.",
            "Password invalid.",
        };

    @SuppressWarnings("unused")
    private transient Logger logger;   // Obsolete field, present just to keep demarshaller happy
    @SuppressWarnings("unused")
    private transient String errors[];   // Obsolete field, present just to keep demarshaller happy

    private final Depot depot;
	final String maxError = "Request too large";

	public AbstractPerforceTemplate(Depot depot) {
		this.depot = depot;
	}

    public Logger getLogger()
    {
        if(depot.getLogger() != null){
            return depot.getLogger();
        } else {
            return LoggerFactory.getLogger(this.getClass());
        }
    }

    /**
	 * Parses lines of formatted text for a list of values. Tokenizes each line into columns and adds the column
	 * specified by index to the list.
	 * 
	 * @param response	The response from perforce to parse
	 * @param index		The column index to add to the list
	 * @return	A List of strings parsed from the response
	 */
	protected List<String> parseList(StringBuilder response, int index) {
		StringTokenizer lines = new StringTokenizer(response.toString(), "\n\r");
		List<String> list = new ArrayList<String>(100);
		while(lines.hasMoreElements()) {
			StringTokenizer columns = new StringTokenizer(lines.nextToken());
			for(int column = 0; column < index; column++) {
				columns.nextToken();
			}
			list.add(columns.nextToken());
		}
		return list;

	}

	/**
	 * Check to see if the perforce request resulted in a "too many results" error.  If so, special handling needs
	 * to happen.
	 *
	 * @param response The response from perforce
	 * @return	True if the limit was reached, false otherwise.
	 */
	protected boolean hitMax(StringBuilder response) {
		return response.toString().startsWith(maxError);
	}

	/**
	 * Adds any extra parameters that need to be applied to all perforce commands. For example, adding the login ticket
	 * to authenticate with.
	 * 
	 * @param cmd
	 *            String array that will be executed
	 * @return A (possibly) modified string array to be executed in place of the original.
	 */
	protected String[] getExtraParams(String cmd[]) {
		String ticket = depot.getP4Ticket();

		if(ticket != null) {
			// Insert the ticket for the password if tickets are being used...
			String newCmds[] = new String[cmd.length + 2];
			newCmds[0] = getP4Exe();
			newCmds[1] = "-P";
			newCmds[2] = ticket;
			for(int i = 3; (i - 2) < cmd.length; i++) {
				newCmds[i] = cmd[i - 2];
			}
			cmd = newCmds;
		} else {
		    cmd[0] = getP4Exe();
		}
		return cmd;
	}

	/**
	 * Handles the IO for opening a process, writing to it, flushing, closing, and then handling any errors.
	 * 
	 * @param object	The perforce object to save
	 * @param builder	The builder responsible for saving the object
	 * @throws PerforceException	If there is any errors thrown from perforce
	 */
	@SuppressWarnings("unchecked")
	protected void saveToPerforce(Object object, Builder builder) throws PerforceException {
		boolean loop = false;
		boolean attemptLogin = true;

		//StringBuilder response = new StringBuilder();
		do {
			int mesgIndex = -1;//, count = 0;
			Executor p4 = depot.getExecFactory().newExecutor();
			String debugCmd = "";
			try {
				String cmds[] = getExtraParams(builder.getSaveCmd(getP4Exe(), object));

				// for exception reporting...
				for(String cm : cmds) {
					debugCmd += cm + " ";
				}

				// back to our regularly scheduled programming...
				p4.exec(cmds);
				BufferedReader reader = p4.getReader();

				// Maintain a log of what was sent to p4 on std input
				final StringBuilder log = new StringBuilder();

				// Conditional use of std input for saving the perforce entity
				if(builder.requiresStandardInput()) {
					BufferedWriter writer = p4.getWriter();
					Writer fwriter = new FilterWriter(writer) {
						public void write(String str) throws IOException {
							log.append(str);
							out.write(str);
						}
					};
					builder.save(object, fwriter);
					fwriter.flush();
					fwriter.close();
				}

				String line;
                StringBuilder error = new StringBuilder();
                StringBuilder info = new StringBuilder();
				int exitCode = 0;

				while((line = reader.readLine()) != null) {

					// Check for authentication errors...
				    if (mesgIndex == -1)
				        mesgIndex = checkAuthnErrors(line);

				    if (mesgIndex != -1) {
				        error.append(line);

				    } else if(line.startsWith("error")) {
						if(!line.trim().equals("") && (line.indexOf("up-to-date") < 0) && (line.indexOf("no file(s) to resolve") < 0)) {
							error.append(line.substring(6));
						}

					} else if(line.startsWith("exit")) {
						exitCode = Integer.parseInt(line.substring(line.indexOf(" ") + 1, line.length()));

					} else {
						if(line.indexOf(":") > -1)
							info.append(line.substring(line.indexOf(":")));
						else
							info.append(line);
					}
				}
				reader.close();

				loop = false;
				// If we failed to execute because of an authentication issue, try a p4 login.
				if(mesgIndex == 1 || mesgIndex == 2 || mesgIndex == 6) {
				    if (attemptLogin) {
	                    // password is unset means that perforce isn't using the environment var P4PASSWD
	                    // Instead it is using tickets. We must attempt to login via p4 login, then
	                    // retry this cmd.
	                    p4.close();
	                    login();
	                    loop = true;
	                    attemptLogin = false;
	                    mesgIndex = -1; // cancel this error for now
	                    continue;
				    }
				}
				
				if(mesgIndex != -1 || exitCode != 0) {
					if(error.length() != 0) {
					    error.append("\nFor Command: ").append(debugCmd);
					    if (log.length() > 0) {
					        error.append("\nWith Data:\n===================\n");
					        error.append(log);
                            error.append("\n===================\n");
					    }
                        throw new PerforceException(error.toString());
					}
					throw new PerforceException(info.toString());
				}

			} catch(IOException e) {
				throw new PerforceException("Failed to open connection to perforce", e);
			} finally {
				p4.close();
			}
		} while(loop);
	}

    /**
	 * Executes a perforce command and returns the output as a StringBuilder.
	 * 
	 * @param cmd	The perforce commands to execute.  Each command and argument is it's own array element
	 * @return	The response from perforce as a stringbuilder
	 * @throws PerforceException	If perforce throws any errors
	 */
	protected StringBuilder getPerforceResponse(String cmd[]) throws PerforceException {
		// TODO: Create a way to wildcard portions of the error checking.  Add method to check for these errors.
		boolean loop = false;
		boolean attemptLogin = true;

		List<String> lines = null;
		int totalLength = 0;

		do {
			int mesgIndex = -1, count = 0;
			Executor p4 = depot.getExecFactory().newExecutor();
			String debugCmd = "";
			// get entire cmd to execute
			cmd = getExtraParams(cmd);

			// setup information for logging...
			for(String cm : cmd) {
				debugCmd += cm + " ";
			}

			// Perform execution and IO
			p4.exec(cmd);
			BufferedReader reader = p4.getReader();
			String line = null;
			totalLength = 0;
			lines = new ArrayList<String>(1024);

			try
			{
				while((line = reader.readLine()) != null) {
				    lines.add(line);
				    totalLength += line.length();
					count++;
					
					// only check for errors if we have not found one already
					if (mesgIndex == -1)
					    mesgIndex = checkAuthnErrors(line);
				}
			}
			catch(IOException ioe)
			{
				//this is generally not anything to worry about.  The underlying
				//perforce process terminated and that causes java to be angry
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw, true);
				ioe.printStackTrace(pw);
				pw.flush();
				sw.flush();
				getLogger().warn("Perforce process terminated suddenly");
				getLogger().warn(sw.toString());
			}
			finally{
				p4.close();
			}
			loop = false;
			// If we failed to execute because of an authentication issue, try a p4 login.
			if(attemptLogin && (mesgIndex == 1 || mesgIndex == 2 || mesgIndex == 6)) {
				// password is unset means that perforce isn't using the environment var P4PASSWD
				// Instead it is using tickets. We must attempt to login via p4 login, then
				// retry this cmd.
				p4.close();
				login();
				loop = true;
				attemptLogin = false;
				continue;
			}

			// We aren't using the exact message because we want to add the username for more info
			if(mesgIndex == 4)
				throw new PerforceException("Access for user '" + depot.getUser() + "' has not been enabled by 'p4 protect'");
			if(mesgIndex != -1)
				throw new PerforceException(p4errors[mesgIndex]);
			if(count == 0)
				throw new PerforceException("No output for: " + debugCmd);
		} while(loop);

		StringBuilder response = new StringBuilder(totalLength + lines.size());
		for (String line : lines)
        {
	        response.append(line);
	        response.append("\n");
        }

		return response;
	}

    /**
     * Executes a p4 command and returns the output as list of lines.
     * 
     * TODO Introduce a method that handles prefixed messages (i.e. "p4 -s <sub-command>"),
     * and can thus stop reading once if reads the "exit: <exit-code>" line, which
     * should avoid the "expected" Exception at EOF.
     * 
     * @param cmd
     *      The perforce command to execute.  The command and arguments are
     *      each in their own array element (e.g. cmd = {"p4", "info"}).
     * @return
     *      The response from perforce as a list
     * @throws PerforceException 
     */
    protected List<String> getRawPerforceResponseLines(String cmd[]) throws PerforceException {
        List<String> lines = new ArrayList<String>(1024);

        Executor p4 = depot.getExecFactory().newExecutor();
        String debugCmd = "";
        // get entire cmd to execute
        cmd = getExtraParams(cmd);

        // setup information for logging...
        for(String cm : cmd) {
            debugCmd += cm + " ";
        }

        // Perform execution and IO
        p4.exec(cmd);

        try
        {
            BufferedReader reader = p4.getReader();
            String line = null;
            while((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        catch(IOException ioe)
        {
            //this is generally not anything to worry about.  The underlying
            //perforce process terminated and that causes java to be angry.

            // TODO Given the above comment, should we bother to log a warning?
            // See this blog for a discussion of IOException with message "Write end dead" from pipes:
            //      http://techtavern.wordpress.com/2008/07/16/whats-this-ioexception-write-end-dead/

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true);
            ioe.printStackTrace(pw);
            pw.flush();
            sw.flush();
            getLogger().warn("IOException reading from Perforce process (may just be EOF)");
            getLogger().warn(sw.toString());
        }
        finally{
            p4.close();
        }

        return lines;
    }

	/**
	 * Tries to perform a p4 login if the security level on the server is set to level 3 and no ticket was set via
	 * depot.setP4Ticket().
	 * <p>
	 * Unfortunately, this likely doesn't work on windows.
	 * 
	 * @throws PerforceException	If perforce throws any errors
	 */
	protected void login() throws PerforceException {

		try {
		    // try the default location for p4 executable
            String ticket = null;
            try {
                ticket = p4Login(getP4Exe());
            } catch (PerforceException e) {
                // Strange error under hudson's execution of unit tests.  It appears
                // that the environment is not setup correctly from within hudson.  The sh shell
                // cannot find the p4 executable.  So we'll try again with a hard coded path.
                // Though, I don't believe this problem exists outside of the build environment, 
                // and wouldn't normally worry, I still want to be able to test security level 3
                // from the automated build...
                try {
                    ticket = p4Login("/usr/bin/p4");
                } catch (PerforceException e1) {
                    // throw the original exception and not the one caused by the workaround
                    getLogger().warn("Attempt to workaround p4 executable location failed", e1);
                    throw e;
                }
            }

	        // if we obtained a ticket, save it for later use. Our environment setup by Depot can't usually
	        // see the .p4tickets file.
	        if (ticket != null) {
	            getLogger().warn("Using p4 issued ticket.");
	            depot.setP4Ticket(ticket);
	        }

		} catch(IOException e) {
			throw new PerforceException("Unable to login via p4 login due to IOException: " + e.getMessage());
		}
	}

    /**
     * Read the last line of output which should be the ticket.
     * 
     * @param p4Exe the perforce executable with or without full path information
     * @return the p4 ticket
     * @throws IOException if an I/O error prevents this from working
     * @throws PerforceException if the execution of the p4Exe fails
     */
    private String p4Login(String p4Exe) throws IOException, PerforceException {
        Executor login = depot.getExecFactory().newExecutor();
        login.exec(new String[] { p4Exe, "login", "-p" });

        try {
            // "echo" the password for the p4 process to read
            BufferedWriter writer = login.getWriter();
            try {
                writer.write(depot.getPassword() + "\n");
            } finally {
                // help the writer move the data
                writer.flush();
            }
            // read the ticket from the output
            String ticket = null;
            BufferedReader reader = login.getReader();
            String line;
            // The last line output from p4 login will be the ticket
            while ((line = reader.readLine()) != null) {
                int error = checkAuthnErrors(line);
                if (error != -1)
                    throw new PerforceException("Login attempt failed: " + line);
                ticket = line;
            }
            
            return ticket;
        } finally {
            login.close();
        }
    }
    
    /**
     * Check for authentication errors.
     * 
     * @param line the perforce response line
     * @return the index in the p4errors array or -1
     */
    private int checkAuthnErrors(String line) {
        for (int i = 0; i < p4errors.length; i++) {
        	if (line.indexOf(p4errors[i]) != -1)
        		return i;
    
        }
        return -1;
    }

    protected String getP4Exe() {
        return depot.getExecutable();
    }
}
