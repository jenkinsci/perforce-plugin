package hudson.plugins.perforce;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.*;
import java.util.Date;
import java.util.StringTokenizer;

import hudson.plugins.perforce.model.*;

import com.perforce.api.*;

/**
 * Provides additional features not available in the standard API.
 * <p>
 * I'll refrain from commenting on how assign it is that the base API isn't
 * sufficient and that you can extend it.
 * 
 * @author Mike Wille
 * 
 */
public class PerforceUtils {
	/**
	 * Saves changes to a client root and its views.
	 * <p>
	 * Unfortunately, the standard Perforce API doesn't work when you want to
	 * modify a client's views. Our need is to replace a client view with a new
	 * one. You can't do that, only append to the list of views a client has.
	 * here... We need a Client.clearViews() method. Nice thing about Perforce
	 * is they made their classes final so we can't even modify the behavior :(
	 * Instead of creating a new client object and API, we'll just create a
	 * static save method.
	 * <p>
	 * Testing Note #2<br>
	 * Grr... Apparently Perforce's API doesn't even fully support the latest
	 * features in their system. Using their Client object, it doesn't fully
	 * load all of the fields from the server. So if you resave a client
	 * retrieved from the API, you loose those fields! As a fix, this method
	 * will first load the client from the server, staying aware of all fields,
	 * then overlay the changes and save the client. Ridiculous!
	 * <p>
	 * Additionally, the API method to save your changes to your client doesn't
	 * save all fields. Out of the 11 fields a client has, only 4 are actually
	 * saved back! Ridiculous!
	 * 
	 * @param env
	 * @param client
	 * @param newRoot
	 * @param newView
	 */
	public static void changeClientProject(Env env, String clientName, String newRoot, String newView)
			throws PerforceException {
		P4Client client = getClient(env, clientName);
		System.out.println("Have client: \n" + client);
		client.setRoot(newRoot);
		client.clearViews();
		client.addView(newView);

		System.out.println("Revised client: \n" + client);

		String[] cmd = { "p4", "client", "-i" };
		String l;
		int cnt = 0;
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			p.println("Client: " + client.getName());
			p.println("Owner: " + client.getOwner());
			p.println("Host: " + client.getHost());
			p.println("Description:");
			p.println("\t" + client.getDescription());
			p.println("Root: " + client.getRoot());
			p.println("Options: " + client.getOptions());
			p.println("LineEnd: " + client.getLineEnd());
			p.println("View:");
			p.println(client.getViewsAsString());
			if(!client.getAltRoots().equals(""))
				p.println("AltRoots: " + client.getAltRoots());
			if(!client.getSubmitOptions().equals(""))
				p.println("SubmitOptions: " + client.getSubmitOptions());
			p.flush();
			p.outClose();
			while(null != (l = p.readLine())) {
				if(0 == cnt++)
					continue;
			}
			p.close();
		} catch(Exception ex) {
			System.out.println("Caught Exception in changeClientProject: " + ex.getMessage());
			ex.printStackTrace();
			throw new CommitException(ex.getMessage());
		}
	}

	public static void saveClient(Env env, Client client) throws PerforceException {
		String[] cmd = { "p4", "client", "-i" };
		String l;
		int cnt = 0;
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			p.println("Client: " + client.getName());
			p.println("Owner: " + client.getOwner());
			p.println("Root: " + client.getRoot());
			p.println("View:");
			p.println(client.getView());
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

	public static P4Client getClient(Env env, String name) throws PerforceException {
		String description = "";
		String l;
		String[] cmd = { "p4", "client", "-o", "name" };
		cmd[3] = name;

		P4Client client = new P4Client();

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#")) {
					continue;
				}
				if(l.startsWith("Client:")) {
					client.setName(l.substring(8).trim());

				} else if(l.startsWith("Owner:")) {
					client.setOwner(l.substring(7).trim());

				} else if(l.startsWith("Host:")) {
					client.setOwner(l.substring(6).trim());

				} else if(l.startsWith("Root:")) {
					client.setRoot(l.substring(6).trim());

				} else if(l.startsWith("Options:")) {
					client.setOptions(l.substring(9).trim());

				} else if(l.startsWith("SubmitOptions:")) {
					client.setSubmitOptions(l.substring(15).trim());

				} else if(l.startsWith("LineEnd:")) {
					client.setLineEnd(l.substring(9).trim());

				} else if(l.startsWith("AltRoots:")) {
					client.setAltRoots(l.substring(10).trim());

				} else if(l.startsWith("Description:")) {
					while(null != (l = p.readLine())) {
						if(!l.startsWith("\t"))
							break;
						description += l + "\n";
					}
					client.setDescription(description);
				} else if(l.startsWith("View:")) {
					while(null != (l = p.readLine())) {
						if(!(l.startsWith("\t") || l.startsWith(" ") || l.startsWith("//")))
							break;
						client.addView(l);
					}
				}
			}

			return client;

		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
			throw new PerforceException("Unable to load client: " + ex.getMessage());
		}
	}

	/**
	 * Returns the last changelist number in the depot for the specified path.
	 * <p>
	 * By default Perforce API doesn't allow you to specify many options for
	 * retrieving a list of changes. All you can really do is get a list of the
	 * 100 most recent changes. This is more peformant because it bypasses the
	 * API and only gets the last changelist.
	 * 
	 * @param env
	 * @param projectPath
	 * @return
	 * @throws PerforceException
	 */
	public static int getLatestChangeForProject(Env env, String projectPath) throws PerforceException {

		String[] cmd = new String[7];

		cmd[0] = "p4";
		cmd[1] = "changes";
		cmd[2] = "-m"; // max of latest
		cmd[3] = "1"; // latest 1 only
		cmd[4] = "-s"; // specify status
		cmd[5] = "submitted"; // status value restriction
		cmd[6] = projectPath;

		StringTokenizer st;
		int num;
		String l;
		P4Process p = null;
		try {
			p = new P4Process(env);
			p.setRawMode(true);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("info: Change")) {
					l = l.substring(5).trim();
					st = new StringTokenizer(l);
					if(!st.nextToken().equals("Change")) {
						continue;
					}

					try {
						num = Integer.parseInt(st.nextToken());
					} catch(Exception ex) {
						throw new PerforceException("Could not parse change number from line: " + l);
					}
					return num;
				} else {
					if(l.length() <= 5) {
						Debug.warn("Line l is less than 5 chars? l:" + l);
					}
					return -1;
				}
			}
		} catch(IOException e) {
			Debug.out(Debug.ERROR, e);
		}
		return -1;
	}
}
