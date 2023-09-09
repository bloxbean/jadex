
package com.bloxbean.cardano.jadex.core.order.collateral;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.MinAdaCalculator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Default implementation of the {@link CollateralProvider CollateralProvider}
 *
 * @author $stik
 */
@Slf4j
public class DefaultCollateralProvider implements CollateralProvider {
    private static final BigInteger COLLATERAL_AMOUNT = BigInteger.valueOf(5_000_000);
    private final UtxoService utxoService;
    private final EpochService epochService;
    private final BackendService backendService;

    public DefaultCollateralProvider(BackendService backendService) {
        this.utxoService = backendService.getUtxoService();
        this.epochService = backendService.getEpochService();
        this.backendService = backendService;
    }

    @Override
    public Collateral get(String address) {
        try{
            var collected = utxoService.getUtxos(address, 100, 1).getValue(); //Check 1st page 100 utxos
            if(collected == null || collected.isEmpty()){
                return null;
            }

            Optional<Utxo> collateralUtxoOption = collected.stream()
                    .filter(utxo -> utxo.getAmount().size() == 1 //Assumption: 1 Amount means, only LOVELACE
                            && CardanoConstants.LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                            && BigIntegerUtil.equals(COLLATERAL_AMOUNT, utxo.getAmount().get(0).getQuantity()))
                    .findFirst();
            if (collateralUtxoOption.isPresent()) {//Collateral present
                log.info("--- Collateral utxo still there");
                var collateralUtxoHash = collateralUtxoOption.get().getTxHash();
                var collateralIndex = collateralUtxoOption.get().getOutputIndex();
                return new Collateral(collateralUtxoHash, collateralIndex);
            }
            return null;
        }catch(Exception e){
            log.error("Failed to get collateral", e);
            throw new IllegalStateException("Failed to get collateral", e);
        }
    }

    @Override
    public boolean verify(String address, String collateralTxHash) {
        try{
            int page = 1;
            while(true){
                var collected = utxoService.getUtxos(address, 100, page).getValue(); //Check 1st page 100 utxos
                if(collected == null || collected.isEmpty()){
                    break;
                }
                Optional<Utxo> collateralUtxoOption = StringUtils.isNotBlank(collateralTxHash)
                        ? collected.stream()
                            .filter(utxo -> utxo.getTxHash().equals(collateralTxHash))
                            .filter(utxo -> utxo.getAmount().size() == 1 //Assumption: 1 Amount means, only LOVELACE
                                    && CardanoConstants.LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                                    && BigIntegerUtil.equals(COLLATERAL_AMOUNT, utxo.getAmount().get(0).getQuantity()))
                            .findAny()
                        : Optional.empty();
                if (collateralUtxoOption.isPresent()) {//Collateral present
                    log.info("--- Collateral utxo exists");
                    return true;
                }
                page += 1;
            }
            return false;
        }catch(Exception e){
            log.error("Failed to check collateral", e);
            return false;
        }
    }

