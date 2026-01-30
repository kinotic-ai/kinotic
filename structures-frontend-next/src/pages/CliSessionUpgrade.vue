<template>
  <div class="flex w-full justify-center items-center h-screen mx-auto">
    <div class="relative w-1/2 h-full bg-gradient-to-br from-[#0A0A0B] from-0% via-[#0A0A0B] via-70% to-[#293A9E] to-100% hidden md:block">
      <img src="@/assets/login-page-symbol-new.svg" class="absolute right-0 bottom-0 h-screen"/>
      <img src="@/assets/login-page-logo-new.svg" class="absolute left-[75px] bottom-[56px] h-[63px] w-auto  xl:h-[63px] lg:h-[52px] md:max-w-[200px] md:h-[42px] sm:max-w-[150px] sm:h-[32px]"/>
    </div>
    
    <div class="w-1/2 h-full flex flex-col justify-center items-center bg-center bg-cover relative">
      <div class="flex flex-col items-center">
        <img src="@/assets/login-page-logo.svg" class="w-[218px] h-[45px] mb-[53px]" />

        <div class="text-center">
          <div class="w-[64px] h-[64px] mx-auto mb-6 flex items-center justify-center bg-green-50 border-2 border-green-200 rounded-full">
            <svg v-if="authenticated" class="w-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <svg v-else class="w-16 h-16 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </div>
          <h2 class="text-4xl font-semibold text-gray-800 mb-4">{{ title }}</h2>
          <p class="text-base text-gray-600">{{ message }}</p>
        </div>

        <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white bg-opacity-75">
          <div class="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-600"></div>
        </div>
      </div>
      
      <div class="flex gap-2 absolute bottom-8 left-0 right-0 justify-center"> 
        <a href="#" class="text-[#0568FD] border-b-1">
          Terms of use
        </a>
        <span class="text-gray-400">
          |
        </span>
        <span class="text-[#0568FD] border-b-1">
          Privacy policy
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-facing-decorator'
import { USER_STATE } from '@/states/IUserState'
import { SESSION_UPGRADE_SERVICE, SessionMetadata } from '@/services/SessionUpgradeService'
import { Continuum } from '@kinotic/continuum-client'

@Component({
  components: {}
})
export default class CliSessionUpgrade extends Vue {
  @Prop({ type: String, required: false, default: null })
  public id!: string | null

  private loading: boolean = false
  private authenticated: boolean = false
  private title: string = 'Authenticating'
  private message: string = 'Connecting CLI to Continuum...'

  public async mounted() {
    const id = this.$route.params.id as string
    
    if (id) {
      if (USER_STATE.connectedInfo?.sessionId) {
        this.loading = true
        try {
          const decodedId = decodeURIComponent(id)
          await SESSION_UPGRADE_SERVICE.upgradeSession(decodedId, new SessionMetadata(USER_STATE.connectedInfo.sessionId, USER_STATE.connectedInfo.replyToId))
          
        } catch (e) {
          this.setStatus('Error connecting CLI to Continuum: ' + e, false, false)
        } finally {
          this.setStatus('You can close this tab and return to your command line.', true, false)
          await Continuum.disconnect(true)
          this.loading = false
        }
      } else {
        this.setStatus('Error connecting CLI to Continuum: No session found.', false, false)
      }
    } else {
      this.setStatus('Error connecting CLI to Continuum: No upgrade id provided.', false, false)
    }
  }

  private setStatus(message: string, authenticated: boolean, loading: boolean) {
    this.title = (loading ? 'Authenticating' : (authenticated ? 'Authentication Successful' : 'Authentication Failed'))
    this.message = message
    this.authenticated = authenticated
    this.loading = loading
  }
}
</script>

