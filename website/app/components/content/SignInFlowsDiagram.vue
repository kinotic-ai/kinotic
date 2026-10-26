<script setup lang="ts">
// One component, seven figures, so the page's diagrams share one palette and one vocabulary.
defineProps<{ figure: 'owners' | 'model' | 'offering' | 'paths' | 'identity' | 'organization-user' | 'doors' }>()
</script>

<template>
  <DiagramFrame>
  <div class="sign-in-diagram-wrap">

    <!-- ═══════════════════════════ OWNERS: two owners, two keys into one vault ═══════════════════════════ -->
    <svg v-if="figure === 'owners'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1140 440" role="img" aria-label="A system user seeds a managed registration, which names its secret in the vault and is read by that name at sign-in. An organization administrator saves its own configuration together with the client secret; the row has no secret field, and the service stores the secret in the organization's scope keyed by the configuration id, reading it back at sign-in. A crossed-out arrow shows that an organization's row cannot reach the system's named secrets. Clients only ever receive a summary: configuration id, name and provider, with its type, or at an application's login whether it signs people up.">
      <defs>
        <marker id="si1-ind" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-ind" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si1-grn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-grn" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <!-- actors -->
      <rect class="chip" x="60" y="30" width="220" height="44" rx="8"></rect>
      <text class="t-name" x="170" y="49" text-anchor="middle">System user</text>
      <text class="t-sub" x="170" y="65" text-anchor="middle">system participant</text>

      <rect class="chip" x="860" y="30" width="220" height="44" rx="8"></rect>
      <text class="t-name" x="970" y="49" text-anchor="middle">Organization admin</text>
      <text class="t-sub" x="970" y="65" text-anchor="middle">organization participant</text>

      <line class="flow-ind" x1="170" y1="74" x2="170" y2="120" marker-end="url(#si1-ind)"></line>
      <text class="t-lbl" x="180" y="102">seeds it</text>
      <line class="flow-grn" x1="970" y1="74" x2="970" y2="120" marker-end="url(#si1-grn)"></line>
      <text class="t-lbl" x="960" y="102" text-anchor="end">saves it, secret alongside</text>

      <!-- the two kinds -->
      <rect class="chip-ind" x="40" y="120" width="300" height="80" rx="8"></rect>
      <text class="t-name" x="190" y="144" text-anchor="middle">Managed registration</text>
      <text class="t-sub" x="190" y="161" text-anchor="middle">GitHub · Google · Microsoft</text>
      <text class="t-mono-ink" x="190" y="184" text-anchor="middle">secretNameRef: "google-platform"</text>

      <rect class="chip-grn" x="800" y="120" width="300" height="80" rx="8"></rect>
      <text class="t-name" x="950" y="144" text-anchor="middle">Organization's own configuration</text>
      <text class="t-sub" x="950" y="161" text-anchor="middle">its own Okta, Entra or Google tenant</text>
      <text class="t-mono-ink" x="950" y="184" text-anchor="middle">no secret field · confidentialClient: true</text>

      <!-- the vault -->
      <rect class="wall" x="340" y="250" width="460" height="130" rx="12"></rect>
      <text class="t-tag" x="570" y="274" text-anchor="middle">SECRET STORAGE · ONE VAULT</text>

      <rect class="chip-ind" x="356" y="290" width="200" height="72" rx="8"></rect>
      <text class="t-chip" x="456" y="312" text-anchor="middle">named by the system user</text>
      <text class="t-mono" x="456" y="330" text-anchor="middle">google-platform</text>
      <text class="t-mono" x="456" y="344" text-anchor="middle">github-platform …</text>

      <rect class="chip-grn" x="584" y="290" width="200" height="72" rx="8"></rect>
      <text class="t-chip" x="684" y="312" text-anchor="middle">scoped to an organization</text>
      <text class="t-mono" x="684" y="330" text-anchor="middle">scope acme</text>
      <text class="t-mono" x="684" y="344" text-anchor="middle">key = configuration id</text>

      <!-- paths to the vault -->
      <polyline class="flow-ind" points="190,200 190,326 356,326" marker-end="url(#si1-ind)"></polyline>
      <text class="t-lbl-ind" x="200" y="246">read by name at sign-in</text>
      <text class="t-tiny" x="200" y="260">SecretReferenceResolver</text>

      <polyline class="flow-grn" points="950,200 950,326 784,326" marker-end="url(#si1-grn)"></polyline>
      <text class="t-lbl-grn" x="940" y="246" text-anchor="end">stored at save, read at sign-in</text>
      <text class="t-tiny" x="940" y="260" text-anchor="end">SecretStorageService, in acme's scope</text>

      <!-- the blocked path -->
      <polyline class="flow-red" points="800,170 570,170 570,290"></polyline>
      <line class="dead" x1="558" y1="222" x2="582" y2="246"></line><line class="dead" x1="582" y1="222" x2="558" y2="246"></line>
      <text class="t-lbl-red" x="685" y="150" text-anchor="middle">cannot name one</text>
      <text class="t-tiny" x="685" y="164" text-anchor="middle">no field to put a name in</text>

      <!-- the wire -->
      <rect class="chip" x="40" y="396" width="1060" height="30" rx="8"></rect>
      <text class="t-mono-ink" x="570" y="416" text-anchor="middle">what any client sees of either kind:  { configurationId, name, provider }, with type (LoginProviderSummary) or signUp (ApplicationLoginProvider)</text>
    </svg>

    <!-- ═══════════════════════════ MODEL: the configuration classes ═══════════════════════════ -->
    <svg v-else-if="figure === 'model'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 600" role="img" aria-label="UML class diagram. OidcConfiguration, sealed and abstract, holds id, name, provider, clientId, authority, audience, enabled and timestamps, and refers to the enums OidcProviderType and OidcConfigurationType. Two sealed abstract subclasses: ManagedOidcConfiguration with secretNameRef and the explicit endpoint fields, and OwnedOidcConfiguration with organizationId, providerAccount, confidentialClient and createUserOnSignIn. Managed has two final leaves, OrgSignupOidcConfiguration and ApplicationSocialOidcConfiguration. Owned has three final leaves: OrganizationOidcConfiguration, ApplicationOidcConfiguration with applicationId and clientRegistrationType, MANAGED or OWN, and TenantOidcConfiguration with applicationId and tenantId. Application and Tenant appear as the entities those ids point at.">
      <defs>
        <marker id="si2-gen" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto"><polygon class="mk-gen" points="1,1 11,6 1,11"></polygon></marker>
        <marker id="si2-mut" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><polygon class="mk-mut" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <!-- enums -->
      <rect class="cls-enum" x="40" y="40" width="240" height="132" rx="4"></rect>
      <text class="t-ster" x="160" y="58" text-anchor="middle">«enum»</text>
      <text class="t-name" x="160" y="74" text-anchor="middle">OidcProviderType</text>
      <line class="sep" x1="40" y1="82" x2="280" y2="82"></line>
      <text class="t-attr" x="52" y="100">GOOGLE · AZURE_AD · OKTA</text>
      <text class="t-attr" x="52" y="116">GITHUB · MICROSOFT_LIVE · APPLE</text>
      <text class="t-attr" x="52" y="132">… · KEYCLOAK · OIDC</text>
      <text class="t-attr-mut" x="52" y="156">key, authority template, account rule</text>

      <rect class="cls-enum" x="900" y="40" width="240" height="132" rx="4"></rect>
      <text class="t-ster" x="1020" y="58" text-anchor="middle">«enum»</text>
      <text class="t-name" x="1020" y="74" text-anchor="middle">OidcConfigurationType</text>
      <line class="sep" x1="900" y1="82" x2="1140" y2="82"></line>
      <text class="t-attr" x="912" y="100">ORG_SIGNUP · APP_SOCIAL</text>
      <text class="t-attr" x="912" y="116">ORGANIZATION · APPLICATION · TENANT</text>
      <text class="t-attr-mut" x="912" y="140">one per leaf class; the index's</text>
      <text class="t-attr-mut" x="912" y="156">type discriminator</text>

      <!-- root -->
      <rect class="cls-abs" x="440" y="40" width="300" height="196" rx="4"></rect>
      <text class="t-ster" x="590" y="58" text-anchor="middle">«sealed, abstract»</text>
      <text class="t-name" x="590" y="74" text-anchor="middle">OidcConfiguration</text>
      <line class="sep" x1="440" y1="82" x2="740" y2="82"></line>
      <text class="t-attr" x="452" y="100">id: String</text>
      <text class="t-attr" x="452" y="116">name: String</text>
      <text class="t-attr" x="452" y="132">provider: OidcProviderType</text>
      <text class="t-attr" x="452" y="148">clientId: String</text>
      <text class="t-attr" x="452" y="164">authority: String · audience: String</text>
      <text class="t-attr" x="452" y="180">enabled: boolean · created, updated: Date</text>
      <line class="sep" x1="440" y1="190" x2="740" y2="190"></line>
      <text class="t-attr" x="452" y="208">getType(): OidcConfigurationType</text>
      <text class="t-attr-mut" x="452" y="224">the leaf class is the type</text>

      <line class="link" x1="440" y1="132" x2="280" y2="132" marker-end="url(#si2-mut)"></line>
      <text class="t-tiny" x="360" y="126" text-anchor="middle">provider</text>
      <line class="link" x1="740" y1="208" x2="900" y2="160" marker-end="url(#si2-mut)"></line>
      <text class="t-tiny" x="836" y="198">getType()</text>

      <!-- managed -->
      <rect class="cls-abs" x="60" y="300" width="320" height="150" rx="4"></rect>
      <text class="t-ster" x="220" y="318" text-anchor="middle">«sealed, abstract»</text>
      <text class="t-name" x="220" y="334" text-anchor="middle">ManagedOidcConfiguration</text>
      <line class="sep" x1="60" y1="342" x2="380" y2="342"></line>
      <text class="t-attr" x="72" y="360">secretNameRef: String</text>
      <text class="t-attr" x="72" y="376">authorizationUri, tokenUri: String</text>
      <text class="t-attr" x="72" y="392">userInfoUri, userEmailsUri: String</text>
      <text class="t-attr" x="72" y="408">scopes: String</text>
      <text class="t-attr-mut" x="72" y="432">seeded by migration; the system's registration</text>

      <!-- owned -->
      <rect class="cls-abs" x="800" y="300" width="320" height="150" rx="4"></rect>
      <text class="t-ster" x="960" y="318" text-anchor="middle">«sealed, abstract»</text>
      <text class="t-name" x="960" y="334" text-anchor="middle">OwnedOidcConfiguration</text>
      <line class="sep" x1="800" y1="342" x2="1120" y2="342"></line>
      <text class="t-attr" x="812" y="360">organizationId: String</text>
      <text class="t-attr" x="812" y="376">providerAccount: String</text>
      <text class="t-attr" x="812" y="392">confidentialClient: boolean</text>
      <text class="t-attr" x="812" y="408">createUserOnSignIn: boolean</text>
      <text class="t-attr-mut" x="812" y="432">OrganizationScoped · secret in the org's vault</text>

      <!-- generalizations to the root -->
      <polyline class="gen" points="220,300 220,268 590,268 590,236" marker-end="url(#si2-gen)"></polyline>
      <polyline class="gen" points="960,300 960,268 590,268"></polyline>

      <!-- the entities the ids point at -->
      <rect class="cls" x="440" y="300" width="300" height="76" rx="4"></rect>
      <text class="t-name" x="590" y="320" text-anchor="middle">Application</text>
      <line class="sep" x1="440" y1="328" x2="740" y2="328"></line>
      <text class="t-attr" x="452" y="346">tenantManagement: TenantManagement</text>

      <rect class="cls" x="440" y="396" width="300" height="76" rx="4"></rect>
      <text class="t-name" x="590" y="416" text-anchor="middle">Tenant</text>
      <line class="sep" x1="440" y1="424" x2="740" y2="424"></line>
      <text class="t-attr" x="452" y="442">id, organizationId, applicationId: String</text>
      <text class="t-attr" x="452" y="458">name, description: String</text>

      <!-- managed leaves -->
      <rect class="cls" x="40" y="516" width="170" height="60" rx="4"></rect>
      <text class="t-name" x="125" y="536" text-anchor="middle">OrgSignupOidc…</text>
      <line class="sep" x1="40" y1="544" x2="210" y2="544"></line>
      <text class="t-attr-mut" x="125" y="562" text-anchor="middle">type ORG_SIGNUP</text>

      <rect class="cls" x="230" y="516" width="190" height="60" rx="4"></rect>
      <text class="t-name" x="325" y="536" text-anchor="middle">ApplicationSocialOidc…</text>
      <line class="sep" x1="230" y1="544" x2="420" y2="544"></line>
      <text class="t-attr-mut" x="325" y="562" text-anchor="middle">type APP_SOCIAL</text>

      <polyline class="gen" points="125,516 125,484 220,484 220,450" marker-end="url(#si2-gen)"></polyline>
      <polyline class="gen" points="325,516 325,484 220,484"></polyline>

      <!-- owned leaves -->
      <rect class="cls" x="620" y="500" width="170" height="60" rx="4"></rect>
      <text class="t-name" x="705" y="520" text-anchor="middle">OrganizationOidc…</text>
      <line class="sep" x1="620" y1="528" x2="790" y2="528"></line>
      <text class="t-attr-mut" x="705" y="546" text-anchor="middle">type ORGANIZATION</text>

      <rect class="cls" x="810" y="500" width="170" height="92" rx="4"></rect>
      <text class="t-name" x="895" y="520" text-anchor="middle">ApplicationOidc…</text>
      <line class="sep" x1="810" y1="528" x2="980" y2="528"></line>
      <text class="t-attr" x="822" y="546">applicationId: String</text>
      <text class="t-attr" x="822" y="562">clientRegistrationType</text>
      <text class="t-attr-mut" x="822" y="578">type APPLICATION</text>

      <rect class="cls" x="1000" y="500" width="170" height="92" rx="4"></rect>
      <text class="t-name" x="1085" y="520" text-anchor="middle">TenantOidc…</text>
      <line class="sep" x1="1000" y1="528" x2="1170" y2="528"></line>
      <text class="t-attr" x="1012" y="546">applicationId: String</text>
      <text class="t-attr" x="1012" y="562">tenantId: String</text>
      <text class="t-attr-mut" x="1012" y="578">type TENANT</text>

      <polyline class="gen" points="705,500 705,476 960,476 960,450" marker-end="url(#si2-gen)"></polyline>
      <polyline class="gen" points="895,500 895,476 960,476"></polyline>
      <polyline class="gen" points="1085,500 1085,476 960,476"></polyline>
    </svg>

    <!-- ═══════════════════════════ OFFERING: which login offers a configuration ═══════════════════════════ -->
    <svg v-else-if="figure === 'offering'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1140 490" role="img" aria-label="Five configurations on the left: GitHub, the system's org sign-up registration; Acme Okta, the organization's own ORGANIZATION kind; a copy of Acme Okta of APPLICATION kind for the orders application; Google, a system social registration; Globex Entra, a TENANT kind for the globex tenant of orders. Three logins on the right: acme's own login on the management server, the acme--orders application login on the app server, and the globex tenant's login reached from the tenant's sites. GitHub reaches acme's login by right. Acme Okta reaches acme's login; an arrow from it to the application login is crossed out, since an ORGANIZATION kind is offered at the organization's login only. The copy reaches the application login. Google reaches the application login through orders' own Google configuration, which signs in through the system's registration because it is MANAGED. Globex Entra reaches the tenant login, which then also offers what the application login offers.">
      <defs>
        <marker id="si3-ind" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-ind" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si3-grn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-grn" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si3-amb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-amb" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si3-mut" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-mut" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <text class="t-tag" x="40" y="30">CONFIGURATIONS</text>
      <text class="t-tag" x="1100" y="30" text-anchor="end">LOGINS OF ORGANIZATION acme</text>

      <rect class="chip-ind" x="40" y="50" width="260" height="60" rx="8"></rect>
      <text class="t-name" x="170" y="74" text-anchor="middle">GitHub</text>
      <text class="t-sub" x="170" y="92" text-anchor="middle">system · ORG_SIGNUP</text>

      <rect class="chip-grn" x="40" y="130" width="260" height="60" rx="8"></rect>
      <text class="t-name" x="170" y="154" text-anchor="middle">Acme Okta</text>
      <text class="t-sub" x="170" y="172" text-anchor="middle">acme's own · ORGANIZATION</text>

      <rect class="chip-grn" x="40" y="210" width="260" height="60" rx="8"></rect>
      <text class="t-name" x="170" y="234" text-anchor="middle">Acme Okta, copied for orders</text>
      <text class="t-sub" x="170" y="252" text-anchor="middle">acme's own · APPLICATION · app = orders</text>

      <rect class="chip-ind" x="40" y="290" width="260" height="60" rx="8"></rect>
      <text class="t-name" x="170" y="314" text-anchor="middle">Google</text>
      <text class="t-sub" x="170" y="332" text-anchor="middle">system · APP_SOCIAL</text>

      <rect class="chip-amb" x="40" y="370" width="260" height="60" rx="8"></rect>
      <text class="t-name" x="170" y="394" text-anchor="middle">Globex Entra</text>
      <text class="t-sub" x="170" y="412" text-anchor="middle">acme's own · TENANT · orders / globex</text>

      <!-- logins -->
      <rect class="gw gw-org" x="800" y="50" width="300" height="100" rx="8"></rect>
      <text class="t-name" x="950" y="74" text-anchor="middle">acme's own login · /login/acme</text>
      <text class="t-sub" x="950" y="90" text-anchor="middle">management server</text>
      <line class="sep" x1="816" y1="100" x2="1084" y2="100"></line>
      <text class="t-lbl" x="950" y="120" text-anchor="middle">GitHub · Acme Okta · password</text>
      <text class="t-tiny" x="950" y="138" text-anchor="middle">Google and Microsoft: applications only</text>

      <rect class="gw gw-app" x="800" y="180" width="300" height="110" rx="8"></rect>
      <text class="t-name" x="950" y="204" text-anchor="middle">acme--orders login</text>
      <text class="t-sub" x="950" y="220" text-anchor="middle">app server · acme--orders.apps-api.kinotic.io</text>
      <line class="sep" x1="816" y1="230" x2="1084" y2="230"></line>
      <text class="t-lbl" x="950" y="250" text-anchor="middle">Acme Okta (copy) · Google · password</text>
      <text class="t-tiny" x="950" y="270" text-anchor="middle">Microsoft: no configuration, so not offered</text>

      <rect class="gw gw-app" x="800" y="330" width="300" height="110" rx="8"></rect>
      <text class="t-name" x="950" y="354" text-anchor="middle">globex tenant login</text>
      <text class="t-sub" x="950" y="370" text-anchor="middle">from globex--orders--web.acme.kinotic.io</text>
      <line class="sep" x1="816" y1="380" x2="1084" y2="380"></line>
      <text class="t-lbl" x="950" y="400" text-anchor="middle">Globex Entra · then the orders list</text>
      <text class="t-tiny" x="950" y="420" text-anchor="middle">admits the tenant's users only</text>

      <line class="link" x1="950" y1="290" x2="950" y2="330" marker-end="url(#si3-mut)"></line>
      <text class="t-tiny" x="960" y="314">the app's list follows</text>

      <!-- GitHub → org login, by right -->
      <line class="flow-ind" x1="300" y1="80" x2="800" y2="80" marker-end="url(#si3-ind)"></line>
      <text class="t-lbl-ind" x="550" y="72" text-anchor="middle">every ORG_SIGNUP registration · at every organization's login</text>

      <!-- Acme Okta → org login; refused at the app login -->
      <polyline class="flow-grn" points="300,150 520,150 520,120 800,120" marker-end="url(#si3-grn)"></polyline>
      <text class="t-lbl-grn" x="660" y="112" text-anchor="middle">kind ORGANIZATION → the organization's login</text>
      <polyline class="flow-red" points="300,175 440,175 440,215 800,215" marker-end="url(#si3-grn)"></polyline>
      <line class="dead" x1="428" y1="183" x2="452" y2="207"></line><line class="dead" x1="452" y1="183" x2="428" y2="207"></line>
      <text class="t-lbl-red" x="470" y="200">refused: an ORGANIZATION kind is not offered at an application · copy it</text>

      <!-- the copy → app login -->
      <line class="flow-grn" x1="300" y1="240" x2="800" y2="240" marker-end="url(#si3-grn)"></line>
      <text class="t-lbl-grn" x="550" y="232" text-anchor="middle">kind APPLICATION, app = orders → that application's login</text>

      <!-- Google → app login -->
      <polyline class="flow-ind" points="300,320 560,320 560,275 800,275" marker-end="url(#si3-ind)"></polyline>
      <text class="t-lbl-ind" x="680" y="267" text-anchor="middle">orders' Google configuration, MANAGED</text>

      <!-- Globex Entra → tenant login -->
      <line class="flow-amb" x1="300" y1="400" x2="800" y2="400" marker-end="url(#si3-amb)"></line>
      <text class="t-lbl-amb" x="550" y="392" text-anchor="middle">kind TENANT, orders / globex → that tenant's login, first</text>

      <text class="t-tiny" x="40" y="462">findOffered(configuration, org, app, tenant) answers this picture for one configuration at every sign-in start and callback, so a configuration not offered at a login signs no one in there.</text>
      <text class="t-tiny" x="40" y="478">findEnabledForScope(org, app, tenant) answers a login page's whole list, and the MCP tools build the page from the same answer.</text>
    </svg>

    <!-- ═══════════════════════════ PATHS: where the provider sends the browser back ═══════════════════════════ -->
    <svg v-else-if="figure === 'paths'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 600" role="img" aria-label="Two sequences over the same four columns: the application's UI, the application's host, the shared host, and the provider. Path A, the organization's own provider: the UI posts start and gets a redirect; the browser goes to the provider; the provider returns to the application's host; the host sets the session and sends the browser back to the page. Path B, a system social registration: the UI posts start and gets the shared host's authorize URL; the browser opens it; the shared host sends it to Google; Google returns to the shared host, the one registered redirect URI; the shared host stages the verified identity under a single-use code and sends the browser to the application's host with it; that host consumes the code, sets the session and sends the browser back to the page.">
      <defs>
        <marker id="si4-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-ink" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si4-grn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-grn" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si4-vio" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-vio" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si4-amb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-amb" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <rect class="chip" x="60" y="30" width="200" height="44" rx="8"></rect>
      <text class="t-name" x="160" y="49" text-anchor="middle">Application UI</text>
      <text class="t-tiny" x="160" y="65" text-anchor="middle">orders--web.acme.kinotic.io</text>
      <rect class="gw gw-app" x="340" y="30" width="240" height="44" rx="8"></rect>
      <text class="t-name" x="460" y="49" text-anchor="middle">Application host</text>
      <text class="t-tiny" x="460" y="65" text-anchor="middle">acme--orders.apps-api.kinotic.io</text>
      <rect class="gw gw-shared" x="660" y="30" width="240" height="44" rx="8"></rect>
      <text class="t-name" x="780" y="49" text-anchor="middle">Shared host</text>
      <text class="t-tiny" x="780" y="65" text-anchor="middle">apps-api.kinotic.io · same server</text>
      <rect class="chip-amb" x="980" y="30" width="160" height="44" rx="8"></rect>
      <text class="t-name" x="1060" y="49" text-anchor="middle">Provider</text>
      <text class="t-tiny" x="1060" y="65" text-anchor="middle">Okta · Google</text>

      <line class="life" x1="160" y1="74" x2="160" y2="580"></line>
      <line class="life" x1="460" y1="74" x2="460" y2="580"></line>
      <line class="life" x1="780" y1="74" x2="780" y2="580"></line>
      <line class="life" x1="1060" y1="74" x2="1060" y2="580"></line>

      <!-- Path A -->
      <text class="t-plane-grn" x="24" y="110">A · THE ORGANIZATION'S OWN PROVIDER</text>

      <circle class="step" cx="36" cy="140" r="11"></circle><text class="t-step" x="36" y="144" text-anchor="middle">1</text>
      <line class="flow" x1="160" y1="140" x2="460" y2="140" marker-end="url(#si4-ink)"></line>
      <text class="t-lbl" x="310" y="132" text-anchor="middle">start, for the Okta copy</text>
      <line class="flow-grn" x1="460" y1="162" x2="160" y2="162" marker-end="url(#si4-grn)"></line>
      <text class="t-lbl-grn" x="310" y="178" text-anchor="middle">the provider's authorize URL</text>

      <circle class="step" cx="36" cy="206" r="11"></circle><text class="t-step" x="36" y="210" text-anchor="middle">2</text>
      <line class="flow-amb" x1="160" y1="206" x2="1060" y2="206" marker-end="url(#si4-amb)"></line>
      <text class="t-lbl" x="610" y="198" text-anchor="middle">the user signs in at Okta</text>

      <circle class="step" cx="36" cy="240" r="11"></circle><text class="t-step" x="36" y="244" text-anchor="middle">3</text>
      <line class="flow-amb" x1="1060" y1="240" x2="460" y2="240" marker-end="url(#si4-amb)"></line>
      <text class="t-lbl" x="760" y="232" text-anchor="middle">callback, to the application's host</text>

      <circle class="step" cx="36" cy="274" r="11"></circle><text class="t-step" x="36" y="278" text-anchor="middle">4</text>
      <line class="flow-grn" x1="460" y1="274" x2="160" y2="274" marker-end="url(#si4-grn)"></line>
      <text class="t-lbl-grn" x="310" y="266" text-anchor="middle">the binding's user · session cookie · back to the page</text>

      <line class="sep" x1="24" y1="304" x2="1156" y2="304"></line>

      <!-- Path B -->
      <text class="t-plane-vio" x="24" y="332">B · A SYSTEM SOCIAL REGISTRATION</text>

      <circle class="step" cx="36" cy="362" r="11"></circle><text class="t-step" x="36" y="366" text-anchor="middle">1</text>
      <line class="flow" x1="160" y1="362" x2="460" y2="362" marker-end="url(#si4-ink)"></line>
      <text class="t-lbl" x="310" y="354" text-anchor="middle">start, for Google</text>
      <line class="flow-grn" x1="460" y1="384" x2="160" y2="384" marker-end="url(#si4-grn)"></line>
      <text class="t-lbl-grn" x="310" y="400" text-anchor="middle">the shared host's authorize URL</text>

      <circle class="step" cx="36" cy="428" r="11"></circle><text class="t-step" x="36" y="432" text-anchor="middle">2</text>
      <line class="flow" x1="160" y1="428" x2="780" y2="428" marker-end="url(#si4-ink)"></line>
      <text class="t-lbl" x="470" y="420" text-anchor="middle">authorize: which app, which tenant, which page</text>
      <line class="flow-amb" x1="780" y1="450" x2="1060" y2="450" marker-end="url(#si4-amb)"></line>
      <text class="t-lbl" x="920" y="442" text-anchor="middle">the user signs in at Google</text>

      <circle class="step" cx="36" cy="484" r="11"></circle><text class="t-step" x="36" y="488" text-anchor="middle">3</text>
      <line class="flow-amb" x1="1060" y1="484" x2="780" y2="484" marker-end="url(#si4-amb)"></line>
      <text class="t-lbl" x="920" y="476" text-anchor="middle">callback, to the shared host</text>
      <text class="t-tiny" x="920" y="500" text-anchor="middle">the one URI registered with Google</text>

      <circle class="step" cx="36" cy="524" r="11"></circle><text class="t-step" x="36" y="528" text-anchor="middle">4</text>
      <line class="flow-vio" x1="780" y1="524" x2="460" y2="524" marker-end="url(#si4-vio)"></line>
      <text class="t-lbl-vio" x="620" y="516" text-anchor="middle">hand-off: a single-use code, 1 minute</text>
      <text class="t-tiny" x="620" y="540" text-anchor="middle">the identity waits in an Ignite cache; the code names the application and tenant</text>

      <circle class="step" cx="36" cy="566" r="11"></circle><text class="t-step" x="36" y="570" text-anchor="middle">5</text>
      <line class="flow-grn" x1="460" y1="566" x2="160" y2="566" marker-end="url(#si4-grn)"></line>
      <text class="t-lbl-grn" x="310" y="558" text-anchor="middle">the binding's user · session cookie · back to the page</text>
    </svg>

    <!-- ═══════════════════════════ IDENTITY: the identity and participant classes ═══════════════════════════ -->
    <svg v-else-if="figure === 'identity'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1180 884" role="img" aria-label="UML class diagram. ParticipantIdentity, sealed and abstract, holds id, displayName, enabled and timestamps, and getType returns the ParticipantIdentityType discriminator. OrganizationParticipantIdentity, ApplicationParticipantIdentity, DelegatingParticipantIdentity and MachineParticipantIdentity implement the sealed ScopedParticipantIdentity interface: an organization user carries organizationId, the others organizationId and applicationId, and an application user and a delegate a tenantId too. Three subclasses: UserParticipantIdentity, sealed and abstract, with email; DelegatingParticipantIdentity with ownerId, clientKey and delegateType; and MachineParticipantIdentity with machineType. A delegate's ownerId points at a user. UserParticipantIdentity has three final subclasses, one per scope: SystemParticipantIdentity, OrganizationParticipantIdentity and ApplicationParticipantIdentity. Every user has zero or one IdentityCredential, and organization and application users zero or more OidcBinding rows, each id naming the user's organization, and application for an application user. Below, the sealed ScopedParticipant interface extending the core Participant, with SystemParticipant, OrganizationParticipant and ApplicationParticipant, and the arrow from an identity in its own scope to its participant through resolveParticipant.">
      <defs>
        <marker id="si5-gen" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto"><polygon class="mk-gen" points="1,1 11,6 1,11"></polygon></marker>
        <marker id="si5-mut" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><polygon class="mk-mut" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si5-ind" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><polygon class="mk-ind" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <!-- root -->
      <rect class="cls-abs" x="440" y="30" width="300" height="196" rx="4"></rect>
      <text class="t-ster" x="590" y="48" text-anchor="middle">«sealed, abstract»</text>
      <text class="t-name" x="590" y="64" text-anchor="middle">ParticipantIdentity</text>
      <line class="sep" x1="440" y1="72" x2="740" y2="72"></line>
      <text class="t-attr" x="452" y="90">id: String · displayName: String</text>
      <text class="t-attr" x="452" y="106">enabled: boolean · created, updated: Date</text>
      <line class="sep" x1="440" y1="116" x2="740" y2="116"></line>
      <text class="t-attr" x="452" y="134">getType(): ParticipantIdentityType</text>
      <text class="t-attr-mut" x="452" y="166">a «ScopedParticipantIdentity» carries its</text>
      <text class="t-attr-mut" x="452" y="182">scope: none → system, org → organization,</text>
      <text class="t-attr-mut" x="452" y="198">org + app → application; scope() returns it</text>

      <text class="t-attr-mut" x="760" y="100">the sign-in methods are rows of</text>
      <text class="t-attr-mut" x="760" y="116">their own: a credential, bindings;</text>
      <text class="t-attr-mut" x="760" y="132">AuthType {PASSWORD, OIDC} names</text>
      <text class="t-attr-mut" x="760" y="148">one on the Profile page, never the row</text>

      <!-- subclasses -->
      <rect class="cls-abs" x="60" y="290" width="300" height="122" rx="4"></rect>
      <text class="t-ster" x="210" y="308" text-anchor="middle">«sealed, abstract»</text>
      <text class="t-name" x="210" y="324" text-anchor="middle">UserParticipantIdentity</text>
      <line class="sep" x1="60" y1="332" x2="360" y2="332"></line>
      <text class="t-attr" x="72" y="350">email: String</text>
      <line class="sep" x1="60" y1="358" x2="360" y2="358"></line>
      <text class="t-attr-mut" x="72" y="374">a person; the concrete type is the</text>
      <text class="t-attr-mut" x="72" y="390">scope it signs in to, unique by</text>
      <text class="t-attr-mut" x="72" y="406">email within that scope</text>

      <rect class="cls" x="420" y="290" width="340" height="122" rx="4"></rect>
      <text class="t-ster" x="590" y="308" text-anchor="middle">«final»</text>
      <text class="t-name" x="590" y="324" text-anchor="middle">DelegatingParticipantIdentity</text>
      <line class="sep" x1="420" y1="332" x2="760" y2="332"></line>
      <text class="t-attr" x="432" y="350">ownerId: String · clientKey: String</text>
      <text class="t-attr" x="432" y="366">delegateType: DelegateType</text>
      <text class="t-attr" x="432" y="382">  {CLI, MCP_CLIENT}</text>
      <text class="t-attr" x="432" y="398">organizationId, applicationId, tenantId</text>

      <rect class="cls" x="800" y="290" width="340" height="122" rx="4"></rect>
      <text class="t-ster" x="970" y="308" text-anchor="middle">«final»</text>
      <text class="t-name" x="970" y="324" text-anchor="middle">MachineParticipantIdentity</text>
      <line class="sep" x1="800" y1="332" x2="1140" y2="332"></line>
      <text class="t-attr" x="812" y="350">machineType: MachineType</text>
      <text class="t-attr" x="812" y="366">  {CLIENT, PROJECT_SYNC, APP_RUNTIME}</text>
      <text class="t-attr" x="812" y="382">organizationId, applicationId</text>
      <text class="t-attr-mut" x="812" y="402">credential: id + provisioned secret</text>

      <polyline class="gen" points="210,290 210,258 590,258 590,226" marker-end="url(#si5-gen)"></polyline>
      <polyline class="gen" points="590,290 590,258"></polyline>
      <polyline class="gen" points="970,290 970,258 590,258"></polyline>

      <line class="link" x1="420" y1="344" x2="360" y2="344" marker-end="url(#si5-mut)"></line>
      <text class="t-tiny" x="390" y="338" text-anchor="middle">ownerId</text>

      <!-- one kind of user per scope -->
      <rect class="cls" x="40" y="460" width="340" height="92" rx="4"></rect>
      <text class="t-ster" x="210" y="478" text-anchor="middle">«final»</text>
      <text class="t-name" x="210" y="494" text-anchor="middle">SystemParticipantIdentity</text>
      <line class="sep" x1="40" y1="502" x2="380" y2="502"></line>
      <text class="t-attr" x="52" y="520">SYSTEM_USER</text>
      <text class="t-attr-mut" x="52" y="538">the system console, password only</text>

      <rect class="cls" x="420" y="460" width="340" height="92" rx="4"></rect>
      <text class="t-ster" x="590" y="478" text-anchor="middle">«final»</text>
      <text class="t-name" x="590" y="494" text-anchor="middle">OrganizationParticipantIdentity</text>
      <line class="sep" x1="420" y1="502" x2="760" y2="502"></line>
      <text class="t-attr" x="432" y="520">ORGANIZATION_USER · organizationId</text>
      <text class="t-attr-mut" x="432" y="538">acts in its organization only</text>

      <rect class="cls" x="800" y="460" width="340" height="92" rx="4"></rect>
      <text class="t-ster" x="970" y="478" text-anchor="middle">«final»</text>
      <text class="t-name" x="970" y="494" text-anchor="middle">ApplicationParticipantIdentity</text>
      <line class="sep" x1="800" y1="502" x2="1140" y2="502"></line>
      <text class="t-attr" x="812" y="520">APPLICATION_USER · organizationId,</text>
      <text class="t-attr" x="812" y="536">applicationId, tenantId</text>
      <text class="t-attr-mut" x="812" y="548">acts in its application only</text>

      <polyline class="gen" points="210,460 210,436 210,412" marker-end="url(#si5-gen)"></polyline>
      <polyline class="gen" points="590,460 590,436 210,436"></polyline>
      <polyline class="gen" points="970,460 970,436 590,436"></polyline>

      <!-- rows that point at a user -->
      <rect class="cls" x="40" y="590" width="200" height="76" rx="4"></rect>
      <text class="t-name" x="140" y="610" text-anchor="middle">IdentityCredential</text>
      <line class="sep" x1="40" y1="618" x2="240" y2="618"></line>
      <text class="t-attr" x="52" y="636">id = identity id</text>
      <text class="t-attr" x="52" y="652">secretHash: String</text>

      <rect class="cls" x="260" y="590" width="270" height="124" rx="4"></rect>
      <text class="t-name" x="395" y="610" text-anchor="middle">OidcBinding</text>
      <line class="sep" x1="260" y1="618" x2="530" y2="618"></line>
      <text class="t-attr" x="272" y="636">id = org/oidcConfigId:subject</text>
      <text class="t-attr-mut" x="272" y="652">  app user: org/app/config:subject</text>
      <text class="t-attr" x="272" y="668">identityId: String</text>
      <text class="t-attr" x="272" y="684">organizationId, applicationId</text>
      <text class="t-attr" x="272" y="700">oidcConfigId, subject · created</text>

      <line class="link" x1="140" y1="552" x2="140" y2="590" marker-start="url(#si5-mut)"></line>
      <text class="t-tiny" x="148" y="576">0..1 password · every user</text>
      <polyline class="link" points="470,552 470,570 395,570 395,590" marker-start="url(#si5-mut)"></polyline>
      <text class="t-tiny" x="300" y="584">0..* bindings</text>

      <text class="t-attr-mut" x="884" y="610">every user may hold a credential;</text>
      <text class="t-attr-mut" x="884" y="626">organization and application users</text>
      <text class="t-attr-mut" x="884" y="642">hold bindings, each in the user's</text>
      <text class="t-attr-mut" x="884" y="658">own scope. deleting an identity</text>
      <text class="t-attr-mut" x="884" y="674">cascades to both and to its</text>
      <text class="t-attr-mut" x="884" y="690">delegates.</text>

      <!-- participants -->
      <rect class="wall" x="40" y="750" width="1100" height="118" rx="8"></rect>
      <text class="t-tag" x="56" y="770">«SEALED» ScopedParticipant extends Participant (kinotic-core)</text>
      <text class="t-tiny" x="56" y="786">getId() · getRoles() · getScope(): ParticipantScope(organizationId, applicationId, tenantId)</text>

      <rect class="chip-ind" x="56" y="800" width="336" height="52" rx="6"></rect>
      <text class="t-name" x="224" y="820" text-anchor="middle">SystemParticipant</text>
      <text class="t-tiny" x="224" y="838" text-anchor="middle">a system user · system machine</text>

      <rect class="chip-ind" x="404" y="800" width="356" height="52" rx="6"></rect>
      <text class="t-name" x="582" y="820" text-anchor="middle">OrganizationParticipant · getOrganizationId()</text>
      <text class="t-tiny" x="582" y="838" text-anchor="middle">an organization user · org delegate</text>

      <rect class="chip-ind" x="772" y="800" width="356" height="52" rx="6"></rect>
      <text class="t-name" x="950" y="820" text-anchor="middle">ApplicationParticipant · org, app, tenant ids</text>
      <text class="t-tiny" x="950" y="838" text-anchor="middle">an application user · application machine</text>

      <line class="flow-ind" x1="710" y1="552" x2="710" y2="750" marker-end="url(#si5-ind)"></line>
      <text class="t-lbl-ind" x="722" y="736">resolveParticipant(identityId, orgId, appId) → the identity in its own scope</text>
    </svg>

    <!-- ═══════════════════════════ ORGANIZATION USER: one user per organization ═══════════════════════════ -->
    <svg v-else-if="figure === 'organization-user'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1140 596" role="img" aria-label="Two rows, one per organization Jane belongs to. In acme, a password and a GitHub binding sign in Jane's acme user, which carries organizationId acme and belongs to acme alone. In globex, the same GitHub account bound again and a Globex Okta binding sign in Jane's separate globex user, which carries organizationId globex. Below, three logins: the password login at acme authenticates acme's user and resolves its participant; the GitHub button at globex finds globex's user by the binding in globex, refusing a disabled user with user_disabled and an identity bound to no globex user with no_user; the landing page's GitHub button finds the users the identity is bound to in any organization, signing in the one there is and offering the organizations to choose from when there are several. At the bottom, separate rows for Jane as a system user and as a user of the orders application, each with its own methods, and a red note that a provider's email never selects a user.">
      <defs>
        <marker id="si6-grn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-grn" points="0,0 10,5 0,10"></polygon></marker>
        <marker id="si6-amb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-amb" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <text class="t-tag" x="40" y="30">SIGN-IN METHODS · BOUND IN ONE ORGANIZATION</text>
      <text class="t-tag" x="570" y="30" text-anchor="middle">ONE USER PER ORGANIZATION</text>
      <text class="t-tag" x="1100" y="30" text-anchor="end">WHERE IT SIGNS IN</text>

      <!-- acme: methods -->
      <rect class="chip-amb" x="40" y="50" width="270" height="44" rx="8"></rect>
      <text class="t-chip" x="175" y="68" text-anchor="middle">password</text>
      <text class="t-tiny" x="175" y="84" text-anchor="middle">IdentityCredential u-acme-jane · bcrypt hash</text>

      <rect class="chip-amb" x="40" y="104" width="270" height="44" rx="8"></rect>
      <text class="t-chip" x="175" y="122" text-anchor="middle">GitHub · OidcBinding</text>
      <text class="t-tiny" x="175" y="138" text-anchor="middle">acme/github-platform:7781 → u-acme-jane</text>

      <!-- acme: user -->
      <rect class="chip-ind" x="420" y="50" width="300" height="98" rx="8"></rect>
      <text class="t-name" x="570" y="72" text-anchor="middle">Jane at acme</text>
      <text class="t-sub" x="570" y="89" text-anchor="middle">OrganizationParticipantIdentity u-acme-jane</text>
      <line class="sep" x1="436" y1="99" x2="704" y2="99"></line>
      <text class="t-mono-ink" x="570" y="118" text-anchor="middle">organizationId: acme · email: jane@acme.com</text>
      <text class="t-mono-ink" x="570" y="136" text-anchor="middle">enabled: true</text>

      <line class="flow-amb" x1="310" y1="72" x2="420" y2="88" marker-end="url(#si6-amb)"></line>
      <line class="flow-amb" x1="310" y1="126" x2="420" y2="110" marker-end="url(#si6-amb)"></line>

      <!-- acme: organization -->
      <rect class="chip-grn" x="830" y="50" width="270" height="98" rx="8"></rect>
      <text class="t-chip" x="965" y="74" text-anchor="middle">acme</text>
      <text class="t-tiny" x="965" y="94" text-anchor="middle">signs in at /login/acme only</text>
      <text class="t-tiny" x="965" y="110" text-anchor="middle">removing Jane deletes u-acme-jane</text>
      <text class="t-tiny" x="965" y="126" text-anchor="middle">and leaves her globex user as it is</text>

      <line class="flow-grn" x1="720" y1="99" x2="830" y2="99" marker-end="url(#si6-grn)"></line>
      <text class="t-lbl-grn" x="775" y="91" text-anchor="middle">member of</text>

      <text class="t-lbl-amb" x="175" y="163" text-anchor="middle">the same GitHub account, bound in each</text>

      <!-- globex: methods -->
      <rect class="chip-amb" x="40" y="170" width="270" height="44" rx="8"></rect>
      <text class="t-chip" x="175" y="188" text-anchor="middle">GitHub · OidcBinding</text>
      <text class="t-tiny" x="175" y="204" text-anchor="middle">globex/github-platform:7781 → u-globex-jane</text>

      <rect class="chip-amb" x="40" y="224" width="270" height="44" rx="8"></rect>
      <text class="t-chip" x="175" y="242" text-anchor="middle">Globex Okta · OidcBinding</text>
      <text class="t-tiny" x="175" y="258" text-anchor="middle">globex/globex-okta:jane.d → u-globex-jane</text>

      <!-- globex: user -->
      <rect class="chip-ind" x="420" y="170" width="300" height="98" rx="8"></rect>
      <text class="t-name" x="570" y="192" text-anchor="middle">Jane at globex</text>
      <text class="t-sub" x="570" y="209" text-anchor="middle">OrganizationParticipantIdentity u-globex-jane</text>
      <line class="sep" x1="436" y1="219" x2="704" y2="219"></line>
      <text class="t-mono-ink" x="570" y="238" text-anchor="middle">organizationId: globex · email: jane@acme.com</text>
      <text class="t-mono-ink" x="570" y="256" text-anchor="middle">enabled: true · no password</text>

      <line class="flow-amb" x1="310" y1="192" x2="420" y2="208" marker-end="url(#si6-amb)"></line>
      <line class="flow-amb" x1="310" y1="246" x2="420" y2="230" marker-end="url(#si6-amb)"></line>

      <!-- globex: organization -->
      <rect class="chip-grn" x="830" y="170" width="270" height="98" rx="8"></rect>
      <text class="t-chip" x="965" y="194" text-anchor="middle">globex</text>
      <text class="t-tiny" x="965" y="214" text-anchor="middle">signs in at /login/globex only</text>
      <text class="t-tiny" x="965" y="230" text-anchor="middle">disabling Jane here leaves</text>
      <text class="t-tiny" x="965" y="246" text-anchor="middle">her acme user as it is</text>

      <line class="flow-grn" x1="720" y1="219" x2="830" y2="219" marker-end="url(#si6-grn)"></line>
      <text class="t-lbl-grn" x="775" y="211" text-anchor="middle">member of</text>

      <!-- logins -->
      <line class="sep" x1="40" y1="290" x2="1100" y2="290"></line>
      <text class="t-tag" x="40" y="314">A LOGIN IS ADDRESSED TO ONE ORGANIZATION · resolveParticipant(user, organizationId, null)</text>

      <rect class="gw gw-org" x="40" y="330" width="340" height="118" rx="8"></rect>
      <text class="t-name" x="210" y="354" text-anchor="middle">/login/acme · email + password</text>
      <line class="sep" x1="56" y1="364" x2="364" y2="364"></line>
      <text class="t-mono-ink" x="56" y="384">authenticateOrganizationUser(email, pw, acme)</text>
      <text class="t-mono-ink" x="56" y="402">  → u-acme-jane, enabled, password verified</text>
      <text class="t-mono-ink" x="56" y="420">resolveParticipant(u-acme-jane, acme, null)</text>
      <text class="t-lbl-ind" x="56" y="438">→ OrganizationParticipant { sub u-acme-jane }</text>

      <rect class="gw gw-org" x="400" y="330" width="340" height="118" rx="8"></rect>
      <text class="t-name" x="570" y="354" text-anchor="middle">/login/globex · GitHub</text>
      <line class="sep" x1="416" y1="364" x2="724" y2="364"></line>
      <text class="t-mono-ink" x="416" y="384">binding globex/github-platform:7781</text>
      <text class="t-mono-ink" x="416" y="402">  → u-globex-jane · disabled → user_disabled</text>
      <text class="t-mono-ink" x="416" y="420">  bound to no globex user → no_user</text>
      <text class="t-lbl-ind" x="416" y="438">→ OrganizationParticipant { sub u-globex-jane }</text>

      <rect class="gw gw-org" x="760" y="330" width="340" height="118" rx="8"></rect>
      <text class="t-name" x="930" y="354" text-anchor="middle">/login · the landing page's GitHub button</text>
      <line class="sep" x1="776" y1="364" x2="1084" y2="364"></line>
      <text class="t-mono-ink" x="776" y="384">bindings of github-platform:7781 in any org</text>
      <text class="t-mono-ink" x="776" y="402">  → u-acme-jane, u-globex-jane</text>
      <text class="t-mono-ink" x="776" y="420">  one → sign in there · none → no_user</text>
      <text class="t-lbl-ind" x="776" y="438">→ two: offer both, sign in at the chosen one's page</text>

      <!-- the other scopes' users -->
      <rect class="chip-vio" x="40" y="468" width="1060" height="54" rx="8"></rect>
      <text class="t-chip" x="56" y="490">Jane as a system user, or as a user of acme--orders, is another row each: SystemParticipantIdentity · ApplicationParticipantIdentity acme/orders</text>
      <text class="t-tiny" x="56" y="508">each with its own password; one signs in at the system console only, the other at acme--orders.apps-api.kinotic.io only, and her organization users grant nothing at either</text>

      <!-- the rule -->
      <rect class="note-red" x="40" y="536" width="1060" height="44" rx="8"></rect>
      <text class="t-lbl-red" x="56" y="556">A provider's email never selects a user. Only a binding of (configId, sub) in the user's own scope does.</text>
      <text class="t-tiny" x="56" y="572">read once, when the binding is created: sign-up, an invitation, the org's provider claiming a pending member, createUserOnSignIn, or a connect from the Profile page</text>
    </svg>

    <!-- ═══════════════════════════ DOORS: the management server ═══════════════════════════ -->
    <svg v-else-if="figure === 'doors'" class="sign-in-diagram" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1140 400" role="img" aria-label="Four doors on the management server. Sign up with GitHub always produces a new organization and the founder as its first user, bound to the GitHub identity. Log in has a landing page with a GitHub button, which signs in the user the identity is bound to or offers the organizations of its users, a field that asks for the organization and sends the person to its page, and a link that emails the person their organizations, and an organization page at /login/<org> with GitHub, the organization's own SSO and a password form. Invited or added: an administrator adds a member from the Members page, which creates a pending user of the organization until the organization's own provider or an invitation claims it; an invitation lets the invitee set a password or use a provider the organization offers, which claims the pending member or creates a user; a member who already signs in is refused and signs in instead; an application invitee becomes a user of that application. Profile, once signed in, connects GitHub or the organization's SSO, sets or changes the password, and removes a method but never the last one that signs in; a session acts in one organization, and another is a sign-in at its login.">
      <defs>
        <marker id="si7-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><polygon class="mk-ink" points="0,0 10,5 0,10"></polygon></marker>
      </defs>

      <text class="t-tag" x="24" y="26">MANAGEMENT SERVER · THE PORTAL'S API</text>

      <!-- sign up -->
      <rect class="gw gw-org" x="40" y="50" width="250" height="300" rx="8"></rect>
      <text class="t-name" x="165" y="74" text-anchor="middle">Sign up</text>
      <line class="sep" x1="56" y1="84" x2="274" y2="84"></line>
      <rect class="chip-ind" x="56" y="100" width="218" height="40" rx="8"></rect>
      <text class="t-chip" x="165" y="124" text-anchor="middle">Continue with GitHub</text>
      <line class="flow" x1="165" y1="140" x2="165" y2="176" marker-end="url(#si7-ink)"></line>
      <rect class="chip" x="56" y="176" width="218" height="60" rx="8"></rect>
      <text class="t-chip" x="165" y="196" text-anchor="middle">new organization</text>
      <text class="t-chip" x="165" y="212" text-anchor="middle">+ founder, its first user</text>
      <text class="t-tiny" x="165" y="228" text-anchor="middle">bound to GitHub in that organization</text>
      <rect class="chip-ghost" x="56" y="246" width="218" height="42" rx="8"></rect>
      <text class="t-tiny" x="165" y="263" text-anchor="middle">every sign-up founds a new</text>
      <text class="t-tiny" x="165" y="277" text-anchor="middle">organization and its first user</text>

      <!-- log in -->
      <rect class="gw gw-org" x="310" y="50" width="290" height="300" rx="8"></rect>
      <text class="t-name" x="455" y="74" text-anchor="middle">Log in</text>
      <line class="sep" x1="326" y1="84" x2="584" y2="84"></line>
      <text class="t-tiny" x="326" y="102">/login · the landing page</text>
      <rect class="chip-ind" x="326" y="110" width="258" height="30" rx="8"></rect>
      <text class="t-chip" x="455" y="130" text-anchor="middle">GitHub → its users' organizations decide</text>
      <rect class="chip" x="326" y="146" width="258" height="30" rx="8"></rect>
      <text class="t-chip" x="455" y="166" text-anchor="middle">organization → /login/&lt;org&gt;</text>
      <rect class="chip" x="326" y="182" width="258" height="30" rx="8"></rect>
      <text class="t-chip" x="455" y="202" text-anchor="middle">Don't know your organization? → email</text>
      <text class="t-tiny" x="326" y="232">/login/acme · the organization's page</text>
      <rect class="chip" x="326" y="240" width="258" height="48" rx="8"></rect>
      <text class="t-chip" x="455" y="259" text-anchor="middle">GitHub · Acme Okta · email + password</text>
      <text class="t-tiny" x="455" y="276" text-anchor="middle">what its kind offers · SSO start is GET-able</text>

      <!-- invited or added -->
      <rect class="gw gw-org" x="620" y="50" width="250" height="300" rx="8"></rect>
      <text class="t-name" x="745" y="74" text-anchor="middle">Invited or added</text>
      <line class="sep" x1="636" y1="84" x2="854" y2="84"></line>
      <rect class="chip" x="636" y="100" width="218" height="50" rx="8"></rect>
      <text class="t-chip" x="745" y="118" text-anchor="middle">added on the Members page:</text>
      <text class="t-chip" x="745" y="134" text-anchor="middle">a pending user of the org</text>
      <text class="t-tiny" x="745" y="146" text-anchor="middle">its provider claims it on first sign-in</text>
      <rect class="chip" x="636" y="158" width="218" height="50" rx="8"></rect>
      <text class="t-chip" x="745" y="176" text-anchor="middle">invited: set a password or use</text>
      <text class="t-chip" x="745" y="192" text-anchor="middle">a provider the org offers</text>
      <text class="t-tiny" x="745" y="204" text-anchor="middle">claims the user, or creates one</text>
      <rect class="note-amb" x="636" y="216" width="218" height="50" rx="8"></rect>
      <text class="t-chip" x="745" y="234" text-anchor="middle">a member who already signs in:</text>
      <text class="t-chip" x="745" y="250" text-anchor="middle">refused · sign in instead</text>
      <text class="t-tiny" x="745" y="262" text-anchor="middle">409 · user_exists</text>
      <rect class="chip-vio" x="636" y="274" width="218" height="50" rx="8"></rect>
      <text class="t-chip" x="745" y="292" text-anchor="middle">invited into an application:</text>
      <text class="t-chip" x="745" y="308" text-anchor="middle">a user of that application</text>
      <text class="t-tiny" x="745" y="320" text-anchor="middle">signs in at the application's host</text>

      <!-- profile -->
      <rect class="gw gw-org" x="890" y="50" width="210" height="300" rx="8"></rect>
      <text class="t-name" x="995" y="74" text-anchor="middle">Profile · signed in</text>
      <line class="sep" x1="906" y1="84" x2="1084" y2="84"></line>
      <text class="t-tiny" x="906" y="102">Sign-in methods</text>
      <rect class="chip-amb" x="906" y="110" width="178" height="30" rx="8"></rect>
      <text class="t-chip" x="995" y="130" text-anchor="middle">Connect GitHub · org SSO</text>
      <rect class="chip-amb" x="906" y="146" width="178" height="30" rx="8"></rect>
      <text class="t-chip" x="995" y="166" text-anchor="middle">Set / change password</text>
      <rect class="chip-amb" x="906" y="182" width="178" height="30" rx="8"></rect>
      <text class="t-chip" x="995" y="202" text-anchor="middle">Remove, never the last</text>
      <rect class="chip-ghost" x="906" y="228" width="178" height="60" rx="8"></rect>
      <text class="t-tiny" x="995" y="248" text-anchor="middle">a session acts in one</text>
      <text class="t-tiny" x="995" y="262" text-anchor="middle">organization · another is a</text>
      <text class="t-tiny" x="995" y="276" text-anchor="middle">sign-in at /login/&lt;org&gt;</text>

      <text class="t-tiny" x="24" y="380">Every callback is checked against its flow session; every configuration passes findOffered (which login offers it); every organization user passes resolveParticipant (a user acts in its own organization only).</text>
    </svg>

  </div>
  </DiagramFrame>
