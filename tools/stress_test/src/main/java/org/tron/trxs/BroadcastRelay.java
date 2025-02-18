package org.tron.trxs;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.BufferedWriter;
import java.io.File;
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

  private static volatile boolean isFinishSend = false;
  public static long trxCount = 0;
  private static ConcurrentLinkedQueue<Transaction> transactionIDs = new ConcurrentLinkedQueue<>();

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private static ExecutorService saveTransactionIDPool = Executors
      .newFixedThreadPool(1, r -> new Thread(r, "save-relay-transaction-id"));

  public BroadcastRelay(TransactionConfig config) {
    channelFull = ManagedChannelBuilder.forTarget(config.getUrl())
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  public void broadcastTransactions() {
    isFinishSend = false;
    saveTransactionIDPool.submit(() -> {
      BufferedWriter bufferedWriter = null;
      int count = 0;
      try {
        bufferedWriter = new BufferedWriter(
            new FileWriter("relay-transactionsID" + ".csv"));

        while (!isFinishSend) {
          count++;

          if (transactionIDs.isEmpty()) {
            try {
              Thread.sleep(100);
              continue;
            } catch (InterruptedException e) {
              System.out.println(e);
            }
          }

          Transaction transaction = transactionIDs.peek();
          try {
            Sha256Hash id = BroadcastGen.getID(transaction);
            bufferedWriter.write(id.toString());
            bufferedWriter.newLine();
            if (count % 1000 == 0) {
              bufferedWriter.flush();
              System.out.println("transaction id size: " + transactionIDs.size());
            }
            transactionIDs.poll();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (bufferedWriter != null) {
          try {
            bufferedWriter.flush();
            bufferedWriter.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    });

    long startTime = System.currentTimeMillis();
    FileInputStream fis = null;
    log.info("Start to process relay transaction broadcast task");
    try {
      isFinishSend = false;
      File f = new File("relay-transaction" + ".csv");
      fis = new FileInputStream(f);
      Transaction transaction;
//        Integer i = 0;
      int cnt = 0;
      while ((transaction = Transaction.parseDelimitedFrom(fis)) != null) {
        trxCount++;
//          log.info(i++ + "   " + transaction.toString());
        while (true) {
          if (cnt <= 100_000) {
            blockingStubFull.broadcastTransaction(transaction);
            transactionIDs.add(transaction);
            cnt++;
            break;
          } else {
            cnt = 0;
            Thread.sleep(500);
            break;
          }
        }
      }

      int emptyCount = 0;
      while (true) {
        if (transactionIDs.isEmpty()) {
          if (emptyCount == 5) {
            Thread.sleep(200);
            isFinishSend = true;
            break;
          } else {
            emptyCount++;
          }
        } else {
          emptyCount = 0;
        }
        Thread.sleep(200);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        log.info("Finishing processing relay transaction broadcast task");
        fis.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    long cost = System.currentTimeMillis() - startTime;
    log.info("relay trx size: {}, cost: {}, tps: {}, txid: {}",
        trxCount, cost, 1.0 * trxCount / cost * 1000, transactionIDs.size());
  }
}
