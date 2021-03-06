<#--
    This is a template for an SMS text that gets sent when there are new notifications about an
    OTP user's monitored trip.
-->

Your trip has the following notifications:
<#list notifications as notification>
  -${notification}
</#list>

View trip: ${OTP_UI_URL}${TRIPS_PATH}/${tripId}

To stop receiving notifications, reply STOP.