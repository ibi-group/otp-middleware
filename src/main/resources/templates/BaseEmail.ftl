<#--
    This is the root template for all emails. Stuff like overall styling should be put in here. Any
    emails that use this must implement a macro called `EmailBody` and the whole html email will
    eventually get rendered by the statement <@HtmlEmail/>.
-->
<style>
    body { 
        padding: 1em;
    }

    h1 {
        font-size: 18px;
        font-weight: 600;
    }

    small {
        color: #515151
    }
</style>

<#macro HtmlEmail>
<body>
    <div>
        <@EmailBody/>
    </div>
</body>
</#macro>