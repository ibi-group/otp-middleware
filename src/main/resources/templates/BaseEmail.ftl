<#--
    This is the root template for all emails. Stuff like overall styling should be put in here. Any
    emails that use this must implement a macro called `email_body` and the whole html email will
    eventually get rendered by the statement <@html_email/>.
-->

<#macro html_email>
<div>
    <@email_body/>
</div>
</#macro>