package org.tron.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.proto.Response.BlockExtention;

@Slf4j(topic = "statistic")
public class Statistic {

  @Setter
  private static ApiWrapper apiWrapper;

  public static void result(long startBlock, long endBlock, String output) throws IllegalException {
    BlockExtention block;
    long startNumber = 0, endNumber = 0;
    for (long i = startBlock; i < endBlock; i++) {
      block = apiWrapper.getBlockByNum(i);
      if (block.getTransactionsCount() >= 10) {
        startNumber = i;
      }
    }

    for (long i = endBlock; i >= startBlock; i--) {
      block = apiWrapper.getBlockByNum(i);
      if (block.getTransactionsCount() >= 10) {
        endNumber = i;
      }
    }

    if (startNumber < endNumber) {
      log.info("startNumber: {}, endNumber: {}", startNumber, endNumber);
    } else {
      log.error("invalid startNumber: {}, endNumber: {}", startBlock, endNumber);
      return;
    }

    int maxTrxCntInOneBlock = 0;
    int minTrxCntInOneBlock = 0;
    int trxCnt = 0;
    int totalTrxCnt = 0;

    for (long i = startNumber; i < endNumber; i++) {
      block = apiWrapper.getBlockByNum(i);
      trxCnt = block.getTransactionsCount();
      totalTrxCnt += trxCnt;

      if (trxCnt > maxTrxCntInOneBlock) {
        maxTrxCntInOneBlock = trxCnt;
      }
      if (trxCnt < minTrxCntInOneBlock) {
        minTrxCntInOneBlock = trxCnt;
      }
    }

    long expectedTime = (endNumber - startNumber) * 3000;
    BlockExtention startNumberBlock = apiWrapper.getBlockByNum(startNumber);
    BlockExtention endNumberBlock = apiWrapper.getBlockByNum(endNumber);
    long actualTime = endNumberBlock.getBlockHeader().getRawData().getTimestamp() - startNumberBlock
        .getBlockHeader().getRawData().getTimestamp();
    log.info("expectedTime: %d, actual time: %d", expectedTime, actualTime);

    float tps = totalTrxCnt * 1000 / actualTime;
    float missBlockRate = (actualTime - expectedTime) / actualTime;

    log.info("Stress test report:");
    log.info(String.format("total transactions: %d", totalTrxCnt));
    log.info(String.format("cost time: %d minutes", actualTime / (60 * 1000)));
    log.info(String.format("max block size: %d", maxTrxCntInOneBlock));
    log.info(String.format("min block size: %d", minTrxCntInOneBlock));
    log.info(String.format("tps: %f", tps));
    log.info(String.format("miss block rate: ", missBlockRate));

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
      writer.write("Stress test report:");
      writer.newLine();
      writer.write(String.format("total transactions: %d", totalTrxCnt));
      writer.newLine();
      writer.write(String.format("cost time: %d minutes", actualTime / (60 * 1000)));
      writer.newLine();
      writer.write(String.format("max block size: %d", maxTrxCntInOneBlock));
      writer.newLine();
      writer.write(String.format("min block size: %d", minTrxCntInOneBlock));
      writer.newLine();
      writer.write(String.format("tps: %f", tps));
      writer.newLine();
      writer.write(String.format("miss block rate: ", missBlockRate));
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
