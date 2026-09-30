<template>
  <DiagramFrame>
  <div class="arch-diagram-wrap">
    <svg class="arch-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1172 768" role="img" aria-label="Kinotic three-server network architecture: every caller, person or workload, reaches one of three public HTTPS hosts, each answered by its own server with its own gateway, SecurityService and session cookie: the app server for application users and runtime workloads, the org server for organization members, GitHub webhooks and sync workloads, the system server for operators and vm-managers. The three servers form one Ignite cluster in which each hosts only its own zones, and reach the shared Elasticsearch and Loki stores only through participant-scoped access.">

      <defs>
        <marker id="ma-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-ink" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ma-vio" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-vio" points="0,0 10,5 0,10"></polygon>
        </marker>
      </defs>

      <!-- ═════════ callers band ═════════ -->
      <text class="t-tag" x="24" y="26">CALLERS · UNTRUSTED UNTIL A GATEWAY AUTHENTICATES THEM</text>

      <!-- app plane callers -->
      <rect class="chip" x="46" y="44" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="114" y="63" text-anchor="middle">App users</text>
      <text class="t-sub"  x="114" y="79" text-anchor="middle">UIs · API clients</text>

      <rect class="chip-w" x="190" y="44" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="258" y="63" text-anchor="middle">Runtime workloads</text>
      <text class="t-sub"  x="258" y="79" text-anchor="middle">APP_RUNTIME</text>

      <!-- org plane callers -->
      <rect class="chip" x="360" y="44" width="140" height="46" rx="8"></rect>
      <text class="t-chip" x="430" y="72" text-anchor="middle">GitHub webhooks</text>

      <rect class="chip" x="508" y="44" width="160" height="46" rx="8"></rect>
      <text class="t-chip" x="588" y="63" text-anchor="middle">Org members</text>
      <text class="t-sub"  x="588" y="79" text-anchor="middle">portal · CLI · MCP hosts</text>

      <rect class="chip-w" x="676" y="44" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="744" y="63" text-anchor="middle">Sync workloads</text>
      <text class="t-sub"  x="744" y="79" text-anchor="middle">PROJECT_SYNC</text>

      <!-- system plane callers -->
      <rect class="chip" x="846" y="44" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="914" y="63" text-anchor="middle">Operators</text>
      <text class="t-sub"  x="914" y="79" text-anchor="middle">system console</text>

      <rect class="chip-w" x="990" y="44" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="1058" y="63" text-anchor="middle">vm-managers</text>
      <text class="t-sub"  x="1058" y="79" text-anchor="middle">workload nodes</text>

      <!-- ingress: every path ends at the gateway of the server its host names -->
      <line class="flow" x1="114" y1="90" x2="114" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="258" y1="90" x2="258" y2="246" marker-end="url(#ma-ink)"></line>
      <text class="t-tiny" x="264" y="124">publishes into</text>
      <text class="t-tiny" x="264" y="136">app.&lt;org&gt;.&lt;app&gt;</text>
      <line class="flow" x1="430" y1="90" x2="430" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="588" y1="90" x2="588" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="744" y1="90" x2="744" y2="246" marker-end="url(#ma-ink)"></line>
      <text class="t-tiny" x="750" y="124">syncs entity</text>
      <text class="t-tiny" x="750" y="136">definitions</text>
      <line class="flow" x1="914" y1="90" x2="914" y2="246" marker-end="url(#ma-ink)"></line>
      <!-- the vm-manager connects out and hosts the service the orchestrator starts workloads through -->
      <line class="flow-vio" x1="1058" y1="94" x2="1058" y2="246" marker-start="url(#ma-vio)" marker-end="url(#ma-vio)"></line>
      <text class="t-tiny" x="1064" y="124">hosts</text>
      <text class="t-tiny" x="1064" y="136">VmManager</text>

      <!-- ═════════ private network ═════════ -->
      <rect class="wall" x="20" y="170" width="1132" height="582" rx="12"></rect>

      <!-- ═════════ app plane ═════════ -->
      <rect class="encl-app" x="36" y="214" width="300" height="190" rx="10"></rect>
      <text class="t-plane-a" x="186" y="238" text-anchor="middle">APP PLANE</text>

      <rect class="gw gw-app" x="52" y="250" width="268" height="140" rx="8"></rect>
      <text class="t-name" x="186" y="273" text-anchor="middle">kinotic-server-app</text>
      <text class="t-sub"  x="186" y="289" text-anchor="middle">apps-api.kinotic.ai and *.apps-api</text>
      <line class="sep" x1="70" y1="298" x2="302" y2="298"></line>
      <text class="t-mono" x="186" y="315" text-anchor="middle">ApplicationSecurityService</text>
      <text class="t-mono" x="186" y="330" text-anchor="middle">app users · clients · machines</text>
      <text class="t-mono" x="186" y="345" text-anchor="middle">+ its organization's runtimes</text>
      <text class="t-mono" x="186" y="360" text-anchor="middle">one application per API host</text>
      <text class="t-mono" x="186" y="375" text-anchor="middle">__Host-kinotic-app-session</text>

      <!-- ═════════ org plane ═════════ -->
      <rect class="encl-org" x="352" y="214" width="468" height="190" rx="10"></rect>
      <text class="t-plane-o" x="509" y="238" text-anchor="middle">ORG PLANE</text>

      <rect class="gw gw-org" x="368" y="250" width="436" height="140" rx="8"></rect>
      <text class="t-name" x="586" y="273" text-anchor="middle">kinotic-server-org</text>
      <text class="t-sub"  x="586" y="289" text-anchor="middle">api.kinotic.ai</text>
      <line class="sep" x1="386" y1="298" x2="786" y2="298"></line>
      <text class="t-mono" x="586" y="315" text-anchor="middle">OrganizationSecurityService</text>
      <text class="t-mono" x="586" y="330" text-anchor="middle">org users · clients · machines, except runtimes</text>
      <text class="t-mono" x="586" y="345" text-anchor="middle">login · signup · invites · OAuth · MCP</text>
      <text class="t-mono" x="586" y="360" text-anchor="middle">CLI device grant · GitHub webhook (HMAC)</text>
      <text class="t-mono" x="586" y="375" text-anchor="middle">__Host-kinotic-org-session</text>

      <!-- ═════════ system plane ═════════ -->
      <rect class="encl-sys" x="836" y="214" width="300" height="190" rx="10"></rect>
      <text class="t-plane-s" x="986" y="238" text-anchor="middle">SYSTEM PLANE</text>

      <rect class="gw gw-sys" x="852" y="250" width="268" height="140" rx="8"></rect>
      <text class="t-name" x="986" y="273" text-anchor="middle">kinotic-server-system</text>
      <text class="t-sub"  x="986" y="289" text-anchor="middle">system-api.kinotic.ai</text>
      <line class="sep" x1="870" y1="298" x2="1102" y2="298"></line>
      <text class="t-mono" x="986" y="315" text-anchor="middle">SystemSecurityService</text>
      <text class="t-mono" x="986" y="330" text-anchor="middle">operators · clients · vm-managers</text>
      <text class="t-mono" x="986" y="345" text-anchor="middle">login · OAuth · MCP</text>
      <text class="t-mono" x="986" y="360" text-anchor="middle">deployments · cluster singletons</text>
      <text class="t-mono" x="986" y="375" text-anchor="middle">__Host-kinotic-system-session</text>

      <!-- ═════════ the cluster: each server joins it with its zone partitioning ═════════ -->
      <line class="link" x1="186" y1="390" x2="186" y2="470"></line>
      <text class="t-tiny" x="194" y="432">hosts app-api</text>
      <text class="t-tiny" x="194" y="444">and app.&lt;org&gt;.&lt;app&gt;</text>
      <line class="link" x1="586" y1="390" x2="586" y2="470"></line>
      <text class="t-tiny" x="594" y="432">hosts management-api</text>
      <text class="t-tiny" x="594" y="444">reaches system-api, app-api</text>
      <line class="link" x1="986" y1="390" x2="986" y2="470"></line>
      <text class="t-tiny" x="994" y="432">hosts system-api</text>
      <text class="t-tiny" x="994" y="444">and management-api</text>

      <rect class="bus" x="52" y="470" width="1068" height="32" rx="16"></rect>
      <text class="t-bus" x="586" y="490" text-anchor="middle">ONE IGNITE CLUSTER — EACH SERVER HOSTS ONLY ITS OWN ZONES</text>

      <!-- ═════════ shared data ═════════ -->
      <text class="t-tag" x="36" y="573">SHARED DATA</text>

      <!-- every store sits behind one enforcement point; no line touches a store directly -->
      <line class="data" x1="186" y1="502" x2="186" y2="546"></line>
      <line class="data" x1="586" y1="502" x2="586" y2="546"></line>
      <line class="data" x1="986" y1="502" x2="986" y2="546"></line>
      <rect class="acl" x="150" y="548" width="872" height="42" rx="21"></rect>
      <text class="t-acl" x="586" y="565" text-anchor="middle">SCOPED ACCESS — SYSTEM · ORG · APP</text>
      <text class="t-tiny" x="586" y="581" text-anchor="middle">every query scoped to the calling participant</text>

      <!-- Elasticsearch -->
      <path class="cyl" d="M 390 624 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="446" cy="624" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="446" y="650" text-anchor="middle">Elasticsearch</text>
      <text class="t-tiny" x="446" y="686" text-anchor="middle">os-data · entity indices</text>

      <!-- Loki -->
      <path class="cyl" d="M 670 624 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="726" cy="624" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="726" y="650" text-anchor="middle">Loki logs</text>
      <text class="t-tiny" x="726" y="686" text-anchor="middle">workload logs</text>

      <text class="t-tag" x="36" y="736">KINOTIC OS · PRIVATE NETWORK — ONLY THE GATEWAYS LISTEN PUBLICLY, ON :443</text>
    </svg>
  </div>
  </DiagramFrame>
