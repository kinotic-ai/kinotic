import{O,P as b,R as p,c as u,o as l,f as d,X as o,ak as A,F as P,k as w,y as g,z as E,b as m,w as C,ai as y,B as S,a1 as N,Y as K,al as $,W as F,a7 as B,am as R,an as h,ab as W,ao as L,ag as V,a as T,ap as _,a9 as f}from"./index-BAuc_XpL.js";import{s as D}from"./index-CEDs-roO.js";import{s as H}from"./index-2fgv3Tde.js";var U=O`
    .p-tabs {
        display: flex;
        flex-direction: column;
    }

    .p-tablist {
        display: flex;
        position: relative;
    }

    .p-tabs-scrollable > .p-tablist {
        overflow: hidden;
    }

    .p-tablist-viewport {
        overflow-x: auto;
        overflow-y: hidden;
        scroll-behavior: smooth;
        scrollbar-width: none;
        overscroll-behavior: contain auto;
    }

    .p-tablist-viewport::-webkit-scrollbar {
        display: none;
    }

    .p-tablist-tab-list {
        position: relative;
        display: flex;
        background: dt('tabs.tablist.background');
        border-style: solid;
        border-color: dt('tabs.tablist.border.color');
        border-width: dt('tabs.tablist.border.width');
    }

    .p-tablist-content {
        flex-grow: 1;
    }

    .p-tablist-nav-button {
        all: unset;
        position: absolute !important;
        flex-shrink: 0;
        inset-block-start: 0;
        z-index: 2;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: dt('tabs.nav.button.background');
        color: dt('tabs.nav.button.color');
        width: dt('tabs.nav.button.width');
        transition:
            color dt('tabs.transition.duration'),
            outline-color dt('tabs.transition.duration'),
            box-shadow dt('tabs.transition.duration');
        box-shadow: dt('tabs.nav.button.shadow');
        outline-color: transparent;
        cursor: pointer;
    }

    .p-tablist-nav-button:focus-visible {
        z-index: 1;
        box-shadow: dt('tabs.nav.button.focus.ring.shadow');
        outline: dt('tabs.nav.button.focus.ring.width') dt('tabs.nav.button.focus.ring.style') dt('tabs.nav.button.focus.ring.color');
        outline-offset: dt('tabs.nav.button.focus.ring.offset');
    }

    .p-tablist-nav-button:hover {
        color: dt('tabs.nav.button.hover.color');
    }

    .p-tablist-prev-button {
        inset-inline-start: 0;
    }

    .p-tablist-next-button {
        inset-inline-end: 0;
    }

    .p-tablist-prev-button:dir(rtl),
    .p-tablist-next-button:dir(rtl) {
        transform: rotate(180deg);
    }

    .p-tab {
        flex-shrink: 0;
        cursor: pointer;
        user-select: none;
        position: relative;
        border-style: solid;
        white-space: nowrap;
        gap: dt('tabs.tab.gap');
        background: dt('tabs.tab.background');
        border-width: dt('tabs.tab.border.width');
        border-color: dt('tabs.tab.border.color');
        color: dt('tabs.tab.color');
        padding: dt('tabs.tab.padding');
        font-weight: dt('tabs.tab.font.weight');
        transition:
            background dt('tabs.transition.duration'),
            border-color dt('tabs.transition.duration'),
            color dt('tabs.transition.duration'),
            outline-color dt('tabs.transition.duration'),
            box-shadow dt('tabs.transition.duration');
        margin: dt('tabs.tab.margin');
        outline-color: transparent;
    }

    .p-tab:not(.p-disabled):focus-visible {
        z-index: 1;
        box-shadow: dt('tabs.tab.focus.ring.shadow');
        outline: dt('tabs.tab.focus.ring.width') dt('tabs.tab.focus.ring.style') dt('tabs.tab.focus.ring.color');
        outline-offset: dt('tabs.tab.focus.ring.offset');
    }

    .p-tab:not(.p-tab-active):not(.p-disabled):hover {
        background: dt('tabs.tab.hover.background');
        border-color: dt('tabs.tab.hover.border.color');
        color: dt('tabs.tab.hover.color');
    }

    .p-tab-active {
        background: dt('tabs.tab.active.background');
        border-color: dt('tabs.tab.active.border.color');
        color: dt('tabs.tab.active.color');
    }

    .p-tabpanels {
        background: dt('tabs.tabpanel.background');
        color: dt('tabs.tabpanel.color');
        padding: dt('tabs.tabpanel.padding');
        outline: 0 none;
    }

    .p-tabpanel:focus-visible {
        box-shadow: dt('tabs.tabpanel.focus.ring.shadow');
        outline: dt('tabs.tabpanel.focus.ring.width') dt('tabs.tabpanel.focus.ring.style') dt('tabs.tabpanel.focus.ring.color');
        outline-offset: dt('tabs.tabpanel.focus.ring.offset');
    }

    .p-tablist-active-bar {
        z-index: 1;
        display: block;
        position: absolute;
        inset-block-end: dt('tabs.active.bar.bottom');
        height: dt('tabs.active.bar.height');
        background: dt('tabs.active.bar.background');
        transition: 250ms cubic-bezier(0.35, 0, 0.25, 1);
    }
`,j={root:function(t){var n=t.props;return["p-tabs p-component",{"p-tabs-scrollable":n.scrollable}]}},M=b.extend({name:"tabs",style:U,classes:j}),q={name:"BaseTabs",extends:p,props:{value:{type:[String,Number],default:void 0},lazy:{type:Boolean,default:!1},scrollable:{type:Boolean,default:!1},showNavigators:{type:Boolean,default:!0},tabindex:{type:Number,default:0},selectOnFocus:{type:Boolean,default:!1}},style:M,provide:function(){return{$pcTabs:this,$parentInstance:this}}},X={name:"Tabs",extends:q,inheritAttrs:!1,emits:["update:value"],data:function(){return{d_value:this.value}},watch:{value:function(t){this.d_value=t}},methods:{updateValue:function(t){this.d_value!==t&&(this.d_value=t,this.$emit("update:value",t))},isVertical:function(){return this.orientation==="vertical"}}};function Y(a,t,n,s,i,e){return l(),u("div",o({class:a.cx("root")},a.ptmi("root")),[d(a.$slots,"default")],16)}X.render=Y;var G={root:"p-tabpanels"},J=b.extend({name:"tabpanels",classes:G}),Q={name:"BaseTabPanels",extends:p,props:{},style:J,provide:function(){return{$pcTabPanels:this,$parentInstance:this}}},Z={name:"TabPanels",extends:Q,inheritAttrs:!1};function tt(a,t,n,s,i,e){return l(),u("div",o({class:a.cx("root"),role:"presentation"},a.ptmi("root")),[d(a.$slots,"default")],16)}Z.render=tt;var at={root:function(t){var n=t.instance;return["p-tabpanel",{"p-tabpanel-active":n.active}]}},et=b.extend({name:"tabpanel",classes:at}),nt={name:"BaseTabPanel",extends:p,props:{value:{type:[String,Number],default:void 0},as:{type:[String,Object],default:"DIV"},asChild:{type:Boolean,default:!1},header:null,headerStyle:null,headerClass:null,headerProps:null,headerActionProps:null,contentStyle:null,contentClass:null,contentProps:null,disabled:Boolean},style:et,provide:function(){return{$pcTabPanel:this,$parentInstance:this}}},st={name:"TabPanel",extends:nt,inheritAttrs:!1,inject:["$pcTabs"],computed:{active:function(){var t;return A((t=this.$pcTabs)===null||t===void 0?void 0:t.d_value,this.value)},id:function(){var t;return"".concat((t=this.$pcTabs)===null||t===void 0?void 0:t.$id,"_tabpanel_").concat(this.value)},ariaLabelledby:function(){var t;return"".concat((t=this.$pcTabs)===null||t===void 0?void 0:t.$id,"_tab_").concat(this.value)},attrs:function(){return o(this.a11yAttrs,this.ptmi("root",this.ptParams))},a11yAttrs:function(){var t;return{id:this.id,tabindex:(t=this.$pcTabs)===null||t===void 0?void 0:t.tabindex,role:"tabpanel","aria-labelledby":this.ariaLabelledby,"data-pc-name":"tabpanel","data-p-active":this.active}},ptParams:function(){return{context:{active:this.active}}}}};function it(a,t,n,s,i,e){var r,c;return e.$pcTabs?(l(),u(P,{key:1},[a.asChild?d(a.$slots,"default",{key:1,class:S(a.cx("root")),active:e.active,a11yAttrs:e.a11yAttrs}):(l(),u(P,{key:0},[!((r=e.$pcTabs)!==null&&r!==void 0&&r.lazy)||e.active?g((l(),m(y(a.as),o({key:0,class:a.cx("root")},e.attrs),{default:C(function(){return[d(a.$slots,"default")]}),_:3},16,["class"])),[[E,(c=e.$pcTabs)!==null&&c!==void 0&&c.lazy?!0:e.active]]):w("",!0)],64))],64)):d(a.$slots,"default",{key:0})}st.render=it;var rt={root:"p-tablist",content:function(t){var n=t.instance;return["p-tablist-content",{"p-tablist-viewport":n.$pcTabs.scrollable}]},tabList:"p-tablist-tab-list",activeBar:"p-tablist-active-bar",prevButton:"p-tablist-prev-button p-tablist-nav-button",nextButton:"p-tablist-next-button p-tablist-nav-button"},ot=b.extend({name:"tablist",classes:rt}),lt={name:"BaseTabList",extends:p,props:{},style:ot,provide:function(){return{$pcTabList:this,$parentInstance:this}}},ct={name:"TabList",extends:lt,inheritAttrs:!1,inject:["$pcTabs"],data:function(){return{isPrevButtonEnabled:!1,isNextButtonEnabled:!0}},resizeObserver:void 0,watch:{showNavigators:function(t){t?this.bindResizeObserver():this.unbindResizeObserver()},activeValue:{flush:"post",handler:function(){this.updateInkBar()}}},mounted:function(){var t=this;setTimeout(function(){t.updateInkBar()},150),this.showNavigators&&(this.updateButtonState(),this.bindResizeObserver())},updated:function(){this.showNavigators&&this.updateButtonState()},beforeUnmount:function(){this.unbindResizeObserver()},methods:{onScroll:function(t){this.showNavigators&&this.updateButtonState(),t.preventDefault()},onPrevButtonClick:function(){var t=this.$refs.content,n=this.getVisibleButtonWidths(),s=$(t)-n,i=Math.abs(t.scrollLeft),e=s*.8,r=i-e,c=Math.max(r,0);t.scrollLeft=L(t)?-1*c:c},onNextButtonClick:function(){var t=this.$refs.content,n=this.getVisibleButtonWidths(),s=$(t)-n,i=Math.abs(t.scrollLeft),e=s*.8,r=i+e,c=t.scrollWidth-s,v=Math.min(r,c);t.scrollLeft=L(t)?-1*v:v},bindResizeObserver:function(){var t=this;this.resizeObserver=new ResizeObserver(function(){return t.updateButtonState()}),this.resizeObserver.observe(this.$refs.list)},unbindResizeObserver:function(){var t;(t=this.resizeObserver)===null||t===void 0||t.unobserve(this.$refs.list),this.resizeObserver=void 0},updateInkBar:function(){var t=this.$refs,n=t.content,s=t.inkbar,i=t.tabs;if(s){var e=B(n,'[data-pc-name="tab"][data-p-active="true"]');this.$pcTabs.isVertical()?(s.style.height=R(e)+"px",s.style.top=h(e).top-h(i).top+"px"):(s.style.width=W(e)+"px",s.style.left=h(e).left-h(i).left+"px")}},updateButtonState:function(){var t=this.$refs,n=t.list,s=t.content,i=s.scrollTop,e=s.scrollWidth,r=s.scrollHeight,c=s.offsetWidth,v=s.offsetHeight,k=Math.abs(s.scrollLeft),x=[$(s),F(s)],z=x[0],I=x[1];this.$pcTabs.isVertical()?(this.isPrevButtonEnabled=i!==0,this.isNextButtonEnabled=n.offsetHeight>=v&&parseInt(i)!==r-I):(this.isPrevButtonEnabled=k!==0,this.isNextButtonEnabled=n.offsetWidth>=c&&parseInt(k)!==e-z)},getVisibleButtonWidths:function(){var t=this.$refs,n=t.prevButton,s=t.nextButton,i=0;return this.showNavigators&&(i=((n==null?void 0:n.offsetWidth)||0)+((s==null?void 0:s.offsetWidth)||0)),i}},computed:{templates:function(){return this.$pcTabs.$slots},activeValue:function(){return this.$pcTabs.d_value},showNavigators:function(){return this.$pcTabs.scrollable&&this.$pcTabs.showNavigators},prevButtonAriaLabel:function(){return this.$primevue.config.locale.aria?this.$primevue.config.locale.aria.previous:void 0},nextButtonAriaLabel:function(){return this.$primevue.config.locale.aria?this.$primevue.config.locale.aria.next:void 0},dataP:function(){return K({scrollable:this.$pcTabs.scrollable})}},components:{ChevronLeftIcon:D,ChevronRightIcon:H},directives:{ripple:N}},dt=["data-p"],ut=["aria-label","tabindex"],bt=["data-p"],pt=["aria-orientation"],vt=["aria-label","tabindex"];function ht(a,t,n,s,i,e){var r=V("ripple");return l(),u("div",o({ref:"list",class:a.cx("root"),"data-p":e.dataP},a.ptmi("root")),[e.showNavigators&&i.isPrevButtonEnabled?g((l(),u("button",o({key:0,ref:"prevButton",type:"button",class:a.cx("prevButton"),"aria-label":e.prevButtonAriaLabel,tabindex:e.$pcTabs.tabindex,onClick:t[0]||(t[0]=function(){return e.onPrevButtonClick&&e.onPrevButtonClick.apply(e,arguments)})},a.ptm("prevButton"),{"data-pc-group-section":"navigator"}),[(l(),m(y(e.templates.previcon||"ChevronLeftIcon"),o({"aria-hidden":"true"},a.ptm("prevIcon")),null,16))],16,ut)),[[r]]):w("",!0),T("div",o({ref:"content",class:a.cx("content"),onScroll:t[1]||(t[1]=function(){return e.onScroll&&e.onScroll.apply(e,arguments)}),"data-p":e.dataP},a.ptm("content")),[T("div",o({ref:"tabs",class:a.cx("tabList"),role:"tablist","aria-orientation":e.$pcTabs.orientation||"horizontal"},a.ptm("tabList")),[d(a.$slots,"default"),T("span",o({ref:"inkbar",class:a.cx("activeBar"),role:"presentation","aria-hidden":"true"},a.ptm("activeBar")),null,16)],16,pt)],16,bt),e.showNavigators&&i.isNextButtonEnabled?g((l(),u("button",o({key:1,ref:"nextButton",type:"button",class:a.cx("nextButton"),"aria-label":e.nextButtonAriaLabel,tabindex:e.$pcTabs.tabindex,onClick:t[2]||(t[2]=function(){return e.onNextButtonClick&&e.onNextButtonClick.apply(e,arguments)})},a.ptm("nextButton"),{"data-pc-group-section":"navigator"}),[(l(),m(y(e.templates.nexticon||"ChevronRightIcon"),o({"aria-hidden":"true"},a.ptm("nextIcon")),null,16))],16,vt)),[[r]]):w("",!0)],16,dt)}ct.render=ht;var ft={root:function(t){var n=t.instance,s=t.props;return["p-tab",{"p-tab-active":n.active,"p-disabled":s.disabled}]}},gt=b.extend({name:"tab",classes:ft}),mt={name:"BaseTab",extends:p,props:{value:{type:[String,Number],default:void 0},disabled:{type:Boolean,default:!1},as:{type:[String,Object],default:"BUTTON"},asChild:{type:Boolean,default:!1}},style:gt,provide:function(){return{$pcTab:this,$parentInstance:this}}},yt={name:"Tab",extends:mt,inheritAttrs:!1,inject:["$pcTabs","$pcTabList"],methods:{onFocus:function(){this.$pcTabs.selectOnFocus&&this.changeActiveValue()},onClick:function(){this.changeActiveValue()},onKeydown:function(t){switch(t.code){case"ArrowRight":this.onArrowRightKey(t);break;case"ArrowLeft":this.onArrowLeftKey(t);break;case"Home":this.onHomeKey(t);break;case"End":this.onEndKey(t);break;case"PageDown":this.onPageDownKey(t);break;case"PageUp":this.onPageUpKey(t);break;case"Enter":case"NumpadEnter":case"Space":this.onEnterKey(t);break}},onArrowRightKey:function(t){var n=this.findNextTab(t.currentTarget);n?this.changeFocusedTab(t,n):this.onHomeKey(t),t.preventDefault()},onArrowLeftKey:function(t){var n=this.findPrevTab(t.currentTarget);n?this.changeFocusedTab(t,n):this.onEndKey(t),t.preventDefault()},onHomeKey:function(t){var n=this.findFirstTab();this.changeFocusedTab(t,n),t.preventDefault()},onEndKey:function(t){var n=this.findLastTab();this.changeFocusedTab(t,n),t.preventDefault()},onPageDownKey:function(t){this.scrollInView(this.findLastTab()),t.preventDefault()},onPageUpKey:function(t){this.scrollInView(this.findFirstTab()),t.preventDefault()},onEnterKey:function(t){this.changeActiveValue(),t.preventDefault()},findNextTab:function(t){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:!1,s=n?t:t.nextElementSibling;return s?f(s,"data-p-disabled")||f(s,"data-pc-section")==="activebar"?this.findNextTab(s):B(s,'[data-pc-name="tab"]'):null},findPrevTab:function(t){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:!1,s=n?t:t.previousElementSibling;return s?f(s,"data-p-disabled")||f(s,"data-pc-section")==="activebar"?this.findPrevTab(s):B(s,'[data-pc-name="tab"]'):null},findFirstTab:function(){return this.findNextTab(this.$pcTabList.$refs.tabs.firstElementChild,!0)},findLastTab:function(){return this.findPrevTab(this.$pcTabList.$refs.tabs.lastElementChild,!0)},changeActiveValue:function(){this.$pcTabs.updateValue(this.value)},changeFocusedTab:function(t,n){_(n),this.scrollInView(n)},scrollInView:function(t){var n;t==null||(n=t.scrollIntoView)===null||n===void 0||n.call(t,{block:"nearest"})}},computed:{active:function(){var t;return A((t=this.$pcTabs)===null||t===void 0?void 0:t.d_value,this.value)},id:function(){var t;return"".concat((t=this.$pcTabs)===null||t===void 0?void 0:t.$id,"_tab_").concat(this.value)},ariaControls:function(){var t;return"".concat((t=this.$pcTabs)===null||t===void 0?void 0:t.$id,"_tabpanel_").concat(this.value)},attrs:function(){return o(this.asAttrs,this.a11yAttrs,this.ptmi("root",this.ptParams))},asAttrs:function(){return this.as==="BUTTON"?{type:"button",disabled:this.disabled}:void 0},a11yAttrs:function(){return{id:this.id,tabindex:this.active?this.$pcTabs.tabindex:-1,role:"tab","aria-selected":this.active,"aria-controls":this.ariaControls,"data-pc-name":"tab","data-p-disabled":this.disabled,"data-p-active":this.active,onFocus:this.onFocus,onKeydown:this.onKeydown}},ptParams:function(){return{context:{active:this.active}}},dataP:function(){return K({active:this.active})}},directives:{ripple:N}};function $t(a,t,n,s,i,e){var r=V("ripple");return a.asChild?d(a.$slots,"default",{key:1,dataP:e.dataP,class:S(a.cx("root")),active:e.active,a11yAttrs:e.a11yAttrs,onClick:e.onClick}):g((l(),m(y(a.as),o({key:0,class:a.cx("root"),"data-p":e.dataP,onClick:e.onClick},e.attrs),{default:C(function(){return[d(a.$slots,"default")]}),_:3},16,["class","data-p","onClick"])),[[r]])}yt.render=$t;export{Z as a,yt as b,ct as c,X as d,st as s};
//# sourceMappingURL=index-BOiP5G75.js.map
