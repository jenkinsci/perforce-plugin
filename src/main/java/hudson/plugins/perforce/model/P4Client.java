package hudson.plugins.perforce.model;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a Perforce client.
 * <p>
 * This is necessary because the Client class that Perforce provides in their
 * API is not complete. It is missing several fields and we cannot extend that
 * class because its final.
 * 
 * @author Mike Wille
 */
public class P4Client {
	String name;
	String owner;
	String host;
	String description;
	String root;
	String altRoots;
	String options;
	String lineEnd;
	String submitOptions;
	List<String> views;

	public P4Client() {
		this.name = "";
		this.owner = "";
		this.host = "";
		this.description = "";
		this.root = "";
		this.altRoots = "";
		this.options = "";
		this.views = new ArrayList<String>(1);
		this.lineEnd = "";
		this.submitOptions = "";
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Client]\n");
		sb.append("Name: " + getName() + "\n");
		sb.append("Owner: " + getOwner() + "\n");
		sb.append("Host: " + getHost() + "\n");
		sb.append("Description: " + getDescription() + "\n");
		sb.append("Root: " + getRoot() + "\n");
		sb.append("AltRoot: " + getAltRoots() + "\n");
		sb.append("Options: " + getOptions() + "\n");
		sb.append("LineEnd: " + getLineEnd() + "\n");
		sb.append("Views: \n");
		for(String view : views) {
			sb.append("\t" + view + "\n");
		}
		
		return sb.toString();
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the owner
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * @param owner
	 *            the owner to set
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @param host
	 *            the host to set
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description
	 *            the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the root
	 */
	public String getRoot() {
		return root;
	}

	/**
	 * @param root
	 *            the root to set
	 */
	public void setRoot(String root) {
		this.root = root;
	}

	/**
	 * @return the altRoots
	 */
	public String getAltRoots() {
		return altRoots;
	}

	/**
	 * @param altRoots
	 *            the altRoots to set
	 */
	public void setAltRoots(String altRoots) {
		this.altRoots = altRoots;
	}

	/**
	 * @return the options
	 */
	public String getOptions() {
		return options;
	}

	/**
	 * @param options
	 *            the options to set
	 */
	public void setOptions(String options) {
		this.options = options;
	}

	/**
	 * @return the view
	 */
	public List<String> getViews() {
		return views;
	}

	public String getViewsAsString() {
		StringBuilder sb = new StringBuilder();
		for(String view : views) {
			sb.append("\t" + view + "\n");
		}
		return sb.toString();
	}
	/**
	 * @param view
	 *            the view to set
	 */
	public void addView(String view) {
		this.views.add(view);
	}

	/**
	 * Removes all views from this client.
	 */
	public void clearViews() {
		this.views.clear();
	}

	/**
	 * @return the lineEnd
	 */
	public String getLineEnd() {
		return lineEnd;
	}

	/**
	 * @param lineEnd
	 *            the lineEnd to set
	 */
	public void setLineEnd(String lineEnd) {
		this.lineEnd = lineEnd;
	}

	/**
	 * @return the submitOptions
	 */
	public String getSubmitOptions() {
		return submitOptions;
	}

	/**
	 * @param submitOptions the submitOptions to set
	 */
	public void setSubmitOptions(String submitOptions) {
		this.submitOptions = submitOptions;
	}

}
