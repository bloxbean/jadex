
package com.bloxbean.cardano.jadex.core.util;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * Util class for working with {@link Address Addresses}
 *
 * @author $stik
 */
public class AddressUtil {

    /**
     * Retrieve a script hash from an addresses payment credentials
     * @param address the address to parse to script hash
     * @return the script hash generated from provided address
     */
    public static String getScriptHashFromAddress(String address){
        var contractAddress = new Address(address);
        var pc = AddressProvider.getPaymentCredential(contractAddress);
        return HexUtil.encodeHexString(pc.get().getBytes());
    }
}
