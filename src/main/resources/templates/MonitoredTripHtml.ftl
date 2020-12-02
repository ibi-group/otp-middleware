<#include "OtpUserContainer.ftl">

<#macro email_main>
    <div>
        <h2></h2>
        <p>Your trip has the following notifications:</p>
        <ul>
        <#list notifications as notification>
          <li>${notification}</li>
        </#list>
        </ul>
        <p>View trip <a href="${OTP_UI_URL}/#/savedtrips/${tripId}">here</a>.
    </div>
</#macro>

<@html_email/>