<script setup lang="ts">
// One component, ten views, so the authorization figures share one palette and one vocabulary
// wherever a page places them.
defineProps<{
  view: 'directory' | 'request' | 'parts' | 'shapes' | 'permission' | 'roles' | 'walk' | 'grants' | 'pair' | 'application'
}>()
</script>

<template>
  <DiagramFrame>
  <div class="authz-diagram-wrap">
    <svg v-if="view === 'directory'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 500" role="img" aria-label="A Java platform service and a TypeScript application service both register into the service directory index; the directory is read by the request authorizer and, through the reconcile master, drives the model generator that writes the OpenFGA model.">
      <defs>
        <marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="arr-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>

      <text class="lane-t code" x="20" y="28">Java platform service</text>
      <text class="lane-t code" x="20" y="168">TypeScript application service</text>
      <text class="lane-t model" x="20" y="368">Reconcile (system server)</text>
      <line class="lane" x1="20" y1="156" x2="640" y2="156"/>
      <line class="lane" x1="20" y1="356" x2="1160" y2="356"/>

      <g class="box code"><rect x="20" y="40" width="250" height="104" rx="4"/>
        <text class="t" x="32" y="61">@Publish interface</text>
        <text class="d" x="32" y="79">@AuthzResource(type, parent, roles)</text>
        <text class="d" x="32" y="96">@AuthzCheck or @AuthzUnchecked</text>
        <text class="d" x="32" y="113">@Scope, @Version, @Zone, @McpTool</text>
        <text class="d" x="32" y="130">implemented by a Spring bean</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M270,92 H330"/>
      <text class="lbl" x="300" y="34" text-anchor="middle">at startup</text>
      <g class="box code"><rect x="330" y="40" width="300" height="104" rx="4"/>
        <text class="t" x="342" y="61">DefaultServiceDirectory.register</text>
        <text class="d" x="342" y="79">kept if advertised, @McpTool or a resource</text>
        <text class="d" x="342" y="96">DefaultSchemaService.createForServices</text>
        <text class="d" x="342" y="113">AuthzDecorators.resourceOf / checkOf</text>
        <text class="d" x="342" y="130">derives each function's check</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M630,92 H670"/>
      <text class="lbl" x="650" y="34" text-anchor="middle">upsert</text>

      <g class="box code"><rect x="20" y="180" width="250" height="104" rx="4"/>
        <text class="t" x="32" y="201">@Publish class</text>
        <text class="d" x="32" y="219">decorators from @kinotic-ai/core</text>
        <text class="d" x="32" y="236">kinotic sync: ServiceDefinitions.ts</text>
        <text class="d" x="32" y="253">types declared inline</text>
        <text class="d" x="32" y="270">registered by the client on connect</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M270,232 H330"/>
      <text class="lbl" x="300" y="174" text-anchor="middle">on connect</text>
      <g class="box code"><rect x="330" y="180" width="300" height="104" rx="4"/>
        <text class="t" x="342" y="201">ServiceDirectoryService.register(entry)</text>
        <text class="d" x="342" y="219">app-api, needs can_register_services</text>
        <text class="d" x="342" y="236">DefaultServiceDirectory.register(entry)</text>
        <text class="d" x="342" y="253">schemaService.deriveChecks: same rules</text>
        <text class="d" x="342" y="270">organizationId and applicationId set</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M630,232 H670"/>
      <text class="lbl" x="650" y="174" text-anchor="middle">upsert</text>

      <g class="box store"><rect x="670" y="40" width="240" height="244" rx="4"/>
        <text class="t" x="682" y="61">Service directory</text>
        <text class="dm" x="682" y="81">index kinotic_service_directory</text>
        <text class="d" x="682" y="100">one entry per service</text>
        <text class="dm" x="682" y="118">id = zone~namespace.Name</text>
        <text class="d" x="682" y="138">contract: functions, types,</text>
        <text class="d" x="682" y="155">AuthzResource and AuthzCheck</text>
        <text class="d" x="682" y="175">SHA-256 contractHash</text>
        <text class="d" x="682" y="195">watched: CONTRACT_PUBLISHED,</text>
        <text class="d" x="682" y="212">parent = authz store (platform or app)</text>
        <text class="d" x="682" y="232">online flag from the liveness singleton</text>
        <text class="d" x="682" y="252">null org/app = platform service</text>
      </g>

      <path class="e" marker-end="url(#arr)" d="M910,92 H950"/>
      <text class="lbl" x="930" y="84" text-anchor="middle">read</text>
      <g class="box req"><rect x="950" y="40" width="210" height="104" rx="4"/>
        <text class="t" x="962" y="61">DefaultRequestAuthorizer</text>
        <text class="d" x="962" y="79">findEntry(zone~name) on the</text>
        <text class="d" x="962" y="96">first request, cached 5 min</text>
        <text class="d" x="962" y="113">one FunctionSpec per function</text>
        <text class="d" x="962" y="130">asked on every request</text>
      </g>

      <path class="e" marker-end="url(#arr)" d="M790,284 V332 H145 V380"/>
      <text class="lbl" x="470" y="324" text-anchor="middle">every write stamps the parent store's record</text>

      <g class="box model"><rect x="20" y="380" width="250" height="104" rx="4"/>
        <text class="t" x="32" y="401">ReconcileMaster</text>
        <text class="d" x="32" y="419">tick 2 s: records written since,</text>
        <text class="d" x="32" y="436">each queues its worker and its parent's</text>
        <text class="d" x="32" y="453">resync 5 min: records off desired state</text>
        <text class="d" x="32" y="470">a running key is re-queued once</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M270,432 H310"/>
      <g class="box model"><rect x="310" y="380" width="270" height="104" rx="4"/>
        <text class="t" x="322" y="401">AuthzStoreReconciler</text>
        <text class="d" x="322" y="419">platform: findSystemDefinitions, paged</text>
        <text class="d" x="322" y="436">app: platform services typed tenant or</text>
        <text class="d" x="322" y="453">entity_definition, and the app's own</text>
        <text class="d" x="322" y="470">nothing read from the definitions</text>
      </g>
      <path class="e" marker-end="url(#arr)" d="M580,432 H620"/>
      <g class="box model"><rect x="620" y="380" width="260" height="104" rx="4"/>
        <text class="t" x="632" y="401">DefaultAuthzModelGenerator</text>
        <text class="d" x="632" y="419">kernel types + one per declared type</text>
        <text class="d" x="632" y="436">app store: + the tenant-typed services</text>
        <text class="d" x="632" y="453">each permission a relation, carried to</text>
        <text class="d" x="632" y="470">every ancestor, role and role_binding</text>
      </g>
      <path class="e acc" marker-end="url(#arr-acc)" d="M880,432 H920"/>
      <g class="box engine"><rect x="920" y="380" width="240" height="104" rx="4"/>
        <text class="t" x="932" y="401">OpenFGA store</text>
        <text class="d" x="932" y="419">one per application + the platform's</text>
        <text class="d" x="932" y="436">ensureModel: write if the hash differs</text>
        <text class="dm" x="932" y="453">ensureRoles: role:r#perm@user:*</text>
        <text class="d" x="932" y="470">reportObserved(generation, version)</text>
      </g>
    </svg>
    <svg v-else-if="view === 'request'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1260 400" role="img" aria-label="A request goes from the client to the gateway, which authenticates and applies the zone rule, then to the request authorizer, which loads the function's spec from the directory on a cache miss, makes one OpenFGA check, and on success forwards the event over the bus to a service node; a denial returns an error event to the client, and an unchecked function skips the engine.">
      <defs>
        <marker id="arr2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="arr2-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>

      <path class="e" marker-end="url(#arr2)" d="M590,110 V52 H1030 V110"/>
      <text class="lbl" x="810" y="44" text-anchor="middle">marked unchecked: admitted on the zone, the engine is not asked. No contract, or a function it neither checks nor marks: refused</text>

      <g class="box"><rect x="20" y="110" width="130" height="120" rx="4"/>
        <text class="t" x="32" y="131">Client</text>
        <text class="d" x="32" y="149">browser, Node</text>
        <text class="d" x="32" y="166">or machine</text>
        <text class="d" x="32" y="183">connects with</text>
        <text class="d" x="32" y="200">credentials</text>
      </g>
      <path class="e" marker-end="url(#arr2)" d="M150,170 H210"/>
      <g class="badge"><circle cx="180" cy="152" r="10"/><text x="180" y="156">1</text></g>
      <text class="lbl" x="180" y="186" text-anchor="middle">SEND</text>

      <g class="box req"><rect x="210" y="110" width="210" height="120" rx="4"/>
        <text class="t" x="222" y="131">Gateway STOMP endpoint</text>
        <text class="d" x="222" y="149">authenticate → Participant</text>
        <text class="d" x="222" y="166">with org / app / tenant scope</text>
        <text class="d" x="222" y="183">zone admits the destination?</text>
        <text class="d" x="222" y="200">sender = participant</text>
        <text class="d" x="222" y="217">reply-to validated, call tracked</text>
      </g>
      <path class="e" marker-end="url(#arr2)" d="M420,170 H480"/>
      <g class="badge"><circle cx="450" cy="152" r="10"/><text x="450" y="156">2</text></g>
      <text class="lbl" x="450" y="186" text-anchor="middle">authorize</text>

      <g class="box req"><rect x="480" y="110" width="220" height="120" rx="4"/>
        <text class="t" x="492" y="131">DefaultRequestAuthorizer</text>
        <text class="d" x="492" y="149">store: app user → app store,</text>
        <text class="d" x="492" y="166">anyone else → platform store</text>
        <text class="d" x="492" y="183">FunctionSpec from cache (5 min)</text>
        <text class="d" x="492" y="200">scopeLevelOf, resolve templates</text>
        <text class="dm" x="492" y="217">(user, relation, type:id)</text>
      </g>
      <path class="e acc" marker-end="url(#arr2-acc)" d="M700,170 H760"/>
      <g class="badge"><circle cx="730" cy="152" r="10"/><text x="730" y="156">4</text></g>
      <text class="lbl acc" x="730" y="186" text-anchor="middle">Check</text>

      <g class="box engine"><rect x="760" y="110" width="170" height="120" rx="4"/>
        <text class="t" x="772" y="131">OpenFGA</text>
        <text class="d" x="772" y="149">store + modelId (cached)</text>
        <text class="d" x="772" y="166">one Check per call</text>
        <text class="d" x="772" y="183">MINIMIZE_LATENCY, or</text>
        <text class="d" x="772" y="200">HIGHER_CONSISTENCY</text>
        <text class="d" x="772" y="217">if declared consistent</text>
      </g>
      <path class="e" marker-end="url(#arr2)" d="M930,170 H980"/>
      <g class="badge"><circle cx="955" cy="152" r="10"/><text x="955" y="156">5</text></g>
      <text class="lbl" x="955" y="186" text-anchor="middle">allowed</text>

      <g class="box"><rect x="980" y="110" width="100" height="120" rx="4"/>
        <text class="t" x="992" y="131">Event bus</text>
        <text class="d" x="992" y="149">sendWithAck</text>
        <text class="d" x="992" y="166">to a node</text>
        <text class="d" x="992" y="183">serving the</text>
        <text class="d" x="992" y="200">address</text>
      </g>
      <path class="e" marker-end="url(#arr2)" d="M1080,170 H1130"/>
      <g class="badge"><circle cx="1105" cy="152" r="10"/><text x="1105" y="156">6</text></g>
      <text class="lbl" x="1105" y="186" text-anchor="middle">invoke</text>

      <g class="box"><rect x="1130" y="110" width="110" height="120" rx="4"/>
        <text class="t" x="1142" y="131">Service node</text>
        <text class="d" x="1142" y="149">runs the</text>
        <text class="d" x="1142" y="166">function and</text>
        <text class="d" x="1142" y="183">replies through</text>
        <text class="d" x="1142" y="200">the gateway</text>
      </g>

      <path class="e dash" marker-end="url(#arr2)" d="M590,230 V258"/>
      <g class="badge"><circle cx="610" cy="244" r="10"/><text x="610" y="248">3</text></g>
      <text class="lbl" x="628" y="248">findEntry on a cache miss</text>
      <g class="box store"><rect x="480" y="258" width="220" height="72" rx="4"/>
        <text class="t" x="492" y="279">Service directory</text>
        <text class="d" x="492" y="297">entry zone~name → its definition</text>
        <text class="d" x="492" y="314">one FunctionSpec per function</text>
      </g>

      <path class="e" marker-end="url(#arr2)" d="M845,230 V362 H315 V230"/>
      <text class="lbl" x="580" y="354" text-anchor="middle">denied: AuthorizationException → error event on the reply destination, the request never reaches the bus</text>
    </svg>
    <svg v-else-if="view === 'parts'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 440" role="img" aria-label="A check is a user, a relation and an object. Three precedence ladders feed its parts: the permission from a check's own declaration, else the service's, else the verb of the name; the type from a check's resource, else the parent, else the service's type; the id from a check's template, else the service's, else an id parameter, else the parent's id. The rungs the example matched are highlighted.">
      <defs>
        <marker id="f3" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f3-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <text class="lane-t code" x="20" y="28">Example</text>
      <text class="dm" x="90" y="28">heartbeat(nodeId, status) on VmNodeOrchestrationService, declared @AuthzCheck(permission = can_report, resourceId = {nodeId})</text>
      <text class="lane-t code" x="230" y="66">Permission</text>
      <g class="rung hit"><rect x="230" y="78" width="290" height="28" rx="14"/><text class="rn" x="246" y="97">1</text><text class="rm" x="262" y="97">@AuthzCheck(permission)</text></g>
      <path class="e" marker-end="url(#f3)" d="M375,106 V116"/>
      <text class="lbl" x="383" y="115">else</text>
      <g class="rung"><rect x="230" y="116" width="290" height="28" rx="14"/><text class="rn" x="246" y="135">2</text><text class="rm" x="262" y="135">@AuthzResource(permission)</text></g>
      <path class="e" marker-end="url(#f3)" d="M375,144 V154"/>
      <text class="lbl" x="383" y="153">else</text>
      <g class="rung"><rect x="230" y="154" width="290" height="28" rx="14"/><text class="rn" x="246" y="173">3</text><text class="rm" x="262" y="173">the verb of the name</text></g>
      <text class="d" x="230" y="200">find get count search list → can_view</text>
      <text class="d" x="230" y="216">save update set create → can_edit</text>
      <text class="d" x="230" y="232">delete remove → can_delete</text>
      <text class="d" x="230" y="248">none → the service is refused</text>
      <path class="e acc" marker-end="url(#f3-acc)" d="M375,258 V330"/>
      <text class="lane-t code" x="550" y="66">Type</text>
      <g class="rung"><rect x="550" y="78" width="240" height="28" rx="14"/><text class="rn" x="566" y="97">1</text><text class="rm" x="582" y="97">@AuthzCheck(resource)</text></g>
      <path class="e" marker-end="url(#f3)" d="M670,106 V116"/>
      <text class="lbl" x="678" y="115">else</text>
      <g class="rung"><rect x="550" y="116" width="240" height="28" rx="14"/><text class="rn" x="566" y="135">2</text><text class="rm" x="582" y="135">the parent: create, or no id</text></g>
      <path class="e" marker-end="url(#f3)" d="M670,144 V154"/>
      <text class="lbl" x="678" y="153">else</text>
      <g class="rung hit"><rect x="550" y="154" width="240" height="28" rx="14"/><text class="rn" x="566" y="173">3</text><text class="rm" x="582" y="173">the service's type</text></g>
      <text class="d" x="550" y="200">a constant, never a template</text>
      <path class="e acc" marker-end="url(#f3-acc)" d="M670,210 V330"/>
      <text class="lane-t code" x="820" y="66">Id</text>
      <g class="rung hit"><rect x="820" y="78" width="320" height="28" rx="14"/><text class="rn" x="836" y="97">1</text><text class="rm" x="852" y="97">@AuthzCheck(resourceId)</text></g>
      <path class="e" marker-end="url(#f3)" d="M980,106 V116"/>
      <text class="lbl" x="988" y="115">else</text>
      <g class="rung"><rect x="820" y="116" width="320" height="28" rx="14"/><text class="rn" x="836" y="135">2</text><text class="rm" x="852" y="135">@AuthzResource(resourceId)</text></g>
      <path class="e" marker-end="url(#f3)" d="M980,144 V154"/>
      <text class="lbl" x="988" y="153">else</text>
      <g class="rung"><rect x="820" y="154" width="320" height="28" rx="14"/><text class="rn" x="836" y="173">3</text><text class="rm" x="852" y="173">{id} · {&lt;type&gt;Id} · {param.id}</text></g>
      <path class="e" marker-end="url(#f3)" d="M980,182 V192"/>
      <text class="lbl" x="988" y="191">else</text>
      <g class="rung"><rect x="820" y="192" width="320" height="28" rx="14"/><text class="rn" x="836" y="211">4</text><text class="rm" x="852" y="211">{&lt;parent&gt;Id} · {@organizationId} …</text></g>
      <text class="d" x="820" y="238">none → the service is refused</text>
      <path class="e acc" marker-end="url(#f3-acc)" d="M980,248 V330"/>
      <text class="d" x="40" y="318">the check: user # relation @ object</text>
      <g class="box req"><rect x="40" y="330" width="160" height="44" rx="22"/><text class="pl" x="120" y="356" text-anchor="middle">user:u</text></g>
      <g class="box engine"><rect x="230" y="330" width="290" height="44" rx="4"/><text class="nm" x="375" y="358" text-anchor="middle">vm_node_can_report</text></g>
      <g class="box model"><rect x="550" y="330" width="590" height="44" rx="4"/><text class="nm" x="670" y="358" text-anchor="middle">vm_node</text><text class="nm" x="805" y="358" text-anchor="middle">:</text><text class="nm" x="980" y="358" text-anchor="middle">n</text></g>
      <line class="lane" x1="805" y1="336" x2="805" y2="368"/>
      <text class="d" x="120" y="394" text-anchor="middle">the caller, or the owner</text>
      <text class="d" x="120" y="410" text-anchor="middle">a delegate acts for</text>
      <text class="d" x="375" y="394" text-anchor="middle">named &lt;type&gt;_&lt;permission&gt;</text>
      <text class="d" x="375" y="410" text-anchor="middle">can_report of the service's type vm_node</text>
      <text class="d" x="670" y="394" text-anchor="middle">the type the permission</text>
      <text class="d" x="670" y="410" text-anchor="middle">is named for, or another</text>
      <text class="d" x="980" y="394" text-anchor="middle">per request: read from the body</text>
      <text class="d" x="980" y="410" text-anchor="middle">with a parse that stops at it,</text>
      <text class="d" x="980" y="426" text-anchor="middle">or the caller's scope for {@…}</text>
    </svg>
    <svg v-else-if="view === 'shapes'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 600" role="img" aria-label="Six pictograms, one per shape a platform service takes: one object with a permission per function; one object and one permission; permissions and objects derived from verbs and id parameters; the object named per request by a template; a check made on another type; and functions served without a check.">
      <defs>
        <marker id="f4" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f4-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <text class="t" x="20" y="44">One object for the service, a permission per function</text>
      <g class="box code"><rect x="20" y="58" width="190" height="102" rx="4"/>
        <text class="ts" x="30" y="76">PermissionService</text>
        <text class="stub" x="30" y="98">findGrants(…)</text>
        <text class="stub" x="30" y="122">grant(…)</text>
        <text class="stub" x="30" y="146">saveRole(…)</text>
      </g>
      <g class="box model"><rect x="400" y="89" width="170" height="40" rx="4"/><text class="nm" x="485" y="106" text-anchor="middle">organization</text><text class="idt" x="485" y="121" text-anchor="middle">{@organizationId}</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,94 H350 L400,109"/>
      <g class="chip perm"><rect x="228" y="84" width="110" height="20" rx="10"/><text x="283" y="98">can_view_access</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,118 H350 L400,109"/>
      <g class="chip perm"><rect x="228" y="108" width="123" height="20" rx="10"/><text x="289" y="122">can_manage_access</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,142 H350 L400,109"/>
      <g class="chip perm"><rect x="228" y="132" width="123" height="20" rx="10"/><text x="289" y="146">can_manage_access</text></g>
      <text class="dm" x="20" y="182">MemberService, MachineService, TelemetryService, the system services …</text>
      <text class="t" x="610" y="44">One object, one permission for every function</text>
      <g class="box code"><rect x="610" y="58" width="190" height="102" rx="4"/>
        <text class="ts" x="620" y="76">WorkloadService</text>
        <text class="stub" x="620" y="98">deploy(…)</text>
        <text class="stub" x="620" y="122">stop(…)</text>
        <text class="stub" x="620" y="146">destroy(…)</text>
      </g>
      <g class="box model"><rect x="990" y="89" width="170" height="40" rx="4"/><text class="nm" x="1075" y="106" text-anchor="middle">platform</text><text class="idt" x="1075" y="121" text-anchor="middle">kinotic</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,94 H940 L990,109"/>
      <g class="chip perm"><rect x="818" y="84" width="142" height="20" rx="10"/><text x="889" y="98">can_manage_workloads</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,118 H940 L990,109"/>
      <g class="chip perm"><rect x="818" y="108" width="142" height="20" rx="10"/><text x="889" y="122">can_manage_workloads</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,142 H940 L990,109"/>
      <g class="chip perm"><rect x="818" y="132" width="142" height="20" rx="10"/><text x="889" y="146">can_manage_workloads</text></g>
      <text class="dm" x="610" y="182">LogManager, with can_manage_cluster; the reads declare can_view_workloads</text>
      <text class="t" x="20" y="234">Derived from the verb and the id the function names</text>
      <g class="box code"><rect x="20" y="248" width="190" height="102" rx="4"/>
        <text class="ts" x="30" y="266">VmNodeService</text>
        <text class="stub" x="30" y="288">findById(nodeId)</text>
        <text class="stub" x="30" y="312">save(vmNode)</text>
        <text class="stub" x="30" y="336">findAll()</text>
      </g>
      <g class="box model"><rect x="400" y="250" width="170" height="40" rx="4"/><text class="nm" x="485" y="267" text-anchor="middle">vm_node</text><text class="idt" x="485" y="282" text-anchor="middle">{nodeId}</text></g>
      <g class="box model"><rect x="400" y="300" width="170" height="40" rx="4"/><text class="nm" x="485" y="317" text-anchor="middle">platform</text><text class="idt" x="485" y="332" text-anchor="middle">kinotic</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,284 H350 L400,270"/>
      <g class="chip perm"><rect x="228" y="274" width="66" height="20" rx="10"/><text x="261" y="288">can_view</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,308 H350 L400,270"/>
      <g class="chip perm"><rect x="228" y="298" width="66" height="20" rx="10"/><text x="261" y="312">can_edit</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,332 H350 L400,320"/>
      <g class="chip perm"><rect x="228" y="322" width="116" height="20" rx="10"/><text x="286" y="336">vm_node_can_view</text></g>
      <text class="dm" x="20" y="372">ProjectService, ApplicationService, EntityDefinitionService: nothing declared</text>
      <text class="t" x="610" y="234">The object named per request, by a template</text>
      <g class="box code"><rect x="610" y="248" width="190" height="102" rx="4"/>
        <text class="ts" x="620" y="266">JsonEntitiesRepository</text>
        <text class="stub" x="620" y="288">findById(…)</text>
        <text class="stub" x="620" y="312">save(…)</text>
        <text class="stub" x="620" y="336">search(…)</text>
      </g>
      <g class="box model"><rect x="990" y="279" width="170" height="40" rx="4"/><text class="nm" x="1075" y="296" text-anchor="middle">entity_definition</text><text class="idt" x="1075" y="311" text-anchor="middle">{entityDefinitionId}</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,284 H940 L990,299"/>
      <g class="chip perm"><rect x="818" y="274" width="66" height="20" rx="10"/><text x="851" y="288">can_read</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,308 H940 L990,299"/>
      <g class="chip perm"><rect x="818" y="298" width="79" height="20" rx="10"/><text x="857" y="312">can_create</text></g>
      <path class="e" marker-end="url(#f4)" d="M800,332 H940 L990,299"/>
      <g class="chip perm"><rect x="818" y="322" width="79" height="20" rx="10"/><text x="857" y="336">can_search</text></g>
      <text class="dm" x="610" y="372">VmNodeOrchestrationService on {nodeId}; ServiceDirectoryService on {entry…}</text>
      <text class="t" x="20" y="424">Checked on another type than the service's</text>
      <g class="box code"><rect x="20" y="438" width="190" height="102" rx="4"/>
        <text class="ts" x="30" y="456">VmNodeOrchestrationService</text>
        <text class="stub" x="30" y="478">registerNode(…)</text>
        <text class="stub" x="30" y="502">heartbeat(nodeId)</text>
        <text class="stub" x="30" y="526"></text>
      </g>
      <g class="box model"><rect x="400" y="440" width="170" height="40" rx="4"/><text class="nm" x="485" y="457" text-anchor="middle">platform</text><text class="idt" x="485" y="472" text-anchor="middle">kinotic</text></g>
      <g class="box model"><rect x="400" y="490" width="170" height="40" rx="4"/><text class="nm" x="485" y="507" text-anchor="middle">vm_node</text><text class="idt" x="485" y="522" text-anchor="middle">{nodeId}</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,474 H350 L400,460"/>
      <g class="chip perm"><rect x="228" y="464" width="123" height="20" rx="10"/><text x="289" y="478">can_register_node</text></g>
      <path class="e" marker-end="url(#f4)" d="M210,498 H350 L400,510"/>
      <g class="chip perm"><rect x="228" y="488" width="79" height="20" rx="10"/><text x="267" y="502">can_report</text></g>
      <text class="dm" x="20" y="562">the deployment services on project:{projectId}; InviteEmailTemplateService</text>
      <text class="t" x="610" y="424">Served unchecked</text>
      <g class="box code"><rect x="610" y="438" width="190" height="102" rx="4"/>
        <text class="ts" x="620" y="456">ProfileService</text>
        <text class="stub" x="620" y="478">me()</text>
        <text class="stub" x="620" y="502">update(…)</text>
        <text class="stub" x="620" y="526">changePassword(…)</text>
      </g>
      <path class="e" marker-end="url(#f4)" d="M800,474 H890"/>
      <path class="e" marker-end="url(#f4)" d="M800,498 H890"/>
      <path class="e" marker-end="url(#f4)" d="M800,522 H890"/>
      <circle class="nocheck" cx="950" cy="498" r="22"/><line class="x" x1="939" y1="487" x2="961" y2="509"/><line class="x" x1="939" y1="509" x2="961" y2="487"/>
      <text class="d" x="982" y="494">no engine check: the service</text>
      <text class="d" x="982" y="510">answers for the caller alone</text>
      <text class="dm" x="610" y="562">DelegateService; findById of the application, project and definition services</text>
    </svg>
    <svg v-else-if="view === 'permission'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 452" role="img" aria-label="The type tree of an application's store, application over tenant over report, each node with a port for the relation report_can_generate; the relation is carried down from parent to child. On the right, a role holding the permission for everyone and a role binding attached to the tenant, whose member holds it through the role. Three sources are numbered at the tenant's port: a binding attached here, carried from the parent, a permission implying it.">
      <defs>
        <marker id="f5" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f5-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <g class="box model"><rect x="100" y="40" width="160" height="40" rx="4"/><text class="nm" x="180" y="64" text-anchor="middle">application:a</text></g>
      <g class="box model"><rect x="100" y="190" width="160" height="40" rx="4"/><text class="nm" x="180" y="214" text-anchor="middle">tenant:t</text></g>
      <g class="box model"><rect x="100" y="340" width="160" height="40" rx="4"/><text class="nm" x="180" y="364" text-anchor="middle">report:r</text></g>
      <path class="e" marker-end="url(#f5)" d="M180,190 V80"/>
      <text class="lbl m" x="188" y="140">#application</text>
      <path class="e" marker-end="url(#f5)" d="M180,340 V230"/>
      <text class="lbl m" x="188" y="290">#tenant</text>
      <circle class="port" cx="260" cy="60" r="7"/>
      <text class="lbl m acc" x="272" y="48">report_can_generate</text>
      <circle class="port" cx="260" cy="210" r="7"/>
      <text class="lbl m acc" x="272" y="198">report_can_generate</text>
      <circle class="port" cx="260" cy="360" r="7"/>
      <text class="lbl m acc" x="272" y="348">report_can_generate</text>
      <path class="e acc dash" marker-end="url(#f5-acc)" d="M267,62 H320 V208 H268"/>
      <text class="lbl acc" x="328" y="139">carried: from application</text>
      <path class="e acc dash" marker-end="url(#f5-acc)" d="M267,212 H320 V358 H268"/>
      <text class="lbl acc" x="328" y="289">carried: from tenant</text>
      <text class="d" x="20" y="392">a node holds the relation through</text>
      <g class="box grant"><rect x="700" y="40" width="190" height="40" rx="4"/><text class="nm" x="795" y="64" text-anchor="middle">role:r</text></g>
      <g class="chip perm"><rect x="900" y="50" width="186" height="20" rx="10"/><text x="993" y="64">report_can_generate @user:*</text></g>
      <text class="d" x="900" y="86">held for everyone</text>
      <g class="box grant"><rect x="700" y="190" width="190" height="40" rx="4"/><text class="nm" x="795" y="214" text-anchor="middle">role_binding:b</text></g>
      <text class="d" x="900" y="205">one role, some members,</text>
      <text class="d" x="900" y="221">attached to one object</text>
      <g class="box req"><rect x="735" y="340" width="120" height="40" rx="20"/><text class="pl" x="795" y="364" text-anchor="middle">user:u</text></g>
      <path class="e acc" marker-end="url(#f5-acc)" d="M795,80 V190"/>
      <text class="lbl m" x="803" y="160">#role</text>
      <path class="e acc" marker-end="url(#f5-acc)" d="M795,340 V230"/>
      <text class="lbl m" x="803" y="290">#member</text>
      <path class="e acc" marker-end="url(#f5-acc)" d="M700,224 H262"/>
      <text class="lbl m" x="684" y="243" text-anchor="end">tenant:t #role_binding @role_binding:b</text>
      <text class="d" x="700" y="400">a member holds the permission on the object when the binding's role</text>
      <text class="d" x="700" y="416">bundles it: member ∩ (the permission from the role), per binding</text>
      <g class="badge"><circle cx="30" cy="416" r="10"/><text x="30" y="420">1</text></g>
      <text class="d" x="46" y="420">a binding attached to the node</text>
      <g class="badge"><circle cx="290" cy="416" r="10"/><text x="290" y="420">2</text></g>
      <text class="d" x="306" y="420">carried from the parent</text>
      <g class="badge"><circle cx="520" cy="416" r="10"/><text x="520" y="420">3</text></g>
      <text class="d" x="536" y="420">a permission implying it</text>
    </svg>
    <svg v-else-if="view === 'roles'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1250 370" role="img" aria-label="Three views of roles: nested rings for one type, viewer inside editor inside admin, each ring adding permissions; horizontal bars for the platform's staff roles spanning the types they reach; and the declared and custom roles as chips.">
      <defs>
        <marker id="f6" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f6-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <text class="lane-t grant" x="20" y="28">Built in, for every type</text>
      <rect class="ring" x="20" y="40" width="400" height="290" rx="10"/>
      <text class="rl" x="32" y="60">project.admin</text>
      <rect class="ring" x="50" y="100" width="340" height="200" rx="10"/>
      <text class="rl" x="62" y="120">project.editor</text>
      <rect class="ring" x="80" y="160" width="280" height="110" rx="10"/>
      <text class="rl" x="92" y="180">project.viewer</text>
      <g class="chip perm"><rect x="150" y="215" width="116" height="20" rx="10"/><text x="208" y="229">project_can_view</text></g>
      <text class="d" x="220" y="255" text-anchor="middle">every reading permission</text>
      <g class="chip perm"><rect x="150" y="274" width="116" height="20" rx="10"/><text x="208" y="288">project_can_edit</text></g>
      <g class="chip perm"><rect x="90" y="304" width="129" height="20" rx="10"/><text x="154" y="318">project_can_delete</text></g>
      <g class="chip perm"><rect x="230" y="304" width="148" height="20" rx="10"/><text x="304" y="318">+ entity_definition_*</text></g>
      <text class="lane-t grant" x="470" y="28">The platform's staff, over every type</text>
      <g class="box model"><rect x="470" y="290" width="92" height="30" rx="4"/><text class="nm" x="516" y="309" text-anchor="middle">platform</text></g>
      <g class="box model"><rect x="564" y="290" width="92" height="30" rx="4"/><text class="nm" x="610" y="309" text-anchor="middle">organization</text></g>
      <g class="box model"><rect x="658" y="290" width="92" height="30" rx="4"/><text class="nm" x="704" y="309" text-anchor="middle">application</text></g>
      <g class="box model"><rect x="752" y="290" width="92" height="30" rx="4"/><text class="nm" x="798" y="309" text-anchor="middle">project</text></g>
      <g class="box model"><rect x="846" y="290" width="92" height="30" rx="4"/><text class="nm" x="892" y="309" text-anchor="middle">definition</text></g>
      <g class="box model"><rect x="940" y="290" width="92" height="30" rx="4"/><text class="nm" x="986" y="309" text-anchor="middle">vm_node</text></g>
      <g class="bar"><rect x="470" y="60" width="562" height="26" rx="6"/><text class="rl" x="480" y="77">platform.admin</text></g>
      <text class="d" x="596" y="77">everything in the model</text>
      <g class="bar"><rect x="470" y="104" width="562" height="26" rx="6"/><text class="rl" x="480" y="121">platform.operator</text></g>
      <text class="d" x="617" y="121">the platform's own, and reading everything on it</text>
      <g class="bar"><rect x="470" y="148" width="562" height="26" rx="6"/><text class="rl" x="480" y="165">platform.support</text></g>
      <text class="d" x="610" y="165">reading everything, the platform included</text>
      <g class="bar"><rect x="752" y="192" width="280" height="26" rx="6"/><text class="rl" x="762" y="209">application.developer</text></g>
      <text class="d" x="744" y="209" text-anchor="end">inside an application, none of its own</text>
      <g class="bar"><rect x="564" y="236" width="374" height="26" rx="6"/><text class="rl" x="574" y="253">organization.admin</text></g>
      <text class="d" x="718" y="253">and everything inside it</text>
      <text class="lane-t grant" x="1030" y="28">Declared, custom</text>
      <g class="chip "><rect x="1030" y="44" width="123" height="20" rx="10"/><text x="1091" y="58">vm_node.registrar</text></g>
      <text class="dm" x="1042" y="84">can_register_node</text>
      <g class="chip "><rect x="1030" y="100" width="97" height="20" rx="10"/><text x="1078" y="114">vm_node.agent</text></g>
      <text class="dm" x="1042" y="140">can_report · can_view</text>
      <g class="chip "><rect x="1030" y="156" width="135" height="20" rx="10"/><text x="1097" y="170">application.runtime</text></g>
      <text class="dm" x="1042" y="196">can_register_services</text>
      <text class="d" x="1062" y="226">@AuthzRole on the service</text>
      <g class="chip "><rect x="1030" y="262" width="97" height="20" rx="10"/><text x="1078" y="276">a custom role</text></g>
      <text class="d" x="1042" y="302">picked from the catalog</text>
      <text class="d" x="1042" y="318">PermissionService.saveRole</text>
      <text class="d" x="20" y="356">every role is seeded as role:&lt;id&gt; #&lt;permission&gt; @user:* by the reconciler, before the model version becomes current</text>
    </svg>
    <svg v-else-if="view === 'walk'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 540" role="img" aria-label="The tuple graph an engine check walks in the platform store: the user is a member of a role binding, the binding carries a role that bundles the permission, the binding is attached to the project, and the same relation is tried on the application, the organization and the platform in turn; group membership and organization membership are alternative ways to be a binding's member.">
      <defs>
        <marker id="arr4" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="arr4-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>

      <g class="box req"><rect x="40" y="340" width="130" height="44" rx="22"/><text class="m" x="105" y="368" text-anchor="middle">user:u</text></g>
      <g class="box req"><rect x="40" y="450" width="130" height="44" rx="22"/><text class="m" x="105" y="478" text-anchor="middle">group:g</text></g>
      <g class="box grant"><rect x="330" y="340" width="180" height="44" rx="4"/><text class="m" x="420" y="368" text-anchor="middle">role_binding:b</text></g>
      <g class="box grant"><rect x="330" y="40" width="180" height="44" rx="4"/><text class="m" x="420" y="68" text-anchor="middle">role:r</text></g>
      <g class="box model"><rect x="720" y="40" width="170" height="44" rx="4"/><text class="m" x="805" y="68" text-anchor="middle">platform:kinotic</text></g>
      <g class="box model"><rect x="720" y="140" width="170" height="44" rx="4"/><text class="m" x="805" y="168" text-anchor="middle">organization:o</text></g>
      <g class="box model"><rect x="720" y="240" width="170" height="44" rx="4"/><text class="m" x="805" y="268" text-anchor="middle">application:a</text></g>
      <g class="box model"><rect x="720" y="340" width="170" height="44" rx="4"/><text class="m" x="805" y="368" text-anchor="middle">project:p</text></g>

      <path class="e dash" d="M314,62 H330"/>
      <text class="dm" x="310" y="58" text-anchor="end">role:r #project_can_view @user:*</text>
      <text class="dm" x="310" y="74" text-anchor="end">role:r #project_can_edit @user:*</text>
      <text class="d" x="310" y="91" text-anchor="end">the role bundles the permission</text>

      <path class="e" marker-end="url(#arr4)" d="M105,340 V120 H760 V140"/>
      <text class="lbl m" x="440" y="113" text-anchor="middle">organization:o #member @user:u, written when the user is created</text>

      <path class="e acc" marker-end="url(#arr4-acc)" d="M170,362 H330"/>
      <text class="lbl m" x="250" y="332" text-anchor="middle">role_binding:b #member @user:u</text>
      <g class="badge"><circle cx="250" cy="378" r="10"/><text x="250" y="382">2</text></g>

      <path class="e" marker-end="url(#arr4)" d="M105,384 V450"/>
      <text class="lbl m" x="115" y="421">group:g #member @user:u</text>
      <path class="e dash" marker-end="url(#arr4)" d="M170,472 H420 V384"/>
      <text class="lbl m" x="300" y="465" text-anchor="middle">or #member @group:g#member</text>

      <path class="e acc" marker-end="url(#arr4-acc)" d="M420,340 V84"/>
      <text class="lbl m" x="430" y="216">role_binding:b #role @role:r</text>
      <g class="badge"><circle cx="420" cy="236" r="10"/><text x="420" y="240">3</text></g>

      <path class="e acc" marker-end="url(#arr4-acc)" d="M510,362 H720"/>
      <text class="lbl m" x="615" y="347" text-anchor="middle">project:p #role_binding</text>
      <text class="lbl m" x="615" y="361" text-anchor="middle">@role_binding:b</text>
      <g class="badge"><circle cx="615" cy="380" r="10"/><text x="615" y="384">1</text></g>

      <g class="badge"><circle cx="905" cy="362" r="10"/><text x="905" y="366">4</text></g>
      <text class="d" x="920" y="358">or project_can_edit</text>
      <text class="d" x="920" y="373">on project:p, implied</text>

      <path class="e acc" marker-end="url(#arr4-acc)" d="M805,340 V284"/>
      <text class="lbl m" x="815" y="316">project:p #application @application:a</text>
      <path class="e acc" marker-end="url(#arr4-acc)" d="M805,240 V184"/>
      <text class="lbl m" x="815" y="216">application:a #organization @organization:o</text>
      <path class="e acc" marker-end="url(#arr4-acc)" d="M805,140 V84"/>
      <text class="lbl m" x="815" y="116">organization:o #platform @platform:kinotic</text>
      <g class="badge"><circle cx="905" cy="262" r="10"/><text x="905" y="266">5</text></g>
      <text class="d" x="920" y="258">the same relation tried</text>
      <text class="d" x="920" y="273">on each ancestor, against</text>
      <text class="d" x="920" y="288">its own bindings</text>

      <text class="d" x="590" y="520" text-anchor="middle">a binding may name organization:o#member, application:a#end_user or tenant:t#member as its member, reaching everyone in it</text>
    </svg>
    <svg v-else-if="view === 'grants'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1210 530" role="img" aria-label="The two store trees with every writer of a grant drawn beside the object it binds on: in the platform store the bootstraps, sign-up, node registration, deployment and the two access services; in the application's store the tenant sign-up, the tenant created for a user, the first SSO sign-in and the two access services. Dashed regions mark the objects an access service may bind on.">
      <defs>
        <marker id="f8" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f8-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <rect class="region" x="20" y="40" width="690" height="470" rx="10"/>
      <text class="lane-t model" x="32" y="62">Platform store</text>
      <rect class="region" x="740" y="40" width="450" height="470" rx="10"/>
      <text class="lane-t model" x="752" y="62">An application's store</text>
      <g class="box model"><rect x="340" y="80" width="150" height="36" rx="4"/><text class="nm" x="415" y="102" text-anchor="middle">platform:kinotic</text></g>
      <g class="box model"><rect x="340" y="180" width="150" height="36" rx="4"/><text class="nm" x="415" y="202" text-anchor="middle">organization:o</text></g>
      <g class="box model"><rect x="340" y="280" width="150" height="36" rx="4"/><text class="nm" x="415" y="302" text-anchor="middle">application:a</text></g>
      <g class="box model"><rect x="210" y="380" width="150" height="36" rx="4"/><text class="nm" x="285" y="402" text-anchor="middle">entity_definition:d</text></g>
      <g class="box model"><rect x="400" y="380" width="150" height="36" rx="4"/><text class="nm" x="475" y="402" text-anchor="middle">project:p</text></g>
      <g class="box model"><rect x="520" y="80" width="150" height="36" rx="4"/><text class="nm" x="595" y="102" text-anchor="middle">vm_node:n</text></g>
      <path class="e" marker-end="url(#f8)" d="M415,180 V116"/>
      <path class="e" marker-end="url(#f8)" d="M415,280 V216"/>
      <path class="e" marker-end="url(#f8)" d="M475,380 L450,316"/>
      <path class="e" marker-end="url(#f8)" d="M285,380 L380,316"/>
      <path class="e" marker-end="url(#f8)" d="M520,97 H490"/>
      <g class="box req"><rect x="40" y="72" width="170" height="32" rx="16"/><text class="pl" x="125" y="92" text-anchor="middle">platform bootstrap</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M210,88 H340"/>
      <text class="lbl m grant" x="275" y="74" text-anchor="middle">platform.admin</text>
      <text class="lbl m grant" x="275" y="103" text-anchor="middle">vm_node.registrar</text>
      <g class="box req"><rect x="40" y="122" width="170" height="32" rx="16"/><text class="pl" x="125" y="142" text-anchor="middle">SystemAccessService</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M210,138 H300 V110 H340"/>
      <text class="lbl m grant" x="258" y="154" text-anchor="middle">any role</text>
      <g class="box req"><rect x="40" y="182" width="170" height="32" rx="16"/><text class="pl" x="125" y="202" text-anchor="middle">sign-up</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M210,198 H340"/>
      <text class="lbl m grant" x="275" y="191" text-anchor="middle">organization.admin</text>
      <g class="box req"><rect x="40" y="224" width="170" height="32" rx="16"/><text class="pl" x="125" y="244" text-anchor="middle">organization bootstrap</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M210,240 H300 V210 H340"/>
      <text class="lbl m grant" x="222" y="268">organization.admin</text>
      <g class="box req"><rect x="520" y="150" width="170" height="32" rx="16"/><text class="pl" x="605" y="170" text-anchor="middle">node registration</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M595,150 V116"/>
      <text class="lbl m grant" x="605" y="138">vm_node.agent</text>
      <g class="box req"><rect x="500" y="450" width="170" height="32" rx="16"/><text class="pl" x="585" y="470" text-anchor="middle">deployment</text></g>
      <path class="e acc" marker-end="url(#f8-acc)" d="M560,450 L520,416"/>
      <text class="lbl m grant" x="560" y="440">project.editor</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M670,465 H690 V298 H490"/>
      <text class="lbl m grant" x="600" y="292" text-anchor="middle">application.runtime</text>
      <rect class="region grant" x="205" y="168" width="350" height="250" rx="8"/>
      <text class="rl" x="214" y="436">PermissionService.grant: any role, on any of</text>
      <text class="rl" x="214" y="452">these, to a user, a machine or a group</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M210,330 H250"/>
      <g class="box req"><rect x="40" y="314" width="170" height="32" rx="16"/><text class="pl" x="125" y="334" text-anchor="middle">PermissionService</text></g>
      <g class="box model"><rect x="990" y="80" width="160" height="36" rx="4"/><text class="nm" x="1070" y="102" text-anchor="middle">application:a </text></g>
      <g class="box model"><rect x="990" y="190" width="160" height="36" rx="4"/><text class="nm" x="1070" y="212" text-anchor="middle">tenant:t</text></g>
      <g class="box model"><rect x="990" y="300" width="160" height="36" rx="4"/><text class="nm" x="1070" y="322" text-anchor="middle">entity_definition:d </text></g>
      <g class="box engine ctx"><rect x="982" y="410" width="176" height="36" rx="4"/><text class="nm" x="1070" y="432" text-anchor="middle">tenant_definition:d@t</text></g>
      <path class="e" marker-end="url(#f8)" d="M1070,190 V116"/>
      <path class="e" marker-end="url(#f8)" d="M1150,317 H1166 V98 H1150"/>
      <path class="e acc dash" marker-end="url(#f8-acc)" d="M1070,410 V336"/>
      <path class="e acc dash" marker-end="url(#f8-acc)" d="M1158,427 H1178 V208 H1150"/>
      <rect class="region grant" x="975" y="66" width="190" height="390" rx="8"/>
      <text class="rl" x="760" y="480">ApplicationAccessService.grant: any role, on any of these,</text>
      <text class="rl" x="760" y="496">to an end user or a machine of the application</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M930,470 H1010 V456"/>
      <g class="box req"><rect x="752" y="440" width="178" height="32" rx="16"/><text class="pl" x="841" y="460" text-anchor="middle">ApplicationAccessService</text></g>
      <g class="box req"><rect x="752" y="130" width="178" height="32" rx="16"/><text class="pl" x="841" y="150" text-anchor="middle">sign-up · tenant per user</text></g>
      <text class="lbl m grant" x="760" y="180">tenant.admin</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M930,146 H950 V200 H990"/>
      <g class="box req"><rect x="760" y="200" width="150" height="32" rx="16"/><text class="pl" x="835" y="220" text-anchor="middle">first SSO sign-in</text></g>
      <text class="lbl m grant" x="760" y="250">the role the tenant chose</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M910,216 H990"/>
      <g class="box req"><rect x="760" y="270" width="150" height="32" rx="16"/><text class="pl" x="835" y="290" text-anchor="middle">TenantMemberService</text></g>
      <text class="lbl m grant" x="760" y="320">any role</text>
      <path class="e acc" marker-end="url(#f8-acc)" d="M910,286 H950 V232 H990"/>
    </svg>
    <svg v-else-if="view === 'pair'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1300 462" role="img" aria-label="The contextual pair object a tenant's user is checked on: the pair's definition and tenant edges are supplied with the check; the definition and the tenant are each contained in the application, which everyone holds placed on; the pair holds a permission through a binding on it, an implying permission, the definition, or the tenant when the definition is placed.">
      <defs>
        <marker id="arr5" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="arr5-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>

      <g class="box req"><rect x="40" y="230" width="130" height="44" rx="22"/><text class="m" x="105" y="258" text-anchor="middle">user:u</text></g>
      <g class="box grant"><rect x="300" y="230" width="180" height="44" rx="4"/><text class="m" x="390" y="258" text-anchor="middle">role_binding:b</text></g>
      <g class="box grant"><rect x="300" y="60" width="180" height="44" rx="4"/><text class="m" x="390" y="88" text-anchor="middle">role:r</text></g>
      <g class="box engine ctx"><rect x="590" y="230" width="240" height="44" rx="4"/><text class="m" x="710" y="258" text-anchor="middle">tenant_definition:d@t</text></g>
      <g class="box model"><rect x="960" y="90" width="210" height="44" rx="4"/><text class="m" x="1065" y="118" text-anchor="middle">entity_definition:d</text></g>
      <g class="box model"><rect x="960" y="230" width="210" height="44" rx="4"/><text class="m" x="1065" y="258" text-anchor="middle">application:a</text></g>
      <g class="box model"><rect x="960" y="370" width="210" height="44" rx="4"/><text class="m" x="1065" y="398" text-anchor="middle">tenant:t</text></g>

      <path class="e dash" d="M284,82 H300"/>
      <text class="dm" x="280" y="78" text-anchor="end">#entity_definition_can_read @user:*</text>
      <text class="dm" x="280" y="94" text-anchor="end">#entity_definition_can_edit @user:*</text>
      <text class="d" x="280" y="111" text-anchor="end">entity_definition.editor, for instance</text>

      <path class="e acc" marker-end="url(#arr5-acc)" d="M170,252 H300"/>
      <text class="lbl m" x="235" y="245" text-anchor="middle">#member @user:u</text>
      <g class="badge"><circle cx="235" cy="268" r="10"/><text x="235" y="272">1</text></g>

      <path class="e acc" marker-end="url(#arr5-acc)" d="M390,230 V104"/>
      <text class="lbl m" x="400" y="172">role_binding:b #role @role:r</text>
      <g class="badge"><circle cx="390" cy="190" r="10"/><text x="390" y="194">2</text></g>

      <path class="e acc" marker-end="url(#arr5-acc)" d="M480,252 H590"/>
      <text class="lbl m" x="535" y="237" text-anchor="middle">#role_binding</text>
      <text class="lbl m" x="535" y="251" text-anchor="middle">@role_binding:b</text>
      <g class="badge"><circle cx="535" cy="270" r="10"/><text x="535" y="274">3</text></g>

      <path class="e acc dash" marker-end="url(#arr5-acc)" d="M830,240 H880 V112 H960"/>
      <text class="lbl m" x="890" y="160">#definition</text>
      <text class="lbl m" x="890" y="174">@entity_definition:d</text>
      <text class="lbl" x="890" y="190">contextual: the id the</text>
      <text class="lbl" x="890" y="204">request names</text>
      <g class="badge"><circle cx="880" cy="128" r="10"/><text x="880" y="132">4</text></g>

      <path class="e acc dash" marker-end="url(#arr5-acc)" d="M830,264 H880 V392 H960"/>
      <text class="lbl m" x="890" y="316">#tenant @tenant:t</text>
      <text class="lbl" x="890" y="332">contextual: the caller's</text>
      <text class="lbl" x="890" y="346">tenant</text>
      <g class="badge"><circle cx="880" cy="376" r="10"/><text x="880" y="380">5</text></g>

      <path class="e" marker-end="url(#arr5)" d="M1065,134 V230"/>
      <text class="lbl m" x="1075" y="176">#application @application:a</text>
      <text class="lbl" x="1075" y="191">written when the definition is created</text>
      <path class="e" marker-end="url(#arr5)" d="M1065,370 V274"/>
      <text class="lbl m" x="1075" y="326">#application @application:a</text>
      <text class="lbl" x="1075" y="341">written when the user is created</text>
      <text class="dm" x="1075" y="298">application:a #placed @user:*</text>
      <text class="d" x="1075" y="313">at provisioning</text>

      <text class="d" x="560" y="304">held through: a binding on the pair, the definition,</text>
      <text class="d" x="560" y="321">or the tenant if placed: placed on the pair is placed</text>
      <text class="d" x="560" y="338">from the definition, which is placed from the</text>
      <text class="d" x="560" y="355">application, which everyone holds (user:*)</text>
      <text class="d" x="560" y="382">another application's definition is placed in no</text>
      <text class="d" x="560" y="399">store of this one, so a grant on the tenant reaches</text>
      <text class="d" x="560" y="416">nothing there</text>
      <text class="d" x="560" y="446">an organization's member is checked on entity_definition:d in the platform's store instead, under its application</text>
    </svg>
    <svg v-else-if="view === 'application'" class="authz-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 440" role="img" aria-label="An application's service goes from its decorators through kinotic sync and the runtime's registration into the directory, where its checks are derived; the reconciler writes the application's model and roles into the store; grants from the tenant sign-up, the portal and the application's own pages add bindings; and each request is checked in that store.">
      <defs>
        <marker id="f10" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah" d="M0,0 L10,5 L0,10 z"/></marker>
        <marker id="f10-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path class="ah acc" d="M0,0 L10,5 L0,10 z"/></marker>
      </defs>
      <g class="box code"><rect x="20" y="40" width="200" height="64" rx="4"/><text class="t" x="30" y="64">The decorators</text><text class="dm" x="30" y="86">@AuthzResource @AuthzCheck</text></g>
      <g class="box code"><rect x="260" y="40" width="200" height="64" rx="4"/><text class="t" x="270" y="64">ServiceDefinitions.ts</text><text class="dm" x="270" y="86">written by kinotic sync</text></g>
      <g class="box req"><rect x="500" y="40" width="200" height="64" rx="4"/><text class="t" x="510" y="64">The runtime registers</text><text class="dm" x="510" y="86">needs application.runtime</text></g>
      <g class="box store"><rect x="740" y="40" width="200" height="64" rx="4"/><text class="t" x="750" y="64">Directory entry</text><text class="dm" x="750" y="86">checks derived once</text></g>
      <g class="box model"><rect x="980" y="40" width="200" height="64" rx="4"/><text class="t" x="990" y="64">The application's model</text><text class="dm" x="990" y="86">type, permissions, roles</text></g>
      <path class="e" marker-end="url(#f10)" d="M220,72 H260"/>
      <text class="lbl" x="240" y="30" text-anchor="middle">sync</text>
      <path class="e" marker-end="url(#f10)" d="M460,72 H500"/>
      <text class="lbl" x="480" y="30" text-anchor="middle">on connect</text>
      <path class="e" marker-end="url(#f10)" d="M700,72 H740"/>
      <text class="lbl" x="720" y="30" text-anchor="middle">register</text>
      <path class="e" marker-end="url(#f10)" d="M940,72 H980"/>
      <text class="lbl" x="960" y="30" text-anchor="middle">reconcile</text>
      <g class="box engine"><rect x="980" y="220" width="200" height="64" rx="4"/><text class="t" x="990" y="244">The application's store</text><text class="dm" x="990" y="266">tuples and the model</text></g>
      <path class="e acc" marker-end="url(#f10-acc)" d="M1080,104 V220"/>
      <text class="lbl acc" x="1072" y="166" text-anchor="end">model, then roles</text>
      <g class="box grant"><rect x="500" y="130" width="200" height="56" rx="4"/><text class="t" x="510" y="154">tenant.admin</text><text class="dm" x="510" y="176">at sign-up, per-user tenant</text></g>
      <g class="box grant"><rect x="500" y="200" width="200" height="56" rx="4"/><text class="t" x="510" y="224">ApplicationAccessService</text><text class="dm" x="510" y="246">the portal, four places</text></g>
      <g class="box grant"><rect x="500" y="270" width="200" height="56" rx="4"/><text class="t" x="510" y="294">TenantMemberService</text><text class="dm" x="510" y="316">the app's own pages</text></g>
      <path class="e acc" marker-end="url(#f10-acc)" d="M700,158 H940 L980,252"/>
      <path class="e acc" marker-end="url(#f10-acc)" d="M700,228 H980 V252"/>
      <path class="e acc" marker-end="url(#f10-acc)" d="M700,298 H940 L980,252"/>
      <text class="lbl acc" x="840" y="220">bindings</text>
      <g class="box engine"><rect x="20" y="350" width="420" height="64" rx="4"/><text class="t" x="30" y="374">The check</text><text class="dm" x="30" y="396">tenant:t #report_can_generate @user:u</text></g>
      <path class="e acc" marker-end="url(#f10-acc)" d="M1080,284 V382 H440"/>
      <text class="lbl acc" x="760" y="374" text-anchor="middle">one check per request, from the tuples through the model</text>
      <g class="box req"><rect x="20" y="250" width="200" height="32" rx="16"/><text class="pl" x="120" y="270" text-anchor="middle">a request from the app's user</text></g>
      <path class="e" marker-end="url(#f10)" d="M120,282 V350"/>
      <text class="d" x="20" y="426">a check resolved to the application's own object, report:r, is reached by no grant yet: nothing places it under a tenant, so declare it on the tenant</text>
    </svg>
  </div>
  </DiagramFrame>