</template>

<style>
/* Palette follows the site color-mode class, so the diagrams track the theme toggle. */
svg.sign-in-diagram {
  --surface: #FFFFFF;
  --ink: #1A2332;
  --muted: #5C6879;
  --line: #D8DDE5;
  --indigo: #4A5FD0;
  --indigo-tint: rgba(74, 95, 208, 0.10);
  --green: #2C8F5E;
  --green-tint: rgba(44, 143, 94, 0.10);
  --violet: #7C4FD0;
  --violet-tint: rgba(124, 79, 208, 0.10);
  --amber: #A97C22;
  --amber-tint: rgba(169, 124, 34, 0.10);
  --red: #C64A4A;
  --red-tint: rgba(198, 74, 74, 0.10);
  --host-tint: rgba(100, 116, 139, 0.07);
}
.dark svg.sign-in-diagram {
  --surface: #151D2D;
  --ink: #E6EBF4;
  --muted: #96A2B6;
  --line: #2B3648;
  --indigo: #7E8EF0;
  --indigo-tint: rgba(126, 142, 240, 0.14);
  --green: #48B482;
  --green-tint: rgba(72, 180, 130, 0.13);
  --violet: #A47EF0;
  --violet-tint: rgba(164, 126, 240, 0.14);
  --amber: #D3A24C;
  --amber-tint: rgba(211, 162, 76, 0.13);
  --red: #E06A6A;
  --red-tint: rgba(224, 106, 106, 0.14);
  --host-tint: rgba(148, 163, 184, 0.08);
}

