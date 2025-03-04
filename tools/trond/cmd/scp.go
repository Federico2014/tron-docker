package cmd

import (
	"fmt"
	"log"
	"os/exec"

	"github.com/spf13/cobra"
)

var (
	sourceFile  string
	destination string
	keyPath     string
)

// Define the `scp` command
var scpCmd = &cobra.Command{
	Use:   "scp",
	Short: "Securely copy a file over SSH",
	Long:  "This command securely copies a file from a local machine to a remote machine over SSH using the SCP protocol.",
	Run: func(cmd *cobra.Command, args []string) {
		// Ensure the flags are provided
		if sourceFile == "" || destination == "" {
			log.Fatal("Both source file and destination must be provided.")
		}

		// Build the SCP command
		argsNew := []string{"scp"}
		if keyPath != "" {
			argsNew = append(argsNew, "-i", keyPath) // Add custom SSH key if provided
		}
		argsNew = append(argsNew, sourceFile, destination)

		// Execute the SCP command
		cmdScp := exec.Command(argsNew[0], argsNew[1:]...)
		output, err := cmdScp.CombinedOutput()
		if err != nil {
			log.Fatalf("Failed to run command: %v\n", err)
		}

		// Print the output
		fmt.Println(string(output))
	},
}

func init() {
	// rootCmd.AddCommand(scpCmd)

	// Define flags for source file, destination, and SSH key path
	scpCmd.Flags().StringVarP(&sourceFile, "source", "s", "", "Path to the source file")
	scpCmd.Flags().StringVarP(&destination, "destination", "d", "", "Path to the destination (user@hostname:/path/to/remote)")
	scpCmd.Flags().StringVarP(&keyPath, "key", "k", "", "Path to the SSH private key file")

	// Mark source and destination as required flags
	if err := scpCmd.MarkFlagRequired("source"); err != nil {
		log.Fatalf("Error marking source flag as required: %v", err)
	}
	if err := scpCmd.MarkFlagRequired("destination"); err != nil {
		log.Fatalf("Error marking destination flag as required: %v", err)
	}
}
