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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;
import com.tek42.perforce.model.Workspace;

/**
 * Base API object for interacting with changelists.
 * 
 * @author Mike Wille
 * @author Brian Westrich
 */
public class Changes extends AbstractPerforceTemplate {

	public Changes(Depot depot) {
		super(depot);
	}

	/**
	 * Returns a single changelist specified by its number.
	 * 
	 * @param number
	 * @return
	 * @throws PerforceException
	 */
	public Changelist getChangelist(int number) throws PerforceException {
		ChangelistBuilder builder = new ChangelistBuilder();
		Changelist change = builder.build(getPerforceResponse(builder.getBuildCmd(Integer.toString(number))));
		if(change == null)
			throw new PerforceException("Failed to retrieve changelist " + number);
		return change;
	}

	/**
	 * Returns a list of changelists that match the parameters
	 * 
	 * @param path
	 *            What point in the depot to show changes for?
	 * @param lastChange
	 *            The last changelist number to start from
	 * @param limit
	 *            The maximum changes to return if less than 1, will return everything
	 * @return
	 * @throws PerforceException
	 */
	public List<Changelist> getChangelists(String path, int lastChange, int limit) throws PerforceException {
		path = normalizePath(path);
		if(lastChange > 0)
			path += "@" + lastChange;

		String cmd[];

		if(limit > 0)
			cmd = new String[] { "p4", "changes", "-m", Integer.toString(limit), path };
		else
			cmd = new String[] { "p4", "changes", path };

		StringBuilder response = getPerforceResponse(cmd);
		List<String> ids = parseList(response, 1);

		List<Changelist> changes = new ArrayList<Changelist>();
		for(String id : ids) {
			changes.add(getChangelist(new Integer(id)));
		}
		return changes;
	}

	/**
	 * A lightweight call to return changelist numbers for a given path.
	 * <p>
	 * To get the latest change in the depot for the project, you can use:
	 * 
	 * <pre>
	 * depot.getChangeNumbers(&quot;//project/...&quot;, -1, 1)
	 * </pre>
	 * 
	 * <p>
	 * Note: this method follows perforce in that it starts at the highest number and works backwards. So this might not
	 * be what you want. (It certainly isn't for Hudson)
	 * 
	 * @param path
	 *            Path to filter on
	 * @param start
	 *            The number of the change to start from
	 * @param limit
	 *            The number of changes to return
	 * @return
	 * @throws PerforceException
	 */
	public List<Integer> getChangeNumbers(String path, int start, int limit) throws PerforceException {
		path = normalizePath(path);
		if(start > 0)
			path += "@" + start;

		String cmd[];

		if(limit > 0)
			cmd = new String[] { "p4", "changes", "-m", Integer.toString(limit), path };
		else
			cmd = new String[] { "p4", "changes", path };

		StringBuilder response = getPerforceResponse(cmd);
		List<String> ids = parseList(response, 1);
		List<Integer> numbers = new ArrayList<Integer>(ids.size());
		for(String id : ids) {
			numbers.add(new Integer(id));
		}
		return numbers;
	}

	/**
	 * Returns a list of changenumbers that start with the most recent change and work back to the specified change.
	 * 
	 * @param path
	 * @param untilChange
	 * @return
	 */
	public List<Integer> getChangeNumbersTo(String path, int untilChange) throws PerforceException {

		return getChangeNumbersTo(null, path, untilChange);

	}

