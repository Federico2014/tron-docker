package snapshot

import (
	"fmt"
	"log"

	"github.com/MakeNowJust/heredoc/v2"
	"github.com/spf13/cobra"
	"github.com/tronprotocol/tron-docker/utils"
)

// listCmd represents the listFull command
var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List available snapshots of target source.",
	Long: heredoc.Doc(`
			Refer to the snapshot source domain you input, the available backup snapshots will be showen below.<br>
			Note: different domain may have different snapshots that can be downloaded.
	`),
	Example: heredoc.Doc(`
			# List available snapshots of target source domain 34.143.247.77
			$ ./trond snapshot list -d 34.143.247.77
		`),
	Run: func(cmd *cobra.Command, args []string) {

		// Get the flag value
		domain, _ := cmd.Flags().GetString("domain")

		// Validate flag values
		if domain == "" {
			fmt.Println("Error: --domain flag cannot be empty")
			return
		}

		if !utils.CheckDomain(domain) {
			fmt.Println("Error: domain value not supported\nRun \"./trond snapshot source\" to check available items")
			return
		}

		if utils.IsNile(domain) {
			if err := utils.ShowSnapshotListForNile(); err != nil {
				fmt.Println("Error:", err)
			}
			return
		}

		if err := utils.ShowSnapshotList(domain, false); err != nil {
			fmt.Println("Error:", err)
		}
	},
}

func init() {

	listCmd.Flags().StringP("domain", "d", "", "Domain for target snapshot source (required)\nPlease run command \"./trond snapshot source\" to get the available snapshot source domains")

	if err := listCmd.MarkFlagRequired("domain"); err != nil {
		log.Fatalf("Error marking domain flag as required: %v", err)
	}

	SnapshotCmd.AddCommand(listCmd)
}