</template>

<style>
/* Palette follows the site color-mode class, so the diagram tracks the theme toggle. One hue per
   subsystem, used identically in every view, so a component is recognizable across them. */
svg.authz-diagram {
  --panel: #FFFFFF;
  --fg: #1A2332;
  --muted: #51607A;
  --line: #8795A7;
  --rule: #D6DCE4;
  --code-fill: #EFE9FB;   --code-ink: #5B3FC4;   /* declarations and derivation */
  --store-fill: #FBF2DF;  --store-ink: #9A620C;  /* the service directory */
  --req-fill: #E6F0FC;    --req-ink: #2A63B5;    /* the request path: gateway, authorizer, callers */
  --model-fill: #E6F6EC;  --model-ink: #27744A;  /* reconcile, model generation, scope objects */
  --engine-fill: #E0F4F6; --engine-ink: #0E7C8B; /* the engine and the walk it makes */
  --grant-fill: #EFE9FB;  --grant-ink: #5B3FC4;  /* roles and bindings in the tuple graph */
  --sans: 'Inter', 'Segoe UI', system-ui, sans-serif;
  --mono: 'Fira Code', ui-monospace, 'SF Mono', Menlo, monospace;
}
.dark svg.authz-diagram {
  --panel: #161D26;
  --fg: #E4E9EF;
  --muted: #A3AFBE;
  --line: #6F7E91;
  --rule: #2A3442;
  --code-fill: #2B2447;   --code-ink: #B9A8F8;
  --store-fill: #3A2D12;  --store-ink: #F0B85C;
  --req-fill: #15283F;    --req-ink: #7DB4F5;
  --model-fill: #133022;  --model-ink: #6CCF95;
  --engine-fill: #0F3034; --engine-ink: #4FD0E0;
  --grant-fill: #2B2447;  --grant-ink: #B9A8F8;
}