.sign-in-diagram-wrap { overflow-x: auto; }
svg.sign-in-diagram { min-width: 860px; width: 100%; height: auto; display: block; }

/* ── SVG vocabulary (shared by every figure) ─────────────────────────── */
svg.sign-in-diagram text { font-family: "Avenir Next", "Segoe UI", system-ui, sans-serif; }
svg.sign-in-diagram .wall       { fill: var(--host-tint);   stroke: var(--line);   stroke-width: 1.5; }
svg.sign-in-diagram .chip       { fill: var(--surface);     stroke: var(--line);   stroke-width: 1.25; }
svg.sign-in-diagram .chip-ind   { fill: var(--surface);     stroke: var(--indigo); stroke-width: 1.5; }
svg.sign-in-diagram .chip-grn   { fill: var(--surface);     stroke: var(--green);  stroke-width: 1.5; }
svg.sign-in-diagram .chip-vio   { fill: var(--surface);     stroke: var(--violet); stroke-width: 1.5; }
svg.sign-in-diagram .chip-amb   { fill: var(--surface);     stroke: var(--amber);  stroke-width: 1.5; }
svg.sign-in-diagram .chip-ghost { fill: none; stroke: var(--line); stroke-width: 1.25; stroke-dasharray: 5 4; }
svg.sign-in-diagram .gw         { fill: var(--surface);     stroke-width: 2; }
svg.sign-in-diagram .gw-app     { stroke: var(--green); }
svg.sign-in-diagram .gw-org     { stroke: var(--indigo); }
svg.sign-in-diagram .gw-shared  { stroke: var(--violet); }
svg.sign-in-diagram .note-red   { fill: var(--red-tint);    stroke: var(--red);    stroke-width: 1.25; }
svg.sign-in-diagram .note-amb   { fill: var(--amber-tint);  stroke: var(--amber);  stroke-width: 1.25; }
svg.sign-in-diagram .step       { fill: var(--surface);     stroke: var(--ink);    stroke-width: 1.25; }
svg.sign-in-diagram .sep        { stroke: var(--line); stroke-width: 1; }
svg.sign-in-diagram .life       { stroke: var(--line); stroke-width: 1.25; stroke-dasharray: 4 4; }
svg.sign-in-diagram .dead       { stroke: var(--red); stroke-width: 3; stroke-linecap: round; }
svg.sign-in-diagram .gen        { stroke: var(--ink); stroke-width: 1.4; fill: none; }
svg.sign-in-diagram .mk-gen     { fill: var(--surface); stroke: var(--ink); stroke-width: 1.2; }
svg.sign-in-diagram .cls        { fill: var(--surface);   stroke: var(--ink);   stroke-width: 1.4; }
svg.sign-in-diagram .cls-abs    { fill: var(--host-tint); stroke: var(--ink);   stroke-width: 1.4; }
svg.sign-in-diagram .cls-enum   { fill: var(--surface);   stroke: var(--muted); stroke-width: 1.2; stroke-dasharray: 3 3; }

