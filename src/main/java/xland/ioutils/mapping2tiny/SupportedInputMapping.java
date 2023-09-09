/*
 *    Mapping2Tiny, a simple mapping converter
 *    Copyright (C) 2023 teddyxlandlee
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xland.ioutils.mapping2tiny;

import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.*;

import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

enum SupportedInputMapping {
    TINY1("tiny1"),
    TINY2("tiny2"),
    PROGUARD("proguard"),
    ENIGMA("enigma", true),
    ENIGMA_ZIP("enigma_zip"),
    TINY_ZIP("tiny_zip"),
    AUTODETECT("autodetect"),
    ;
    private final String id;
    private final boolean isDirectory;
    private static final Map<String, SupportedInputMapping> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Object::toString, Function.identity()));



    SupportedInputMapping(String id, boolean isDirectory) {
        this.id = id;
        this.isDirectory = isDirectory;
    }

    SupportedInputMapping(String id) {
        this(id, false);
    }

    public static SupportedInputMapping byId(String s) {
        return BY_ID.getOrDefault(s, AUTODETECT);
    }

    @Override
    public String toString() {
        return id;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    @FunctionalInterface
    interface MappingTransformer {
        void transform(MappingWriter writer) throws IOException;
    }

    MappingTransformer transformMapping(
            Path input,
            String defaultSrcName, String defaultTargetName
    ) throws IOException {
        checkDir(input);
        final boolean[] assumeTinyZip = {false};
        switch (this) {
            case TINY1:
                return writer -> {
                    try (var reader = Files.newBufferedReader(input)) {
                        Tiny1Reader.read(reader, writer);
                    }
                };
            case TINY2:
                return writer -> {
                    try (var reader = Files.newBufferedReader(input)) {
                        Tiny2Reader.read(reader, writer);
                    }
                };
            case PROGUARD:
                return writer -> {
                    try (var reader = Files.newBufferedReader(input)) {
                        ProGuardReader.read(reader, defaultSrcName, defaultTargetName, writer);
                    }
                };
            case ENIGMA:
                return writer -> EnigmaReader.read(input, defaultSrcName, defaultTargetName, writer);
            case ENIGMA_ZIP:
                // ZipFS
                return writer -> {
                    URI uri = URI.create("jar:" + input.toUri());
                    try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                        final Path path = fs.getPath("/");
                        ENIGMA.transformMapping(path, defaultSrcName, defaultTargetName).transform(writer);
                    }
                };
            case TINY_ZIP:
                assumeTinyZip[0] = true;
                // no break
            case AUTODETECT:
                if (Files.isDirectory(input))
                    return ENIGMA.transformMapping(input, defaultSrcName, defaultTargetName);
                if (!isZip(input)) {
                    MappingFormat mappingFormat;
                    try (var reader = Files.newBufferedReader(input)) {
                        mappingFormat = net.fabricmc.mappingio.MappingReader.detectFormat(reader);
                    }
                    SupportedInputMapping sim;
                    switch (mappingFormat) {
                        case TINY: sim = TINY1; break;
                        case TINY_2: sim = TINY2; break;
                        case PROGUARD: sim = PROGUARD; break;
                        default: throw new UnsupportedOperationException("Unsupported mapping format: " + mappingFormat);
                    }
                    return sim.transformMapping(input, defaultSrcName, defaultTargetName);
                } else {
                    return writer -> {
                        scanZip:
                        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(input))) {
                            ZipEntry e;
                            while ((e = zis.getNextEntry()) != null) {
                                if ("mappings/mappings.tiny".equals(e.getName()))
                                    break;
                            }
                            if (e == null) {
                                // mappings.tiny not found, be enigma
                                if (assumeTinyZip[0]) {
                                    throw new FileNotFoundException("mappings/mappings.tiny");
                                }
                                break scanZip;
                            }
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zis))) {
                                reader.mark(3);
                                char[] buf = new char[3];
                                if (reader.read(buf) < 3) {
                                    throw new IOException("Unknown format for mappings.tiny");
                                }
                                reader.reset();
                                switch (String.valueOf(buf)) {
                                    case "v1\t":
                                        Tiny1Reader.read(reader, writer);
                                        break;
                                    case "tin":
                                        Tiny2Reader.read(reader, writer);
                                        break;
                                    default:
                                        throw new IOException("Unknown format for mappings.tiny");
                                }
                            }
                        }
                    };
                }
            default:
                throw new IncompatibleClassChangeError();
        }
    }

    private void checkDir(Path path) throws IOException {
        if (this == AUTODETECT) return;
        if (Files.isDirectory(path) == this.isDirectory()) return;
        throw new IOException(String.format("Path %s doesn't match the directory rule of %s", path, this));
    }

    private static final byte[] ZIP_HEADER = { 'P', 'K', 3, 4 };

    private static boolean isZip(Path path) throws IOException {
        try (var is = Files.newInputStream(path)) {
            return Arrays.equals(is.readNBytes(4), ZIP_HEADER);
        }
    }
}