	/**
	 * Returns a list of changenumbers that start with the most recent change and work back to the specified change.
	 * 
	 * @param workspace
	 * @param path
	 *            one or more paths, e.g. "//testproject/... //testfw/...". Paths are assumed to be delimited by a
	 *            single space.
	 * @param untilChange
	 * @return
	 */
	public List<Integer> getChangeNumbersTo(String workspace, String path, int untilChange) throws PerforceException {
		String DELIM = " ";

		// maximum number of paths per command supported by perforce
		// note that command line perforce supports up to three, but p4java only
		// supports one.
		int MAX_PATHS_SUPPORTED_PER_COMMAND = 1;

		// Ccheck our path variable to see if we have multiple paths separated by space.  Add those to the list
		StringTokenizer allPaths = new StringTokenizer(path, DELIM);
		List<String> supportedPaths = new ArrayList<String>();
		StringBuilder currentPaths = new StringBuilder("");
		int numberOfPathsInCurrentPaths = 0;
		while(true) {
			if(!allPaths.hasMoreTokens()) {
				if(currentPaths.length() > 0) {
					supportedPaths.add(currentPaths.toString().trim());
				}
				break;
			}
			String nextPath = allPaths.nextToken();
			currentPaths.append(nextPath + " ");
			numberOfPathsInCurrentPaths++;
			if(numberOfPathsInCurrentPaths == MAX_PATHS_SUPPORTED_PER_COMMAND) {
				supportedPaths.add(currentPaths.toString().trim());
				currentPaths.setLength(0);
				numberOfPathsInCurrentPaths = 0;
			}
		}

		// For each of those paths found, load the change list numbers for it.  Store them in a set.
		Set<Integer> uniqueIds = new HashSet<Integer>();
		for(String pathToUse : supportedPaths) {
			List<Integer> ids = getChangeNumbersToForSinglePath(workspace, pathToUse, untilChange);
			uniqueIds.addAll(ids);
		}

		// Sort and return
		List<Integer> sortedIds = new ArrayList<Integer>(uniqueIds);
		Collections.sort(sortedIds, Collections.reverseOrder());
		return sortedIds;
	}

	/**
	 * Returns a list of changenumbers that start with the most recent change and work back to the specified change.
	 * 
	 * @param workspace
	 * @param path
	 *            a single path, e.g. //testproject/...
	 * @param untilChange
	 * @return
	 */
	private List<Integer> getChangeNumbersToForSinglePath(String workspace, String path, int untilChange) throws PerforceException {
		List<Integer> numbers = new ArrayList<Integer>();
		recurseGetChangeNumbersTo(workspace, path, untilChange, numbers);
		return numbers;
	}

	/**
	 * Internal method that will handle a Perforce MaxResults when looking for changelists that return too many results.  If
	 * the error is encountered, it will call p4 dirs path/* to find a list of top level directories beneath the desired path.
	 * It will then iterate over that list and call itself on each directory.  This gets beyond the MaxResults
	 * issue.  See: https://hudson.dev.java.net/issues/show_bug.cgi?id=1939
	 *
	 * @param workspace
	 * @param path
	 * @param untilChange
	 * @param numbers
	 * @throws PerforceException
	 */
	private void recurseGetChangeNumbersTo(String workspace, String path, int untilChange, List<Integer> numbers) throws PerforceException {
		path = normalizePath(path);

		List<String> cmdList = new ArrayList<String>();

		addCommand(cmdList, "p4", "changes", "-m", "25");
		addCommandWorkspace(cmdList, workspace);
		addCommand(cmdList, path);

		String lastChange;
		boolean continueProcessing = true; 
		while(continueProcessing) {
			// System.out.println("Looping: " + counter++);
			StringBuilder response;
			try {
				// getPerforceResponse will throw an exception if a command it executes
				// returns nothing from perforce. If we are moving back through a list and have
				// less change lists in the history then what was specified, we will hit this
				// exception
				response = getPerforceResponse(cmdList.toArray(new String[cmdList.size()]));
				if(hitMax(response)) {
					String newPaths[] = getTopLevelDirectoriesForPath(workspace, path);
					for(String newPath : newPaths) {
						recurseGetChangeNumbersTo(workspace, newPath, untilChange, numbers);
					}
					break;
				}
			} catch(PerforceException e) {
				if(e.getMessage().startsWith("No output for"))
					break;
				throw e;
			}
			List<String> temp = parseList(response, 1);
			if(temp.size() == 0)
				break;
			for(String num : temp) {
				if(new Integer(num) >= untilChange)
				{
					getLogger().warn("num is " + num + " until is " + untilChange);
					numbers.add(new Integer(num));
				}
				else
				{
					continueProcessing = false;
					break;
				}
			}
			lastChange = temp.get(temp.size() - 1);
			int next = 0;
			try {
				next = new Integer(lastChange) - 1;
			}
			catch (NumberFormatException nfe)
			{
				getLogger().warn("Unable to parse perforce message.  Expected a number but got " + lastChange);
				getLogger().warn("From command " + response.toString());
			}
			cmdList.clear();
			getLogger().warn("running p4 changes for " + next + " until change is " + untilChange);
			addCommand(cmdList, "p4", "changes", "-m", "25");
			addCommandWorkspace(cmdList, workspace);
			addCommand(cmdList, path + "@" + next);
		}
	}