svg.sign-in-diagram .flow       { stroke: var(--ink);    stroke-width: 1.6; fill: none; }
svg.sign-in-diagram .flow-ind   { stroke: var(--indigo); stroke-width: 1.8; fill: none; }
svg.sign-in-diagram .flow-grn   { stroke: var(--green);  stroke-width: 1.8; fill: none; }
svg.sign-in-diagram .flow-vio   { stroke: var(--violet); stroke-width: 1.8; fill: none; }
svg.sign-in-diagram .flow-amb   { stroke: var(--amber);  stroke-width: 1.8; fill: none; }
svg.sign-in-diagram .flow-red   { stroke: var(--red);    stroke-width: 1.8; fill: none; stroke-dasharray: 6 4; }
svg.sign-in-diagram .link       { stroke: var(--muted);  stroke-width: 1.4; fill: none; }

svg.sign-in-diagram .mk-ink { fill: var(--ink); }
svg.sign-in-diagram .mk-ind { fill: var(--indigo); }
svg.sign-in-diagram .mk-grn { fill: var(--green); }
svg.sign-in-diagram .mk-vio { fill: var(--violet); }
svg.sign-in-diagram .mk-amb { fill: var(--amber); }
svg.sign-in-diagram .mk-mut { fill: var(--muted); }

