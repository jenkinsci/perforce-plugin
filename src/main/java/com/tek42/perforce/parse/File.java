package com.tek42.perforce.parse;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;

public class File extends AbstractPerforceTemplate
{
    String file;

    public File(Depot depot, String file) {
        super(depot);
        this.file = file;
    }

    public boolean exists() throws PerforceException {
        StringBuilder sb = getPerforceResponse(new String[] { getP4Exe(), "fstat", "-m", "1", file });
        if(sb.indexOf("no such file(s).") > 0)
            return false;
        return true;
    }

    public String read() throws PerforceException {
        StringBuilder sb = getPerforceResponse(new String[] { getP4Exe(), "print", "-q", file });
        return sb.toString();
    }
}
