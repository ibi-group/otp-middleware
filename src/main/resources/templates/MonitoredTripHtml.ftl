<#include "OtpUserContainer.ftl">

<#--
    This is a template for an HTML email that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->

<#macro EmailMain>
    <div>
        <p>Your trip has the following notifications:</p>
        <ul>
        <#list notifications as notification>
          <li>${notification}</li>
        </#list>
        </ul>
        <p>View trip <a href="${OTP_UI_URL}/#/savedtrips/${tripId}">here</a>.
    </div>
</#macro>

<@HtmlEmail/>