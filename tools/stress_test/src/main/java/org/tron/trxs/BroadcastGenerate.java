package org.tron.trxs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.utils.Sha256Hash;
import org.tron.trident.proto.Chain.Transaction;

@Slf4j(topic = "broadcastGenerate")
public class BroadcastGenerate {

  private Integer dispatchCount;
  private String output = "stress-test-output";

  private volatile boolean isFinishSend = false;
  private ConcurrentLinkedQueue<Transaction> transactionIDs = new ConcurrentLinkedQueue<>();

  private List<ApiWrapper> apiWrapper;

  private static ExecutorService saveTransactionIDPool = Executors
      .newFixedThreadPool(1, r -> new Thread(r, "save-gen-trx-id"));

  private final Random random = new Random(System.currentTimeMillis());

  public BroadcastGenerate(TrxConfig config, List<ApiWrapper> apiWrapper) {
    this.dispatchCount = config.getTotalTrxCnt() / config.getSingleTaskCnt();
    this.apiWrapper = apiWrapper;
  }

  private void processTransactionID(int count, BufferedWriter bufferedWriter)
      throws InterruptedException, IOException {
    while (!isFinishSend || !transactionIDs.isEmpty()) {
      count++;
      if (transactionIDs.isEmpty()) {
        Thread.sleep(100);
        continue;
      }

      Transaction transaction = transactionIDs.peek();
      Sha256Hash id = getID(transaction);
      bufferedWriter.write(id.toString());
      bufferedWriter.newLine();
      if (count % 10000 == 0) {
        bufferedWriter.flush();
        log.info("transaction id size: {}", transactionIDs.size());
      }
      transactionIDs.poll();
    }
  }

  public void broadcastTransactions() throws IOException, InterruptedException {
    long trxCount = 0;
    boolean saveTrxId = TrxConfig.getInstance().isSaveTrxId();
    int totalTask =
        TrxConfig.getInstance().getTotalTrxCnt() % TrxConfig.getInstance().getSingleTaskCnt() == 0
            ? dispatchCount : dispatchCount + 1;
    int apiSize = apiWrapper.size();
    long startTime = System.currentTimeMillis();
    for (int index = 0; index < totalTask; index++) {
      isFinishSend = false;
      if (saveTrxId) {
        int taskIndex = index;
        saveTransactionIDPool.submit(() -> {
          int count = 0;
          try (
              FileWriter writer = new FileWriter(
                  output + File.separator + "broadcast-trxID" + taskIndex + ".csv");
              BufferedWriter bufferedWriter = new BufferedWriter(writer)
          ) {
            processTransactionID(count, bufferedWriter);
          } catch (IOException e) {
            e.printStackTrace();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      }

      log.info("Start to process broadcast generate trx task {}/{}", index + 1, totalTask);
      Transaction transaction;
      int cnt = 0;
      try (FileInputStream fis = new FileInputStream(
          output + File.separator + "generate-trx" + index + ".csv")) {
        long startTps = System.currentTimeMillis();
        long endTps;
        while ((transaction = Transaction.parseDelimitedFrom(fis)) != null) {
          trxCount++;
          if (cnt > TrxConfig.getInstance().getTps()) {
            log.info("broadcast task {}/{} tps has reached: {}", index + 1, totalTask,
                TrxConfig.getInstance().getTps());
            endTps = System.currentTimeMillis();
            if (endTps - startTps < 1000) {
              Thread.sleep(1000 - (endTps - startTps));
            }
            cnt = 0;
            startTps = System.currentTimeMillis();
          } else {
            apiWrapper.get(random.nextInt(apiSize)).broadcastTransaction(transaction);
            if (saveTrxId) {
              transactionIDs.add(transaction);
            }
            cnt++;
          }
        }

        isFinishSend = true;
        while (saveTrxId && !transactionIDs.isEmpty()) {
          Thread.sleep(200);
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw e;
      }
    }

    long cost = System.currentTimeMillis() - startTime;
    log.info("broadcast generate trx size: {}, cost: {}, tps: {}",
        trxCount, cost, 1.0 * trxCount / cost * 1000);
    shutDown(saveTransactionIDPool);
  }

  static void shutDown(ExecutorService saveTransactionIDPool) {
    saveTransactionIDPool.shutdown();
    while (!saveTransactionIDPool.isTerminated()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static Sha256Hash getID(Transaction transaction) {
    return Sha256Hash.of(true, transaction.getRawData().toByteArray());
  }
}
