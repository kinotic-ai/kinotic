<template>
  <div style="width: 100%; height: 100%">
    <div class="glitch">
        <span class="msg">{{message}}</span>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'

@Component({
    components: { }
})
export default class Glitch extends Vue {

    @Prop({ default: '' })
    public message!: string

}
</script>

<style scoped>
    @font-face {
        font-family: 'Michroma';
        font-style: normal;
        font-weight: 400;
        src: local('Michroma'), url(https://fonts.gstatic.com/s/michroma/v10/PN_zRfy9qWD8fEagAPg9pTn5_PDb.woff2) format('woff2');
        unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+2000-206F, U+2074, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
    }
    @keyframes glitch {
        0% {
            transform: skew(-88deg);
        }
        5% {
            transform: skew(-51deg);
        }
        10% {
            transform: skew(73deg);
        }
        15% {
            transform: skew(45deg);
        }
        20% {
            transform: skew(87deg);
        }
        25% {
            transform: skew(-31deg);
        }
        30% {
            transform: skew(12deg);
        }
        35% {
            transform: skew(-87deg);
        }
        40% {
            transform: skew(14deg);
        }
        45% {
            transform: skew(-24deg);
        }
        50% {
            transform: skew(37deg);
        }
        55% {
            transform: skew(54deg);
        }
        60% {
            transform: skew(41deg);
        }
        65% {
            transform: skew(8deg);
        }
        70% {
            transform: skew(-55deg);
        }
        75% {
            transform: skew(36deg);
        }
        80% {
            transform: skew(-53deg);
        }
        85% {
            transform: skew(-37deg);
        }
        90% {
            transform: skew(40deg);
        }
        95% {
            transform: skew(-8deg);
        }
    }
    @keyframes noise {
        0% {
            background-position: 16px -91px;
        }
        10% {
            background-position: -63px 89px;
        }
        20% {
            background-position: -79px -92px;
        }
        30% {
            background-position: 85px 73px;
        }
        40% {
            background-position: 86px -26px;
        }
        50% {
            background-position: 21px -23px;
        }
        60% {
            background-position: -84px -54px;
        }
        70% {
            background-position: 5px 17px;
        }
        80% {
            background-position: 15px -45px;
        }
        90% {
            background-position: -15px -14px;
        }
    }
    @keyframes bar {
        0% {
            height: 283px;
            top: 45%;
            opacity: 0.03;
        }
        10% {
            height: 462px;
            top: 39%;
            opacity: 0.3;
        }
        20% {
            height: 444px;
            top: 67%;
            opacity: 0.37;
        }
        30% {
            height: 481px;
            top: 47%;
            opacity: 0.52;
        }
        40% {
            height: 482px;
            top: 5%;
            opacity: 0.7;
        }
        50% {
            height: 411px;
            top: 100%;
            opacity: 0.42;
        }
        60% {
            height: 243px;
            top: 4%;
            opacity: 0.38;
        }
        70% {
            height: 215px;
            top: 47%;
            opacity: 0.55;
        }
        80% {
            height: 557px;
            top: 79%;
            opacity: 0.19;
        }
        90% {
            height: 43px;
            top: 28%;
            opacity: 0.55;
        }
    }
    .glitch {
        position: absolute;
        left: 0;
        top: 0;
        right: 0;
        bottom: 0;
        color: #fff;
        text-align: center;
        text-transform: uppercase;
        font: 24px "Michroma", helvetica, sans-serif;
        letter-spacing: 0.6em;
        background: url("../assets/whitenoise-361x370_op20.png");
        text-shadow: 2px 0 1px #f6050a, -2px 0 1px #1cf2f2;
        animation: noise 0.3s infinite;
    }
    .glitch:before {
        content: "";
        display: inline-block;
        vertical-align: middle;
        height: 100%;
    }
    .glitch:after {
        content: "";
        display: block;
        position: absolute;
        left: 0;
        right: 0;
        background: url("../assets/whitenoise-361x370_op40.png");
        animation: bar 0.5s infinite;
    }
    .glitch .msg {
        display: inline-block;
        vertical-align: middle;
        position: relative;
        z-index: 1;
        animation: glitch 3s infinite alternate;
    }

</style>
