package hudson.plugins.perforce.browsers;

import static hudson.Util.fixEmpty;
import hudson.model.Descriptor;
import hudson.plugins.perforce.*;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormFieldValidator;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import com.tek42.perforce.model.Changelist;

/**
 * {@link RepositoryBrowser} for Perforce's P4Web.
 * 
 * @author Mike Wille
 */
public class P4Web extends PerforceRepositoryBrowser {
    /**
	 * The URL of the P4Web server.
	 * 
	 * This is normally like <tt>http://perforce.com:5000/</tt> Normalized to
	 * have '/' at the tail.
	 */
    public final URL url;

    public final String p4WebBegShite = "@md=d&rc=s&rt=s&thb=y&c=ihy@";
    // in between goes the path we are looking for...
    public final String p4WebEndShite = "?ac=22";
    public final String p4DifEndShite = "?ac=19&";
    
    @DataBoundConstructor
    public P4Web(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }

    @Override
    public URL getDiffLink(Changelist.FileEntry file) throws IOException {
        if(file.getAction() != Changelist.FileEntry.Action.EDIT && file.getAction() != Changelist.FileEntry.Action.INTEGRATE)
        	return null;
        int r = new Integer(file.getRevision());
        if(r <= 1)
        	return null;
        return new URL(url, p4WebBegShite + file.getFilename() + p4DifEndShite + "&rev1=" + (r - 1) + "&rev2=" + (r));
    }

    @Override
    public URL getFileLink(Changelist.FileEntry file) throws IOException {
        return new URL(url, p4WebBegShite + file.getFilename() + p4WebEndShite);
    }

    /**
     * P4 Web doesn't have a pretty view for changelists.  In fact, the one implemented
     * for hudson is more informative and better looking.  Sheesh!
     */
    @Override
    public URL getChangeSetLink(PerforceChangeLogEntry changeSet) throws IOException {
    	// Like the man says, we ain't showing you jack!
        return null;
    }
    
    public URL getURL() {
    	return url;
    }
    
    public Descriptor<RepositoryBrowser<?>> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public DescriptorImpl() {
            super(P4Web.class);
        }

        public String getDisplayName() {
            return "P4Web";
        }

        /**
		 * Performs on-the-fly validation of the URL.
		 */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.URLCheck(req,rsp) {
                protected void check() throws IOException, ServletException {
                    String host = fixEmpty(request.getParameter("url"));
                    String user = fixEmpty(request.getParameter("user"));
                    String pass = fixEmpty(request.getParameter("pass"));
                    if(host==null) {// nothing entered yet
                        ok();
                        return;
                    }

                    if(!host.endsWith("/")) 
                    	host += '/';
                    
                    if(!host.startsWith("http://")) {
                        errorWithMarkup("The URL should contain <tt>http://</tt>");
                        return;
                    }
                    /**
                     * We need a base64 encoder in order to do authentication.
                     * 
                    String login = user + ":" + pass;
    				String encodedLogin = new BASE64Encoder().encodeBuffer(login.getBytes());
            		
            		URL url = new URL(host);
            		HttpURLConnection con = (HttpURLConnection) url.openConnection();
            			
            		if(encodedLogin != null)
            			con.setRequestProperty("Authorization", "Basic " + encodedLogin);
            		
            		// really our only other option we care about.
            		con.setInstanceFollowRedirects(true);
                    
                    try {
                        if(findText(open(con), "P4Web")) {
                            ok();
                        } else {
                            error("This is a valid URL but it doesn't look like P4Web is running");
                        }
                    } catch (IOException e) {
                        handleIOException(host, e);
                    }
                    */
                }
            }.process();
        }
  
        public P4Web newInstance(StaplerRequest req) throws FormException {
            return req.bindParameters(P4Web.class,"p4web.");
        }
    }

    private static final long serialVersionUID = 1L;
}
