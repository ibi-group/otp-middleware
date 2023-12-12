<#--
    This is a template for a text email that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->

Your trip has the following notifications:
<#if notifications?size gt 1>
  <#list notifications as notification>
    • ${notification}
  </#list>
<#else>
  <#list notifications as notification>
    ${notification}
  </#list>
</#if>

View trip: ${OTP_UI_URL}${TRIPS_PATH}/${tripId}