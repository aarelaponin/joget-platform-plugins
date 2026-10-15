/*
 * The page's side of the smart search element when it searches a record its properties name
 * (RecordSearchElement.ftl; RecordSearchSettings, 8.2.0). What is typed is sent to
 * RecordSearchWebService with the settings the server sealed; the rows found are shown with the
 * columns the settings name; the row chosen stores its value, fills the fields the settings name
 * and locks the fields they name; when nothing is found the settings' words are shown and the
 * officer stays on the search. Nothing here names a record or a domain.
 *
 * Written for the browsers the platform supports, without a library; the functions below the
 * wiring take the document and the view as arguments so that they are tested on their own
 * (src/test/js/record-search.test.js).
 */
(function (root, factory) {
    var api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.RecordSearch = api;
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var LOCKED = 'rss-locked';
    var FAILED = 'The search could not be made.';

    function esc(s) {
        return String(s === null || s === undefined ? '' : s).replace(/[&<>"']/g, function (c) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c];
        });
    }

    function named(doc, name) {
        var out = [], els = doc.getElementsByName(name) || [];
        for (var i = 0; i < els.length; i++) {
            out.push(els[i]);
        }
        return out;
    }

    function fire(e, type) {
        var ev;
        if (typeof Event === 'function') {
            ev = new Event(type, {bubbles: true});
        } else {
            ev = document.createEvent('HTMLEvents');
            ev.initEvent(type, true, false);
        }
        e.dispatchEvent(ev);
    }

    /** Write each value into the form's field of that name, and tell the form it changed. */
    function fill(doc, fills) {
        var names = Object.keys(fills || {});
        names.forEach(function (name) {
            var v = fills[name] === null || fills[name] === undefined ? '' : String(fills[name]);
            named(doc, name).forEach(function (e) {
                if (e.type === 'radio' || e.type === 'checkbox') {
                    e.checked = e.value === v;
                } else {
                    e.value = v;
                }
                fire(e, 'change');
            });
        });
        return names;
    }

    /** Lock the named fields: read-only to the keyboard, out of the tab order, closed to the
     *  pointer (the class), and said to be read-only. The platform's own read-only is not used:
     *  a field it makes read-only is saved with the value it was drawn with, not the one the
     *  choice wrote. A hidden field of the same name is left as it is. */
    function lock(doc, ids) {
        (ids || []).forEach(function (name) {
            named(doc, name).forEach(function (e) {
                if (e.type === 'hidden') {
                    return;
                }
                e.setAttribute('readonly', 'readonly');
                e.setAttribute('aria-readonly', 'true');
                e.setAttribute('tabindex', '-1');
                if ((' ' + e.className + ' ').indexOf(' ' + LOCKED + ' ') < 0) {
                    e.className = e.className ? e.className + ' ' + LOCKED : LOCKED;
                }
            });
        });
    }

    function clear(doc, ids) {
        var empty = {};
        (ids || []).forEach(function (n) { empty[n] = ''; });
        fill(doc, empty);
    }

    /** The words beside the result: the answer's own when nothing was found or it was refused. */
    function answerText(answer) {
        if (!answer) {
            return FAILED;
        }
        if (answer.error) {
            return answer.error;
        }
        var rows = answer.rows || [];
        if (!rows.length) {
            return answer.message || '';
        }
        if (answer.more) {
            return 'The first ' + rows.length + ' are shown; narrow the search to see the rest.';
        }
        return '';
    }

    /** The result as a table: the columns the settings name, one row for each record found. */
    function rowsHtml(columns, rows) {
        var h = ['<table class="rss-result"><thead><tr>'];
        (columns || []).forEach(function (c) { h.push('<th>' + esc(c) + '</th>'); });
        h.push('</tr></thead><tbody>');
        (rows || []).forEach(function (r, i) {
            h.push('<tr class="rss-row" data-row="' + i + '" tabindex="0">');
            (r.cells || []).forEach(function (v) { h.push('<td>' + esc(v) + '</td>'); });
            h.push('</tr>');
        });
        h.push('</tbody></table>');
        return h.join('');
    }

    function describe(row) {
        return row.value + (row.label && row.label !== row.value ? ' \u2014 ' + row.label : '');
    }

    /** Show an answer: its rows to choose from, or its words; the search stays open with what was
     *  typed. Returns the rows shown. */
    function onAnswer(view, cfg, answer) {
        var rows = (answer && !answer.error && answer.rows) || [];
        view.words(answerText(answer));
        view.rows(rows.length ? rowsHtml(cfg.columns, rows) : '');
        view.searching(true);
        return rows;
    }

    /** The row chosen: its value stored, the fields filled and locked, the choice shown. */
    function choose(doc, view, cfg, row) {
        view.value(row.value);
        fill(doc, row.fills || {});
        lock(doc, cfg.locks);
        view.chosen(describe(row));
        view.rows('');
        view.words('');
        view.searching(false);
    }

    /** The choice undone: nothing stored, the fields it filled emptied and still locked, the
     *  search open again. */
    function change(doc, view, cfg) {
        view.value('');
        clear(doc, cfg.fills);
        lock(doc, cfg.locks);
        view.chosen('');
        view.searching(true);
    }

    function url(cfg, term) {
        return cfg.serviceUrl + '?_s=' + encodeURIComponent(cfg.token || '') + '&_n='
            + encodeURIComponent(cfg.nonce || '') + '&q=' + encodeURIComponent(term);
    }

    function request(cfg, term, done) {
        var x = new XMLHttpRequest();
        x.open('GET', url(cfg, term), true);
        x.setRequestHeader('Accept', 'application/json');
        x.onreadystatechange = function () {
            if (x.readyState !== 4) {
                return;
            }
            var a = null;
            try {
                a = JSON.parse(x.responseText);
            } catch (e) {
                a = null;
            }
            if (x.status !== 200 && !(a && a.error)) {
                a = {error: FAILED};
            }
            done(a || {error: FAILED});
        };
        x.send();
    }

    /** Wire the element drawn with `cfg` on this page. */
    function init(cfg) {
        var doc = document, box = doc.getElementById(cfg.elementId);
        if (!box) {
            return null;
        }
        lock(doc, cfg.locks);
        var hidden = doc.getElementById(cfg.elementId + '_value');
        var input = box.querySelector('.rss-term');
        if (!input || !hidden) {
            return null;                                   // the form says why it cannot search
        }
        var go = box.querySelector('.rss-go'), find = box.querySelector('.rss-find');
        var chosen = box.querySelector('.rss-chosen'), chosenText = box.querySelector('.rss-chosen-text');
        var again = box.querySelector('.rss-change'), words = box.querySelector('.rss-words');
        var rowsBox = box.querySelector('.rss-rows');
        var shown = [];
        var view = {
            value: function (v) { hidden.value = v; fire(hidden, 'change'); },
            words: function (t) { words.textContent = t || ''; },
            rows: function (html) { rowsBox.innerHTML = html || ''; },
            searching: function (on) {
                find.style.display = on ? '' : 'none';
                chosen.style.display = on ? 'none' : '';
                if (on) {
                    try { input.focus(); } catch (e) { /* not focusable yet */ }
                }
            },
            chosen: function (t) { chosenText.textContent = t || ''; }
        };
        function run() {
            var term = (input.value || '').replace(/^\s+|\s+$/g, '');
            if (!term) {
                view.words('');
                view.rows('');
                return;
            }
            view.words('Searching\u2026');
            request(cfg, term, function (answer) { shown = onAnswer(view, cfg, answer); });
        }
        function pick(target) {
            while (target && target !== rowsBox && !(target.getAttribute && target.getAttribute('data-row'))) {
                target = target.parentNode;
            }
            if (target && target !== rowsBox && shown[+target.getAttribute('data-row')]) {
                choose(doc, view, cfg, shown[+target.getAttribute('data-row')]);
            }
        }
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.keyCode === 13) {
                e.preventDefault();                        // Enter searches; it does not submit the form
                run();
            }
        });
        go.addEventListener('click', function (e) { e.preventDefault(); run(); });
        rowsBox.addEventListener('click', function (e) { pick(e.target); });
        rowsBox.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.keyCode === 13) {
                e.preventDefault();
                pick(e.target);
            }
        });
        again.addEventListener('click', function (e) { e.preventDefault(); change(doc, view, cfg); });
        if (hidden.value) {                                // a record opened again: show what was chosen
            request(cfg, hidden.value, function (answer) {
                var rows = (answer && answer.rows) || [];
                for (var i = 0; i < rows.length; i++) {
                    if (rows[i].value === hidden.value) {
                        view.chosen(describe(rows[i]));
                        return;
                    }
                }
            });
        }
        return view;
    }

    return {
        init: init, fill: fill, lock: lock, clear: clear, answerText: answerText, rowsHtml: rowsHtml,
        onAnswer: onAnswer, choose: choose, change: change, url: url, esc: esc
    };
}));
