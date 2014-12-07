package hudson.plugins.perforce.browsers;

import com.tek42.perforce.model.Changelist;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.plugins.perforce.PerforceChangeLogEntry;
import hudson.plugins.perforce.PerforceRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import java.io.IOException;
import java.net.URL;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link RepositoryBrowser} for Perforce's Perfbrowse.
 * 
 * @author Nick James
 */
public class Perfbrowse extends PerforceRepositoryBrowser {
    
   /**
    * The URL of the Perfbrowse server script, e.g.
    * <tt>http://perforce/perfbrowse.cgi</tt>
    */
    public final URL url;
    
    public final String p4Describe = "?@describe+";
    public final String p4FileLog  = "?@filelog+";
    public final String p4Diff     = "?@diff+";

    @DataBoundConstructor
    public Perfbrowse(URL url) {
	
        this.url = url;
    }

    @Override
    public URL getDiffLink(Changelist.FileEntry file) throws IOException {
        if(file.getAction() != Changelist.FileEntry.Action.EDIT && file.getAction() != Changelist.FileEntry.Action.INTEGRATE)
            return null;
        int r = new Integer(file.getRevision());
        if(r <= 1)
            return null;
        return new URL(url + p4Diff + file.getFilename() + "+" + r + "+" + file.getAction());
    }

    @Override
    public URL getFileLink(Changelist.FileEntry file) throws IOException {
        return new URL(url + p4FileLog + file.getFilename());
    }

    @Override
    public URL getChangeSetLink(PerforceChangeLogEntry change) throws IOException {
        return new URL(url + p4Describe + change.getChangeNumber());
    }
    
    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

        @Override
        public String getDisplayName() {
            return "Perfbrowse";
        }

        public FormValidation doCheck(@QueryParameter final String value) throws IOException, ServletException {
            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    String url = Util.fixEmpty(value);
                    if (url == null) { // nothing entered yet
                        return FormValidation.ok();
                    }
                    if(!url.startsWith("http://") && !url.startsWith("https://")) {
                        return FormValidation.errorWithMarkup("The URL should contain <tt>http://</tt> or <tt>https://</tt>");
                    }
                    if (!url.endsWith(".cgi")) {
                        return FormValidation.errorWithMarkup("The URL should end with a CGI script, usually <tt>perfbrowse.cgi</tt>");
                    }                    
                    return FormValidation.ok();
                }
            }.check();
        }

        @Override
        public Perfbrowse newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindParameters(Perfbrowse.class, "perfbrowse.");
        }
    }

    private static final long serialVersionUID = 1L;
    
}
