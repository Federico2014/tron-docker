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
import org.apache.commons.lang3.ArrayUtils;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.db.TronDatabase;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.Protocol.Permission;

import static org.tron.utils.Constant.*;

import org.tron.utils.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "dbfork")
@Command(name = "dbfork", mixinStandardHelpOptions = true, version = "DBFork 1.0",
    description = "Modify the database of java-tron for shadow fork testing.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/dbfork.log"})
public class DBFork implements Callable<Integer> {

  private TronDatabase witnessStore;
  private TronDatabase witnessScheduleStore;
  private TronDatabase accountStore;
  private TronDatabase dynamicPropertiesStore;

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-d", "--database-directory"},
      defaultValue = "output-directory",
      description = "java-tron database directory path. Default: ${DEFAULT-VALUE}")
  private String database;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "fork.conf",
      description = "config the new witnesses, balances, etc for shadow fork."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"--db-engine"},
      defaultValue = "leveldb",
      description = "database engine: leveldb or rocksdb. Default: ${DEFAULT-VALUE}")
  private String dbEngine;

  @CommandLine.Option(names = {"-r", "--retain-witnesses"},
      description = "retain the previous witnesses and active witnesses.")
  private boolean retain;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DBFork()).execute(args);
    System.exit(exitCode);
  }

  private void initStore() {
    witnessStore = new TronDatabase(database, WITNESS_STORE, dbEngine);
    witnessScheduleStore = new TronDatabase(database, WITNESS_SCHEDULE_STORE,
        dbEngine);
    accountStore = new TronDatabase(database, ACCOUNT_STORE, dbEngine);
    dynamicPropertiesStore = new TronDatabase(database, DYNAMIC_PROPERTY_STORE,
        dbEngine);
  }

  private void closeStore() {
    witnessStore.close();
    witnessScheduleStore.close();
    accountStore.close();
    dynamicPropertiesStore.close();
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
      Utils.deleteDir(tmp);
    }

    Config forkConfig;
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      forkConfig = ConfigFactory.parseFile(Paths.get(config).toFile());
    } else {
      throw new IOException("Fork config file [" + config + "] not exist!");
    }

    initStore();

    log.info("Choose the DB engine: {}.", dbEngine);
    spec.commandLine().getOut().format("Choose the DB engine: %s.", dbEngine).println();

    if (!retain) {
      log.info("Erase the previous witnesses and active witnesses.");
      spec.commandLine().getOut().println("Erase the previous witnesses and active witnesses.");
      witnessScheduleStore.delete(ACTIVE_WITNESSES);
      witnessStore.reset();
    } else {
      log.warn("Keep the previous witnesses and active witnesses.");
      spec.commandLine().getOut().println("Keep the previous witnesses and active witnesses.");
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

      List<ByteString> witnessList = new ArrayList<>();
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
            witnessStore.put(address.toByteArray(), witness.getData());
            witnessList.add(witness.getAddress());
          });

      witnessList.sort(Comparator.comparingLong((ByteString b) ->
          new WitnessCapsule(witnessStore.get(b.toByteArray())).getVoteCount())
          .reversed()
          .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
      List<ByteString> activeWitnesses = witnessList.subList(0,
          witnesses.size() >= MAX_ACTIVE_WITNESS_NUM ? MAX_ACTIVE_WITNESS_NUM : witnessList.size());
      witnessScheduleStore.put(ACTIVE_WITNESSES, Utils.getActiveWitness(activeWitnesses));
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
            byte[] value = accountStore.get(address);
            AccountCapsule accountCapsule =
                ArrayUtils.isEmpty(value) ? null : new AccountCapsule(value);
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

            accountStore.put(address, accountCapsule.getData());
          });
      log.info("{} accounts have been modified.", accounts.size());
      spec.commandLine().getOut().format("%d accounts have been modified.", accounts.size())
          .println();
    }

    if (forkConfig.hasPath(LATEST_BLOCK_TIMESTAMP)
        && forkConfig.getLong(LATEST_BLOCK_TIMESTAMP) > 0) {
      long latestBlockHeaderTimestamp = forkConfig.getLong(LATEST_BLOCK_TIMESTAMP);
      dynamicPropertiesStore
          .put(LATEST_BLOCK_HEADER_TIMESTAMP, ByteArray.fromLong(latestBlockHeaderTimestamp));
      log.info("The latest block header timestamp has been modified as {}.",
          latestBlockHeaderTimestamp);
      spec.commandLine().getOut().format("The latest block header timestamp has been modified "
          + "as %d.", latestBlockHeaderTimestamp).println();
    }

    if (forkConfig.hasPath(MAINTENANCE_INTERVAL)
        && forkConfig.getLong(MAINTENANCE_INTERVAL) > 0) {
      long maintenanceTimeInterval = forkConfig.getLong(MAINTENANCE_INTERVAL);
      dynamicPropertiesStore
          .put(MAINTENANCE_TIME_INTERVAL, ByteArray.fromLong(maintenanceTimeInterval));
      log.info("The maintenance time interval has been modified as {}.",
          maintenanceTimeInterval);
      spec.commandLine().getOut().format("The maintenance time interval has been modified as %d.",
          maintenanceTimeInterval).println();
    }

    if (forkConfig.hasPath(NEXT_MAINTENANCE_TIME)
        && forkConfig.getLong(NEXT_MAINTENANCE_TIME) > 0) {
      long nextMaintenanceTime = forkConfig.getLong(NEXT_MAINTENANCE_TIME);
      dynamicPropertiesStore.put(MAINTENANCE_TIME, ByteArray.fromLong(nextMaintenanceTime));
      log.info("The next maintenance time has been modified as {}.",
          nextMaintenanceTime);
      spec.commandLine().getOut().format("The next maintenance time has been modified as %d.",
          nextMaintenanceTime).println();
    }

    closeStore();
    return 0;
  }
}
