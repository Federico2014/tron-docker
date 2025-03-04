package docker

import (
	"fmt"
	"strings"

	"github.com/MakeNowJust/heredoc/v2"
	"github.com/spf13/cobra"
	"github.com/tronprotocol/tron-docker/utils"
)

// buildCmd represents the snapshot source command
var buildCmd = &cobra.Command{
	Use:   "build",
	Short: "Build java-tron docker image.",
	Long: heredoc.Doc(`
			Build java-tron docker image locally. The master branch of java-tron repository will be built by default, using jdk1.8.0_202.
		`),
	Example: heredoc.Doc(`
			# Please ensure that JDK 8 is installed, as it is required to execute the commands below.
			# Build java-tron docker image, default: tronprotocol/java-tron:latest.
			$ ./trond docker build

			# Build java-tron docker image with specified org, artifact and version
			$ ./trond docker build -o tronprotocol -a java-tron -v latest
		`),
	Run: func(cmd *cobra.Command, args []string) {

		if yes, err := utils.IsJDK1_8(); err != nil || !yes {
			fmt.Println("Error: JDK version should be 1.8")
			return
		}

		// Get the flag value
		org, _ := cmd.Flags().GetString("org")
		artifact, _ := cmd.Flags().GetString("artifact")
		version, _ := cmd.Flags().GetString("version")

		fmt.Println("The building progress may take a long time, depending on your network speed.")
		fmt.Println("If you don't specify the flags for building, the default values will be used.")
		fmt.Println("The default result will be: tronprotocol/java-tron:latest")
		fmt.Println("Start building...")
		cmds := []string{
			fmt.Sprintf("./gradlew --no-daemon sourceDocker -PdockerOrgName=%s -PdockerArtifactName=%s -Prelease.releaseVersion=%s", org, artifact, version),
		}
		if err := utils.RunMultipleCommands(strings.Join(cmds, " && "), "./tools/gradlew"); err != nil {
			fmt.Println("Error: ", err)
			return
		}
	},
}

func init() {

	buildCmd.Flags().StringP("org", "o", "tronprotocol", "OrgName for the docker image")
	buildCmd.Flags().StringP("artifact", "a", "java-tron", "ArtifactName for the docker image")
	buildCmd.Flags().StringP("version", "v", "latest", "Release version for the docker image")

	DockerCmd.AddCommand(buildCmd)
}