svg.sign-in-diagram .t-name     { font-size: 13px; font-weight: 600; fill: var(--ink); }
svg.sign-in-diagram .t-chip     { font-size: 12px; font-weight: 500; fill: var(--ink); }
svg.sign-in-diagram .t-sub      { font-size: 11px; fill: var(--muted); }
svg.sign-in-diagram .t-lbl      { font-size: 11px; fill: var(--ink); }
svg.sign-in-diagram .t-lbl-ind  { font-size: 11px; font-weight: 600; fill: var(--indigo); }
svg.sign-in-diagram .t-lbl-grn  { font-size: 11px; font-weight: 600; fill: var(--green); }
svg.sign-in-diagram .t-lbl-vio  { font-size: 11px; font-weight: 600; fill: var(--violet); }
svg.sign-in-diagram .t-lbl-amb  { font-size: 11px; font-weight: 600; fill: var(--amber); }
svg.sign-in-diagram .t-lbl-red  { font-size: 11px; font-weight: 600; fill: var(--red); }
svg.sign-in-diagram .t-step     { font-size: 11px; font-weight: 700; fill: var(--ink); }
svg.sign-in-diagram .t-ster     { font-size: 10px; fill: var(--muted); }
svg.sign-in-diagram .t-attr     { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--ink); }
svg.sign-in-diagram .t-attr-mut { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--muted); }
svg.sign-in-diagram .t-mono     { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--muted); }
svg.sign-in-diagram .t-mono-ink { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10px; fill: var(--ink); }
svg.sign-in-diagram .t-tag      { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 11px; letter-spacing: 0.14em; fill: var(--muted); }
svg.sign-in-diagram .t-tiny     { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 9.5px; fill: var(--muted); }
svg.sign-in-diagram .t-plane-grn { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10.5px; letter-spacing: 0.12em; font-weight: 600; fill: var(--green); }
svg.sign-in-diagram .t-plane-vio { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 10.5px; letter-spacing: 0.12em; font-weight: 600; fill: var(--violet); }
</style>