.authz-diagram-wrap { overflow-x: auto; }
svg.authz-diagram { min-width: 920px; width: 100%; height: auto; display: block; }

/* ── SVG vocabulary: a box's hue is its subsystem; titles take the hue's ink, body text stays neutral ── */
svg.authz-diagram .box rect { fill: var(--panel); stroke: var(--line); stroke-width: 1.2; }
svg.authz-diagram .t { font: 600 13px var(--sans); fill: var(--fg); }
svg.authz-diagram .m { font: 500 13px var(--mono); fill: var(--fg); }
svg.authz-diagram .d { font: 12px var(--sans); fill: var(--muted); }
svg.authz-diagram .dm { font: 11.5px var(--mono); fill: var(--muted); }
svg.authz-diagram .box.code rect { fill: var(--code-fill); stroke: var(--code-ink); }
svg.authz-diagram .box.code .t { fill: var(--code-ink); }
svg.authz-diagram .box.store rect { fill: var(--store-fill); stroke: var(--store-ink); }
svg.authz-diagram .box.store .t { fill: var(--store-ink); }
svg.authz-diagram .box.req rect { fill: var(--req-fill); stroke: var(--req-ink); }
svg.authz-diagram .box.req .t, svg.authz-diagram .box.req .m { fill: var(--req-ink); }
svg.authz-diagram .box.model rect { fill: var(--model-fill); stroke: var(--model-ink); }
svg.authz-diagram .box.model .t, svg.authz-diagram .box.model .m { fill: var(--model-ink); }
svg.authz-diagram .box.engine rect { fill: var(--engine-fill); stroke: var(--engine-ink); stroke-width: 1.6; }
svg.authz-diagram .box.engine .t { fill: var(--engine-ink); }
svg.authz-diagram .box.engine.ctx rect { stroke-dasharray: 6 4; }
svg.authz-diagram .box.grant rect { fill: var(--grant-fill); stroke: var(--grant-ink); }
svg.authz-diagram .box.grant .m { fill: var(--grant-ink); }
svg.authz-diagram .e { fill: none; stroke: var(--line); stroke-width: 1.4; }
svg.authz-diagram .e.acc { stroke: var(--engine-ink); stroke-width: 1.8; }
svg.authz-diagram .e.dash { stroke-dasharray: 5 4; }
/* A label on an edge knocks the edge out behind it with a halo the color of the page */
svg.authz-diagram .lbl { font: 11.5px var(--sans); fill: var(--fg); paint-order: stroke; stroke: var(--ui-bg); stroke-width: 5px; stroke-linejoin: round; }
svg.authz-diagram .lbl.m { font: 500 11.5px var(--mono); }
svg.authz-diagram .lbl.acc { fill: var(--engine-ink); }
svg.authz-diagram .lbl.grant { fill: var(--grant-ink); }
svg.authz-diagram .ah { fill: var(--line); }
svg.authz-diagram .ah.acc { fill: var(--engine-ink); }
svg.authz-diagram .badge circle { fill: var(--fg); }
svg.authz-diagram .badge text { font: 600 11px var(--mono); fill: var(--panel); text-anchor: middle; }
svg.authz-diagram .lane { fill: none; stroke: var(--rule); stroke-dasharray: 3 5; }
svg.authz-diagram .lane-t { font: 600 11px var(--sans); fill: var(--muted); letter-spacing: 0.06em; text-transform: uppercase; }
svg.authz-diagram .lane-t.code { fill: var(--code-ink); }
svg.authz-diagram .lane-t.model { fill: var(--model-ink); }
svg.authz-diagram .lane-t.grant { fill: var(--grant-ink); }
svg.authz-diagram .nm { font: 500 11.5px var(--mono); fill: var(--fg); }
svg.authz-diagram .box.model .nm { fill: var(--model-ink); }
svg.authz-diagram .box.grant .nm { fill: var(--grant-ink); }
svg.authz-diagram .box.engine .nm { fill: var(--engine-ink); }
svg.authz-diagram .pl { font: 500 12px var(--sans); fill: var(--req-ink); }
svg.authz-diagram .idt { font: 500 10.5px var(--mono); fill: var(--muted); }
svg.authz-diagram .ts { font: 600 11px var(--sans); fill: var(--code-ink); }
svg.authz-diagram .stub { font: 500 11px var(--mono); fill: var(--fg); }
svg.authz-diagram .chip rect { fill: var(--grant-fill); stroke: var(--grant-ink); stroke-width: 1.1; }
svg.authz-diagram .chip text { font: 500 10.5px var(--mono); fill: var(--grant-ink); text-anchor: middle; }
svg.authz-diagram .chip.perm rect { fill: var(--engine-fill); stroke: var(--engine-ink); }
svg.authz-diagram .chip.perm text { fill: var(--engine-ink); }
svg.authz-diagram .port { fill: var(--engine-fill); stroke: var(--engine-ink); stroke-width: 1.8; }
svg.authz-diagram .region { fill: none; stroke: var(--rule); stroke-dasharray: 6 5; }
svg.authz-diagram .region.grant { stroke: var(--grant-ink); }
svg.authz-diagram .ring { fill: var(--grant-fill); fill-opacity: 0.45; stroke: var(--grant-ink); stroke-width: 1.2; }
svg.authz-diagram .rl { font: 600 11.5px var(--mono); fill: var(--grant-ink); }
svg.authz-diagram .bar rect { fill: var(--grant-fill); stroke: var(--grant-ink); stroke-width: 1.2; }
svg.authz-diagram .span { stroke: var(--grant-ink); stroke-width: 1; stroke-dasharray: 2 3; }
svg.authz-diagram .rung rect { fill: var(--code-fill); stroke: var(--code-ink); stroke-width: 1.2; }
svg.authz-diagram .rung.hit rect { stroke: var(--engine-ink); stroke-width: 2.2; }
svg.authz-diagram .rn { font: 600 11px var(--mono); fill: var(--code-ink); }
svg.authz-diagram .rm { font: 500 11.5px var(--mono); fill: var(--fg); }
svg.authz-diagram .nocheck { fill: var(--panel); stroke: var(--muted); stroke-width: 1.6; }
svg.authz-diagram .x { stroke: var(--muted); stroke-width: 2.2; }
</style>
