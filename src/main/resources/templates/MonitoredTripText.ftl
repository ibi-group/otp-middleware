<#--
    This is a template for a text email that gets sent when there are new notifications about an
    OTP user's monitored trip.

    Note: in plain text emails, all whitespace is preserved,
    so the indentation of the notification content is intentionally not aligned
    with the indentation of the macros.

-->

Your trip has the following notifications:

<#list notifications as notification>
  <#if notification.type == "INITIAL_REMINDER">
${notification.body}
  </#if>
</#list>

<#list notifications as notification>
    <#if notification.type == "ALERT_FOUND">
        <#if notification.newAlertsNotification??>
• ${notification.newAlertsNotification.body}

            <#list notification.newAlertsNotification.alerts as alert>
    ${notification.newAlertsNotification.icon} ${alert.alertHeaderText}

${alert.alertDescriptionText}

            </#list>
        </#if>
        <#if notification.resolvedAlertsNotification??>
• ${notification.resolvedAlertsNotification.body}

            <#list notification.resolvedAlertsNotification.alerts as alert>
    ${notification.resolvedAlertsNotification.icon} (RESOLVED) ${alert.alertHeaderText}

${alert.alertDescriptionText}

            </#list>
        </#if>
    </#if>
    <#if notification.type != "INITIAL_REMINDER" && notification.type != "ALERT_FOUND">
• ${notification.body}

    </#if>
</#list>

View trip: ${OTP_UI_URL}${TRIPS_PATH}/${tripId}