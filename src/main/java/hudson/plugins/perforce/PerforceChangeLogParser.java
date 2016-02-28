package hudson.plugins.perforce;

import com.tek42.perforce.model.Changelist;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PerforceChangeLogParser extends ChangeLogParser {

    /*
     * @see hudson.scm.ChangeLogParser#parse(hudson.model.AbstractBuild, java.io.File)
     */
    @Override
    public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File file) throws IOException, SAXException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            ChangeLogHandler handler = new ChangeLogHandler(build);
            parser.parse(file,handler);
            return handler.getChangeLogSet();
        } catch (Exception e) {
            throw new SAXException("Could not parse perforce changelog: ",e);
        }
    }
    
    public static class ChangeLogHandler extends DefaultHandler {
        private Stack objects = new Stack();
        private StringBuffer text = new StringBuffer();

        private List<PerforceChangeLogEntry> changeLogEntries = null;
        private PerforceChangeLogSet changeLogSet = null;
        private AbstractBuild build = null;
        
        public ChangeLogHandler(AbstractBuild build) {
            this.build = build;
        }
        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            text.append(ch, start, length);
        }

        @Override
        public void endDocument() throws SAXException {
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.equalsIgnoreCase("changelog")) {
                //this is the root, so don't do anything
                return;
            }
            if (objects.peek() instanceof Changelist) {
                Changelist changelist = (Changelist) objects.peek();
                if (qName.equalsIgnoreCase("changenumber")) {
                    changelist.setChangeNumber(new Integer(text.toString()));
                    return;
                }
                if (qName.equalsIgnoreCase("date")) {
                    changelist.setDate(stringDateToJavaDate(text.toString()));
                    return;
                }
                if (qName.equalsIgnoreCase("description")) {
                    changelist.setDescription(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("user")) {
                    changelist.setUser(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("workspace")) {
                    changelist.setWorkspace(text.toString());
                    return;
                }
            }
            if (objects.peek() instanceof Changelist.JobEntry) {
                Changelist.JobEntry job = (Changelist.JobEntry) objects.peek();
                if (qName.equalsIgnoreCase("name")) {
                    job.setJob(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("description")) {
                    job.setDescription(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("status")) {
                    job.setStatus(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("job")) {
                    objects.pop();
                    List joblist = (List) objects.peek();
                    joblist.add(job);
                    return;
                }
            }
            if (objects.peek() instanceof Changelist.FileEntry) {
                Changelist.FileEntry file = (Changelist.FileEntry) objects.peek();
                if (qName.equalsIgnoreCase("name")) {
                    file.setFilename(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("workspacePath")) {
                    file.setWorkspacePath(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("action")) {
                    file.setAction(Changelist.FileEntry.Action.valueOf(text.toString()));
                    return;
                }
                if (qName.equalsIgnoreCase("rev")) {
                    file.setRevision(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("changenumber")) {
                    file.setChangenumber(text.toString());
                    return;
                }
                if (qName.equalsIgnoreCase("file")) {
                    objects.pop();
                    List filelist = (List) objects.peek();
                    filelist.add(file);
                    return;
                }
            }
            if (qName.equalsIgnoreCase("files")) {
                ArrayList<Changelist.FileEntry> files = (ArrayList<Changelist.FileEntry>) objects.pop();
                Changelist changelist = (Changelist) objects.peek();
                changelist.setFiles(files);
                return;
            }
            if (qName.equalsIgnoreCase("jobs")) {
                ArrayList<Changelist.JobEntry> jobs = (ArrayList<Changelist.JobEntry>) objects.pop();
                Changelist changelist = (Changelist) objects.peek();
                changelist.setJobs(jobs);
                return;
            }
            if (qName.equalsIgnoreCase("entry")) {
                Changelist changelist = (Changelist) objects.pop();
                PerforceChangeLogEntry entry = new PerforceChangeLogEntry(changeLogSet);
                entry.setChange(changelist);
                changeLogEntries.add(entry);
                return;
            }
        }

        @Override
        public void startDocument() throws SAXException {
            changeLogEntries = new ArrayList<PerforceChangeLogEntry>();
            changeLogSet = new PerforceChangeLogSet(build, changeLogEntries);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            text.setLength(0);

            if (qName.equalsIgnoreCase("changelog")) {
                //this is the root, so don't do anything
                return;
            }
            if (qName.equalsIgnoreCase("entry")) {
                objects.push(new Changelist());
                return;
            }
            if (objects.peek() instanceof Changelist) {
                if (qName.equalsIgnoreCase("files")) {
                    objects.push(new ArrayList<Changelist.FileEntry>());
                    return;
                }
                if (qName.equalsIgnoreCase("jobs")) {
                    objects.push(new ArrayList<Changelist.JobEntry>());
                    return;
                }
            }
            if (qName.equalsIgnoreCase("job")) {
                objects.push(new Changelist.JobEntry());
                return;
            }
            if (qName.equalsIgnoreCase("file")) {
                objects.push(new Changelist.FileEntry());
                return;
            }
        }
        
        public PerforceChangeLogSet getChangeLogSet() {
            return changeLogSet;
        }
    }
    
        /**
     * This takes a java.util.Date and converts it to a string.
     *
     * @return A string representation of the date
     */
    public static String javaDateToStringDate(java.util.Date newDate) {
        if (newDate == null)
            return "";

        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
        cal.clear();
        cal.setTime(newDate);

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);

        String date = year + "-" + putZero(month) + "-" + putZero(day);
        if (hour + min + sec > 0)
            date += " " + putZero(hour) + ":" + putZero(min) + ":" + putZero(sec);

        return date;
    }

    /**
     * Returns a java.util.Date object set to the time specified in newDate. The
     * format expected is the format of: YYYY-MM-DD HH:MM:SS
     *
     * @param newDate
     *            the string date to convert
     * @return A java.util.Date based off of the string format.
     */
    public static java.util.Date stringDateToJavaDate(String newDate) {
        // when we have a null from the database, give it zeros first.
        if (newDate == null || newDate.equals("")) {
            return null;
        }

        String[] parts = newDate.split(" ");
        String[] date = parts[0].split("-");
        String[] time = null;

        if (parts.length > 1) {
            time = parts[1].split(":");
            time[2] = time[2].replaceAll("\\.0", "");
        } else {
            time = "00:00:00".split(":");
        }

        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();
        cal.clear();

        cal.set(Integer.parseInt(date[0]), (Integer.parseInt(date[1]) - 1),
                Integer.parseInt(date[2]), Integer.parseInt(time[0]), Integer.parseInt(time[1]),
                Integer.parseInt(time[2]));

        return cal.getTime();
    }

    public static String putZero(int i) {
        if (i < 10) {
            return "0" + i;
        }
        return i + "";
    }
}