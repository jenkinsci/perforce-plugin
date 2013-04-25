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
import java.io.InputStream;
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
            "The authenticity of",
        };

    @SuppressWarnings("unused")
    private transient Logger logger;   // Obsolete field, present just to keep demarshaller happy
    @SuppressWarnings("unused")
    private transient String errors[];   // Obsolete field, present just to keep demarshaller happy

    private final Depot depot;
    final transient String maxError = "Request too large";

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
         * Used to filter the response from perforce so the API can throw out 
         * useless lines and thus save memory during large operations.
         * ie. synced/refreshed lines from 'p4 sync'
         */
        public abstract static class ResponseFilter {
            public abstract boolean accept(String line);
            public boolean reject(String line){
                return !accept(line);
            }
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

		StringBuilder debugResponse = new StringBuilder();

		do {
			int mesgIndex = -1;//, count = 0;
			Executor p4 = depot.getExecFactory().newExecutor();
			String debugCmd = "";
			try {
				String cmds[] = getExtraParams(builder.getSaveCmd(getP4Exe(), object));

                // Add '-v 1' to add some verbosity
                // If the p4 command fails, the log will show a bit more info, like the local port number
                
                String newCmds[] = new String[cmds.length + 2];
			    newCmds[0] = cmds[0];
			    newCmds[1] = "-v";
			    newCmds[2] = "1";
			    for(int i = 3; (i - 2) < cmds.length; i++) {
				    newCmds[i] = cmds[i - 2];
			    }
			    cmds = newCmds;

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

                    debugResponse.append(line + "\n");

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
				if(mesgIndex == 1 || mesgIndex == 2 || mesgIndex == 6 || mesgIndex == 9) {
				    if (attemptLogin) {
	                    // password is unset means that perforce isn't using the environment var P4PASSWD
	                    // Instead it is using tickets. We must attempt to login via p4 login, then
	                    // retry this cmd.
	                    p4.close();
                            trustIfSSL();
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
                            error.append("===================\n");
                            error.append(debugResponse.toString());
                            error.append("===================\n");
					    }
                        throw new PerforceException(error.toString());
					}
					throw new PerforceException("COMMAND: " + debugCmd + "\n---\n" + debugResponse.toString() + "---");
				}

			} catch(IOException e) {
				throw new PerforceException("Failed to open connection to perforce", e);
			} finally {
                                try{
                                    p4.getWriter().close();
                                } catch (IOException e) {
                                    //failed to close pipe, but we can't do much about that
                                }
                                try{
                                    p4.getReader().close();
                                } catch (IOException e) {
                                    //failed to close pipe, but we can't do much about that
                                }
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
            return getPerforceResponse(cmd, new ResponseFilter(){
                @Override
                public boolean accept(String line) {
                    return true;
                }
            });
        }

	protected StringBuilder getPerforceResponse(String origcmd[], ResponseFilter filter) throws PerforceException {
		// TODO: Create a way to wildcard portions of the error checking.  Add method to check for these errors.
		boolean loop = false;
		boolean attemptLogin = true;

		List<String> lines = null;
		int totalLength = 0;

		StringBuilder debugResponse = new StringBuilder();

		do {
			int mesgIndex = -1, count = 0;
			Executor p4 = depot.getExecFactory().newExecutor();
			String debugCmd = "";
			// get entire cmd to execute
                        String cmds[] = getExtraParams(origcmd);
                        
            // Add '-v 1' to add some verbosity
            // If the p4 command fails, the log will show a bit more info, like the local port number
            
            String newCmds[] = new String[cmds.length + 3];
            newCmds[0] = cmds[0];
            newCmds[1] = "-s";
            newCmds[2] = "-v";
            newCmds[3] = "1";
            for(int i = 4; (i - 3) < cmds.length; i++) {
       	        newCmds[i] = cmds[i - 3];
            }
           cmds = newCmds;
			    
			// setup information for logging...
			for(String cm : cmds) {
				debugCmd += cm + " ";
			}

			// Perform execution and IO
			p4.exec(cmds);
			BufferedReader reader = p4.getReader();
			String line = null;
			totalLength = 0;
			lines = new ArrayList<String>(1024);

			try
			{
                p4.getWriter().close();
				while((line = reader.readLine()) != null) {
				    debugResponse.append(line + "\n");
                    // only check for errors if we have not found one already
                    if (mesgIndex == -1)
                        mesgIndex = checkAuthnErrors(line);
                    if(filter.reject(line)) continue;
                    
                    if (line.matches("^exit: .*")) {
                        break;
                    }
                    
                    if (line.matches("^info: .*|^info1: .*|^info2: .*|^text: .*|^error: .*")) {
                        String strippedline = line.replaceFirst("^info: |^info1: |^info2: |^text: |^error: ", "");
                        
                        lines.add(strippedline);
                        totalLength += strippedline.length();
                        count++;
                    }
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
                            try{
                                p4.getWriter().close();
                            } catch (IOException e) {
                                getLogger().warn("Write pipe failed to close.");
                            }
                            try{
                                p4.getReader().close();
                            } catch (IOException e) {
                                getLogger().warn("Read pipe failed to close.");
                            }
                            p4.close();
			}
			loop = false;
			// If we failed to execute because of an authentication issue, try a p4 login.
			if(attemptLogin && (mesgIndex == 1 || mesgIndex == 2 || mesgIndex == 6 || mesgIndex == 9)) {
				// password is unset means that perforce isn't using the environment var P4PASSWD
				// Instead it is using tickets. We must attempt to login via p4 login, then
				// retry this cmd.
				p4.close();
                                trustIfSSL();
				login();
				loop = true;
				attemptLogin = false;
				continue;
			}

			// We aren't using the exact message because we want to add the username for more info
			if(mesgIndex == 4)
				throw new PerforceException("Access for user '" + depot.getUser() + "' has not been enabled by 'p4 protect'");
			if(mesgIndex != -1)
				throw new PerforceException(p4errors[mesgIndex] + "\nCOMMAND: " + debugCmd + "\n---\n" + debugResponse.toString() + "---");
			if(count == 0)
				throw new PerforceException("No output for:\nCOMMAND: " + debugCmd + "\n---\n" + debugResponse.toString() + "---");
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
     *
     * Used only by src/main/java/com/tek42/perforce/parse/Changes.java
     * - getHighestLabelChangeNumber
     * - getChangeNumbersInRangeForSinglePath
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
            p4.getWriter().close();
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
            try{
                p4.getWriter().close();
            } catch (IOException e) {
                getLogger().warn("Write pipe failed to close.");
            }
            try{
                p4.getReader().close();
            } catch (IOException e) {
                getLogger().warn("Read pipe failed to close.");
            }
            p4.close();
        }

        return lines;
    }

    /**
     * Used by calls that make use of p4.exe's python dictionary output format.
     * @param cmd
     * @return
     * @throws PerforceException
     *
     * Used only by src/main/java/com/tek42/perforce/parse/Changes.java
     * - calculateWorkspacePaths
     */

    protected byte[] getRawPerforceResponseBytes(String cmd[]) throws PerforceException {
        List<Byte> bytes = new ArrayList<Byte>(1024);

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
            byte[] cbuf = new byte[1024];
            InputStream input = p4.getInputStream();
            p4.getWriter().close();
            int readCount = -1;
            while((readCount = input.read(cbuf, 0, 1024)) != -1) {
                for(int i=0; i<readCount; i++){
                    bytes.add(new Byte((byte)(cbuf[i]&0xff)));
                }
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
            try{
                p4.getWriter().close();
            } catch (IOException e) {
                getLogger().warn("Write pipe failed to close.");
            }
            try{
                p4.getReader().close();
            } catch (IOException e) {
                getLogger().warn("Read pipe failed to close.");
            }
            p4.close();
        }
        byte[] byteArray = new byte[bytes.size()];
        for(int i=0; i<bytes.size(); i++){
            byteArray[i] = bytes.get(i).byteValue();
        }
        return byteArray;
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
                getLogger().warn("Login with '" + getP4Exe() + "' failed: " + e.getMessage());
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
	        if (ticket != null && !ticket.contains("Enter password:")) {
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
        login.exec(new String[] { p4Exe, "login", "-a", "-p" });

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
            // The line matching ^[0-9A-F]{32}$ will be the ticket
            while ((line = reader.readLine()) != null) {
                int error = checkAuthnErrors(line);
                if (error != -1)
                    throw new PerforceException("Login attempt failed: " + line);
                if (line.trim().matches("^[0-9A-F]{32}$"))
                    ticket = line;
            }
            
            return ticket;
        } finally {
            login.close();
        }
    }
    
    /**
     * Trust the perforce server if using SSL
     */
    private void trustIfSSL() throws PerforceException {
        Executor trust = depot.getExecFactory().newExecutor();
        String p4Port = depot.getPort();
        if(p4Port.toLowerCase().startsWith("ssl:")){
            trust.exec(new String[] { getP4Exe(), "-p", depot.getPort(), "trust", "-y" });
            try{
                trust.getWriter().close();
                BufferedReader reader = trust.getReader();
                String line;
                // The line matching ^[0-9A-F]{32}$ will be the ticket
                while ((line = reader.readLine()) != null) {
                    int error = checkAuthnErrors(line);
                    if (error != -1)
                        throw new PerforceException("Trust attempt failed: " + line);
                }
            } catch (IOException e) {
                throw new PerforceException("Could not establish ssl trust with perforce server", e);
            }
            trust.close();
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
