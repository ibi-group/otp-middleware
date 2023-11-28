<#include "OtpUserContainer.ftl">

<#--
    This is a template for an HTML email that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->

<#macro EmailMain>
    <div>
        <h1>Your trip has the following notifications:</h1>

        <#if notification.type == "INITIAL_REMINDER">
            <p>${notification.body}</p>
        </#if>

        <ul>
            <#list notifications as notification>
                <#if notification.type != "INITIAL_REMINDER">
                    <li>${notification.body}</li>
                </#if>
            </#list>
        </ul>

        <p>View all of your saved trips in <a href="${OTP_UI_URL}${TRIPS_PATH}/${tripId}">${OTP_UI_NAME}</a>.</p>
    </div>
</#macro>

<@HtmlEmail/>