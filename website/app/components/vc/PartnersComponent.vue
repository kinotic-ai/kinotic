<template>
<section id="partners" class="k-section partnership" aria-labelledby="partners-title">
<div class="k-wrap">
<div class="k-eyebrow">
<span>BUILDING TOGETHER</span>
</div>
<h2 id="partners-title" class="k-heading partnership__title">The partners we’re<br>looking for</h2>
<div class="partnership__layout">
<div class="partnership__copy">
<p>Between us, we’ve built successful software services businesses. Kinotic is our first software product business, and we recognize that building a great platform and building a repeatable path to market require different experience.</p>
<p>We’re looking for investors who want to be actively involved: challenging our assumptions, helping us sharpen our business strategy, and mentoring us as we find our first customers and build market traction. Our commercial plan is still taking shape, and we want partners who bring experience, judgment, and relationships to help us develop and test it.</p>
<p>We bring deep technical and design experience, a history of working together, and the commitment to execute. We’re seeking investors whose involvement goes beyond capital.</p>
</div>
<div class="partnership__graphic">
<svg viewBox="0 0 500 440" role="img" aria-label="Our experience and your guidance join into a shared path toward market traction">
<defs>
<radialGradient id="pathGlow">
<stop stop-color="#25e5a5" stop-opacity=".12"/>
<stop offset="1" stop-color="#25e5a5" stop-opacity="0"/>
</radialGradient>
<pattern id="pathGrid" width="32" height="32" patternUnits="userSpaceOnUse">
<path d="M32 0H0V32" fill="none" stroke="#fff" stroke-opacity=".035"/>
</pattern>
</defs>
<rect width="500" height="440" fill="url(#pathGrid)"/>
<ellipse cx="250" cy="230" rx="240" ry="205" fill="url(#pathGlow)"/>
<g fill="none" stroke-linecap="round" stroke-linejoin="round">
<path d="M95 105V145C95 190 250 165 250 222" stroke="#25e5a5" stroke-width="3"/>
<path d="M405 105V145C405 190 250 165 250 222" stroke="#ef1753" stroke-width="3"/>
<path d="M250 222C250 258 305 252 305 282S220 302 220 334S250 351 250 375" stroke="#25e5a5" stroke-width="3"/>
<path d="M242 366l8 9 8-9" stroke="#25e5a5" stroke-width="3"/>
<circle cx="250" cy="222" r="12" fill="#0b1512" stroke="#25e5a5" stroke-width="2"/>
<circle cx="250" cy="222" r="4" fill="#25e5a5" stroke="none"/>
<circle cx="95" cy="105" r="5" fill="#25e5a5" stroke="none"/>
<circle cx="405" cy="105" r="5" fill="#ef1753" stroke="none"/>
</g>
<g font-family="monospace" font-size="12" letter-spacing="1.3" text-anchor="middle">
<text x="95" y="64" fill="#25e5a5">OUR EXPERIENCE</text>
<text x="95" y="85" fill="#8e9794" font-size="10" letter-spacing=".4">Building &amp; delivering</text>
<text x="405" y="64" fill="#ef527d">YOUR GUIDANCE</text>
<text x="405" y="85" fill="#8e9794" font-size="10" letter-spacing=".4">Strategy &amp; mentorship</text>
<text x="322" y="226" fill="#cbd4d0" font-size="10" text-anchor="start">TOGETHER</text>
<text x="250" y="411" fill="#25e5a5">MARKET TRACTION</text>
</g>
</svg>
</div>
</div>
</div>
</section>
</template>

<style scoped>

.partnership {
  padding:110px 0 100px;
  border-top:1px solid #ffffff14;
  border-bottom:1px solid #ffffff14;
  background:radial-gradient(ellipse at 85% 60%,#0b211b55,transparent 55%)
}


.partnership__title {
  font-size:clamp(38px,4.8vw,66px);
  line-height:1.08;
  letter-spacing:-.035em;
  margin:30px 0 48px;
  max-width:950px
}


.partnership__layout {
  display:grid;
  grid-template-columns:1.25fr 1fr;
  gap:70px;
  align-items:center
}

.partnership__copy {
  font-size:18px;
  line-height:1.8;
  color:#a9abad;
  max-width:690px
}

.partnership__copy p {
  margin:0 0 27px
}


.partnership__copy p:last-child {
  margin-bottom:0
}

.partnership__graphic svg {
  width:100%;
  height:auto
}

.partnership .k-eyebrow {
  color:#96999b
}


@media(max-width:850px) {
  .partnership {
  padding:80px 0
}

.partnership__layout {
  grid-template-columns:1fr;
  gap:36px
}

.partnership__graphic {
  max-width:430px;
  margin:auto
}

.partnership__copy {
  font-size:17px
}


.partnership__title {
  margin-bottom:36px
}


}



#partners  {
   scroll-margin-top: 100px;

}



</style>
