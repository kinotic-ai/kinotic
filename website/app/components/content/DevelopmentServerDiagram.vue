<template>
  <DiagramFrame>
  <div class="dev-server-diagram-wrap">
    <svg class="dev-server-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1172 860" role="img" aria-label="Development server topology: peers and GitHub reach kinotic-server over two forwarded ports; Azure keeps Front Door, the sites storage account, email, DNS, a Key Vault and a snapshot container; one Proxmox host runs a container per service — kinotic-server, Loki, Tempo, Mimir, Grafana on the LAN, three Elasticsearch nodes and the one-shot migration on a private NAT-only network — with a ZFS pool on its own drive for each Elasticsearch node and a drive of Proxmox's own; two Intel NUCs beside it run the vm-manager with Cloud Hypervisor micro VMs.">

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
      <text class="t-tag" x="24" y="26">PEERS · INTERNET</text>

      <rect class="chip" x="40" y="44" width="160" height="46" rx="8"></rect>
      <text class="t-chip" x="120" y="63" text-anchor="middle">GitHub</text>
      <text class="t-sub"  x="120" y="79" text-anchor="middle">App webhooks · repo fetch</text>

      <rect class="chip" x="210" y="44" width="200" height="46" rx="8"></rect>
      <text class="t-chip" x="310" y="63" text-anchor="middle">Peers</text>
      <text class="t-sub"  x="310" y="79" text-anchor="middle">portal · CLI · MCP hosts</text>

      <!-- inbound: two forwarded ports, no reverse proxy -->
      <line class="flow" x1="120" y1="90" x2="120" y2="344" marker-end="url(#ds-ink)"></line>
      <text class="t-tiny" x="128" y="256">POST /api/github/webhook</text>
      <line class="flow" x1="310" y1="90" x2="310" y2="344" marker-end="url(#ds-ink)"></line>
      <text class="t-tiny" x="318" y="256">:443 → 9090 · :58503 → 58503</text>

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
      <text class="t-sub"  x="744" y="183" text-anchor="middle">server secret storage</text>

      <rect class="chip" x="828" y="148" width="140" height="46" rx="8"></rect>
      <text class="t-chip" x="898" y="167" text-anchor="middle">Blob container</text>
      <text class="t-sub"  x="898" y="183" text-anchor="middle">ES snapshots · daily</text>

      <rect class="chip" x="984" y="148" width="148" height="46" rx="8"></rect>
      <text class="t-chip" x="1058" y="167" text-anchor="middle">DNS zone</text>
      <text class="t-sub"  x="1058" y="183" text-anchor="middle">A record · DNS-01 certs</text>

      <!-- ═════════ the host ═════════ -->
      <rect class="wall" x="24" y="262" width="696" height="580" rx="12"></rect>
      <rect class="wall" x="740" y="262" width="408" height="580" rx="12"></rect>

      <!-- the containers -->
      <rect class="encl-app" x="40" y="306" width="580" height="396" rx="10"></rect>
      <text class="t-plane-a" x="590" y="330" text-anchor="end">CONTAINERS · ONE PER SERVICE · OCI IMAGES</text>

      <rect class="gw gw-app" x="60" y="344" width="290" height="112" rx="8"></rect>
      <text class="t-name" x="205" y="367" text-anchor="middle">kinotic-server</text>
      <text class="t-sub"  x="205" y="383" text-anchor="middle">UI :9090 · REST · STOMP · MCP :58503</text>
      <line class="sep" x1="78" y1="392" x2="332" y2="392"></line>
      <text class="t-mono" x="205" y="409" text-anchor="middle">Vert.x terminates TLS on both ports</text>
      <text class="t-mono" x="205" y="424" text-anchor="middle">profiles: production · dev-server</text>
      <text class="t-mono" x="205" y="439" text-anchor="middle">secrets → Key Vault · mail → ACS</text>

      <!-- the stores, on the LAN -->
      <rect class="chip" x="60" y="470" width="124" height="40" rx="8"></rect>
      <text class="t-chip" x="122" y="487" text-anchor="middle">Loki</text>
      <text class="t-tiny" x="122" y="501" text-anchor="middle">:3100 · logs</text>

      <rect class="chip" x="198" y="470" width="124" height="40" rx="8"></rect>
      <text class="t-chip" x="260" y="487" text-anchor="middle">Tempo</text>
      <text class="t-tiny" x="260" y="501" text-anchor="middle">:4318 OTLP · :3200</text>

      <rect class="chip" x="336" y="470" width="124" height="40" rx="8"></rect>
      <text class="t-chip" x="398" y="487" text-anchor="middle">Mimir</text>
      <text class="t-tiny" x="398" y="501" text-anchor="middle">:9009 · OTLP · PromQL</text>

      <rect class="chip" x="474" y="470" width="124" height="40" rx="8"></rect>
      <text class="t-chip" x="536" y="487" text-anchor="middle">Grafana</text>
      <text class="t-tiny" x="536" y="501" text-anchor="middle">:3000 · login</text>

      <text class="t-tiny" x="330" y="525" text-anchor="middle">LAN · the node's Alloy and the server's agent push OTLP here · X-Scope-OrgID per tenant</text>

      <!-- the private network: three ES nodes on a whole disk each, and the migration -->
      <rect class="encl-priv" x="48" y="536" width="564" height="150" rx="8"></rect>
      <text class="t-plane-v" x="56" y="552">PRIVATE NETWORK · NAT OUT</text>

      <rect class="chip" x="452" y="544" width="148" height="30" rx="8"></rect>
      <text class="t-tiny" x="526" y="563" text-anchor="middle">migration · runs once</text>

      <path class="cyl" d="M 54 602 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="110" cy="602" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="110" y="622" text-anchor="middle">es-1</text>
      <text class="t-tiny" x="110" y="636" text-anchor="middle">master + data</text>

      <path class="cyl" d="M 234 602 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="290" cy="602" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="290" y="622" text-anchor="middle">es-2</text>
      <text class="t-tiny" x="290" y="636" text-anchor="middle">master + data</text>

      <path class="cyl" d="M 414 602 a 56 9 0 0 0 112 0 v 38 a 56 9 0 0 1 -112 0 z"></path>
      <ellipse class="cyl" cx="470" cy="602" rx="56" ry="9"></ellipse>
      <text class="t-chip" x="470" y="622" text-anchor="middle">es-3</text>
      <text class="t-tiny" x="470" y="636" text-anchor="middle">master + data</text>

      <text class="t-tiny" x="200" y="678" text-anchor="middle">1 shard · 1 replica per index · no TLS</text>

      <line class="link" x1="329" y1="456" x2="329" y2="540"></line>
      <line class="link" x1="329" y1="540" x2="110" y2="593"></line>
      <line class="link" x1="329" y1="540" x2="290" y2="593"></line>
      <line class="link" x1="329" y1="540" x2="470" y2="593"></line>

      <!-- the nodes: their own machines -->
      <rect class="wbox" x="756" y="306" width="376" height="396" rx="10"></rect>
      <text class="t-plane-w" x="772" y="330">NODES · UBUNTU 22.04 · KVM</text>

      <rect class="chip" x="776" y="344" width="180" height="62" rx="8"></rect>
      <text class="t-chip" x="866" y="364" text-anchor="middle">vm-manager</text>
      <text class="t-tiny" x="866" y="380" text-anchor="middle">CLOUD_HYPERVISOR provider</text>
      <text class="t-tiny" x="866" y="394" text-anchor="middle">Alloy · machine credentials</text>

      <rect class="gw gw-node" x="776" y="426" width="336" height="112" rx="8"></rect>
      <text class="t-name" x="944" y="449" text-anchor="middle">Cloud Hypervisor micro VMs</text>
      <line class="sep" x1="794" y1="458" x2="1094" y2="458"></line>
      <text class="t-mono" x="944" y="475" text-anchor="middle">sync VM · runtime VM per microservice · publish VM</text>
      <text class="t-mono" x="944" y="490" text-anchor="middle">reach the gateway at kinotic-server's LAN IPv4</text>
      <text class="t-mono" x="944" y="505" text-anchor="middle">egress denied by default · allowlisted CIDRs only</text>
      <text class="t-mono" x="944" y="520" text-anchor="middle">stdout and stderr captured, shipped by Alloy</text>

      <rect class="chip" x="776" y="558" width="336" height="62" rx="8"></rect>
      <text class="t-chip" x="944" y="578" text-anchor="middle">Docker + kata-clh runtime</text>
      <text class="t-tiny" x="944" y="594" text-anchor="middle">XFS prjquota data root · icc: false · live-restore</text>
      <text class="t-tiny" x="944" y="608" text-anchor="middle">DOCKER-USER floor · egress default-deny</text>

      <line class="link" x1="866" y1="406" x2="866" y2="426" marker-end="url(#ds-ink)"></line>
      <line class="link" x1="944" y1="538" x2="944" y2="558" marker-end="url(#ds-ink)"></line>

      <!-- nodes → server, across the LAN -->
      <line class="flow-vio" x1="776" y1="372" x2="350" y2="372" marker-end="url(#ds-vio)"></line>
      <text class="t-tiny" x="560" y="386" text-anchor="middle">STOMP · machine credentials · heartbeat</text>
      <line class="flow-amb" x1="776" y1="470" x2="350" y2="430" marker-end="url(#ds-amb)"></line>
      <text class="t-tiny" x="490" y="418" text-anchor="middle">gateway = server IPv4 · :58503 · TLS</text>

      <!-- server → Azure: one service principal -->
      <polyline class="flow-ind" points="350,350 600,350 600,171 660,171" marker-end="url(#ds-ind)"></polyline>
      <text class="t-tiny" x="594" y="276" text-anchor="end">service principal</text>
      <text class="t-tiny" x="594" y="288" text-anchor="end">Key Vault · ACS · site URLs</text>

      <!-- ES → snapshot container, out through the host's NAT -->
      <polyline class="data" points="526,602 630,602 630,254 898,254 898,194" marker-end="url(#ds-ink)"></polyline>
      <text class="t-tiny" x="764" y="247" text-anchor="middle">SLM daily snapshot</text>

      <!-- publish workload → sites account -->
      <polyline class="flow-amb" points="1112,482 1140,482 1140,87 1132,87" marker-end="url(#ds-amb)"></polyline>
      <text class="t-tiny" x="1128" y="300" text-anchor="end">signed upload URL</text>

      <!-- ═════════ disks ═════════ -->

      <rect class="disk" x="35" y="756" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="110" y="774" text-anchor="middle">drive 2 · pool es1</text>
      <text class="t-tiny" x="110" y="790" text-anchor="middle">/es1/data → es-1</text>
      <line class="data" x1="110" y1="756" x2="110" y2="652"></line>

      <rect class="disk" x="215" y="756" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="290" y="774" text-anchor="middle">drive 3 · pool es2</text>
      <text class="t-tiny" x="290" y="790" text-anchor="middle">/es2/data → es-2</text>
      <line class="data" x1="290" y1="756" x2="290" y2="652"></line>

      <rect class="disk" x="395" y="756" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="470" y="774" text-anchor="middle">drive 4 · pool es3</text>
      <text class="t-tiny" x="470" y="790" text-anchor="middle">/es3/data → es-3</text>
      <line class="data" x1="470" y1="756" x2="470" y2="652"></line>

      <rect class="disk" x="556" y="756" width="150" height="44" rx="8"></rect>
      <text class="t-chip" x="631" y="774" text-anchor="middle">drive 1</text>
      <text class="t-tiny" x="631" y="790" text-anchor="middle">Proxmox · rootfs · stores</text>

      <text class="t-tag" x="36" y="826">HOST · PROXMOX VE · RYZEN 9 · 96 GB · 4 × 512 GB NVME</text>
      <text class="t-tag" x="752" y="826">2 × INTEL NUC · 32 GB · 250 GB SSD</text>
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
