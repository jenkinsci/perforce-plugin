package hudson.plugins.perforce;

import java.io.*;

import org.xml.sax.SAXException;

import hudson.model.AbstractBuild;
import hudson.scm.*;
import hudson.scm.ChangeLogSet.Entry;

public class PerforceChangeLogParser extends ChangeLogParser {

	/* (non-Javadoc)
	 * @see hudson.scm.ChangeLogParser#parse(hudson.model.AbstractBuild, java.io.File)
	 */
	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File file) throws IOException, SAXException {
		return PerforceChangeLogSet.parse(build, new FileInputStream(file));
	}
}
