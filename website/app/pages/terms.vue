<script setup>
definePageMeta({ layout: 'home', header: false, footer: false })
useHead({ title: 'Terms of Service' })
</script>

<template>
  <section class="bg-[#101010] pt-[100px]">
    <BaseContainer>
      <article class="px-5 py-16 lg:px-[110px] lg:py-24 text-[#AFAFB4]">
        <h1 class="font-[InterBold] text-[44px] leading-[120%] tracking-[-0.02em] text-white sm:text-[52px]">
          Terms of Service
        </h1>
        <p class="mt-4 text-[14px] text-[#8A8A90]">
          Last updated: April 13, 2026
        </p>

        <div class="mt-10 space-y-8 font-[InterRegular] text-[16px] leading-[160%]">
          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">1. Acceptance of Terms</h2>
            <p class="mt-3">
              By accessing or using the Kinotic website, software, documentation, or any related services (collectively, the "Services"), you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the Services.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">2. Use of the Services</h2>
            <p class="mt-3">
              Kinotic grants you a non-exclusive, non-transferable, revocable license to use the Services in accordance with these terms and any applicable open source licenses. You agree not to misuse the Services, attempt to disrupt them, or use them for any unlawful purpose.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">3. Open Source Software</h2>
            <p class="mt-3">
              Kinotic is distributed under open source licenses published in our source repositories. Your use, modification, and distribution of the source code is governed by the applicable license terms included with the software.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">4. Intellectual Property</h2>
            <p class="mt-3">
              All content on this website that is not covered by an open source license, including trademarks, logos, and branding, remains the property of Kinotic and its contributors. You may not use our trademarks without prior written permission.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">5. Disclaimer of Warranties</h2>
            <p class="mt-3">
              The Services are provided "as is" and "as available" without warranties of any kind, either express or implied. Kinotic disclaims all warranties, including but not limited to merchantability, fitness for a particular purpose, and non-infringement.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">6. Limitation of Liability</h2>
            <p class="mt-3">
              To the maximum extent permitted by law, Kinotic and its contributors shall not be liable for any indirect, incidental, special, consequential, or punitive damages arising out of or relating to your use of the Services.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">7. Changes to These Terms</h2>
            <p class="mt-3">
              We may update these Terms of Service from time to time. Continued use of the Services after any changes constitutes acceptance of the updated terms.
            </p>
          </section>

          <section>
            <h2 class="font-[InterBold] text-[22px] text-white">8. Contact</h2>
            <p class="mt-3">
              For questions about these Terms of Service, please reach out through our
              <a href="https://github.com/kinotic-ai/kinotic" class="text-white underline hover:text-[#FF2D7A]">GitHub repository</a>.
            </p>
          </section>
        </div>
      </article>
    </BaseContainer>
  </section>
</template>
