package org.tron.trxs;

import com.google.common.collect.Range;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bouncycastle.util.encoders.Hex;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.core.key.KeyPair;
import org.tron.trident.utils.Base58Check;

import static org.tron.utils.Constant.*;

@NoArgsConstructor
public class TransactionConfig {

  private static final TransactionConfig INSTANCE = new TransactionConfig();

  @Setter
  @Getter
  private boolean isGenerate = true;

  @Setter
  @Getter
  public int totalTrxCnt = 1000000;

  @Setter
  @Getter
  public int tps = 1000;

  @Setter
  @Getter
  public String url;

  @Setter
  @Getter
  public boolean produceValidTxs;

  @Setter
  public Map<TransactionType, Range<Integer>> rangeMap = new LinkedHashMap<>();

  @Setter
  @Getter
  private String privateKey;

  @Setter
  @Getter
  private String fromAddress;

  @Setter
  @Getter
  private String toAddress;

  @Setter
  @Getter
  private String trc10Id = "1000001";

  @Setter
  @Getter
  private String trc20ContractAddress = Hex
      .toHexString(Base58Check.base58ToBytes("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"));

  @Setter
  @Getter
  private int transferAmount = 1;

  @Setter
  @Getter
  private int transferTrc10Amount = 1;

  @Setter
  @Getter
  private int transferTrc20Amount = 1;

  @Setter
  @Getter
  private boolean isRelay = false;

  @Setter
  @Getter
  public String relayUrl;

  @Setter
  @Getter
  private int relayStartNumber = 0;

  @Setter
  @Getter
  private int relayEndNumber = 0;

  @Setter
  @Getter
  private boolean broadcastGen = true;

  @Setter
  @Getter
  private boolean broadcastRelay = false;

  public static void initParams(Config config) {
    if (config.hasPath(IS_GENERATE)) {
      INSTANCE.setGenerate(config.getBoolean(IS_GENERATE));
    }

    if (config.hasPath(TOTAL_TXS_CNT) && config.getInt(TOTAL_TXS_CNT) > 0) {
      INSTANCE.setTotalTrxCnt(config.getInt(TOTAL_TXS_CNT));
    } else {
      throw new IllegalArgumentException("no valid totalTxsCnt.");
    }

    if (config.hasPath(TPS) && config.getInt(TPS) > 0) {
      INSTANCE.setTotalTrxCnt(config.getInt(TPS));
    } else {
      throw new IllegalArgumentException("no valid tps.");
    }

    if (config.hasPath(URL)) {
      INSTANCE.setUrl(config.getString(URL));
    } else {
      throw new IllegalArgumentException("no valid tps.");
    }

    if (config.hasPath(PRODUCE_VALID_TXS)) {
      INSTANCE.setProduceValidTxs(config.getBoolean(PRODUCE_VALID_TXS));
    } else {
      throw new IllegalArgumentException("no valid tps.");
    }

    if (!config.hasPath(TRX_TYPE)) {
      throw new IllegalArgumentException("no valid transaction type.");
    }
    INSTANCE.setRangeMap(calculateRanges(config.getConfig(TRX_TYPE)));

    if (!config.hasPath(PRIVATE_KEY) || config.getString(PRIVATE_KEY).length() != 64) {
      throw new IllegalArgumentException("no valid private key.");
    }
    INSTANCE.setPrivateKey(config.getString(PRIVATE_KEY));
    KeyPair keyPair = new KeyPair(INSTANCE.getPrivateKey());
    INSTANCE.setFromAddress(keyPair.toHexAddress());

    if (!config.hasPath(TO_ADDRESS)) {
      throw new IllegalArgumentException("no valid toAddress.");
    }
    INSTANCE.setToAddress(Hex.toHexString(Base58Check.base58ToBytes(config.getString(TO_ADDRESS))));

    if (config.hasPath(TRC10_ID)) {
      INSTANCE.setTrc10Id(config.getString(TRC10_ID));
    }

    if (config.hasPath(TRC20_CONTRACT_ADDRESS)) {
      INSTANCE.setTrc20ContractAddress(
          Hex.toHexString(Base58Check.base58ToBytes(config.getString(TRC20_CONTRACT_ADDRESS))));
    }

    if (config.hasPath(TRANSFER_AMOUNT) && config.getInt(TRANSFER_AMOUNT) > 0) {
      INSTANCE.setTransferAmount(config.getInt(TRANSFER_AMOUNT));
    }

    if (config.hasPath(TRANSFER_TRC10_AMOUNT) && config.getInt(TRANSFER_TRC10_AMOUNT) > 0) {
      INSTANCE.setTransferTrc10Amount(config.getInt(TRANSFER_TRC10_AMOUNT));
    }

    if (config.hasPath(TRANSFER_TRC20_AMOUNT) && config.getInt(TRANSFER_TRC20_AMOUNT) > 0) {
      INSTANCE.setTransferTrc20Amount(config.getInt(TRANSFER_TRC20_AMOUNT));
    }

    if (config.hasPath(RELAY_ENABLE) && config.getBoolean(RELAY_ENABLE)) {
      INSTANCE.setRelay(true);
      if (!config.hasPath(RELAY_URL) || !config.hasPath(RELAY_START_NUMBER) || !config
          .hasPath(RELAY_END_NUMBER)) {
        throw new IllegalArgumentException("the relay parameters are not valid.");
      }

      INSTANCE.setRelay(true);
      INSTANCE.setRelayUrl(config.getString(RELAY_URL));
      INSTANCE.setRelayStartNumber(config.getInt(RELAY_START_NUMBER));
      INSTANCE.setRelayEndNumber(config.getInt(RELAY_END_NUMBER));

      if (INSTANCE.relayStartNumber < 0 || INSTANCE.relayStartNumber > INSTANCE
          .relayEndNumber) {
        throw new IllegalArgumentException("the relay range is not valid.");
      }
    }

    if (config.hasPath(BROADCAST_GEN)) {
      INSTANCE.setBroadcastGen(config.getBoolean(BROADCAST_GEN));
    }

    if (config.hasPath(BROADCAST_RELAY)) {
      INSTANCE.setBroadcastRelay(config.getBoolean(BROADCAST_RELAY));
    }
  }

  public static Map<TransactionType, Range<Integer>> calculateRanges(Config trxType) {
    Map<TransactionType, Range<Integer>> rangeMap = new LinkedHashMap<>();
    int start = 0;
    for (Map.Entry<String, ConfigValue> entry : trxType.entrySet()) {
      int value = trxType.getInt(entry.getKey());
      rangeMap
          .put(TransactionType.fromString(entry.getKey()), Range.closedOpen(start, start + value));
      start += value;
    }
    if (start != 100) {
      throw new IllegalArgumentException("transaction type sum not equals 100.");
    }

    return rangeMap;
  }

  public TransactionType findTransactionType(int number) {
    for (Map.Entry<TransactionType, Range<Integer>> entry : rangeMap.entrySet()) {
      if (entry.getValue().contains(number)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public static TransactionConfig getInstance() {
    return INSTANCE;
  }

}
