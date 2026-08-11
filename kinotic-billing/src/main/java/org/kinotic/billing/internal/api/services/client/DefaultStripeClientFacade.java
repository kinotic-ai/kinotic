package org.kinotic.billing.internal.api.services.client;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.param.ChargeRetrieveParams;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.billing.api.config.BillingConstants;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultStripeClientFacade implements StripeClientFacade {

    private final StripeClient stripeClient;
    private final Vertx vertx;

    @Override
    public CompletableFuture<StripeChargeDetails> fetchChargeDetails(String stripeChargeId) {
        Validate.notBlank(stripeChargeId, "stripeChargeId cannot be blank");
        // The Stripe SDK is blocking; keep it off the event loop. ordered=false lets
        // concurrent webhook-driven fetches run in parallel.
        return vertx.executeBlocking(() -> {
                        try {
                            Charge charge = stripeClient.charges()
                                                        .retrieve(stripeChargeId,
                                                                  ChargeRetrieveParams.builder()
                                                                                      .addExpand("balance_transaction")
                                                                                      .build());
                            return toDetails(charge);
                        } catch (InvalidRequestException e) {
                            if ("resource_missing".equals(e.getCode())) {
                                return null;
                            }
                            throw e;
                        }
                    }, false)
                    .toCompletionStage()
                    .toCompletableFuture();
    }

    private StripeChargeDetails toDetails(Charge charge) {
        Map<String, String> metadata = charge.getMetadata();
        BalanceTransaction balanceTransaction = charge.getBalanceTransactionObject();
        return new StripeChargeDetails()
                .setChargeId(charge.getId())
                .setPaymentIntentId(charge.getPaymentIntent())
                .setEndUserCustomerId(charge.getCustomer())
                .setAmountTotal(charge.getAmount() != null ? charge.getAmount() : 0L)
                .setCurrency(charge.getCurrency())
                .setLivemode(charge.getLivemode())
                .setOrgId(metadata != null ? metadata.get(BillingConstants.METADATA_ORG_ID) : null)
                .setApplicationId(metadata != null ? metadata.get(BillingConstants.METADATA_APPLICATION_ID) : null)
                .setTenantId(metadata != null ? metadata.get(BillingConstants.METADATA_TENANT_ID) : null)
                .setStripeFeeAmount(balanceTransaction != null ? balanceTransaction.getFee() : null)
                .setTaxAmount(0L);
    }
}
