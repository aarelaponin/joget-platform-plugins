// The page's side of the record search: the fields a choice fills, and that they are locked; the
// result's columns; the words when nothing is found, with the officer left on the search.
// Run, in the plugin's folder: node --test src/test/js/record-search.test.js   (Node 18 or later;
// no package is installed)
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const RS = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'record-search.js'));

// A form's fields, as far as the page's script touches them.
function field(name, type) {
  return {
    name, type: type || 'text', value: '', className: '', attrs: {}, events: [],
    setAttribute(k, v) { this.attrs[k] = String(v); },
    getAttribute(k) { return k in this.attrs ? this.attrs[k] : null; },
    dispatchEvent(e) { this.events.push(e.type); return true; },
  };
}
function form(...fields) {
  const byName = {};
  for (const f of fields) (byName[f.name] = byName[f.name] || []).push(f);
  return { byName, getElementsByName: (n) => byName[n] || [] };
}
// What the page shows, recorded.
function view() {
  const v = { shown: {} };
  for (const k of ['value', 'words', 'rows', 'searching', 'chosen']) v[k] = (x) => { v.shown[k] = x; };
  return v;
}
const cfg = {
  columns: ['Identification number', 'Name', 'Kind of person'],
  fills: ['shown_name', 'shown_kind'],
  locks: ['shown_name', 'shown_kind'],
};
const row = { value: '100000002', label: 'Ana Petrescu', cells: ['100000002', 'Ana Petrescu', 'legal'],
  fills: { shown_name: 'Ana Petrescu', shown_kind: 'legal' } };

test('the choice fills the fields the settings name, and tells the form', () => {
  const doc = form(field('shown_name'), field('shown_kind'), field('other'));
  RS.fill(doc, row.fills);
  assert.equal(doc.byName.shown_name[0].value, 'Ana Petrescu');
  assert.equal(doc.byName.shown_kind[0].value, 'legal');
  assert.deepEqual(doc.byName.shown_name[0].events, ['change']);
  assert.equal(doc.byName.other[0].value, '');
  assert.deepEqual(doc.byName.other[0].events, []);
});

test('the fields the choice fills are locked: read-only, out of the tab order, marked', () => {
  const doc = form(field('shown_name'), field('shown_kind'), field('other'), field('shown_name', 'hidden'));
  RS.lock(doc, cfg.locks);
  for (const n of ['shown_name', 'shown_kind']) {
    const e = doc.byName[n][0];
    assert.equal(e.getAttribute('readonly'), 'readonly', n);
    assert.equal(e.getAttribute('aria-readonly'), 'true', n);
    assert.equal(e.getAttribute('tabindex'), '-1', n);
    assert.match(e.className, /\brss-locked\b/, n);
  }
  assert.equal(doc.byName.other[0].getAttribute('readonly'), null);         // not the search's
  assert.equal(doc.byName.shown_name[1].getAttribute('readonly'), null);    // a hidden twin is left
  RS.lock(doc, cfg.locks);                                                  // locking twice marks once
  assert.equal(doc.byName.shown_name[0].className.split(' ').filter((c) => c === 'rss-locked').length, 1);
});

test('the result shows the columns the settings name, in their order, as text', () => {
  const html = RS.rowsHtml(cfg.columns, [row, { value: 'x', cells: ['<b>1</b>', 'A & B', ''] }]);
  const heads = [...html.matchAll(/<th>(.*?)<\/th>/g)].map((m) => m[1]);
  assert.deepEqual(heads, cfg.columns);
  assert.match(html, /<td>100000002<\/td><td>Ana Petrescu<\/td><td>legal<\/td>/);
  assert.match(html, /<td>&lt;b&gt;1&lt;\/b&gt;<\/td><td>A &amp; B<\/td>/);           // nothing is markup
  assert.equal((html.match(/data-row="/g) || []).length, 2);
});

test('nothing found: the words are shown and the officer stays on the search', () => {
  const v = view();
  const words = 'No person is registered under 999999999. Check the number against the document it came from, or give it again.';
  const rows = RS.onAnswer(v, cfg, { rows: [], how: 'none', message: words, more: false });
  assert.deepEqual(rows, []);
  assert.equal(v.shown.words, words);
  assert.equal(v.shown.rows, '');
  assert.equal(v.shown.searching, true);
  assert.equal('value' in v.shown, false);                                  // nothing is chosen
});

test('a refused or failed search says so, and chooses nothing', () => {
  const v = view();
  RS.onAnswer(v, cfg, { error: 'This search was not issued by this server.' });
  assert.equal(v.shown.words, 'This search was not issued by this server.');
  assert.equal(v.shown.searching, true);
  const w = view();
  RS.onAnswer(w, cfg, null);
  assert.equal(w.shown.words, 'The search could not be made.');
});

test('more found than shown is said', () => {
  const v = view();
  RS.onAnswer(v, cfg, { rows: [row], more: true });
  assert.match(v.shown.words, /^The first 1 are shown/);
});

test('choosing a row stores its value, fills and locks the fields, and shows the choice', () => {
  const doc = form(field('shown_name'), field('shown_kind'));
  const v = view();
  RS.choose(doc, v, cfg, row);
  assert.equal(v.shown.value, '100000002');
  assert.equal(v.shown.chosen, '100000002 — Ana Petrescu');
  assert.equal(v.shown.searching, false);
  assert.equal(doc.byName.shown_name[0].value, 'Ana Petrescu');
  assert.equal(doc.byName.shown_kind[0].getAttribute('readonly'), 'readonly');
});

test('changing the choice empties what it filled and leaves those fields locked', () => {
  const doc = form(field('shown_name'), field('shown_kind'));
  const v = view();
  RS.choose(doc, v, cfg, row);
  RS.change(doc, v, cfg);
  assert.equal(v.shown.value, '');
  assert.equal(v.shown.searching, true);
  assert.equal(doc.byName.shown_name[0].value, '');
  assert.equal(doc.byName.shown_name[0].getAttribute('readonly'), 'readonly');
});

test('the address asks the service with the sealed settings and the words typed', () => {
  const u = RS.url({ serviceUrl: '/jw/web/json/plugin/X/service', token: 'a+b/c=', nonce: 'n 1' }, 'Ana & Co');
  assert.equal(u, '/jw/web/json/plugin/X/service?_s=a%2Bb%2Fc%3D&_n=n%201&q=Ana%20%26%20Co');
});
