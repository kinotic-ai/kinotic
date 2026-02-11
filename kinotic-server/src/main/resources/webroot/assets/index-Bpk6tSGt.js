import{O as z,P as F,a2 as V,a1 as R,R as E,Y as K,ad as B,ac as H,ab as k,ae as I,af as N,ap as v,a6 as y,a7 as x,X as r,au as U,ag as Z,c as l,k as c,o as a,a as O,y as W,b as h,B as j,ai as w,t as T,r as P,w as M,e as X,T as Y,f as L,F as f,C as S,D as q}from"./index-BAuc_XpL.js";import{O as G,C as J}from"./index-AFbNaqJX.js";var Q=z`
    .p-menu {
        background: dt('menu.background');
        color: dt('menu.color');
        border: 1px solid dt('menu.border.color');
        border-radius: dt('menu.border.radius');
        min-width: 12.5rem;
    }

    .p-menu-list {
        margin: 0;
        padding: dt('menu.list.padding');
        outline: 0 none;
        list-style: none;
        display: flex;
        flex-direction: column;
        gap: dt('menu.list.gap');
    }

    .p-menu-item-content {
        transition:
            background dt('menu.transition.duration'),
            color dt('menu.transition.duration');
        border-radius: dt('menu.item.border.radius');
        color: dt('menu.item.color');
    }

    .p-menu-item-link {
        cursor: pointer;
        display: flex;
        align-items: center;
        text-decoration: none;
        overflow: hidden;
        position: relative;
        color: inherit;
        padding: dt('menu.item.padding');
        gap: dt('menu.item.gap');
        user-select: none;
        outline: 0 none;
    }

    .p-menu-item-label {
        line-height: 1;
    }

    .p-menu-item-icon {
        color: dt('menu.item.icon.color');
    }

    .p-menu-item.p-focus .p-menu-item-content {
        color: dt('menu.item.focus.color');
        background: dt('menu.item.focus.background');
    }

    .p-menu-item.p-focus .p-menu-item-icon {
        color: dt('menu.item.icon.focus.color');
    }

    .p-menu-item:not(.p-disabled) .p-menu-item-content:hover {
        color: dt('menu.item.focus.color');
        background: dt('menu.item.focus.background');
    }

    .p-menu-item:not(.p-disabled) .p-menu-item-content:hover .p-menu-item-icon {
        color: dt('menu.item.icon.focus.color');
    }

    .p-menu-overlay {
        box-shadow: dt('menu.shadow');
    }

    .p-menu-submenu-label {
        background: dt('menu.submenu.label.background');
        padding: dt('menu.submenu.label.padding');
        color: dt('menu.submenu.label.color');
        font-weight: dt('menu.submenu.label.font.weight');
    }

    .p-menu-separator {
        border-block-start: 1px solid dt('menu.separator.border.color');
    }
`,_={root:function(e){var i=e.props;return["p-menu p-component",{"p-menu-overlay":i.popup}]},start:"p-menu-start",list:"p-menu-list",submenuLabel:"p-menu-submenu-label",separator:"p-menu-separator",end:"p-menu-end",item:function(e){var i=e.instance;return["p-menu-item",{"p-focus":i.id===i.focusedOptionId,"p-disabled":i.disabled()}]},itemContent:"p-menu-item-content",itemLink:"p-menu-item-link",itemIcon:"p-menu-item-icon",itemLabel:"p-menu-item-label"},$=F.extend({name:"menu",style:Q,classes:_}),ee={name:"BaseMenu",extends:E,props:{popup:{type:Boolean,default:!1},model:{type:Array,default:null},appendTo:{type:[String,Object],default:"body"},autoZIndex:{type:Boolean,default:!0},baseZIndex:{type:Number,default:0},tabindex:{type:Number,default:0},ariaLabel:{type:String,default:null},ariaLabelledby:{type:String,default:null}},style:$,provide:function(){return{$pcMenu:this,$parentInstance:this}}},D={name:"Menuitem",hostName:"Menu",extends:E,inheritAttrs:!1,emits:["item-click","item-mousemove"],props:{item:null,templates:null,id:null,focusedOptionId:null,index:null},methods:{getItemProp:function(e,i){return e&&e.item?U(e.item[i]):void 0},getPTOptions:function(e){return this.ptm(e,{context:{item:this.item,index:this.index,focused:this.isItemFocused(),disabled:this.disabled()}})},isItemFocused:function(){return this.focusedOptionId===this.id},onItemClick:function(e){var i=this.getItemProp(this.item,"command");i&&i({originalEvent:e,item:this.item.item}),this.$emit("item-click",{originalEvent:e,item:this.item,id:this.id})},onItemMouseMove:function(e){this.$emit("item-mousemove",{originalEvent:e,item:this.item,id:this.id})},visible:function(){return typeof this.item.visible=="function"?this.item.visible():this.item.visible!==!1},disabled:function(){return typeof this.item.disabled=="function"?this.item.disabled():this.item.disabled},label:function(){return typeof this.item.label=="function"?this.item.label():this.item.label},getMenuItemProps:function(e){return{action:r({class:this.cx("itemLink"),tabindex:"-1"},this.getPTOptions("itemLink")),icon:r({class:[this.cx("itemIcon"),e.icon]},this.getPTOptions("itemIcon")),label:r({class:this.cx("itemLabel")},this.getPTOptions("itemLabel"))}}},computed:{dataP:function(){return K({focus:this.isItemFocused(),disabled:this.disabled()})}},directives:{ripple:R}},te=["id","aria-label","aria-disabled","data-p-focused","data-p-disabled","data-p"],ie=["data-p"],ne=["href","target"],se=["data-p"],ae=["data-p"];function oe(t,e,i,s,d,n){var b=Z("ripple");return n.visible()?(a(),l("li",r({key:0,id:i.id,class:[t.cx("item"),i.item.class],role:"menuitem",style:i.item.style,"aria-label":n.label(),"aria-disabled":n.disabled(),"data-p-focused":n.isItemFocused(),"data-p-disabled":n.disabled()||!1,"data-p":n.dataP},n.getPTOptions("item")),[O("div",r({class:t.cx("itemContent"),onClick:e[0]||(e[0]=function(p){return n.onItemClick(p)}),onMousemove:e[1]||(e[1]=function(p){return n.onItemMouseMove(p)}),"data-p":n.dataP},n.getPTOptions("itemContent")),[i.templates.item?i.templates.item?(a(),h(w(i.templates.item),{key:1,item:i.item,label:n.label(),props:n.getMenuItemProps(i.item)},null,8,["item","label","props"])):c("",!0):W((a(),l("a",r({key:0,href:i.item.url,class:t.cx("itemLink"),target:i.item.target,tabindex:"-1"},n.getPTOptions("itemLink")),[i.templates.itemicon?(a(),h(w(i.templates.itemicon),{key:0,item:i.item,class:j(t.cx("itemIcon"))},null,8,["item","class"])):i.item.icon?(a(),l("span",r({key:1,class:[t.cx("itemIcon"),i.item.icon],"data-p":n.dataP},n.getPTOptions("itemIcon")),null,16,se)):c("",!0),O("span",r({class:t.cx("itemLabel"),"data-p":n.dataP},n.getPTOptions("itemLabel")),T(n.label()),17,ae)],16,ne)),[[b]])],16,ie)],16,te)):c("",!0)}D.render=oe;function A(t){return ue(t)||de(t)||le(t)||re()}function re(){throw new TypeError(`Invalid attempt to spread non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`)}function le(t,e){if(t){if(typeof t=="string")return C(t,e);var i={}.toString.call(t).slice(8,-1);return i==="Object"&&t.constructor&&(i=t.constructor.name),i==="Map"||i==="Set"?Array.from(t):i==="Arguments"||/^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(i)?C(t,e):void 0}}function de(t){if(typeof Symbol<"u"&&t[Symbol.iterator]!=null||t["@@iterator"]!=null)return Array.from(t)}function ue(t){if(Array.isArray(t))return C(t)}function C(t,e){(e==null||e>t.length)&&(e=t.length);for(var i=0,s=Array(e);i<e;i++)s[i]=t[i];return s}var ce={name:"Menu",extends:ee,inheritAttrs:!1,emits:["show","hide","focus","blur"],data:function(){return{overlayVisible:!1,focused:!1,focusedOptionIndex:-1,selectedOptionIndex:-1}},target:null,outsideClickListener:null,scrollHandler:null,resizeListener:null,container:null,list:null,mounted:function(){this.popup||(this.bindResizeListener(),this.bindOutsideClickListener())},beforeUnmount:function(){this.unbindResizeListener(),this.unbindOutsideClickListener(),this.scrollHandler&&(this.scrollHandler.destroy(),this.scrollHandler=null),this.target=null,this.container&&this.autoZIndex&&I.clear(this.container),this.container=null},methods:{itemClick:function(e){var i=e.item;this.disabled(i)||(i.command&&i.command(e),this.overlayVisible&&this.hide(),!this.popup&&this.focusedOptionIndex!==e.id&&(this.focusedOptionIndex=e.id))},itemMouseMove:function(e){this.focused&&(this.focusedOptionIndex=e.id)},onListFocus:function(e){this.focused=!0,!this.popup&&this.changeFocusedOptionIndex(0),this.$emit("focus",e)},onListBlur:function(e){this.focused=!1,this.focusedOptionIndex=-1,this.$emit("blur",e)},onListKeyDown:function(e){switch(e.code){case"ArrowDown":this.onArrowDownKey(e);break;case"ArrowUp":this.onArrowUpKey(e);break;case"Home":this.onHomeKey(e);break;case"End":this.onEndKey(e);break;case"Enter":case"NumpadEnter":this.onEnterKey(e);break;case"Space":this.onSpaceKey(e);break;case"Escape":this.popup&&(v(this.target),this.hide());case"Tab":this.overlayVisible&&this.hide();break}},onArrowDownKey:function(e){var i=this.findNextOptionIndex(this.focusedOptionIndex);this.changeFocusedOptionIndex(i),e.preventDefault()},onArrowUpKey:function(e){if(e.altKey&&this.popup)v(this.target),this.hide(),e.preventDefault();else{var i=this.findPrevOptionIndex(this.focusedOptionIndex);this.changeFocusedOptionIndex(i),e.preventDefault()}},onHomeKey:function(e){this.changeFocusedOptionIndex(0),e.preventDefault()},onEndKey:function(e){this.changeFocusedOptionIndex(y(this.container,'li[data-pc-section="item"][data-p-disabled="false"]').length-1),e.preventDefault()},onEnterKey:function(e){var i=x(this.list,'li[id="'.concat("".concat(this.focusedOptionIndex),'"]')),s=i&&x(i,'a[data-pc-section="itemlink"]');this.popup&&v(this.target),s?s.click():i&&i.click(),e.preventDefault()},onSpaceKey:function(e){this.onEnterKey(e)},findNextOptionIndex:function(e){var i=y(this.container,'li[data-pc-section="item"][data-p-disabled="false"]'),s=A(i).findIndex(function(d){return d.id===e});return s>-1?s+1:0},findPrevOptionIndex:function(e){var i=y(this.container,'li[data-pc-section="item"][data-p-disabled="false"]'),s=A(i).findIndex(function(d){return d.id===e});return s>-1?s-1:0},changeFocusedOptionIndex:function(e){var i=y(this.container,'li[data-pc-section="item"][data-p-disabled="false"]'),s=e>=i.length?i.length-1:e<0?0:e;s>-1&&(this.focusedOptionIndex=i[s].getAttribute("id"))},toggle:function(e,i){this.overlayVisible?this.hide():this.show(e,i)},show:function(e,i){this.overlayVisible=!0,this.target=i??e.currentTarget},hide:function(){this.overlayVisible=!1,this.target=null},onEnter:function(e){N(e,{position:"absolute",top:"0"}),this.alignOverlay(),this.bindOutsideClickListener(),this.bindResizeListener(),this.bindScrollListener(),this.autoZIndex&&I.set("menu",e,this.baseZIndex+this.$primevue.config.zIndex.menu),this.popup&&v(this.list),this.$emit("show")},onLeave:function(){this.unbindOutsideClickListener(),this.unbindResizeListener(),this.unbindScrollListener(),this.$emit("hide")},onAfterLeave:function(e){this.autoZIndex&&I.clear(e)},alignOverlay:function(){H(this.container,this.target);var e=k(this.target);e>k(this.container)&&(this.container.style.minWidth=k(this.target)+"px")},bindOutsideClickListener:function(){var e=this;this.outsideClickListener||(this.outsideClickListener=function(i){var s=e.container&&!e.container.contains(i.target),d=!(e.target&&(e.target===i.target||e.target.contains(i.target)));e.overlayVisible&&s&&d?e.hide():!e.popup&&s&&d&&(e.focusedOptionIndex=-1)},document.addEventListener("click",this.outsideClickListener,!0))},unbindOutsideClickListener:function(){this.outsideClickListener&&(document.removeEventListener("click",this.outsideClickListener,!0),this.outsideClickListener=null)},bindScrollListener:function(){var e=this;this.scrollHandler||(this.scrollHandler=new J(this.target,function(){e.overlayVisible&&e.hide()})),this.scrollHandler.bindScrollListener()},unbindScrollListener:function(){this.scrollHandler&&this.scrollHandler.unbindScrollListener()},bindResizeListener:function(){var e=this;this.resizeListener||(this.resizeListener=function(){e.overlayVisible&&!B()&&e.hide()},window.addEventListener("resize",this.resizeListener))},unbindResizeListener:function(){this.resizeListener&&(window.removeEventListener("resize",this.resizeListener),this.resizeListener=null)},visible:function(e){return typeof e.visible=="function"?e.visible():e.visible!==!1},disabled:function(e){return typeof e.disabled=="function"?e.disabled():e.disabled},label:function(e){return typeof e.label=="function"?e.label():e.label},onOverlayClick:function(e){G.emit("overlay-click",{originalEvent:e,target:this.target})},containerRef:function(e){this.container=e},listRef:function(e){this.list=e}},computed:{focusedOptionId:function(){return this.focusedOptionIndex!==-1?this.focusedOptionIndex:null},dataP:function(){return K({popup:this.popup})}},components:{PVMenuitem:D,Portal:V}},me=["id","data-p"],pe=["id","tabindex","aria-activedescendant","aria-label","aria-labelledby"],fe=["id"];function he(t,e,i,s,d,n){var b=P("PVMenuitem"),p=P("Portal");return a(),h(p,{appendTo:t.appendTo,disabled:!t.popup},{default:M(function(){return[X(Y,r({name:"p-connected-overlay",onEnter:n.onEnter,onLeave:n.onLeave,onAfterLeave:n.onAfterLeave},t.ptm("transition")),{default:M(function(){return[!t.popup||d.overlayVisible?(a(),l("div",r({key:0,ref:n.containerRef,id:t.$id,class:t.cx("root"),onClick:e[3]||(e[3]=function(){return n.onOverlayClick&&n.onOverlayClick.apply(n,arguments)}),"data-p":n.dataP},t.ptmi("root")),[t.$slots.start?(a(),l("div",r({key:0,class:t.cx("start")},t.ptm("start")),[L(t.$slots,"start")],16)):c("",!0),O("ul",r({ref:n.listRef,id:t.$id+"_list",class:t.cx("list"),role:"menu",tabindex:t.tabindex,"aria-activedescendant":d.focused?n.focusedOptionId:void 0,"aria-label":t.ariaLabel,"aria-labelledby":t.ariaLabelledby,onFocus:e[0]||(e[0]=function(){return n.onListFocus&&n.onListFocus.apply(n,arguments)}),onBlur:e[1]||(e[1]=function(){return n.onListBlur&&n.onListBlur.apply(n,arguments)}),onKeydown:e[2]||(e[2]=function(){return n.onListKeyDown&&n.onListKeyDown.apply(n,arguments)})},t.ptm("list")),[(a(!0),l(f,null,S(t.model,function(o,u){return a(),l(f,{key:n.label(o)+u.toString()},[o.items&&n.visible(o)&&!o.separator?(a(),l(f,{key:0},[o.items?(a(),l("li",r({key:0,id:t.$id+"_"+u,class:[t.cx("submenuLabel"),o.class],role:"none"},{ref_for:!0},t.ptm("submenuLabel")),[L(t.$slots,t.$slots.submenulabel?"submenulabel":"submenuheader",{item:o},function(){return[q(T(n.label(o)),1)]})],16,fe)):c("",!0),(a(!0),l(f,null,S(o.items,function(m,g){return a(),l(f,{key:m.label+u+"_"+g},[n.visible(m)&&!m.separator?(a(),h(b,{key:0,id:t.$id+"_"+u+"_"+g,item:m,templates:t.$slots,focusedOptionId:n.focusedOptionId,unstyled:t.unstyled,onItemClick:n.itemClick,onItemMousemove:n.itemMouseMove,pt:t.pt},null,8,["id","item","templates","focusedOptionId","unstyled","onItemClick","onItemMousemove","pt"])):n.visible(m)&&m.separator?(a(),l("li",r({key:"separator"+u+g,class:[t.cx("separator"),o.class],style:m.style,role:"separator"},{ref_for:!0},t.ptm("separator")),null,16)):c("",!0)],64)}),128))],64)):n.visible(o)&&o.separator?(a(),l("li",r({key:"separator"+u.toString(),class:[t.cx("separator"),o.class],style:o.style,role:"separator"},{ref_for:!0},t.ptm("separator")),null,16)):(a(),h(b,{key:n.label(o)+u.toString(),id:t.$id+"_"+u,item:o,index:u,templates:t.$slots,focusedOptionId:n.focusedOptionId,unstyled:t.unstyled,onItemClick:n.itemClick,onItemMousemove:n.itemMouseMove,pt:t.pt},null,8,["id","item","index","templates","focusedOptionId","unstyled","onItemClick","onItemMousemove","pt"]))],64)}),128))],16,pe),t.$slots.end?(a(),l("div",r({key:1,class:t.cx("end")},t.ptm("end")),[L(t.$slots,"end")],16)):c("",!0)],16,me)):c("",!0)]}),_:3},16,["onEnter","onLeave","onAfterLeave"])]}),_:3},8,["appendTo","disabled"])}ce.render=he;export{ce as s};
//# sourceMappingURL=index-Bpk6tSGt.js.map
