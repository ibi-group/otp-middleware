<#ftl auto_esc=false>
<#include "OtpUserContainer.ftl">

<#--
    This is a template for an HTML email that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->

<#macro EmailMain>
    <div>
        <style>
            ul.alerts {
                padding: 0;
            }
            ul.alerts li {
                background-color: #ddd;
                list-style-type: none;
                margin-bottom: 10px;
                padding: 5px 5px 5px 2em;
                text-indent: -1.5em;
            }
            ul.alerts.resolved li {
                background-color: #99ddff;
            }
        </style>
        <h1>${emailGreeting}</h1>

        <#if initialReminder??>
            <p>${initialReminder.body}</p>
        </#if>

        <ul>
            <#list notifications as notification>
                <#if notification.type == "ALERT_FOUND">
                    <#if notification.newAlertsNotification??>
                        <li>${notification.newAlertsNotification.body}
                            <ul class="alerts">
                                <#list notification.newAlertsNotification.alerts as alert>
                                    <li>
                                        <strong>${notification.newAlertsNotification.icon} ${alert.alertHeaderText}</strong><br/>
                                        ${alert.alertDescriptionForHtml}
                                    </li>
                                </#list>
                            </ul>
                        </li>
                    </#if>
                    <#if notification.resolvedAlertsNotification??>
                        <li>${notification.resolvedAlertsNotification.body}
                            <ul class="alerts resolved">
                                <#list notification.resolvedAlertsNotification.alerts as alert>
                                    <li>
                                        <strong>${notification.resolvedAlertsNotification.icon} (RESOLVED) ${alert.alertHeaderText}</strong><br/>
                                        ${alert.alertDescriptionForHtml}
                                    </li>
                                </#list>
                            </ul>
                        </li>
                    </#if>
                </#if>
                <#if notification.type != "INITIAL_REMINDER" && notification.type != "ALERT_FOUND">
                    <li>${notification.body}</li>
                </#if>
            </#list>
        </ul>

        <p><a href="${tripUrl}">${tripLinkAnchorLabel}</a></p>
    </div>
</#macro>

<@HtmlEmail/>