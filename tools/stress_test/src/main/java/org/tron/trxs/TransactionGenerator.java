package org.tron.trxs;

import com.google.common.collect.Range;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import java.util.LinkedHashMap;
import java.util.Map;

public class TransactionGenerator {

  public static Map<TransactionType, Range<Integer>> rangeMap = new LinkedHashMap<>();

  public static void calculateRanges(Config trxType) {
    int start = 0;
    for (Map.Entry<String, ConfigValue> entry : trxType.entrySet()) {
      int value = trxType.getInt(entry.getKey());
      rangeMap
          .put(TransactionType.fromString(entry.getKey()), Range.closedOpen(start, start + value));
      start += value;
    }
    if (start != 100) {
      throw new IllegalArgumentException("trx sum not equals 100.");
    }
  }

  public static TransactionType findTransactionType(int number) {
    for (Map.Entry<TransactionType, Range<Integer>> entry : rangeMap.entrySet()) {
      if (entry.getValue().contains(number)) {
        return entry.getKey();
      }
    }
    return null;
  }
}
