## trond docker build

Build java-tron docker image.

### Synopsis

Build java-tron docker image locally. The master branch of java-tron repository will be built by default, using jdk1.8.0_202.


```
trond docker build [flags]
```

### Examples

```
# Please ensure that JDK 8 is installed, as it is required to execute the commands below.
# Build java-tron docker image, default: tronprotocol/java-tron:latest.
$ ./trond docker build

# Build java-tron docker image with specified org, artifact and version
$ ./trond docker build -o tronprotocol -a java-tron -v latest

```

### Options

```
  -a, --artifact string   ArtifactName for the docker image (default "java-tron")
  -h, --help              help for build
  -o, --org string        OrgName for the docker image (default "tronprotocol")
  -v, --version string    Release version for the docker image (default "latest")
```

### SEE ALSO

* [trond docker](trond_docker.md)	 - Commands for operating java-tron docker image.
