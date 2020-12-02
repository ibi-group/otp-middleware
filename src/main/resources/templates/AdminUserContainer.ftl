<#include "BaseEmail.ftl">

<#macro email_main>
<#-- Provide an error message in the default case. If this hasn't been overwritten by an inheriting
        component, then an error has occurred. -->
    The main content of this email hasn't been configured! Sorry about that! Please contact the webmaster.
</#macro>

<#macro email_body>
<#-- header -->
<div>
    <h2>An email from the ${OTP_ADMIN_DASHBOARD_NAME}</h2>
</div>
<#-- main -->
<div>
    <@email_main/>
</div>
<#-- footer -->
<div>
    <p>
        <small>
            Manage subscriptions <a href="${OTP_ADMIN_DASHBOARD_URL}/account">here</a>.
        </small>
    </p>
</div>
</#macro>

