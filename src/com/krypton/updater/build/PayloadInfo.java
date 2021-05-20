/*
 * Copyright (C) 2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krypton.updater.build;

import com.krypton.updater.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PayloadInfo {

    // Payload info
    private static final String METADATA_FILE = "META-INF/com/android/metadata";
    private static final String PAYLOAD_FILE = "payload.bin";
    private static final String PAYLOAD_PROPERTIES_FILE = "payload_properties.txt";

    private String filePath;
    private long offset = -1;
    private long size = -1;
    private String[] headerKeyValuePairs = new String[4];

    public PayloadInfo(File file) {
        filePath = String.format("file://%s", file.getAbsolutePath());
		try (ZipFile zip = new ZipFile(file)) {

            // Get offset and size of payload
            ZipEntry metadata = zip.getEntry(METADATA_FILE);
            if (metadata != null) {
                try (InputStreamReader inStream = new InputStreamReader(zip.getInputStream(metadata));
                    BufferedReader reader = new BufferedReader(inStream)) {
                    String line = reader.readLine();
                    if (line != null) {
                        line = line.substring(line.indexOf('=') + 1);
                        for (String str: line.split(",")) {
                            if (str.contains(PAYLOAD_FILE)) {
                                String[] data = str.split(":");
                                offset = Long.parseLong(data[1]);
                                size = Long.parseLong(data[2]);
                            }
                        }
                    }
                } catch (IOException e) {
                    Utils.log(e);
                }
            }

            // Get headerKeyValuePairs
            ZipEntry payloadProps = zip.getEntry(PAYLOAD_PROPERTIES_FILE);
            if (payloadProps != null) {
                try (InputStreamReader inStream = new InputStreamReader(zip.getInputStream(payloadProps));
                        BufferedReader reader = new BufferedReader(inStream)) {
                    String line;
                    for (int i = 0; (line = reader.readLine()) != null && i < 4; i++) {
                        headerKeyValuePairs[i] = line;
                    }
                } catch (IOException e) {
                    Utils.log(e);
                }
            }
		} catch (IOException e) {
            Utils.log(e);
        }
    }

    public String getFilePath() {
        return filePath;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

    public String[] getHeader() {
        return headerKeyValuePairs;
    }

    public boolean validateData() {
        if (offset == -1) {
            return false;
        }
        if (size == -1) {
            return false;
        }
        if (headerKeyValuePairs != null) {
            for (int i = 0; i < 4; i++) {
                if (headerKeyValuePairs[i] == null) {
                    return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
