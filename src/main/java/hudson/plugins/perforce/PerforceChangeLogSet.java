package hudson.plugins.perforce;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

import com.tek42.perforce.model.Changelist;

/**
 * 
 * @author Mike Wille
 *
 */
public class PerforceChangeLogSet extends ChangeLogSet<PerforceChangeLogEntry> {

	private List<PerforceChangeLogEntry> history = null;

	public PerforceChangeLogSet(AbstractBuild<?, ?> build, List<PerforceChangeLogEntry> logs) {
		super(build);
		this.history = Collections.unmodifiableList(logs);
	}
	
	public List<PerforceChangeLogEntry> getHistory() {
		return history;
	}
	
	/* (non-Javadoc)
	 * @see hudson.scm.ChangeLogSet#isEmptySet()
	 */
	@Override
	public boolean isEmptySet() {
		return history.size() == 0;
	}

	/* (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<PerforceChangeLogEntry> iterator() {
		return history.iterator();
	}
	
	/**
	 * Parses the change log stream and returns a clear case change log set.
	 * 
	 * @param build the build for the change log
	 * @param changeLogStream input stream containing the change log
	 * @return the change log set
	 */
	@SuppressWarnings("unchecked")
	public static PerforceChangeLogSet parse(AbstractBuild build, InputStream changeLogStream) throws IOException, SAXException {

		ArrayList<PerforceChangeLogEntry> history = new ArrayList<PerforceChangeLogEntry>();

		SAXReader reader = new SAXReader();
		Document changeDoc = null;			
		
		try {
			changeDoc = reader.read(changeLogStream);
			
			Node historyNode = changeDoc.selectSingleNode("/changelog");
			if(historyNode == null)
				return new PerforceChangeLogSet(build, history);
			
			List<Node> entries = historyNode.selectNodes("entry");
			if(entries == null)
				return new PerforceChangeLogSet(build, history);
			
			for(Node node : entries) {
				Changelist change = new Changelist();
				
				if(node.selectSingleNode("changenumber") != null)
					change.setChangeNumber(new Integer(node.selectSingleNode("changenumber").getStringValue()));
				
				if(node.selectSingleNode("date") != null)
					change.setDate(node.selectSingleNode("date").getStringValue());
				
				if(node.selectSingleNode("description") != null)
					change.setDescription(node.selectSingleNode("description").getStringValue());
				
				if(node.selectSingleNode("user") != null)
					change.setUser(node.selectSingleNode("user").getStringValue());
				
				if(node.selectSingleNode("workspace") != null)
					change.setWorkspace(node.selectSingleNode("workspace").getStringValue());
				
				if(node.selectSingleNode("date") != null)
					change.setDate(node.selectSingleNode("date").getStringValue());
				
				List<Node> fileNodes = node.selectSingleNode("files").selectNodes("file");
				List<Changelist.FileEntry> files = new ArrayList<Changelist.FileEntry>();
				for(Node fnode : fileNodes) {
					Changelist.FileEntry file = new Changelist.FileEntry();
					file.setFilename(fnode.selectSingleNode("name").getStringValue());
					file.setRevision(fnode.selectSingleNode("rev").getStringValue());
					file.setAction(Changelist.FileEntry.Action.valueOf(fnode.selectSingleNode("action").getStringValue()));
					files.add(file);
				}
				change.setFiles(files);
				
				List<Node> jobNodes = node.selectSingleNode("jobs").selectNodes("job");
				List<Changelist.JobEntry> jobs = new ArrayList<Changelist.JobEntry>();
				for(Node jnode : jobNodes) {
					Changelist.JobEntry job = new Changelist.JobEntry();
					job.setJob(jnode.selectSingleNode("name").getStringValue());
					job.setDescription(jnode.selectSingleNode("description").getStringValue());
					job.setStatus(jnode.selectSingleNode("status").getStringValue());
					jobs.add(job);
				}
				change.setJobs(jobs);
				
				PerforceChangeLogEntry entry = new PerforceChangeLogEntry();
				entry.setChange(change);
				history.add(entry);
			}
		} catch(Exception e) {
			throw new IOException("Failed to parse changelog file.", e);
		}

		return new PerforceChangeLogSet(build, history);
	}
	
	/**
	 * Stores the history objects to the output stream as xml
	 * 
	 * @param outputStream the stream to write to
	 * @param history the history objects to store
	 * @throws IOException
	 */
	public static void saveToChangeLog(OutputStream outputStream, List<Changelist> changes) throws IOException {
		PrintStream stream = new PrintStream(outputStream);
		System.out.println("Writing " + changes.size() + " chagnes to log");
		stream.println("<changelog>");
		for(Changelist change : changes) {
			System.out.println("Change: " + change.getChangeNumber());
			stream.println("\t<entry>");
			stream.println("\t\t<changenumber>" + change.getChangeNumber() + "</changenumber>");
			stream.println("\t\t<date>" + change.getDate() + "</date>");
			stream.println("\t\t<description>" + change.getDescription() + "</description>");
			stream.println("\t\t<user>" + change.getUser() + "</user>");
			stream.println("\t\t<workspace>" + change.getWorkspace() + "</workspace>");
			stream.println("\t\t<files>");
			for(Changelist.FileEntry entry : change.getFiles()) {
				stream.println("\t\t\t<file>");
				stream.println("\t\t\t\t<name>" + entry.getFilename() + "</name>");
				stream.println("\t\t\t\t<rev>" + entry.getRevision() + "</rev>");
				stream.println("\t\t\t\t<action>" + entry.getAction() + "</action>");
				stream.println("\t\t\t</file>");
			}
			stream.println("\t\t</files>");
			stream.println("\t\t<jobs>");
			for(Changelist.JobEntry entry : change.getJobs()) {
				stream.println("\t\t\t<job>");
				stream.println("\t\t\t\t<name>" + entry.getJob() + "</name>");
				stream.println("\t\t\t\t<description>" + entry.getDescription() + "</description>");
				stream.println("\t\t\t\t<status>" + entry.getStatus() + "</status>");
				stream.println("\t\t\t</job>");
			}
			stream.println("\t\t</jobs>");			
			stream.println("\t</entry>");
		}
		stream.println("</changelog>");
		stream.close();
	}

}