</template>

<style>
/* Palette follows the site color-mode class, so the diagram tracks the theme toggle. */
svg.arch-diagram {
    --bg: #F6F7F9;
    --surface: #FFFFFF;
    --ink: #1A2332;
    --muted: #5C6879;
    --line: #D8DDE5;
    --indigo: #4A5FD0;
    --indigo-tint: rgba(74, 95, 208, 0.10);
    --green: #2C8F5E;
    --green-tint: rgba(44, 143, 94, 0.10);
    --violet: #7C4FD0;
    --violet-tint: rgba(124, 79, 208, 0.08);
    --amber: #A97C22;
    --pink: #B0487E;
    --vnet-tint: rgba(100, 116, 139, 0.07);
}
.dark svg.arch-diagram {
  --bg: #0D1320;
  --surface: #151D2D;
  --ink: #E6EBF4;
  --muted: #96A2B6;
  --line: #2B3648;
  --indigo: #7E8EF0;
  --indigo-tint: rgba(126, 142, 240, 0.14);
  --green: #48B482;
  --green-tint: rgba(72, 180, 130, 0.13);
  --violet: #A47EF0;
  --violet-tint: rgba(164, 126, 240, 0.12);
  --amber: #D3A24C;
  --pink: #D879AC;
  --vnet-tint: rgba(148, 163, 184, 0.08);
}

