package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * This API response element represents an error in trip planning.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlannerError {

    public int id;
    public String msg;
    private List<String> missing = null;
    private boolean noPath = false;

    /** An error where no path has been found, but no points are missing */
    public PlannerError() {
        noPath = true;
    }

    public PlannerError(Exception e) {
        this();
    }

    public PlannerError(boolean np) {
        noPath = np;
    }

    public PlannerError(List<String> missing) {
        this.setMissing(missing);
    }

    public PlannerError(int id, String msg) {
        this.id  = id;
        this.msg = msg;
    }

    /**
     * @param missing the list of point names which cannot be found (from, to, intermediate.n)
     */
    public void setMissing(List<String> missing) {
        this.missing = missing;
    }

    /**
     * @return the list of point names which cannot be found (from, to, intermediate.n)
     */
    public List<String> getMissing() {
        return missing;
    }

    /**
     * @param noPath whether no path has been found
     */
    public void setNoPath(boolean noPath) {
        this.noPath = noPath;
    }

    /**
     * @return whether no path has been found
     */
    public boolean getNoPath() {
        return noPath;
    }

    @Override
    public String toString() {
        return "PlannerError{" +
                "id=" + id +
                ", msg='" + msg + '\'' +
                ", missing=" + missing +
                ", noPath=" + noPath +
                '}';
    }
}
