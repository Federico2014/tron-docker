package org.tron.trxs;

import com.google.common.collect.Range;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.trident.core.key.KeyPair;
import org.tron.trident.utils.Base58Check;

import static org.tron.utils.Constant.*;

@Slf4j(topic = "trxConfig")
@NoArgsConstructor
public class TrxConfig {

  private static final TrxConfig INSTANCE = new TrxConfig();

  @Setter
  @Getter
  private boolean generateTrx = true;

  @Setter
  @Getter
  private int totalTrxCnt = 1000000;

  @Setter
  @Getter
  private int singleTaskCnt = 1000000;

  @Setter
  @Getter
  private int tps = 1000;

  @Setter
  @Getter
  private Map<TrxType, Range<Integer>> rangeMap = new LinkedHashMap<>();

  @Setter
  @Getter
  private long refBlockNum = 0;

  @Setter
  @Getter
  private String refBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";

  @Setter
  @Getter
  private boolean updateRef = false;

  @Setter
  @Getter
  private String updateRefUrl;

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
  private int trc10Id = 1000001;

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
  private String relayUrl;

  @Setter
  @Getter
  private long relayStartNumber = 0;

  @Setter
  @Getter
  private long relayEndNumber = 0;

  @Setter
  @Getter
  private List<String> broadcastUrl = new ArrayList<>();

  @Setter
  @Getter
  private boolean broadcastGenerate = true;

  @Setter
  @Getter
  private boolean broadcastRelay = false;

  @Setter
  @Getter
  private boolean saveTrxId = true;

  public static void initParams(Config config) {
    if (config.hasPath(GENERATE_TRX)) {
      INSTANCE.setGenerateTrx(config.getBoolean(GENERATE_TRX));
    }

    if (config.hasPath(TOTAL_GENERATE_TRX_CNT)
        && config.getInt(TOTAL_GENERATE_TRX_CNT) > 0) {
      INSTANCE.setTotalTrxCnt(config.getInt(TOTAL_GENERATE_TRX_CNT));
    } else {
      logger.error("totalGenerateTransaction is not valid.");
      throw new IllegalArgumentException("totalGenerateTransaction is not valid");
    }

    if (config.hasPath(SINGLE_TASK_TRX_COUNT)
        && config.getInt(SINGLE_TASK_TRX_COUNT) > 0) {
      INSTANCE.setSingleTaskCnt(config.getInt(SINGLE_TASK_TRX_COUNT));
    } else {
      logger.error("singleTaskTransactionCount is not valid");
      throw new IllegalArgumentException("singleTaskTransactionCount is not valid");
    }

    if (config.hasPath(TPS) && config.getInt(TPS) > 0) {
      INSTANCE.setTps(config.getInt(TPS));
    } else {
      logger.error("tps is not valid");
      throw new IllegalArgumentException("tps is not valid");
    }

    if (!config.hasPath(GENERATE_TRX_TYPE)) {
      logger.error("transaction type is not valid");
      throw new IllegalArgumentException("transaction type is not valid");
    }
    INSTANCE.setRangeMap(calculateRanges(config.getConfig(GENERATE_TRX_TYPE)));

    if (config.hasPath(REF_BLOCK_NUMBER) && config.getLong(REF_BLOCK_NUMBER) > 0) {
      INSTANCE.setRefBlockNum(config.getLong(REF_BLOCK_NUMBER));
    }

    if (config.hasPath(REF_BLOCK_HASH)) {
      INSTANCE.setRefBlockHash(config.getString(REF_BLOCK_HASH));
    }

    if (config.hasPath(UPDATE_REF_URL)) {
      INSTANCE.setUpdateRefUrl(config.getString(UPDATE_REF_URL));
    }

    if (config.hasPath(UPDATE_REF)) {
      INSTANCE.setUpdateRef(config.getBoolean(UPDATE_REF));
    }

    if (!config.hasPath(PRIVATE_KEY) || config.getString(PRIVATE_KEY).length() != 64) {
      logger.error("private key is not valid.");
      throw new IllegalArgumentException("private key is not valid.");
    }
    INSTANCE.setPrivateKey(config.getString(PRIVATE_KEY));
    KeyPair keyPair = new KeyPair(INSTANCE.getPrivateKey());
    INSTANCE.setFromAddress(keyPair.toHexAddress());

    if (!config.hasPath(TO_ADDRESS)) {
      logger.error("toAddress is not valid.");
      throw new IllegalArgumentException("toAddress is not valid.");
    }
    INSTANCE.setToAddress(Hex.toHexString(Base58Check.base58ToBytes(config.getString(TO_ADDRESS))));

    if (config.hasPath(TRC10_ID)) {
      INSTANCE.setTrc10Id(config.getInt(TRC10_ID));
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
        logger.error("the relay parameters are not valid.");
        throw new IllegalArgumentException("the relay parameters are not valid.");
      }

      INSTANCE.setRelay(true);
      INSTANCE.setRelayUrl(config.getString(RELAY_URL));
      INSTANCE.setRelayStartNumber(config.getLong(RELAY_START_NUMBER));
      INSTANCE.setRelayEndNumber(config.getLong(RELAY_END_NUMBER));

      if (INSTANCE.relayStartNumber < 0 || INSTANCE.relayStartNumber > INSTANCE
          .relayEndNumber) {
        logger.error("the relay range is not valid.");
        throw new IllegalArgumentException("the relay range is not valid.");
      }
    }

    if (config.hasPath(BROADCAST_URL)) {
      INSTANCE.setBroadcastUrl(config.getStringList(BROADCAST_URL));
    }

    if (config.hasPath(BROADCAST_GENERATE)) {
      INSTANCE.setBroadcastGenerate(config.getBoolean(BROADCAST_GENERATE));
    }

    if (config.hasPath(BROADCAST_RELAY)) {
      INSTANCE.setBroadcastRelay(config.getBoolean(BROADCAST_RELAY));
    }

    if (config.hasPath(SAVE_TRX_ID)) {
      INSTANCE.setSaveTrxId(config.getBoolean(SAVE_TRX_ID));
    }
  }

  public static Map<TrxType, Range<Integer>> calculateRanges(Config trxType) {
    Map<TrxType, Range<Integer>> rangeMap = new LinkedHashMap<>();
    int start = 0;
    for (Map.Entry<String, ConfigValue> entry : trxType.entrySet()) {
      int value = trxType.getInt(entry.getKey());
      rangeMap
          .put(TrxType.fromString(entry.getKey()), Range.closedOpen(start, start + value));
      start += value;
    }
    if (start != 100) {
      logger.error("transaction type sum not equals 100.");
      throw new IllegalArgumentException("transaction type sum not equals 100.");
    }

    return rangeMap;
  }

  public TrxType findTransactionType(int number) {
    for (Map.Entry<TrxType, Range<Integer>> entry : rangeMap.entrySet()) {
      if (entry.getValue().contains(number)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public static TrxConfig getInstance() {
    return INSTANCE;
  }

}
