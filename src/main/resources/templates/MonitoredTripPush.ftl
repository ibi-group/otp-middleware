<#--
    This is a template for push notifications content for notifications about an
    OTP user's monitored trip.
    Note the following character limitations by mobile OS:
    - iOS: 178 characters over up to 4 lines,
    - Android: 240 characters (We are not using notification title at this time).
    The max length is thus 178 characters.
    - List alerts with bullets if there are more than one of them.
-->
<#if notifications?size gt 1>
<#list notifications as notification>
• ${notification}
</#list>
<#else>
<#list notifications as notification>
${notification}
</#list>
</#if>