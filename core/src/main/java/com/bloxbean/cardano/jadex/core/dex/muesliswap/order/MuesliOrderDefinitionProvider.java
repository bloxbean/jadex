
package com.bloxbean.cardano.jadex.core.dex.muesliswap.order;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.jadex.core.config.DexConfigs;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinitionProvider;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.OrderUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;

/**
 * {@link OrderDefinition OrderDefinition} factory for Muesliswap DEX
 * <p>
 * Since each DEX can have its own order definition, this definition needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
@Slf4j
public class MuesliOrderDefinitionProvider implements OrderDefinitionProvider {
    @Override
    public PlutusData toDatum(OrderDefinition orderDefinition) {
        var allowPartialFill = true;
        if(orderDefinition instanceof MuesliOrderDefinition){
            allowPartialFill = ((MuesliOrderDefinition)orderDefinition).getAllowPartialFill() != null
                        ? ((MuesliOrderDefinition)orderDefinition).getAllowPartialFill()
                        : true;
        }
        try{
            var stakeCredConstr = ConstrPlutusData.builder()
                    .alternative(0)
                    .data(ListPlutusData.of(ConstrPlutusData.builder()
                            .alternative(0)
                            .data(ListPlutusData.of(ConstrPlutusData.builder()
                                    .alternative(0) // key == 0, script == 1
                                    .data(ListPlutusData.of(
                                            BytesPlutusData.of(orderDefinition.getStakeKeyHash())
                                    )).build()
                            )).build())).build();
            var credConstr = ConstrPlutusData.builder()
                    .alternative(0)
                    .data(ListPlutusData.of(
                            ConstrPlutusData.builder()
                                    .alternative(0) // key == 0, script == 1
                                    .data(ListPlutusData.of(
                                            BytesPlutusData.of(orderDefinition.getPaymentKeyHash())
                                    )).build(),
                            stakeCredConstr
                    ))
                    .build();

            return ConstrPlutusData.builder()
                    .alternative(0)
                    .data(ListPlutusData.of(
                            ConstrPlutusData.builder()
                                    .alternative(0)
                                    .data(ListPlutusData.of(credConstr,
                                            StringUtils.isNotBlank(orderDefinition.getAssetOutPolicyId())
                                                    ? BytesPlutusData.deserialize(new ByteString(HexUtil.decodeHexString(orderDefinition.getAssetOutPolicyId())))
                                                    : BytesPlutusData.of(""), // SwapOutTokenPolicyId
                                            StringUtils.isNotBlank(orderDefinition.getAssetOutTokenName()) && !StringUtils.equalsIgnoreCase(orderDefinition.getAssetOutTokenName(), CardanoConstants.LOVELACE)
                                                    ? BytesPlutusData.of(HexUtil.decodeHexString(orderDefinition.getAssetOutTokenName()))
                                                    : BytesPlutusData.of(""), // SwapOutTokenAssetName
                                            StringUtils.isNotBlank(orderDefinition.getAssetInPolicyId())
                                                    ? BytesPlutusData.deserialize(new ByteString(HexUtil.decodeHexString(orderDefinition.getAssetInPolicyId())))
                                                    : BytesPlutusData.of(""), // SwapInTokenPolicyId
                                            StringUtils.isNotBlank(orderDefinition.getAssetInTokenName()) && !StringUtils.equalsIgnoreCase(orderDefinition.getAssetInTokenName(), CardanoConstants.LOVELACE)
                                                    ? BytesPlutusData.of(HexUtil.decodeHexString(orderDefinition.getAssetInTokenName()))
                                                    : BytesPlutusData.of(""), // SwapInTokenAssetName
                                            BigIntPlutusData.of(orderDefinition.getMinimumAmountOut()), // MinReceive
                                            ConstrPlutusData.builder()          // AllowPartialFill
                                                    .alternative(allowPartialFill ? 1 : 0)
                                                    .data(ListPlutusData.of())
                                                    .build(),
                                            BigIntPlutusData.of(BigIntegerUtil.sum(orderDefinition.getReturnLovelace(), orderDefinition.getSwapFee()))
                                    )).build()
                    )).build();
        }catch(Exception e){
            throw new IllegalStateException("Datum creation failed for [" + orderDefinition + "]", e);
        }
    }

    @Override
    public OrderDefinition fromUtxo(Utxo utxo, PlutusData datum){
        try{

            var orderDatum = parseDatum(datum);
            if(orderDatum == null){
                return null;
            }
            var amountIn = determineAmountIn(utxo, orderDatum);
            if(amountIn == null){
                return null;
            }

            var returnLovelace = DexConfigs.MUESLI_V3_CONFIG.outputLovelace();

            return new MuesliOrderDefinition(TokenUtil.getPolicyId(amountIn.getUnit()),
                    TokenUtil.getTokenName(amountIn.getUnit()),
                    amountIn.getQuantity(),
                    StringUtils.trimToNull(orderDatum.assetOutPolicyId()),
                    StringUtils.trimToNull(orderDatum.assetOutTokenName()),
                    orderDatum.minimumAmountOut(),
                    orderDatum.senderStakingKeyHash() != null ? orderDatum.senderStakingKeyHash() : null,
                    orderDatum.senderPubKeyHash() != null ? orderDatum.senderPubKeyHash() : null,
                    returnLovelace,
                    orderDatum.fee().subtract(returnLovelace),
                    orderDatum.allowPartialFill != null ? orderDatum.allowPartialFill : false);
        }catch(Exception e){
            log.debug("Failed to read order definition [" + e + "]");
            throw new IllegalStateException("Failed to read order definition", e);
        }
    }
    private Amount determineAmountIn(Utxo utxo, OrderDatum orderDatum){
        var returnLovelace = DexConfigs.MUESLI_V3_CONFIG.outputLovelace();
        return OrderUtil.getAmountIn(utxo, orderDatum.assetOutPolicyId(), orderDatum.assetOutTokenName(),
                returnLovelace,
                orderDatum.fee().subtract(returnLovelace));
    }

    @Override
    public PlutusData toRedeemerDatum(OrderDefinition orderDefinition) {
        return ConstrPlutusData.builder()
                .alternative(0) // CANCEL_ORDER
                .data(ListPlutusData.of())
                .build();
    }


    private OrderDatum parseDatum(PlutusData datum){
        try{

            long alternative = ((ConstrPlutusData)datum).getAlternative();
            ListPlutusData data = ((ConstrPlutusData)datum).getData();
            var wrapper0 = (ConstrPlutusData) data.getPlutusDataList().get(0);
            var credConstrSource = (ConstrPlutusData) wrapper0.getData().getPlutusDataList().get(0);

            // 1: credConstr
            var paymentConstrSource = (ConstrPlutusData) credConstrSource.getData().getPlutusDataList().get(0);
            var keyType = paymentConstrSource.getAlternative(); // key == 0, script == 1
            var publicKeyData = (BytesPlutusData) paymentConstrSource.getData().getPlutusDataList().get(0);
            var publicKeyHash = publicKeyData.getValue();

            var stakeConstrSourceWrapper = (ConstrPlutusData) credConstrSource.getData().getPlutusDataList().get(1);
            var stakeConstrSource = (ConstrPlutusData) ((ConstrPlutusData) stakeConstrSourceWrapper.getData().getPlutusDataList().get(0)).getData().getPlutusDataList().get(0);
            var stakeKeyType = stakeConstrSource.getAlternative(); // key == 0, script == 1
            var stakePublicKeyData = (BytesPlutusData) stakeConstrSource.getData().getPlutusDataList().get(0);
            var stakePublicKeyHash = stakePublicKeyData.getValue();


            // 2: SwapOutTokenPolicyId
            var swapOutPolicyIdSource = ((BytesPlutusData) wrapper0.getData().getPlutusDataList().get(1)).getValue();
            var swapOutPolicyIdExtracted = HexUtil.encodeHexString(swapOutPolicyIdSource);

            // 3: SwapOutTokenAssetName
            var swapOutAssetNameSource = ((BytesPlutusData) wrapper0.getData().getPlutusDataList().get(2)).getValue();
            var swapOutAssetNameExtracted = HexUtil.encodeHexString(swapOutAssetNameSource);

            // 4: SwapInTokenPolicyId
            var swapInPolicyIdSource = ((BytesPlutusData) wrapper0.getData().getPlutusDataList().get(3)).getValue();
            var swapInPolicyIdExtracted = HexUtil.encodeHexString(swapInPolicyIdSource);

            // 5: SwapInTokenAssetName
            var swapInAssetNameSource = ((BytesPlutusData) wrapper0.getData().getPlutusDataList().get(4)).getValue();
            var swapInAssetNameExtracted = HexUtil.encodeHexString(swapInAssetNameSource);

            // 6: MinReceive
            var minTokenData = (BigIntPlutusData) wrapper0.getData().getPlutusDataList().get(5);
            var minTokenExtracted = minTokenData.getValue();

            // 7: AllowPartialFill
            var allowPartialFillSource = (ConstrPlutusData) wrapper0.getData().getPlutusDataList().get(6);
            var allowPartialFillExtracted = allowPartialFillSource.getAlternative() == 1L;

            // 8: returnLovelace + swapFee
            var feeExtracted = ((BigIntPlutusData) wrapper0.getData().getPlutusDataList().get(7)).getValue();

            return new OrderDatum(StringUtils.trimToNull(swapOutPolicyIdExtracted),
                    StringUtils.isNotBlank(swapOutAssetNameExtracted) ? StringUtils.trimToNull(swapOutAssetNameExtracted) : CardanoConstants.LOVELACE,
                    StringUtils.trimToNull(swapInPolicyIdExtracted),
                    StringUtils.isNotBlank(swapInAssetNameExtracted) ? StringUtils.trimToNull(swapInAssetNameExtracted) : CardanoConstants.LOVELACE,
                    minTokenExtracted,
                    allowPartialFillExtracted,
                    feeExtracted,
                    publicKeyHash,
                    stakePublicKeyHash);
        }catch(Exception e){
            log.debug("Failed to read datum [" + e + "]");
            throw new IllegalStateException("Failed to read datum", e);
        }
    }


    private record OrderDatum(String assetOutPolicyId, String assetOutTokenName,
                              String assetInPolicyId, String assetInTokenName,
                              BigInteger minimumAmountOut,
                              Boolean allowPartialFill,
                              BigInteger fee, byte[] senderPubKeyHash, byte[] senderStakingKeyHash) {
    }
}
