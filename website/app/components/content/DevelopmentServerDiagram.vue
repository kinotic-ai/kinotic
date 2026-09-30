<template>
  <DiagramFrame>
  <div class="dev-server-diagram-wrap">
    <svg class="dev-server-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1172 900" role="img" aria-label="Development server topology: GitHub, peers and application users reach one forwarded port, 443, on the edge, an HAProxy container that passes each TLS connection unopened to the server its SNI names; the org, system and app servers live on a private NAT-only network with three Elasticsearch nodes and the one-shot migration, and form one Ignite cluster; Loki, Tempo, Mimir and Grafana are containers on the LAN; Azure keeps Front Door, the sites storage account, email, DNS, a Key Vault and a snapshot container; each Elasticsearch node has a ZFS pool on its own drive and Proxmox a drive of its own; two Intel NUCs beside the host run the vm-manager with Cloud Hypervisor micro VMs, which reach their servers through the edge by name.">

      <defs>
        <marker id="ds-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-ink" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ds-ind" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-ind" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ds-vio" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-vio" points="0,0 10,5 0,10"></polygon>
        </marker>
        <marker id="ds-amb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <polygon class="mk-amb" points="0,0 10,5 0,10"></polygon>
        </marker>
      </defs>

      <!-- ═════════ internet band ═════════ -->
      <text class="t-tag" x="24" y="26">PEERS · USERS · INTERNET</text>

      <rect class="chip" x="40" y="44" width="160" height="46" rx="8"></rect>
      <text class="t-chip" x="120" y="63" text-anchor="middle">GitHub</text>
      <text class="t-sub"  x="120" y="79" text-anchor="middle">App webhooks · repo fetch</text>

      <rect class="chip" x="215" y="44" width="190" height="46" rx="8"></rect>
      <text class="t-chip" x="310" y="63" text-anchor="middle">Peers</text>
      <text class="t-sub"  x="310" y="79" text-anchor="middle">portal · CLI · MCP hosts</text>

      <rect class="chip" x="420" y="44" width="180" height="46" rx="8"></rect>
      <text class="t-chip" x="510" y="63" text-anchor="middle">Application users</text>
      <text class="t-sub"  x="510" y="79" text-anchor="middle">published UIs</text>

      <!-- inbound: one forwarded port, to the edge -->
      <line class="flow" x1="120" y1="90" x2="120" y2="344" marker-end="url(#ds-ink)"></line>
      <text class="t-tiny" x="128" y="256">webhook → dev-api</text>
      <line class="flow" x1="310" y1="90" x2="310" y2="344" marker-end="url(#ds-ink)"></line>
      <text class="t-tiny" x="318" y="256">dev-api · dev-system-api</text>
      <line class="flow" x1="510" y1="90" x2="510" y2="344" marker-end="url(#ds-ink)"></line>
      <text class="t-tiny" x="518" y="256">*.dev-apps-api</text>

      <!-- ═════════ Azure, kept ═════════ -->
      <rect class="encl-plat" x="660" y="24" width="488" height="202" rx="10"></rect>
      <text class="t-plane-p" x="676" y="46">AZURE · KEPT</text>

      <rect class="chip" x="676" y="64" width="120" height="46" rx="8"></rect>
      <text class="t-chip" x="736" y="83" text-anchor="middle">ACS</text>
      <text class="t-sub"  x="736" y="99" text-anchor="middle">email</text>

      <rect class="chip" x="812" y="64" width="140" height="46" rx="8"></rect>
      <text class="t-chip" x="882" y="83" text-anchor="middle">Front Door</text>
      <text class="t-sub"  x="882" y="99" text-anchor="middle">*.apps-dev.kinotic.ai</text>

      <rect class="chip" x="978" y="64" width="154" height="46" rx="8"></rect>
      <text class="t-chip" x="1055" y="83" text-anchor="middle">Sites storage account</text>
      <text class="t-sub"  x="1055" y="99" text-anchor="middle">sites/&lt;hostname&gt;/</text>
      <line class="flow-ind" x1="952" y1="87" x2="978" y2="87" marker-end="url(#ds-ind)"></line>

      <rect class="chip" x="676" y="148" width="136" height="46" rx="8"></rect>
      <text class="t-chip" x="744" y="167" text-anchor="middle">Key Vault</text>
      <text class="t-sub"  x="744" y="183" text-anchor="middle">servers' secret storage</text>

      <rect class="chip" x="828" y="148" width="140" height="46" rx="8"></rect>
      <text class="t-chip" x="898" y="167" text-anchor="middle">Blob container</text>
      <text class="t-sub"  x="898" y="183" text-anchor="middle">ES snapshots · daily</text>

      <rect class="chip" x="984" y="148" width="148" height="46" rx="8"></rect>
      <text class="t-chip" x="1058" y="167" text-anchor="middle">DNS zone</text>
      <text class="t-sub"  x="1058" y="183" text-anchor="middle">A records · DNS-01 certs</text>

      <!-- ═════════ the host ═════════ -->
      <rect class="wall" x="24" y="262" width="696" height="620" rx="12"></rect>
      <rect class="wall" x="740" y="262" width="408" height="620" rx="12"></rect>

      <!-- the containers -->
      <rect class="encl-app" x="40" y="306" width="580" height="444" rx="10"></rect>
      <!-- the header sits between the inbound lines -->
      <text class="t-plane-a" x="215" y="330" text-anchor="middle">CONTAINERS</text>
      <text class="t-plane-a" x="410" y="330" text-anchor="middle">ONE PER SERVICE</text>
      <text class="t-plane-a" x="565" y="330" text-anchor="middle">OCI IMAGES</text>

      <!-- the edge, on the LAN and the private network -->
      <rect class="gw gw-edge" x="60" y="344" width="540" height="40" rx="8"></rect>
      <text class="t-name" x="330" y="361" text-anchor="middle">edge · HAProxy :443</text>
      <text class="t-mono" x="330" y="376" text-anchor="middle">reads each connection's SNI and passes it, unopened, to that server</text>

      <!-- the stores, on the LAN -->
      <text class="t-tiny" x="102" y="413" text-anchor="middle">stores on</text>
      <text class="t-tiny" x="102" y="426" text-anchor="middle">the LAN</text>

      <rect class="chip" x="155" y="396" width="80" height="40" rx="8"></rect>
      <text class="t-chip" x="195" y="413" text-anchor="middle">Loki</text>
      <text class="t-tiny" x="195" y="428" text-anchor="middle">:3100 · logs</text>

      <rect class="chip" x="240" y="396" width="80" height="40" rx="8"></rect>
      <text class="t-chip" x="280" y="413" text-anchor="middle">Tempo</text>
      <text class="t-tiny" x="280" y="428" text-anchor="middle">:4318 · :3200</text>

      <rect class="chip" x="340" y="396" width="80" height="40" rx="8"></rect>
      <text class="t-chip" x="380" y="413" text-anchor="middle">Mimir</text>
      <text class="t-tiny" x="380" y="428" text-anchor="middle">:9009</text>

      <rect class="chip" x="425" y="396" width="80" height="40" rx="8"></rect>
      <text class="t-chip" x="465" y="413" text-anchor="middle">Grafana</text>
      <text class="t-tiny" x="465" y="428" text-anchor="middle">:3000 · login</text>

      <text class="t-tiny" x="558" y="413" text-anchor="middle">OTLP from</text>
      <text class="t-tiny" x="558" y="426" text-anchor="middle">servers, nodes</text>

      <!-- the edge → each server, by SNI -->
      <line class="flow" x1="145" y1="384" x2="145" y2="480" marker-end="url(#ds-ink)"></line>
      <line class="flow" x1="330" y1="384" x2="330" y2="480" marker-end="url(#ds-ink)"></line>
      <line class="flow" x1="515" y1="384" x2="515" y2="480" marker-end="url(#ds-ink)"></line>

      <!-- the private network: the servers, three ES nodes on a whole disk each, and the migration -->
      <rect class="encl-priv" x="48" y="452" width="564" height="290" rx="8"></rect>
      <text class="t-plane-v" x="237" y="468" text-anchor="middle">PRIVATE NETWORK</text>
      <text class="t-plane-v" x="422" y="468" text-anchor="middle">NAT OUT</text>

      <rect class="gw gw-app" x="60" y="480" width="170" height="76" rx="8"></rect>
      <text class="t-chip" x="145" y="499" text-anchor="middle">kinotic-server-org</text>
      <text class="t-sub"  x="145" y="514" text-anchor="middle">dev-api · :58503</text>
      <text class="t-mono" x="145" y="531" text-anchor="middle">portal · CLI · MCP</text>
      <text class="t-mono" x="145" y="545" text-anchor="middle">GitHub webhook</text>

      <rect class="gw gw-app" x="245" y="480" width="170" height="76" rx="8"></rect>
      <text class="t-chip" x="330" y="499" text-anchor="middle">kinotic-server-system</text>
      <text class="t-sub"  x="330" y="514" text-anchor="middle">dev-system-api · :58504</text>
      <text class="t-mono" x="330" y="531" text-anchor="middle">console · vm-manager</text>
      <text class="t-mono" x="330" y="545" text-anchor="middle">workload orchestration</text>

      <rect class="gw gw-app" x="430" y="480" width="170" height="76" rx="8"></rect>
      <text class="t-chip" x="515" y="499" text-anchor="middle">kinotic-server-app</text>
      <text class="t-sub"  x="515" y="514" text-anchor="middle">*.dev-apps-api · :58505</text>
      <text class="t-mono" x="515" y="531" text-anchor="middle">every application's API</text>
      <text class="t-mono" x="515" y="545" text-anchor="middle">UIs · runtime workloads</text>

      <!-- the servers to one another and to Elasticsearch -->
      <line class="link" x1="145" y1="556" x2="145" y2="576"></line>
      <line class="link" x1="330" y1="556" x2="330" y2="576"></line>
      <line class="link" x1="515" y1="556" x2="515" y2="576"></line>
      <line class="link" x1="110" y1="576" x2="515" y2="576"></line>
      <line class="link" x1="110" y1="576" x2="110" y2="603"></line>
      <line class="link" x1="290" y1="576" x2="290" y2="603"></line>
      <line class="link" x1="470" y1="576" x2="470" y2="603"></line>
      <text class="t-tiny" x="422" y="570" text-anchor="middle">one Ignite cluster</text>
      <text class="t-tiny" x="200" y="592" text-anchor="middle">Elasticsearch :9200</text>

      <rect class="chip" x="534" y="560" width="70" height="36" rx="8"></rect>
      <text class="t-tiny" x="569" y="575" text-anchor="middle">migration</text>
      <text class="t-tiny" x="569" y="588" text-anchor="middle">runs once</text>

      <path class="cyl" d="M 54 612 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="110" cy="612" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="110" y="632" text-anchor="middle">es-1</text>
      <text class="t-tiny" x="110" y="646" text-anchor="middle">master + data</text>

      <path class="cyl" d="M 234 612 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="290" cy="612" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="290" y="632" text-anchor="middle">es-2</text>
      <text class="t-tiny" x="290" y="646" text-anchor="middle">master + data</text>

      <path class="cyl" d="M 414 612 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="470" cy="612" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="470" y="632" text-anchor="middle">es-3</text>
      <text class="t-tiny" x="470" y="646" text-anchor="middle">master + data</text>

      <text class="t-tiny" x="290" y="690" text-anchor="middle">1 shard · 1 replica per index · no TLS</text>

      <!-- the nodes: their own machines -->
      <rect class="wbox" x="756" y="306" width="376" height="444" rx="10"></rect>
      <text class="t-plane-w" x="772" y="330">NODES · UBUNTU 22.04 · KVM</text>

      <rect class="chip" x="776" y="344" width="180" height="62" rx="8"></rect>
      <text class="t-chip" x="866" y="364" text-anchor="middle">vm-manager</text>
      <text class="t-tiny" x="866" y="380" text-anchor="middle">CLOUD_HYPERVISOR provider</text>
      <text class="t-tiny" x="866" y="394" text-anchor="middle">Alloy · machine credentials</text>

      <rect class="gw gw-node" x="776" y="426" width="336" height="112" rx="8"></rect>
      <text class="t-name" x="944" y="449" text-anchor="middle">Cloud Hypervisor micro VMs</text>
      <line class="sep" x1="794" y1="458" x2="1094" y2="458"></line>
      <text class="t-mono" x="944" y="475" text-anchor="middle">sync VM · runtime VM per microservice · publish VM</text>
      <text class="t-mono" x="944" y="490" text-anchor="middle">dial their server by name, through the edge</text>
      <text class="t-mono" x="944" y="505" text-anchor="middle">egress denied by default · allowlisted names only</text>
      <text class="t-mono" x="944" y="520" text-anchor="middle">stdout and stderr captured, shipped by Alloy</text>

      <rect class="chip" x="776" y="558" width="336" height="62" rx="8"></rect>
      <text class="t-chip" x="944" y="578" text-anchor="middle">Docker + kata-clh runtime</text>
      <text class="t-tiny" x="944" y="594" text-anchor="middle">XFS prjquota data root · icc: false · live-restore</text>
      <text class="t-tiny" x="944" y="608" text-anchor="middle">DOCKER-USER floor · egress default-deny</text>

      <line class="link" x1="866" y1="406" x2="866" y2="426" marker-end="url(#ds-ink)"></line>
      <line class="link" x1="944" y1="538" x2="944" y2="558" marker-end="url(#ds-ink)"></line>

      <!-- nodes → the edge, across the LAN, by the names each node pins to the edge's address -->
      <line class="flow-vio" x1="776" y1="356" x2="600" y2="356" marker-end="url(#ds-vio)"></line>
      <text class="t-tiny" x="688" y="350" text-anchor="middle">dev-system-api</text>
      <polyline class="flow-amb" points="776,470 660,470 660,372 600,372" marker-end="url(#ds-amb)"></polyline>
      <text class="t-tiny" x="706" y="486" text-anchor="middle">dev-api,</text>
      <text class="t-tiny" x="706" y="498" text-anchor="middle">dev-apps-api</text>

      <!-- servers → Azure: one service principal -->
      <polyline class="flow-ind" points="600,520 632,520 632,171 676,171" marker-end="url(#ds-ind)"></polyline>
      <text class="t-tiny" x="626" y="300" text-anchor="end">servers' principal</text>

      <!-- ES → snapshot container, out through the host's NAT -->
      <polyline class="data" points="526,626 646,626 646,254 898,254 898,194" marker-end="url(#ds-ink)"></polyline>
      <text class="t-tiny" x="772" y="247" text-anchor="middle">SLM daily snapshot</text>

      <!-- publish workload → sites account -->
      <polyline class="flow-amb" points="1112,482 1140,482 1140,87 1132,87" marker-end="url(#ds-amb)"></polyline>
      <text class="t-tiny" x="1128" y="300" text-anchor="end">signed upload URL</text>

      <!-- ═════════ disks ═════════ -->

      <rect class="disk" x="35" y="796" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="110" y="814" text-anchor="middle">drive 2 · pool es1</text>
      <text class="t-tiny" x="110" y="830" text-anchor="middle">/es1/data → es-1</text>
      <line class="data" x1="110" y1="796" x2="110" y2="660"></line>

      <rect class="disk" x="215" y="796" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="290" y="814" text-anchor="middle">drive 3 · pool es2</text>
      <text class="t-tiny" x="290" y="830" text-anchor="middle">/es2/data → es-2</text>
      <line class="data" x1="290" y1="796" x2="290" y2="660"></line>

      <rect class="disk" x="395" y="796" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="470" y="814" text-anchor="middle">drive 4 · pool es3</text>
      <text class="t-tiny" x="470" y="830" text-anchor="middle">/es3/data → es-3</text>
      <line class="data" x1="470" y1="796" x2="470" y2="660"></line>

      <rect class="disk" x="556" y="796" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="631" y="814" text-anchor="middle">drive 1</text>
      <text class="t-tiny" x="631" y="830" text-anchor="middle">Proxmox · rootfs · stores</text>

      <text class="t-tag" x="36" y="866">HOST · PROXMOX VE · RYZEN 9 · 96 GB · 4 × 512 GB NVME</text>
      <text class="t-tag" x="752" y="866">2 × INTEL NUC · 32 GB · 250 GB SSD</text>
    </svg>
  </div>
  </DiagramFrame>