	/**
	 * Executes: p4 dirs -C workspacename //depot/path/*
	 * to find a list of top level directories beneath the path.
	 *
	 * @param workspace	The optional workspace to limit search to.  Null if not used.
	 * @param path	The path to search
	 * @return	A string array of paths.
	 * @throws PerforceException	If there are problems communicating with perforce.
	 */
	private String[] getTopLevelDirectoriesForPath(String workspace, String path) throws PerforceException {

		if(path.endsWith("..."))
			path = path.replaceAll("\\.\\.\\.", "\\*");

		List<String> cmdList = new ArrayList<String>();
		addCommand(cmdList, "p4", "dirs");
		addCommandWorkspace(cmdList, workspace);
		addCommand(cmdList, path);

		StringBuilder response = getPerforceResponse(cmdList.toArray(new String[cmdList.size()]));
		List<String> list = parseList(response, 0);

		return list.toArray(new String[list.size()]);
	}

	/**
	 * Add workspace to the command.
	 * 
	 * @param cmdList
	 * @param workspace
	 */
	private void addCommandWorkspace(List<String> cmdList, String workspace) {
		if(workspace != null) {
			addCommand(cmdList, "-c", workspace);
		}
	}

	/**
	 * translate the path into a p4 acceptable format.
	 * 
	 * @param path
	 *            the path
	 * @return the normalized path
	 */
	private String normalizePath(String path) {
		if(path == null || path.equals(""))
			path = "//...";
		return path;
	}

	/**
	 * add one or more parameters to a command
	 * 
	 * @param list
	 *            the command
	 * @param args
	 *            the parameters to add
	 */
	private void addCommand(List<String> list, String... args) {
		for(String command : args) {
			list.add(command);
		}
	}

	/**
	 * Converts a list of numbers to a list of changes.
	 * 
	 * @param numbers
	 * @return
	 * @throws PerforceException
	 */
	public List<Changelist> getChangelistsFromNumbers(List<Integer> numbers) throws PerforceException {
		List<Changelist> changes = new ArrayList<Changelist>();
		for(Integer id : numbers) {
			changes.add(getChangelist(id));
		}
		return changes;
	}
	
	/**
     * Return the change numbers in the range [first, last] that apply to the
     * specified workspace.  The change numbers are returned highest (most
     * recent) first.
     * 
     * @param first
     *            The number of the change to start from
     * @param last
     *            The last change to include (if applies to the workspace)
     * @return list of change numbers
     * @throws PerforceException
     */
    public List<Integer> getChangeNumbersInRange(Workspace workspace, int first, int last) throws PerforceException {
        StringBuilder sb = new StringBuilder();
        sb.append("//");
        sb.append(workspace.getName());
        sb.append("/...@");
        sb.append(first);
        sb.append(",@");
        sb.append(last);

        String path = sb.toString();
        String[] cmd = new String[] { "p4", "-s", "changes", path };

        List<String> response = getRawPerforceResponseLines(cmd);
        List<Integer> numbers = new ArrayList<Integer>(response.size());

        // TODO Handle error cases, and "exit: <exit-code>" (currently just ignored,
        // should really be parsing that line, and providing that value to the caller
        // by some means).

        for (String line : response) {
            if (line.startsWith("info: Change ")) {
                int offset = line.indexOf(' ', 13);
                String s = line.substring(13, offset);
                Integer n = Integer.valueOf(s);
                numbers.add(n);
                continue;
            }
        }
        return numbers;
    }
}
