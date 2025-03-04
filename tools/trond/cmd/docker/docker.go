package docker

import (
	"github.com/MakeNowJust/heredoc/v2"
	"github.com/spf13/cobra"
)

// DockerCmd represents the docker command
var DockerCmd = &cobra.Command{
	Use:   "docker",
	Short: "Commands for operating java-tron docker image.",
	Long: heredoc.Doc(`
			Commands used for operating docker image, such as:

				1. build java-tron docker image locally
				2. test the built image
				3. pull the latest image from officical docker hub

			Please refer to the available commands below.
		`),
	Example: heredoc.Doc(`
			# Help information for docker command
			$ ./trond docker

			# Build java-tron docker image, defualt: tronprotocol/java-tron:latest
			$ ./trond docker build

			# Build java-tron docker image with specified org, artifact and version
			$ ./trond docker build -o tronprotocol -a java-tron -v latest

			# Test java-tron docker image, defualt: tronprotocol/java-tron:latest
			$ ./trond docker test

			# Test java-tron docker image with specified org, artifact and version
			$ ./trond docker test -o tronprotocol -a java-tron -v latest
		`),
}

func init() {
}
