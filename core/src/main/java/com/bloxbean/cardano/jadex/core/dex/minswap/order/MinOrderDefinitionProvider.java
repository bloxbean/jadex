
package com.bloxbean.cardano.jadex.core.dex.minswap.order;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinition;
import com.bloxbean.cardano.jadex.core.order.definition.OrderDefinitionProvider;
import com.bloxbean.cardano.jadex.core.util.OrderUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.util.Map;

/**
 * {@link OrderDefinition OrderDefinition} factory for Minswap DEX
 * <p>
 * Since each DEX can have its own order definition, this definition needs to be specified in the {@link com.bloxbean.cardano.jadex.core.config.DexConfig DexConfig}
 *
 * @author $stik
 */
@Slf4j
public class MinOrderDefinitionProvider implements OrderDefinitionProvider {
    @Override
    public PlutusData toDatum(OrderDefinition orderDefinition) {
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
                            credConstr,
                            credConstr,
                            ConstrPlutusData.builder()
                                    .alternative(1)
                                    .data(ListPlutusData.of())
                                    .build(),
                            ConstrPlutusData.builder()
                                    .alternative(0) // SWAP_EXACT_IN
                                    .data(ListPlutusData.of(
                                            ConstrPlutusData.builder()
                                                    .alternative(0)
                                                    .data(ListPlutusData.of(
                                                            StringUtils.isNotBlank(orderDefinition.getAssetOutPolicyId())
                                                                    ? BytesPlutusData.deserialize(new ByteString(HexUtil.decodeHexString(orderDefinition.getAssetOutPolicyId())))
                                                                    : BytesPlutusData.of(""),
                                                            StringUtils.isNotBlank(orderDefinition.getAssetOutTokenName()) && !StringUtils.equalsIgnoreCase(orderDefinition.getAssetOutTokenName(), CardanoConstants.LOVELACE)
                                                                    ? BytesPlutusData.of(HexUtil.decodeHexString(orderDefinition.getAssetOutTokenName()))
                                                                    : BytesPlutusData.of("")))
                                                    .build(),
                                            BigIntPlutusData.of(orderDefinition.getMinimumAmountOut())
                                    ))
                                    .build(),
                            BigIntPlutusData.of(orderDefinition.getSwapFee()),
                            BigIntPlutusData.of(orderDefinition.getReturnLovelace())
                    ))
                    .build();
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

            return new MinOrderDefinition(TokenUtil.getPolicyId(amountIn.getUnit()),
                    TokenUtil.getTokenName(amountIn.getUnit()),
                    amountIn.getQuantity(),
                    StringUtils.trimToNull(orderDatum.assetOutPolicyId()),
                    StringUtils.trimToNull(orderDatum.assetOutTokenName()),
                    orderDatum.minimumAmountOut(),
                    orderDatum.senderStakingKeyHash() != null ? orderDatum.senderStakingKeyHash() : null,
                    orderDatum.senderPubKeyHash() != null ? orderDatum.senderPubKeyHash() : null,
                    orderDatum.returnLovelace(),
                    orderDatum.fee(),
                    null);
        }catch(Exception e){
            log.debug("Failed to read order definition [" + e + "]");
            throw new IllegalStateException("Failed to read order definition", e);
        }
    }
    private boolean isLimitOrder(Map<String, String> metadata){
        var text = metadata.get("674");
        return StringUtils.endsWithIgnoreCase(text, "Limit Order");
    }
    private Amount determineAmountIn(Utxo utxo, OrderDatum orderDatum){
        return OrderUtil.getAmountIn(utxo, orderDatum.assetOutPolicyId(), orderDatum.assetOutTokenName(), orderDatum.returnLovelace(), orderDatum.fee());
    }

    @Override
    public PlutusData toRedeemerDatum(OrderDefinition orderDefinition) {
        return ConstrPlutusData.builder()
                .alternative(1) // CANCEL_ORDER
                .data(ListPlutusData.of())
                .build();
    }

    private OrderDatum parseDatum(PlutusData datum){
        try{

            long alternative = ((ConstrPlutusData)datum).getAlternative();
            ListPlutusData data = ((ConstrPlutusData)datum).getData();
            // 1: credConstr
            var credConstrSource = (ConstrPlutusData) data.getPlutusDataList().get(0);
            var paymentConstrSource = (ConstrPlutusData) credConstrSource.getData().getPlutusDataList().get(0);
            var keyType = paymentConstrSource.getAlternative(); // key == 0, script == 1
            var publicKeyData = (BytesPlutusData) paymentConstrSource.getData().getPlutusDataList().get(0);
            var publicKeyHash = publicKeyData.getValue();

            var stakeConstrSourceWrapper = (ConstrPlutusData) credConstrSource.getData().getPlutusDataList().get(1);
            var stakeConstrSource = (ConstrPlutusData) ((ConstrPlutusData) stakeConstrSourceWrapper.getData().getPlutusDataList().get(0)).getData().getPlutusDataList().get(0);
            var stakeKeyType = stakeConstrSource.getAlternative(); // key == 0, script == 1
            var stakePublicKeyData = (BytesPlutusData) stakeConstrSource.getData().getPlutusDataList().get(0);
            var stakePublicKeyHash = stakePublicKeyData.getValue();

            var orderConstrSourceWrapper = (ConstrPlutusData) data.getPlutusDataList().get(3);
            var orderTypeCode = orderConstrSourceWrapper.getAlternative();
            if(orderTypeCode != 0L && orderTypeCode != 1L){
                log.debug("Unsupported order type [" + orderTypeCode + "]");
                return null;
            }

            var orderList = ((ConstrPlutusData) orderConstrSourceWrapper.getData().getPlutusDataList().get(0)).getData().getPlutusDataList();
            var policyIdByte = ((BytesPlutusData)orderList.get(0)).getValue();
            var policyIdExtracted = HexUtil.encodeHexString(policyIdByte);
            var tokenByte = ((BytesPlutusData)orderList.get(1)).getValue();
            var tokenExtracted = HexUtil.encodeHexString(tokenByte);
            var minTokenData = (BigIntPlutusData) orderConstrSourceWrapper.getData().getPlutusDataList().get(1);
            var minTokenExtracted = minTokenData.getValue();

            var swapFeeExtracted = ((BigIntPlutusData) data.getPlutusDataList().get(4)).getValue();
            var returnLovelaceExtracted = ((BigIntPlutusData) data.getPlutusDataList().get(5)).getValue();


            return new OrderDatum(StringUtils.trimToNull(policyIdExtracted),
                    StringUtils.isNotBlank(tokenExtracted) ? StringUtils.trimToNull(tokenExtracted) : CardanoConstants.LOVELACE,
                    minTokenExtracted,
                    swapFeeExtracted,
                    publicKeyHash,
                    stakePublicKeyHash,
                    returnLovelaceExtracted);
        }catch(Exception e){
            log.debug("Failed to read datum [" + e + "]");
            throw new IllegalStateException("Failed to read datum", e);
        }
    }

    private record OrderDatum(String assetOutPolicyId, String assetOutTokenName, BigInteger minimumAmountOut,
                              BigInteger fee, byte[] senderPubKeyHash, byte[] senderStakingKeyHash,
                              BigInteger returnLovelace) {
    }
}
