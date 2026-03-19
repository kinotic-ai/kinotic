<template>
    <Glitch style="font-size: 10em" message="Not Found" />
</template>

<script lang="ts">
    import { Component, Vue } from 'vue-facing-decorator'
    import Glitch from '@/components/Glitch.vue'

    @Component({
    components: { Glitch }
})
export default class FourOFour extends Vue {

}
</script>

