package org.tron;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Permission;
import org.tron.utils.FileUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "dbfork")
@Command(name = "dbfork", mixinStandardHelpOptions = true, version = "DBFork 1.0",
    description = "Modify the state data for shadow fork testing.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/dbfork.log"})
public class DBFork implements Callable<Integer> {

  private static final String WITNESS_KEY = "witnesses";
  private static final String WITNESS_ADDRESS = "address";
  private static final String WITNESS_URL = "url";
  private static final String WITNESS_VOTE = "voteCount";
  private static final String ACCOUNTS_KEY = "accounts";
  private static final String ACCOUNT_NAME = "accountName";
  private static final String ACCOUNT_TYPE = "accountType";
  private static final String ACCOUNT_ADDRESS = "address";
  private static final String ACCOUNT_BALANCE = "balance";
  private static final String ACCOUNT_OWNER = "owner";
  private static final String LATEST_BLOCK_TIMESTAMP = "latestBlockHeaderTimestamp";
  private static final String MAINTENANCE_INTERVAL = "maintenanceTimeInterval";
  private static final String NEXT_MAINTENANCE_TIME = "nextMaintenanceTime";
  private static final int MAX_ACTIVE_WITNESS_NUM = 27;

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-d", "--database-directory"},
      defaultValue = "output-directory",
      description = "database directory path. Default: ${DEFAULT-VALUE}")
  private String database;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "fork.conf",
      description = "config the new witnesses, balances, etc for shadow fork."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-r", "--retain-witnesses"},
      description = "retain the previous witnesses and active witnesses.")
  private boolean retain;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DBFork()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    if (help) {
      spec.commandLine().usage(System.out);
      return 0;
    }

    File dbFile = Paths.get(database).toFile();
    if (!dbFile.exists() || !dbFile.isDirectory()) {
      throw new IOException("Database [" + database + "] not exist!");
    }
    File tmp = Paths.get(database, "database", "tmp").toFile();
    if (tmp.exists()) {
      FileUtils.deleteDir(tmp);
    }

    Config forkConfig;
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      forkConfig = ConfigFactory.parseFile(Paths.get(config).toFile());
    } else {
      throw new IOException("Fork config file [" + config + "] not exist!");
    }

    Args.setParam(new String[]{"-d", database}, Constant.TESTNET_CONF);
    TronApplicationContext context = new TronApplicationContext(DefaultConfig.class);
    Manager manager = context.getBean(Manager.class);
    ChainBaseManager chainBaseManager = manager.getChainBaseManager();
    ConsensusDelegate consensusDelegate = context.getBean(ConsensusDelegate.class);

    if (!retain) {
      log.info("Erase the previous witnesses and active witnesses.");
      spec.commandLine().getOut().println("Erase the previous witnesses and active witnesses.");
      manager.getWitnessStore().getAllWitnesses().forEach(witnessCapsule -> {
        manager.getWitnessStore().delete(witnessCapsule.getAddress().toByteArray());
      });
      manager.getWitnessScheduleStore().saveActiveWitnesses(new ArrayList<>());
    }

    if (forkConfig.hasPath(WITNESS_KEY)) {
      List<? extends Config> witnesses = forkConfig.getConfigList(WITNESS_KEY);
      if (witnesses.isEmpty()) {
        spec.commandLine().getOut().println("no witness listed in the config.");
      }
      witnesses = witnesses.stream()
          .filter(c -> c.hasPath(WITNESS_ADDRESS))
          .collect(Collectors.toList());

      if (witnesses.isEmpty()) {
        spec.commandLine().getOut().println("no witness listed in the config.");
      }

      witnesses.stream().forEach(
          w -> {
            ByteString address = ByteString.copyFrom(
                Commons.decodeFromBase58Check(w.getString(WITNESS_ADDRESS)));
            WitnessCapsule witness = new WitnessCapsule(address);
            witness.setIsJobs(true);
            if (w.hasPath(WITNESS_VOTE) && w.getLong(WITNESS_VOTE) > 0) {
              witness.setVoteCount(w.getLong(WITNESS_VOTE));
            }
            if (w.hasPath(WITNESS_URL)) {
              witness.setUrl(w.getString(WITNESS_URL));
            }
            chainBaseManager.getWitnessStore().put(address.toByteArray(), witness);
          });

      List<ByteString> witnessList = new ArrayList<>();
      consensusDelegate.getAllWitnesses().forEach(witnessCapsule -> {
        if (witnessCapsule.getIsJobs()) {
          witnessList.add(witnessCapsule.getAddress());
        }
      });
      witnessList.sort(Comparator.comparingLong((ByteString b) ->
          consensusDelegate.getWitness(b.toByteArray()).getVoteCount())
          .reversed()
          .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
      List<ByteString> activeWitnesses = witnessList.subList(0,
          witnesses.size() >= MAX_ACTIVE_WITNESS_NUM ? MAX_ACTIVE_WITNESS_NUM : witnessList.size());
      consensusDelegate.saveActiveWitnesses(activeWitnesses);
      log.info("{} witnesses and {} active witnesses have been modified.",
          witnesses.size(), activeWitnesses.size());
      spec.commandLine().getOut().format("%d witnesses and %d active witnesses have been modified.",
          witnesses.size(), activeWitnesses.size()).println();
    }

    if (forkConfig.hasPath(ACCOUNTS_KEY)) {
      List<? extends Config> accounts = forkConfig.getConfigList(ACCOUNTS_KEY);
      if (accounts.isEmpty()) {
        spec.commandLine().getOut().println("no account listed in the config.");
      }

      accounts = accounts.stream()
          .filter(c -> c.hasPath(ACCOUNT_ADDRESS))
          .collect(Collectors.toList());

      if (accounts.isEmpty()) {
        spec.commandLine().getOut().println("no account listed in the config.");
      }

      accounts.stream().forEach(
          a -> {
            byte[] address = Commons.decodeFromBase58Check(a.getString(ACCOUNT_ADDRESS));
            AccountCapsule accountCapsule = chainBaseManager.getAccountStore().get(address);
            if (Objects.isNull(accountCapsule)) {
              ByteString byteAddress = ByteString.copyFrom(
                  Commons.decodeFromBase58Check(a.getString(ACCOUNT_ADDRESS)));
              Account account = Account.newBuilder().setAddress(byteAddress).build();
              accountCapsule = new AccountCapsule(account);
            }

            if (a.hasPath(ACCOUNT_BALANCE) && a.getLong(ACCOUNT_BALANCE) > 0) {
              accountCapsule.setBalance(a.getLong(ACCOUNT_BALANCE));
            }
            if (a.hasPath(ACCOUNT_NAME)) {
              accountCapsule.setAccountName(
                  ByteArray.fromString(a.getString(ACCOUNT_NAME)));
            }
            if (a.hasPath(ACCOUNT_TYPE)) {
              accountCapsule.updateAccountType(
                  AccountType.valueOf(a.getString(ACCOUNT_TYPE)));
            }

            if (a.hasPath(ACCOUNT_OWNER)) {
              byte[] owner = Commons.decodeFromBase58Check(a.getString(ACCOUNT_OWNER));
              Permission ownerPermission = AccountCapsule
                  .createDefaultOwnerPermission(ByteString.copyFrom(owner));
              accountCapsule.updatePermissions(ownerPermission, null, null);
            }

            chainBaseManager.getAccountStore().put(address, accountCapsule);
          });
      log.info("{} accounts have been modified.", accounts.size());
      spec.commandLine().getOut().format("%d accounts have been modified.", accounts.size())
          .println();
    }

    if (forkConfig.hasPath(LATEST_BLOCK_TIMESTAMP)
        && forkConfig.getLong(LATEST_BLOCK_TIMESTAMP) > 0) {
      long latestBlockHeaderTimestamp = forkConfig.getLong(LATEST_BLOCK_TIMESTAMP);
      chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
          latestBlockHeaderTimestamp);
      log.info("The latest block header timestamp has been modified as {}.",
          latestBlockHeaderTimestamp);
      spec.commandLine().getOut().format("The latest block header timestamp has been modified "
          + "as %d.", latestBlockHeaderTimestamp).println();
    }

    if (forkConfig.hasPath(MAINTENANCE_INTERVAL)
        && forkConfig.getLong(MAINTENANCE_INTERVAL) > 0) {
      long maintenanceTimeInterval = forkConfig.getLong(MAINTENANCE_INTERVAL);
      chainBaseManager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(
          maintenanceTimeInterval);
      log.info("The maintenance time interval has been modified as {}.",
          maintenanceTimeInterval);
      spec.commandLine().getOut().format("The maintenance time interval has been modified as %d.",
          maintenanceTimeInterval).println();
    }

    if (forkConfig.hasPath(NEXT_MAINTENANCE_TIME)
        && forkConfig.getLong(NEXT_MAINTENANCE_TIME) > 0) {
      long nextMaintenanceTime = forkConfig.getLong(NEXT_MAINTENANCE_TIME);
      chainBaseManager.getDynamicPropertiesStore().saveNextMaintenanceTime(
          nextMaintenanceTime);
      log.info("The next maintenance time has been modified as {}.",
          nextMaintenanceTime);
      spec.commandLine().getOut().format("The next maintenance time has been modified as %d.",
          nextMaintenanceTime).println();
    }

    spec.commandLine().getOut().println("The shadow fork has been completed.");
    log.info("The shadow fork has been completed.");
    manager.close();
    context.close();
    return 0;
  }
}
