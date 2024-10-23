<#ftl auto_esc=false>
<#include "OtpUserContainer.ftl">

<#--
    This is a template for an HTML email that gets sent when a dependent user is requesting a trusted companion.
-->

<#macro EmailMain>
    <div>
        <h1>${emailGreeting}</h1>
        <p><a href="${acceptDependentUrl}">${acceptDependentLinkAnchorLabel}</a></p>
    </div>
</#macro>

<@HtmlEmail/>