    @Override
    public Collateral create(Account sender) {
        try{
            String receivingAddress = sender.baseAddress();

            var collateral = get(receivingAddress);
            if(collateral != null){
                return collateral;
            }

            QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
            Tx tx = new Tx()
                    .payToAddress(receivingAddress, Amount.lovelace(COLLATERAL_AMOUNT))
                    .from(receivingAddress);

            Result<String> result = quickTxBuilder.compose(tx)
                    .mergeOutputs(false)
                    .withSigner(SignerProviders.signerFrom(sender))
                    .postBalanceTx((txBuilderContext, transaction) -> {
                        // verify fee is extracted from correct (change) output (could be incorrectly subtracted from collateral output)
                        if(transaction.getBody().getOutputs().stream()
                                .filter(out -> BigIntegerUtil.equals(COLLATERAL_AMOUNT, out.getValue().getCoin())
                                    && (out.getValue().getMultiAssets() == null || out.getValue().getMultiAssets().isEmpty()))
                                .findFirst().isEmpty()){
                            log.debug("Incorrect output encountered in tx builder...");
                            // move
                            var incorrectCollateralOutput = transaction.getBody().getOutputs().stream()
                                    .filter(out -> BigIntegerUtil.equals(COLLATERAL_AMOUNT.subtract(transaction.getBody().getFee()), out.getValue().getCoin())
                                            && (out.getValue().getMultiAssets() == null || out.getValue().getMultiAssets().isEmpty()))
                                    .findFirst();
                            var minAdaCalculator = new MinAdaCalculator(getProtocolParameters());
                            var incorrectChangeOutput = transaction.getBody().getOutputs().stream()
                                    .filter(out -> incorrectCollateralOutput.isPresent() && !incorrectCollateralOutput.get().equals(out))
                                    .filter(out -> out.getValue().getCoin().compareTo(transaction.getBody().getFee().add(minAdaCalculator.calculateMinAda(out))) >= 0)
                                    .findFirst();

                            if(incorrectCollateralOutput.isPresent() && incorrectChangeOutput.isPresent()){
                                log.debug("Before correction: incorrectCollateralOutput = " + incorrectCollateralOutput.get() + ", incorrectChangeOutput = " + incorrectChangeOutput.get());
                                var outputs = new ArrayList<>(transaction.getBody().getOutputs());
                                outputs.remove(incorrectCollateralOutput.get());
                                outputs.remove(incorrectChangeOutput.get());
                                var correctedCollateralOutput = incorrectCollateralOutput.get().toBuilder()
                                        .value(incorrectCollateralOutput.get().getValue().toBuilder()
                                                .coin(incorrectCollateralOutput.get().getValue().getCoin().add(transaction.getBody().getFee()))
                                                .build())
                                        .build();
                                outputs.add(correctedCollateralOutput);
                                var correctedChangeOutput = incorrectChangeOutput.get().toBuilder()
                                        .value(incorrectChangeOutput.get().getValue().toBuilder()
                                                .coin(incorrectChangeOutput.get().getValue().getCoin().subtract(transaction.getBody().getFee()))
                                                .build())
                                        .build();
                                outputs.add(correctedChangeOutput);
                                transaction.getBody().setOutputs(outputs);

                                log.debug("After correction: correctedCollateralOutput = " + correctedCollateralOutput + ", correctedChangeOutput = " + correctedChangeOutput);
                            }
                        }
                    })
                    .completeAndWait();

            if (result.isSuccessful())
                log.debug("Collateral Transaction Id: " + result.getValue());
            else
                log.debug("Collateral Transaction failed: " + result);

            if (result.isSuccessful()) {
                var txId = result.getValue();

                int count = 0;
                while(count <= 20){
                    var collateralTx = backendService.getTransactionService().getTransactionUtxos(txId);
                    if(collateralTx != null && collateralTx.getValue() != null && !collateralTx.getValue().getOutputs().isEmpty()){
                        log.debug("found collateralTx [" + collateralTx + "]");
                        var outputUtxo = collateralTx.getValue().getOutputs()
                                .stream()
                                .filter(utxo -> utxo.getAmount().size() == 1 //Assumption: 1 Amount means, only LOVELACE
                                        && CardanoConstants.LOVELACE.equals(utxo.getAmount().get(0).getUnit())
                                        && BigIntegerUtil.equals(COLLATERAL_AMOUNT, new BigInteger(utxo.getAmount().get(0).getQuantity())))
                                .findFirst();
                        if (outputUtxo.isEmpty()) {
                            log.warn("Invalid collateral UTXO for outputs [" + outputUtxo + "]");
                            throw new IllegalStateException("Invalid collateral UTXO for outputs [" + outputUtxo + "]");
                        }
                        return new Collateral(txId, outputUtxo.get().getOutputIndex());
                    }
                    Thread.sleep(2000);
                    count++;
                }
            }
            throw new IllegalStateException("Tx transfer failed: " + result);
        }catch(Exception e){
            throw new IllegalStateException("failed to transfer fund", e);
        }
    }

    private ProtocolParams getProtocolParameters(){
        try{
            return epochService.getProtocolParameters().getValue();
        }catch(Exception e){
            throw new IllegalStateException("Failed to get protocol parameters", e);
        }
    }

}
