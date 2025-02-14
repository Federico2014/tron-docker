package org.tron.trxs;

import java.util.HashMap;
import java.util.Map;
import org.tron.core.net.message.MessageTypes;

public enum TransactionType {
  TRANSFER("transfer"),
  TRANSFER_TRC10("transferTrc10"),
  TRANSFER_TRC20("transferTrc20");

  private final String type;

  TransactionType(String type) {
    this.type = type;
  }

  private static final Map<String, TransactionType> stringToTypeMap = new HashMap<>();

  static {
    for (TransactionType type : TransactionType.values()) {
      stringToTypeMap.put(type.type, type);
    }
  }

  public static TransactionType fromString(String type) {
    return stringToTypeMap.get(type);
  }


  @Override
  public String toString() {
    return this.type;
  }

}
