# Stress Test Tool
The stress testing tool is designed to evaluate the performance of the java-tron fullnode.
It can generate a large volume of transactions, store them locally, and broadcast them to the test network.
Finally, it provides a TPS (Transactions Per Second) report as the test result.

## Configure
To use the stress test tool, you need to configure the `stress.conf` firstly. Here is an example:
```
generateTrx = true
totalGenerateTrxCnt = 45000
singleTaskTrxCount = 10000

generateTrxType = {
  transfer = 60
  transferTrc10 = 10
  transferTrc20 = 30
}
refBlockNum = 68566000
refBlockHash = "0000000004163bf01498f2216a881e5202db86ce00f9cd4780ed527b7280b71e"
updateRef = true
updateRefUrl = "127.0.0.1:50051"

privateKey = "aab926e86a17f0f46b4d22e61725edd5770a5b0fbdabb04b0f46ee499b1e34f3"
toAddress = "TGwRKiEwVXqYWsHtpvhxZR2jaVZNK1BLEf"

trc10Id = 1000001
trc20ContractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
transferAmount = 1
transferTrc10Amount = 1
transferTrc20Amount = 1

relayTrx = {
  enable = false
  url = "grpc.trongrid.io:50051"
  startBlockNumber = 59720000
  endBlockNumber = 59720500
}

broadcastGenerateTrx = true
broadcastRelayTrx = false
broadcastUrl = "127.0.0.1:50051"
tps = 1000
saveTrxId = true
```
Here is the introduction for the configuration options:

`generateTrx`: configure whether to generate the transactions;

`totalGenerateTrxCnt`: configure the total generated transactions count;

`singleTaskTrxCount`: configure the transaction count for single task;

`generateTrxType`: configure the generated transaction type and proportion. Currently, the supported transaction type
including `transfer`, `transferTrc10`, `transferTrc20`. The sum of all transaction type proportion must equal 100;

`refBlockNum`：configure the reference block number used to build the transactions;

`refBlockNum`: configure the reference block hash used to build the transactions;

`updateRef`: configure whether to update the `refBlockNum` and `refBlockNum`;

`updateRefUrl`: configure the url when needing to update the `refBlockNum` and `refBlockNum`;

`privateKey`: configure the private key used to sign the transactions;

`toAddress`: configure the receiver address use to build the transactions;

`trc10Id`: configure the TRC10 id used to build the `transferTrc10` transactions;

`trc20ContractAddress`: configure the TRC20 contract address used to build the `transferTrc20` transactions;

`transferAmount`: configure the transfer amount used to build the `transfer` transactions;

`transferTrc10Amount`: configure the transfer TRC10 amount used to build the `transferTrc10` transactions;

`transferTrc20Amount`: configure the transfer TRC20 amount used to build the `transferTrc20` transactions;

`relayTrx.enable`: configure whether to relay the transactions from other network and save them locally.

`relayTrx.url`: configure the url to indicate which network the relayed transactions come from;

`relayTrx.startBlockNumber`: configure the start block number of range for the relayed transactions;

`relayTrx.endBlockNumber`: configure the end block number of range for the relayed transactions;

`broadcastGenerateTrx`: configure whether to broadcast the generated the transactions;

`broadcastRelayTrx`: configure whether to broadcast the relayed transactions;

`broadcastUrl`: configure the broadcast url;

`tps`: configure the TPS for broadcasting transactions;

`saveTrxId`: configure whether to save the transaction id of the broadcast transactions.

*Note*: you can use the [dbfork](../dbfork/README.md) tool to get enough `TRX/TRC10/TRC20` balances of address corresponding
to the `privateKey` for the stress test.


## Generate
To generate the transactions, you need to set `generateTrx = true` and configure other options according you demands.
Then execute the following `generate` commands:
```shell script
# clone the tron-docker
git clone https://github.com/tronprotocol/tron-docker.git
# enter the directory
cd tron-docker/tools/gradlew
# compile the stress test tool
./gradlew :stress-test:build
# execute full command
nohup java -jar ../stress_test/build/libs/stresstest.jar generate -c /path/to/stress.conf >> start.log 2>&1 &
# check the log
tail -f logs/stress_test.log
```
The generated transactions will be stored in the `generate-trx*.csv` files in current `stress-test-output` directory.

## Broadcast
To broadcast the transactions, you need to set `broadcastGenerateTrx = true` and configure other options according you demands.
Then execute the following `broadcast` command:

```
# execute full command
nohup java -jar ../stress_test/build/libs/stresstest.jar broadcast -c /path/to/stress.conf >> start.log 2>&1 &
# check the log
tail -f logs/stress_test.log
```
If you set `saveTrxId = true`, the broadcast transactions ids will be stored
in the `broadcast-trxID*.csv` files in current `stress-test-output` directory.

After broadcasting all the transactions, it will generate the `stress-test-output/broadcast-generate-result`
file to report the stress-test statistic result. For example:
```
Stress test report:
total transactions: 3040
cost time: 0.250000 minutes
max block size: 1129
min block size: 0
tps: 202.666672
miss block rate: 0.000000
```

## Relay and Broadcast
If you want to relay the transactions from other network, you need to set `relayTrx.enable = true` and
other related the parameters, then execute the `generate` command.

The relayed transactions will be stored in the `relay-trx.csv` file in current `stress-test-output` directory.

To broadcast the relayed transactions, you need set `broadcastRelayTrx = true` and execute the `broadcast` command.

*Note*: most of the relayed transactions may be illegal in the stress test network. You need to change the
transaction verification condition in java-tron source code to replay the transactions.
