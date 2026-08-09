<template>
  <div class="arch-diagram-wrap">
    <svg class="arch-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1172 825" role="img" aria-label="Kinotic three-plane network architecture: internet clients reach two public gateways over HTTPS; the system server is reachable only through a VPN gate or from inside the Azure VNet; the app plane is an isolated island; shared data stores are the only coupling between the buses.">

      <defs>
        <marker id="ma-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-ink" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ma-red" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-red" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ma-ind" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-ind" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ma-vio" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-vio" points="0,0 10,5 0,10"></polygon>
        </marker>
      </defs>

      <!-- ═════════ internet band ═════════ -->
      <text class="t-tag" x="24" y="26">INTERNET · UNTRUSTED</text>

      <rect class="chip" x="40" y="44" width="205" height="46" rx="8"></rect>
      <text class="t-chip" x="142" y="63" text-anchor="middle">Customer microservices</text>
      <text class="t-sub"  x="142" y="79" text-anchor="middle">+ app machines</text>

      <rect class="chip" x="262" y="44" width="118" height="46" rx="8"></rect>
      <text class="t-chip" x="321" y="72" text-anchor="middle">App end users</text>

      <rect class="chip" x="396" y="44" width="146" height="46" rx="8"></rect>
      <text class="t-chip" x="469" y="72" text-anchor="middle">GitHub webhooks</text>

      <rect class="chip" x="560" y="44" width="150" height="46" rx="8"></rect>
      <text class="t-chip" x="635" y="72" text-anchor="middle">Org users · portal</text>

      <rect class="chip" x="728" y="44" width="140" height="46" rx="8"></rect>
      <text class="t-chip" x="798" y="72" text-anchor="middle">CLI / MCP hosts</text>

      <rect class="chip" x="955" y="44" width="160" height="46" rx="8"></rect>
      <text class="t-chip" x="1035" y="63" text-anchor="middle">System users</text>
      <text class="t-sub"  x="1035" y="79" text-anchor="middle">system console</text>

      <!-- public ingress arrows (never through the VPN) -->
      <line class="flow" x1="142" y1="90" x2="142" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="321" y1="90" x2="321" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="469" y1="90" x2="469" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="635" y1="90" x2="635" y2="246" marker-end="url(#ma-ink)"></line>
      <line class="flow" x1="798" y1="90" x2="775" y2="246" marker-end="url(#ma-ink)"></line>
      <!-- the only internet path to the system plane -->
      <line class="flow-red" x1="1035" y1="90" x2="1035" y2="154" marker-end="url(#ma-red)"></line>

      <!-- ═════════ VNet wall ═════════ -->
      <rect class="wall" x="24" y="170" width="1124" height="618" rx="12"></rect>
      <text class="t-tag" x="36" y="196">KINOTIC OS · PRIVATE AZURE VNET — NO OTHER INBOUND</text>

      <!-- listener labels at the wall crossings -->
      <text class="t-tiny" x="152" y="164">:443</text>
      <text class="t-tiny" x="479" y="164">:443</text>
      <text class="t-tiny" x="645" y="164">:443</text>
      <text class="t-tiny" x="797" y="164">:443</text>

      <!-- ═════════ app plane island ═════════ -->
      <rect class="encl-app" x="40" y="214" width="330" height="372" rx="10"></rect>
      <text class="t-plane-a" x="56" y="238">APP PLANE — ISOLATED ISLAND</text>

      <rect class="gw gw-app" x="60" y="250" width="290" height="112" rx="8"></rect>
      <text class="t-name" x="205" y="273" text-anchor="middle">kinotic-app-server</text>
      <text class="t-sub"  x="205" y="289" text-anchor="middle">public gateway</text>
      <line class="sep" x1="78" y1="298" x2="332" y2="298"></line>
      <text class="t-mono" x="205" y="315" text-anchor="middle">AppSecurityService · app participants only</text>
      <text class="t-mono" x="205" y="330" text-anchor="middle">app-api zone</text>
      <text class="t-mono" x="205" y="345" text-anchor="middle">app.&lt;org&gt;.&lt;app&gt; customer zones</text>

      <line class="link" x1="205" y1="362" x2="205" y2="430"></line>
      <rect class="bus-a" x="60" y="430" width="290" height="32" rx="16"></rect>
      <text class="t-bus t-bus-a" x="205" y="450" text-anchor="middle">APP BUS · VERT.X CLUSTER B</text>

      <!-- ═════════ platform plane ═════════ -->
      <rect class="encl-plat" x="400" y="214" width="736" height="372" rx="10"></rect>
      <text class="t-plane-p" x="416" y="238">OS CORE</text>

      <rect class="gw gw-os" x="430" y="250" width="360" height="112" rx="8"></rect>
      <text class="t-name" x="610" y="273" text-anchor="middle">kinotic-org-server</text>
      <text class="t-sub"  x="610" y="289" text-anchor="middle">public gateway</text>
      <line class="sep" x1="448" y1="298" x2="772" y2="298"></line>
      <text class="t-mono" x="610" y="315" text-anchor="middle">OrgSecurityService · org users · delegates · machines</text>
      <text class="t-mono" x="610" y="330" text-anchor="middle">os-api zone · GitHub webhook (HMAC)</text>
      <text class="t-mono" x="610" y="345" text-anchor="middle">app-api (dual-hosted)</text>

      <!-- system enclosure: the inner boundary -->
      <rect class="encl-sys" x="884" y="226" width="236" height="276" rx="10"></rect>
      <text class="t-plane-s" x="896" y="246">VPN + VNET-INTERNAL ONLY</text>

      <rect class="gw gw-sys" x="900" y="260" width="204" height="130" rx="8"></rect>
      <text class="t-name" x="1002" y="282" text-anchor="middle">kinotic-system-server</text>
      <text class="t-no"   x="1002" y="299" text-anchor="middle">NO public listener</text>
      <line class="sep" x1="916" y1="308" x2="1088" y2="308"></line>
      <text class="t-mono" x="1002" y="324" text-anchor="middle">SystemSecurityService</text>
      <text class="t-mono" x="1002" y="339" text-anchor="middle">Entra SSO · workload identity</text>
      <text class="t-mono" x="1002" y="354" text-anchor="middle">system zone · orchestrator</text>
      <text class="t-mono" x="1002" y="369" text-anchor="middle">system console SPA</text>

      <!-- VPN gate straddling the wall -->
      <polygon class="vpn" points="1003,178 1019,156 1051,156 1067,178 1051,200 1019,200"></polygon>
      <text class="t-vpn" x="1035" y="182" text-anchor="middle">VPN</text>
      <line class="flow-red" x1="1035" y1="200" x2="1035" y2="254" marker-end="url(#ma-red)"></line>

      <!-- narrow waist dispatch -->
      <line class="flow-ind" x1="790" y1="306" x2="894" y2="306" marker-end="url(#ma-ind)"></line>
      <text class="t-tiny" x="840" y="290" text-anchor="middle">job dispatch</text>
      <text class="t-tiny" x="840" y="326" text-anchor="middle">narrow waist</text>
      <text class="t-tiny" x="840" y="338" text-anchor="middle">RBAC</text>

      <!-- platform bus -->
      <line class="link" x1="610" y1="362" x2="610" y2="520"></line>
      <line class="link" x1="930" y1="390" x2="930" y2="520"></line>
      <rect class="bus-p" x="430" y="520" width="560" height="32" rx="16"></rect>
      <text class="t-bus t-bus-p" x="710" y="540" text-anchor="middle">OS BUS · VERT.X CLUSTER A — ZONE RULES + RBAC</text>

      <!-- ═════════ workload nodes ═════════ -->
      <rect class="wbox" x="808" y="600" width="312" height="112" rx="10"></rect>
      <text class="t-plane-w" x="964" y="621" text-anchor="middle">WORKLOAD NODES · AZURE VMS</text>
      <rect class="chip" x="824" y="634" width="138" height="62" rx="8"></rect>
      <text class="t-chip" x="893" y="654" text-anchor="middle">vm-manager</text>
      <text class="t-tiny" x="893" y="670" text-anchor="middle">Entra workload</text>
      <text class="t-tiny" x="893" y="682" text-anchor="middle">identity</text>
      <rect class="chip" x="972" y="634" width="134" height="62" rx="8"></rect>
      <text class="t-chip" x="1039" y="652" text-anchor="middle">workloads</text>
      <text class="t-tiny" x="1039" y="667" text-anchor="middle">boxlite sandbox</text>
      <text class="t-tiny" x="1039" y="681" text-anchor="middle">secret-ref tokens</text>

      <!-- vm-manager ↔ system server (VNet-internal, no VPN) -->
      <line class="flow-vio" x1="1015" y1="600" x2="1015" y2="504" marker-end="url(#ma-vio)"></line>
      <line class="flow-vio" x1="1062" y1="504" x2="1062" y2="598" marker-end="url(#ma-vio)"></line>
      <text class="t-tiny" x="1008" y="566" text-anchor="end">Entra token</text>
      <text class="t-tiny" x="1008" y="578" text-anchor="end">VNet-internal</text>
      <text class="t-tiny" x="1069" y="554">start</text>
      <text class="t-tiny" x="1069" y="566">workloads</text>
      <text class="t-tiny" x="1069" y="578">@node</text>

      <!-- ═════════ shared data ═════════ -->
      <text class="t-tag" x="40" y="636">SHARED DATA</text>

      <!-- every store sits behind one enforcement point; no line touches a store directly -->
      <rect class="acl" x="150" y="646" width="570" height="42" rx="21"></rect>
      <text class="t-acl" x="435" y="663" text-anchor="middle">SCOPED ACCESS — SYSTEM · ORG · APP</text>
      <text class="t-tiny" x="435" y="679" text-anchor="middle">the only coupling between the buses</text>

      <!-- entity ES -->
      <path class="cyl" d="M 194 714 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="250" cy="714" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="250" y="740" text-anchor="middle">entity ES</text>
      <text class="t-tiny" x="250" y="776" text-anchor="middle">tenant entity data</text>

      <!-- os-data ES -->
      <path class="cyl" d="M 414 714 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="470" cy="714" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="470" y="740" text-anchor="middle">os-data ES</text>
      <text class="t-tiny" x="470" y="776" text-anchor="middle">identities · configs · defs · job runs</text>

      <!-- Loki -->
      <path class="cyl" d="M 604 714 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="660" cy="714" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="660" y="740" text-anchor="middle">Loki logs</text>
      <text class="t-tiny" x="660" y="776" text-anchor="middle">workload logs</text>

      <!-- data access: one dotted line per plane, terminating at the principal bar -->
      <line class="data" x1="205" y1="462" x2="240" y2="644"></line>
      <text class="t-tiny" x="228" y="536">entity R/W</text>
      <text class="t-tiny" x="228" y="548">os-data read-only</text>
      <line class="data" x1="610" y1="552" x2="540" y2="644"></line>
      <line class="data" x1="808" y1="678" x2="722" y2="662"></line>
      <text class="t-tiny" x="766" y="654" text-anchor="middle">logs</text>
    </svg>
  </div>
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
    --red: #C64A4A;
    --red-tint: rgba(198, 74, 74, 0.10);
    --amber: #A97C22;
    --amber-tint: rgba(169, 124, 34, 0.10);
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
  --red: #E06A6A;
  --red-tint: rgba(224, 106, 106, 0.14);
  --amber: #D3A24C;
  --amber-tint: rgba(211, 162, 76, 0.13);
  --pink: #D879AC;
  --vnet-tint: rgba(148, 163, 184, 0.08);
}

