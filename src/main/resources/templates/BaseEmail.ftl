<#macro email_body>
<#-- Provide an error message in the default case. If this hasn't been overwritten by an inhereting
        component, then an error has occurred. -->
The contents of this email haven't been configured! Sorry about that! Please contact the webmaster.
</#macro>

<#macro html_email>
<div>
    <@email_body/>
</div>
</#macro>