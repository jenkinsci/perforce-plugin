package hudson.plugins.perforce.browsers;

import hudson.Util;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.*;
import hudson.util.FormValidation;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.tek42.perforce.model.Changelist;
import hudson.plugins.perforce.*;

/**
 * Repository browser for Perforce in a FishEye server.
 */
public final class FishEyePerforce extends PerforceRepositoryBrowser {

    /**
     * The URL of the FishEye repository, e.g.
     * <tt>http://deadlock.netbeans.org/fisheye/browse/netbeans/</tt>
     */
    public final URL url;

    @DataBoundConstructor
    public FishEyePerforce(URL url) {
        this.url = normalizeToEndWithSlash(url);
    }

    @Override
    public URL getDiffLink(Changelist.FileEntry file) throws IOException {
    	if(file.getAction() != Changelist.FileEntry.Action.EDIT && file.getAction() != Changelist.FileEntry.Action.INTEGRATE)
        	return null;
        int r = new Integer(file.getRevision());
        if(r <= 1)
        	return null;
        return new URL(url, trimHeadSlash(file.getFilename()) + new QueryBuilder(url.getQuery()).add("r1=" + (r-1)).add("r2=" + r));
    }

    @Override
    public URL getFileLink(Changelist.FileEntry file) throws IOException {
        return new URL(url, trimHeadSlash(file.getFilename()) + new QueryBuilder(url.getQuery()));
    }

    @Override
    public URL getChangeSetLink(PerforceChangeLogEntry change) throws IOException {
        return new URL(url,"../../changelog/"+getProjectName()+"/?cs="+change.getChange().getChangeNumber());
    }

    /**
     * Pick up "FOOBAR" from "http://site/browse/FOOBAR/"
     */
    private String getProjectName() {
        String p = url.getPath();
        if(p.endsWith("/")) p = p.substring(0,p.length()-1);

        int idx = p.lastIndexOf('/');
        return p.substring(idx+1);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

        @Override
        public String getDisplayName() {
            return "FishEye";
        }

        public FormValidation doCheck(@QueryParameter final String value) throws IOException, ServletException {
            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    String url = Util.fixEmpty(value);
                    if (url == null) {
                        return FormValidation.ok();
                    }
                    if (!url.endsWith("/")) {
                        url += '/';
                    }
                    if (!URL_PATTERN.matcher(url).matches()) {
                        return FormValidation.errorWithMarkup("The URL should end like <tt>.../browse/foobar/</tt>");
                    }
                    try {
                        if (!findText(open(new URL(url)), "FishEye")) {
                            return FormValidation.error("This is a valid URL but it doesn't look like FishEye");
                        }
                    } catch (IOException e) {
                        handleIOException(url, e);
                    }
                    return FormValidation.ok();
                }
            }.check();
        }

        @Override
        public FishEyePerforce newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindParameters(FishEyePerforce.class, "fisheye.perforce.");
        }

        private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");

    }

    private static final long serialVersionUID = 1L;

}
