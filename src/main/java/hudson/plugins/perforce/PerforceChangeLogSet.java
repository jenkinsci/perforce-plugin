package hudson.plugins.perforce;

import com.tek42.perforce.model.Changelist;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * @author Mike Wille
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

    /*
     * @see hudson.scm.ChangeLogSet#isEmptySet()
     */
    @Override
    public boolean isEmptySet() {
        return history.isEmpty();
    }

    public Collection<PerforceChangeLogEntry> getLogs() {
        return history;
    }

    /*
     * @see java.lang.Iterable#iterator()
     */
    public Iterator<PerforceChangeLogEntry> iterator() {
        return history.iterator();
    }

    /**
     * Stores the history objects to the output stream as xml
     *
     * @param outputStream
     *            the stream to write to
     * @param changes
     *            the history objects to store
     * @throws IOException
     */
    public static void saveToChangeLog(OutputStream outputStream, List<Changelist> changes) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, Charset.forName("UTF-8"));
        WriterOutputStream stream1 = new WriterOutputStream(writer);
        PrintStream stream = new PrintStream(stream1);

        stream.println("<?xml version='1.0' encoding='UTF-8'?>");
        stream.println("<changelog>");
        for (Changelist change : changes) {
            stream.println("\t<entry>");
            stream.println("\t\t<changenumber>" + change.getChangeNumber() + "</changenumber>");
            stream.println("\t\t<date>" + Util.xmlEscape(PerforceChangeLogParser.javaDateToStringDate(change.getDate())) + "</date>");
            stream.println("\t\t<description>" + Util.xmlEscape(change.getDescription()) + "</description>");
            stream.println("\t\t<user>" + Util.xmlEscape(change.getUser()) + "</user>");
            stream.println("\t\t<workspace>" + Util.xmlEscape(change.getWorkspace()) + "</workspace>");
            stream.println("\t\t<files>");
            for (Changelist.FileEntry entry : change.getFiles()) {
                stream.println("\t\t\t<file>");
                stream.println("\t\t\t\t<name>" + Util.xmlEscape(entry.getFilename()) + "</name>");
                stream.println("\t\t\t\t<workspacePath>" + Util.xmlEscape(entry.getWorkspacePath()) + "</workspacePath>");
                stream.println("\t\t\t\t<rev>" + Util.xmlEscape(entry.getRevision()) + "</rev>");
                stream.println("\t\t\t\t<changenumber>" + Util.xmlEscape(entry.getChangenumber()) + "</changenumber>");
                stream.println("\t\t\t\t<action>" + entry.getAction() + "</action>");
                stream.println("\t\t\t</file>");
            }
            stream.println("\t\t</files>");
            stream.println("\t\t<jobs>");
            for (Changelist.JobEntry entry : change.getJobs()) {
                stream.println("\t\t\t<job>");
                stream.println("\t\t\t\t<name>" + Util.xmlEscape(entry.getJob()) + "</name>");
                stream.println("\t\t\t\t<description>" + Util.xmlEscape(entry.getDescription()) + "</description>");
                stream.println("\t\t\t\t<status>" + Util.xmlEscape(entry.getStatus()) + "</status>");
                stream.println("\t\t\t</job>");
            }
            stream.println("\t\t</jobs>");
            stream.println("\t</entry>");
        }
        stream.println("</changelog>");
        stream.close();
    }
}