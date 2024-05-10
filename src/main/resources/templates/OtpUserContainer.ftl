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
<footer>
    <p>
        <small>
            ${emailFooter}
            <br/>
            <a href="${manageLinkUrl}">${manageLinkText}</a>
        </small>
    </p>
</footer>
</#macro>

