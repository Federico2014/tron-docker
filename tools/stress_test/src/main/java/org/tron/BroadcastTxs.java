package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trxs.BroadcastGen;
import org.tron.trxs.BroadcastRelay;
import org.tron.trxs.TransactionConfig;
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

  @Override
  public Integer call() {
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

    if (config.isBroadcastGen()) {
      BroadcastGen broadcastGen = new BroadcastGen(config);
      broadcastGen.broadcastTransactions();
    }

    if (config.isBroadcastRelay()) {
      BroadcastRelay broadcastRelay = new BroadcastRelay(config);
      broadcastRelay.broadcastTransactions();
    }

    return 0;
  }

}
