/*
 * Copyright (c) 2015-2017, Dell EMC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.emc.metalnx.services.zip;

import com.emc.metalnx.services.interfaces.ZipService;
import org.irods.jargon.core.exception.FileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for zipping directories.
 */
@Service
@Transactional
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.INTERFACES)
public class ZipServiceImpl implements ZipService {
    private static final Logger logger = LoggerFactory.getLogger(ZipServiceImpl.class);
    private static final String ZIP_EXTENSION = ".zip";

    public File createZip(File directoryToPlaceZip, File directoryToZip) {
        List<File> files = new ArrayList<>();
        getAllFiles(directoryToZip, files);
        return writeZipFile(directoryToPlaceZip, directoryToZip, files);
    }

    private void getAllFiles(File dir, List<File> files) {
        File[] filesArr = dir.listFiles();

        if (filesArr == null) {
            return;
        }

        for (File file: filesArr) {
            files.add(file);
            if (file.isDirectory()) {
                getAllFiles(file, files);
            }
        }
    }

    private File writeZipFile(File directoryToPlaceZip, File directoryToZip, List<File> fileList) {
        String zipFileName = String.format("%s/%s%s", directoryToPlaceZip.getName(),
                directoryToZip.getName(), ZIP_EXTENSION);

        FileOutputStream fos = null;
        ZipOutputStream zos = null;

        try {
            fos = new FileOutputStream(zipFileName);
            zos = new ZipOutputStream(fos);

            for (File file : fileList) {
                if (!file.isDirectory()) { // we only zip files, not directories
                    addToZip(directoryToZip, file, zos);
                }
            }
        } catch (FileNotFoundException | IOException e) {
            logger.error("Could not create zip file");
        } finally {
            try {
                if (zos != null) zos.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                logger.error("Could not close zip streams: {}", e);
            }
        }

        return new File(zipFileName);
    }

    private static void addToZip(File directoryToZip, File file, ZipOutputStream zos) throws FileNotFoundException,
            IOException {

        FileInputStream fis = new FileInputStream(file);

        // we want the zipEntry's path to be a relative path that is relative
        // to the directory being zipped, so chop off the rest of the path
        String zipFilePath = file.getCanonicalPath().substring(directoryToZip.getCanonicalPath().length() + 1,
                file.getCanonicalPath().length());
        System.out.println("Writing '" + zipFilePath + "' to zip file");
        ZipEntry zipEntry = new ZipEntry(zipFilePath);
        zos.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }

        zos.closeEntry();
        fis.close();
    }
}
