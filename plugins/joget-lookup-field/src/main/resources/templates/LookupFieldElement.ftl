<#-- Lookup Field Form Element Template -->
<div class="form-cell lookup-field-cell" ${elementMetaData!}>
    <#if displayType! != "hidden">
    <label class="label">
        ${label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    </#if>
    <div class="form-cell-value">
        <#if displayType! == "hidden">
            <#-- Hidden field -->
            <input type="hidden"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        <#elseif displayType! == "readonly">
            <#-- Read-only display with hidden input -->
            <span id="${fieldId!}_display" class="lookup-field-display">${value!?html}</span>
            <input type="hidden"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        <#else>
            <#-- Editable text field -->
            <input type="text"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        </#if>
        <#-- Not-found notice. Rendered for EVERY display type, including hidden:
             a lookup that resolved to nothing must say so wherever it sits, and
             never be inferred from a blank field. -->
        <span id="${fieldId!}_notice" class="lookup-field-notice" style="display:none;"></span>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Inline CSS -->
<style>
.lookup-field-cell {
    margin-bottom: 10px;
}
.lookup-field-display {
    display: inline-block;
    padding: 4px 8px;
    background-color: #f5f5f5;
    border: 1px solid #ddd;
    border-radius: 3px;
    min-width: 100px;
    min-height: 1.2em;
}
.lookup-field-input[type="text"] {
    width: 100%;
    max-width: 400px;
    padding: 4px 8px;
}
.lookup-field-loading {
    color: #999;
    font-style: italic;
}
.lookup-field-notice {
    display: inline-block;
    margin-left: 8px;
    color: #b00020;
    font-size: 0.9em;
}
.lookup-field-display.lookup-field-notfound {
    color: #b00020;
    font-style: italic;
    background-color: #fdf1f2;
    border-color: #f0b3b8;
}
</style>

<#-- JavaScript -->
<script>
(function() {
    // Unique instance ID to prevent duplicate initialization
    var instanceId = '${elementId!?replace("-", "_")}';

    if (window['LookupFieldLoaded_' + instanceId]) return;
    window['LookupFieldLoaded_' + instanceId] = true;

    // Configuration from server
    var config = ${config!'{}'};
    var fieldId = '${fieldId!}';
    var elementParamName = '${elementParamName!}';
    var notFoundMessage = config.notFoundMessage || 'No matching record found';
    // Debounce for typed (TextField) sources: the lookup fires while the user
    // types, but only once they pause. A SelectBox source never debounces.
    var inputDebounceMs = parseInt(config.inputDebounceMs, 10);
    if (isNaN(inputDebounceMs) || inputDebounceMs < 0) inputDebounceMs = 400;
    // Monotonic request counter: with a typed source, several lookups can be in
    // flight at once and they may come back out of order. Only the newest one is
    // allowed to write — otherwise a slow response for "100000000" lands after a
    // fast one for "1000000002" and puts a name next to the wrong key.
    var requestSeq = 0;
    var debounceTimer = null;
    var watcherRegistered = false;
    var seeded = false;         // the initial value/fetch has been applied once

    // Debug logging
    var DEBUG = false;
    function log(msg) {
        if (DEBUG) console.log('[LookupField ' + fieldId + '] ' + msg);
    }

    log('Initializing with config: ' + JSON.stringify(config));

    // Global lookup cache: shared across all LookupField instances
    // Key: "formId/primaryKey" → Value: full record object
    window._lookupFieldCache = window._lookupFieldCache || {};

    // Global registry for chained lookup notifications
    // Key: sourceFieldId → Value: array of callback functions
    window._lookupFieldWatchers = window._lookupFieldWatchers || {};

    /**
     * Find the source field element using multiple selector strategies
     */
    function findSourceField() {
        var sourceFieldId = config.sourceFieldId;
        var selectors = [
            '[name="' + sourceFieldId + '"]',
            '[name$="' + sourceFieldId + '"]',
            '#' + sourceFieldId,
            '[id$="' + sourceFieldId + '"]'
        ];

        for (var i = 0; i < selectors.length; i++) {
            var elements = document.querySelectorAll(selectors[i]);
            if (elements.length > 0) {
                log('Found source field with selector: ' + selectors[i]);
                return elements[0];
            }
        }

        log('Source field not found: ' + sourceFieldId);
        return null;
    }

    /**
     * Update the display and hidden input with the result of a lookup.
     *
     * `state` is one of:
     *   'ok'        a record was found — `value` is its column
     *   'empty'     there was nothing to look up (the source field is blank)
     *   'notfound'  a key WAS looked up and no record matched it
     *
     * The stored value is cleared on 'empty' AND on 'notfound'. Leaving the name
     * of the previously-looked-up record standing next to a key that resolves to
     * nothing is the never-fabricate defect (dim05) this element exists to avoid:
     * an unknown key shows the not-found notice and stores NOTHING.
     */
    function updateDisplay(value, state) {
        state = state || 'ok';
        if (state !== 'ok') {
            value = '';
        }
        var changed = false;

        // Update hidden/input field by ID
        var inputField = document.getElementById(fieldId);
        if (inputField) {
            changed = (inputField.value !== value);
            inputField.value = value;
        }

        // Also try with element param name (Joget pattern)
        var inputFieldByName = document.querySelector('[name="' + elementParamName + '"]');
        if (inputFieldByName && inputFieldByName !== inputField) {
            inputFieldByName.value = value;
        }

        // Update display span if exists (for readonly mode)
        var displaySpan = document.getElementById(fieldId + '_display');
        if (displaySpan) {
            displaySpan.classList.remove('lookup-field-loading');
            if (state === 'notfound') {
                displaySpan.textContent = notFoundMessage;
                displaySpan.classList.add('lookup-field-notfound');
            } else {
                displaySpan.textContent = value;
                displaySpan.classList.remove('lookup-field-notfound');
            }
        }

        // The not-found notice. It exists for the display types that have nowhere else
        // to put the sentence — `hidden` and `editable`, where a lookup that resolved to
        // nothing would otherwise present as an ordinary empty field. When the readonly
        // display span is present it already carries the message, and showing it twice
        // reads as two separate complaints about one TIN.
        var notice = document.getElementById(fieldId + '_notice');
        if (notice && displaySpan) {
            notice.textContent = '';
            notice.style.display = 'none';
        } else if (notice) {
            if (state === 'notfound') {
                notice.textContent = notFoundMessage;
                notice.style.display = '';
            } else {
                notice.textContent = '';
                notice.style.display = 'none';
            }
        }

        // Machine-readable state for QA/probes and for CSS.
        var cell = (inputField || displaySpan || notice);
        while (cell && cell.className.indexOf('lookup-field-cell') === -1) {
            cell = cell.parentElement;
        }
        if (cell) {
            cell.setAttribute('data-lookup-state', state);
        }

        // Notify chained LookupFields watching this field
        if (changed) {
            // 1. Native DOM event on the input
            if (inputField) {
                inputField.dispatchEvent(new Event('change', { bubbles: true }));
            }
            // 2. Direct notification via global watcher registry (reliable for chained lookups)
            var watchers = window._lookupFieldWatchers[fieldId] || [];
            for (var w = 0; w < watchers.length; w++) {
                log('Notifying chained watcher for: ' + fieldId);
                watchers[w]();
            }
        }

        log('Updated to: ' + value);
    }

    /**
     * Show loading state
     */
    function showLoading() {
        var displaySpan = document.getElementById(fieldId + '_display');
        if (displaySpan) {
            displaySpan.textContent = '...';
            displaySpan.classList.add('lookup-field-loading');
        }
    }

    /**
     * Fetch the record and extract the configured column.
     *
     * Two modes:
     * - If lookupKeyColumn is set: query the list endpoint with column filter
     *   (when SelectBox value != Joget primary key, e.g. customerId from ConcatField)
     * - If lookupKeyColumn is empty: direct GET by primary key
     *   (when SelectBox value IS the Joget primary key, e.g. IdGeneratorField)
     */
    function fetchAndUpdate() {
        var sourceField = findSourceField();
        if (!sourceField) return;

        var selectedId = (sourceField.value || '').trim();

        var seq = ++requestSeq;

        if (!selectedId) {
            updateDisplay('', 'empty');
            return;
        }

        var cacheKey = config.lookupFormId + '/' + selectedId;

        // Check cache first. A MISS is cached too (as null): re-typing a key that is
        // known not to exist must show not-found immediately, not fall through to a
        // fresh request whose reply might race a later one.
        if (window._lookupFieldCache.hasOwnProperty(cacheKey)) {
            var cached = window._lookupFieldCache[cacheKey];
            if (cached === null) {
                updateDisplay('', 'notfound');
                log('Cache hit (miss): ' + cacheKey);
            } else {
                var value = cached[config.lookupColumn] || '';
                updateDisplay(value, 'ok');
                log('Cache hit: ' + cacheKey + ' → ' + value);
            }
            return;
        }

        showLoading();

        // Use the plugin's own web service endpoint (session-authenticated)
        // Table name is resolved server-side from the form definition — no need to pass it
        var wsUrl = '/jw/web/json/plugin/'
            + 'global.govstack.lookupfield.element.LookupFieldWebService/service'
            + '?action=lookup'
            + '&appId=' + encodeURIComponent(config.appId)
            + '&formId=' + encodeURIComponent(config.lookupFormId)
            + '&keyVal=' + encodeURIComponent(selectedId);

        if (config.lookupKeyColumn) {
            wsUrl += '&keyCol=' + encodeURIComponent(config.lookupKeyColumn);
        }

        log('Fetching via plugin WS: ' + wsUrl);

        $.getJSON(wsUrl, function(record) {
            if (seq !== requestSeq) {         // a newer keystroke already won
                log('Stale response for ' + selectedId + ' — discarded');
                return;
            }
            if (record && Object.keys(record).length > 0) {
                window._lookupFieldCache[cacheKey] = record;
                var value = record[config.lookupColumn] || '';
                updateDisplay(value, 'ok');
                log('Fetched: ' + cacheKey + ' → ' + value);
            } else {
                // The key was looked up and matched nothing. Say so, and store nothing.
                window._lookupFieldCache[cacheKey] = null;
                log('No record found for: ' + selectedId);
                updateDisplay('', 'notfound');
            }
        }).fail(function(jqXHR, textStatus, errorThrown) {
            if (seq !== requestSeq) return;
            log('Fetch failed: ' + textStatus + ' ' + errorThrown
                + ' (status=' + jqXHR.status + ', response=' + jqXHR.responseText + ')');
            // A failed lookup is NOT a found record. Clear rather than keep a stale
            // name; the failure is not cached, so the next attempt retries.
            updateDisplay('', 'notfound');
        });
    }

    /**
     * Attach event listener to the source field
     */
    function attachListener() {
        var sourceFieldId = config.sourceFieldId;

        // Register in the global watcher registry (for chained lookup notifications).
        // Once — init() is retried for dynamically loaded forms and a re-push would
        // fire this instance's lookup N times per upstream change.
        if (!window._lookupFieldWatchers[sourceFieldId]) {
            window._lookupFieldWatchers[sourceFieldId] = [];
        }
        if (!watcherRegistered) {
            watcherRegistered = true;
            window._lookupFieldWatchers[sourceFieldId].push(fetchAndUpdate);
            log('Registered as watcher for: ' + sourceFieldId);
        }

        // Also attach a DOM event listener for SelectBox/TextField sources
        var sourceField = findSourceField();
        if (!sourceField) {
            log('Source DOM element not found (will rely on watcher registry): ' + sourceFieldId);
            return;
        }

        if (sourceField.hasAttribute('data-lookup-listener-' + fieldId)) {
            return;
        }
        sourceField.setAttribute('data-lookup-listener-' + fieldId, 'true');

        // Is the watched field TYPED into, or PICKED from?
        //
        // A SelectBox (and a radio/checkbox group) only ever emits `change`, and it
        // emits it once per pick — the original binding, unchanged.
        //
        // A TextField emits nothing until the user leaves it, which for a TIN typed
        // into a text box means the name appears only after a tab-out. That is the
        // whole reason this branch exists: a text source ALSO gets `blur` and a
        // DEBOUNCED `input`, so the name appears while the applicant is still looking
        // at the field they typed into.
        var tag = (sourceField.tagName || '').toLowerCase();
        var type = (sourceField.getAttribute('type') || 'text').toLowerCase();
        var isTyped = (tag === 'textarea')
            || (tag === 'input' && ['text', 'search', 'tel', 'number', 'email', 'url', 'hidden']
                    .indexOf(type) !== -1);

        var eventType = config.updateOn === 'blur' ? 'blur' : 'change';
        sourceField.addEventListener(eventType, function() {
            if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
            fetchAndUpdate();
        });
        log('Attached ' + eventType + ' listener on DOM element (typed=' + isTyped + ')');

        if (!isTyped) {
            return;                          // SelectBox path ends here — unchanged
        }

        // `blur` as well as `change`: a text input fires `change` on blur only when the
        // value actually changed, and a re-visit that ends on the same (unresolved)
        // value must still re-state its verdict.
        if (eventType !== 'blur') {
            sourceField.addEventListener('blur', function() {
                if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
                fetchAndUpdate();
            });
        }

        // Debounced typing.
        sourceField.addEventListener('input', function() {
            if (debounceTimer) clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function() {
                debounceTimer = null;
                fetchAndUpdate();
            }, inputDebounceMs);
        });
        log('Attached debounced input listener (' + inputDebounceMs + 'ms)');
    }

    /**
     * Initialize the component
     */
    function init() {
        log('Initializing...');

        // Attach listener for future changes (self-guarded — safe to call again)
        attachListener();

        // Seed ONCE. init() is retried for dynamically loaded forms, and re-seeding
        // would re-stamp the server-rendered value over a verdict the user has since
        // produced by typing — putting a stale name back next to an unknown key.
        if (!seeded) {
            seeded = true;
            var existingValue = '${value!?js_string}';
            if (existingValue) {
                updateDisplay(existingValue, 'ok');   // edit mode: the stored value
            } else {
                fetchAndUpdate();                     // resolve from the current source
            }
        }

        log('Initialization complete');
    }

    // Initialize when DOM is ready
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(init, 100);
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(init, 100);
        });
    }

    // Also re-initialize after a delay (for dynamic form loading)
    setTimeout(init, 500);

    // Store reference globally for debugging
    window['lookupField_' + fieldId] = {
        fetch: fetchAndUpdate,
        config: config
    };

})();
</script>
