# TRON Docker

This repository provides tools and guidance to help the community quickly get started with the TRON network and development.

## Features

### 🚀 Quick start for single FullNode
Easily deploy a single TRON FullNode connected to **Mainnet** or **Nile testnet** using Docker. Follow the instructions to get your node up and running in minutes.

### 🔗 Private chain setup
Set up your own private TRON blockchain network for development and testing. The provided configurations simplify deployment and management, making it ideal for custom use cases.

### 📊 Node monitoring with Prometheus and Grafana
Monitor the health and performance of your TRON nodes with integrated **Prometheus** and **Grafana** services. Real-time metrics and visualizations are just a few steps away.

### 🛠️ Tools
We also provide tools to facilitate the CI and testing process:
- **Gradle Docker**: Automate the build and testing of the `java-tron` Docker image using Gradle.
- **DBFork**: Launch a private java-tron network based on the Mainnet database state, enabling shadow fork testing.
- **Stress Test**: Execute the stress test and evaluate the performance of the java-tron fullnode.

## Getting Started

### Prerequisites
- Docker
- Docker Compose

### Start the services
First clone the repository:

```sh
git clone https://github.com/tronprotocol/tron-docker.git
cd tron-docker
```

Then, navigate to the relevant directory and follow the instructions in the respective README to start the services：
- **TRON network deployment related:**
   - **Single FullNode**: Use the [single_node](./single_node) folder.
   - **Private TRON network**: Use the [private_net](./private_net) folder.
   - **Node monitoring**: Use the [metric_monitor](./metric_monitor) folder.

- **Tools**:
   - **Gradle Docker**: Automate Docker image builds and testing. Check the [gradle docker](./tools/docker/README.md) documentation.
   - **DBFork**: Perform shadow fork testing. Follow the [DBFork guidance](./tools/dbfork/README.md).
   - **Stress Test**: Execute the stress testing. Follow the [stress test guidance](./tools/stress_test/README.md).


## Troubleshooting
If you encounter any difficulties, please refer to the [Issue Work Flow](https://tronprotocol.github.io/documentation-en/developers/issue-workflow/#issue-work-flow), then raise an issue on [GitHub](https://github.com/tronprotocol/tron-docker/issues). For general questions, please use [Discord](https://discord.gg/cGKSsRVCGm) or [Telegram](https://t.me/TronOfficialDevelopersGroupEn).

## Contributing

All contributions are welcome. Check [contribution](CONTRIBUTING.md) for more details.

## License

This repository is released under the [LGPLv3 license](https://github.com/tronprotocol/tron-docker/blob/main/LICENSE).
