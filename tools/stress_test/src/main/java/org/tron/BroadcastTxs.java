package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.api.GrpcAPI.EmptyMessage;
import org.tron.trident.api.WalletGrpc;
import org.tron.trident.proto.Chain.Block;
import org.tron.trxs.BroadcastGenerate;
import org.tron.trxs.BroadcastRelay;
import org.tron.trxs.TransactionConfig;
import org.tron.utils.Statistic;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "broadcast")
@Command(name = "broadcast",
    description = "Broadcast the transactions and compute the TPS.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/stress_test.log"})
public class BroadcastTxs implements Callable<Integer> {

  @CommandLine.Spec
  public static CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for broadcasting transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  @Override
  public Integer call() throws IOException, InterruptedException {
    if (help) {
      spec.commandLine().usage(System.out);
      return 0;
    }

    Config stressConfig = ConfigFactory.load();
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      stressConfig = ConfigFactory.parseFile(Paths.get(config).toFile());
    } else {
      log.error("Stress test config file [" + config + "] not exists!");
      spec.commandLine().getErr()
          .format("Stress test config file [%s] not exists!", config)
          .println();
      System.exit(-1);
    }
    TransactionConfig.initParams(stressConfig);
    TransactionConfig config = TransactionConfig.getInstance();

    channelFull = ManagedChannelBuilder.forTarget(config.getUrl())
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Statistic.setBlockingStubFull(blockingStubFull);

    if (config.isBroadcastGen()) {
      BroadcastGenerate broadcastGenerate = new BroadcastGenerate(config);
      Block startBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      broadcastGenerate.broadcastTransactions();
      Block endBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "gen-broadcast-result.txt");
    }

    if (config.isBroadcastRelay()) {
      BroadcastRelay broadcastRelay = new BroadcastRelay(config);
      Block startBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      broadcastRelay.broadcastTransactions();
      Block endBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "relay-broadcast-result.txt");
    }

    return 0;
  }

}
