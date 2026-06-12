#!/usr/bin/env node
// Unified, requirement-aligned CPT report generator (Option A) — v2.
// Adds interactivity for the Process tests section: expandable plain-language instruction
// breakdowns (deterministically translated from the .test.json schema), per-test diagram
// path highlighting on selection, fixed test labels, and ID-ascending sort.

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const artifacts = join(here, 'artifacts');
const out = join(here, '..', 'target', 'unified-report.html');
const testCasesPath = join(here, '..', 'src', 'test', 'resources', 'test-cases', 'CamundaInsurance_ClaimsProcessing.test.json');

const spec = JSON.parse(readFileSync(join(here, 'requirements.json'), 'utf8'));
const vendor = readFileSync(join(here, 'vendor', 'bpmn-navigated-viewer.js'), 'utf8');
const testCases = existsSync(testCasesPath) ? JSON.parse(readFileSync(testCasesPath, 'utf8')).testCases : [];

// ---------- surefire ----------
function parseSurefireDir(dir) {
  const cases = {};
  if (!existsSync(dir)) return cases;
  for (const f of readdirSync(dir).filter(n => n.endsWith('.xml'))) {
    const xml = readFileSync(join(dir, f), 'utf8');
    const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
    let m;
    while ((m = re.exec(xml))) {
      const attrs = m[1], body = m[3] || '';
      const name = (attrs.match(/name="([^"]*)"/) || [])[1] || '';
      let status = 'pass', message = '';
      if (/<skipped/.test(body)) { status = 'skipped'; message = (body.match(/<skipped[^>]*message="([^"]*)"/) || [])[1] || ''; }
      else if (/<failure/.test(body)) { status = 'fail'; message = (body.match(/<failure[^>]*message="([^"]*)"/) || [])[1] || ''; }
      else if (/<error/.test(body)) { status = 'fail'; message = (body.match(/<error[^>]*message="([^"]*)"/) || [])[1] || ''; }
      cases[name] = { status, message: decode(message) };
    }
  }
  return cases;
}
const decode = s => s.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#10;/g,' ').replace(/&amp;/g,'&');
const surefire = {
  process: parseSurefireDir(join(artifacts, 'process', 'surefire')),
  integration: parseSurefireDir(join(artifacts, 'integration', 'surefire')),
};
const readReport = run => {
  const p = join(artifacts, run, 'report.json');
  try { return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : null; } catch { return null; }
};
const reports = { process: readReport('process'), integration: readReport('integration') };

// ---------- coverage helpers ----------
const coverageFor = (run, pid) => {
  const r = reports[run]; if (!r) return null;
  return (r.processCoverages || r.coverages || []).find(x => x.processDefinitionId === pid) || null;
};
const modelFor = (run, pid) => {
  const r = reports[run]; if (!r) return null;
  return (r.processModels || []).find(x => x.processDefinitionId === pid) || null;
};
// per-scenario (per-run) coverage for the process suite, indexed 1-based in scenario order
function scenarioCoverage(run, suiteIdMatch, pid) {
  const r = reports[run]; if (!r) return [];
  const suite = (r.suites || []).find(s => (s.id || '').includes(suiteIdMatch));
  if (!suite) return [];
  return (suite.runs || []).map(rn => {
    const c = (rn.processCoverages || []).find(x => x.processDefinitionId === pid);
    return c ? { completed: c.completedElements || [], taken: c.takenSequenceFlows || [] } : { completed: [], taken: [] };
  });
}
const procScenarioCov = scenarioCoverage('process', 'ProcessTest', spec.processId);

// ---------- plain-language instruction translation (deterministic, schema-based) ----------
const short = v => {
  if (v == null) return 'null';
  if (typeof v === 'object') { const s = JSON.stringify(v); return s.length > 80 ? s.slice(0, 77) + '…' : s; }
  const s = String(v); return s.length > 80 ? s.slice(0, 77) + '…' : s;
};
const kv = obj => Object.entries(obj || {}).map(([k, v]) => `${k}=${short(v)}`).join(', ');
const prettyState = s => String(s || '').toLowerCase().replace(/_/g, ' ').replace('is ', '');
function translate(i) {
  const t = i.type;
  const sel = i.jobSelector ? (i.jobSelector.elementId || i.jobSelector.jobType) : '';
  switch (t) {
    case 'CREATE_PROCESS_INSTANCE':
      return { kind: 'action', text: `Start a "${i.processDefinitionSelector?.processDefinitionId}" instance` + (i.variables ? ` with ${kv(i.variables)}` : '') };
    case 'COMPLETE_JOB_AD_HOC_SUB_PROCESS': {
      const tools = (i.activateElements || []).map(e => e.elementId).join(', ');
      const phase = i.completionConditionFulfilled ? 'final round — assessment complete' : 'tool round — awaiting results';
      return { kind: 'action', text: `Complete the AI agent (ad-hoc) job — ${phase}` + (tools ? `; activate tools: ${tools}` : '') };
    }
    case 'COMPLETE_JOB':
      return { kind: 'action', text: `Complete the "${sel}" job` + (i.variables ? ` returning ${kv(i.variables)}` : '') };
    case 'COMPLETE_USER_TASK':
      return { kind: 'action', text: `Complete the "${i.userTaskSelector?.elementId}" user task` + (i.variables ? ` with ${kv(i.variables)}` : '') };
    case 'THROW_BPMN_ERROR_FROM_JOB':
      return { kind: 'action', text: `Throw BPMN error "${i.errorCode}" from the "${sel}" job` };
    case 'PUBLISH_MESSAGE':
      return { kind: 'action', text: `Publish message "${i.messageName}"` };
    case 'INCREASE_TIME':
      return { kind: 'action', text: `Advance time by ${i.duration}` };
    case 'ASSERT_ELEMENT_INSTANCES':
      return { kind: 'assert', text: `Assert ${(i.elementSelectors || []).map(e => e.elementId).join(', ')} ${prettyState(i.state)}` };
    case 'ASSERT_ELEMENT_INSTANCE':
      return { kind: 'assert', text: `Assert ${i.elementSelector?.elementId} ${prettyState(i.state)}` };
    case 'ASSERT_PROCESS_INSTANCE':
      return { kind: 'assert', text: `Assert the process instance is ${prettyState(i.state)}` };
    case 'ASSERT_VARIABLES':
      return { kind: 'assert', text: `Assert variables: ${kv(i.variables)}` };
    case 'ASSERT_USER_TASK':
      return { kind: 'assert', text: `Assert user task ${i.userTaskSelector?.elementId} ${prettyState(i.state)}` };
    default:
      return { kind: 'action', text: t };
  }
}

// ---------- status join ----------
function statusFor(req, run) {
  const cases = surefire[run] || {};
  const names = Object.keys(cases);
  if (req.match === 'aggregate')
    return names.length ? { status: names.some(n => cases[n].status === 'fail') ? 'fail' : 'pass', message: '' } : { status: 'missing', message: 'no test run' };
  let key;
  if (req.scenarioIndex) key = names.find(n => n.includes('shouldPass') && n.includes('[' + req.scenarioIndex + ']'));
  else key = names.find(n => n.includes(req.match)) || names.find(n => n.includes(req.id));
  return key ? cases[key] : { status: 'missing', message: 'test not found in run' };
}

// ---------- diagrams ----------
const diagrams = [];
function addDiagram(key, pid, run) {
  const model = modelFor(run, pid) || modelFor('integration', pid) || modelFor('process', pid);
  const cov = coverageFor(run, pid);
  if (model && model.xml) diagrams.push({ key, xml: model.xml, completed: cov ? cov.completedElements || [] : [], taken: cov ? cov.takenSequenceFlows || [] : [] });
}
addDiagram('proc-main', spec.processId, 'process');
addDiagram('comp-tools', spec.toolsProcessId, 'integration');
addDiagram('pint-main', spec.processId, 'integration');

// ---------- id sort ----------
const idKey = id => { const m = id.match(/^([A-Z]+)-(\d+)([a-z]*)$/); return m ? [m[1], +m[2], m[3]] : [id, 0, '']; };
const byId = (a, b) => { const ka = idKey(a.id), kb = idKey(b.id); return ka[0] < kb[0] ? -1 : ka[0] > kb[0] ? 1 : ka[1] - kb[1] || (ka[2] < kb[2] ? -1 : ka[2] > kb[2] ? 1 : 0); };

// ---------- build ----------
const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const pct = v => v == null ? 'n/a' : (Math.round(v * 1000) / 10) + '%';
const badge = st => ({ pass:'<span class="b pass">PASS</span>', fail:'<span class="b fail">FAIL</span>', skipped:'<span class="b skip">SKIPPED</span>', missing:'<span class="b miss">NOT RUN</span>' }[st] || st);

const rowCov = {};        // rowId -> {completed, taken} for diagram highlight
const skipped = [];
let sections = '';

for (const cat of spec.categories) {
  const dgKey = cat.layer === 'process' ? 'proc-main' : cat.layer === 'component' ? 'comp-tools' : 'pint-main';
  const reqs = [...cat.requirements].sort(byId);
  let rows = '';
  for (const req of reqs) {
    const r = statusFor(req, cat.run);
    if (r.status === 'skipped') skipped.push({ id: req.id, statement: req.statement, message: r.message });

    // test label + instruction breakdown (process scenarios only, from .test.json)
    let label, detail = '';
    if (req.scenarioIndex && testCases[req.scenarioIndex - 1]) {
      const tc = testCases[req.scenarioIndex - 1];
      label = 'ProcessTest › ' + tc.name;
      rowCov[req.id] = procScenarioCov[req.scenarioIndex - 1] || { completed: [], taken: [] };
      const steps = (tc.instructions || []).map(translate)
        .map(s => `<li class="${s.kind}"><span class="tag ${s.kind}">${s.kind === 'assert' ? 'ASSERT' : 'ACT'}</span>${esc(s.text)}</li>`).join('');
      detail = `<ol class="steps">${steps}</ol>`;
    } else if (req.match === 'aggregate') {
      label = 'ProcessTest (all scenarios)';
      detail = `<p class="muted">Aggregate over all process scenarios — green only if every scenario passed.</p>`;
    } else {
      label = `${req.test}.${req.match}`;
      detail = `<p class="muted">Java integration test. See ${esc(label)} for assertions.</p>`;
    }

    const hl = rowCov[req.id] ? ` data-dg="${dgKey}" data-id="${req.id}"` : '';
    rows += `<tr class="req${rowCov[req.id] ? ' clickable' : ''}"${hl} data-detail="det-${req.id}">
       <td class="id">${esc(req.id)}</td><td>${esc(req.statement)}</td><td>${badge(r.status)}</td></tr>
       <tr class="detail" id="det-${req.id}"><td colspan="3"><div class="testname">Test: <code>${esc(label)}</code></div>${detail}</td></tr>`;
  }
  const hint = cat.layer === 'process'
    ? 'Click a requirement to expand its steps and highlight the path that test instance took.'
    : 'Click a requirement to expand its steps.';
  sections += `<section><h2>${esc(cat.name)}</h2><p class="blurb">${esc(cat.blurb)}</p>
    <div class="split">
      <div class="diag-col"><div class="diagram" id="dg-${dgKey}"></div></div>
      <div class="req-col">
        <p class="hint">${hint}</p>
        <table><thead><tr><th>ID</th><th>Business requirement</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table>
      </div>
    </div></section>`;
}

const procCov = coverageFor('process', spec.processId);
const pintCov = coverageFor('integration', spec.processId);
const bands = [
  { label: 'Process tests', actual: procCov ? procCov.coverage : null, threshold: spec.thresholds.process },
  { label: 'Process integration', actual: pintCov ? pintCov.coverage : null, threshold: spec.thresholds.processIntegration },
];
const bandHtml = bands.map(b => {
  const ok = b.actual != null && b.actual + 1e-9 >= b.threshold;
  return `<div class="band ${ok ? 'ok' : 'under'}"><div class="bl">${esc(b.label)}</div><div class="bv">${pct(b.actual)}</div><div class="bt">threshold ${pct(b.threshold)} ${ok ? '✓' : '✗'}</div></div>`;
}).join('');
const skipHtml = skipped.length ? `<div class="skipbox"><h3>Skipped tests (${skipped.length})</h3>` +
  skipped.map(s => `<div class="skiprow"><b>${esc(s.id)}</b> ${esc(s.statement)}<br><span class="reason">${esc(s.message || 'skipped')}</span></div>`).join('') + `</div>` : '';

const html = `<!doctype html><html><head><meta charset="utf-8"><title>Claims Processing Agent — CPT Requirement Report</title>
<style>
 body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;margin:0;color:#1f2430;background:#f6f7f9}
 header{background:#101521;color:#fff;padding:18px 28px}header h1{margin:0;font-size:20px}
 header .sub{color:#9aa4b2;font-size:13px;margin-top:4px}
 main{padding:24px 32px;max-width:1600px;margin:0 auto}
 .split{display:flex;gap:20px;align-items:flex-start}
 .diag-col{flex:1 1 56%;min-width:0;position:sticky;top:14px}
 .req-col{flex:1 1 44%;min-width:0}
 @media(max-width:980px){.split{flex-direction:column}.diag-col{position:static;width:100%}}
 .testname{font-size:11px;color:#586174;font-family:ui-monospace,monospace;margin:2px 0 8px;word-break:break-all}
 .testname code{background:#eef2f7;padding:1px 5px;border-radius:4px}
 .bands{display:flex;gap:16px;margin-bottom:24px;flex-wrap:wrap}
 .band{flex:1;min-width:220px;border-radius:10px;padding:14px 16px;background:#fff;border:1px solid #e3e6ea}
 .band.ok{border-left:5px solid #16a34a}.band.under{border-left:5px solid #dc2626}
 .bl{font-size:13px;color:#586174}.bv{font-size:28px;font-weight:700}.bt{font-size:12px;color:#586174}
 section{background:#fff;border:1px solid #e3e6ea;border-radius:10px;padding:18px 20px;margin-bottom:22px}
 h2{margin:0 0 4px;font-size:17px}.blurb{margin:0 0 12px;color:#586174;font-size:13px}.hint{font-size:12px;color:#8a93a2;margin:2px 0 12px}
 table{width:100%;border-collapse:collapse;font-size:13px;margin-bottom:6px}
 th,td{text-align:left;padding:8px 10px;border-bottom:1px solid #eef0f3;vertical-align:top}
 th{color:#586174;font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.03em}
 td.id{font-weight:700;white-space:nowrap}td.t{color:#586174;font-family:ui-monospace,monospace;font-size:11px}
 tr.req.clickable{cursor:pointer}tr.req.clickable:hover{background:#f3f8ff}tr.req.active{background:#eaf2ff}
 tr.detail{display:none}tr.detail.open{display:table-row}tr.detail td{background:#fbfcfe}
 ol.steps{margin:6px 0;padding-left:20px}ol.steps li{margin:3px 0;font-size:12.5px}
 ol.steps li.assert{color:#0b6}ol.steps li.action{color:#334155}
 .tag{display:inline-block;font-size:9px;font-weight:700;padding:1px 5px;border-radius:4px;margin-right:6px;color:#fff;vertical-align:middle}
 .tag.assert{background:#16a34a}.tag.action{background:#64748b}
 .muted{color:#8a93a2;font-size:12px;margin:6px 0}
 .b{font-size:11px;font-weight:700;padding:2px 8px;border-radius:20px;color:#fff;white-space:nowrap}
 .b.pass{background:#16a34a}.b.fail{background:#dc2626}.b.skip{background:#9aa4b2}.b.miss{background:#cbd5e1;color:#334155}
 .diagram{height:520px;border:1px solid #eef0f3;border-radius:8px;background:#fbfbfc}
 .skipbox{background:#fff;border:1px solid #e3e6ea;border-left:5px solid #9aa4b2;border-radius:10px;padding:16px 20px;margin-bottom:22px}
 .skiprow{font-size:13px;margin:8px 0}.reason{color:#586174;font-size:12px}
 .cov .djs-visual>:is(rect,circle,polygon){stroke:#16a34a !important;stroke-width:2px !important;fill:#dcfce7 !important}
 .cov .djs-visual>path{stroke:#16a34a !important;stroke-width:2px !important}
 .covf .djs-visual>path{stroke:#16a34a !important;stroke-width:3px !important}
</style></head><body>
<header><h1>Claims Processing Agent — CPT Requirement Report</h1>
<div class="sub">Three layers, each requirement proven by a named test. Expand a row for its steps; green = the path that instance took.</div></header>
<main>
 <div class="bands">${bandHtml}</div>
 ${skipHtml}
 ${sections}
</main>
<script>${vendor}</script>
<script>
 const DIAGRAMS = ${JSON.stringify(diagrams)};
 const ROWCOV = ${JSON.stringify(rowCov)};
 const VIEWERS = {};
 function clearMarkers(key){ const v=VIEWERS[key]; if(!v) return; const c=v.get('canvas'); const reg=v.get('elementRegistry');
   reg.getAll().forEach(e=>{ try{c.removeMarker(e.id,'cov');c.removeMarker(e.id,'covf');}catch(_){} }); }
 function highlight(key, cov){ const v=VIEWERS[key]; if(!v||!cov) return; const c=v.get('canvas'); clearMarkers(key);
   (cov.completed||[]).forEach(id=>{try{c.addMarker(id,'cov')}catch(_){}}); (cov.taken||[]).forEach(id=>{try{c.addMarker(id,'covf')}catch(_){}}); }
 (async () => {
   for (const d of DIAGRAMS) {
     const el = document.getElementById('dg-' + d.key); if (!el) continue;
     const viewer = new BpmnJS({ container: el });
     try { await viewer.importXML(d.xml); viewer.get('canvas').zoom('fit-viewport'); VIEWERS[d.key]=viewer;
       (d.completed||[]).forEach(id=>{try{viewer.get('canvas').addMarker(id,'cov')}catch(_){}});
       (d.taken||[]).forEach(id=>{try{viewer.get('canvas').addMarker(id,'covf')}catch(_){}});
     } catch(e){ el.innerHTML='<p style="padding:12px;color:#dc2626">diagram error: '+e.message+'</p>'; }
   }
   document.querySelectorAll('tr.req').forEach(tr => {
     tr.addEventListener('click', () => {
       const det = document.getElementById(tr.getAttribute('data-detail'));
       const table = tr.closest('table');
       const wasOpen = det && det.classList.contains('open');
       // accordion: collapse all open rows in this table, then open this one if it was closed
       table.querySelectorAll('tr.detail.open').forEach(d => d.classList.remove('open'));
       if (det && !wasOpen) det.classList.add('open');
       const id = tr.getAttribute('data-id'), dg = tr.getAttribute('data-dg');
       if (id && dg && ROWCOV[id]) {
         document.querySelectorAll('tr.req.active').forEach(x=>x.classList.remove('active'));
         tr.classList.add('active');
         highlight(dg, ROWCOV[id]);
       }
     });
   });
 })();
</script>
</body></html>`;

writeFileSync(out, html);
console.log('wrote', out);
console.log('process coverage:', procCov ? pct(procCov.coverage) : 'n/a', '| process-integration:', pintCov ? pct(pintCov.coverage) : 'n/a');
console.log('skipped:', skipped.map(s => s.id).join(', ') || 'none');
const under = bands.filter(b => !(b.actual != null && b.actual + 1e-9 >= b.threshold));
if (under.length) { console.error('COVERAGE GATE FAILED: ' + under.map(b => `${b.label} ${pct(b.actual)} < ${pct(b.threshold)}`).join('; ')); process.exitCode = 1; }
else console.log('coverage gate: OK');
