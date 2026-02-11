var q=Object.defineProperty;var W=(i,e,t)=>e in i?q(i,e,{enumerable:!0,configurable:!0,writable:!0,value:t}):i[e]=t;var g=(i,e,t)=>W(i,typeof e!="symbol"?e+"":e,t);import{s as E}from"./index-Bz44qjX7.js";import{s as P}from"./index-DeH_O1z3.js";import{s as F}from"./index-PqjA9Uka.js";import{s as U}from"./index-d3RWyEKF.js";import{B as _,b as Y,C as j}from"./component-CLWqUams.js";import{O as H,P as V,R as X,S as M,V as D,W as Q,c as h,o as f,a as r,X as x,f as N,Y as G,k as z,A as b,Z as T,n as Z,$ as S,g as J,_ as ee,t as v,e as w,B as A,F as C,C as L,w as te,D as R}from"./index-BAuc_XpL.js";import{D as ie,a as se}from"./DataInsightsWidgetEntityService-Bo06B8sr.js";import"./index-AFbNaqJX.js";import"./index-g95NevXP.js";import"./index-CEDs-roO.js";import"./index-2fgv3Tde.js";var ne=H`
    .p-scrollpanel-content-container {
        overflow: hidden;
        width: 100%;
        height: 100%;
        position: relative;
        z-index: 1;
        float: left;
    }

    .p-scrollpanel-content {
        height: calc(100% + calc(2 * dt('scrollpanel.bar.size')));
        width: calc(100% + calc(2 * dt('scrollpanel.bar.size')));
        padding-inline: 0 calc(2 * dt('scrollpanel.bar.size'));
        padding-block: 0 calc(2 * dt('scrollpanel.bar.size'));
        position: relative;
        overflow: auto;
        box-sizing: border-box;
        scrollbar-width: none;
    }

    .p-scrollpanel-content::-webkit-scrollbar {
        display: none;
    }

    .p-scrollpanel-bar {
        position: relative;
        border-radius: dt('scrollpanel.bar.border.radius');
        z-index: 2;
        cursor: pointer;
        opacity: 0;
        outline-color: transparent;
        background: dt('scrollpanel.bar.background');
        border: 0 none;
        transition:
            outline-color dt('scrollpanel.transition.duration'),
            opacity dt('scrollpanel.transition.duration');
    }

    .p-scrollpanel-bar:focus-visible {
        box-shadow: dt('scrollpanel.bar.focus.ring.shadow');
        outline: dt('scrollpanel.barfocus.ring.width') dt('scrollpanel.bar.focus.ring.style') dt('scrollpanel.bar.focus.ring.color');
        outline-offset: dt('scrollpanel.barfocus.ring.offset');
    }

    .p-scrollpanel-bar-y {
        width: dt('scrollpanel.bar.size');
        inset-block-start: 0;
    }

    .p-scrollpanel-bar-x {
        height: dt('scrollpanel.bar.size');
        inset-block-end: 0;
    }

    .p-scrollpanel-hidden {
        visibility: hidden;
    }

    .p-scrollpanel:hover .p-scrollpanel-bar,
    .p-scrollpanel:active .p-scrollpanel-bar {
        opacity: 1;
    }

    .p-scrollpanel-grabbed {
        user-select: none;
    }
`,ae={root:"p-scrollpanel p-component",contentContainer:"p-scrollpanel-content-container",content:"p-scrollpanel-content",barX:"p-scrollpanel-bar p-scrollpanel-bar-x",barY:"p-scrollpanel-bar p-scrollpanel-bar-y"},re=V.extend({name:"scrollpanel",style:ne,classes:ae}),oe={name:"BaseScrollPanel",extends:X,props:{step:{type:Number,default:5}},style:re,provide:function(){return{$pcScrollPanel:this,$parentInstance:this}}},K={name:"ScrollPanel",extends:oe,inheritAttrs:!1,initialized:!1,documentResizeListener:null,documentMouseMoveListener:null,documentMouseUpListener:null,frame:null,scrollXRatio:null,scrollYRatio:null,isXBarClicked:!1,isYBarClicked:!1,lastPageX:null,lastPageY:null,timer:null,outsideClickListener:null,data:function(){return{orientation:"vertical",lastScrollTop:0,lastScrollLeft:0}},mounted:function(){this.$el.offsetParent&&this.initialize()},updated:function(){!this.initialized&&this.$el.offsetParent&&this.initialize()},beforeUnmount:function(){this.unbindDocumentResizeListener(),this.frame&&window.cancelAnimationFrame(this.frame)},methods:{initialize:function(){this.moveBar(),this.bindDocumentResizeListener(),this.calculateContainerHeight()},calculateContainerHeight:function(){var e=getComputedStyle(this.$el),t=getComputedStyle(this.$refs.xBar),s=Q(this.$el)-parseInt(t.height,10);e["max-height"]!=="none"&&s===0&&(this.$refs.content.offsetHeight+parseInt(t.height,10)>parseInt(e["max-height"],10)?this.$el.style.height=e["max-height"]:this.$el.style.height=this.$refs.content.offsetHeight+parseFloat(e.paddingTop)+parseFloat(e.paddingBottom)+parseFloat(e.borderTopWidth)+parseFloat(e.borderBottomWidth)+"px")},moveBar:function(){var e=this;if(this.$refs.content){var t=this.$refs.content.scrollWidth,s=this.$refs.content.clientWidth,a=(this.$el.clientHeight-this.$refs.xBar.clientHeight)*-1;this.scrollXRatio=s/t;var n=this.$refs.content.scrollHeight,l=this.$refs.content.clientHeight,m=(this.$el.clientWidth-this.$refs.yBar.clientWidth)*-1;this.scrollYRatio=l/n,this.frame=this.requestAnimationFrame(function(){e.$refs.xBar&&(e.scrollXRatio>=1?(e.$refs.xBar.setAttribute("data-p-scrollpanel-hidden","true"),!e.isUnstyled&&D(e.$refs.xBar,"p-scrollpanel-hidden")):(e.$refs.xBar.setAttribute("data-p-scrollpanel-hidden","false"),!e.isUnstyled&&M(e.$refs.xBar,"p-scrollpanel-hidden"),e.$refs.xBar.style.cssText="width:"+Math.max(e.scrollXRatio*100,10)+"%; inset-inline-start:"+Math.abs(e.$refs.content.scrollLeft)/t*100+"%;bottom:"+a+"px;")),e.$refs.yBar&&(e.scrollYRatio>=1?(e.$refs.yBar.setAttribute("data-p-scrollpanel-hidden","true"),!e.isUnstyled&&D(e.$refs.yBar,"p-scrollpanel-hidden")):(e.$refs.yBar.setAttribute("data-p-scrollpanel-hidden","false"),!e.isUnstyled&&M(e.$refs.yBar,"p-scrollpanel-hidden"),e.$refs.yBar.style.cssText="height:"+Math.max(e.scrollYRatio*100,10)+"%; top: calc("+e.$refs.content.scrollTop/n*100+"% - "+e.$refs.xBar.clientHeight+"px); inset-inline-end:"+m+"px;"))})}},onYBarMouseDown:function(e){this.isYBarClicked=!0,this.$refs.yBar.focus(),this.lastPageY=e.pageY,this.$refs.yBar.setAttribute("data-p-scrollpanel-grabbed","true"),!this.isUnstyled&&D(this.$refs.yBar,"p-scrollpanel-grabbed"),document.body.setAttribute("data-p-scrollpanel-grabbed","true"),!this.isUnstyled&&D(document.body,"p-scrollpanel-grabbed"),this.bindDocumentMouseListeners(),e.preventDefault()},onXBarMouseDown:function(e){this.isXBarClicked=!0,this.$refs.xBar.focus(),this.lastPageX=e.pageX,this.$refs.yBar.setAttribute("data-p-scrollpanel-grabbed","false"),!this.isUnstyled&&D(this.$refs.xBar,"p-scrollpanel-grabbed"),document.body.setAttribute("data-p-scrollpanel-grabbed","false"),!this.isUnstyled&&D(document.body,"p-scrollpanel-grabbed"),this.bindDocumentMouseListeners(),e.preventDefault()},onScroll:function(e){this.lastScrollLeft!==e.target.scrollLeft?(this.lastScrollLeft=e.target.scrollLeft,this.orientation="horizontal"):this.lastScrollTop!==e.target.scrollTop&&(this.lastScrollTop=e.target.scrollTop,this.orientation="vertical"),this.moveBar()},onKeyDown:function(e){if(this.orientation==="vertical")switch(e.code){case"ArrowDown":{this.setTimer("scrollTop",this.step),e.preventDefault();break}case"ArrowUp":{this.setTimer("scrollTop",this.step*-1),e.preventDefault();break}case"ArrowLeft":case"ArrowRight":{e.preventDefault();break}}else if(this.orientation==="horizontal")switch(e.code){case"ArrowRight":{this.setTimer("scrollLeft",this.step),e.preventDefault();break}case"ArrowLeft":{this.setTimer("scrollLeft",this.step*-1),e.preventDefault();break}case"ArrowDown":case"ArrowUp":{e.preventDefault();break}}},onKeyUp:function(){this.clearTimer()},repeat:function(e,t){this.$refs.content[e]+=t,this.moveBar()},setTimer:function(e,t){var s=this;this.clearTimer(),this.timer=setTimeout(function(){s.repeat(e,t)},40)},clearTimer:function(){this.timer&&clearTimeout(this.timer)},onDocumentMouseMove:function(e){this.isXBarClicked?this.onMouseMoveForXBar(e):this.isYBarClicked?this.onMouseMoveForYBar(e):(this.onMouseMoveForXBar(e),this.onMouseMoveForYBar(e))},onMouseMoveForXBar:function(e){var t=this,s=e.pageX-this.lastPageX;this.lastPageX=e.pageX,this.frame=this.requestAnimationFrame(function(){t.$refs.content.scrollLeft+=s/t.scrollXRatio})},onMouseMoveForYBar:function(e){var t=this,s=e.pageY-this.lastPageY;this.lastPageY=e.pageY,this.frame=this.requestAnimationFrame(function(){t.$refs.content.scrollTop+=s/t.scrollYRatio})},onFocus:function(e){this.$refs.xBar.isSameNode(e.target)?this.orientation="horizontal":this.$refs.yBar.isSameNode(e.target)&&(this.orientation="vertical")},onBlur:function(){this.orientation==="horizontal"&&(this.orientation="vertical")},onDocumentMouseUp:function(){this.$refs.yBar.setAttribute("data-p-scrollpanel-grabbed","false"),!this.isUnstyled&&M(this.$refs.yBar,"p-scrollpanel-grabbed"),this.$refs.xBar.setAttribute("data-p-scrollpanel-grabbed","false"),!this.isUnstyled&&M(this.$refs.xBar,"p-scrollpanel-grabbed"),document.body.setAttribute("data-p-scrollpanel-grabbed","false"),!this.isUnstyled&&M(document.body,"p-scrollpanel-grabbed"),this.unbindDocumentMouseListeners(),this.isXBarClicked=!1,this.isYBarClicked=!1},requestAnimationFrame:function(e){var t=window.requestAnimationFrame||this.timeoutFrame;return t(e)},refresh:function(){this.moveBar()},scrollTop:function(e){var t=this.$refs.content.scrollHeight-this.$refs.content.clientHeight;e=e>t?t:e>0?e:0,this.$refs.content.scrollTop=e},timeoutFrame:function(e){setTimeout(e,0)},bindDocumentMouseListeners:function(){var e=this;this.documentMouseMoveListener||(this.documentMouseMoveListener=function(t){e.onDocumentMouseMove(t)},document.addEventListener("mousemove",this.documentMouseMoveListener)),this.documentMouseUpListener||(this.documentMouseUpListener=function(t){e.onDocumentMouseUp(t)},document.addEventListener("mouseup",this.documentMouseUpListener))},unbindDocumentMouseListeners:function(){this.documentMouseMoveListener&&(document.removeEventListener("mousemove",this.documentMouseMoveListener),this.documentMouseMoveListener=null),this.documentMouseUpListener&&(document.removeEventListener("mouseup",this.documentMouseUpListener),this.documentMouseUpListener=null)},bindDocumentResizeListener:function(){var e=this;this.documentResizeListener||(this.documentResizeListener=function(){e.moveBar()},window.addEventListener("resize",this.documentResizeListener))},unbindDocumentResizeListener:function(){this.documentResizeListener&&(window.removeEventListener("resize",this.documentResizeListener),this.documentResizeListener=null)}},computed:{contentId:function(){return this.$id+"_content"}}},le=["id"],de=["aria-controls","aria-valuenow"],ce=["aria-controls","aria-valuenow"];function ue(i,e,t,s,a,n){return f(),h("div",x({class:i.cx("root")},i.ptmi("root")),[r("div",x({class:i.cx("contentContainer")},i.ptm("contentContainer")),[r("div",x({ref:"content",id:n.contentId,class:i.cx("content"),onScroll:e[0]||(e[0]=function(){return n.onScroll&&n.onScroll.apply(n,arguments)}),onMouseenter:e[1]||(e[1]=function(){return n.moveBar&&n.moveBar.apply(n,arguments)})},i.ptm("content")),[N(i.$slots,"default")],16,le)],16),r("div",x({ref:"xBar",class:i.cx("barx"),tabindex:"0",role:"scrollbar","aria-orientation":"horizontal","aria-controls":n.contentId,"aria-valuenow":a.lastScrollLeft,onMousedown:e[2]||(e[2]=function(){return n.onXBarMouseDown&&n.onXBarMouseDown.apply(n,arguments)}),onKeydown:e[3]||(e[3]=function(l){return n.onKeyDown(l)}),onKeyup:e[4]||(e[4]=function(){return n.onKeyUp&&n.onKeyUp.apply(n,arguments)}),onFocus:e[5]||(e[5]=function(){return n.onFocus&&n.onFocus.apply(n,arguments)}),onBlur:e[6]||(e[6]=function(){return n.onBlur&&n.onBlur.apply(n,arguments)})},i.ptm("barx"),{"data-pc-group-section":"bar"}),null,16,de),r("div",x({ref:"yBar",class:i.cx("bary"),tabindex:"0",role:"scrollbar","aria-orientation":"vertical","aria-controls":n.contentId,"aria-valuenow":a.lastScrollTop,onMousedown:e[7]||(e[7]=function(){return n.onYBarMouseDown&&n.onYBarMouseDown.apply(n,arguments)}),onKeydown:e[8]||(e[8]=function(l){return n.onKeyDown(l)}),onKeyup:e[9]||(e[9]=function(){return n.onKeyUp&&n.onKeyUp.apply(n,arguments)}),onFocus:e[10]||(e[10]=function(){return n.onFocus&&n.onFocus.apply(n,arguments)})},i.ptm("bary"),{"data-pc-group-section":"bar"}),null,16,ce)],16)}K.render=ue;var pe=H`
    .p-divider-horizontal {
        display: flex;
        width: 100%;
        position: relative;
        align-items: center;
        margin: dt('divider.horizontal.margin');
        padding: dt('divider.horizontal.padding');
    }

    .p-divider-horizontal:before {
        position: absolute;
        display: block;
        inset-block-start: 50%;
        inset-inline-start: 0;
        width: 100%;
        content: '';
        border-block-start: 1px solid dt('divider.border.color');
    }

    .p-divider-horizontal .p-divider-content {
        padding: dt('divider.horizontal.content.padding');
    }

    .p-divider-vertical {
        min-height: 100%;
        display: flex;
        position: relative;
        justify-content: center;
        margin: dt('divider.vertical.margin');
        padding: dt('divider.vertical.padding');
    }

    .p-divider-vertical:before {
        position: absolute;
        display: block;
        inset-block-start: 0;
        inset-inline-start: 50%;
        height: 100%;
        content: '';
        border-inline-start: 1px solid dt('divider.border.color');
    }

    .p-divider.p-divider-vertical .p-divider-content {
        padding: dt('divider.vertical.content.padding');
    }

    .p-divider-content {
        z-index: 1;
        background: dt('divider.content.background');
        color: dt('divider.content.color');
    }

    .p-divider-solid.p-divider-horizontal:before {
        border-block-start-style: solid;
    }

    .p-divider-solid.p-divider-vertical:before {
        border-inline-start-style: solid;
    }

    .p-divider-dashed.p-divider-horizontal:before {
        border-block-start-style: dashed;
    }

    .p-divider-dashed.p-divider-vertical:before {
        border-inline-start-style: dashed;
    }

    .p-divider-dotted.p-divider-horizontal:before {
        border-block-start-style: dotted;
    }

    .p-divider-dotted.p-divider-vertical:before {
        border-inline-start-style: dotted;
    }

    .p-divider-left:dir(rtl),
    .p-divider-right:dir(rtl) {
        flex-direction: row-reverse;
    }
`,he={root:function(e){var t=e.props;return{justifyContent:t.layout==="horizontal"?t.align==="center"||t.align===null?"center":t.align==="left"?"flex-start":t.align==="right"?"flex-end":null:null,alignItems:t.layout==="vertical"?t.align==="center"||t.align===null?"center":t.align==="top"?"flex-start":t.align==="bottom"?"flex-end":null:null}}},fe={root:function(e){var t=e.props;return["p-divider p-component","p-divider-"+t.layout,"p-divider-"+t.type,{"p-divider-left":t.layout==="horizontal"&&(!t.align||t.align==="left")},{"p-divider-center":t.layout==="horizontal"&&t.align==="center"},{"p-divider-right":t.layout==="horizontal"&&t.align==="right"},{"p-divider-top":t.layout==="vertical"&&t.align==="top"},{"p-divider-center":t.layout==="vertical"&&(!t.align||t.align==="center")},{"p-divider-bottom":t.layout==="vertical"&&t.align==="bottom"}]},content:"p-divider-content"},me=V.extend({name:"divider",style:pe,classes:fe,inlineStyles:he}),ge={name:"BaseDivider",extends:X,props:{align:{type:String,default:null},layout:{type:String,default:"horizontal"},type:{type:String,default:"solid"}},style:me,provide:function(){return{$pcDivider:this,$parentInstance:this}}};function k(i){"@babel/helpers - typeof";return k=typeof Symbol=="function"&&typeof Symbol.iterator=="symbol"?function(e){return typeof e}:function(e){return e&&typeof Symbol=="function"&&e.constructor===Symbol&&e!==Symbol.prototype?"symbol":typeof e},k(i)}function I(i,e,t){return(e=be(e))in i?Object.defineProperty(i,e,{value:t,enumerable:!0,configurable:!0,writable:!0}):i[e]=t,i}function be(i){var e=ve(i,"string");return k(e)=="symbol"?e:e+""}function ve(i,e){if(k(i)!="object"||!i)return i;var t=i[Symbol.toPrimitive];if(t!==void 0){var s=t.call(i,e);if(k(s)!="object")return s;throw new TypeError("@@toPrimitive must return a primitive value.")}return(e==="string"?String:Number)(i)}var O={name:"Divider",extends:ge,inheritAttrs:!1,computed:{dataP:function(){return G(I(I(I({},this.align,this.align),this.layout,this.layout),this.type,this.type))}}},ye=["aria-orientation","data-p"],we=["data-p"];function xe(i,e,t,s,a,n){return f(),h("div",x({class:i.cx("root"),style:i.sx("root"),role:"separator","aria-orientation":i.layout,"data-p":n.dataP},i.ptmi("root")),[i.$slots.default?(f(),h("div",x({key:0,class:i.cx("content"),"data-p":n.dataP},i.ptm("content")),[N(i.$slots,"default")],16,we)):z("",!0)],16,ye)}O.render=xe;var De=Object.defineProperty,Me=Object.getOwnPropertyDescriptor,$=(i,e,t,s)=>{for(var a=s>1?void 0:s?Me(e,t):e,n=i.length-1,l;n>=0;n--)(l=i[n])&&(a=(s?l(e,t,a):l(a))||a);return s&&a&&De(e,t,a),a};const p=J("data-insights");class ze{constructor(){g(this,"subscribers",new Set);g(this,"currentDateRange",{startDate:null,endDate:null})}subscribe(e){return this.subscribers.add(e),e(this.currentDateRange),()=>{this.subscribers.delete(e)}}updateDateRange(e){this.currentDateRange=e,this.subscribers.forEach(t=>t(e))}getCurrentDateRange(){return{...this.currentDateRange}}}let B=class extends _{constructor(){super(...arguments);g(this,"chatMessages",[]);g(this,"userInput","");g(this,"isLoading",!1);g(this,"visualizations",[]);g(this,"renderedVisualizationIds",new Set);g(this,"currentApplicationId","");g(this,"dateRange",{startDate:null,endDate:null});g(this,"showDateRangePicker",!1);g(this,"widgetService",new ie)}get currentApplicationName(){var s;return((s=b.currentApplication)==null?void 0:s.id)||"Unknown Application"}mounted(){var s,a;window.globalDateRangeObservable||(window.globalDateRangeObservable=new ze),this.currentApplicationId=((s=b.currentApplication)==null?void 0:s.id)||this.$route.params.applicationId||"default";const t=this.$route.params.applicationId;if(t&&!b.currentApplication){const n=(a=b.allApplications)==null?void 0:a.find(l=>l.id===t);n?b.currentApplication=n:this.currentApplicationId=t}window.setCurrentApp=this.setCurrentApplication,this.restoreStateFromStoredInsights()}setCurrentApplication(t){var a;const s=(a=b.allApplications)==null?void 0:a.find(n=>n.id===t);if(s){b.currentApplication=s,this.currentApplicationId=s.id,this.chatMessages=[],this.visualizations=[],this.renderedVisualizationIds=new Set;const n=document.getElementById("dashboard-container");n&&(n.innerHTML=""),this.addWelcomeMessage()}}restoreStateFromStoredInsights(){const t=T.getInsightsByApplication(this.currentApplicationId);if(t.length===0){this.visualizations=[],this.chatMessages=[],this.addWelcomeMessage();return}this.visualizations=t.filter(s=>s.htmlContent).map(s=>({id:s.id,htmlContent:s.htmlContent,createdAt:s.createdAt,status:"success",supportsDateRangeFiltering:!1})),this.addWelcomeMessage(),this.chatMessages.push({id:"restored-summary",type:"assistant",content:`Restored ${t.length} previous analysis${t.length>1?"es":""}. Your visualizations are available in the dashboard.`,timestamp:new Date,loading:!1,tasks:[],isExpanded:!1}),setTimeout(()=>{this.visualizations.forEach(s=>{this.executeVisualization(s.htmlContent,s.id)})},1e3)}addWelcomeMessage(){this.chatMessages.push({id:"welcome",type:"assistant",content:`Hello! I'm your data insights assistant. 

Ask me questions about your data in application "${this.currentApplicationName}" and I'll create visualizations for you. Try asking things like:

• Show me a summary of my data
• Create a chart showing trends over time
• Display the most important metrics

Components that support date filtering will automatically respond to the global date range picker.`,timestamp:new Date})}onApplicationChange(){if(b.currentApplication){this.currentApplicationId=b.currentApplication.id,this.chatMessages=[],this.visualizations=[],this.renderedVisualizationIds=new Set;const t=document.getElementById("dashboard-container");t&&(t.innerHTML=""),this.restoreStateFromStoredInsights()}}onApplicationNameChange(t,s){var a;if(t!=="Unknown Application"&&t!==s){this.currentApplicationId=((a=b.currentApplication)==null?void 0:a.id)||"",this.chatMessages=[],this.visualizations=[];const n=document.getElementById("dashboard-container");n&&(n.innerHTML=""),this.addWelcomeMessage()}}updateDateRange(){window.globalDateRangeObservable.updateDateRange(this.dateRange)}toggleDateRangePicker(){this.showDateRangePicker=!this.showDateRangePicker}async sendMessage(){if(!this.userInput.trim()||this.isLoading)return;const t={id:Date.now().toString(),type:"user",content:this.userInput,timestamp:new Date};this.chatMessages.push(t);const s=this.userInput;this.userInput="",this.isLoading=!0;const a={id:(Date.now()+1).toString(),type:"assistant",content:"Starting analysis...",timestamp:new Date,loading:!0,tasks:[]};this.chatMessages.push(a);try{const n=Z.getDataInsightsService(),l={query:s,applicationId:this.currentApplicationId,focusStructureId:void 0,preferredVisualization:void 0,additionalContext:void 0};n.processRequest(l).subscribe({next:c=>{var o;const u=this.chatMessages.find(d=>d.loading);if(u&&(c.message&&!((o=u.tasks)!=null&&o.includes(c.message))&&(u.tasks||(u.tasks=[]),u.tasks.push(c.message)),u.content=c.message),c.type===S.COMPONENTS_READY&&c.components&&c.components.length>0&&(p("Received %d components",c.components.length),c.components.forEach(async d=>{p("Processing component: %s (%s)",d.id,d.name),this.visualizations.push({id:d.id,htmlContent:d.rawHtml,createdAt:new Date(d.modifiedAt),status:"success",supportsDateRangeFiltering:d.supportsDateRangeFiltering||!1,saved:!1,component:d,userQuery:s});const y={id:d.id,title:d.name,description:`AI-generated insight for: "${s}"`,query:s,applicationId:this.currentApplicationId,createdAt:new Date(d.modifiedAt),data:d,visualizationType:this.detectVisualizationType(d.rawHtml),htmlContent:d.rawHtml,metadata:{tasks:u==null?void 0:u.tasks,status:"success"}};T.addInsight(y),p("Executing visualization for: %s",d.id),this.executeVisualization(d.rawHtml,d.id)}),p("Total visualizations: %d",this.visualizations.length)),c.type===S.COMPLETED){const d=this.chatMessages.find(y=>y.loading);d&&(d.loading=!1,d.content="Analysis completed! Your visualizations have been added to the dashboard.",d.isExpanded=!1)}if(c.type===S.ERROR){const d=this.chatMessages.find(y=>y.loading);d&&(d.loading=!1,d.content=`Error: ${c.errorMessage||"An error occurred during analysis"}`,d.isExpanded=!1)}},error:c=>{const u=this.chatMessages.find(o=>o.loading);u&&(u.loading=!1,u.content=`Error: ${c.message||"An error occurred during analysis"}`,u.isExpanded=!1)},complete:()=>{this.isLoading=!1}})}catch{const l=this.chatMessages.find(m=>m.loading);l&&(l.loading=!1,l.content="Sorry, I couldn't process your request. Please try again or rephrase your question.",l.isExpanded=!1),this.isLoading=!1}}executeVisualization(t,s){if(p("Executing visualization for: %s",s),s&&this.renderedVisualizationIds.has(s)){p("Visualization already rendered, skipping: %s",s);return}s&&(this.renderedVisualizationIds.add(s),p("Marked as rendering: %s",s));try{const a=document.createElement("script");a.textContent=t,document.head.appendChild(a),p("Script added to head");const n=t.match(/customElements\.define\(['"`]([^'"`]+)['"`]/),l=n?n[1]:"data-insights-dashboard";p("Custom element name: %s",l),setTimeout(()=>{try{if(p("Checking if custom element is registered: %s",l),!customElements.get(l)){p("Custom element not registered: %s",l);return}p("Custom element is registered: %s",l);const m=document.createElement("div");m.className="visualization-wrapper relative",m.setAttribute("data-viz-id",s||"");const c=document.createElement("button");c.className="save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10",c.innerHTML='<i class="pi pi-bookmark text-base"></i>',c.onclick=()=>this.handleSaveWidget(s);const u=document.createElement(l);p("Created element: %s",l),m.appendChild(c),m.appendChild(u);const o=document.getElementById("dashboard-container");p("Dashboard container found: %s",!!o),o?(o.appendChild(m),p("Visualization added to dashboard container")):p("Dashboard container not found!")}catch(m){p("Error in setTimeout block: %O",m)}},1e3)}catch(a){p("Error in executeVisualization: %O",a)}}onKeyPress(t){t.key==="Enter"&&!t.shiftKey&&(t.preventDefault(),this.sendMessage())}generateInsightTitle(t){const a=t.toLowerCase().split(" ").filter(n=>!["show","me","create","display","generate","make","a","an","the","of","for","with","in","on","at","to","from"].includes(n));return a.length>0?a.slice(0,3).map(n=>n.charAt(0).toUpperCase()+n.slice(1)).join(" "):"Data Insight"}detectVisualizationType(t){const s=t.toLowerCase();return s.includes("chart")||s.includes("apexchart")||s.includes("canvas")?"chart":s.includes("table")||s.includes("<table>")||s.includes("thead")?"table":s.includes("stat")||s.includes("metric")||s.includes("number")?"stat":"list"}async handleSaveWidget(t){const s=this.visualizations.find(a=>a.id===t);if(!(!s||!s.component||!s.userQuery)&&!s.saved)try{const a=document.querySelector(`[data-viz-id="${t}"]`),n=a==null?void 0:a.querySelector(".save-widget-btn");n&&(n.disabled=!0,n.innerHTML='<i class="pi pi-spin pi-spinner text-base"></i>'),await this.saveWidgetAsEntity(s.component),s.saved=!0,n&&(n.className="save-widget-btn absolute top-2 right-2 bg-gray-200 text-gray-600 rounded p-2 shadow-sm z-10 flex items-center justify-center w-10 h-10 cursor-default",n.innerHTML='<i class="pi pi-bookmark-fill text-base"></i>',n.disabled=!0)}catch{const n=document.querySelector(`[data-viz-id="${t}"]`),l=n==null?void 0:n.querySelector(".save-widget-btn");l&&(l.disabled=!1,l.className="save-widget-btn absolute top-2 right-2 bg-white hover:bg-primary-500 hover:text-white text-surface-600 rounded-full p-2 shadow-md transition-all duration-200 z-10 flex items-center justify-center w-10 h-10",l.innerHTML='<i class="pi pi-bookmark text-base"></i>')}}async saveWidgetAsEntity(t){try{const s=new se;s.applicationId=this.currentApplicationId,s.dataInsightsComponent=t,s.created=new Date,s.updated=new Date,await this.widgetService.save(s)}catch{}}};$([Y("APPLICATION_STATE.currentApplication",{immediate:!0,deep:!0})],B.prototype,"onApplicationChange",1);$([Y("currentApplicationName",{immediate:!0})],B.prototype,"onApplicationNameChange",1);B=$([j({components:{InputText:E,Button:U,Card:P,ScrollPanel:K,Divider:O,Calendar:F}})],B);const ke={class:"flex h-full"},Be={class:"w-2/3 flex flex-col"},Ae={class:"p-4 border-b border-surface-200 bg-surface-50 rounded-t-lg"},Se={class:"flex justify-between items-center"},Ce={class:"text-sm text-surface-600 mt-1"},Le={class:"flex items-center gap-2"},Re={key:0,class:"flex items-center gap-2 bg-white p-2 rounded border"},Ie={class:"flex items-center gap-2"},$e={class:"flex items-center gap-2"},Te={class:"flex-1 p-4 overflow-auto rounded-b-lg"},Ee={key:0,class:"flex items-center justify-center h-full"},Pe={key:1,id:"dashboard-container",class:"space-y-6"},Fe={class:"w-1/3 border-l border-surface-200 flex flex-col"},Ue={class:"flex-1 p-4 overflow-y-auto min-h-0 rounded-b-lg"},Ye={class:"space-y-4"},He={class:"text-sm"},Ve={class:"whitespace-pre-wrap"},Xe={key:0,class:"mt-3"},Ne={class:"space-y-1"},Ke={key:0,class:"pi pi-spin pi-spinner mr-2 text-primary-500"},Oe={key:1,class:"pi pi-check mr-2 text-green-500"},qe={key:1,class:"mt-3"},We=["onClick"],_e={key:0,class:"mt-2"},je={class:"space-y-1"},Qe={class:"text-xs text-surface-500 mt-2"},Ge={class:"p-4 border-t border-surface-200 bg-surface-50 flex-shrink-0 rounded-b-lg"},Ze={class:"flex gap-2"};function Je(i,e,t,s,a,n){const l=U,m=F,c=P,u=E;return f(),h("div",ke,[r("div",Be,[r("div",Ae,[r("div",Se,[r("div",null,[e[4]||(e[4]=r("h2",{class:"text-xl font-semibold text-surface-900"},"Visualization Dashboard",-1)),r("p",Ce,v(i.visualizations.length)+" visualization"+v(i.visualizations.length!==1?"s":"")+" created ",1)]),r("div",Le,[w(l,{onClick:i.toggleDateRangePicker,class:A(i.showDateRangePicker?"p-button-primary":"p-button-outlined"),icon:"pi pi-calendar",size:"small",label:i.showDateRangePicker?"Hide Date Range":"Set Date Range"},null,8,["onClick","class","label"]),i.showDateRangePicker?(f(),h("div",Re,[r("div",Ie,[e[5]||(e[5]=r("label",{class:"text-sm font-medium text-surface-700"},"From:",-1)),w(m,{modelValue:i.dateRange.startDate,"onUpdate:modelValue":e[0]||(e[0]=o=>i.dateRange.startDate=o),onDateSelect:i.updateDateRange,placeholder:"Start Date",size:"small",showIcon:""},null,8,["modelValue","onDateSelect"])]),r("div",$e,[e[6]||(e[6]=r("label",{class:"text-sm font-medium text-surface-700"},"To:",-1)),w(m,{modelValue:i.dateRange.endDate,"onUpdate:modelValue":e[1]||(e[1]=o=>i.dateRange.endDate=o),onDateSelect:i.updateDateRange,placeholder:"End Date",size:"small",showIcon:""},null,8,["modelValue","onDateSelect"])]),w(l,{onClick:e[2]||(e[2]=o=>{i.dateRange={startDate:null,endDate:null},i.updateDateRange()}),icon:"pi pi-times",size:"small",class:"p-button-text",title:"Clear date range"})])):z("",!0)])])]),r("div",Te,[i.visualizations.length===0?(f(),h("div",Ee,e[7]||(e[7]=[r("div",{class:"text-center text-surface-500"},[r("i",{class:"pi pi-chart-line text-4xl mb-4"}),r("p",{class:"text-lg font-medium"},"No visualizations yet"),r("p",{class:"text-sm"},"Start a conversation to create your first visualization")],-1)]))):(f(),h("div",Pe))])]),r("div",Fe,[e[10]||(e[10]=r("div",{class:"p-4 border-b border-surface-200 bg-surface-50 flex-shrink-0 rounded-t-lg"},[r("h1",{class:"text-xl font-semibold text-surface-900"},"Data Insights Chat"),r("p",{class:"text-sm text-surface-600 mt-1"},"Ask questions about your data")],-1)),r("div",Ue,[r("div",Ye,[(f(!0),h(C,null,L(i.chatMessages,o=>(f(),h("div",{key:o.id,class:A(["flex",o.type==="user"?"justify-end":"justify-start"])},[w(c,{class:A(["max-w-xs",o.type==="user"?"bg-primary-50 border-primary-200":"bg-surface-50 border-surface-200"])},{content:te(()=>[r("div",He,[r("div",Ve,v(o.content),1),o.loading&&o.tasks&&o.tasks.length>0?(f(),h("div",Xe,[e[8]||(e[8]=r("div",{class:"text-xs font-medium text-surface-700 mb-2"},"Progress:",-1)),r("ul",Ne,[(f(!0),h(C,null,L(o.tasks,(d,y)=>(f(),h("li",{key:d,class:"text-xs text-surface-600 flex items-center"},[y===o.tasks.length-1?(f(),h("i",Ke)):(f(),h("i",Oe)),R(" "+v(d),1)]))),128))])])):z("",!0),!o.loading&&o.tasks&&o.tasks.length>0?(f(),h("div",qe,[r("button",{onClick:d=>o.isExpanded=!o.isExpanded,class:"text-xs text-primary-600 hover:text-primary-700 flex items-center"},[r("i",{class:A([o.isExpanded?"pi pi-chevron-up":"pi pi-chevron-down","mr-1"])},null,2),R(" "+v(o.isExpanded?"Hide":"Show")+" task history ("+v(o.tasks.length)+" steps) ",1)],8,We),o.isExpanded?(f(),h("div",_e,[r("ul",je,[(f(!0),h(C,null,L(o.tasks,d=>(f(),h("li",{key:d,class:"text-xs text-surface-600 flex items-center"},[e[9]||(e[9]=r("i",{class:"pi pi-check mr-2 text-green-500"},null,-1)),R(" "+v(d),1)]))),128))])])):z("",!0)])):z("",!0),r("div",Qe,v(o.timestamp.toLocaleTimeString()),1)])]),_:2},1032,["class"])],2))),128))])]),r("div",Ge,[r("div",Ze,[w(u,{modelValue:i.userInput,"onUpdate:modelValue":e[3]||(e[3]=o=>i.userInput=o),placeholder:"Ask about your data...",class:"flex-1",disabled:i.isLoading,onKeypress:i.onKeyPress},null,8,["modelValue","disabled","onKeypress"]),w(l,{onClick:i.sendMessage,loading:i.isLoading,disabled:!i.userInput.trim()||i.isLoading,icon:"pi pi-send",class:"p-button-primary"},null,8,["onClick","loading","disabled"])])])])])}const ht=ee(B,[["render",Je]]);export{ht as default};
//# sourceMappingURL=DataInsights-CCiLO1up.js.map
