<template>
    <v-container fluid fill-height>
        <v-row justify-center>
            <v-col>
                <v-card :min-height="computedHeight"
                        v-resize.quiet="onResize"
                        flat>
                    <v-expansion-panels>
                        <GrindJob :service-identifier="serviceIdentifier"
                                  :describe-method-identifier="describeMethodIdentifier"
                                  :describe-method-args="['']"
                                  :execute-method-identifier="executeMethodIdentifier"
                                  :execute-method-args="['']">
                        </GrindJob>
                    </v-expansion-panels>
                </v-card>
            </v-col>
        </v-row>
    </v-container>
</template>

<script lang="ts">
    import { Component, Vue } from 'vue-property-decorator'
    import GrindJob from '@/frontends/continuum/components/GrindJob.vue'

    @Component({
        components: { GrindJob }
    })
    export default class GrindTest extends Vue {

        private serviceIdentifier: string = 'org.mindignited.continuum.substratum.api.ProvisioningService'
        private describeMethodIdentifier: string = 'describeProvisionKafka'
        private executeMethodIdentifier: string = 'provisionKafka'

        private computedHeight: number = (window.innerHeight - 225)


        constructor() {
            super()
        }

        // Lifecycle hooks
        public mounted() {

        }

        public beforeDestroy() {
        }

        private onResize() {
            this.computedHeight = (window.innerHeight - 225)
        }

        private hideAlert() {
            (this.$notify as any as { close: (value: string) => void }).close('grindTestAlert')
        }

        private displayAlert(text: string) {
            this.$notify({ group: 'alert', type: 'grindTestAlert', text})
        }

    }
</script>

<style scoped>

</style>
