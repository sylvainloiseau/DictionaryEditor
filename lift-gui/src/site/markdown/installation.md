# Installation

*Prerequisites and launch of the application*

## Prerequisites

- Java 25
- Maven 3.x

## Compilation

From the root of the project:

```sh
mvn install -DskipTests
```

This compiles the application and installs it in the local Maven repository.

## Launch

From the root of the project:

```sh
mvn javafx:run
```

On Windows, if Maven fails, check that `JAVA_HOME` is set correctly (no stray character or line break). For example, in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
mvn javafx:run
```

## Tests

```sh
mvn test
```
