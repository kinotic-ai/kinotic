<template>
  <section class="hero-surface overflow-hidden">
    <div class="mx-auto w-full max-w-[1440px] px-5 py-24 lg:px-[110px] lg:py-[130px] xl:min-h-[700px]">
      <div class="grid items-center gap-14 xl:min-h-[440px] xl:grid-cols-[650px_1fr] xl:gap-0">
        <div class="relative z-10 w-full xl:w-[650px]">
          <h1 class="w-full font-[InterBold] text-[44px] leading-[120%] tracking-[-0.02em] text-white sm:text-[52px] xl:text-[61px]">
            Humane Representation of Thought
          </h1>
          <p class="mt-6 w-full font-[InterRegular] text-[17px] leading-[140%] tracking-[0] text-[#AFAFB4] xl:text-[18px]">
              Kinotic is a next-generation Operating System for the Cloud. Just as traditional OSs (like Linux or Windows) abstracted away the complexity of hardware, Kinotic OS abstracts away the complexity of modern cloud-based applications. We believe that by removing the friction of infrastructure, networking, and deployment, we enable AI to rapidly prototype and build enterprise-grade applications that can operate at internet scale.
          </p>

          <div class="mt-8 flex flex-col gap-4 sm:flex-row sm:items-center">
            <NuxtLink
              to="/apps/introduction"
              class="inline-flex h-[44px] items-center justify-center rounded-full bg-[linear-gradient(135deg,#FF2D7A_0%,#FF2A55_100%)] px-6 font-[InterMedium] text-[14px] leading-[120%] text-white shadow-[0_20px_50px_rgba(255,45,122,0.28)] transition hover:scale-[1.02]"
            >
              Get Started
            </NuxtLink>
            <a
              href="https://github.com/kinotic-ai/kinotic"
              target="_blank"
              rel="noreferrer"
              class="inline-flex h-[44px] items-center justify-center rounded-full border border-white px-6 font-[InterMedium] text-[14px] leading-[120%] text-white transition hover:border-white hover:bg-white/5"
            >
              View on Github
            </a>
          </div>
        </div>

        <div class="hidden xl:block"></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero-surface {
  background-color: #171717;
  background-image:
    linear-gradient(90deg, rgba(23, 23, 23, 0.98) 0%, rgba(23, 23, 23, 0.96) 34%, rgba(23, 23, 23, 0.72) 48%, rgba(23, 23, 23, 0.18) 65%, rgba(23, 23, 23, 0) 100%),
    url('/images/Background.svg');
  background-position: center top, center top;
  background-repeat: no-repeat, no-repeat;
  background-size: cover, cover;
  min-height: 740px;
}

@media (max-width: 1279px) {
  .hero-surface {
    min-height: auto;
    background-image:
      linear-gradient(180deg, rgba(23, 23, 23, 0.92) 0%, rgba(23, 23, 23, 0.82) 48%, rgba(23, 23, 23, 0.92) 100%),
      url('/images/Background.svg');
    background-position: center top, 72% top;
    background-size: cover, cover;
    min-height: auto;
  }
}
</style>