.arch-diagram-wrap { overflow-x: auto; }
svg.arch-diagram { min-width: 700px; width: 100%; height: auto; display: block; }

  /* ── SVG vocabulary ─────────────────────────────── */
  svg.arch-diagram .wall      { fill: var(--vnet-tint); stroke: var(--line); stroke-width: 1.5; }
  svg.arch-diagram .chip      { fill: var(--surface); stroke: var(--line); stroke-width: 1.25; }
  svg.arch-diagram .chip-w    { fill: var(--surface); stroke: var(--amber); stroke-width: 1.25; }
  svg.arch-diagram .gw        { fill: var(--surface); stroke-width: 2; }
  svg.arch-diagram .gw-org    { stroke: var(--indigo); }
  svg.arch-diagram .gw-sys    { stroke: var(--violet); }
  svg.arch-diagram .gw-app    { stroke: var(--green); }
  svg.arch-diagram .encl-app  { fill: var(--green-tint);  stroke: var(--green);  stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.arch-diagram .encl-org  { fill: var(--indigo-tint); stroke: var(--indigo); stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.arch-diagram .encl-sys  { fill: var(--violet-tint); stroke: var(--violet); stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.arch-diagram .bus       { fill: var(--surface); stroke: var(--ink); stroke-width: 1.5; }
  svg.arch-diagram .cyl       { fill: var(--surface); stroke: var(--pink); stroke-width: 1.5; }
  svg.arch-diagram .acl       { fill: var(--surface); stroke: var(--pink); stroke-width: 1.5; }
  svg.arch-diagram .t-acl     { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.1em; font-weight: 600; fill: var(--pink); }
  svg.arch-diagram .sep       { stroke: var(--line); stroke-width: 1; }

  svg.arch-diagram .flow      { stroke: var(--ink);    stroke-width: 1.6; fill: none; }
  svg.arch-diagram .flow-vio  { stroke: var(--violet); stroke-width: 1.6; fill: none; }
  svg.arch-diagram .link      { stroke: var(--muted);  stroke-width: 1.4; fill: none; }
  svg.arch-diagram .data      { stroke: var(--muted);  stroke-width: 1.3; fill: none; stroke-dasharray: 2 4; stroke-linecap: round; }

  svg.arch-diagram .mk-ink { fill: var(--ink); }
  svg.arch-diagram .mk-vio { fill: var(--violet); }

  svg.arch-diagram text { font-family: "Avenir Next", "Segoe UI", system-ui, sans-serif; }
  svg.arch-diagram .t-name  { font-size: 13px; font-weight: 600; fill: var(--ink); }
  svg.arch-diagram .t-sub   { font-size: 11px; fill: var(--muted); }
  svg.arch-diagram .t-chip  { font-size: 12px; font-weight: 500; fill: var(--ink); }
  svg.arch-diagram .t-mono  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--muted); }
  svg.arch-diagram .t-tag   { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; fill: var(--muted); }
  svg.arch-diagram .t-tiny  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 9.5px; fill: var(--muted); }
  svg.arch-diagram .t-bus   { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.1em; font-weight: 600; fill: var(--ink); }
  svg.arch-diagram .t-plane-o { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--indigo); }
  svg.arch-diagram .t-plane-a { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--green); }
  svg.arch-diagram .t-plane-s { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--violet); }
</style>
