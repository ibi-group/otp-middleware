<#--
    This is the root template for all emails. Stuff like overall styling should be put in here. Any
    emails that use this must implement a macro called `EmailBody` and the whole html email will
    eventually get rendered by the statement <@HtmlEmail/>.
-->

<#macro HtmlEmail>
<div>
    <@EmailBody/>
</div>
</#macro>