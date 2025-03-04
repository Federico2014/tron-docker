package snapshot

import (
	"github.com/MakeNowJust/heredoc/v2"
	"github.com/spf13/cobra"
	"github.com/tronprotocol/tron-docker/utils"
)

// sourceCmd represents the snapshot source command
var sourceCmd = &cobra.Command{
	Use:   "source",
	Short: "Show available snapshot source.",
	Long: heredoc.Doc(`
			Support different types of snapshot (Fullnode, Lite Fullnode), in different regions (Singapore, America).
		`),
	Example: heredoc.Doc(`
			# Show available snapshot source
			$ ./trond snapshot source
		`),
	Run: func(cmd *cobra.Command, args []string) {
		utils.ShowSnapshotDataSourceList()
	},
}

func init() {
	SnapshotCmd.AddCommand(sourceCmd)
}
