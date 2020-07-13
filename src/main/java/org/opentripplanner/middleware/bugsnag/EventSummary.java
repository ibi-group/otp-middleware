package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.response.EventException;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagProject;

import java.util.Date;
import java.util.List;

/**
 * @schema EventSummary
 * description: "Summary information for events logged in Bugsnag. For additional information: https://bugsnagapiv2.docs.apiary.io/#reference/organizations/event-data-requests/create-an-event-data-requestData structure for log response. The data is based on https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/AmazonWebServiceResult.html, with items having the fields outlined in https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/apigateway/model/GetUsageResult.html."
 * type: object
 * properties:
 *   errorId:
 *     type: string
 *     description: The error which this event is associated with.
 *   exceptions:
 *     type: array
 *     items:
 *       type: object # TODO - to implement.
 *     description: A list of EventException classes and messages associated with this event.
 *   received:
 *     type: string
 *     # TODO - format:
 *     description: The date/time Bugsnag received the event.
 *   projectId:
 *     type: string
 *     description: The project which this event is associated with.
 *   projectName:
 *     type: string
 *     description: The associated project name.
 *   releaseState:
 *     type: string
 *     description: Associated environment e.g. Test, Dev, Production.
 *   htmlUrl:
 *     type: string
 *     description: The dashboard URL for the project.
 */

/**
 * Event summary information provided to calling services. Response is based on static project and dynamic event
 * information. Additional information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/event-data-requests/create-an-event-data-request
 */
public class EventSummary {

    /** The error which this event is associated with */
    public String errorId;

    /** A list of {@link EventException) classes and messages associated with this event */
    public List<EventException> exceptions;

    /** The date/time Bugsnag received the event */
    public Date received;

    /** The project which this event is associated with */
    public String projectId = "-1";

    /** The associated project name */
    public String projectName = "Unknown";

    /** Associated environment e.g. Test, Dev, Production */
    public String releaseStage;

    /** The dashboard URL for the project */
    public String htmlUrl = "Unknown";


    public EventSummary(BugsnagProject project, BugsnagEvent bugsnagEvent) {
        if (project != null) {
            this.projectName = project.name;
            this.projectId = project.id;
            this.htmlUrl = project.htmlUrl;
        }
        this.errorId = bugsnagEvent.id;
        this.exceptions = bugsnagEvent.exceptions;
        this.received = bugsnagEvent.receivedAt;
        this.releaseStage = bugsnagEvent.app.releaseStage;
    }
}
