# RunExternal 

This project builds a library with classes which better handle running external programs using Java.

## Building RunExternal
- fork or clone this repository
- cd into the folder and ..
- run `mvn clean install`

## Adding to your maven project
Inside `dependencies` block, add:
```
        <dependency>
            <groupId>com.myproject.runner</groupId>
            <artifactId>run-external</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

## Samples
- [Java version](https://github.com/wagnerjfr/run-external/blob/main/src/main/java/com/myproject/runner/samples/JavaVersion.java)
- [Oracle Cloud CLI](https://github.com/wagnerjfr/run-external/blob/main/samples/OracleCloudCLI.java)
