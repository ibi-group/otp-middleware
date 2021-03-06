<#include "BaseEmail.ftl">

<#--
    This is a template for all emails related to the OTP end-user application. Any templates that
    use this template must implement a macro called `EmailMain`. The whole HTML email can then be
    rendered by the statement <@HtmlEmail/>.
-->

<#macro EmailBody>
<#-- header -->
<div>
    <h2>An email from the ${OTP_UI_NAME}</h2>
</div>
<#-- main -->
<div>
    <@EmailMain/>
</div>
<#-- footer -->
<div>
    <p>
        <small>
            Manage Subscriptions <a href="${OTP_UI_URL}/#/account">here</a>.
        </small>
    </p>
</div>
</#macro>