.arch-diagram-wrap { overflow-x: auto; }
svg.arch-diagram { min-width: 700px; width: 100%; height: auto; display: block; }

  /* ── SVG vocabulary ─────────────────────────────── */
  svg.arch-diagram .wall      { fill: var(--vnet-tint); stroke: var(--line); stroke-width: 1.5; }
  svg.arch-diagram .chip      { fill: var(--surface); stroke: var(--line); stroke-width: 1.25; }
  svg.arch-diagram .gw        { fill: var(--surface); stroke-width: 2; }
  svg.arch-diagram .gw-os     { stroke: var(--indigo); }
  svg.arch-diagram .gw-sys    { stroke: var(--violet); }
  svg.arch-diagram .gw-app    { stroke: var(--green); }
  svg.arch-diagram .encl-app  { fill: var(--green-tint);  stroke: var(--green);  stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.arch-diagram .encl-plat { fill: var(--indigo-tint); stroke: var(--indigo); stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.arch-diagram .encl-sys  { fill: var(--violet-tint); stroke: var(--violet); stroke-width: 1.5;  stroke-dasharray: 6 5; }
  svg.arch-diagram .bus-p     { fill: var(--indigo-tint); stroke: var(--indigo); stroke-width: 1.5; }
  svg.arch-diagram .bus-a     { fill: var(--green-tint);  stroke: var(--green);  stroke-width: 1.5; }
  svg.arch-diagram .wbox      { fill: var(--amber-tint);  stroke: var(--amber);  stroke-width: 1.5; }
  svg.arch-diagram .vpn       { fill: var(--red-tint);    stroke: var(--red);    stroke-width: 2; }
  svg.arch-diagram .cyl       { fill: var(--surface); stroke: var(--pink); stroke-width: 1.5; }
  svg.arch-diagram .acl       { fill: var(--surface); stroke: var(--pink); stroke-width: 1.5; }
  svg.arch-diagram .t-acl     { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.1em; font-weight: 600; fill: var(--pink); }
  svg.arch-diagram .sep       { stroke: var(--line); stroke-width: 1; }

  svg.arch-diagram .flow      { stroke: var(--ink);    stroke-width: 1.6; fill: none; }
  svg.arch-diagram .flow-red  { stroke: var(--red);    stroke-width: 2;   fill: none; }
  svg.arch-diagram .flow-ind  { stroke: var(--indigo); stroke-width: 1.8; fill: none; }
  svg.arch-diagram .flow-vio  { stroke: var(--violet); stroke-width: 1.6; fill: none; }
  svg.arch-diagram .link      { stroke: var(--muted);  stroke-width: 1.4; fill: none; }
  svg.arch-diagram .data      { stroke: var(--muted);  stroke-width: 1.3; fill: none; stroke-dasharray: 2 4; stroke-linecap: round; }

  svg.arch-diagram .mk-ink { fill: var(--ink); }
  svg.arch-diagram .mk-red { fill: var(--red); }
  svg.arch-diagram .mk-ind { fill: var(--indigo); }
  svg.arch-diagram .mk-vio { fill: var(--violet); }

  svg.arch-diagram text { font-family: "Avenir Next", "Segoe UI", system-ui, sans-serif; }
  svg.arch-diagram .t-name  { font-size: 13px; font-weight: 600; fill: var(--ink); }
  svg.arch-diagram .t-sub   { font-size: 11px; fill: var(--muted); }
  svg.arch-diagram .t-chip  { font-size: 12px; font-weight: 500; fill: var(--ink); }
  svg.arch-diagram .t-mono  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--muted); }
  svg.arch-diagram .t-tag   { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; fill: var(--muted); }
  svg.arch-diagram .t-tiny  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 9.5px; fill: var(--muted); }
  svg.arch-diagram .t-bus   { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.1em; font-weight: 600; }
  svg.arch-diagram .t-bus-p { fill: var(--indigo); }
  svg.arch-diagram .t-bus-a { fill: var(--green); }
  svg.arch-diagram .t-plane-p { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--indigo); }
  svg.arch-diagram .t-plane-a { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--green); }
  svg.arch-diagram .t-plane-s { font-family: ui-monospace, Menlo, monospace; font-size: 9.5px; letter-spacing: 0.1em; font-weight: 600; fill: var(--violet); }
  svg.arch-diagram .t-plane-w { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.12em; font-weight: 600; fill: var(--amber); }
  svg.arch-diagram .t-vpn   { font-size: 11px; font-weight: 700; fill: var(--red); }
  svg.arch-diagram .t-no    { font-size: 11px; font-weight: 700; fill: var(--violet); }
</style>
