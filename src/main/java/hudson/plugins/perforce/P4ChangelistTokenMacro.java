package hudson.plugins.perforce;

import hudson.Extension;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;
import java.util.Map;

/**
* {@code P4_CHANGELIST} token that expands to the Perforce changelist of the commit that was built.
*
* @author Jeremy Hayes
*/
@Extension
public class P4ChangelistTokenMacro extends DataBoundTokenMacro {

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("P4_CHANGELIST");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {
//        Map<String, String> env = context.getBuildVariables();
        Map<String, String> env = context.getEnvironment(listener);
        if (env.containsKey("P4_CHANGELIST")) {
            return env.get("P4_CHANGELIST");
        }
        return "-1";
    }
}

