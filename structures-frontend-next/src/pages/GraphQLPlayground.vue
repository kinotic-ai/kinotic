<template>
    <iframe
        ref="iframeRef"
        src="/graphiql.html"
        width="100%"
        height="100%"
        frameborder="0"
    ></iframe>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Cookies from 'js-cookie'
import { USER_STATE } from '@/states/IUserState'
import {createDebug} from '@/util/debug.ts'
const debug = createDebug('graphql-playground')

const iframeRef = ref(null)

function getQueryParam(name) {
    const urlParams = new URLSearchParams(window.location.search)
    return urlParams.get(name)
}

onMounted(() => {
    const namespace = getQueryParam('namespace') || 'default'

    // Determine auth method based on whether we have an OIDC user
    const isOidcAuth = USER_STATE.oidcUser !== null
    const token = isOidcAuth ? Cookies.get('token') : null
    const sessionId = USER_STATE.connectedInfo?.sessionId

    if (!isOidcAuth && !sessionId) {
        debug('No active session for playground')
        return
    }

    iframeRef.value?.addEventListener('load', () => {
        iframeRef.value.contentWindow.postMessage({
                                                      namespace,
                                                      token,      // Will be set for OIDC, null for basic auth
                                                      sessionId   // Will be set for both, used by basic auth
                                                  }, '*')
    })
})
</script>
