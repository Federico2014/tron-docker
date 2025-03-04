package utils

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
)

// RunCommand executes a shell command and returns its output or an error
func RunCommand(command string, args ...string) (string, error) {
	// Create a new command
	cmd := exec.Command(command, args...)

	// Capture the output
	var out bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &stderr

	// Run the command
	err := cmd.Run()
	if err != nil {
		return "", fmt.Errorf("command failed: %s, error: %v", stderr.String(), err)
	}

	return out.String(), nil
}

// Function to stream output line by line
func streamOutput(pipe io.Reader) {
	scanner := bufio.NewScanner(pipe)
	for scanner.Scan() {
		fmt.Println(scanner.Text()) // Print each line as it comes
	}
}

// RunMultipleCommands executes multiple shell commands in a single execution
func RunMultipleCommands(commands string, workDir string) error {
	fmt.Println(commands)
	// Get the current directory
	originalDir, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to get current directory: %v", err)
	}

	// Change to the target directory
	if err := os.Chdir(workDir); err != nil {
		return fmt.Errorf("failed to change directory: %v", err)
	}

	// Ensure we return to the original directory
	defer func() {
		if err := os.Chdir(originalDir); err != nil {
			fmt.Fprintf(os.Stderr, "failed to change back to original directory: %v\n", err)
		}
	}()

	cmd := exec.Command("bash", "-c", commands) // Linux/macOS uses 'bash -c'
	// Get stdout pipe
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("command failed, error: %v", err)
	}

	// Get stderr pipe
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return fmt.Errorf("command failed, error: %v", err)
	}

	// Start the command before reading output
	if err := cmd.Start(); err != nil {
		return fmt.Errorf("command failed, error: %v", err)
	}

	// Create goroutines to read both stdout and stderr in real time
	go streamOutput(stdout)
	go streamOutput(stderr)

	// Wait for the command to finish

	// Run the command
	err = cmd.Wait()
	if err != nil {
		return fmt.Errorf("command failed, error: %v", err)
	}

	return nil
}

// PathExists checks if a given path exists and returns whether it's a file or a directory.
func PathExists(path string) (exists bool, isDir bool) {
	info, err := os.Stat(path)
	if os.IsNotExist(err) {
		return false, false
	}
	return true, info.IsDir()
}

// Check if the current working directory ends with the target directory
func PwdEndsWith(target string) (bool, error) {
	pwd, err := os.Getwd()
	if err != nil {
		return false, err
	}
	return strings.HasSuffix(pwd, target), nil
}

// CreateDir creates a directory. If recursive is true, it creates parent directories as needed.
func CreateDir(path string, recursive bool) error {
	var err error
	if recursive {
		err = os.MkdirAll(path, 0755) // Creates parent directories if needed
	} else {
		err = os.Mkdir(path, 0755) // Creates a single directory
	}

	return err
}

// -----------------------------------------------------------------------------
// JDK check
// -----------------------------------------------------------------------------
func getJDKVersion() (string, error) {
	// Execute the 'java -version' command
	cmd := exec.Command("java", "-version")
	var stderr bytes.Buffer
	cmd.Stderr = &stderr // JDK version info is printed to stderr

	// Run the command
	if err := cmd.Run(); err != nil {
		return "", err
	}

	// Extract the first line from stderr (which contains the version)
	outputLines := strings.Split(stderr.String(), "\n")
	if len(outputLines) > 0 {
		return outputLines[0], nil
	}

	return "", fmt.Errorf("unable to determine JDK version")
}

func IsJDK1_8() (bool, error) {
	version, err := getJDKVersion()
	if err != nil {
		return false, err
	}

	// Check if the version string contains "1.8"
	return strings.Contains(version, `"1.8.`), nil
}

// -----------------------------------------------------------------------------
// docker-compose
// -----------------------------------------------------------------------------
// IsComposeRunning checks if specific services from a compose file are running
func IsComposeRunning(composePath string, serviceNames ...string) (bool, error) {
	args := []string{"-f", composePath, "ps", "-q"}
	args = append(args, serviceNames...)

	cmd := exec.CommandContext(context.Background(), "docker-compose", args...)
	output, err := cmd.CombinedOutput()

	if err != nil {
		return false, fmt.Errorf("error checking running state: %v\nOutput: %s", err, output)
	}

	return len(strings.TrimSpace(string(output))) > 0, nil
}

func RunComposeServiceOnce(composePath string) error {
	cmd := exec.CommandContext(
		context.Background(),
		"docker-compose",
		"-f", composePath,
		"up", "-d",
	)

	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("failed to start: %v\nOutput: %s", err, output)
	}

	return nil
}

// StartDockerCompose starts Docker Compose services
func StartDockerCompose(composePath string) (string, error) {
	cmd := exec.CommandContext(context.Background(),
		"docker-compose",
		"-f", composePath,
		"up", "-d",
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("error starting services: %v\nOutput: %s", err, output)
	}

	return string(output), nil
}

// StopDockerCompose stops Docker Compose services
func StopDockerCompose(composePath string) (string, error) {
	cmd := exec.CommandContext(context.Background(),
		"docker-compose",
		"-f", composePath,
		"down",
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("error stopping services: %v\nOutput: %s", err, output)
	}

	return string(output), nil
}
