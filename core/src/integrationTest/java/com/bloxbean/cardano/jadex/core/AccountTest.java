package com.bloxbean.cardano.jadex.core;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.jadex.core.util.BigIntegerUtil;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

@Slf4j
public class AccountTest extends JadexBaseTest{

    @Test
    void testGetUtxo() throws Exception{
        var txHash = "deab905631752a6acb199446a65f881bf9cea415893b6aef106a86363d51c4bb";
        var outputIndex = 0;
        var fetched = getBackendService(Networks.testnet()).getUtxoService().getTxOutput(txHash, outputIndex).getValue();
        Assertions.assertEquals(BigInteger.valueOf(9000000), fetched.getAmount().stream().filter(it -> CardanoConstants.LOVELACE.equals(it.getUnit())).findFirst().map(Amount::getQuantity).orElse(null));
    }

    @Test
    void testSerializeKeys(){
        Account sender = getSender(Networks.testnet());
        var paymentKeyHash = sender.hdKeyPair().getPublicKey().getKeyHash();
        var stakeKeyHash = sender.stakeHdKeyPair().getPublicKey().getKeyHash();

        var actual = AddressProvider.getBaseAddress(Credential.fromKey(paymentKeyHash),
                Credential.fromKey(stakeKeyHash), Networks.testnet()).getAddress();

        Assertions.assertEquals(sender.baseAddress(), actual);
    }

    @Test
    void testGetLovelaceBalanceFromUtxos(){
        var network = Networks.testnet();
        Account sender = getSender(network);
        var lovelaceAmountBeforeSwap = getAllUtxos(sender.baseAddress(), CardanoConstants.LOVELACE, getBackendService(network))
                .stream()
                .flatMap(it -> it.getAmount().stream().filter(amt -> org.apache.commons.lang3.StringUtils.equals(TokenUtil.getUnit(null, CardanoConstants.LOVELACE), amt.getUnit())))
                .map(Amount::getQuantity)
                .reduce(BigInteger::add)
                .orElse(BigInteger.ZERO);
        log.debug("lovelaceAmountBeforeSwap: " + lovelaceAmountBeforeSwap);
        Assertions.assertTrue(BigIntegerUtil.isPositive(lovelaceAmountBeforeSwap));
    }
}
