<#include "BaseEmail.ftl">

<#--
    This is a template for all emails related to the OTP end-user application. Any templates that
    use this template must implement a macro called `EmailMain`. The whole HTML email can then be
    rendered by the statement <@HtmlEmail/>.
-->

<#macro EmailBody>
<#-- main -->
<div>
    <@EmailMain/>
</div>
<#-- footer -->
<hr/>
<div>
    <p>
        <small>
            You're receiving this email because you're subscribed to notifications through ${OTP_UI_NAME}. You can manage that subscription <a href="${OTP_UI_URL}/#/account">here</a>.
        </small>
    </p>
</div>
</#macro>

