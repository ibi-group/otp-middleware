<#--
    This is a template for an SMS text that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->
${tripNameOrReminder}
<#if notifications?size gt 1>
<#list notifications as notification>
• ${notification.body}
</#list>
<#else>
<#list notifications as notification>
${notification.body}
</#list>
</#if>

View trip: ${OTP_UI_URL}${TRIPS_PATH}/${tripId}

To stop receiving notifications, reply STOP.