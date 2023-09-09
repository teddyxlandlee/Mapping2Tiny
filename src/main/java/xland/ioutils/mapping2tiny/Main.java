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

import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println(help()); return;
        }

        Boolean exportsTiny2 = null;
        SupportedInputMapping mappingFrom = null;
        String defaultSourceName = null, defaultTargetName = null;
        boolean download = false;
        String input = null;
        Path output = null;

        var itr = Arg.parse(UNIX_MAP, args).iterator();
        while (itr.hasNext()) {
            Arg arg = itr.next();
            if (arg.isNormal()) {
                assumeNull(input, "input");
                input = arg.getText();
                continue;
            }
            switch (arg.getText()) {    // gnu
                case "help":
                    System.out.println(help()); return;
                case "from":
                    assumeNull(mappingFrom, "source mapping");
                    mappingFrom = SupportedInputMapping.byId(itr.nextNormalText());
                    break;
                case "tiny1":
                    assumeNull(exportsTiny2, "output format");
                    exportsTiny2 = Boolean.FALSE;
                    break;
                case "tiny2":
                    assumeNull(exportsTiny2, "output format");
                    exportsTiny2 = Boolean.TRUE;
                    break;
                case "default-source-name":
                    assumeNull(defaultSourceName, "default source name");
                    defaultSourceName = itr.nextNormalText();
                    break;
                case "default-target-name":
                    assumeNull(defaultTargetName, "default target name");
                    defaultTargetName = itr.nextNormalText();
                    break;
                case "download":
                    assumeNull(download ? "" : null, "download toggle");
                    download = true;
                    break;
                case "output":
                    assumeNull(output, "output path");
                    output = Path.of(itr.nextNormalText());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        if (exportsTiny2 == null) exportsTiny2 = Boolean.TRUE;
        if (mappingFrom == null) mappingFrom = SupportedInputMapping.AUTODETECT;
        if (defaultSourceName == null) defaultSourceName = MappingUtil.NS_SOURCE_FALLBACK;
        if (defaultTargetName == null) defaultTargetName = MappingUtil.NS_TARGET_FALLBACK;
        checkNonNull(input, "input");
        checkNonNull(output, "output");

        Path inputFile;
        if (!download) {
            inputFile = Path.of(input);
        } else {
            try {
                URL url = new URL(input);
                inputFile = Files.createTempFile("Mapping2Tiny-".concat(Integer.toHexString(input.hashCode())), ".tmp");
                try (InputStream in = url.openStream()) {
                    Files.copy(in, inputFile, StandardCopyOption.REPLACE_EXISTING);
                }
                inputFile.toFile().deleteOnExit();
            } catch (Exception e) {
                throw new IOException("Failed to download " + input, e);
            }
        }

        var transformer = mappingFrom.transformMapping(inputFile, defaultSourceName, defaultTargetName);

        try (MappingWriter mw = MappingWriter.create(output, exportsTiny2 ? MappingFormat.TINY_2 : MappingFormat.TINY)) {
            transformer.transform(mw);
        }
    }

    private static final Map<Character, String> UNIX_MAP = Map.of(
            'h', "help",
            'f', "from",
            '1', "tiny1",
            '2', "tiny2",
            'c', "default-source-name",
            'e', "default-target-name",
            'w', "download",
            'o', "output"
    );

    public static String help() {
        return "java -jar Mapping2Tiny.jar [-h|--help]" +
                "[-f|--from tiny1|tiny2|proguard|enigma|enigma_zip|autodetect] " +
                "[-1|--tiny1] [-2|--tiny2] " +
                "[-c|--default-source-name source|...] " +
                "[-e|--default-target-name target|...] " +
                "[-w|--download] " +
                "-o <output.tiny>" +
                "<path_or_uri/to/source>";
    }

    private static void assumeNull(Object o, String s) throws IllegalArgumentException {
        if (o != null)
            throw new IllegalArgumentException("Duplicated " + s);
    }

    private static void checkNonNull(Object o, String s) throws IllegalArgumentException {
        if (o == null)
            throw new IllegalArgumentException("Missing " + s);
    }
}
