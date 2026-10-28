<script setup>
definePageMeta({ layout: 'home', header: false, footer: false })
useHead({ title: 'Privacy Policy' })
</script>

<template>
  <section class="bg-k-bg pt-[100px]">
    <BaseContainer>
      <article class="px-5 py-16 lg:px-[110px] lg:py-24 text-[#AFAFB4]">
        <h1 class="font-[InterBold] text-[44px] leading-[120%] tracking-[-0.02em] text-white sm:text-[52px]">
          Privacy Policy
        </h1>
        <p class="mt-4 text-[14px] text-[#8A8A90]">
          Last updated: October 14, 2026
        </p>

        <div class="mt-10 space-y-8 font-[InterRegular] text-[16px] leading-[160%]">
          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Our Commitment</h2>
            <p class="mt-3">
              At Kinotic, we respect your privacy. <strong class="text-white">We do not sell your data.</strong> This policy describes the limited information we handle when you visit our website, join our cloud waitlist, or use our open source software, including the analytics we use to improve the site.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Information We Do Not Collect</h2>
            <p class="mt-3">
              We do not sell, rent, or trade your personal information. We do not build user profiles for resale. Apart from the analytics and advertising services described below, which receive data only as described there, we do not share your personal information with third parties for marketing or advertising purposes.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Information You Provide</h2>
            <p class="mt-3">
              If you voluntarily contact us, open an issue on GitHub, or contribute to the project, any information you provide (such as your name, email, or GitHub handle) is used solely to respond to your communication or manage contributions. It is not shared with any third party.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Kinotic OS Cloud Waitlist</h2>
            <p class="mt-3">
              When you join the Kinotic OS Cloud waitlist, your email address is sent to Waitlister, the service we use to manage signups and notify you when cloud access opens. We use this information to manage your waitlist subscription and communicate about the cloud release.
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
              We use two analytics services to understand how visitors interact with our website so we can improve it:
            </p>
            <ul class="mt-3 list-disc space-y-2 pl-6">
              <li>
                <strong class="text-white">Microsoft Clarity</strong>, a behavioral analytics service provided by Microsoft. Clarity may collect information such as pages viewed, clicks, scrolling, mouse movement, approximate geographic region, browser and device type, referring URL, and session recordings. Clarity automatically masks sensitive content (such as text in input fields) before it is transmitted, and Microsoft states that Clarity is <strong class="text-white">GDPR and CCPA ready</strong>.
              </li>
              <li>
                <strong class="text-white">Google Analytics</strong>, a web analytics service provided by Google. Google Analytics may collect information such as pages viewed, time on page, scrolling, outbound link clicks, approximate geographic region, browser and device type, and referring URL.
              </li>
            </ul>
            <p class="mt-3">
              When you allow Analytics, we also record when you successfully copy the installation command or
              open the quickstart from the homepage's getting-started panel. These website events help us
              measure interest in installing Kinotic; they do not tell us whether an installation completed.
            </p>
            <p class="mt-3">
              We use this information in aggregate to measure site performance and usage trends, and we do not use it to identify individual visitors. If you allow Marketing cookies, Google may also use the data Google Analytics collects for advertising, including measuring ad performance and personalizing the ads you see on Google and its partners, and Microsoft may connect Clarity data to Microsoft Advertising. Their handling of this data is governed by the
              <a href="https://privacy.microsoft.com/privacystatement" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Microsoft Privacy Statement</a>
              and
              <a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Google's Privacy Policy</a>.
              You can learn more at
              <a href="https://clarity.microsoft.com/" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">clarity.microsoft.com</a>
              and
              <a href="https://marketingplatform.google.com/about/analytics/" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Google Analytics</a>.
            </p>
            <p class="mt-3">
              By default, both services run in <strong class="text-white">cookieless mode</strong> on our site: no first- or third-party cookies are set, and visits are not linked across pageviews. The consent banner lets you choose two categories separately: <strong class="text-white">Analytics</strong> turns on analytics cookies and Clarity's continuous session recordings, and <strong class="text-white">Marketing</strong> turns on advertising cookies and Google's and Microsoft's use of the data for advertising. Each stays off until you turn it on, and you can change your choice at any time with <strong class="text-white">Cookie settings</strong> in the site footer. You can control how Google personalizes ads in <a href="https://myadcenter.google.com/" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Google's My Ad Center</a>.
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
              We may use cookies or similar browser storage for essential site functionality, and for analytics and advertising when you allow those categories in the consent banner.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">Third-Party Services</h2>
            <p class="mt-3">
              Our website may link to third-party services such as GitHub. Those services have their own privacy policies, and we encourage you to review them. Kinotic does not control and is not responsible for the privacy practices of third-party sites.
            </p>
            <p class="mt-3">
              Some pages include videos hosted on YouTube. The player loads from YouTube's privacy-enhanced domain (youtube-nocookie.com) only after you click play, at which point YouTube receives your request and handles it under
              <a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Google's Privacy Policy</a>.
            </p>
            <p class="mt-3">
              Our investor page embeds a presentation hosted on Figma. The embed loads when the page opens, at which point Figma receives your request and handles it under
              <a href="https://www.figma.com/legal/privacy/" target="_blank" rel="noreferrer" class="text-white underline hover:text-[#FF2D7A]">Figma's Privacy Policy</a>.
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
