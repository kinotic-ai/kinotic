<script setup>
definePageMeta({ layout: 'home' })
useHead({ title: 'Privacy Policy' })
</script>

<template>
  <section class="bg-[#101010] pt-[100px]">
    <BaseContainer>
      <article class="px-5 py-16 lg:px-[110px] lg:py-24 text-[#AFAFB4]">
        <h1 class="font-[InterBold] text-[44px] leading-[120%] tracking-[-0.02em] text-white sm:text-[52px]">
          Privacy Policy
        </h1>
        <p class="mt-4 text-[14px] text-[#8A8A90]">
          Last updated: April 13, 2026
        </p>

        <div class="mt-10 space-y-8 font-[InterRegular] text-[16px] leading-[160%]">
          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Our Commitment</h2>
            <p class="mt-3">
              At Kinotic, we respect your privacy. <strong class="text-white">We do not sell or share your data with third parties for marketing.</strong> This policy describes the limited information we handle when you visit our website or use our open source software, including the analytics we use to improve the site.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Information We Do Not Collect</h2>
            <p class="mt-3">
              We do not sell, rent, trade, or otherwise share your personal information with third parties for marketing or advertising purposes. We do not build user profiles for resale or external use.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Information You Provide</h2>
            <p class="mt-3">
              If you voluntarily contact us, open an issue on GitHub, or contribute to the project, any information you provide (such as your name, email, or GitHub handle) is used solely to respond to your communication or manage contributions. It is not shared with any third party.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Log and Server Data</h2>
            <p class="mt-3">
              Like most websites, our hosting provider may automatically record basic technical information such as IP addresses and browser types in server logs for security and reliability purposes. This data is not used to identify individual visitors and is not shared.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Analytics</h2>
            <p class="mt-3">
              We use <strong class="text-white">Microsoft Clarity</strong>, a behavioral analytics service provided by Microsoft, to understand how visitors interact with our website so we can improve it. Clarity may collect information such as pages viewed, clicks, scrolling, mouse movement, approximate geographic region, browser and device type, referring URL, and session recordings. Clarity automatically masks sensitive content (such as text in input fields) before it is transmitted, and Microsoft states that Clarity is <strong class="text-white">GDPR and CCPA ready</strong>.
            </p>
            <p class="mt-3">
              We use this information only in aggregate to measure site performance and usage trends. We do not use analytics data to identify individual visitors, and we do not share this data with any third party beyond Microsoft's role as the analytics provider. Microsoft's handling of this data is governed by the
              <a href="https://privacy.microsoft.com/privacystatement" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Microsoft Privacy Statement</a>.
              You can learn more about Clarity at
              <a href="https://clarity.microsoft.com/" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">clarity.microsoft.com</a>.
            </p>
            <p class="mt-3">
              For visitors located in the European Economic Area, United Kingdom, or Switzerland, Clarity runs in <strong class="text-white">cookieless mode by default</strong>: no first- or third-party cookies are set, and each pageview is treated as a new session. We only enable cookies and continuous session recordings if you explicitly accept the consent banner.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Your Rights (GDPR & CCPA)</h2>
            <p class="mt-3">
              Depending on where you live, you may have rights under privacy laws such as the EU General Data Protection Regulation (GDPR) or the California Consumer Privacy Act (CCPA), including the right to access, correct, or delete personal information we hold about you, and the right to opt out of the sale of personal information. <strong class="text-white">We do not sell personal information.</strong>
            </p>
            <p class="mt-3">
              To exercise any of these rights, please contact us through our
              <a href="https://github.com/kinotic-ai/kinotic" class="text-white underline hover:text-[#FF2D7A]">GitHub repository</a>.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Cookies</h2>
            <p class="mt-3">
              Our website does not use advertising cookies. We may use cookies or similar browser storage for essential site functionality and for the analytics described above.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Third-Party Services</h2>
            <p class="mt-3">
              Our website may link to third-party services such as GitHub. Those services have their own privacy policies, and we encourage you to review them. Kinotic does not control and is not responsible for the privacy practices of third-party sites.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Children's Privacy</h2>
            <p class="mt-3">
              Our Services are not directed to children under 13, and we do not knowingly collect personal information from children.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Changes to This Policy</h2>
            <p class="mt-3">
              We may update this Privacy Policy from time to time. We will post any changes on this page with an updated "Last updated" date.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Contact</h2>
            <p class="mt-3">
              If you have questions about this Privacy Policy, please reach out through our
              <a href="https://github.com/kinotic-ai/kinotic" class="text-white underline hover:text-[#FF2D7A]">GitHub repository</a>.
            </p>
          </section>
        </div>
      </article>
    </BaseContainer>
  </section>
</template>
