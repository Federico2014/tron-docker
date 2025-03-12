package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.tron.api.GrpcAPI.EmptyMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.net.TronNetService;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import org.tron.protos.Protocol;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.proto.Chain.Block;
import org.tron.trxs.BroadcastGenerate;
import org.tron.trxs.BroadcastRelay;
import org.tron.trxs.TrxConfig;
import org.tron.utils.PublicMethod;
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

  @CommandLine.Option(names = {"-d", "--database-directory"},
      defaultValue = "output-directory",
      description = "java-tron database directory path. Default: ${DEFAULT-VALUE}")
  private String database;

  @CommandLine.Option(names = {"--fn-config"},
      defaultValue = "config.conf",
      description = "configure the parameters for broadcasting transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String fnConfig;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  private List<ApiWrapper> apiWrapper = new ArrayList<>();

  private TronApplicationContext context;
  private Application app;
  private TronNetService tronNetService;

  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull;

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
      logger.error("Stress test config file [" + config + "] not exists!");
      spec.commandLine().getErr()
          .format("Stress test config file [%s] not exists!", config)
          .println();
      System.exit(1);
    }
    TrxConfig.initParams(stressConfig);
    TrxConfig config = TrxConfig.getInstance();

    if (config.getBroadcastUrl().isEmpty()) {
      logger.error("no available broadcast url found.");
      spec.commandLine().getErr().println("no available broadcast url found.");
      System.exit(1);
    }
    config.getBroadcastUrl().stream().forEach(
        url -> apiWrapper.add(new ApiWrapper(url, url, config.getPrivateKey()))
    );

    Statistic.setApiWrapper(apiWrapper.get(0));

    File fnConfigFile = Paths.get(fnConfig).toFile();
    if (!fnConfigFile.exists() || fnConfigFile.isDirectory()) {
      fnConfig = getConfig("config.conf");
    }

    Args.setParam(new String[]{"-d", database}, fnConfig);
    Args.getInstance().setRpcPort(PublicMethod.chooseRandomPort());
    context = new TronApplicationContext(DefaultConfig.class);
    app = ApplicationFactory.create(context);
    app.addService(context.getBean(RpcApiService.class));
    app.startup();

    String fullNode = String.format("%s:%d", "127.0.0.1",
        Args.getInstance().getRpcPort());
    channelFull = ManagedChannelBuilder.forTarget(fullNode)
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    Protocol.Block localBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
    Block targetBlock = apiWrapper.get(0).getNowBlock();

    while (localBlock.getBlockHeader().getRawData().getNumber() < targetBlock.getBlockHeader()
        .getRawData().getNumber()) {
      logger.info("current block num: " + localBlock.getBlockHeader().getRawData().getNumber());
      logger.info("target block num: " + targetBlock.getBlockHeader().getRawData().getNumber());
      Thread.sleep(1000);
      localBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      targetBlock = apiWrapper.get(0).getNowBlock();
    }
    logger.info("full node syncing finished");

    tronNetService = context.getBean(TronNetService.class);
    if (config.isBroadcastGenerate()) {
      BroadcastGenerate broadcastGenerate = new BroadcastGenerate(config, tronNetService);
      Block startBlock = apiWrapper.get(0).getNowBlock();
      broadcastGenerate.broadcastTransactions();
      Block endBlock = apiWrapper.get(0).getNowBlock();
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "stress-test-output/broadcast-generate-result");
    }

    if (config.isBroadcastRelay()) {
      BroadcastRelay broadcastRelay = new BroadcastRelay(tronNetService);
      Block startBlock = apiWrapper.get(0).getNowBlock();
      broadcastRelay.broadcastTransactions();
      Block endBlock = apiWrapper.get(0).getNowBlock();
      long startNumber = startBlock.getBlockHeader().getRawData().getNumber();
      long endNumber = endBlock.getBlockHeader().getRawData().getNumber();
      Statistic.result(startNumber, endNumber, "stress-test-output/broadcast-relay-result");
    }

    shutdown();
    return 0;
  }

  private String getConfig(String config) {
    URL path = BroadcastTrx.class.getClassLoader().getResource(config);
    return path == null ? null : path.getPath();
  }

  private void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    context.close();
  }
}
