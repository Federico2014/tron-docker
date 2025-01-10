package org.tron;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.consensus.ConsensusDelegate;
import org.tron.core.ChainBaseManager;
import org.tron.core.Constant;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import picocli.CommandLine;

public class DBForkTest {

  private TronApplicationContext context;
  private Manager manager;
  private ChainBaseManager chainBaseManager;
  private ConsensusDelegate consensusDelegate;

  private static final String WITNESS_KEY = "witnesses";
  private static final String WITNESS_ADDRESS = "address";
  private static final String WITNESS_URL = "url";
  private static final String WITNESS_VOTE = "voteCount";
  private static final String ASSETS_KEY = "assets";
  private static final String ASSETS_ACCOUNT_NAME = "accountName";
  private static final String ASSETS_ACCOUNT_TYPE = "accountType";
  private static final String ASSETS_ADDRESS = "address";
  private static final String ASSETS_BALANCE = "balance";
  private static final String LATEST_BLOCK_TIMESTAMP = "latestBlockHeaderTimestamp";
  private static final String MAINTENANCE_INTERVAL = "maintenanceTimeInterval";
  private static final String NEXT_MAINTENANCE_TIME = "nextMaintenanceTime";

  @Rule
  public final TemporaryFolder folder = new TemporaryFolder();
  private String dbPath;
  private String forkPath;

  public void init() {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TESTNET_CONF);
    context = new TronApplicationContext(DefaultConfig.class);
    manager = context.getBean(Manager.class);
    chainBaseManager = manager.getChainBaseManager();
    consensusDelegate = context.getBean(ConsensusDelegate.class);
  }

  public void shutdown() {
    context.close();
  }

  @Test
  public void testDbFork() throws IOException {
    dbPath = folder.newFolder().toString();
    forkPath = getConfig("fork.conf");

    String[] args = new String[]{"-d",
        dbPath, "-c",
        forkPath};
    CommandLine cli = new CommandLine(new DBFork());
    Assert.assertEquals(0, cli.execute(args));

    init();
    Config forkConfig;
    File file = Paths.get(forkPath).toFile();
    if (file.exists() && file.isFile()) {
      forkConfig = ConfigFactory.parseFile(Paths.get(forkPath).toFile());
    } else {
      throw new IOException("Fork config file [" + forkPath + "] not exist!");
    }

    if (forkConfig.hasPath(WITNESS_KEY)) {
      List<? extends Config> witnesses = forkConfig.getConfigList(WITNESS_KEY);
      if (witnesses.isEmpty()) {
        System.out.println("no witness listed in the config.");
      }
      witnesses = witnesses.stream()
          .filter(c -> c.hasPath(WITNESS_ADDRESS))
          .collect(Collectors.toList());
      if (witnesses.isEmpty()) {
        System.out.println("no witness listed in the config.");
      }

      List<ByteString> witnessAddresses = witnesses.stream().map(
          w -> {
            ByteString address = ByteString.copyFrom(
                Commons.decodeFromBase58Check(w.getString(WITNESS_ADDRESS)));
            return address;
          }
      ).collect(Collectors.toList());
      manager.getWitnessStore().getAllWitnesses().forEach(witnessCapsule -> {
        Assert.assertTrue(witnessAddresses.contains(witnessCapsule.getAddress()));
      });

      witnesses.stream().forEach(
          w -> {
            WitnessCapsule witnessCapsule = chainBaseManager.getWitnessStore().get(
                Commons.decodeFromBase58Check(w.getString(WITNESS_ADDRESS)));
            if (w.hasPath(WITNESS_VOTE)) {
              Assert.assertEquals(w.getLong(WITNESS_VOTE), witnessCapsule.getVoteCount());
            }
            if (w.hasPath(WITNESS_URL)) {
              Assert.assertEquals(w.getString(WITNESS_URL), witnessCapsule.getUrl());
            }
          }
      );
    }

    if (forkConfig.hasPath(ASSETS_KEY)) {
      List<? extends Config> accounts = forkConfig.getConfigList(ASSETS_KEY);
      if (accounts.isEmpty()) {
        System.out.println("no account listed in the config.");
      }
      accounts = accounts.stream()
          .filter(c -> c.hasPath(ASSETS_ADDRESS))
          .collect(Collectors.toList());
      if (accounts.isEmpty()) {
        System.out.println("no account listed in the config.");
      }
      accounts.stream().forEach(
          a -> {
            byte[] address = Commons.decodeFromBase58Check(a.getString(ASSETS_ADDRESS));
            AccountCapsule account = chainBaseManager.getAccountStore().get(address);
            if (a.hasPath(ASSETS_BALANCE)) {
              Assert.assertEquals(a.getLong(ASSETS_BALANCE), account.getBalance());
            }
            if (a.hasPath(ASSETS_ACCOUNT_NAME)) {
              Assert.assertArrayEquals(ByteArray.fromString(a.getString(ASSETS_ACCOUNT_NAME)),
                  account.getAccountName().toByteArray());
            }
            if (a.hasPath(ASSETS_ACCOUNT_TYPE)) {
              Assert.assertEquals(a.getString(ASSETS_ACCOUNT_TYPE), account.getType().toString());
            }
          }
      );
    }

    if (forkConfig.hasPath(LATEST_BLOCK_TIMESTAMP)) {
      long latestBlockHeaderTimestamp = forkConfig.getLong(LATEST_BLOCK_TIMESTAMP);
      Assert.assertEquals(latestBlockHeaderTimestamp,
          chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp());
    }

    if (forkConfig.hasPath(MAINTENANCE_INTERVAL)) {
      long maintenanceTimeInterval = forkConfig.getLong(MAINTENANCE_INTERVAL);
      Assert.assertEquals(maintenanceTimeInterval,
          chainBaseManager.getDynamicPropertiesStore().getMaintenanceTimeInterval());
    }

    if (forkConfig.hasPath(NEXT_MAINTENANCE_TIME)) {
      long nextMaintenanceTime = forkConfig.getLong(NEXT_MAINTENANCE_TIME);
      Assert.assertEquals(nextMaintenanceTime,
          chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime());
    }
    shutdown();
  }

  private static String getConfig(String config) {
    URL path = DBForkTest.class.getClassLoader().getResource(config);
    return path == null ? null : path.getPath();
  }
}