</template>

<style>
/* Palette follows the site color-mode class, so the diagram tracks the theme toggle. */
svg.dev-server-diagram {
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
    --amber: #A97C22;
    --amber-tint: rgba(169, 124, 34, 0.10);
    --pink: #B0487E;
    --host-tint: rgba(100, 116, 139, 0.07);
}
.dark svg.dev-server-diagram {
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
  --amber: #D3A24C;
  --amber-tint: rgba(211, 162, 76, 0.13);
  --pink: #D879AC;
  --host-tint: rgba(148, 163, 184, 0.08);
}

.dev-server-diagram-wrap { overflow-x: auto; }
svg.dev-server-diagram { min-width: 700px; width: 100%; height: auto; display: block; }

  /* ── SVG vocabulary ─────────────────────────────── */
  svg.dev-server-diagram .wall      { fill: var(--host-tint);  stroke: var(--line);   stroke-width: 1.5; }
  svg.dev-server-diagram .chip      { fill: var(--surface);    stroke: var(--line);   stroke-width: 1.25; }
  svg.dev-server-diagram .disk      { fill: var(--surface);    stroke: var(--muted);  stroke-width: 1.25; stroke-dasharray: 3 3; }
  svg.dev-server-diagram .gw        { fill: var(--surface);    stroke-width: 2; }
  svg.dev-server-diagram .gw-app    { stroke: var(--green); }
  svg.dev-server-diagram .gw-node   { stroke: var(--amber); }
  svg.dev-server-diagram .gw-edge   { stroke: var(--violet); }
  svg.dev-server-diagram .encl-app  { fill: var(--green-tint);  stroke: var(--green);  stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.dev-server-diagram .encl-plat { fill: var(--indigo-tint); stroke: var(--indigo); stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.dev-server-diagram .wbox      { fill: var(--amber-tint);  stroke: var(--amber);  stroke-width: 1.25; stroke-dasharray: 6 5; }
  svg.dev-server-diagram .encl-priv { fill: var(--surface);     stroke: var(--violet); stroke-width: 1.25; stroke-dasharray: 3 4; }
  svg.dev-server-diagram .cyl       { fill: var(--surface); stroke: var(--pink); stroke-width: 1.5; }
  svg.dev-server-diagram .sep       { stroke: var(--line); stroke-width: 1; }

  svg.dev-server-diagram .flow      { stroke: var(--ink);    stroke-width: 1.6; fill: none; }
  svg.dev-server-diagram .flow-ind  { stroke: var(--indigo); stroke-width: 1.8; fill: none; }
  svg.dev-server-diagram .flow-vio  { stroke: var(--violet); stroke-width: 1.6; fill: none; }
  svg.dev-server-diagram .flow-amb  { stroke: var(--amber);  stroke-width: 1.6; fill: none; }
  svg.dev-server-diagram .link      { stroke: var(--muted);  stroke-width: 1.4; fill: none; }
  svg.dev-server-diagram .data      { stroke: var(--muted);  stroke-width: 1.3; fill: none; stroke-dasharray: 2 4; stroke-linecap: round; }

  svg.dev-server-diagram .mk-ink { fill: var(--ink); }
  svg.dev-server-diagram .mk-ind { fill: var(--indigo); }
  svg.dev-server-diagram .mk-vio { fill: var(--violet); }
  svg.dev-server-diagram .mk-amb { fill: var(--amber); }

  svg.dev-server-diagram text { font-family: "Avenir Next", "Segoe UI", system-ui, sans-serif; }
  svg.dev-server-diagram .t-name  { font-size: 13px; font-weight: 600; fill: var(--ink); }
  svg.dev-server-diagram .t-sub   { font-size: 11px; fill: var(--muted); }
  svg.dev-server-diagram .t-chip  { font-size: 12px; font-weight: 500; fill: var(--ink); }
  svg.dev-server-diagram .t-mono  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--muted); }
  svg.dev-server-diagram .t-tag   { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; fill: var(--muted); }
  svg.dev-server-diagram .t-tiny  { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 9.5px; fill: var(--muted); }
  svg.dev-server-diagram .t-plane-p { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--indigo); }
  svg.dev-server-diagram .t-plane-a { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; font-weight: 600; fill: var(--green); }
  svg.dev-server-diagram .t-plane-w { font-family: ui-monospace, Menlo, monospace; font-size: 11px; letter-spacing: 0.12em; font-weight: 600; fill: var(--amber); }
  svg.dev-server-diagram .t-plane-v { font-family: ui-monospace, Menlo, monospace; font-size: 10px; letter-spacing: 0.12em; font-weight: 600; fill: var(--violet); }
</style>
