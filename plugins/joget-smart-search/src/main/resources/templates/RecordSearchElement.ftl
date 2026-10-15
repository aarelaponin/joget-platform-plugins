<#-- The search of a record the element's properties name (RecordSearchSettings, 8.2.0): a record
     found by what is typed — exactly in one column, or as a fragment of another — chosen from a
     result that shows the columns the properties name, filling and locking the fields they name.
     The page asks RecordSearchWebService with the settings this server sealed (token, nonce). -->
<div class="form-cell rss-form-cell" ${elementMetaData!}>
    <label class="label">
        ${element.properties.label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    <div class="form-cell-value rss-box" id="${elementId!}">
        <input type="hidden" id="${elementId!}_value" name="${elementParamName!}" value="${value!?html}">
        <#if problems?has_content>
        <div class="rss-problem" role="alert">This search cannot search:<#list problems as p> ${p?html}.</#list></div>
        <#else>
        <div class="rss-find"<#if value?has_content> style="display:none;"</#if>>
            <input type="text" class="rss-term" autocomplete="off" aria-label="${(element.properties.label!"")?html}">
            <button type="button" class="rss-go">Search</button>
        </div>
        <div class="rss-chosen"<#if !value?has_content> style="display:none;"</#if>>
            <span class="rss-chosen-text">${value!?html}</span>
            <button type="button" class="rss-change">Change</button>
        </div>
        <div class="rss-words" role="status" aria-live="polite"></div>
        <div class="rss-rows"></div>
        </#if>
    </div>
    <div class="form-clear"></div>
</div>
<#if !request.getAttribute("rss_css_loaded")??>
${request.setAttribute("rss_css_loaded", true)!}
<style>
    .rss-box .rss-find, .rss-box .rss-chosen { display: flex; gap: 6px; align-items: center; }
    .rss-box .rss-term { flex: 1 1 auto; min-width: 12em; }
    .rss-box .rss-words { margin: 6px 0; }
    .rss-box .rss-problem { color: #b71c1c; }
    .rss-box table.rss-result { border-collapse: collapse; margin-top: 4px; width: 100%; }
    .rss-box table.rss-result th, .rss-box table.rss-result td { border: 1px solid #ddd; padding: 4px 8px; text-align: left; }
    .rss-box table.rss-result tr.rss-row { cursor: pointer; }
    .rss-box table.rss-result tr.rss-row:hover, .rss-box table.rss-result tr.rss-row:focus { background: #eef4fb; }
    .rss-locked { background-color: rgb(242, 242, 242); pointer-events: none; }
</style>
</#if>
<script>
(function () {
    var cfg = ${config!"{}"};
    cfg.token = '${(token!"")?js_string}';
    cfg.nonce = '${(nonce!"")?js_string}';
    var src = '${resourceBase!}record-search.js&v=8.2.0';
    function start() {
        if (window.RecordSearch) {
            window.RecordSearch.init(cfg);
            return true;
        }
        return false;
    }
    function whenReady() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () { start(); });
        } else {
            start();
        }
    }
    if (window.RecordSearch) {
        whenReady();
        return;
    }
    var s = document.querySelector('script[data-rss-src]');
    if (!s) {
        s = document.createElement('script');
        s.src = src;
        s.setAttribute('data-rss-src', 'true');
        document.head.appendChild(s);
    }
    s.addEventListener('load', whenReady);
})();
</script>
