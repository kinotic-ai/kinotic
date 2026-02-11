var C=Object.defineProperty;var B=(t,e,i)=>e in t?C(t,e,{enumerable:!0,configurable:!0,writable:!0,value:i}):t[e]=i;var r=(t,e,i)=>B(t,typeof e!="symbol"?e+"":e,i);import{s as g}from"./index-Bz44qjX7.js";import{s as $}from"./index-d3RWyEKF.js";import{B as S,d as c,c as w,C as x}from"./component-CLWqUams.js";import{O as D,P as N,R as O,c as f,o as y,X as b,a as l,av as R,_ as k,e as h,k as V,B as L,t as j,aw as q,b as F,w as T}from"./index-BAuc_XpL.js";import{s as U}from"./index-BWJDcOuA.js";import"./index-ChDFEGgs.js";var W=D`
    .p-progressspinner {
        position: relative;
        margin: 0 auto;
        width: 100px;
        height: 100px;
        display: inline-block;
    }

    .p-progressspinner::before {
        content: '';
        display: block;
        padding-top: 100%;
    }

    .p-progressspinner-spin {
        height: 100%;
        transform-origin: center center;
        width: 100%;
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        margin: auto;
        animation: p-progressspinner-rotate 2s linear infinite;
    }

    .p-progressspinner-circle {
        stroke-dasharray: 89, 200;
        stroke-dashoffset: 0;
        stroke: dt('progressspinner.colorOne');
        animation:
            p-progressspinner-dash 1.5s ease-in-out infinite,
            p-progressspinner-color 6s ease-in-out infinite;
        stroke-linecap: round;
    }

    @keyframes p-progressspinner-rotate {
        100% {
            transform: rotate(360deg);
        }
    }
    @keyframes p-progressspinner-dash {
        0% {
            stroke-dasharray: 1, 200;
            stroke-dashoffset: 0;
        }
        50% {
            stroke-dasharray: 89, 200;
            stroke-dashoffset: -35px;
        }
        100% {
            stroke-dasharray: 89, 200;
            stroke-dashoffset: -124px;
        }
    }
    @keyframes p-progressspinner-color {
        100%,
        0% {
            stroke: dt('progressspinner.color.one');
        }
        40% {
            stroke: dt('progressspinner.color.two');
        }
        66% {
            stroke: dt('progressspinner.color.three');
        }
        80%,
        90% {
            stroke: dt('progressspinner.color.four');
        }
    }
`,z={root:"p-progressspinner",spin:"p-progressspinner-spin",circle:"p-progressspinner-circle"},G=N.extend({name:"progressspinner",style:W,classes:z}),M={name:"BaseProgressSpinner",extends:O,props:{strokeWidth:{type:String,default:"2"},fill:{type:String,default:"none"},animationDuration:{type:String,default:"2s"}},style:G,provide:function(){return{$pcProgressSpinner:this,$parentInstance:this}}},E={name:"ProgressSpinner",extends:M,inheritAttrs:!1,computed:{svgStyle:function(){return{"animation-duration":this.animationDuration}}}},X=["fill","stroke-width"];function Z(t,e,i,s,n,d){return y(),f("div",b({class:t.cx("root"),role:"progressbar"},t.ptmi("root")),[(y(),f("svg",b({class:t.cx("spin"),viewBox:"25 25 50 50",style:d.svgStyle},t.ptm("spin")),[l("circle",b({class:t.cx("circle"),cx:"50",cy:"50",r:"20",fill:t.fill,"stroke-width":t.strokeWidth,strokeMiterlimit:"10"},t.ptm("circle")),null,16,X)],16))],16)}E.render=Z;var H=Object.defineProperty,J=Object.getOwnPropertyDescriptor,p=(t,e,i,s)=>{for(var n=s>1?void 0:s?J(e,i):e,d=t.length-1,a;d>=0;d--)(a=t[d])&&(n=(s?a(e,i,n):a(n))||n);return s&&n&&H(e,i,n),n};let o=class extends S{constructor(){super(...arguments);r(this,"crudServiceIdentifier");r(this,"title");r(this,"identity");r(this,"identityLabel");r(this,"identityRules");r(this,"identityEditable");r(this,"showBasicInfoSubheader");r(this,"entity");r(this,"syncedEntity",{id:""});r(this,"crudServiceProxy");r(this,"editing",!1);r(this,"valid",!0);r(this,"loading",!1);r(this,"rulesForIdentity",[])}async mounted(){this.rulesForIdentity=this.identityRules.length>0?this.identityRules:[e=>!!e||`${this.identityLabel} is required`];try{if(this.crudServiceProxy=R.crudServiceProxy(this.crudServiceIdentifier),this.identity!==null){this.editing=!0,this.loading=!0;const e=await this.crudServiceProxy.findById(this.identity);this.syncedEntity=e,this.afterLoad(e),this.loading=!1}}catch(e){this.loading=!1,this.displayAlert(e.message||"Error connecting or loading data")}}close(){this.$router.back()}validateIdentity(){this.valid=this.rulesForIdentity.every(e=>e(this.syncedEntity.id)===!0)}async save(){if(this.validateIdentity(),!!this.valid){this.loading=!0,this.beforeSave();try{this.editing?await this.crudServiceProxy.save(this.syncedEntity):await this.crudServiceProxy.create(this.syncedEntity),this.$router.push({path:"/application",query:{created:"true"}})}catch(e){this.displayAlert(e.message)}this.loading=!1}}beforeSave(){return this.syncedEntity}afterLoad(e){return e}displayAlert(e){this.$toast.add({severity:"error",summary:"Error",detail:e,life:3e3})}};p([c({type:String,required:!0})],o.prototype,"crudServiceIdentifier",2);p([c({type:String,required:!0})],o.prototype,"title",2);p([c({type:String,default:null})],o.prototype,"identity",2);p([c({type:String,default:"Enter your name here"})],o.prototype,"identityLabel",2);p([c({type:Array,default:()=>[]})],o.prototype,"identityRules",2);p([c({type:Boolean,default:!0})],o.prototype,"identityEditable",2);p([c({type:Boolean,default:!0})],o.prototype,"showBasicInfoSubheader",2);p([c({type:Object,default:()=>({id:""})})],o.prototype,"entity",2);p([w()],o.prototype,"beforeSave",1);p([w()],o.prototype,"afterLoad",1);o=p([x({components:{Dialog:U,InputText:g,Button:$,ProgressSpinner:E}})],o);const K="/assets/create-application-image-GdDZajAn.svg",Q={class:"flex justify-between h-screen max-w-[1440px] mx-auto"},Y={class:"pt-6 pl-8 w-1/2 flex flex-col"},ee={class:"flex justify-content-start items-center"},te={class:"mt-[260px] w-4/6 my-auto"},ie={class:"my-[60px]"},se={key:0,class:"p-error block mt-1"},re={class:"mt-4"};function ne(t,e,i,s,n,d){const a=$,v=g;return y(),f("div",Q,[l("div",Y,[l("div",ee,[h(a,{icon:"pi pi-times",class:"p-button-text p-button-plain mr-3",onClick:t.close,"aria-label":"Close"},null,8,["onClick"]),e[2]||(e[2]=l("span",{class:"text-black text-xl"},"Create Application",-1))]),l("div",te,[e[3]||(e[3]=l("h2",{class:"text-3xl"}," Let's begin by choosing a name for your application. ",-1)),l("div",ie,[h(v,{modelValue:t.syncedEntity.id,"onUpdate:modelValue":e[0]||(e[0]=u=>t.syncedEntity.id=u),disabled:!t.identityEditable,placeholder:t.identityLabel,class:L(["w-full max-w-[540px] h-[56px]",{"p-invalid":!t.valid&&t.rulesForIdentity.length>0}]),onInput:e[1]||(e[1]=u=>t.validateIdentity())},null,8,["modelValue","disabled","placeholder","class"]),!t.valid&&t.rulesForIdentity.length>0?(y(),f("small",se,j(t.identityLabel)+" is required ",1)):V("",!0)]),l("div",re,[h(a,{label:"Create",class:"rounded-[10px] max-h-[56px] !py-[18px] !w-4/6 !text-base",onClick:t.save},null,8,["onClick"])])])]),e[4]||(e[4]=l("div",{class:"w-1/2 h-full relative"},[l("img",{src:K,alt:"Create Application Image",class:"absolute left-0 top-0 max-h-[100vh]"})],-1))])}const A=k(o,[["render",ne]]);class _{static checkNameAndNamespace(e,i){let s="";return e==null?s=`${i} must contain a valid value`:e.length===0?s="This field is required":e.length>=255?s=`${i} must be less than 255 characters`:e.charAt(0)==="_"?s=`${i} must not start with _`:e.charAt(0)==="-"?s=`${i} must not start with -`:e.charAt(0)==="+"?s=`${i} must not start with +`:e.charAt(0)==="."?s=`${i} must not start with .`:this.illegalStructureNameChars.test(e)&&(s=`${i} must not contain these characters .. \\ / * ?  < > | , # : ; + = ( ) { } or spaces`),s}}r(_,"illegalStructureNameChars",new RegExp(/[.][.]|[\\][\\]|[/]|[*]|[?]|[\\]|<|>|[|]|[ ]|[,]|[#]|[:]|[;]|[+]|[=]|[(]|[)]|[{]|[}]/));var oe=Object.defineProperty,ae=Object.getOwnPropertyDescriptor,I=(t,e,i,s)=>{for(var n=s>1?void 0:s?ae(e,i):e,d=t.length-1,a;d>=0;d--)(a=t[d])&&(n=(s?a(e,i,n):a(n))||n);return s&&n&&oe(e,i,n),n};let m=class extends S{constructor(){super(...arguments);r(this,"id");r(this,"crudServiceIdentifier","org.mindignited.structures.api.services.ApplicationService");r(this,"application",new q("",""));r(this,"applicationRules",[i=>!!i||"Name is required",i=>{const s=_.checkNameAndNamespace(i,"Name");return s.length===0?!0:s}])}handleEntityUpdate(i){this.application=i}};I([c({type:String,required:!1,default:null})],m.prototype,"id",2);m=I([x({components:{CrudEntityAddEdit:A,InputText:g}})],m);const le={class:"mb-4"};function de(t,e,i,s,n,d){const a=g,v=A;return y(),F(v,{"crud-service-identifier":t.crudServiceIdentifier,title:"Application",identity:t.id,identityRules:t.applicationRules,entity:t.application,"update:entity":"handleEntityUpdate"},{"basic-info":T(({entity:u})=>[l("div",le,[e[0]||(e[0]=l("label",{for:"description",class:"block text-sm font-medium text-gray-700 mb-1"},"Description",-1)),h(a,{id:"description",modelValue:u.description,"onUpdate:modelValue":P=>u.description=P,class:"w-full p-2 border rounded-m",placeholder:"Enter description"},null,8,["modelValue","onUpdate:modelValue"])])]),_:1},8,["crud-service-identifier","identity","identityRules","entity"])}const ve=k(m,[["render",de]]);export{ve as default};
//# sourceMappingURL=ApplicationAddEdit-BukXKCS_.js.map
