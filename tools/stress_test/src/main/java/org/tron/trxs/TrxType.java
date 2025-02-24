package org.tron.trxs;

import java.util.HashMap;
import java.util.Map;

public enum TrxType {
  TRANSFER("transfer"),
  TRANSFER_TRC10("transferTrc10"),
  TRANSFER_TRC20("transferTrc20");

  private final String type;

  TrxType(String type) {
    this.type = type;
  }

  private static final Map<String, TrxType> stringToTypeMap = new HashMap<>();

  static {
    for (TrxType type : TrxType.values()) {
      stringToTypeMap.put(type.type, type);
    }
  }

  public static TrxType fromString(String type) {
    return stringToTypeMap.get(type);
  }

  @Override
  public String toString() {
    return this.type;
  }

}
