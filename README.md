# Mapping2Tiny

This is a command-line program used to transform a mapping file/archive into tiny format.

### Usage
```
java -jar Mapping2Tiny.jar [-h|--help][-f|--from tiny1|tiny2|proguard|enigma|enigma_zip|autodetect] [-1|--tiny1] [-2|--tiny2] [-c|--default-source-name source|...] [-e|--default-target-name target|...] [-w|--download] -o <output.tiny><path_or_uri/to/source>
```

### Build

Run this command to build:

```
./gradlew build
```

The executable output jar file is at `build/libs/Mapping2Tiny-$version-slim.jar`.
