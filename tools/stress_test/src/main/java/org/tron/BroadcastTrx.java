package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.proto.Chain.Block;
import org.tron.trxs.BroadcastGenerate;
import org.tron.trxs.BroadcastRelay;
import org.tron.trxs.TrxConfig;
import org.tron.utils.Statistic;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "broadcast")
@Command(name = "broadcast",
    description = "Broadcast the transactions and compute the TPS.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred, please check logs/stress_test.log"})
public class BroadcastTrx implements Callable<Integer> {

  @CommandLine.Spec
  public static CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for broadcasting transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  private List<ApiWrapper> apiWrapper = new ArrayList<>();

  @Override
  public Integer call() throws IOException, InterruptedException, IllegalException {
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
      System.exit(1);
    }
    TrxConfig.initParams(stressConfig);
    TrxConfig config = TrxConfig.getInstance();

    if (config.getBroadcastUrl().isEmpty()) {
      log.error("no available broadcast url found.");
      spec.commandLine().getErr().println("no available broadcast url found.");
      System.exit(1);
    }
    config.getBroadcastUrl().stream().forEach(
        url -> apiWrapper.add(new ApiWrapper(url, url, config.getPrivateKey()))
    );

    Statistic.setApiWrapper(apiWrapper.get(0));

    if (config.isBroadcastGenerate()) {
      BroadcastGenerate broadcastGenerate = new BroadcastGenerate(config, apiWrapper);
      Block startBlock = apiWrapper.get(0).getNowBlock();
      broadcastGenerate.broadcastTransactions();
      Block endBlock = apiWrapper.get(0).getNowBlock();
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "stress-test-output/broadcast-generate-result");
    }

    if (config.isBroadcastRelay()) {
      BroadcastRelay broadcastRelay = new BroadcastRelay(apiWrapper);
      Block startBlock = apiWrapper.get(0).getNowBlock();
      broadcastRelay.broadcastTransactions();
      Block endBlock = apiWrapper.get(0).getNowBlock();
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "stress-test-output/broadcast-relay-result");
    }

    return 0;
  }
}
