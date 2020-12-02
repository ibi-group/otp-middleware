<#include "AdminUserContainer.ftl">

<#macro email_main>
    <div>
        <h3>${subject}</h3>
        <p>
            Visit the
            <a href="${OTP_ADMIN_DASHBOARD_URL}?dashboard=errors">${OTP_ADMIN_DASHBOARD_NAME}</a>
            to view new errors.
        </p>
    </div>
</#macro>

<@html_email/>