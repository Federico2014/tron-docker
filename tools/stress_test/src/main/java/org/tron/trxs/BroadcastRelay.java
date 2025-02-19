package org.tron.trxs;

import static org.tron.trxs.BroadcastGenerate.getID;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.api.WalletGrpc;
import org.tron.trident.core.utils.Sha256Hash;
import org.tron.trident.proto.Chain.Transaction;

@Slf4j(topic = "broadcastRelay")
public class BroadcastRelay {

  private volatile boolean isFinishSend = false;
  private ConcurrentLinkedQueue<Transaction> transactionIDs = new ConcurrentLinkedQueue<>();

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private ExecutorService saveTransactionIDPool = Executors
      .newFixedThreadPool(1, r -> new Thread(r, "save-relay-transaction-id"));

  public BroadcastRelay(TransactionConfig config) {
    channelFull = ManagedChannelBuilder.forTarget(config.getUrl())
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  public void saveTransactionID() {
    saveTransactionIDPool.submit(() -> {
      int count = 0;
      try (
          FileWriter writer = new FileWriter("relay-transactionsID.csv");
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

  private void processTransactionID(int count, BufferedWriter bufferedWriter)
      throws InterruptedException, IOException {
    while (!isFinishSend || !transactionIDs.isEmpty()) {
      count++;
      if (transactionIDs.isEmpty()) {
        Thread.sleep(100);
      }

      Transaction transaction = transactionIDs.peek();
      Sha256Hash id = getID(transaction);
      bufferedWriter.write(id.toString());
      bufferedWriter.newLine();
      if (count % 1000 == 0) {
        bufferedWriter.flush();
        log.info("transaction id size: %d", transactionIDs.size());
      }
      transactionIDs.poll();
    }
  }

  public void broadcastTransactions() {
    long trxCount = 0;
    saveTransactionID();
    long startTime = System.currentTimeMillis();
    log.info("Start to process relay transaction broadcast task");
    try (FileInputStream fis = new FileInputStream("relay-transaction.csv")) {
      Transaction transaction;
      int cnt = 0;
      long startTps = System.currentTimeMillis();
      long endTps;
      while ((transaction = Transaction.parseDelimitedFrom(fis)) != null) {
        trxCount++;
        if (cnt > TransactionConfig.getInstance().getTps()) {
          endTps = System.currentTimeMillis();
          if (endTps - startTps < 1000) {
            Thread.sleep(1000 - (endTps - startTps));
          }
          cnt = 0;
          startTps = System.currentTimeMillis();
        } else {
          blockingStubFull.broadcastTransaction(transaction);
          transactionIDs.add(transaction);
          cnt++;
        }
      }

      isFinishSend = true;
      while (!transactionIDs.isEmpty()) {
        Thread.sleep(200);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e ) {
      Thread.currentThread().interrupt();
    }

    long cost = System.currentTimeMillis() - startTime;
    log.info("relay trx size: {}, cost: {}, tps: {}, txid: {}",
        trxCount, cost, 1.0 * trxCount / cost * 1000, transactionIDs.size());
  }
}
