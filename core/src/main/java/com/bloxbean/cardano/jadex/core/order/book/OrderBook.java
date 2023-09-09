
package com.bloxbean.cardano.jadex.core.order.book;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.jadex.core.util.TokenUtil;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * contains all open buy and sell order for a certain asset pair (pool)
 * <p>
 * buy orders are orders where assetIn from order definition matches assetB from pool and assetOut from order definition matches assetA
 * sell orders are orders where assetIn from order definition matches assetA from pool and assetOut from order definition matches assetB
 *
 * @author $stik
 */
@Getter
public class OrderBook {
    private final String assetAPolicyId;
    private final String assetATokenName;
    private final String assetBPolicyId;
    private final String assetBTokenName;

    // utxo order - price
    private final List<Tuple<UtxoOrder, BigDecimal>> buyOrder = new ArrayList<>();
    private final List<Tuple<UtxoOrder, BigDecimal>> sellOrder = new ArrayList<>();

    public OrderBook(String assetAPolicyId, String assetATokenName, String assetBPolicyId, String assetBTokenName, List<UtxoOrder> buyOrder, List<UtxoOrder> sellOrder, int decimalsA, int decimalsB) {
        this.assetAPolicyId = assetAPolicyId;
        this.assetATokenName = assetATokenName;
        this.assetBPolicyId = assetBPolicyId;
        this.assetBTokenName = assetBTokenName;
        if(buyOrder != null){
            this.buyOrder.addAll(buyOrder.stream()
                    .map(order -> new Tuple<>(order, order.getPrice(TokenUtil.getUnit(assetAPolicyId, assetATokenName), decimalsA, TokenUtil.getUnit(assetBPolicyId, assetBTokenName), decimalsB)))
                    .map(tuple -> new Tuple<>(tuple._1, tuple._2 != null ? tuple._2._1 : null))
                    .sorted(Comparator.comparing(tuple -> tuple._2, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList());
        }
        if(sellOrder != null){
            this.sellOrder.addAll(sellOrder.stream()
                    .map(order -> new Tuple<>(order, order.getPrice(TokenUtil.getUnit(assetAPolicyId, assetATokenName), decimalsA, TokenUtil.getUnit(assetBPolicyId, assetBTokenName), decimalsB)))
                    .map(tuple -> new Tuple<>(tuple._1, tuple._2 != null ? tuple._2._2 : null))
                    .sorted(Comparator.comparing(tuple -> tuple._2, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList());
        }
    }

    public List<Tuple<UtxoOrder, BigDecimal>> getBuyOrders(String senderAddress){
        var network = new Address(senderAddress).getNetwork();
        return this.buyOrder.stream()
                .filter(it -> StringUtils.isBlank(senderAddress)
                                || StringUtils.equals(it._1.orderDefinition().getSenderAddress(network), senderAddress)
                                || StringUtils.equals(it._1.orderDefinition().getSenderAddress(network), senderAddress))
                .collect(Collectors.toList());
    }
    public List<Tuple<UtxoOrder, BigDecimal>> getSellOrders(String senderAddress){
        var network = new Address(senderAddress).getNetwork();
        return this.sellOrder.stream()
                .filter(it -> StringUtils.isBlank(senderAddress)
                        || StringUtils.equals(it._1.orderDefinition().getSenderAddress(network), senderAddress)
                        || StringUtils.equals(it._1.orderDefinition().getSenderAddress(network), senderAddress))
                .collect(Collectors.toList());
    }
}
