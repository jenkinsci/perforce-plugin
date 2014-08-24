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

import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;

/**
 * Responsible for building and saving changelists.
 * 
 * @author Mike Wille
 */
public class ChangelistBuilder implements Builder<Changelist> {
	private final Logger logger = LoggerFactory.getLogger("perforce");
	//Maximum amount of files to be recorded to a changelist
	private int maxFiles;
	private final Depot depot;
	private static final SimpleDateFormat dateParser = new
			SimpleDateFormat("yyyy/MM/dd HH:mm:ss ZZZZZ", Locale.ENGLISH);
	
	public ChangelistBuilder(int maxFiles) {
		this(maxFiles, null);
	}
	
	public ChangelistBuilder(int maxFiles, Depot depot) {
	    this.maxFiles = maxFiles;
	    this.depot = depot;
	}

	public String[] getBuildCmd(String p4exe, String id) {
		return new String[] { p4exe, "describe", "-s", id };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#build(java.lang.StringBuilder)
	 */
	public Changelist build(StringBuilder sb) throws PerforceException {
		Changelist change = null;
		StringTokenizer lines = new StringTokenizer(sb.toString(), "\n\r");
		try {
			while(lines.hasMoreElements()) {
				String line = lines.nextToken();
				logger.debug("Line: " + line);

				if(line.startsWith("Change")) {
					logger.debug("New changelist.");

					change = new Changelist();
					// Line looks like:
					// Change XXXX by user@client on YYYY/MM/DD HH:MM:SS
					StringTokenizer details = new StringTokenizer(line);
					details.nextToken(); // client
					change.setChangeNumber(new Integer(details.nextToken()));
					details.nextToken(); // by
					String user = details.nextToken();
					change.setUser(user.substring(0, user.indexOf("@")));
					change.setWorkspace(user.substring(user.indexOf("@") + 1));
					details.nextToken(); // on

					String date = details.nextToken();
					String time = details.nextToken();
					
					// check if we have a reference to the depot containing the changelists.
					// if so, use it in order to get information about the server's timezone.
					if(depot != null) {
						change.setDate(parseDateWithTimezone(date + " " + time + " " + depot.getServerTimezone()));
					}
					else {
						change.setDate(parseDate(date + " " + time));
					}
					

					// the lines immediately following is the description
					StringBuilder desc = new StringBuilder();
					line = lines.nextToken();
					while(line != null && !line.startsWith("Affected files") && !line.startsWith("Jobs fixed")) {
						logger.debug("Description Line: " + line.trim());
						desc.append(line + "\n");
						line = lines.nextToken();
					}
					change.setDescription(desc.toString().trim());

				}

				if(line.startsWith("Jobs fixed")) {
					logger.debug("Has jobs.");
					List<Changelist.JobEntry> jobs = new ArrayList<Changelist.JobEntry>();
					boolean getDesc = false;
					Changelist.JobEntry job = new Changelist.JobEntry();
					String description = null;
					line = lines.nextToken();
					while(!line.startsWith("Affected files")) {
						logger.debug("Job Line: " + line);
						if(!getDesc) {
							// Line looks like:
							// EXT-84 on 2007/09/25 by mwille *closed*
							// or
							// EXT-84 on 2007/09/25 *closed*
							// or
							// EXT-84 on 2007/09/25
                            // or 
                            // EXT-84 on 2007/09/25 by mwille
							StringTokenizer details = new StringTokenizer(line);
							job = new Changelist.JobEntry();
							if (details.hasMoreTokens())
								job.setJob(details.nextToken());
							else
								logger.error("We shouldnt be here.  Should be getting job but no nextToken: " + line);
							
							if (details.hasMoreTokens())
								details.nextToken(); // on
							else
								logger.error("We shouldnt be here.  Should be popping off on but no nextToken: " + line);
							
							if (details.hasMoreTokens())
								details.nextToken(); // date
							else
								logger.error("We shouldnt be here.  Should be popping off date but no nextToken: " + line);
							
							String status = "";

							if (details.hasMoreTokens())
							{
								String possibleUser = details.nextToken(); // by
								if ("by".equals(possibleUser))
								{
									if (details.hasMoreTokens())
										details.nextToken(); // user
									else
										logger.error("We shouldnt be here.  Should be popping off user since found by but no nextToken: " + line);
                                                                        if (details.hasMoreTokens()) //status is optional
										status = details.nextToken(); // status
								}
								else
								{
									status = possibleUser;
								}
							}

							job.setStatus(status);
							getDesc = true;
							line = lines.nextToken();
						} else {  //getDesc
							//What comes back from p4 describe -s is actually the title not description and should only be one line
							//leave handling of multiple lines because who knows what the future will bring
							//It is possible to not have a description/title
							//The title will start with a tab where Affected files or another job will not.
							//Also possible to have blank lines
							description = "";							
							while(line.startsWith("\t") || line.length() == 0) {
								description += line;
								if(!lines.hasMoreElements()) {
									logger.error("We shouldnt be here.  We are not out of getting the description for the job but no more lines");
									break;
								}
								description += "\n";
								line = lines.nextToken();
							}
							job.setDescription(description.trim());
							jobs.add(job);
							getDesc = false;
						}

					}  //while lines

					change.setJobs(jobs);

				}

				if(line.startsWith("Affected files")) {
					logger.debug("reading files...");
					List<Changelist.FileEntry> files = new ArrayList<Changelist.FileEntry>();
			        int fileCount = 0;

					while(lines.hasMoreElements()) {
					    //Record a maximum of maxFiles files
					    if(maxFiles > 0) {
	                        if(fileCount >= maxFiles) {
	                            break;
	                        }
					        fileCount++;
					    }
						String entry = lines.nextToken();
						logger.debug("File Line: " + entry);
						// if(!entry.startsWith("..."))
						// break;
						// line looks lie:
						// ... //depot/path/to/file/file.ext#1 edit

						int revStart = entry.indexOf("#");
						if(revStart < 0)
							continue;
						String filename = entry.substring(4, revStart);
						String rev = entry.substring(revStart + 1, entry.indexOf(" ", revStart));
						String action = entry.substring(entry.indexOf(" ", revStart) + 1);
						action = action.replace('/', '_');
						Changelist.FileEntry file = new Changelist.FileEntry();
						file.setFilename(filename);
						file.setRevision(rev);
                                                file.setChangenumber(Integer.valueOf(change.getChangeNumber()).toString());
						file.setAction(Changelist.FileEntry.Action.valueOf(action.toUpperCase(Locale.US)));
						files.add(file);
					}

					change.setFiles(files);

				}
			}
		} catch(Exception e) {
			logger.error("Exception: " + e.getMessage());
			throw new PerforceException("Failed to retrieve changelist.\nResponse from perforce was:\n" + sb, e);
		}
		return change;
	}

	public String[] getSaveCmd(String p4exe, Changelist obj) {
		return new String[] { p4exe, "change", "-i" };
	}

	public boolean requiresStandardInput() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#save(java.lang.Object)
	 */
	public void save(Changelist obj, Writer out) throws PerforceException {
		throw new UnsupportedOperationException("This is not implemented.");
	}

	/**
	 * Returns a java.util.Date object set to the time specified in newDate.
	 * This function uses the {@code dateParser} object internally, and thus
	 * expects the same format on input.
	 * @throws PerforceException When the string passed into the function could
	 * not be parsed according to the format specified in {@code dateParser}.
	 */
	private static java.util.Date parseDateWithTimezone(String newDate) throws PerforceException {
		try {
			return dateParser.parse(newDate);
		}
		catch(ParseException e) {
			throw new PerforceException("Cannot parse changelist timestamp : " + newDate);
		}
	}
	
	/**
	 * Returns a java.util.Date object set to the time specified in newDate. The format expected is the format of:
	 * YYYY-MM-DD HH:MM:SS
	 * 
	 * @param newDate
	 *            the string date to convert
	 * @return A java.util.Date based off of the string format.
	 */
	public static java.util.Date parseDate(String newDate) {
		// when we have a null from the database, give it zeros first.
		if(newDate == null || newDate.equals("")) {
			return null;
		}

		String parts[] = newDate.split(" ");
		String date[] = parts[0].split("/");
		String time[] = null;

		if(parts.length > 1) {
			time = parts[1].split(":");
			time[2] = time[2].replaceAll("\\.0", "");
		} else {
			time = "00:00:00".split(":");
		}

		GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
		cal.clear();

		cal.set(new Integer(date[0]).intValue(), (new Integer(date[1]).intValue() - 1), new Integer(date[2]).intValue(), new Integer(
				time[0]).intValue(), new Integer(time[1]).intValue(), new Integer(time[2]).intValue());

		return cal.getTime();
	}

}
